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
 * Callbacks of a {@link ROS2ActionServer}, as in rclcpp_action.
 */
public interface ROS2ActionServerCallbacks<G extends ROS2Message<G>, R extends ROS2Message<R>, F extends ROS2Message<F>>
{
   /**
    * Decide whether to accept a new goal. Called on a Fast-DDS listener thread; return quickly.
    *
    * @param goalId the goal id chosen by the client
    * @param goal   the goal, owned by the callee
    */
   ROS2GoalResponse handleGoal(UUID goalId, G goal);

   /**
    * Decide whether to accept a request to cancel an active goal. Called on a Fast-DDS listener thread; return quickly. When
    * accepted, the goal goes to {@link ROS2GoalStatus#CANCELING}; finish it with {@link ROS2ServerGoalHandle#canceled(ROS2Message)}.
    */
   ROS2CancelResponse handleCancel(ROS2ServerGoalHandle<G, R, F> goalHandle);

   /**
    * Execute an accepted goal. Called on a thread of the action server's own pool, so it may block until the goal finishes.
    * Publish feedback with {@link ROS2ServerGoalHandle#publishFeedback(ROS2Message)} and finish with
    * {@link ROS2ServerGoalHandle#succeed(ROS2Message)}, {@link ROS2ServerGoalHandle#abort(ROS2Message)} or
    * {@link ROS2ServerGoalHandle#canceled(ROS2Message)}.
    */
   void handleAccepted(ROS2ServerGoalHandle<G, R, F> goalHandle);
}
