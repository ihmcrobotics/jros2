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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A ROS 2 service client. Requests are written to the DDS topic {@code rq<service name>Request} and responses read from
 * {@code rr<service name>Reply}, following the same request/response correlation as rmw_fastrtps, so it can call
 * services of ROS 2 nodes using Fast-DDS.
 * <p>
 * Create with {@link ROS2Node#createClient(String, Class, Class)}.
 */
public class ROS2Client<Req extends ROS2Message<Req>, Res extends ROS2Message<Res>>
{
   private final String serviceName;
   private final Class<Req> requestType;
   private final Class<Res> responseType;

   private final ROS2Publisher<Req> requestPublisher;
   private final ROS2Subscription<Res> responseSubscription;
   private final Guid requestWriterGuid;
   private final Guid responseReaderGuid;

   /**
    * Requests waiting for a response, by the sequence number of the request sample. Guarded by itself.
    */
   private final Map<Long, CompletableFuture<Res>> pendingRequests;
   /**
    * Responses received while a request is being written, before its future is registered. With Fast-DDS intraprocess
    * delivery the request and its response are delivered on the writing thread, inside the write call. Guarded by
    * {@link #pendingRequests}.
    */
   private final Map<Long, Res> responsesDuringWrite;
   private boolean writing;
   private final SampleIdentity relatedIdentity;
   private final SampleIdentity writtenIdentity;
   private final SampleInfo sampleInfo;
   private final SampleIdentity responseRelatedIdentity;
   private boolean closed;

   ROS2Client(String serviceName,
              Class<Req> requestType,
              Class<Res> responseType,
              ROS2Publisher<Req> requestPublisher,
              Function<ROS2SubscriptionCallback<Res>, ROS2Subscription<Res>> responseSubscriptionFactory)
   {
      this.serviceName = serviceName;
      this.requestType = requestType;
      this.responseType = responseType;
      this.requestPublisher = requestPublisher;

      pendingRequests = new HashMap<>();
      responsesDuringWrite = new HashMap<>();
      relatedIdentity = new SampleIdentity();
      writtenIdentity = new SampleIdentity();
      sampleInfo = new SampleInfo();
      responseRelatedIdentity = new SampleIdentity();
      closed = false;

      responseSubscription = responseSubscriptionFactory.apply(this::onResponseAvailable);

      requestWriterGuid = new Guid();
      requestWriterGuid.set(requestPublisher.getGuid());
      responseReaderGuid = new Guid();
      responseReaderGuid.set(responseSubscription.getGuid());
   }

   /**
    * Send a request to the service. The returned future completes with the response, or is cancelled if this client is closed
    * first. It never completes if the service does not answer; use {@link CompletableFuture#get(long, TimeUnit)} or
    * {@link CompletableFuture#orTimeout(long, TimeUnit)} to bound the wait.
    *
    * @param request the request to send
    * @return the response future, completed exceptionally if the request could not be written
    */
   public CompletableFuture<Res> sendRequest(Req request)
   {
      CompletableFuture<Res> future = new CompletableFuture<>();

      synchronized (pendingRequests)
      {
         if (closed)
         {
            future.completeExceptionally(new IllegalStateException("Client for " + serviceName + " is closed"));
            return future;
         }

         // Like rmw_fastrtps, tell the service which reader expects the response
         relatedIdentity.getWriterGuid().set(responseReaderGuid);
         relatedIdentity.setSequenceNumber(SampleIdentity.UNKNOWN_SEQUENCE_NUMBER);

         // Other threads wait for the lock, so a response processed before the future is registered can only come from this
         // thread, during the write (intraprocess delivery); it is kept in responsesDuringWrite
         boolean written;
         writing = true;
         try
         {
            written = requestPublisher.publish(request, relatedIdentity, writtenIdentity);
         }
         finally
         {
            writing = false;
         }

         if (written)
         {
            Res earlyResponse = responsesDuringWrite.remove(writtenIdentity.getSequenceNumber());
            if (earlyResponse != null)
            {
               future.complete(earlyResponse);
            }
            else
            {
               pendingRequests.put(writtenIdentity.getSequenceNumber(), future);
            }
         }
         else
         {
            future.completeExceptionally(new IllegalStateException("Failed to write request for " + serviceName));
         }
         responsesDuringWrite.clear();
      }

      return future;
   }

   private void onResponseAvailable(ROS2MessageReader<Res> reader)
   {
      Res response = ROS2Message.createInstance(responseType);
      if (!reader.read(response, sampleInfo))
         return;

      CompletableFuture<Res> future;
      synchronized (pendingRequests)
      {
         sampleInfo.getRelatedSampleIdentity(responseRelatedIdentity);

         // Responses to other clients of the same service are also received; only accept the ones addressed to this client
         Guid related = responseRelatedIdentity.getWriterGuid();
         if (!related.equals(responseReaderGuid) && !related.equals(requestWriterGuid))
            return;

         future = pendingRequests.remove(responseRelatedIdentity.getSequenceNumber());
         if (future == null && writing)
         {
            responsesDuringWrite.put(responseRelatedIdentity.getSequenceNumber(), response);
            return;
         }
      }

      if (future != null)
      {
         future.complete(response);
      }
   }

   /**
    * @return true if a service has been matched for both the request and the response topics
    */
   public boolean isServiceAvailable()
   {
      return requestPublisher.getPublicationMatchedStatus() > 0 && responseSubscription.getSubscriptionMatchedStatus() > 0;
   }

   /**
    * Block until a service is available or the timeout elapses.
    *
    * @param timeoutMs maximum time to wait in milliseconds
    * @return true if the service is available
    */
   public boolean waitForService(long timeoutMs)
   {
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);

      if (!requestPublisher.waitForSubscription(timeoutMs))
         return false;

      long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
      return responseSubscription.waitForPublisher(Math.max(remainingMs, 0)) && isServiceAvailable();
   }

   /**
    * @return the number of requests sent that have not received a response yet
    */
   public int getPendingRequestCount()
   {
      synchronized (pendingRequests)
      {
         return pendingRequests.size();
      }
   }

   ROS2Publisher<Req> getRequestPublisher()
   {
      return requestPublisher;
   }

   ROS2Subscription<Res> getResponseSubscription()
   {
      return responseSubscription;
   }

   void closeResources()
   {
      synchronized (pendingRequests)
      {
         closed = true;
         for (CompletableFuture<Res> future : pendingRequests.values())
         {
            future.completeExceptionally(new CancellationException("Client for " + serviceName + " was closed"));
         }
         pendingRequests.clear();
      }
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
