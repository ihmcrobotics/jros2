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

import java.util.List;

/**
 * Validates parameter changes before they are applied, like rclcpp's {@code add_on_set_parameters_callback}. Called when
 * parameters are declared or set, locally or through the parameter services.
 */
@FunctionalInterface
public interface ROS2ParameterCallback
{
   /**
    * @param parameters the parameters about to be declared or set, with their new values
    * @return null to accept the change, or the reason it is rejected
    */
   String onSetParameters(List<ROS2Parameter> parameters);
}
