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

import sensor_msgs.Image;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.locks.LockSupport;

/**
 * Child JVM of {@link SubscriptionPayloadMemoryTest}, started with a fixed, pre-touched Java heap and intraprocess delivery
 * OFF, so that the resident set only grows with native memory. Sends large images on one topic per case and prints how much
 * the resident set grew while the topic's payload pool grew to their size:
 * <pre>
 * RESULT &lt;case&gt; size=.. sent=.. received=.. growthBytes=..
 * </pre>
 */
public final class SubscriptionPayloadMemoryChild
{
   static final int SIZE = 2764800; // 1280x720 rgb8
   static final int PUBLISHER_DEPTH = 5;
   static final int MESSAGES = 20;

   private SubscriptionPayloadMemoryChild()
   {
   }

   public static void main(String[] args) throws Exception
   {
      System.setProperty("jros2.node.printout", "false");
      jros2.load();
      if (jros2.get().intraprocessDelivery())
      {
         System.out.println("ERROR intraprocess delivery is on");
         System.exit(2);
      }

      String prefix = "/jros2_payload_memory_" + ProcessHandle.current().pid();
      Image tx = LargeMessageDeliveryChild.image(SIZE);
      Image rx = LargeMessageDeliveryChild.image(SIZE);
      ROS2Node publisherNode = new ROS2Node("payload_memory_pub");
      ROS2Node subscriberNode = new ROS2Node("payload_memory_sub");
      try
      {
         run("keep-last", publisherNode, subscriberNode, prefix + "/a", qos(ROS2QoSProfile.History.KEEP_LAST), tx, rx);
         run("keep-all", publisherNode, subscriberNode, prefix + "/b", qos(ROS2QoSProfile.History.KEEP_ALL), tx, rx);
      }
      finally
      {
         subscriberNode.close();
         publisherNode.close();
      }
      System.out.println("DONE");
      System.exit(0);
   }

   private static ROS2QoSProfile qos(ROS2QoSProfile.History history)
   {
      ROS2QoSProfile qos = new ROS2QoSProfile();
      qos.reliability(ROS2QoSProfile.Reliability.RELIABLE);
      qos.durability(ROS2QoSProfile.Durability.VOLATILE);
      qos.history(history);
      qos.depth(PUBLISHER_DEPTH);
      return qos;
   }

   private static void run(String name, ROS2Node publisherNode, ROS2Node subscriberNode, String topicName, ROS2QoSProfile subscriptionQos, Image tx, Image rx)
         throws Exception
   {
      int[] received = new int[1];
      ROS2Topic<Image> topic = new ROS2Topic<>(topicName, Image.class);
      ROS2Subscription<Image> subscription = subscriberNode.createSubscription(topic, reader ->
      {
         if (reader.read(rx))
            ++received[0];
      }, subscriptionQos);
      ROS2Publisher<Image> publisher = publisherNode.createPublisher(topic, qos(ROS2QoSProfile.History.KEEP_LAST));
      if (!publisher.waitForSubscription(30_000) || !subscription.waitForPublisher(30_000))
         throw new IllegalStateException("no match on " + topicName);
      Thread.sleep(300);

      long before = residentBytes();
      for (int i = 0; i < MESSAGES; ++i)
      {
         publisher.publish(tx);
         LockSupport.parkNanos(20_000_000L);
      }
      long deadline = System.nanoTime() + 5_000_000_000L;
      while (received[0] < MESSAGES && System.nanoTime() < deadline)
         Thread.sleep(10);
      long growth = residentBytes() - before;
      System.out.println("RESULT " + name + " size=" + SIZE + " sent=" + MESSAGES + " received=" + received[0] + " growthBytes=" + growth);
   }

   private static long residentBytes() throws Exception
   {
      for (String line : Files.readAllLines(Paths.get("/proc/self/status")))
      {
         if (line.startsWith("VmRSS:"))
            return Long.parseLong(line.replaceAll("[^0-9]", "")) * 1024L;
      }
      throw new IllegalStateException("no VmRSS in /proc/self/status");
   }
}
