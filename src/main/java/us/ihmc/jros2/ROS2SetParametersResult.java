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
 * Result of setting parameters, as in {@code rcl_interfaces/msg/SetParametersResult}.
 *
 * @param successful whether the parameters were set
 * @param reason     why they were not set, empty when successful
 */
public record ROS2SetParametersResult(boolean successful, String reason)
{
   static final ROS2SetParametersResult SUCCESS = new ROS2SetParametersResult(true, "");

   static ROS2SetParametersResult failure(String reason)
   {
      return new ROS2SetParametersResult(false, reason);
   }
}
