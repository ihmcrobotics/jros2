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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A goal sent by a {@link ROS2ActionClient}. Thread-safe.
 */
public class ROS2ClientGoalHandle<G extends ROS2Message<G>, R extends ROS2Message<R>, F extends ROS2Message<F>>
{
   private final ROS2ActionClient<G, R, F> client;
   private final UUID goalId;
   private final ROS2FeedbackCallback<G, R, F> feedbackCallback;
   private final CompletableFuture<ROS2ActionResult<R>> resultFuture = new CompletableFuture<>();
   private volatile boolean accepted;
   private volatile long acceptedTimeNanos;
   private volatile ROS2GoalStatus status = ROS2GoalStatus.UNKNOWN;

   ROS2ClientGoalHandle(ROS2ActionClient<G, R, F> client, UUID goalId, ROS2FeedbackCallback<G, R, F> feedbackCallback)
   {
      this.client = client;
      this.goalId = goalId;
      this.feedbackCallback = feedbackCallback;
   }

   public UUID getGoalId()
   {
      return goalId;
   }

   /**
    * @return true if the server accepted the goal
    */
   public boolean isAccepted()
   {
      return accepted;
   }

   /**
    * @return the time the server accepted the goal, in nanoseconds since the epoch
    */
   public long getAcceptedTimeNanos()
   {
      return acceptedTimeNanos;
   }

   /**
    * @return the last status received from the server's status topic or result
    */
   public ROS2GoalStatus getStatus()
   {
      return status;
   }

   /**
    * @return completes when the goal finishes. For rejected goals it completes at once with status UNKNOWN and a null result.
    */
   public CompletableFuture<ROS2ActionResult<R>> getResultFuture()
   {
      return resultFuture;
   }

   /**
    * Request the server to cancel this goal.
    *
    * @see ROS2ActionClient#cancelGoal(ROS2ClientGoalHandle)
    */
   public CompletableFuture<action_msgs.CancelGoal_Response> cancel()
   {
      return client.cancelGoal(this);
   }

   void accepted(long stampNanos)
   {
      acceptedTimeNanos = stampNanos;
      accepted = true;
      if (status == ROS2GoalStatus.UNKNOWN)
         status = ROS2GoalStatus.ACCEPTED;
   }

   void updateStatus(ROS2GoalStatus newStatus)
   {
      // Once finished, the status does not go back
      if (!status.isTerminal())
         status = newStatus;
   }

   void onFeedback(F feedback)
   {
      if (feedbackCallback != null)
         feedbackCallback.onFeedback(this, feedback);
   }

   void complete(ROS2GoalStatus finalStatus, R result)
   {
      status = finalStatus;
      resultFuture.complete(new ROS2ActionResult<>(finalStatus, result));
   }

   void fail(Throwable throwable)
   {
      resultFuture.completeExceptionally(throwable);
   }

   @Override
   public String toString()
   {
      return "Goal " + goalId + " (" + status + ")";
   }
}
