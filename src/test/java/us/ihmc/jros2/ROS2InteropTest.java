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

import example_interfaces.AddTwoInts_Request;
import example_interfaces.AddTwoInts_Response;
import example_interfaces.Fibonacci_Feedback;
import example_interfaces.Fibonacci_Goal;
import example_interfaces.Fibonacci_Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Interoperability of jros2 services, parameters and actions with rclcpp and rclcpp_action (rmw_fastrtps_cpp).
 * <p>
 * Requires a ROS 2 workspace containing the {@code jros2_interop} package from {@code src/test/ros2/jros2_interop}, built with
 * colcon. Set {@code JROS2_INTEROP_SETUP} to its {@code setup.bash}, e.g. {@code JROS2_INTEROP_SETUP=~/ros2_ws/install/setup.bash}.
 */
@EnabledIfEnvironmentVariable(named = "JROS2_INTEROP_SETUP", matches = ".+")
public class ROS2InteropTest
{
   private static final long WAIT_MS = 15000;

   private static ProcessBuilder interopProcess(int domainId, String executable, String... args)
   {
      // setup.bash lives in the install directory; executables are in <install>/lib/jros2_interop
      Path setup = Path.of(System.getenv("JROS2_INTEROP_SETUP")).toAbsolutePath();
      Path executablePath = setup.getParent().resolve("lib").resolve("jros2_interop").resolve(executable);

      StringBuilder command = new StringBuilder();
      command.append("source '").append(setup).append("' && exec '").append(executablePath).append('\'');
      for (String arg : args)
      {
         command.append(" '").append(arg).append('\'');
      }

      ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", command.toString());
      processBuilder.environment().put("ROS_DOMAIN_ID", String.valueOf(domainId));
      processBuilder.environment().put("RMW_IMPLEMENTATION", "rmw_fastrtps_cpp");
      processBuilder.redirectErrorStream(true);
      return processBuilder;
   }

   @Test
   @Timeout(120)
   public void testJros2ClientCallsRclcppService() throws Exception
   {
      String serviceName = "/jros2_interop/rclcpp_server";
      try (ROS2Node node = new ROS2Node("jros2_interop_client"))
      {
         Process server = interopProcess(node.getDomainId(), "add_two_ints_server", serviceName).start();
         ProcessOutput serverOutput = new ProcessOutput(server);
         try
         {
            assertTrue(serverOutput.waitForLine("ready", WAIT_MS), serverOutput::get);

            ROS2Client<AddTwoInts_Request, AddTwoInts_Response> client = node.createClient(serviceName,
                                                                                           AddTwoInts_Request.class,
                                                                                           AddTwoInts_Response.class);
            assertTrue(client.waitForService(WAIT_MS), "rclcpp service not discovered");

            List<CompletableFuture<AddTwoInts_Response>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++)
            {
               AddTwoInts_Request request = new AddTwoInts_Request();
               request.setA(i);
               request.setB(100);
               futures.add(client.sendRequest(request));
            }
            for (int i = 0; i < futures.size(); i++)
            {
               int index = i;
               AddTwoInts_Response response = assertDoesNotThrow(() -> futures.get(index).get(WAIT_MS, TimeUnit.MILLISECONDS),
                                                                 () -> "No response to request " + index + ". Server output:\n" + serverOutput.get());
               assertEquals(i + 100, response.getSum());
            }

            for (int i = 0; i < 10; i++)
            {
               assertTrue(serverOutput.waitForLine("request a=" + i + " b=100", WAIT_MS), serverOutput::get);
            }
         }
         finally
         {
            server.destroy();
            server.waitFor(5, TimeUnit.SECONDS);
         }
      }
   }

   @Test
   @Timeout(120)
   public void testRclcppClientCallsJros2Service() throws Exception
   {
      String serviceName = "/jros2_interop/jros2_server";
      try (ROS2Node node = new ROS2Node("jros2_interop_server"))
      {
         node.createService(serviceName, AddTwoInts_Request.class, AddTwoInts_Response.class, (request, response) ->
               response.setSum(request.getA() + request.getB()));

         // Several separate rclcpp clients, each one a new process and a new discovery
         for (int i = 0; i < 3; i++)
         {
            Process client = interopProcess(node.getDomainId(), "add_two_ints_client", serviceName, String.valueOf(i), "40").start();
            String output = readAll(client);
            assertTrue(client.waitFor(60, TimeUnit.SECONDS));
            assertEquals(0, client.exitValue(), output);
            assertTrue(output.contains("sum=" + (i + 40)), output);
         }
      }
   }

   @Test
   @Timeout(120)
   public void testRclcppParameterClientOnJros2Node() throws Exception
   {
      try (ROS2Node node = new ROS2Node("jros2_interop_parameters"))
      {
         node.declareParameter("rate", 10);
         node.declareParameter("control.gain", 1.5);
         node.declareParameter("frame", "map");

         Process client = interopProcess(node.getDomainId(), "param_client", "/jros2_interop_parameters", "rate", "25").start();
         String output = readAll(client);
         assertTrue(client.waitFor(60, TimeUnit.SECONDS));
         assertEquals(0, client.exitValue(), output);

         assertTrue(output.contains("name=rate\nname=control.gain\nname=frame\n"), output);
         assertTrue(output.contains("get rate=10\n"), output);
         assertTrue(output.contains("get control.gain=1.5"), output);
         assertTrue(output.contains("get frame=map\n"), output);
         // set_parameters sets each parameter on its own: the first succeeds, the second has the wrong type
         assertTrue(output.contains("set successful=1 reason=\n"), output);
         assertTrue(output.contains("set successful=0 reason=Wrong parameter type, parameter {rate} is of type {integer}, setting it to {string} is not allowed."), output);
         assertTrue(output.contains("after rate=25\n"), output);
         assertEquals(25L, node.getParameter("rate").asLong());
      }
   }

   @Test
   @Timeout(120)
   public void testJros2ActionClientWithRclcppServer() throws Exception
   {
      String actionName = "/jros2_interop/rclcpp_fibonacci";
      try (ROS2Node node = new ROS2Node("jros2_interop_action_client"))
      {
         Process server = interopProcess(node.getDomainId(), "fibonacci_server", actionName).start();
         try
         {
            ProcessOutput serverOutput = new ProcessOutput(server);
            assertTrue(serverOutput.waitForLine("ready", WAIT_MS), serverOutput::get);

            ROS2ActionClient<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> client = node.createActionClient(actionName,
                                                                                                                     Fibonacci_Goal.class,
                                                                                                                     Fibonacci_Result.class,
                                                                                                                     Fibonacci_Feedback.class);
            assertTrue(client.waitForActionServer(WAIT_MS), "rclcpp action server not discovered");

            // Success with feedback
            List<Integer> feedbackSizes = new ArrayList<>();
            Fibonacci_Goal goal = new Fibonacci_Goal();
            goal.setOrder(8);
            ROS2ClientGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> handle = assertDoesNotThrow(() -> client.sendGoal(goal, (h, feedback) ->
            {
               synchronized (feedbackSizes)
               {
                  feedbackSizes.add(feedback.getSequence().size());
               }
            }).get(WAIT_MS, TimeUnit.MILLISECONDS), serverOutput::get);
            assertTrue(handle.isAccepted());
            ROS2ActionResult<Fibonacci_Result> result = handle.getResultFuture().get(WAIT_MS, TimeUnit.MILLISECONDS);
            assertEquals(ROS2GoalStatus.SUCCEEDED, result.status());
            assertEquals(9, result.result().getSequence().size());
            assertEquals(21, result.result().getSequence().get(8));
            synchronized (feedbackSizes)
            {
               assertFalse(feedbackSizes.isEmpty());
               assertEquals(9, feedbackSizes.get(feedbackSizes.size() - 1));
            }

            // Rejected
            goal.setOrder(-1);
            assertFalse(client.sendGoal(goal).get(WAIT_MS, TimeUnit.MILLISECONDS).isAccepted());

            // Canceled after the first feedback
            goal.setOrder(200);
            CompletableFuture<Void> firstFeedback = new CompletableFuture<>();
            ROS2ClientGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> longGoal = client.sendGoal(goal, (h, f) -> firstFeedback.complete(null))
                                                                                                         .get(WAIT_MS, TimeUnit.MILLISECONDS);
            firstFeedback.get(WAIT_MS, TimeUnit.MILLISECONDS);
            action_msgs.CancelGoal_Response cancel = longGoal.cancel().get(WAIT_MS, TimeUnit.MILLISECONDS);
            assertEquals(action_msgs.CancelGoal_Response.ERROR_NONE, cancel.getReturnCode());
            assertEquals(1, cancel.getGoalsCanceling().size());
            assertEquals(ROS2GoalStatus.CANCELED, longGoal.getResultFuture().get(WAIT_MS, TimeUnit.MILLISECONDS).status());
         }
         finally
         {
            server.destroy();
            server.waitFor(5, TimeUnit.SECONDS);
         }
      }
   }

   @Test
   @Timeout(120)
   public void testRclcppActionClientWithJros2Server() throws Exception
   {
      String actionName = "/jros2_interop/jros2_fibonacci";
      try (ROS2Node node = new ROS2Node("jros2_interop_action_server"))
      {
         node.createActionServer(actionName, Fibonacci_Goal.class, Fibonacci_Result.class, Fibonacci_Feedback.class, new ROS2ActionServerCallbacks<>()
         {
            @Override
            public ROS2GoalResponse handleGoal(UUID goalId, Fibonacci_Goal goal)
            {
               return goal.getOrder() < 0 ? ROS2GoalResponse.REJECT : ROS2GoalResponse.ACCEPT_AND_EXECUTE;
            }

            @Override
            public ROS2CancelResponse handleCancel(ROS2ServerGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> goalHandle)
            {
               return ROS2CancelResponse.ACCEPT;
            }

            @Override
            public void handleAccepted(ROS2ServerGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> goalHandle)
            {
               Fibonacci_Feedback feedback = new Fibonacci_Feedback();
               feedback.getSequence().add(0);
               feedback.getSequence().add(1);
               Fibonacci_Result result = new Fibonacci_Result();
               for (int i = 1; i < goalHandle.getGoal().getOrder(); i++)
               {
                  if (goalHandle.isCanceling())
                  {
                     result.getSequence().set(feedback.getSequence());
                     goalHandle.canceled(result);
                     return;
                  }
                  feedback.getSequence().add(feedback.getSequence().get(i) + feedback.getSequence().get(i - 1));
                  goalHandle.publishFeedback(feedback);
                  try
                  {
                     Thread.sleep(50);
                  }
                  catch (InterruptedException e)
                  {
                     return;
                  }
               }
               result.getSequence().set(feedback.getSequence());
               goalHandle.succeed(result);
            }
         });

         // Success with feedback (result code 4 = SUCCEEDED in rclcpp_action::ResultCode)
         Process success = interopProcess(node.getDomainId(), "fibonacci_client", actionName, "8", "0").start();
         String output = readAll(success);
         assertTrue(success.waitFor(60, TimeUnit.SECONDS));
         assertEquals(0, success.exitValue(), output);
         assertTrue(output.contains("accepted\n"), output);
         assertTrue(output.contains("feedback size=9\n"), output);
         assertTrue(output.contains("result code=4 sequence=0,1,1,2,3,5,8,13,21\n"), output);

         // Rejected
         Process rejected = interopProcess(node.getDomainId(), "fibonacci_client", actionName, "-1", "0").start();
         output = readAll(rejected);
         assertTrue(rejected.waitFor(60, TimeUnit.SECONDS));
         assertTrue(output.contains("rejected\n"), output);

         // Canceled after the first feedback (result code 5 = CANCELED)
         Process canceled = interopProcess(node.getDomainId(), "fibonacci_client", actionName, "200", "1").start();
         output = readAll(canceled);
         assertTrue(canceled.waitFor(60, TimeUnit.SECONDS));
         assertEquals(0, canceled.exitValue(), output);
         assertTrue(output.contains("cancel return_code=0 canceling=1\n"), output);
         assertTrue(output.contains("result code=5 "), output);
      }
   }

   private static String readAll(Process process) throws IOException
   {
      StringBuilder output = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
         String line;
         while ((line = reader.readLine()) != null)
         {
            output.append(line).append('\n');
         }
      }
      return output.toString();
   }

   /**
    * Collects the output of a process on a background thread, so it can be waited on and shown when a test fails.
    */
   private static class ProcessOutput
   {
      private final StringBuilder output = new StringBuilder();

      ProcessOutput(Process process)
      {
         Thread reader = new Thread(() ->
         {
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
            {
               String line;
               while ((line = lines.readLine()) != null)
               {
                  synchronized (output)
                  {
                     output.append(line).append('\n');
                     output.notifyAll();
                  }
               }
            }
            catch (IOException ignored)
            {
               // Process closed
            }
         }, "InteropProcessOutput");
         reader.setDaemon(true);
         reader.start();
      }

      boolean waitForLine(String line, long timeoutMs) throws InterruptedException
      {
         long deadline = System.currentTimeMillis() + timeoutMs;
         synchronized (output)
         {
            while (!("\n" + output).contains("\n" + line + "\n"))
            {
               long remaining = deadline - System.currentTimeMillis();
               if (remaining <= 0)
                  return false;
               output.wait(remaining);
            }
            return true;
         }
      }

      String get()
      {
         synchronized (output)
         {
            return output.toString();
         }
      }
   }
}
