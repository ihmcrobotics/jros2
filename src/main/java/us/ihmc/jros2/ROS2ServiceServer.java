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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A ROS 2-compatible service server for receiving service requests and sending responses.
 * <p>
 * Service servers implement the request-reply pattern using two DDS topics:
 * <ul>
 *    <li>Request topic: {@code serviceName + "Request"}</li>
 *    <li>Response topic: {@code serviceName + "Response"}</li>
 * </ul>
 * <p>
 * The server runs a background thread that continuously polls for incoming requests
 * and invokes the user-provided {@link ROS2ServiceCallback} to generate responses.
 * <p>
 * Thread-safe.
 *
 * @param <Request>  The service request message type
 * @param <Response> The service response message type
 */
public class ROS2ServiceServer<Request extends ROS2Message<Request>, Response extends ROS2Message<Response>> implements Closeable
{
   /*
    * Service identification
    */
   /**
    * The name of the service this server advertises.
    */
   private final String serviceName;

   /*
    * ROS 2 primitives
    */
   /**
    * Subscription for receiving service requests.
    */
   private final ROS2Subscription<Request> requestSubscription;
   /**
    * Publisher for sending service responses.
    */
   private final ROS2Publisher<Response> responsePublisher;
   /**
    * Quality-of-service profile used for both request subscription and response publisher.
    */
   private final ROS2QoSProfile qosProfile;

   /*
    * Callback
    */
   /**
    * User-provided callback to handle incoming requests and generate responses.
    */
   private final ROS2ServiceCallback<Request, Response> callback;
   /**
    * Response message type class, used to create new response instances.
    */
   private final Class<Response> responseType;

   /*
    * Threading
    */
   /**
    * Executor service for running the request handling loop in a background thread.
    */
   private final ExecutorService executorService;
   /**
    * Atomic flag indicating whether the request handling loop should continue running.
    */
   private final AtomicBoolean running;

   /*
    * Locks
    */
   /**
    * Read-write lock for thread-safe closure of this server.
    */
   private final ReadWriteLock closeLock;
   /**
    * Flag indicating whether this server has been closed.
    */
   private boolean closed;

   /**
    * Package-private constructor. Use {@link ROS2Node#createServiceServer} to create instances.
    *
    * @param node          The ROS 2 node managing this service server
    * @param serviceName   The name of the service to advertise
    * @param requestTopic  The topic for receiving service requests
    * @param responseTopic The topic for sending service responses
    * @param callback      The callback to invoke for each incoming request
    * @param responseType  The response message type class
    * @param qosProfile    The quality-of-service profile for the service
    */
   ROS2ServiceServer(ROS2Node node,
                     String serviceName,
                     ROS2Topic<Request> requestTopic,
                     ROS2Topic<Response> responseTopic,
                     ROS2ServiceCallback<Request, Response> callback,
                     Class<Response> responseType,
                     ROS2QoSProfile qosProfile)
   {
      this.serviceName = serviceName;
      this.qosProfile = qosProfile;
      this.requestSubscription = node.createSubscription(requestTopic, qosProfile);
      this.responsePublisher = node.createPublisher(responseTopic, qosProfile);
      this.callback = callback;
      this.responseType = responseType;
      this.executorService = Executors.newSingleThreadExecutor();
      this.closeLock = new ReentrantReadWriteLock(true);
      this.running = new AtomicBoolean(true);
      this.closed = false;

      // Start background thread to handle requests
      executorService.submit(this::handleRequests);
   }

   /**
    * Background thread loop that continuously polls for incoming requests.
    * <p>
    * For each request received, creates a new response instance, invokes the callback
    * to fill the response, and publishes it back to the client.
    */
   private void handleRequests()
   {
      while (running.get() && !Thread.currentThread().isInterrupted())
      {
         try
         {
            Request request = requestSubscription.read();
            if (request != null)
            {
               closeLock.readLock().lock();
               try
               {
                  if (!closed && callback != null)
                  {
                     // Create response instance
                     Response response = ROS2Message.createInstance(responseType);
                     if (response != null)
                     {
                        // Invoke callback to fill response
                        callback.handleRequest(request, response);

                        // Send response back
                        responsePublisher.publish(response);
                     }
                  }
               }
               finally
               {
                  closeLock.readLock().unlock();
               }
            }
            else
            {
               // No data available, sleep briefly to avoid busy-waiting
               Thread.sleep(1);
            }
         }
         catch (InterruptedException e)
         {
            // Thread was interrupted, exit gracefully
            Thread.currentThread().interrupt();
            break;
         }
         catch (Exception e)
         {
            if (running.get() && !Thread.currentThread().isInterrupted())
            {
               jros2.logError("Error handling service request for " + serviceName, e);
            }
         }
      }
   }

   /**
    * Get the name of the service this server advertises.
    *
    * @return The service name
    */
   public String getServiceName()
   {
      return serviceName;
   }

   /**
    * Get the quality-of-service profile used by this service server.
    *
    * @return The QoS profile
    */
   public ROS2QoSProfile getQoSProfile()
   {
      return qosProfile;
   }

   /**
    * Close this service server and release resources.
    * <p>
    * Stops the request handling loop, shuts down the executor service,
    * and closes the underlying subscription and publisher.
    * <p>
    * For internal use only. This is called by {@link ROS2Node#destroyServiceServer}.
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
            running.set(false);
            closed = true;
            executorService.shutdownNow();
            try
            {
               if (!executorService.awaitTermination(2, TimeUnit.SECONDS))
               {
                  jros2.logError("ExecutorService did not terminate in time for service server: " + serviceName, null);
               }
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
            requestSubscription.close(fastddsParticipant);
            responsePublisher.close(fastddsParticipant);
         }
      }
      finally
      {
         closeLock.writeLock().unlock();
      }
   }

   /**
    * Do not call directly. Use {@link ROS2Node#destroyServiceServer} instead.
    *
    * @throws UnsupportedOperationException always
    */
   @Override
   public void close()
   {
      throw new UnsupportedOperationException("Use ROS2Node.destroyServiceServer() instead");
   }
}
