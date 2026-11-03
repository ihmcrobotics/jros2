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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Functional discovery: two jros2 nodes of this process communicate with the LOCALHOST range (also with a static peer and with
 * thread settings), and never with the OFF range (each participant may only know itself). Creates DDS participants: run under
 * the shared DDS lock in this workstream's domain.
 */
public class ROS2DiscoveryRangeFunctionalTest
{
   static
   {
      jros2.load();
   }

   /**
    * Publishes on a fresh topic from one node and counts what a second node receives.
    *
    * @return the number of messages received before {@code receiveWindowMillis} after the first one, or 0
    */
   private static int exchange(ROS2NodeOptions publisherOptions, ROS2NodeOptions subscriberOptions, long waitMillis) throws InterruptedException
   {
      String topicName = "/g2_discovery_" + System.nanoTime();
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(topicName, std_msgs.String_.class);
      ROS2Node publisherNode = new ROS2Node("g2_discovery_pub", publisherOptions);
      ROS2Node subscriberNode = new ROS2Node("g2_discovery_sub", subscriberOptions);
      AtomicInteger received = new AtomicInteger();
      try
      {
         subscriberNode.createSubscription(topic, reader ->
         {
            if (reader.read() != null)
            {
               received.incrementAndGet();
            }
         }, ROS2QoSProfile.DEFAULT);
         ROS2Publisher<std_msgs.String_> publisher = publisherNode.createPublisher(topic, ROS2QoSProfile.DEFAULT);
         std_msgs.String_ message = new std_msgs.String_();
         message.setData("discovery");

         long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
         while (System.nanoTime() < deadline && received.get() == 0)
         {
            publisher.publish(message);
            Thread.sleep(50);
         }
         return received.get();
      }
      finally
      {
         subscriberNode.close();
         publisherNode.close();
      }
   }

   @Test
   @Timeout(120)
   public void testLocalhostNodesCommunicate() throws InterruptedException
   {
      ROS2NodeOptions localhost = ROS2NodeOptions.defaults().discoveryRange(ROS2DiscoveryRange.LOCALHOST);
      assertTrue(exchange(localhost, localhost, 30_000) > 0, "Two LOCALHOST participants on this host must discover each other");
   }

   @Test
   @Timeout(120)
   public void testLocalhostWithStaticPeerAndThreadSettingsCommunicates() throws InterruptedException
   {
      ROS2NodeOptions options = ROS2NodeOptions.defaults()
                                               .discoveryRange(ROS2DiscoveryRange.LOCALHOST)
                                               .staticPeers("127.0.0.1")
                                               .threadSettings(new ROS2ThreadSettings().stackSizeBytes(1048576)
                                                                                       .forThreads(ROS2ThreadSettings.Kind.RECEPTION,
                                                                                                   new ROS2ThreadSettings().schedulingPolicy(0)));
      assertTrue(exchange(options, ROS2NodeOptions.defaults().discoveryRange(ROS2DiscoveryRange.LOCALHOST), 30_000) > 0);
   }

   @Test
   @Timeout(120)
   public void testOffNodesNeverCommunicate() throws InterruptedException
   {
      ROS2NodeOptions off = ROS2NodeOptions.defaults().discoveryRange(ROS2DiscoveryRange.OFF);
      assertEquals(0, exchange(off, off, 5_000), "OFF participants must not discover anything");
      // OFF on one side is enough
      assertEquals(0, exchange(off, ROS2NodeOptions.defaults().discoveryRange(ROS2DiscoveryRange.LOCALHOST), 5_000));
   }

   /**
    * The discovery settings reach the node from the jros2 settings sources (system properties here; environment variables and
    * jros2.properties use the same lookup), not only from {@link ROS2NodeOptions}.
    */
   @Test
   @Timeout(180)
   public void testDiscoverySettingsReachTheNode() throws InterruptedException
   {
      try
      {
         System.setProperty(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE.property, "OFF");
         assertEquals(0, exchange(ROS2NodeOptions.defaults(), ROS2NodeOptions.defaults(), 5_000), "ROS_AUTOMATIC_DISCOVERY_RANGE=OFF must apply");

         // An unresolvable ROS_STATIC_PEERS entry fails node creation, like rmw_fastrtps ("Unable to resolve peer")
         System.setProperty(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE.property, "LOCALHOST");
         System.setProperty(jros2Settings.Key.STATIC_PEERS.property, "no-such-host.invalid");
         IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new ROS2Node("g2_bad_peer").close());
         assertTrue(exception.getMessage().contains("no-such-host.invalid"), exception.getMessage());

         // ROS_LOCALHOST_ONLY=1 wins over everything else: LOCALHOST, and the static peers are ignored (not even resolved)
         System.setProperty(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE.property, "OFF");
         System.setProperty(jros2Settings.Key.LOCALHOST_ONLY.property, "1");
         assertTrue(exchange(ROS2NodeOptions.defaults(), ROS2NodeOptions.defaults(), 30_000) > 0, "ROS_LOCALHOST_ONLY=1 must select LOCALHOST");
      }
      finally
      {
         System.clearProperty(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE.property);
         System.clearProperty(jros2Settings.Key.STATIC_PEERS.property);
         System.clearProperty(jros2Settings.Key.LOCALHOST_ONLY.property);
      }
   }

   @Test
   @Timeout(120)
   public void testSubnetWithThreadSettingsOnBuiltinTransportsCommunicates() throws InterruptedException
   {
      ROS2NodeOptions options = ROS2NodeOptions.defaults()
                                               .discoveryRange(ROS2DiscoveryRange.SUBNET)
                                               .threadSettings(new ROS2ThreadSettings().forThreads(ROS2ThreadSettings.Kind.RECEPTION,
                                                                                                   new ROS2ThreadSettings().stackSizeBytes(1048576)));
      assertTrue(exchange(options, ROS2NodeOptions.defaults().discoveryRange(ROS2DiscoveryRange.SUBNET), 30_000) > 0);
   }
}
