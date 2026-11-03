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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import us.ihmc.jros2.RmwDdsCommonMessages.NodeEntitiesInfo;
import us.ihmc.jros2.RmwDdsCommonMessages.ParticipantEntitiesInfo;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The ROS graph ({@code ros_discovery_info}) as seen by a raw DDS reader, like rmw's graph listener.
 */
public class ROS2GraphTest
{
   private static final String COMPATIBILITY_PROPERTY = "jros2.graph.compatibility";
   private static final long TIMEOUT_MS = 10000;

   @AfterEach
   public void clearCompatibility()
   {
      System.clearProperty(COMPATIBILITY_PROPERTY);
   }

   private static boolean contains(List<RmwDdsCommonMessages.Gid> gids, byte[] guid)
   {
      for (RmwDdsCommonMessages.Gid gid : gids)
      {
         if (java.util.Arrays.equals(gid.getGuid(), guid))
         {
            return true;
         }
      }
      return false;
   }

   private static boolean describes(ParticipantEntitiesInfo info, String namespace, String name, byte[][] readers, byte[][] writers)
   {
      if (info.getNodeEntitiesInfoSeq().size() != 1)
      {
         return false;
      }
      NodeEntitiesInfo node = info.getNodeEntitiesInfoSeq().get(0);
      if (!node.getNodeNamespace().equals(namespace) || !node.getNodeName().equals(name))
      {
         return false;
      }
      if (node.getReaderGids().size() != readers.length || node.getWriterGids().size() != writers.length)
      {
         return false;
      }
      for (byte[] reader : readers)
      {
         if (!contains(node.getReaderGids(), reader))
         {
            return false;
         }
      }
      for (byte[] writer : writers)
      {
         if (!contains(node.getWriterGids(), writer))
         {
            return false;
         }
      }
      return true;
   }

   @Test
   @Timeout(60)
   public void testNodeAndEndpointsAreAnnounced() throws InterruptedException
   {
      System.setProperty(COMPATIBILITY_PROPERTY, "IRON_PLUS");
      ROS2Node node = new ROS2Node("graph_node", ROS2NodeOptions.defaults().namespace("graph_test"));
      byte[] participantGuid = node.getParticipant().getGuid();
      assertEquals(ROS2Graph.Compatibility.IRON_PLUS, node.getParticipant().getGraph().getCompatibility());

      ROS2Publisher<std_msgs.String_> publisher = node.createPublisher(new ROS2Topic<>("chatter", std_msgs.String_.class));
      ROS2Subscription<example_interfaces.Bool> subscription = node.createSubscription(new ROS2Topic<>("~/cmd", example_interfaces.Bool.class));
      byte[] writerGuid = publisher.getGuid().getValue().clone();
      byte[] readerGuid = subscription.getGuid().getValue().clone();
      assertEquals("/graph_test/chatter", publisher.getTopicName());
      assertEquals("/graph_test/graph_node/cmd", subscription.getTopicName());

      // The probe joins late: TRANSIENT_LOCAL delivers the latest state
      try (ROS2GraphProbe probe = new ROS2GraphProbe(node.getDomainId()))
      {
         ParticipantEntitiesInfo info = probe.awaitLatest(participantGuid,
                                                          16,
                                                          i -> describes(i, "/graph_test", "graph_node", new byte[][] {readerGuid}, new byte[][] {writerGuid}),
                                                          TIMEOUT_MS);
         assertNotNull(info, "the node with its reader and writer was not announced");
         assertArrayEquals(participantGuid, info.getGid().getGuid());
         // The participant GUID ends with the participant entity ID, and the graph writer itself is never listed
         assertEquals((byte) 0xc1, participantGuid[15]);
         byte[] graphWriter = node.getParticipant().getGraph().getWriterGuid();
         assertFalse(contains(info.getNodeEntitiesInfoSeq().get(0).getWriterGids(), graphWriter));

         // Destroying an endpoint publishes the new state
         assertTrue(node.destroyPublisher(publisher));
         assertNotNull(probe.awaitLatest(participantGuid,
                                         16,
                                         i -> describes(i, "/graph_test", "graph_node", new byte[][] {readerGuid}, new byte[0][]),
                                         TIMEOUT_MS), "the destroyed publisher is still announced");

         // Every sample is 16-byte only
         for (byte[] sample : probe.samplesFrom(participantGuid))
         {
            assertEquals(16, ROS2GraphProbe.sampleGidSize(sample));
         }

         // Closing announces an empty node list before deleting the participant
         ROS2Graph graph = node.getParticipant().getGraph();
         int publishCount = graph.getPublishCount();
         node.close();
         assertTrue(graph.isClosed());
         assertEquals(publishCount + 1, graph.getPublishCount());
         ParticipantEntitiesInfo last = ParticipantEntitiesInfo.fromBytes(graph.getLastSamples().get(0), 16);
         assertEquals(0, last.getNodeEntitiesInfoSeq().size());
         assertArrayEquals(participantGuid, last.getGid().getGuid());
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(60)
   public void testDualModePublishesBothFormats() throws InterruptedException
   {
      System.setProperty(COMPATIBILITY_PROPERTY, "DUAL");
      ROS2Node node = new ROS2Node("dual_node", ROS2NodeOptions.defaults().namespace("/dual_test"));
      try (ROS2GraphProbe probe = new ROS2GraphProbe(node.getDomainId()))
      {
         assertEquals(ROS2Graph.Compatibility.DUAL, node.getParticipant().getGraph().getCompatibility());
         byte[] participantGuid = node.getParticipant().getGuid();
         ROS2Publisher<std_msgs.String_> publisher = node.createPublisher(new ROS2Topic<>("/dual_test/chatter", std_msgs.String_.class));
         byte[] writerGuid = publisher.getGuid().getValue().clone();

         assertNotNull(probe.awaitLatest(participantGuid,
                                         24,
                                         i -> describes(i, "/dual_test", "dual_node", new byte[0][], new byte[][] {writerGuid}),
                                         TIMEOUT_MS), "no 24-byte sample");
         assertNotNull(probe.awaitLatest(participantGuid,
                                         16,
                                         i -> describes(i, "/dual_test", "dual_node", new byte[0][], new byte[][] {writerGuid}),
                                         TIMEOUT_MS), "no 16-byte sample");

         // Every update is a 24-byte sample followed by a 16-byte sample
         List<byte[]> samples = probe.samplesFrom(participantGuid);
         assertEquals(0, samples.size() % 2, "samples come in pairs");
         for (int i = 0; i < samples.size(); i += 2)
         {
            assertEquals(24, ROS2GraphProbe.sampleGidSize(samples.get(i)));
            assertEquals(16, ROS2GraphProbe.sampleGidSize(samples.get(i + 1)));
         }

         List<byte[]> last = node.getParticipant().getGraph().getLastSamples();
         assertEquals(2, last.size());
         assertEquals(24, ROS2GraphProbe.sampleGidSize(last.get(0)));
         assertEquals(16, ROS2GraphProbe.sampleGidSize(last.get(1)));
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(60)
   public void testHumbleModePublishes24ByteGids() throws InterruptedException
   {
      System.setProperty(COMPATIBILITY_PROPERTY, "HUMBLE");
      ROS2Node node = new ROS2Node("humble_node");
      try (ROS2GraphProbe probe = new ROS2GraphProbe(node.getDomainId()))
      {
         byte[] participantGuid = node.getParticipant().getGuid();
         ParticipantEntitiesInfo info = probe.awaitLatest(participantGuid, 24, i -> describes(i, "/", "humble_node", new byte[0][], new byte[0][]), TIMEOUT_MS);
         assertNotNull(info);
         assertEquals(24, info.getGid().getGidSize());
         for (byte[] sample : probe.samplesFrom(participantGuid))
         {
            assertEquals(24, ROS2GraphProbe.sampleGidSize(sample));
         }
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(60)
   public void testAutoModeFollowsRosDistro()
   {
      System.setProperty(COMPATIBILITY_PROPERTY, "AUTO");
      String distroProperty = "ros.distro";
      String previous = System.getProperty(distroProperty);
      try
      {
         System.setProperty(distroProperty, "humble");
         assertEquals(ROS2Graph.Compatibility.HUMBLE, ROS2Graph.compatibilityFromSettings());
         System.setProperty(distroProperty, "jazzy");
         assertEquals(ROS2Graph.Compatibility.IRON_PLUS, ROS2Graph.compatibilityFromSettings());
         System.setProperty(distroProperty, "some_future_distro");
         assertEquals(ROS2Graph.Compatibility.DUAL, ROS2Graph.compatibilityFromSettings());

         ROS2Node node = new ROS2Node("auto_node");
         try
         {
            assertEquals(ROS2Graph.Compatibility.DUAL, node.getParticipant().getGraph().getCompatibility());
         }
         finally
         {
            node.close();
         }
      }
      finally
      {
         if (previous == null)
         {
            System.clearProperty(distroProperty);
         }
         else
         {
            System.setProperty(distroProperty, previous);
         }
      }
   }

   @Test
   @Timeout(60)
   public void testGraphStateAfterEveryChange()
   {
      System.setProperty(COMPATIBILITY_PROPERTY, "IRON_PLUS");
      ROS2Node node = new ROS2Node("state_node");
      try
      {
         ROS2Graph graph = node.getParticipant().getGraph();
         byte[] participantGuid = node.getParticipant().getGuid();

         ParticipantEntitiesInfo initial = ParticipantEntitiesInfo.fromBytes(graph.getLastSamples().get(0), 16);
         assertTrue(describes(initial, "/", "state_node", new byte[0][], new byte[0][]));
         assertArrayEquals(participantGuid, initial.getGid().getGuid());

         int count = graph.getPublishCount();
         ROS2Subscription<std_msgs.String_> subscription = node.createSubscription(new ROS2Topic<>("/state/a", std_msgs.String_.class));
         assertEquals(count + 1, graph.getPublishCount());
         byte[] readerGuid = subscription.getGuid().getValue().clone();
         assertTrue(describes(ParticipantEntitiesInfo.fromBytes(graph.getLastSamples().get(0), 16), "/", "state_node", new byte[][] {readerGuid}, new byte[0][]));

         ROS2Publisher<std_msgs.String_> publisher = node.createPublisher(new ROS2Topic<>("/state/a", std_msgs.String_.class));
         assertEquals(count + 2, graph.getPublishCount());
         byte[] writerGuid = publisher.getGuid().getValue().clone();
         assertTrue(describes(ParticipantEntitiesInfo.fromBytes(graph.getLastSamples().get(0), 16),
                              "/",
                              "state_node",
                              new byte[][] {readerGuid},
                              new byte[][] {writerGuid}));

         assertTrue(node.destroySubscription(subscription));
         assertEquals(count + 3, graph.getPublishCount());
         assertTrue(describes(ParticipantEntitiesInfo.fromBytes(graph.getLastSamples().get(0), 16), "/", "state_node", new byte[0][], new byte[][] {writerGuid}));

         // A failed creation does not change the graph
         assertThrows(IllegalArgumentException.class, () -> node.createPublisher(new ROS2Topic<>("/state/a", example_interfaces.Bool.class)));
         assertEquals(count + 3, graph.getPublishCount());
      }
      finally
      {
         node.close();
      }
   }
}
