/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.jros2;

import action_msgs.CancelGoal_Request;
import action_msgs.CancelGoal_Response;
import action_msgs.GoalStatus;
import action_msgs.GoalStatusArray;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * A ROS 2 action client, compatible with rclcpp_action / rcl_action servers. It calls
 * {@code <action>/_action/send_goal}, {@code cancel_goal} and {@code get_result}, and subscribes to
 * {@code <action>/_action/feedback} and {@code <action>/_action/status}.
 * <p>
 * Create with {@link ROS2Node#createActionClient(String, Class, Class, Class)}.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class ROS2ActionClient<G extends ROS2Message<G>, R extends ROS2Message<R>, F extends ROS2Message<F>>
{
   /**
    * rcl_action_qos_profile_status_default: keep last 1, reliable, transient local.
    */
   static final ROS2QoSProfile STATUS_QOS = new ROS2QoSProfile();

   static
   {
      STATUS_QOS.history(ROS2QoSProfile.History.KEEP_LAST);
      STATUS_QOS.depth(1);
      STATUS_QOS.reliability(ROS2QoSProfile.Reliability.RELIABLE);
      STATUS_QOS.durability(ROS2QoSProfile.Durability.TRANSIENT_LOCAL);
   }

   private final String actionName;
   private final ROS2ActionTypeSupport<G, R, F> types;

   private final ROS2Client sendGoalClient;
   private final ROS2Client<CancelGoal_Request, CancelGoal_Response> cancelGoalClient;
   private final ROS2Client getResultClient;
   private final ROS2Subscription feedbackSubscription;
   private final ROS2Subscription<GoalStatusArray> statusSubscription;

   /**
    * Goals sent and not finished yet.
    */
   private final Map<UUID, ROS2ClientGoalHandle<G, R, F>> goals = new ConcurrentHashMap<>();
   private volatile boolean closed;

   ROS2ActionClient(ROS2Node node, String actionName, Class<G> goalType, Class<R> resultType, Class<F> feedbackType)
   {
      this.actionName = actionName;
      this.types = new ROS2ActionTypeSupport<>(goalType, resultType, feedbackType);

      String prefix = actionName + "/_action/";
      sendGoalClient = node.createClient(prefix + "send_goal", types.sendGoalRequestType, types.sendGoalResponseType);
      cancelGoalClient = node.createClient(prefix + "cancel_goal", CancelGoal_Request.class, CancelGoal_Response.class);
      getResultClient = node.createClient(prefix + "get_result", types.getResultRequestType, types.getResultResponseType);
      feedbackSubscription = node.createSubscription(new ROS2Topic<>(prefix + "feedback", types.feedbackMessageType, ROS2QoSProfile.DEFAULT),
                                                     (ROS2SubscriptionCallback) this::onFeedbackAvailable);
      statusSubscription = node.createSubscription(new ROS2Topic<>(prefix + "status", GoalStatusArray.class, STATUS_QOS), this::onStatusAvailable);
   }

   /**
    * Send a goal without feedback.
    *
    * @see #sendGoal(ROS2Message, ROS2FeedbackCallback)
    */
   public CompletableFuture<ROS2ClientGoalHandle<G, R, F>> sendGoal(G goal)
   {
      return sendGoal(goal, null);
   }

   /**
    * Send a goal. The returned future completes when the server accepts or rejects it ({@link ROS2ClientGoalHandle#isAccepted()});
    * the result then arrives through {@link ROS2ClientGoalHandle#getResultFuture()}, which this client requests automatically.
    *
    * @param goal             the goal, copied before this method returns
    * @param feedbackCallback called with the feedback of this goal, or null
    */
   public CompletableFuture<ROS2ClientGoalHandle<G, R, F>> sendGoal(G goal, ROS2FeedbackCallback<G, R, F> feedbackCallback)
   {
      UUID goalId = UUID.randomUUID();
      ROS2ClientGoalHandle<G, R, F> handle = new ROS2ClientGoalHandle<>(this, goalId, feedbackCallback);
      if (closed)
      {
         return CompletableFuture.failedFuture(new IllegalStateException("Action client for " + actionName + " is closed"));
      }

      ROS2Message request = types.newSendGoalRequest();
      ROS2ActionTypeSupport.toMessage(goalId, types.sendGoalRequestGoalId(request));
      types.sendGoalRequestGoal(request).set(goal);

      // Registered before sending so early feedback is not lost
      goals.put(goalId, handle);
      CompletableFuture<ROS2Message> response = sendGoalClient.sendRequest(request);
      return response.handle((sendGoalResponse, error) ->
      {
         if (error != null)
         {
            goals.remove(goalId);
            handle.fail(error);
            throw new java.util.concurrent.CompletionException(error);
         }

         if (!types.sendGoalResponseAccepted(sendGoalResponse))
         {
            goals.remove(goalId);
            handle.complete(ROS2GoalStatus.UNKNOWN, null);
            return handle;
         }

         handle.accepted(ROS2ActionTypeSupport.toNanos(types.sendGoalResponseStamp(sendGoalResponse)));
         requestResult(handle);
         return handle;
      });
   }

   private void requestResult(ROS2ClientGoalHandle<G, R, F> handle)
   {
      ROS2Message request = types.newGetResultRequest();
      ROS2ActionTypeSupport.toMessage(handle.getGoalId(), types.getResultRequestGoalId(request));
      CompletableFuture<ROS2Message> response = getResultClient.sendRequest(request);
      response.whenComplete((getResultResponse, error) ->
      {
         goals.remove(handle.getGoalId());
         if (error != null)
         {
            handle.fail(error);
            return;
         }
         R result = types.newResult();
         result.set(types.getResultResponseResult(getResultResponse));
         handle.complete(ROS2GoalStatus.fromId(types.getResultResponseStatus(getResultResponse)), result);
      });
   }

   /**
    * Request the server to cancel a goal.
    *
    * @return the server's answer; {@link CancelGoal_Response#getGoalsCanceling()} lists the goals that are being canceled
    */
   public CompletableFuture<CancelGoal_Response> cancelGoal(ROS2ClientGoalHandle<G, R, F> handle)
   {
      CancelGoal_Request request = new CancelGoal_Request();
      ROS2ActionTypeSupport.toMessage(handle.getGoalId(), request.getGoalInfo().getGoalId());
      return cancelGoalClient.sendRequest(request);
   }

   /**
    * Request the server to cancel all its goals, including those of other clients.
    */
   public CompletableFuture<CancelGoal_Response> cancelAllGoals()
   {
      return cancelGoalClient.sendRequest(new CancelGoal_Request());
   }

   /**
    * Request the server to cancel all goals accepted at or before a time, including those of other clients.
    *
    * @param stampNanos nanoseconds since the epoch
    */
   public CompletableFuture<CancelGoal_Response> cancelGoalsBefore(long stampNanos)
   {
      CancelGoal_Request request = new CancelGoal_Request();
      ROS2ActionTypeSupport.toMessage(stampNanos, request.getGoalInfo().getStamp());
      return cancelGoalClient.sendRequest(request);
   }

   /**
    * @return true if an action server has been matched for the three services
    */
   public boolean isActionServerReady()
   {
      return sendGoalClient.isServiceAvailable() && cancelGoalClient.isServiceAvailable() && getResultClient.isServiceAvailable();
   }

   /**
    * Block until an action server is available or the timeout elapses.
    */
   public boolean waitForActionServer(long timeoutMs)
   {
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
      for (ROS2Client client : new ROS2Client[] {sendGoalClient, cancelGoalClient, getResultClient})
      {
         long remainingMs = Math.max(0, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
         if (!client.waitForService(remainingMs))
            return false;
      }
      return true;
   }

   public String getActionName()
   {
      return actionName;
   }

   private void onFeedbackAvailable(ROS2MessageReader reader)
   {
      ROS2Message message = types.newFeedbackMessage();
      if (!reader.read(message))
         return;

      ROS2ClientGoalHandle<G, R, F> handle = goals.get(ROS2ActionTypeSupport.toUUID(types.feedbackMessageGoalId(message)));
      if (handle == null)
         return; // Another client's goal

      F feedback = types.newFeedback();
      feedback.set(types.feedbackMessageFeedback(message));
      try
      {
         handle.onFeedback(feedback);
      }
      catch (RuntimeException e)
      {
         jros2.logError("Exception in feedback callback of action " + actionName, e);
      }
   }

   private void onStatusAvailable(ROS2MessageReader<GoalStatusArray> reader)
   {
      GoalStatusArray statusArray = new GoalStatusArray();
      if (!reader.read(statusArray))
         return;

      for (GoalStatus status : statusArray.getStatusList())
      {
         ROS2ClientGoalHandle<G, R, F> handle = goals.get(ROS2ActionTypeSupport.toUUID(status.getGoalInfo().getGoalId()));
         if (handle != null)
            handle.updateStatus(ROS2GoalStatus.fromId(status.getStatus()));
      }
   }

   /*
    * Lifecycle
    */

   List<ROS2Client<?, ?>> getClients()
   {
      return List.of(sendGoalClient, cancelGoalClient, getResultClient);
   }

   List<ROS2Subscription<?>> getSubscriptions()
   {
      return List.of(feedbackSubscription, statusSubscription);
   }

   void closeResources()
   {
      closed = true;
      for (ROS2ClientGoalHandle<G, R, F> handle : goals.values())
      {
         handle.fail(new CancellationException("Action client for " + actionName + " was closed"));
      }
      goals.clear();
   }
}
