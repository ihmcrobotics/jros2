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
import action_msgs.GoalInfo;
import action_msgs.GoalStatus;
import action_msgs.GoalStatusArray;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A ROS 2 action server, compatible with rclcpp_action / rcl_action clients. It serves
 * {@code <action>/_action/send_goal}, {@code cancel_goal} and {@code get_result}, and publishes
 * {@code <action>/_action/feedback} and {@code <action>/_action/status}.
 * <p>
 * Create with {@link ROS2Node#createActionServer(String, Class, Class, Class, ROS2ActionServerCallbacks)}.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class ROS2ActionServer<G extends ROS2Message<G>, R extends ROS2Message<R>, F extends ROS2Message<F>>
{
   /**
    * Default time a finished goal is kept to answer result requests and appear in the status, as in rcl_action.
    */
   public static final long DEFAULT_RESULT_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);

   private final String actionName;
   private final ROS2ActionTypeSupport<G, R, F> types;
   private final ROS2ActionServerCallbacks<G, R, F> callbacks;
   private final ExecutorService executor;

   private final ROS2Service sendGoalService;
   private final ROS2Service<CancelGoal_Request, CancelGoal_Response> cancelGoalService;
   private final ROS2Service getResultService;
   private final ROS2Publisher feedbackPublisher;
   private final ROS2Publisher<GoalStatusArray> statusPublisher;

   /**
    * Goals by id, in acceptance order. Guarded by this.
    */
   private final Map<UUID, ROS2ServerGoalHandle<G, R, F>> goals = new LinkedHashMap<>();
   /**
    * Result requests waiting for their goal to finish. Guarded by this.
    */
   private final Map<UUID, List<SampleIdentity>> pendingResultRequests = new LinkedHashMap<>();
   private volatile long resultTimeoutNanos = DEFAULT_RESULT_TIMEOUT_NANOS;
   private boolean closed;

   ROS2ActionServer(ROS2Node node,
                    String actionName,
                    Class<G> goalType,
                    Class<R> resultType,
                    Class<F> feedbackType,
                    ROS2ActionServerCallbacks<G, R, F> callbacks)
   {
      this.actionName = actionName;
      this.types = new ROS2ActionTypeSupport<>(goalType, resultType, feedbackType);
      this.callbacks = callbacks;

      AtomicInteger threadCount = new AtomicInteger();
      executor = Executors.newCachedThreadPool(runnable ->
      {
         Thread thread = new Thread(runnable, "ROS2ActionServer" + actionName + "-" + threadCount.incrementAndGet());
         thread.setDaemon(true);
         return thread;
      });

      String prefix = actionName + "/_action/";
      feedbackPublisher = node.createPublisher(new ROS2Topic<>(prefix + "feedback", types.feedbackMessageType, ROS2QoSProfile.DEFAULT));
      statusPublisher = node.createPublisher(new ROS2Topic<>(prefix + "status", GoalStatusArray.class, ROS2ActionClient.STATUS_QOS));
      sendGoalService = node.createService(prefix + "send_goal", types.sendGoalRequestType, types.sendGoalResponseType,
                                           (ROS2ServiceCallback) (request, response) -> handleSendGoal((ROS2Message) request, (ROS2Message) response));
      cancelGoalService = node.createService(prefix + "cancel_goal", CancelGoal_Request.class, CancelGoal_Response.class, this::handleCancelGoal);
      getResultService = node.createDeferredService(prefix + "get_result", types.getResultRequestType, types.getResultResponseType,
                                                    (ROS2Service.DeferredCallback) (request, requestId) -> handleGetResult((ROS2Message) request, requestId),
                                                    ROS2QoSProfile.DEFAULT);
   }

   /*
    * Services
    */

   private void handleSendGoal(ROS2Message request, ROS2Message response)
   {
      UUID goalId = ROS2ActionTypeSupport.toUUID(types.sendGoalRequestGoalId(request));
      G goal = types.newGoal();
      goal.set(types.sendGoalRequestGoal(request));
      long nowNanos = ROS2ActionTypeSupport.nowNanos();

      synchronized (this)
      {
         if (closed || goals.containsKey(goalId))
         {
            types.sendGoalResponseAccepted(response, false);
            return;
         }
      }

      ROS2GoalResponse decision;
      try
      {
         decision = callbacks.handleGoal(goalId, goal);
      }
      catch (RuntimeException e)
      {
         jros2.logError("Exception in handleGoal of action " + actionName + ", rejecting the goal", e);
         decision = ROS2GoalResponse.REJECT;
      }

      if (decision == null || decision == ROS2GoalResponse.REJECT)
      {
         types.sendGoalResponseAccepted(response, false);
         return;
      }

      ROS2GoalStatus initialStatus = decision == ROS2GoalResponse.ACCEPT_AND_EXECUTE ? ROS2GoalStatus.EXECUTING : ROS2GoalStatus.ACCEPTED;
      ROS2ServerGoalHandle<G, R, F> handle = new ROS2ServerGoalHandle<>(this, goalId, goal, nowNanos, initialStatus);
      synchronized (this)
      {
         goals.put(goalId, handle);
         publishStatus();
      }

      types.sendGoalResponseAccepted(response, true);
      ROS2ActionTypeSupport.toMessage(nowNanos, types.sendGoalResponseStamp(response));

      executor.execute(() ->
      {
         try
         {
            callbacks.handleAccepted(handle);
         }
         catch (Throwable e)
         {
            jros2.logError("Exception in handleAccepted of action " + actionName + " for goal " + goalId, e);
         }
      });
   }

   /**
    * Same goal selection and return codes as rcl_action_process_cancel_request, then each selected goal is offered to
    * {@link ROS2ActionServerCallbacks#handleCancel}, as rclcpp_action does.
    */
   private void handleCancelGoal(CancelGoal_Request request, CancelGoal_Response response)
   {
      UUID requestedId = ROS2ActionTypeSupport.toUUID(request.getGoalInfo().getGoalId());
      long requestedNanos = ROS2ActionTypeSupport.toNanos(request.getGoalInfo().getStamp());
      boolean zeroId = requestedId.getMostSignificantBits() == 0 && requestedId.getLeastSignificantBits() == 0;

      List<ROS2ServerGoalHandle<G, R, F>> candidates = new ArrayList<>();
      synchronized (this)
      {
         expireGoals();
         if (!zeroId && requestedNanos == 0)
         {
            // Exactly one goal
            ROS2ServerGoalHandle<G, R, F> handle = goals.get(requestedId);
            if (handle == null)
            {
               response.setReturnCode(CancelGoal_Response.ERROR_UNKNOWN_GOAL_ID);
               return;
            }
            if (!isCancelable(handle.status))
            {
               response.setReturnCode(CancelGoal_Response.ERROR_GOAL_TERMINATED);
               return;
            }
            candidates.add(handle);
         }
         else
         {
            // All goals accepted at or before the stamp (all goals if both are zero), plus the given goal
            long limitNanos = zeroId && requestedNanos == 0 ? Long.MAX_VALUE : requestedNanos;
            for (ROS2ServerGoalHandle<G, R, F> handle : goals.values())
            {
               if (isCancelable(handle.status) && (handle.getAcceptedTimeNanos() <= limitNanos || handle.getGoalId().equals(requestedId)))
               {
                  candidates.add(handle);
               }
            }
         }
      }

      response.setReturnCode(CancelGoal_Response.ERROR_NONE);
      boolean anyAccepted = false;
      for (ROS2ServerGoalHandle<G, R, F> handle : candidates)
      {
         ROS2CancelResponse decision;
         try
         {
            decision = callbacks.handleCancel(handle);
         }
         catch (RuntimeException e)
         {
            jros2.logError("Exception in handleCancel of action " + actionName + ", rejecting", e);
            decision = ROS2CancelResponse.REJECT;
         }

         if (decision == ROS2CancelResponse.ACCEPT)
         {
            synchronized (this)
            {
               if (!isCancelable(handle.status))
                  continue; // Finished meanwhile
               handle.status = ROS2GoalStatus.CANCELING;
            }
            anyAccepted = true;
            GoalInfo info = response.getGoalsCanceling().add();
            ROS2ActionTypeSupport.toMessage(handle.getGoalId(), info.getGoalId());
            ROS2ActionTypeSupport.toMessage(handle.getAcceptedTimeNanos(), info.getStamp());
         }
      }

      if (!candidates.isEmpty() && !anyAccepted)
      {
         response.setReturnCode(CancelGoal_Response.ERROR_REJECTED);
      }
      if (anyAccepted)
      {
         synchronized (this)
         {
            publishStatus();
         }
      }
   }

   private static boolean isCancelable(ROS2GoalStatus status)
   {
      return status == ROS2GoalStatus.ACCEPTED || status == ROS2GoalStatus.EXECUTING;
   }

   private void handleGetResult(ROS2Message request, SampleIdentity requestId)
   {
      UUID goalId = ROS2ActionTypeSupport.toUUID(types.getResultRequestGoalId(request));
      synchronized (this)
      {
         expireGoals();
         ROS2ServerGoalHandle<G, R, F> handle = goals.get(goalId);
         if (handle != null && !handle.status.isTerminal())
         {
            // Answered when the goal finishes
            SampleIdentity copy = new SampleIdentity();
            copy.set(requestId);
            pendingResultRequests.computeIfAbsent(goalId, id -> new ArrayList<>()).add(copy);
            return;
         }

         sendResult(handle, requestId);
      }
   }

   /**
    * @param handle the finished goal, or null for an unknown goal (status UNKNOWN and an empty result)
    */
   private void sendResult(ROS2ServerGoalHandle<G, R, F> handle, SampleIdentity requestId)
   {
      ROS2Message response = types.newGetResultResponse();
      if (handle != null)
      {
         types.getResultResponseStatus(response, handle.status.getId());
         types.getResultResponseResult(response).set(handle.result);
      }
      else
      {
         types.getResultResponseStatus(response, ROS2GoalStatus.UNKNOWN.getId());
      }
      getResultService.sendResponse(response, requestId);
   }

   /*
    * Goal handles
    */

   synchronized ROS2GoalStatus getStatus(ROS2ServerGoalHandle<G, R, F> handle)
   {
      return handle.status;
   }

   void transition(ROS2ServerGoalHandle<G, R, F> handle, ROS2GoalStatus target, R result)
   {
      synchronized (this)
      {
         ROS2GoalStatus current = handle.status;
         boolean allowed = switch (target)
         {
            case EXECUTING -> current == ROS2GoalStatus.ACCEPTED;
            case SUCCEEDED, ABORTED -> current == ROS2GoalStatus.EXECUTING || current == ROS2GoalStatus.CANCELING;
            case CANCELED -> current == ROS2GoalStatus.CANCELING;
            default -> false;
         };
         if (!allowed)
         {
            throw new IllegalStateException("Goal " + handle.getGoalId() + " cannot go from " + current + " to " + target);
         }

         handle.status = target;
         if (target.isTerminal())
         {
            handle.result = types.newResult();
            if (result != null)
               handle.result.set(result);
            handle.terminalNanos = System.nanoTime();

            List<SampleIdentity> waiting = pendingResultRequests.remove(handle.getGoalId());
            if (waiting != null)
            {
               for (SampleIdentity requestId : waiting)
                  sendResult(handle, requestId);
            }
         }
         publishStatus();
      }
   }

   void publishFeedback(ROS2ServerGoalHandle<G, R, F> handle, F feedback)
   {
      if (!handle.getStatus().isActive())
         return;

      ROS2Message message = types.newFeedbackMessage();
      ROS2ActionTypeSupport.toMessage(handle.getGoalId(), types.feedbackMessageGoalId(message));
      types.feedbackMessageFeedback(message).set(feedback);
      feedbackPublisher.publish(message);
   }

   /**
    * Guarded by this.
    */
   private void publishStatus()
   {
      if (closed)
         return;

      expireGoals();
      GoalStatusArray statusArray = new GoalStatusArray();
      for (ROS2ServerGoalHandle<G, R, F> handle : goals.values())
      {
         GoalStatus status = statusArray.getStatusList().add();
         ROS2ActionTypeSupport.toMessage(handle.getGoalId(), status.getGoalInfo().getGoalId());
         ROS2ActionTypeSupport.toMessage(handle.getAcceptedTimeNanos(), status.getGoalInfo().getStamp());
         status.setStatus(handle.status.getId());
      }
      statusPublisher.publish(statusArray);
   }

   /**
    * Forget goals that finished more than the result timeout ago. Guarded by this.
    */
   private void expireGoals()
   {
      long now = System.nanoTime();
      Iterator<ROS2ServerGoalHandle<G, R, F>> iterator = goals.values().iterator();
      while (iterator.hasNext())
      {
         ROS2ServerGoalHandle<G, R, F> handle = iterator.next();
         if (handle.status.isTerminal() && now - handle.terminalNanos >= resultTimeoutNanos)
            iterator.remove();
      }
   }

   /**
    * @return the goals this server knows about: active ones and those finished less than the result timeout ago
    */
   public synchronized List<ROS2ServerGoalHandle<G, R, F>> getGoals()
   {
      expireGoals();
      return new ArrayList<>(goals.values());
   }

   /**
    * Set how long finished goals are kept to answer result requests (default 10 s, as in rcl_action).
    */
   public void setResultTimeout(long timeout, TimeUnit unit)
   {
      resultTimeoutNanos = unit.toNanos(timeout);
   }

   public String getActionName()
   {
      return actionName;
   }

   /*
    * Lifecycle
    */

   List<ROS2Service<?, ?>> getServices()
   {
      return List.of(sendGoalService, cancelGoalService, getResultService);
   }

   List<ROS2Publisher<?>> getPublishers()
   {
      return List.of(feedbackPublisher, statusPublisher);
   }

   void closeResources()
   {
      synchronized (this)
      {
         closed = true;
         pendingResultRequests.clear();
      }
      executor.shutdownNow();
   }
}
