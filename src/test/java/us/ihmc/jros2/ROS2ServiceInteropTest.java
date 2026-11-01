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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Interoperability of jros2 services with rclcpp (rmw_fastrtps_cpp).
 * <p>
 * Requires a ROS 2 workspace containing the {@code jros2_interop} package from {@code src/test/ros2/jros2_interop}, built with
 * colcon. Set {@code JROS2_INTEROP_SETUP} to its {@code setup.bash}, e.g. {@code JROS2_INTEROP_SETUP=~/ros2_ws/install/setup.bash}.
 */
@EnabledIfEnvironmentVariable(named = "JROS2_INTEROP_SETUP", matches = ".+")
public class ROS2ServiceInteropTest
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
         try
         {
            BufferedReader serverOutput = new BufferedReader(new InputStreamReader(server.getInputStream(), StandardCharsets.UTF_8));
            assertEquals("ready", serverOutput.readLine());

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
               assertEquals(i + 100, futures.get(i).get(WAIT_MS, TimeUnit.MILLISECONDS).getSum());
            }

            for (int i = 0; i < 10; i++)
            {
               assertEquals("request a=" + i + " b=100", serverOutput.readLine());
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
}
