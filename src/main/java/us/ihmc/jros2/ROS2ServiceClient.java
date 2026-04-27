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

import org.bytedeco.javacpp.Pointer;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A ROS 2-compatible service client for sending service requests and receiving responses.
 * <p>
 * Service clients implement the request-reply pattern using two DDS topics:
 * <ul>
 *    <li>Request topic: {@code serviceName + "Request"}</li>
 *    <li>Response topic: {@code serviceName + "Response"}</li>
 * </ul>
 * <p>
 * Thread-safe.
 *
 * @param <Request>  The service request message type
 * @param <Response> The service response message type
 */
public class ROS2ServiceClient<Request extends ROS2Message<Request>, Response extends ROS2Message<Response>> implements Closeable
{
   /*
    * Service identification
    */
   /**
    * The name of the service this client calls.
    */
   private final String serviceName;

   /*
    * ROS 2 primitives
    */
   /**
    * Publisher for sending service requests.
    */
   private final ROS2Publisher<Request> requestPublisher;
   /**
    * Subscription for receiving service responses.
    */
   private final ROS2Subscription<Response> responseSubscription;
   /**
    * Quality-of-service profile used for both request publisher and response subscription.
    */
   private final ROS2QoSProfile qosProfile;

   /*
    * Threading
    */
   /**
    * Executor service for handling asynchronous request-response operations.
    */
   private final ExecutorService executorService;

   /*
    * Locks
    */
   /**
    * Read-write lock for thread-safe closure of this client.
    */
   private final ReadWriteLock closeLock;
   /**
    * Flag indicating whether this client has been closed.
    */
   private boolean closed;

   /*
    * Discovery
    */
   /**
    * Flag indicating whether a service server has been discovered.
    */
   private volatile boolean serverDiscovered;
   private final Object discoveryLock = new Object();

   /**
    * Package-private constructor. Use {@link ROS2Node#createServiceClient} to create instances.
    *
    * @param node          The ROS 2 node managing this service client
    * @param serviceName   The name of the service to call
    * @param requestTopic  The topic for publishing service requests
    * @param responseTopic The topic for receiving service responses
    * @param qosProfile    The quality-of-service profile for the service
    */
   ROS2ServiceClient(ROS2Node node, String serviceName, ROS2Topic<Request> requestTopic, ROS2Topic<Response> responseTopic, ROS2QoSProfile qosProfile)
   {
      this.serviceName = serviceName;
      this.qosProfile = qosProfile;
      this.requestPublisher = node.createPublisher(requestTopic, qosProfile);
      this.responseSubscription = node.createSubscription(responseTopic, qosProfile);
      this.executorService = Executors.newCachedThreadPool();
      this.closeLock = new ReentrantReadWriteLock(true);
      this.closed = false;
      this.serverDiscovered = false;

      // Set up discovery callback to detect when service server becomes available
      responseSubscription.setOnSubscriptionMatchedCallback(() ->
      {
         synchronized (discoveryLock)
         {
            serverDiscovered = true;
            discoveryLock.notifyAll();
         }
      });

      // Check if server is already matched (handles case where server existed before client)
      // If a publisher matched during subscription creation but before callback was set,
      // the callback won't have fired, so we need to check the current match status
      synchronized (discoveryLock)
      {
         if (responseSubscription.getSubscriptionMatchedStatus() > 0 && requestPublisher.getPublicationMatchedStatus() > 0)
         {
            serverDiscovered = true;
         }
      }
   }

   /**
    * Wait for a service server to become available.
    * <p>
    * Bidirectional discovery is ensured: both the client's request publisher and response
    * subscription must be matched to the server.
    *
    * @param timeoutMs Maximum time to wait in milliseconds
    * @return true if server was discovered within timeout, false otherwise
    */
   public boolean waitForServer(long timeoutMs)
   {
      long startTime = System.nanoTime();
      long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);

      synchronized (discoveryLock)
      {
         while (!closed)
         {
            long elapsedNanos = System.nanoTime() - startTime;
            if (elapsedNanos >= timeoutNanos)
            {
               return false;
            }

            // Check both directions: response subscription and request publisher
            if (responseSubscription.getSubscriptionMatchedStatus() > 0 && requestPublisher.getPublicationMatchedStatus() > 0)
            {
               serverDiscovered = true;
               return true;
            }

            long remainingNanos = timeoutNanos - elapsedNanos;
            long remainingMs = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
            if (remainingMs <= 0)
            {
               return false;
            }

            try
            {
               discoveryLock.wait(Math.min(remainingMs, 10));
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
               return false;
            }
         }

         return false;
      }
   }

   /**
    * Send a service request synchronously and wait for a response.
    * <p>
    * This method blocks until a response is received or the timeout expires.
    *
    * @param request   The service request message
    * @param timeoutMs Timeout in milliseconds to wait for a response
    * @return The service response, or null if timeout occurred or an error occurred
    */
   public Response sendRequestSync(Request request, long timeoutMs)
   {
      CompletableFuture<Response> future = sendRequestAsync(request);
      try
      {
         return future.get(timeoutMs, TimeUnit.MILLISECONDS);
      }
      catch (Exception e)
      {
         return null;
      }
   }

   /**
    * Send a service request asynchronously.
    * <p>
    * This method returns immediately with a {@link CompletableFuture} that will be completed
    * when a response is received. The response will be waited for up to 5 seconds.
    *
    * @param request The service request message
    * @return A {@link CompletableFuture} that will contain the response when available
    */
   public CompletableFuture<Response> sendRequestAsync(Request request)
   {
      CompletableFuture<Response> future = new CompletableFuture<>();

      // Publish the request
      requestPublisher.publish(request);

      // Wait for response in background
      executorService.submit(() ->
      {
         try
         {
            long startTime = System.nanoTime();
            long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(5000);
            Response response = null;

            while (System.nanoTime() - startTime < timeoutNanos && !closed)
            {
               response = responseSubscription.read();
               if (response != null)
               {
                  break;
               }
               Thread.sleep(1);
            }

            future.complete(response);
         }
         catch (Exception e)
         {
            future.completeExceptionally(e);
         }
      });

      return future;
   }

   /**
    * Get the name of the service this client calls.
    *
    * @return The service name
    */
   public String getServiceName()
   {
      return serviceName;
   }

   /**
    * Get the quality-of-service profile used by this service client.
    *
    * @return The QoS profile
    */
   public ROS2QoSProfile getQoSProfile()
   {
      return qosProfile;
   }

   /**
    * Close this service client and release resources.
    * <p>
    * For internal use only. This is called by {@link ROS2Node#destroyServiceClient}.
    *
    * @param fastddsParticipant The Fast-DDS participant pointer (for cleanup)
    */
   void close(Pointer fastddsParticipant)
   {
      closeLock.writeLock().lock();
      try
      {
         if (!closed)
         {
            closed = true;
            executorService.shutdownNow();
            try
            {
               if (!executorService.awaitTermination(1, TimeUnit.SECONDS))
               {
                  jros2.logError("ExecutorService did not terminate in time for service client: " + serviceName, null);
               }
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
            requestPublisher.close(fastddsParticipant);
            responseSubscription.close(fastddsParticipant);
         }
      }
      finally
      {
         closeLock.writeLock().unlock();
      }
   }

   /**
    * Do not call directly. Use {@link ROS2Node#destroyServiceClient} instead.
    *
    * @throws UnsupportedOperationException always
    */
   @Override
   public void close()
   {
      throw new UnsupportedOperationException("Use ROS2Node.destroyServiceClient() instead");
   }
}
