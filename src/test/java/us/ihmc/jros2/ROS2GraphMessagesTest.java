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
import us.ihmc.jros2.ROS2Graph.Compatibility;
import us.ihmc.jros2.RmwDdsCommonMessages.Gid;
import us.ihmc.jros2.RmwDdsCommonMessages.NodeEntitiesInfo;
import us.ihmc.jros2.RmwDdsCommonMessages.ParticipantEntitiesInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Byte-exact tests of the {@code ros_discovery_info} messages against samples that ROS 2 Humble, Jazzy, Kilted and
 * Lyrical accepted (captured from the jros2 graph prototype and from a Jazzy talker).
 */
public class ROS2GraphMessagesTest
{
   /** 16-byte sample of node /jros2/jnode with 2 readers and 2 writers (120 bytes), accepted by jazzy/kilted/lyrical. */
   private static final String SAMPLE_16 = "00010000" + "010f8de1af11982700000000000001c1" + "01000000" + "070000002f6a726f73320000" + "060000006a6e6f6465000000"
                                           + "02000000" + "010f8de1af1198270000000000000204" + "010f8de1af1198270000000000000304" + "02000000"
                                           + "010f8de1af1198270000000000000103" + "010f8de1af1198270000000000000403";
   /** The same node with 24-byte GIDs (160 bytes), accepted by humble. */
   private static final String SAMPLE_24 = "00010000010f8de1af11982700000000000001c1000000000000000001000000070000002f6a726f73320000060000006a6e6f646500000002000000"
                                           + "010f8de1af11982700000000000002040000000000000000010f8de1af1198270000000000000304000000000000000002000000"
                                           + "010f8de1af11982700000000000001030000000000000000010f8de1af11982700000000000004030000000000000000";

   private static final byte[] PARTICIPANT = hex("010f8de1af11982700000000000001c1");
   private static final byte[] READER_1 = hex("010f8de1af1198270000000000000204");
   private static final byte[] READER_2 = hex("010f8de1af1198270000000000000304");
   private static final byte[] WRITER_1 = hex("010f8de1af1198270000000000000103");
   private static final byte[] WRITER_2 = hex("010f8de1af1198270000000000000403");

   static byte[] hex(String hex)
   {
      byte[] bytes = new byte[hex.length() / 2];
      for (int i = 0; i < bytes.length; ++i)
      {
         bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
      }
      return bytes;
   }

   static String hex(byte[] bytes)
   {
      StringBuilder builder = new StringBuilder();
      for (byte b : bytes)
      {
         builder.append(String.format("%02x", b & 0xff));
      }
      return builder.toString();
   }

   private static List<ROS2Graph.NodeSnapshot> jnode()
   {
      return Collections.singletonList(new ROS2Graph.NodeSnapshot("/jros2", "jnode", Arrays.asList(READER_1, READER_2), Arrays.asList(WRITER_1, WRITER_2)));
   }

   @Test
   public void testSerialize16ByteGids()
   {
      ParticipantEntitiesInfo message = ROS2Graph.createMessage(16, PARTICIPANT, jnode());
      byte[] bytes = message.toBytes();
      assertEquals(120, bytes.length);
      assertEquals(SAMPLE_16, hex(bytes));
      assertEquals(bytes.length - 4, message.calculateSizeBytes(0));
   }

   @Test
   public void testSerialize24ByteGids()
   {
      ParticipantEntitiesInfo message = ROS2Graph.createMessage(24, PARTICIPANT, jnode());
      byte[] bytes = message.toBytes();
      assertEquals(160, bytes.length);
      assertEquals(SAMPLE_24, hex(bytes));
      assertEquals(bytes.length - 4, message.calculateSizeBytes(0));
   }

   @Test
   public void testSerializeJazzyTalkerShutdownSample()
   {
      // A Jazzy talker in /demo_ns during shutdown: one node, no entities (60 bytes). rmw leaves garbage in the padding
      // after "/demo_ns"; jros2 writes zeros.
      byte[] participant = hex("010f01bd8c7a29f600000000000001c1");
      List<ROS2Graph.NodeSnapshot> nodes = Collections.singletonList(new ROS2Graph.NodeSnapshot("/demo_ns",
                                                                                                "talker",
                                                                                                Collections.emptyList(),
                                                                                                Collections.emptyList()));
      byte[] bytes = ROS2Graph.createMessage(16, participant, nodes).toBytes();
      String expected = "00010000" + "010f01bd8c7a29f600000000000001c1" + "01000000" + "090000002f64656d6f5f6e7300" + "000000" + "0700000074616c6b657200" + "00"
                        + "00000000" + "00000000";
      assertEquals(60, bytes.length);
      assertEquals(expected, hex(bytes));
   }

   @Test
   public void testSerializeEmptyNodeList()
   {
      byte[] bytes16 = ROS2Graph.createMessage(16, PARTICIPANT, Collections.emptyList()).toBytes();
      assertEquals("00010000" + "010f8de1af11982700000000000001c1" + "00000000", hex(bytes16));
      byte[] bytes24 = ROS2Graph.createMessage(24, PARTICIPANT, Collections.emptyList()).toBytes();
      assertEquals("00010000" + "010f8de1af11982700000000000001c1" + "0000000000000000" + "00000000", hex(bytes24));
   }

   @Test
   public void testSerializeMultipleNodes()
   {
      List<ROS2Graph.NodeSnapshot> nodes = Arrays.asList(new ROS2Graph.NodeSnapshot("/", "a", Collections.singletonList(READER_1), Collections.emptyList()),
                                                         new ROS2Graph.NodeSnapshot("/ns", "bb", Collections.emptyList(), Collections.singletonList(WRITER_1)));
      for (int gidSize : new int[] {16, 24})
      {
         ParticipantEntitiesInfo message = ROS2Graph.createMessage(gidSize, PARTICIPANT, nodes);
         byte[] bytes = message.toBytes();
         assertEquals(0, bytes.length % 4, "samples are always a multiple of 4 bytes");
         assertEquals(bytes.length - 4, message.calculateSizeBytes(0));

         ParticipantEntitiesInfo parsed = ParticipantEntitiesInfo.fromBytes(bytes, gidSize);
         assertArrayEquals(PARTICIPANT, parsed.getGid().getGuid());
         assertEquals(2, parsed.getNodeEntitiesInfoSeq().size());
         NodeEntitiesInfo a = parsed.getNodeEntitiesInfoSeq().get(0);
         assertEquals("/", a.getNodeNamespace());
         assertEquals("a", a.getNodeName());
         assertEquals(1, a.getReaderGids().size());
         assertArrayEquals(READER_1, a.getReaderGids().get(0).getGuid());
         assertEquals(0, a.getWriterGids().size());
         NodeEntitiesInfo b = parsed.getNodeEntitiesInfoSeq().get(1);
         assertEquals("/ns", b.getNodeNamespace());
         assertEquals("bb", b.getNodeName());
         assertEquals(0, b.getReaderGids().size());
         assertArrayEquals(WRITER_1, b.getWriterGids().get(0).getGuid());
      }
   }

   @Test
   public void testRoundTripResearchSamples()
   {
      ParticipantEntitiesInfo parsed16 = ParticipantEntitiesInfo.fromBytes(hex(SAMPLE_16), 16);
      assertEquals(SAMPLE_16, hex(parsed16.toBytes()));
      assertEquals("/jros2", parsed16.getNodeEntitiesInfoSeq().get(0).getNodeNamespace());
      assertEquals("jnode", parsed16.getNodeEntitiesInfoSeq().get(0).getNodeName());

      ParticipantEntitiesInfo parsed24 = ParticipantEntitiesInfo.fromBytes(hex(SAMPLE_24), 24);
      assertEquals(SAMPLE_24, hex(parsed24.toBytes()));
      Gid gid = parsed24.getNodeEntitiesInfoSeq().get(0).getWriterGids().get(1);
      assertEquals(24, gid.getData().length);
      assertArrayEquals(WRITER_2, gid.getGuid());

      ParticipantEntitiesInfo copy = new ParticipantEntitiesInfo(24);
      copy.set(parsed24);
      assertEquals(SAMPLE_24, hex(copy.toBytes()));
   }

   @Test
   public void testMismatchedGidSizeIsRejected()
   {
      // A Humble reader decoding an Iron+ sample: reading 24 GID bytes leaves an implausible sequence length
      assertThrows(IllegalArgumentException.class, () -> ParticipantEntitiesInfo.fromBytes(hex(SAMPLE_16), 24));
   }

   @Test
   public void testMalformedInputIsRejected()
   {
      byte[] sample = hex(SAMPLE_16);
      assertThrows(IllegalArgumentException.class, () -> ParticipantEntitiesInfo.fromBytes(new byte[2], 16));
      assertThrows(IllegalArgumentException.class, () -> ParticipantEntitiesInfo.fromBytes(Arrays.copyOf(sample, 10), 16));
      assertThrows(IllegalArgumentException.class, () -> ParticipantEntitiesInfo.fromBytes(Arrays.copyOf(sample, 60), 16));

      // A node count of 0x7fffffff must not allocate
      byte[] hugeCount = sample.clone();
      hugeCount[20] = (byte) 0xff;
      hugeCount[21] = (byte) 0xff;
      hugeCount[22] = (byte) 0xff;
      hugeCount[23] = (byte) 0x7f;
      assertThrows(IllegalArgumentException.class, () -> ParticipantEntitiesInfo.fromBytes(hugeCount, 16));

      // A negative string length
      byte[] negativeLength = sample.clone();
      negativeLength[27] = (byte) 0x80;
      assertThrows(IllegalArgumentException.class, () -> ParticipantEntitiesInfo.fromBytes(negativeLength, 16));
   }

   @Test
   public void testGid()
   {
      Gid gid = new Gid(24, WRITER_1);
      assertEquals(24, gid.getGidSize());
      assertArrayEquals(WRITER_1, gid.getGuid());
      for (int i = 16; i < 24; ++i)
      {
         assertEquals(0, gid.getData()[i]);
      }
      assertEquals("01.0f.8d.e1.af.11.98.27.00.00.00.00.00.00.01.03.00.00.00.00.00.00.00.00", gid.toString());
      assertEquals(new Gid(24, WRITER_1), gid);
      assertEquals(new Gid(24, WRITER_1).hashCode(), gid.hashCode());
      assertNotEquals(new Gid(16, WRITER_1), gid);

      Gid small = new Gid(16);
      small.set(gid);
      assertArrayEquals(WRITER_1, small.getData());

      assertThrows(IllegalArgumentException.class, () -> new Gid(20));
      assertThrows(IllegalArgumentException.class, () -> new Gid(16, new byte[12]));
   }

   @Test
   public void testStringBoundIsEnforced()
   {
      StringBuilder longName = new StringBuilder("/");
      for (int i = 0; i < 256; ++i)
      {
         longName.append('x');
      }
      List<ROS2Graph.NodeSnapshot> nodes = Collections.singletonList(new ROS2Graph.NodeSnapshot(longName.toString(),
                                                                                                "n",
                                                                                                Collections.emptyList(),
                                                                                                Collections.emptyList()));
      assertThrows(IllegalArgumentException.class, () -> ROS2Graph.createMessage(16, PARTICIPANT, nodes).toBytes());
   }

   @Test
   public void testTypeNamesAndHash()
   {
      assertEquals("rmw_dds_common::msg::dds_::ParticipantEntitiesInfo_", ROS2Message.getNameFromMessageClass(ParticipantEntitiesInfo.class));
      assertEquals("RIHS01_91a0593bacdcc50ea9bdcf849a938b128412cc1ea821245c663bcd26f83c295e", ROS2Message.getTypeHashFromMessageClass(ParticipantEntitiesInfo.class));
      assertEquals("ros_discovery_info", ROS2Graph.DDS_TOPIC_NAME);
      assertNotNull(ROS2Message.createInstance(ParticipantEntitiesInfo.class));
   }

   @Test
   public void testDualModePublishes24Then16()
   {
      List<byte[]> samples = ROS2Graph.serializeSamples(Compatibility.DUAL, PARTICIPANT, jnode());
      assertEquals(2, samples.size());
      assertEquals(SAMPLE_24, hex(samples.get(0)));
      assertEquals(SAMPLE_16, hex(samples.get(1)));
      assertEquals(2, Compatibility.DUAL.historyDepth());

      List<byte[]> iron = ROS2Graph.serializeSamples(Compatibility.IRON_PLUS, PARTICIPANT, jnode());
      assertEquals(1, iron.size());
      assertEquals(SAMPLE_16, hex(iron.get(0)));
      assertEquals(1, Compatibility.IRON_PLUS.historyDepth());

      List<byte[]> humble = ROS2Graph.serializeSamples(Compatibility.HUMBLE, PARTICIPANT, jnode());
      assertEquals(1, humble.size());
      assertEquals(SAMPLE_24, hex(humble.get(0)));
   }

   @Test
   public void testResolveCompatibility()
   {
      assertEquals(Compatibility.IRON_PLUS, ROS2Graph.resolveCompatibility("IRON_PLUS", "humble"));
      assertEquals(Compatibility.IRON_PLUS, ROS2Graph.resolveCompatibility("iron_plus", null));
      assertEquals(Compatibility.HUMBLE, ROS2Graph.resolveCompatibility("HUMBLE", "jazzy"));
      assertEquals(Compatibility.HUMBLE, ROS2Graph.resolveCompatibility(" humble ", null));
      assertEquals(Compatibility.DUAL, ROS2Graph.resolveCompatibility("DUAL", "jazzy"));

      // AUTO (explicit, unset or unknown) follows ROS_DISTRO
      for (String auto : new String[] {"AUTO", "auto", null, "", "bogus"})
      {
         assertEquals(Compatibility.HUMBLE, ROS2Graph.resolveCompatibility(auto, "foxy"));
         assertEquals(Compatibility.HUMBLE, ROS2Graph.resolveCompatibility(auto, "galactic"));
         assertEquals(Compatibility.HUMBLE, ROS2Graph.resolveCompatibility(auto, "humble"));
         assertEquals(Compatibility.IRON_PLUS, ROS2Graph.resolveCompatibility(auto, "iron"));
         assertEquals(Compatibility.IRON_PLUS, ROS2Graph.resolveCompatibility(auto, "jazzy"));
         assertEquals(Compatibility.IRON_PLUS, ROS2Graph.resolveCompatibility(auto, "Kilted"));
         assertEquals(Compatibility.IRON_PLUS, ROS2Graph.resolveCompatibility(auto, "lyrical"));
         assertEquals(Compatibility.IRON_PLUS, ROS2Graph.resolveCompatibility(auto, "rolling"));
         assertEquals(Compatibility.DUAL, ROS2Graph.resolveCompatibility(auto, null));
         assertEquals(Compatibility.DUAL, ROS2Graph.resolveCompatibility(auto, ""));
         assertEquals(Compatibility.DUAL, ROS2Graph.resolveCompatibility(auto, "unknown_future_distro"));
      }
   }

   @Test
   public void testCompatibilityGidSizes()
   {
      assertArrayEquals(new int[] {16}, Compatibility.IRON_PLUS.gidSizes());
      assertArrayEquals(new int[] {24}, Compatibility.HUMBLE.gidSizes());
      assertArrayEquals(new int[] {24, 16}, Compatibility.DUAL.gidSizes());
   }
}
