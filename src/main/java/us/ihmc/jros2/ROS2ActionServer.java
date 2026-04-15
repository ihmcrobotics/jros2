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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A ROS 2-compatible action server for receiving action goals and sending results and feedback.
 * <p>
 * Actions are used for long-running tasks that provide periodic feedback updates. An action
 * server receives goals from action clients, executes them asynchronously, sends periodic
 * feedback, and returns a final result.
 * <p>
 * The server runs a background thread that continuously polls for incoming goals
 * and invokes the user-provided {@link ROS2ActionGoalCallback} to execute them.
 * <p>
 * Thread-safe.
 *
 * @param <Goal>     The action goal message type
 * @param <Result>   The action result message type
 * @param <Feedback> The action feedback message type
 */
public class ROS2ActionServer<Goal extends ROS2Message<Goal>, Result extends ROS2Message<Result>, Feedback extends ROS2Message<Feedback>>
      implements Closeable
{
   /*
    * Action identification
    */
   /**
    * The name of the action this server advertises.
    */
   private final String actionName;

   /*
    * ROS 2 primitives
    */
   /**
    * Subscription for receiving action goals.
    */
   private final ROS2Subscription<Goal> goalSubscription;
   /**
    * Publisher for sending action results.
    */
   private final ROS2Publisher<Result> resultPublisher;
   /**
    * Publisher for sending action feedback.
    */
   private final ROS2Publisher<Feedback> feedbackPublisher;
   /**
    * Quality-of-service profile used for all topics.
    */
   private final ROS2QoSProfile qosProfile;

   /*
    * Callback
    */
   /**
    * User-provided callback to handle incoming goals and generate results/feedback.
    */
   private final ROS2ActionGoalCallback<Goal, Result, Feedback> goalCallback;
   /**
    * Result message type class, used to create new result instances.
    */
   private final Class<Result> resultType;
   /**
    * Feedback message type class, used to create new feedback instances.
    */
   private final Class<Feedback> feedbackType;

   /*
    * Threading
    */
   /**
    * Executor service for running the goal handling loop in a background thread.
    */
   private final ExecutorService executorService;
   /**
    * Atomic flag indicating whether the goal handling loop should continue running.
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
    * Package-private constructor. Use {@link ROS2Node#createActionServer} to create instances.
    *
    * @param node             The ROS 2 node managing this action server
    * @param actionName       The name of the action to advertise
    * @param goalTopic        The topic for receiving action goals
    * @param resultTopic      The topic for sending action results
    * @param feedbackTopic    The topic for sending action feedback
    * @param goalCallback     The callback to invoke for each incoming goal
    * @param resultType       The result message type class
    * @param feedbackType     The feedback message type class
    * @param qosProfile       The quality-of-service profile for the action
    */
   ROS2ActionServer(ROS2Node node,
                    String actionName,
                    ROS2Topic<Goal> goalTopic,
                    ROS2Topic<Result> resultTopic,
                    ROS2Topic<Feedback> feedbackTopic,
                    ROS2ActionGoalCallback<Goal, Result, Feedback> goalCallback,
                    Class<Result> resultType,
                    Class<Feedback> feedbackType,
                    ROS2QoSProfile qosProfile)
   {
      this.actionName = actionName;
      this.qosProfile = qosProfile;
      this.goalSubscription = node.createSubscription(goalTopic, qosProfile);
      this.resultPublisher = node.createPublisher(resultTopic, qosProfile);
      this.feedbackPublisher = node.createPublisher(feedbackTopic, qosProfile);
      this.goalCallback = goalCallback;
      this.resultType = resultType;
      this.feedbackType = feedbackType;
      this.executorService = Executors.newCachedThreadPool();
      this.closeLock = new ReentrantReadWriteLock(true);
      this.running = new AtomicBoolean(true);
      this.closed = false;

      // Start background thread to handle goals
      executorService.submit(this::handleGoals);
   }

   /**
    * Background thread loop that continuously polls for incoming goals.
    * <p>
    * For each goal received, spawns a new thread to execute it asynchronously,
    * allowing multiple goals to be processed concurrently.
    */
   private void handleGoals()
   {
      while (running.get())
      {
         try
         {
            Goal goal = goalSubscription.read();
            if (goal != null)
            {
               // Execute goal in separate thread to allow concurrent goal execution
               executorService.submit(() -> executeGoal(goal));
            }
            else
            {
               // No data available, sleep briefly to avoid busy-waiting
               Thread.sleep(1);
            }
         }
         catch (Exception e)
         {
            if (running.get())
            {
               jros2.logError("Error handling action goal for " + actionName, e);
            }
         }
      }
   }

   /**
    * Execute a single goal by invoking the user callback.
    * <p>
    * The callback can publish feedback messages during execution and must
    * fill in the result before returning.
    *
    * @param goal The goal to execute
    */
   private void executeGoal(Goal goal)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed && goalCallback != null)
         {
            // Create result and feedback instances
            Result result = ROS2Message.createInstance(resultType);
            if (result == null)
            {
               jros2.logError("Failed to create result instance for action: " + actionName, null);
               return;
            }

            // Create feedback publisher helper
            FeedbackPublisher feedbackPub = new FeedbackPublisher();

            // Invoke callback to execute the goal
            goalCallback.execute(goal, result, feedbackPub);

            // Send final result
            resultPublisher.publish(result);
         }
      }
      catch (Exception e)
      {
         jros2.logError("Error executing action goal for " + actionName, e);
      }
      finally
      {
         closeLock.readLock().unlock();
      }
   }

   /**
    * Helper class for publishing feedback messages during goal execution.
    */
   private class FeedbackPublisher implements ROS2ActionGoalCallback.FeedbackPublisher<Feedback>
   {
      @Override
      public void publishFeedback(Feedback feedback)
      {
         if (!closed && feedback != null)
         {
            feedbackPublisher.publish(feedback);
         }
      }

      @Override
      public Feedback createFeedback()
      {
         return ROS2Message.createInstance(feedbackType);
      }
   }

   /**
    * Get the name of the action this server advertises.
    *
    * @return The action name
    */
   public String getActionName()
   {
      return actionName;
   }

   /**
    * Get the quality-of-service profile used by this action server.
    *
    * @return The QoS profile
    */
   public ROS2QoSProfile getQoSProfile()
   {
      return qosProfile;
   }

   /**
    * Close this action server and release resources.
    * <p>
    * Stops the goal handling loop, shuts down the executor service,
    * and closes the underlying subscription and publishers.
    * <p>
    * For internal use only. This is called by {@link ROS2Node#destroyActionServer}.
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
            executorService.shutdown();
            goalSubscription.close(fastddsParticipant);
            resultPublisher.close(fastddsParticipant);
            feedbackPublisher.close(fastddsParticipant);
            closed = true;
         }
      }
      finally
      {
         closeLock.writeLock().unlock();
      }
   }

   /**
    * Do not call directly. Use {@link ROS2Node#destroyActionServer} instead.
    *
    * @throws UnsupportedOperationException always
    */
   @Override
   public void close()
   {
      throw new UnsupportedOperationException("Use ROS2Node.destroyActionServer() instead");
   }
}
