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

/**
 * Decision of an action server on a new goal, as in rclcpp_action.
 */
public enum ROS2GoalResponse
{
   /** The goal is rejected. */
   REJECT,
   /** The goal is accepted and starts executing. */
   ACCEPT_AND_EXECUTE,
   /** The goal is accepted; call {@link ROS2ServerGoalHandle#execute()} to start executing it. */
   ACCEPT_AND_DEFER
}
