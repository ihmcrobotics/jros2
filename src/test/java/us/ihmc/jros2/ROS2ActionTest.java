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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

         for (int i = 2; i <= order; i++)
         {
            int next = prev + curr;
            result.getSequence().add(next);
            prev = curr;
            curr = next;

            // Send feedback periodically
            if (i % 3 == 0 || i == order)
            {
               Fibonacci_Feedback feedback = feedbackPublisher.createFeedback();
               for (int j = 0; j <= i; j++)
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

      // Give time for discovery
      Thread.sleep(500);

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
         for (int i = 2; i <= order; i++)
         {
            int next = prev + curr;
            result.getSequence().add(next);
            prev = curr;
            curr = next;

            Fibonacci_Feedback feedback = feedbackPublisher.createFeedback();
            for (int j = 0; j <= i; j++)
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

      Thread.sleep(500);

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

         for (int i = 2; i <= order; i++)
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

      Thread.sleep(500);

      // Send goal asynchronously
      Fibonacci_Goal goal = new Fibonacci_Goal();
      goal.setOrder(7);

      CompletableFuture<Fibonacci_Result> future = client.sendGoalAsync(goal);

      Fibonacci_Result result = future.get();

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

         for (int i = 2; i <= order; i++)
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

      Thread.sleep(500);

      // Send multiple goals
      for (int i = 1; i <= 3; i++)
      {
         Fibonacci_Goal goal = new Fibonacci_Goal();
         goal.setOrder(i * 2);

         Fibonacci_Result result = client.sendGoalSync(goal, 2000);

         assertNotNull(result);
         assertEquals(i * 2 + 1, result.getSequence().size());
      }

      assertEquals(3, goalsExecuted.get(), "All 3 goals should have been executed");
   }
}
