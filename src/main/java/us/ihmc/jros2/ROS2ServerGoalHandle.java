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

/**
 * A goal accepted by a {@link ROS2ActionServer}. Thread-safe.
 */
public class ROS2ServerGoalHandle<G extends ROS2Message<G>, R extends ROS2Message<R>, F extends ROS2Message<F>>
{
   private final ROS2ActionServer<G, R, F> server;
   private final UUID goalId;
   private final G goal;
   private final long acceptedNanos;
   // Guarded by the server
   ROS2GoalStatus status;
   R result;
   long terminalNanos;

   ROS2ServerGoalHandle(ROS2ActionServer<G, R, F> server, UUID goalId, G goal, long acceptedNanos, ROS2GoalStatus status)
   {
      this.server = server;
      this.goalId = goalId;
      this.goal = goal;
      this.acceptedNanos = acceptedNanos;
      this.status = status;
   }

   public UUID getGoalId()
   {
      return goalId;
   }

   /**
    * @return the goal; do not modify it
    */
   public G getGoal()
   {
      return goal;
   }

   /**
    * @return the time the goal was accepted, in nanoseconds since the epoch
    */
   public long getAcceptedTimeNanos()
   {
      return acceptedNanos;
   }

   public ROS2GoalStatus getStatus()
   {
      return server.getStatus(this);
   }

   public boolean isActive()
   {
      return getStatus().isActive();
   }

   public boolean isExecuting()
   {
      return getStatus() == ROS2GoalStatus.EXECUTING;
   }

   /**
    * @return true if a cancel request was accepted; the goal should stop and call {@link #canceled(ROS2Message)}
    */
   public boolean isCanceling()
   {
      return getStatus() == ROS2GoalStatus.CANCELING;
   }

   /**
    * Start executing a goal accepted with {@link ROS2GoalResponse#ACCEPT_AND_DEFER}.
    *
    * @throws IllegalStateException if the goal is not {@link ROS2GoalStatus#ACCEPTED}
    */
   public void execute()
   {
      server.transition(this, ROS2GoalStatus.EXECUTING, null);
   }

   /**
    * Publish feedback for this goal. Ignored once the goal has finished.
    */
   public void publishFeedback(F feedback)
   {
      server.publishFeedback(this, feedback);
   }

   /**
    * Finish the goal successfully.
    *
    * @throws IllegalStateException if the goal is not executing or canceling
    */
   public void succeed(R result)
   {
      server.transition(this, ROS2GoalStatus.SUCCEEDED, result);
   }

   /**
    * Finish the goal unsuccessfully.
    *
    * @throws IllegalStateException if the goal is not executing or canceling
    */
   public void abort(R result)
   {
      server.transition(this, ROS2GoalStatus.ABORTED, result);
   }

   /**
    * Finish a goal whose cancellation was accepted.
    *
    * @throws IllegalStateException if the goal is not canceling
    */
   public void canceled(R result)
   {
      server.transition(this, ROS2GoalStatus.CANCELED, result);
   }

   @Override
   public String toString()
   {
      return "Goal " + goalId + " (" + getStatus() + ")";
   }
}
