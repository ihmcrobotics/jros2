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

import java.util.function.Function;

/**
 * A ROS 2 service server. Requests are read from the DDS topic {@code rq<service name>Request} and responses written to
 * {@code rr<service name>Reply}, following the same request/response correlation as rmw_fastrtps, so it can serve
 * clients of ROS 2 nodes using Fast-DDS.
 * <p>
 * Create with {@link ROS2Node#createService(String, Class, Class, ROS2ServiceCallback)}.
 */
public class ROS2Service<Req extends ROS2Message<Req>, Res extends ROS2Message<Res>>
{
   /**
    * Per the RTPS specification (9.3.1.2) reader entity kinds have this bit set and writer entity kinds do not.
    */
   private static final byte ENTITY_KIND_IS_READER_BIT = 0x04;
   /**
    * Maximum time to wait for the response writer to match a newly discovered client before replying.
    */
   private static final long CLIENT_DISCOVERY_TIMEOUT_MS = 1000;

   private final String serviceName;
   private final Class<Req> requestType;
   private final Class<Res> responseType;
   private final ROS2ServiceCallback<Req, Res> callback;

   private final ROS2Publisher<Res> responsePublisher;
   private final ROS2Subscription<Req> requestSubscription;

   private final Req request;
   private final Res response;
   private final Res emptyResponse;
   private final SampleInfo sampleInfo;
   private final SampleIdentity requestIdentity;
   private final SampleIdentity relatedIdentity;

   ROS2Service(String serviceName,
               Class<Req> requestType,
               Class<Res> responseType,
               ROS2ServiceCallback<Req, Res> callback,
               ROS2Publisher<Res> responsePublisher,
               Function<ROS2SubscriptionCallback<Req>, ROS2Subscription<Req>> requestSubscriptionFactory)
   {
      this.serviceName = serviceName;
      this.requestType = requestType;
      this.responseType = responseType;
      this.callback = callback;
      this.responsePublisher = responsePublisher;

      request = ROS2Message.createInstance(requestType);
      response = ROS2Message.createInstance(responseType);
      emptyResponse = ROS2Message.createInstance(responseType);
      sampleInfo = new SampleInfo();
      requestIdentity = new SampleIdentity();
      relatedIdentity = new SampleIdentity();

      // Created last so requests are not delivered before this object is initialized
      requestSubscription = requestSubscriptionFactory.apply(this::onRequestAvailable);
   }

   private void onRequestAvailable(ROS2MessageReader<Req> reader)
   {
      synchronized (request)
      {
         if (!reader.read(request, sampleInfo))
            return;

         // The request id is the sample identity of the request. When the client sets the related sample identity, its
         // writer GUID is the GUID of the client's response reader, which then replaces the request writer GUID (rmw_fastrtps).
         sampleInfo.getSampleIdentity(requestIdentity);
         sampleInfo.getRelatedSampleIdentity(relatedIdentity);
         boolean relatedIsResponseReader = !isZero(relatedIdentity.getWriterGuid());
         if (relatedIsResponseReader)
         {
            requestIdentity.getWriterGuid().set(relatedIdentity.getWriterGuid());
         }

         response.set(emptyResponse);
         try
         {
            callback.onRequest(request, response);
         }
         catch (Throwable e)
         {
            jros2.logError("Exception in service callback for " + serviceName + ", no response sent", e);
            return;
         }

         // Work around the discovery race: the request may arrive before our response writer has matched the client's
         // response reader, in which case the response would be lost.
         if (relatedIsResponseReader && (requestIdentity.getWriterGuid().getValue()[15] & ENTITY_KIND_IS_READER_BIT) != 0)
         {
            responsePublisher.waitForSubscription(CLIENT_DISCOVERY_TIMEOUT_MS);
         }

         responsePublisher.publish(response, requestIdentity, null);
      }
   }

   private static boolean isZero(Guid guid)
   {
      for (byte b : guid.getValue())
      {
         if (b != 0)
            return false;
      }
      return true;
   }

   ROS2Publisher<Res> getResponsePublisher()
   {
      return responsePublisher;
   }

   ROS2Subscription<Req> getRequestSubscription()
   {
      return requestSubscription;
   }

   void closeResources()
   {
      sampleInfo.close();
   }

   public String getServiceName()
   {
      return serviceName;
   }

   public Class<Req> getRequestType()
   {
      return requestType;
   }

   public Class<Res> getResponseType()
   {
      return responseType;
   }
}
