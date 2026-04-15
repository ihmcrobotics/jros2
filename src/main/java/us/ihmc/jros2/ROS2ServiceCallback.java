/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
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
 * Callback interface for ROS 2 service servers.
 * <p>
 * Implement this interface to handle incoming service requests and generate responses.
 * The callback is invoked by {@link ROS2ServiceServer} each time a service request is received.
 * <p>
 * Example usage:
 * <pre>{@code
 * ROS2ServiceCallback<AddTwoInts_Request, AddTwoInts_Response> callback = (request, response) -> {
 *    response.setSum(request.getA() + request.getB());
 * };
 *
 * ROS2ServiceServer<AddTwoInts_Request, AddTwoInts_Response> server =
 *    node.createServiceServer("add_two_ints",
 *                            AddTwoInts_Request.class,
 *                            AddTwoInts_Response.class,
 *                            callback);
 * }</pre>
 *
 * @param <Request>  The service request message type
 * @param <Response> The service response message type
 */
@FunctionalInterface
public interface ROS2ServiceCallback<Request extends ROS2Message<Request>, Response extends ROS2Message<Response>>
{
   /**
    * Handle a service request and generate a response.
    * <p>
    * This method is called each time a service request is received. The implementation
    * should read data from the {@code request} parameter and populate the {@code response}
    * parameter with the appropriate reply.
    * <p>
    * The response object is pre-allocated and should be filled in-place. Do not create
    * a new response instance.
    *
    * @param request  The incoming service request (read-only)
    * @param response The response object to be filled and sent back to the client
    */
   void handleRequest(Request request, Response response);
}
