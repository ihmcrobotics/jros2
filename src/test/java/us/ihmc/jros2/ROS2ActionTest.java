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

import action_msgs.CancelGoal_Response;
import example_interfaces.Fibonacci_Feedback;
import example_interfaces.Fibonacci_Goal;
import example_interfaces.Fibonacci_Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ROS2ActionTest
{
   private static final long WAIT_MS = 10000;

   /**
    * Fibonacci server like the rclcpp_action example: rejects negative orders, publishes the partial sequence as feedback
    * every {@code stepMs}, stops if canceled, aborts orders above 40.
    */
   private static class FibonacciServer implements ROS2ActionServerCallbacks<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback>
   {
      private final long stepMs;
      private final ROS2CancelResponse cancelResponse;
      private final ROS2GoalResponse acceptResponse;
      final List<ROS2ServerGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback>> accepted = new ArrayList<>();

      FibonacciServer(long stepMs, ROS2CancelResponse cancelResponse, ROS2GoalResponse acceptResponse)
      {
         this.stepMs = stepMs;
         this.cancelResponse = cancelResponse;
         this.acceptResponse = acceptResponse;
      }

      @Override
      public ROS2GoalResponse handleGoal(UUID goalId, Fibonacci_Goal goal)
      {
         return goal.getOrder() < 0 ? ROS2GoalResponse.REJECT : acceptResponse;
      }

      @Override
      public ROS2CancelResponse handleCancel(ROS2ServerGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> goalHandle)
      {
         return cancelResponse;
      }

      @Override
      public void handleAccepted(ROS2ServerGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> goalHandle)
      {
         synchronized (accepted)
         {
            accepted.add(goalHandle);
            accepted.notifyAll();
         }
         if (acceptResponse == ROS2GoalResponse.ACCEPT_AND_DEFER)
            return; // The test starts it

         execute(goalHandle);
      }

      void execute(ROS2ServerGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> goalHandle)
      {
         int order = goalHandle.getGoal().getOrder();
         Fibonacci_Feedback feedback = new Fibonacci_Feedback();
         Fibonacci_Result result = new Fibonacci_Result();
         feedback.getSequence().add(0);
         feedback.getSequence().add(1);
         for (int i = 1; i < order; i++)
         {
            if (goalHandle.isCanceling())
            {
               result.getSequence().set(feedback.getSequence());
               goalHandle.canceled(result);
               return;
            }
            feedback.getSequence().add(feedback.getSequence().get(i) + feedback.getSequence().get(i - 1));
            goalHandle.publishFeedback(feedback);
            sleep(stepMs);
         }
         result.getSequence().set(feedback.getSequence());
         if (order > 40)
            goalHandle.abort(result);
         else
            goalHandle.succeed(result);
      }

      ROS2ServerGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> waitForAccepted() throws InterruptedException
      {
         synchronized (accepted)
         {
            long deadline = System.currentTimeMillis() + WAIT_MS;
            while (accepted.isEmpty() && System.currentTimeMillis() < deadline)
               accepted.wait(100);
            return accepted.get(accepted.size() - 1);
         }
      }
   }

   private static void sleep(long ms)
   {
      try
      {
         Thread.sleep(ms);
      }
      catch (InterruptedException e)
      {
         Thread.currentThread().interrupt();
      }
   }

   private static Fibonacci_Goal goal(int order)
   {
      Fibonacci_Goal goal = new Fibonacci_Goal();
      goal.setOrder(order);
      return goal;
   }

   private static List<Integer> sequence(Fibonacci_Result result)
   {
      List<Integer> list = new ArrayList<>();
      for (int i = 0; i < result.getSequence().size(); i++)
         list.add(result.getSequence().get(i));
      return list;
   }

   @Test
   @Timeout(60)
   public void testSucceedWithFeedback() throws Exception
   {
      try (ROS2Node serverNode = new ROS2Node("action_test_server"); ROS2Node clientNode = new ROS2Node("action_test_client"))
      {
         serverNode.createActionServer("/jros2_test/fibonacci",
                                       Fibonacci_Goal.class,
                                       Fibonacci_Result.class,
                                       Fibonacci_Feedback.class,
                                       new FibonacciServer(20, ROS2CancelResponse.ACCEPT, ROS2GoalResponse.ACCEPT_AND_EXECUTE));
         ROS2ActionClient<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> client = clientNode.createActionClient("/jros2_test/fibonacci",
                                                                                                                        Fibonacci_Goal.class,
                                                                                                                        Fibonacci_Result.class,
                                                                                                                        Fibonacci_Feedback.class);
         assertTrue(client.waitForActionServer(WAIT_MS));

         List<Integer> feedbackSizes = new ArrayList<>();
         ROS2ClientGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> handle = client.sendGoal(goal(10), (goalHandle, feedback) ->
         {
            synchronized (feedbackSizes)
            {
               feedbackSizes.add(feedback.getSequence().size());
            }
         }).get(WAIT_MS, TimeUnit.MILLISECONDS);
         assertTrue(handle.isAccepted());
         assertTrue(handle.getAcceptedTimeNanos() > 0);

         ROS2ActionResult<Fibonacci_Result> result = handle.getResultFuture().get(WAIT_MS, TimeUnit.MILLISECONDS);
         assertEquals(ROS2GoalStatus.SUCCEEDED, result.status());
         assertEquals(List.of(0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55), sequence(result.result()));
         assertEquals(ROS2GoalStatus.SUCCEEDED, handle.getStatus());
         synchronized (feedbackSizes)
         {
            // Feedback may lose its first messages while the topic matches, but the ones received are in order
            assertFalse(feedbackSizes.isEmpty());
            for (int i = 1; i < feedbackSizes.size(); i++)
               assertEquals(feedbackSizes.get(i - 1) + 1, feedbackSizes.get(i));
            assertEquals(11, feedbackSizes.get(feedbackSizes.size() - 1));
         }

         // A finished goal cannot be canceled
         CancelGoal_Response cancelFinished = handle.cancel().get(WAIT_MS, TimeUnit.MILLISECONDS);
         assertEquals(CancelGoal_Response.ERROR_GOAL_TERMINATED, cancelFinished.getReturnCode());
      }
   }

   @Test
   @Timeout(60)
   public void testRejectAndAbort() throws Exception
   {
      try (ROS2Node node = new ROS2Node("action_test_reject"))
      {
         node.createActionServer("/jros2_test/fibonacci_reject",
                                 Fibonacci_Goal.class,
                                 Fibonacci_Result.class,
                                 Fibonacci_Feedback.class,
                                 new FibonacciServer(0, ROS2CancelResponse.ACCEPT, ROS2GoalResponse.ACCEPT_AND_EXECUTE));
         ROS2ActionClient<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> client = node.createActionClient("/jros2_test/fibonacci_reject",
                                                                                                                  Fibonacci_Goal.class,
                                                                                                                  Fibonacci_Result.class,
                                                                                                                  Fibonacci_Feedback.class);
         assertTrue(client.waitForActionServer(WAIT_MS));

         ROS2ClientGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> rejected = client.sendGoal(goal(-1)).get(WAIT_MS, TimeUnit.MILLISECONDS);
         assertFalse(rejected.isAccepted());
         assertEquals(ROS2GoalStatus.UNKNOWN, rejected.getResultFuture().get(WAIT_MS, TimeUnit.MILLISECONDS).status());

         ROS2ClientGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> aborted = client.sendGoal(goal(45)).get(WAIT_MS, TimeUnit.MILLISECONDS);
         ROS2ActionResult<Fibonacci_Result> result = aborted.getResultFuture().get(WAIT_MS, TimeUnit.MILLISECONDS);
         assertEquals(ROS2GoalStatus.ABORTED, result.status());
         assertEquals(46, result.result().getSequence().size());
      }
   }

   @Test
   @Timeout(60)
   public void testCancel() throws Exception
   {
      try (ROS2Node serverNode = new ROS2Node("action_test_cancel_server"); ROS2Node clientNode = new ROS2Node("action_test_cancel_client"))
      {
         FibonacciServer server = new FibonacciServer(100, ROS2CancelResponse.ACCEPT, ROS2GoalResponse.ACCEPT_AND_EXECUTE);
         serverNode.createActionServer("/jros2_test/fibonacci_cancel", Fibonacci_Goal.class, Fibonacci_Result.class, Fibonacci_Feedback.class, server);
         ROS2ActionClient<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> client = clientNode.createActionClient("/jros2_test/fibonacci_cancel",
                                                                                                                        Fibonacci_Goal.class,
                                                                                                                        Fibonacci_Result.class,
                                                                                                                        Fibonacci_Feedback.class);
         assertTrue(client.waitForActionServer(WAIT_MS));

         CountDownLatch firstFeedback = new CountDownLatch(1);
         ROS2ClientGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> handle = client.sendGoal(goal(40), (h, f) -> firstFeedback.countDown())
                                                                                                    .get(WAIT_MS, TimeUnit.MILLISECONDS);
         assertTrue(firstFeedback.await(WAIT_MS, TimeUnit.MILLISECONDS));
         // The status topic reports the goal as executing
         long deadline = System.currentTimeMillis() + WAIT_MS;
         while (handle.getStatus() != ROS2GoalStatus.EXECUTING && System.currentTimeMillis() < deadline)
            Thread.sleep(10);
         assertEquals(ROS2GoalStatus.EXECUTING, handle.getStatus());

         CancelGoal_Response cancelResponse = handle.cancel().get(WAIT_MS, TimeUnit.MILLISECONDS);
         assertEquals(CancelGoal_Response.ERROR_NONE, cancelResponse.getReturnCode());
         assertEquals(1, cancelResponse.getGoalsCanceling().size());
         assertEquals(handle.getGoalId(), ROS2ActionTypeSupport.toUUID(cancelResponse.getGoalsCanceling().get(0).getGoalId()));

         ROS2ActionResult<Fibonacci_Result> result = handle.getResultFuture().get(WAIT_MS, TimeUnit.MILLISECONDS);
         assertEquals(ROS2GoalStatus.CANCELED, result.status());
         assertTrue(result.result().getSequence().size() < 41);

         // Nothing left to cancel
         assertEquals(CancelGoal_Response.ERROR_NONE, client.cancelAllGoals().get(WAIT_MS, TimeUnit.MILLISECONDS).getReturnCode());
         assertEquals(0, client.cancelAllGoals().get(WAIT_MS, TimeUnit.MILLISECONDS).getGoalsCanceling().size());
      }
   }

   @Test
   @Timeout(60)
   public void testCancelRejectedAndDeferredExecution() throws Exception
   {
      try (ROS2Node node = new ROS2Node("action_test_defer"))
      {
         FibonacciServer server = new FibonacciServer(0, ROS2CancelResponse.REJECT, ROS2GoalResponse.ACCEPT_AND_DEFER);
         node.createActionServer("/jros2_test/fibonacci_defer", Fibonacci_Goal.class, Fibonacci_Result.class, Fibonacci_Feedback.class, server);
         ROS2ActionClient<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> client = node.createActionClient("/jros2_test/fibonacci_defer",
                                                                                                                  Fibonacci_Goal.class,
                                                                                                                  Fibonacci_Result.class,
                                                                                                                  Fibonacci_Feedback.class);
         assertTrue(client.waitForActionServer(WAIT_MS));

         ROS2ClientGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> handle = client.sendGoal(goal(5)).get(WAIT_MS, TimeUnit.MILLISECONDS);
         ROS2ServerGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> serverHandle = server.waitForAccepted();
         assertEquals(ROS2GoalStatus.ACCEPTED, serverHandle.getStatus());
         // Cannot finish a goal that is not executing
         assertThrows(IllegalStateException.class, () -> serverHandle.succeed(new Fibonacci_Result()));
         assertThrows(IllegalStateException.class, () -> serverHandle.canceled(new Fibonacci_Result()));

         assertEquals(CancelGoal_Response.ERROR_REJECTED, handle.cancel().get(WAIT_MS, TimeUnit.MILLISECONDS).getReturnCode());
         assertFalse(handle.getResultFuture().isDone());

         serverHandle.execute();
         assertThrows(IllegalStateException.class, serverHandle::execute);
         server.execute(serverHandle);
         assertEquals(List.of(0, 1, 1, 2, 3, 5), sequence(handle.getResultFuture().get(WAIT_MS, TimeUnit.MILLISECONDS).result()));

         // Unknown goal id
         AtomicReference<CancelGoal_Response> unknown = new AtomicReference<>();
         ROS2ClientGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> fake = new ROS2ClientGoalHandle<>(client, UUID.randomUUID(), null);
         unknown.set(client.cancelGoal(fake).get(WAIT_MS, TimeUnit.MILLISECONDS));
         assertEquals(CancelGoal_Response.ERROR_UNKNOWN_GOAL_ID, unknown.get().getReturnCode());

         assertTrue(node.destroyActionClient(client));
         assertEquals(1, node.getActionServers().size());
      }
   }
}
