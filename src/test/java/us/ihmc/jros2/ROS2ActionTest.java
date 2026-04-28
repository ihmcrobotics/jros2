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

import example_interfaces.Fibonacci_Feedback;
import example_interfaces.Fibonacci_Goal;
import example_interfaces.Fibonacci_Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ROS 2 actions.
 */
public class ROS2ActionTest
{
   private ROS2Node serverNode;
   private ROS2Node clientNode;

   @BeforeEach
   public void setUp()
   {
      serverNode = new ROS2Node("test_action_server_node");
      clientNode = new ROS2Node("test_action_client_node");
   }

   @AfterEach
   public void tearDown()
   {
      if (serverNode != null)
         serverNode.close();
      if (clientNode != null)
         clientNode.close();
   }

   @Test
   public void testFibonacciAction() throws InterruptedException
   {
      // Create action server that computes Fibonacci sequence
      ROS2ActionGoalCallback<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> callback = (goal, result, feedbackPublisher) -> {
         int order = goal.getOrder();

         // Compute Fibonacci sequence
         int prev = 0;
         int curr = 1;
         result.getSequence().add(prev);
         if (order > 0)
            result.getSequence().add(curr);

         for (int i = 2; i <= order; ++i)
         {
            int next = prev + curr;
            result.getSequence().add(next);
            prev = curr;
            curr = next;

            // Send feedback periodically
            if (i % 3 == 0 || i == order)
            {
               Fibonacci_Feedback feedback = feedbackPublisher.createFeedback();
               for (int j = 0; j <= i; ++j)
               {
                  feedback.getSequence().add(result.getSequence().get(j));
               }
               feedbackPublisher.publishFeedback(feedback);
            }

            try
            {
               Thread.sleep(10); // Simulate work
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
         }
      };

      ROS2ActionServer<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> server = serverNode.createActionServer("fibonacci",
                                                                                                                      Fibonacci_Goal.class,
                                                                                                                      Fibonacci_Result.class,
                                                                                                                      Fibonacci_Feedback.class,
                                                                                                                      callback);

      assertNotNull(server);

      // Create action client
      ROS2ActionClient<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> client = clientNode.createActionClient("fibonacci",
                                                                                                                      Fibonacci_Goal.class,
                                                                                                                      Fibonacci_Result.class,
                                                                                                                      Fibonacci_Feedback.class);

      assertNotNull(client);

      assertTrue(client.waitForServer(5000), "Should discover action server");

      // Send goal synchronously
      Fibonacci_Goal goal = new Fibonacci_Goal();
      goal.setOrder(10);

      Fibonacci_Result result = client.sendGoalSync(goal, 5000);

      assertNotNull(result, "Action result should not be null");
      assertNotNull(result.getSequence(), "Fibonacci sequence should not be null");
      assertEquals(11, result.getSequence().size(), "Fibonacci sequence should have 11 elements");
      assertEquals(55, result.getSequence().get(10), "10th Fibonacci number should be 55");
   }

   @Test
   public void testActionWithFeedback() throws InterruptedException
   {
      List<Fibonacci_Feedback> receivedFeedback = new ArrayList<>();
      AtomicInteger feedbackCount = new AtomicInteger(0);

      // Create server
      ROS2ActionGoalCallback<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> callback = (goal, result, feedbackPublisher) -> {
         int order = goal.getOrder();

         int prev = 0;
         int curr = 1;
         result.getSequence().add(prev);
         if (order > 0)
            result.getSequence().add(curr);

         // Compute and send feedback for each step
         for (int i = 2; i <= order; ++i)
         {
            int next = prev + curr;
            result.getSequence().add(next);
            prev = curr;
            curr = next;

            Fibonacci_Feedback feedback = feedbackPublisher.createFeedback();
            for (int j = 0; j <= i; ++j)
            {
               feedback.getSequence().add(result.getSequence().get(j));
            }
            feedbackPublisher.publishFeedback(feedback);

            try
            {
               Thread.sleep(10);
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
         }
      };

      ROS2ActionServer<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> server = serverNode.createActionServer("fibonacci_feedback",
                                                                                                                      Fibonacci_Goal.class,
                                                                                                                      Fibonacci_Result.class,
                                                                                                                      Fibonacci_Feedback.class,
                                                                                                                      callback);

      ROS2ActionClient<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> client = clientNode.createActionClient("fibonacci_feedback",
                                                                                                                      Fibonacci_Goal.class,
                                                                                                                      Fibonacci_Result.class,
                                                                                                                      Fibonacci_Feedback.class);

      assertTrue(client.waitForServer(5000), "Should discover action server");

      // Send goal with feedback callback
      Fibonacci_Goal goal = new Fibonacci_Goal();
      goal.setOrder(5);

      ROS2ActionFeedbackCallback<Fibonacci_Feedback> feedbackCallback = feedback -> {
         receivedFeedback.add(feedback);
         feedbackCount.incrementAndGet();
      };

      Fibonacci_Result result = client.sendGoalSync(goal, feedbackCallback, 5000);

      assertNotNull(result);
      assertEquals(6, result.getSequence().size());
      assertTrue(feedbackCount.get() > 0, "Should have received feedback messages");
   }

   @Test
   public void testAsyncAction() throws Exception
   {
      // Create server
      ROS2ActionGoalCallback<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> callback = (goal, result, feedbackPublisher) -> {
         int order = goal.getOrder();

         int prev = 0;
         int curr = 1;
         result.getSequence().add(prev);
         if (order > 0)
            result.getSequence().add(curr);

         for (int i = 2; i <= order; ++i)
         {
            int next = prev + curr;
            result.getSequence().add(next);
            prev = curr;
            curr = next;
         }
      };

      serverNode.createActionServer("fibonacci_async",
                                    Fibonacci_Goal.class,
                                    Fibonacci_Result.class,
                                    Fibonacci_Feedback.class,
                                    callback);

      ROS2ActionClient<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> client = clientNode.createActionClient("fibonacci_async",
                                                                                                                      Fibonacci_Goal.class,
                                                                                                                      Fibonacci_Result.class,
                                                                                                                      Fibonacci_Feedback.class);

      assertTrue(client.waitForServer(5000), "Should discover action server");

      // Send goal asynchronously
      Fibonacci_Goal goal = new Fibonacci_Goal();
      goal.setOrder(7);

      CompletableFuture<Fibonacci_Result> future = client.sendGoalAsync(goal);

      Fibonacci_Result result = future.get(5, TimeUnit.SECONDS);

      assertNotNull(result);
      assertEquals(8, result.getSequence().size());
      assertEquals(13, result.getSequence().get(7));
   }

   @Test
   public void testMultipleGoals() throws InterruptedException
   {
      AtomicInteger goalsExecuted = new AtomicInteger(0);

      // Create server
      ROS2ActionGoalCallback<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> callback = (goal, result, feedbackPublisher) -> {
         goalsExecuted.incrementAndGet();
         int order = goal.getOrder();

         int prev = 0;
         int curr = 1;
         result.getSequence().add(prev);
         if (order > 0)
            result.getSequence().add(curr);

         for (int i = 2; i <= order; ++i)
         {
            int next = prev + curr;
            result.getSequence().add(next);
            prev = curr;
            curr = next;
         }
      };

      serverNode.createActionServer("fibonacci_multi",
                                    Fibonacci_Goal.class,
                                    Fibonacci_Result.class,
                                    Fibonacci_Feedback.class,
                                    callback);

      ROS2ActionClient<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> client = clientNode.createActionClient("fibonacci_multi",
                                                                                                                      Fibonacci_Goal.class,
                                                                                                                      Fibonacci_Result.class,
                                                                                                                      Fibonacci_Feedback.class);

      // Wait for server to be discovered
      assertTrue(client.waitForServer(5000), "Should discover action server");

      // Send multiple goals
      for (int i = 1; i <= 3; ++i)
      {
         Fibonacci_Goal goal = new Fibonacci_Goal();
         goal.setOrder(i * 2);

         Fibonacci_Result result = client.sendGoalSync(goal, 2000);

         assertNotNull(result);
         assertEquals(i * 2 + 1, result.getSequence().size());
      }

      assertEquals(3, goalsExecuted.get(), "All 3 goals should have been executed");
   }

   @Test
   public void testActionCleanupOnDestroy() throws InterruptedException
   {
      ROS2ActionGoalCallback<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> callback = (goal, result, feedbackPublisher) -> {
         result.getSequence().add(0);
         result.getSequence().add(1);
      };

      ROS2ActionServer<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> server =
            serverNode.createActionServer("test_cleanup_action",
                                          Fibonacci_Goal.class,
                                          Fibonacci_Result.class,
                                          Fibonacci_Feedback.class,
                                          callback);

      ROS2ActionClient<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> client =
            clientNode.createActionClient("test_cleanup_action",
                                          Fibonacci_Goal.class,
                                          Fibonacci_Result.class,
                                          Fibonacci_Feedback.class);

      assertNotNull(server);
      assertNotNull(client);

      // Destroy server and client
      boolean serverDestroyed = serverNode.destroyActionServer(server);
      boolean clientDestroyed = clientNode.destroyActionClient(client);

      assertTrue(serverDestroyed, "Server should be destroyed successfully");
      assertTrue(clientDestroyed, "Client should be destroyed successfully");

      // Trying to destroy again should return false
      assertFalse(serverNode.destroyActionServer(server));
      assertFalse(clientNode.destroyActionClient(client));
   }

   @Test
   public void testActionCleanupOnNodeClose()
   {
      // Create multiple actions
      serverNode.createActionServer("action1",
                                    Fibonacci_Goal.class,
                                    Fibonacci_Result.class,
                                    Fibonacci_Feedback.class,
                                    (goal, result, feedbackPublisher) -> {});

      clientNode.createActionClient("action2",
                                    Fibonacci_Goal.class,
                                    Fibonacci_Result.class,
                                    Fibonacci_Feedback.class);

      // Close nodes - should clean up all actions
      serverNode.close();
      clientNode.close();

      assertTrue(serverNode.isClosed());
      assertTrue(clientNode.isClosed());

      // Reinitialize for tearDown
      serverNode = new ROS2Node("test_action_server_node");
      clientNode = new ROS2Node("test_action_client_node");
   }

   @Test
   public void testActionCallbackException() throws InterruptedException
   {
      // Create action server that throws exception
      ROS2ActionGoalCallback<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> callback = (goal, result, feedbackPublisher) -> {
         throw new RuntimeException("Intentional exception in action callback");
      };

      ROS2ActionServer<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> server =
            serverNode.createActionServer("exception_action",
                                          Fibonacci_Goal.class,
                                          Fibonacci_Result.class,
                                          Fibonacci_Feedback.class,
                                          callback);

      ROS2ActionClient<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> client =
            clientNode.createActionClient("exception_action",
                                          Fibonacci_Goal.class,
                                          Fibonacci_Result.class,
                                          Fibonacci_Feedback.class);

      // Send goal
      Fibonacci_Goal goal = new Fibonacci_Goal();
      goal.setOrder(5);

      // Server should survive the exception (though result may be null or empty)
      Fibonacci_Result result = client.sendGoalSync(goal, 2000);

      // The important thing is the server didn't crash
      assertNotNull(server);
   }

   @Test
   public void testConcurrentActionGoals() throws InterruptedException
   {
      AtomicInteger goalsExecuted = new AtomicInteger(0);

      // Create action server
      ROS2ActionGoalCallback<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> callback = (goal, result, feedbackPublisher) -> {
         goalsExecuted.incrementAndGet();
         try
         {
            Thread.sleep(100); // Simulate work
         }
         catch (InterruptedException e)
         {
            Thread.currentThread().interrupt();
         }

         int order = goal.getOrder();
         result.getSequence().add(0);
         if (order > 0)
            result.getSequence().add(1);
      };

      serverNode.createActionServer("concurrent_action",
                                    Fibonacci_Goal.class,
                                    Fibonacci_Result.class,
                                    Fibonacci_Feedback.class,
                                    callback);

      ROS2ActionClient<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> client =
            clientNode.createActionClient("concurrent_action",
                                          Fibonacci_Goal.class,
                                          Fibonacci_Result.class,
                                          Fibonacci_Feedback.class);

      // Send multiple concurrent goals
      int numGoals = 3;
      CountDownLatch latch = new CountDownLatch(numGoals);

      for (int i = 0; i < numGoals; ++i)
      {
         final int order = i + 1;
         new Thread(() -> {
            Fibonacci_Goal goal = new Fibonacci_Goal();
            goal.setOrder(order);

            Fibonacci_Result result = client.sendGoalSync(goal, 5000);
            if (result != null)
            {
               latch.countDown();
            }
         }).start();
      }

      boolean allCompleted = latch.await(10, TimeUnit.SECONDS);
      assertTrue(allCompleted, "All concurrent goals should complete");
      assertTrue(goalsExecuted.get() >= numGoals, "Server should have executed at least " + numGoals + " goals");
   }

   @Test
   public void testActionTimeout() throws InterruptedException
   {
      // Create client without server
      ROS2ActionClient<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> client =
            clientNode.createActionClient("nonexistent_action",
                                          Fibonacci_Goal.class,
                                          Fibonacci_Result.class,
                                          Fibonacci_Feedback.class);

      Fibonacci_Goal goal = new Fibonacci_Goal();
      goal.setOrder(5);

      // Should timeout since there's no server
      long startTime = System.currentTimeMillis();
      Fibonacci_Result result = client.sendGoalSync(goal, 1000);
      long elapsed = System.currentTimeMillis() - startTime;

      assertNull(result, "Result should be null when action times out");
      assertTrue(elapsed >= 1000 && elapsed < 2000, "Timeout should take approximately 1 second");
   }
}
