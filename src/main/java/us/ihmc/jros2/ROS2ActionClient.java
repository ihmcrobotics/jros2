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
 * A ROS 2-compatible action client for sending action goals and receiving results and feedback.
 * <p>
 * Actions are used for long-running tasks that provide periodic feedback updates. An action
 * client sends a goal to an action server and receives feedback during execution and a
 * result when complete.
 * <p>
 * Thread-safe.
 *
 * @param <Goal>     The action goal message type
 * @param <Result>   The action result message type
 * @param <Feedback> The action feedback message type
 */
public class ROS2ActionClient<Goal extends ROS2Message<Goal>, Result extends ROS2Message<Result>, Feedback extends ROS2Message<Feedback>>
      implements Closeable
{
   /*
    * Action identification
    */
   /**
    * The name of the action this client calls.
    */
   private final String actionName;

   /*
    * ROS 2 primitives
    */
   /**
    * Publisher for sending action goals.
    */
   private final ROS2Publisher<Goal> goalPublisher;
   /**
    * Subscription for receiving action results.
    */
   private final ROS2Subscription<Result> resultSubscription;
   /**
    * Subscription for receiving action feedback.
    */
   private final ROS2Subscription<Feedback> feedbackSubscription;
   /**
    * Quality-of-service profile used for all topics.
    */
   private final ROS2QoSProfile qosProfile;

   /*
    * Threading
    */
   /**
    * Executor service for handling asynchronous goal-result operations.
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
    * Lock for server discovery synchronization.
    */
   private final Object discoveryLock;
   /**
    * Flag indicating whether an action server has been discovered.
    */
   private boolean serverDiscovered;

   /**
    * Package-private constructor. Use {@link ROS2Node#createActionClient} to create instances.
    *
    * @param node             The ROS 2 node managing this action client
    * @param actionName       The name of the action to call
    * @param goalTopic        The topic for publishing action goals
    * @param resultTopic      The topic for receiving action results
    * @param feedbackTopic    The topic for receiving action feedback
    * @param qosProfile       The quality-of-service profile for the action
    */
   ROS2ActionClient(ROS2Node node,
                    String actionName,
                    ROS2Topic<Goal> goalTopic,
                    ROS2Topic<Result> resultTopic,
                    ROS2Topic<Feedback> feedbackTopic,
                    ROS2QoSProfile qosProfile)
   {
      this.actionName = actionName;
      this.qosProfile = qosProfile;
      this.goalPublisher = node.createPublisher(goalTopic, qosProfile);
      this.resultSubscription = node.createSubscription(resultTopic, qosProfile);
      this.feedbackSubscription = node.createSubscription(feedbackTopic, qosProfile);
      this.executorService = Executors.newCachedThreadPool();
      this.closeLock = new ReentrantReadWriteLock(true);
      this.closed = false;
      this.discoveryLock = new Object();
      this.serverDiscovered = false;

      // Set up discovery callback
      resultSubscription.setOnSubscriptionMatchedCallback(() ->
      {
         synchronized (discoveryLock)
         {
            serverDiscovered = true;
            discoveryLock.notifyAll();
         }
      });

      // Check if server is already matched
      synchronized (discoveryLock)
      {
         if (resultSubscription.getSubscriptionMatchedStatus() > 0 && goalPublisher.getPublicationMatchedStatus() > 0)
         {
            serverDiscovered = true;
         }
      }
   }

   /**
    * Wait for an action server to be discovered.
    * <p>
    * This method blocks until an action server is discovered or the timeout expires.
    * Bidirectional discovery is ensured: both the client's goal publisher and result
    * subscription must be matched to the server.
    *
    * @param timeoutMs Timeout in milliseconds to wait for server discovery
    * @return true if a server was discovered, false if timeout occurred
    */
   public boolean waitForServer(long timeoutMs)
   {
      boolean discovered = false;
      long startTime = System.nanoTime();
      long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);

      synchronized (discoveryLock)
      {
         boolean timedOut = false;
         while (!closed && !discovered && !timedOut)
         {
            long elapsedNanos = System.nanoTime() - startTime;
            if (elapsedNanos >= timeoutNanos)
            {
               timedOut = true;
            }
            else
            {
               // Check both directions: result subscription and goal publisher
               if (resultSubscription.getSubscriptionMatchedStatus() > 0 && goalPublisher.getPublicationMatchedStatus() > 0)
               {
                  serverDiscovered = true;
                  discovered = true;
               }
               else
               {
                  long remainingNanos = timeoutNanos - elapsedNanos;
                  long remainingMs = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
                  if (remainingMs <= 0)
                  {
                     timedOut = true;
                  }
                  else
                  {
                     try
                     {
                        discoveryLock.wait(Math.min(remainingMs, 10));
                     }
                     catch (InterruptedException e)
                     {
                        Thread.currentThread().interrupt();
                        timedOut = true;
                     }
                  }
               }
            }
         }
      }

      return discovered;
   }

   /**
    * Send an action goal synchronously and wait for the result.
    * <p>
    * This method blocks until a result is received or the timeout expires.
    *
    * @param goal      The action goal to send
    * @param timeoutMs Timeout in milliseconds to wait for a result
    * @return The action result, or null if timeout occurred
    */
   public Result sendGoalSync(Goal goal, long timeoutMs)
   {
      return sendGoalSync(goal, null, timeoutMs);
   }

   /**
    * Send an action goal synchronously with feedback callback.
    * <p>
    * This method blocks until a result is received or the timeout expires.
    * Feedback messages are delivered to the callback as they arrive.
    *
    * @param goal             The action goal to send
    * @param feedbackCallback Optional callback for receiving feedback during execution
    * @param timeoutMs        Timeout in milliseconds to wait for a result
    * @return The action result, or null if timeout occurred
    */
   public Result sendGoalSync(Goal goal, ROS2ActionFeedbackCallback<Feedback> feedbackCallback, long timeoutMs)
   {
      CompletableFuture<Result> future = sendGoalAsync(goal, feedbackCallback);
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
    * Send an action goal asynchronously.
    * <p>
    * This method returns immediately with a {@link CompletableFuture} that will be completed
    * when a result is received.
    *
    * @param goal The action goal to send
    * @return A {@link CompletableFuture} that will contain the result when available
    */
   public CompletableFuture<Result> sendGoalAsync(Goal goal)
   {
      return sendGoalAsync(goal, null);
   }

   /**
    * Send an action goal asynchronously with feedback callback.
    * <p>
    * This method returns immediately with a {@link CompletableFuture} that will be completed
    * when a result is received. Feedback messages are delivered to the callback as they arrive.
    *
    * @param goal             The action goal to send
    * @param feedbackCallback Optional callback for receiving feedback during execution
    * @return A {@link CompletableFuture} that will contain the result when available
    */
   public CompletableFuture<Result> sendGoalAsync(Goal goal, ROS2ActionFeedbackCallback<Feedback> feedbackCallback)
   {
      CompletableFuture<Result> future = new CompletableFuture<>();

      // Publish the goal
      goalPublisher.publish(goal);

      // Wait for result in background, optionally processing feedback
      executorService.submit(() -> {
         try
         {
            long startTime = System.nanoTime();
            long timeoutNanos = TimeUnit.SECONDS.toNanos(30); // 30 second timeout
            Result result = null;

            while (System.nanoTime() - startTime < timeoutNanos && !closed)
            {
               // Check for feedback if callback provided
               if (feedbackCallback != null)
               {
                  Feedback feedback = feedbackSubscription.read();
                  if (feedback != null)
                  {
                     feedbackCallback.onFeedback(feedback);
                  }
               }

               // Check for result
               result = resultSubscription.read();
               if (result != null)
               {
                  break;
               }

               Thread.sleep(1);
            }

            future.complete(result);
         }
         catch (Exception e)
         {
            future.completeExceptionally(e);
         }
      });

      return future;
   }

   /**
    * Get the name of the action this client calls.
    *
    * @return The action name
    */
   public String getActionName()
   {
      return actionName;
   }

   /**
    * Get the quality-of-service profile used by this action client.
    *
    * @return The QoS profile
    */
   public ROS2QoSProfile getQoSProfile()
   {
      return qosProfile;
   }

   /**
    * Close this action client and release resources.
    * <p>
    * For internal use only. This is called by {@link ROS2Node#destroyActionClient}.
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
                  jros2.logError("ExecutorService did not terminate in time for action client: " + actionName, null);
               }
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
            goalPublisher.close(fastddsParticipant);
            resultSubscription.close(fastddsParticipant);
            feedbackSubscription.close(fastddsParticipant);
         }
      }
      finally
      {
         closeLock.writeLock().unlock();
      }
   }

   /**
    * Do not call directly. Use {@link ROS2Node#destroyActionClient} instead.
    *
    * @throws UnsupportedOperationException always
    */
   @Override
   public void close()
   {
      throw new UnsupportedOperationException("Use ROS2Node.destroyActionClient() instead");
   }
}
