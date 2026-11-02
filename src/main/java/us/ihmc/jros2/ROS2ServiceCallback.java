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
 * Handles the requests received by a {@link ROS2Service}.
 *
 * @param <Req> the request type, e.g. {@code example_interfaces.AddTwoInts_Request}
 * @param <Res> the response type, e.g. {@code example_interfaces.AddTwoInts_Response}
 */
@FunctionalInterface
public interface ROS2ServiceCallback<Req extends ROS2Message<Req>, Res extends ROS2Message<Res>>
{
   /**
    * Called once per request, on a Fast-DDS listener thread. Fill {@code response}; it is sent when this method returns.
    * Both objects are reused between calls, so do not keep references to them.
    *
    * @param request  the received request
    * @param response the response to fill, reset to its default values before each call
    */
   void onRequest(Req request, Res response);
}
