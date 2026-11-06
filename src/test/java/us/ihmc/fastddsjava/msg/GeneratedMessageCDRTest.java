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
package us.ihmc.fastddsjava.msg;

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.CDRSerializable;
import us.ihmc.jros2.ROS2Message;

import java.lang.management.ManagementFactory;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wire compatibility of the generated message classes with ROS 2 Jazzy. The expected payloads were produced by
 * {@code rclpy.serialization.serialize_message} (rmw_fastrtps_cpp, Fast-CDR 2). In the "normalized" vectors the
 * uninitialized alignment padding of rmw is shown as zero and the trailing slack of the serialized-size estimate that
 * {@code serialize_message} returns is cut off; the raw vectors are used unchanged for deserialization.
 */
public class GeneratedMessageCDRTest
{
   private static final HexFormat HEX = HexFormat.of();
   /** rclpy serialization of a sensor_msgs/JointState, including 8 trailing bytes of serialized-size estimate slack. */
   private static final String JOINT_STATE_RAW = "0001000005000000070000000700000062617365c3a9000003000000030000006a310000080000006a6fc3af6e74320002000000780000"
                                                 + "0003000000000000000000f03f00000000000004c00000000000000a400000000001000000000000000000e03f000000001041930a";

   private static byte[] serialize(CDRSerializable message)
   {
      CDRBuffer buffer = new CDRBuffer();
      buffer.writePayloadHeader();
      message.serialize(buffer);
      byte[] bytes = new byte[buffer.getBufferUnsafe().position()];
      for (int i = 0; i < bytes.length; i++)
         bytes[i] = buffer.getBufferUnsafe().get(i);
      assertEquals(bytes.length - 4, message.calculateSizeBytes(0), "calculateSizeBytes must be exact for " + message.getClass().getSimpleName());
      return bytes;
   }

   private static <T extends CDRSerializable> T deserialize(String payloadHex, T message)
   {
      byte[] payload = HEX.parseHex(payloadHex);
      CDRBuffer buffer = new CDRBuffer();
      buffer.ensureRemainingCapacity(payload.length);
      buffer.getBufferUnsafe().put(payload);
      buffer.beginRead(payload.length);
      buffer.readPayloadHeader();
      message.deserialize(buffer);
      return message;
   }

   private static void assertSerializesTo(String expectedHex, CDRSerializable message)
   {
      assertEquals(expectedHex, HEX.formatHex(serialize(message)), message.getClass().getSimpleName());
   }

   @Test
   public void testStdMsgsString()
   {
      std_msgs.String_ message = new std_msgs.String_();
      message.setData("hello");
      assertSerializesTo("000100000600000068656c6c6f00", message);
      message.setData("");
      assertSerializesTo("000100000100000000", message);
      message.setData("héllo wörld € 𝄞");
      assertSerializesTo("000100001700000068c3a96c6c6f2077c3b6726c6420e282ac20f09d849e00", message);

      assertEquals("héllo wörld € 𝄞",
                   deserialize("000100001700000068c3a96c6c6f2077c3b6726c6420e282ac20f09d849e00", new std_msgs.String_()).getDataAsString());
      assertEquals("", deserialize("000100000100000000", new std_msgs.String_()).getDataAsString());
      assertThrows(IllegalArgumentException.class, () ->
      {
         std_msgs.String_ withNul = new std_msgs.String_();
         withNul.setData("a\0b");
         serialize(withNul);
      });
   }

   @Test
   public void testEmptyMessagesSerializeTheDummyMember()
   {
      // rclpy: 0001000000000000 (header, uint8 structure_needs_at_least_one_member, 3 bytes of payload padding)
      assertSerializesTo("0001000000", new std_msgs.Empty());
      assertSerializesTo("0001000000", new example_interfaces.Empty());
      assertSerializesTo("0001000000", new rcl_interfaces.ParameterType()); // constants only
      assertSerializesTo("0001000000", new statistics_msgs.StatisticDataType()); // constants only

      assertNotNull(deserialize("0001000000000000", new std_msgs.Empty()));
      assertNotNull(deserialize("00010000", new std_msgs.Empty()), "payload of jros2 1.5 (no member) must still be accepted");
   }

   @Test
   public void testJointState()
   {
      String raw = JOINT_STATE_RAW;
      String normalized = raw.substring(0, raw.length() - 16); // 8 bytes of estimate slack

      sensor_msgs.JointState message = new sensor_msgs.JointState();
      message.getHeader().getStamp().setSec(5);
      message.getHeader().getStamp().setNanosec(7);
      message.getHeader().setFrameId("baseé");
      message.getName().add("j1");
      message.getName().add("joïnt2");
      message.getName().add("x");
      message.getPosition().addAll(new double[] {1.0, -2.5, 3.25});
      message.getEffort().add(0.5);
      assertSerializesTo(normalized, message);

      sensor_msgs.JointState received = deserialize(raw, new sensor_msgs.JointState());
      assertEquals(message.toString(), received.toString());
      assertEquals("joïnt2", received.getName().getAsString(1));
   }

   @Test
   public void testFloat64MultiArray()
   {
      String hex = "00010000020000000500000072c3b677000000000200000006000000020000006300000003000000030000000100000006000000000000000000f83f0000000000000440"
                   + "00000000000008c0000000000000104000000000000014400000000000001840";
      std_msgs.Float64MultiArray message = new std_msgs.Float64MultiArray();
      std_msgs.MultiArrayDimension row = message.getLayout().getDim().add();
      row.setLabel("röw");
      row.setSize(2);
      row.setStride(6);
      std_msgs.MultiArrayDimension column = message.getLayout().getDim().add();
      column.setLabel("c");
      column.setSize(3);
      column.setStride(3);
      message.getLayout().setDataOffset(1);
      message.getData().addAll(new double[] {1.5, 2.5, -3.0, 4.0, 5.0, 6.0});
      assertSerializesTo(hex, message);
      assertEquals(message.toString(), deserialize(hex, new std_msgs.Float64MultiArray()).toString());
   }

   @Test
   public void testSmallIntegerSequences()
   {
      std_msgs.UInt16MultiArray uint16 = new std_msgs.UInt16MultiArray();
      uint16.getData().addAll(new short[] {1, (short) 65535, 300});
      assertSerializesTo("000100000000000000000000030000000100ffff2c01", uint16);
      std_msgs.UInt16MultiArray receivedUInt16 = deserialize("000100000000000000000000030000000100ffff2c01", new std_msgs.UInt16MultiArray());
      assertEquals(65535, Short.toUnsignedInt(receivedUInt16.getData().get(1)));

      std_msgs.Int8MultiArray int8 = new std_msgs.Int8MultiArray();
      int8.getData().addAll(new byte[] {-1, 2, -128, 127});
      assertSerializesTo("00010000000000000000000004000000ff02807f", int8);
      assertEquals(int8.toString(), deserialize("00010000000000000000000004000000ff02807f", new std_msgs.Int8MultiArray()).toString());
   }

   @Test
   public void testPoseWithCovarianceFixedArray()
   {
      StringBuilder hex = new StringBuilder("00010000000000000000f03f");
      hex.append("0000000000000000".repeat(5)).append("000000000000f03f");
      geometry_msgs.PoseWithCovariance message = new geometry_msgs.PoseWithCovariance();
      message.getPose().getPosition().setX(1.0);
      message.getPose().getOrientation().setW(1.0);
      for (int i = 0; i < 36; i++)
      {
         message.getCovariance()[i] = i;
         CDRBuffer one = new CDRBuffer();
         one.writePayloadHeader();
         one.writeDouble(i);
         for (int b = 4; b < 12; b++)
            hex.append(String.format("%02x", one.getBufferUnsafe().get(b)));
      }
      assertSerializesTo(hex.toString(), message);
      geometry_msgs.PoseWithCovariance received = deserialize(hex.toString(), new geometry_msgs.PoseWithCovariance());
      assertArrayEquals(message.getCovariance(), received.getCovariance());
      assertEquals(1.0, received.getPose().getOrientation().getW());

      geometry_msgs.PoseWithCovariance copy = new geometry_msgs.PoseWithCovariance(received);
      received.getCovariance()[3] = -1.0;
      assertEquals(3.0, copy.getCovariance()[3], "set() must copy fixed-size arrays, not share them");
   }

   @Test
   public void testImageWithGarbagePadding()
   {
      String raw = "0001000000000000000000000400000063616d000200000003000000060000006d6f6e6f380000720300000006000000000102fafbff";
      String normalized = raw.replace("6d6f6e6f38000072", "6d6f6e6f38000000"); // is_bigendian + 1 byte of uninitialized padding
      sensor_msgs.Image message = new sensor_msgs.Image();
      message.getHeader().setFrameId("cam");
      message.setHeight(2);
      message.setWidth(3);
      message.setEncoding("mono8");
      message.setStep(3);
      message.getData().addAll(new byte[] {0, 1, 2, (byte) 250, (byte) 251, (byte) 255});
      assertSerializesTo(normalized, message);
      assertEquals(message.toString(), deserialize(raw, new sensor_msgs.Image()).toString());
   }

   @Test
   public void testBoundedSequence()
   {
      String hex = "000100000100000003000000000000000000f03f0000000000000040000000000000084000000000";
      shape_msgs.SolidPrimitive message = new shape_msgs.SolidPrimitive();
      message.setType(shape_msgs.SolidPrimitive.BOX);
      message.getDimensions().addAll(new double[] {1.0, 2.0, 3.0});
      assertSerializesTo(hex, message);
      assertEquals(message.toString(), deserialize(hex, new shape_msgs.SolidPrimitive()).toString());

      // rclcpp subscribers crash (std::length_error) on over-bound sequences, so jros2 never produces one
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> message.getDimensions().add(4.0));
      assertTrue(exception.getMessage().startsWith("array size exceeds upper bound"));
      assertEquals(3, message.getDimensions().size());
   }

   @Test
   public void testCharIsUInt8()
   {
      std_msgs.Char message = new std_msgs.Char();
      message.setData((char) 200);
      assertSerializesTo("00010000c8", message);
      assertEquals(200, deserialize("00010000c8000000", new std_msgs.Char()).getData());
   }

   @Test
   public void testWString()
   {
      example_interfaces.WString message = new example_interfaces.WString();
      message.setData("😀x𐐷");
      assertSerializesTo("00010000050000003dd8000000de00007800000001d8000037dc0000", message);
      assertEquals("😀x𐐷", deserialize("00010000050000003dd8000000de00007800000001d8000037dc0000", new example_interfaces.WString()).getDataAsString());
   }

   @Test
   public void testGeneratedTypeHashes()
   {
      assertEquals("RIHS01_df668c740482bbd48fb39d76a70dfd4bd59db1288021743503259e948f6b1a18", ROS2Message.getTypeHashFromMessageClass(std_msgs.String_.class));
      assertEquals("RIHS01_20b625256f32d5dbc0d04fee44f43c41e51c70d3502f84b4a08e7a9c26a96312", ROS2Message.getTypeHashFromMessageClass(std_msgs.Empty.class));
      assertNull(ROS2Message.getTypeHashFromMessageClass(TestIDLMsg.class));
      assertNull(ROS2Message.getTypeHashFromMessageClass(Object.class));

      // Malformed values are never handed out (rmw would fail to parse them from USER_DATA)
      assertNull(ROS2Message.getTypeHashFromMessageClass(NullTypeHash.class));
      assertNull(ROS2Message.getTypeHashFromMessageClass(ShortTypeHash.class));
      assertNull(ROS2Message.getTypeHashFromMessageClass(NonHexTypeHash.class));
      assertNull(ROS2Message.getTypeHashFromMessageClass(WrongVersionTypeHash.class));
      assertNull(ROS2Message.getTypeHashFromMessageClass(InstanceTypeHash.class));
      assertNull(ROS2Message.getTypeHashFromMessageClass(NonStringTypeHash.class));
      assertEquals(UpperCaseTypeHash.typeHash, ROS2Message.getTypeHashFromMessageClass(UpperCaseTypeHash.class));
   }

   public static class NullTypeHash
   {
      public static final String typeHash = null;
   }

   public static class ShortTypeHash
   {
      public static final String typeHash = "RIHS01_df668c74";
   }

   public static class NonHexTypeHash
   {
      public static final String typeHash = "RIHS01_df668c740482bbd48fb39d76a70dfd4bd59db1288021743503259e948f6b1a1g";
   }

   public static class WrongVersionTypeHash
   {
      public static final String typeHash = "RIHS02_df668c740482bbd48fb39d76a70dfd4bd59db1288021743503259e948f6b1a18";
   }

   public static class InstanceTypeHash
   {
      public final String typeHash = "RIHS01_df668c740482bbd48fb39d76a70dfd4bd59db1288021743503259e948f6b1a18";
   }

   public static class NonStringTypeHash
   {
      public static final Object typeHash = new StringBuilder("RIHS01_df668c740482bbd48fb39d76a70dfd4bd59db1288021743503259e948f6b1a18");
   }

   public static class UpperCaseTypeHash
   {
      public static final String typeHash = "RIHS01_DF668C740482BBD48FB39D76A70DFD4BD59DB1288021743503259E948F6B1A18";
   }

   @Test
   public void testSerializeDeserializeSetAreAllocationFree()
   {
      ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
      if (!threadMXBean.isThreadAllocatedMemorySupported())
         return;
      threadMXBean.setThreadAllocatedMemoryEnabled(true);

      std_msgs.String_ string = new std_msgs.String_();
      string.setData("héllo wörld € 𝄞");
      sensor_msgs.JointState jointState = deserialize(JOINT_STATE_RAW, new sensor_msgs.JointState());
      sensor_msgs.Image image = new sensor_msgs.Image();
      image.setEncoding("rgb8");
      image.getData().addAll(new byte[640 * 4]);
      geometry_msgs.PoseWithCovariance pose = new geometry_msgs.PoseWithCovariance();
      geometry_msgs.PoseArray poses = new geometry_msgs.PoseArray();
      for (int i = 0; i < 10; i++)
         poses.getPoses().add().getPosition().setX(i);
      CDRSerializable[] messages = {string, jointState, image, pose, new std_msgs.Empty(), poses};
      CDRSerializable[] targets = {new std_msgs.String_(), new sensor_msgs.JointState(), new sensor_msgs.Image(), new geometry_msgs.PoseWithCovariance(),
                                   new std_msgs.Empty(), new geometry_msgs.PoseArray()};
      CDRBuffer buffer = new CDRBuffer();

      Runnable roundTrip = () ->
      {
         for (int m = 0; m < messages.length; m++)
         {
            buffer.rewind();
            buffer.writePayloadHeader();
            messages[m].serialize(buffer);
            int size = buffer.getBufferUnsafe().position();
            messages[m].calculateSizeBytes(0);
            buffer.beginRead(size);
            buffer.readPayloadHeader();
            targets[m].deserialize(buffer);
         }
         jointState.set((sensor_msgs.JointState) targets[1]);
         string.set((std_msgs.String_) targets[0]);
         image.set((sensor_msgs.Image) targets[2]);
         pose.set((geometry_msgs.PoseWithCovariance) targets[3]);
         poses.set((geometry_msgs.PoseArray) targets[5]);
      };

      for (int i = 0; i < 20000; i++)
         roundTrip.run();

      // JIT activity can show up as a few bytes in one round; a real per-message allocation shows up in every round
      long threadId = Thread.currentThread().getId();
      long minimumAllocated = Long.MAX_VALUE;
      for (int round = 0; round < 5; round++)
      {
         long before = threadMXBean.getThreadAllocatedBytes(threadId);
         for (int i = 0; i < 2000; i++)
            roundTrip.run();
         minimumAllocated = Math.min(minimumAllocated, threadMXBean.getThreadAllocatedBytes(threadId) - before);
      }

      assertTrue(minimumAllocated <= 512, "serialize/deserialize/set allocated " + minimumAllocated + " bytes for 2000 round trips in steady state");
      assertEquals(string.getDataAsString(), ((std_msgs.String_) targets[0]).getDataAsString());
      assertEquals(poses.toString(), targets[5].toString());
   }

   @Test
   public void testSetDeepCopiesSequencesOfNestedMessages()
   {
      // AsyncROS2Publisher snapshots messages with set(); sharing element objects would let later changes of the user's
      // message (or a deserialize into the copy) tear the queued message.
      geometry_msgs.PoseArray source = new geometry_msgs.PoseArray();
      source.getPoses().add().getPosition().setX(1.0);
      source.getPoses().add().getPosition().setX(2.0);

      geometry_msgs.PoseArray copy = new geometry_msgs.PoseArray(source);
      assertEquals(2, copy.getPoses().size());
      for (int i = 0; i < 2; i++)
      {
         assertNotSame(source.getPoses().get(i), copy.getPoses().get(i), "element " + i + " must be copied, not shared");
      }

      source.getPoses().get(0).getPosition().setX(-1.0);
      assertEquals(1.0, copy.getPoses().get(0).getPosition().getX(), "changing the source must not change the copy");
      copy.getPoses().get(1).getPosition().setX(-2.0);
      assertEquals(2.0, source.getPoses().get(1).getPosition().getX(), "changing the copy must not change the source");

      // Deserializing into the copy must not write through to the source
      byte[] payload = serialize(copy);
      deserialize(HEX.formatHex(payload), copy);
      assertEquals(-1.0, source.getPoses().get(0).getPosition().getX());
      assertEquals(2.0, source.getPoses().get(1).getPosition().getX());

      // Shrinking keeps the spare element objects for reuse, growing reuses them
      geometry_msgs.PoseArray single = new geometry_msgs.PoseArray();
      single.getPoses().add().getPosition().setY(5.0);
      geometry_msgs.Pose spare = copy.getPoses().get(1);
      copy.set(single);
      assertEquals(1, copy.getPoses().size());
      assertEquals(5.0, copy.getPoses().get(0).getPosition().getY());
      copy.set(source);
      assertEquals(2, copy.getPoses().size());
      assertSame(spare, copy.getPoses().get(1), "spare elements are reused");
      assertEquals(source.toString(), copy.toString());

      // IDLObjectSequence.set(other) copies references (as in jros2 1.5); a message set() from the same source afterward
      // replaces the shared elements instead of writing into them
      geometry_msgs.PoseArray aliased = new geometry_msgs.PoseArray();
      aliased.getPoses().set(source.getPoses());
      assertSame(source.getPoses().get(0), aliased.getPoses().get(0));
      aliased.set(source);
      for (int i = 0; i < 2; i++)
      {
         assertNotSame(source.getPoses().get(i), aliased.getPoses().get(i));
      }
      assertEquals(source.toString(), aliased.toString());
      source.getPoses().get(0).getPosition().setX(-3.0);
      assertEquals(-1.0, aliased.getPoses().get(0).getPosition().getX());
      source.getPoses().get(0).getPosition().setX(-1.0);

      // Nested sequences in nested sequences, and bounds are kept
      visualization_msgs.MarkerArray markers = new visualization_msgs.MarkerArray();
      visualization_msgs.Marker marker = markers.getMarkers().add();
      marker.getPoints().add().setZ(3.0);
      marker.setText("märker");
      visualization_msgs.MarkerArray markersCopy = new visualization_msgs.MarkerArray(markers);
      marker.getPoints().get(0).setZ(4.0);
      assertEquals(3.0, markersCopy.getMarkers().get(0).getPoints().get(0).getZ());
      assertEquals("märker", markersCopy.getMarkers().get(0).getTextAsString());

      copy.set(copy);
      assertEquals(source.toString(), copy.toString(), "set(this) is a no-op");
   }

   @Test
   public void testUnderestimatedSizeFromLegacyGeneratedClassStillSerializes()
   {
      // Classes generated by jros2 1.5 estimate string sizes in chars instead of UTF-8 bytes
      CDRSerializable legacy = new CDRSerializable()
      {
         private final StringBuilder text = new StringBuilder("€".repeat(100));

         @Override
         public int calculateSizeBytes(int currentAlignment)
         {
            return 4 + CDRBuffer.alignment(currentAlignment, 4) + text.length() + 1;
         }

         @Override
         public void serialize(CDRBuffer buffer)
         {
            buffer.writeString(text);
         }

         @Override
         public void deserialize(CDRBuffer buffer)
         {
            buffer.readString(text);
         }
      };

      CDRBuffer buffer = new CDRBuffer();
      buffer.ensureRemainingCapacity(4 + legacy.calculateSizeBytes(0));
      buffer.writePayloadHeader();
      legacy.serialize(buffer);
      assertEquals(4 + 4 + 300 + 1, buffer.getBufferUnsafe().position());
   }
}
