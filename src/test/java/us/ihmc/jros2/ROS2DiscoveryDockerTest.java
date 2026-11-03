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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Discovery ranges against "another host": a ROS 2 Jazzy container on the default docker bridge network (its own IP, no
 * shared memory). A SUBNET jros2 publisher is heard by the container, a LOCALHOST one is not. Runs only on Linux with docker
 * and the {@value #IMAGE} image available. Creates DDS participants: run under the shared DDS lock in this workstream's domain.
 */
@EnabledOnOs(OS.LINUX)
public class ROS2DiscoveryDockerTest
{
   static final String IMAGE = "osrf/ros:jazzy-desktop";

   static
   {
      jros2.load();
   }

   static boolean isDockerImageAvailable()
   {
      try
      {
         Process process = new ProcessBuilder("docker", "image", "inspect", IMAGE).redirectErrorStream(true).start();
         process.getInputStream().readAllBytes();
         return process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
      }
      catch (IOException | InterruptedException e)
      {
         return false;
      }
   }

   /**
    * Publishes from a jros2 node with the given range while a container runs {@code ros2 topic echo --once}.
    *
    * @return whether the container printed the message
    */
   private static boolean containerHears(ROS2DiscoveryRange range, int timeoutSeconds) throws IOException, InterruptedException
   {
      String topicName = "/g2_docker_" + System.nanoTime();
      ROS2Node node = new ROS2Node("g2_docker_pub", ROS2NodeOptions.defaults().discoveryRange(range));
      Process container = null;
      try
      {
         ROS2Publisher<std_msgs.String_> publisher = node.createPublisher(new ROS2Topic<>(topicName, std_msgs.String_.class), ROS2QoSProfile.DEFAULT);
         container = new ProcessBuilder("docker",
                                        "run",
                                        "--rm",
                                        "--network",
                                        "bridge",
                                        "-e",
                                        "ROS_DOMAIN_ID=" + node.getDomainId(),
                                        IMAGE,
                                        "bash",
                                        "-c",
                                        "source /opt/ros/jazzy/setup.bash && timeout " + timeoutSeconds + " ros2 topic echo --once " + topicName
                                        + " std_msgs/msg/String").redirectErrorStream(true).start();
         AtomicBoolean heard = new AtomicBoolean();
         Process finalContainer = container;
         Thread reader = new Thread(() ->
         {
            try (BufferedReader output = new BufferedReader(new InputStreamReader(finalContainer.getInputStream(), StandardCharsets.UTF_8)))
            {
               String line;
               while ((line = output.readLine()) != null)
               {
                  if (line.contains("data: from jros2"))
                  {
                     heard.set(true);
                  }
               }
            }
            catch (IOException ignored)
            {
            }
         });
         reader.setDaemon(true);
         reader.start();

         std_msgs.String_ message = new std_msgs.String_();
         message.setData("from jros2");
         long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds + 30);
         while (container.isAlive() && System.nanoTime() < deadline)
         {
            publisher.publish(message);
            Thread.sleep(100);
         }
         reader.join(5000);
         return heard.get();
      }
      finally
      {
         if (container != null)
         {
            container.destroy();
            container.waitFor(10, TimeUnit.SECONDS);
         }
         node.close();
      }
   }

   @Test
   @Timeout(300)
   @EnabledIf("isDockerImageAvailable")
   public void testSubnetReachesOtherHostAndLocalhostDoesNot() throws IOException, InterruptedException
   {
      assertTrue(containerHears(ROS2DiscoveryRange.SUBNET, 60), "A SUBNET participant must be discovered by a ROS 2 node on the docker bridge");
      assertFalse(containerHears(ROS2DiscoveryRange.LOCALHOST, 20), "A LOCALHOST participant must not be discovered from another host");
   }
}
