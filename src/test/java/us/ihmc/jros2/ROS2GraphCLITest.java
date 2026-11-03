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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Interop with the ros2 CLI of the local ROS 2 installation: the jros2 node must be visible in the ROS graph.
 * The graph format follows ROS_DISTRO (AUTO compatibility), so the CLI of that distro sees the node.
 */
@EnabledIf("us.ihmc.jros2.ROS2TestTools#isROS2CLIAvailable")
public class ROS2GraphCLITest
{
   private static final String NAMESPACE = "/jros2_cli_test";
   private static final String NODE_NAME = "cli_node";
   private static final long CLI_TIMEOUT_S = 40;

   private static ROS2Node node;
   private static ROS2Context context;
   private static ROS2Node contextNodeA;
   private static ROS2Node contextNodeB;

   @BeforeAll
   public static void createNodes()
   {
      node = new ROS2Node(NODE_NAME, ROS2NodeOptions.defaults().namespace(NAMESPACE));
      node.createPublisher(new ROS2Topic<>("chatter", std_msgs.String_.class));
      node.createSubscription(new ROS2Topic<>("cmd", example_interfaces.Bool.class), reader -> reader.read());

      context = ROS2Context.create();
      contextNodeA = new ROS2Node("context_a", ROS2NodeOptions.defaults().namespace(NAMESPACE).context(context));
      contextNodeB = new ROS2Node("context_b", ROS2NodeOptions.defaults().namespace(NAMESPACE).context(context));
      contextNodeB.createPublisher(new ROS2Topic<>("~/status", std_msgs.String_.class));
   }

   @AfterAll
   public static void closeNodes()
   {
      if (node != null)
      {
         node.close();
      }
      if (context != null)
      {
         context.close();
      }
   }

   private static String ros2(String command) throws IOException, InterruptedException
   {
      return ROS2TestTools.runROS2Command(node.getDomainId(), command, CLI_TIMEOUT_S);
   }

   @Test
   @Timeout(60)
   public void testNodeList() throws IOException, InterruptedException
   {
      String output = ros2("node list --no-daemon --spin-time 5");
      assertTrue(output.contains(NAMESPACE + "/" + NODE_NAME + "\n"), output);
      assertTrue(output.contains(NAMESPACE + "/context_a\n"), output);
      assertTrue(output.contains(NAMESPACE + "/context_b\n"), output);
   }

   @Test
   @Timeout(60)
   public void testNodeInfo() throws IOException, InterruptedException
   {
      String output = ros2("node info " + NAMESPACE + "/" + NODE_NAME + " --no-daemon --spin-time 5");
      int subscribers = output.indexOf("Subscribers:");
      int publishers = output.indexOf("Publishers:");
      assertTrue(subscribers >= 0 && publishers > subscribers, output);
      String subscriberSection = output.substring(subscribers, publishers);
      String publisherSection = output.substring(publishers);
      assertTrue(subscriberSection.contains(NAMESPACE + "/cmd: example_interfaces/msg/Bool"), output);
      assertTrue(publisherSection.contains(NAMESPACE + "/chatter: std_msgs/msg/String"), output);

      String contextOutput = ros2("node info " + NAMESPACE + "/context_b --no-daemon --spin-time 5");
      assertTrue(contextOutput.contains(NAMESPACE + "/context_b/status: std_msgs/msg/String"), contextOutput);
   }

   @Test
   @Timeout(60)
   public void testTopicInfoVerbose() throws IOException, InterruptedException
   {
      // Foxy's topic info has no --no-daemon / --spin-time; the helper asks its daemon after the same discovery time instead
      String output = ROS2TestTools.ros2TopicInfoVerbose(node.getDomainId(), NAMESPACE + "/chatter", 5, CLI_TIMEOUT_S);
      assertTrue(output.contains("Node name: " + NODE_NAME), output);
      assertTrue(output.contains("Node namespace: " + NAMESPACE), output);
      assertTrue(output.contains("Endpoint type: PUBLISHER"), output);
      assertFalse(output.contains("_NODE_NAME_UNKNOWN_"), output);
      assertFalse(output.contains("_CREATED_BY_BARE_DDS_APP_"), output);

      String typeHash = ROS2Message.getTypeHashFromMessageClass(std_msgs.String_.class);
      boolean cliPrintsTypeHash = !ROS2TestTools.ROS_DISTRO.equals("foxy") && !ROS2TestTools.ROS_DISTRO.equals("galactic") && !ROS2TestTools.ROS_DISTRO.equals(
            "humble");
      if (typeHash != null && cliPrintsTypeHash)
      {
         // The endpoint announces "typehash=<hash>;" when the generated class carries the hash
         assertTrue(output.contains("Topic type hash: " + typeHash), output);
      }
   }
}
