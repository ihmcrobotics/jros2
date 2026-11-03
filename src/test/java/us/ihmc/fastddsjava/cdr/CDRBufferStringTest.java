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
package us.ihmc.fastddsjava.cdr;

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UTF-8 strings, wide strings and wire-length validation of {@link CDRBuffer}. Expected byte vectors come from ROS 2
 * Jazzy ({@code rclpy.serialization.serialize_message}, which uses the same Fast-CDR string encoding as rclcpp) and from
 * captures of rmw_fastrtps wide strings.
 */
public class CDRBufferStringTest
{
   private static final HexFormat HEX = HexFormat.of();

   /** Writes the payload header followed by whatever {@code body} writes, and returns the body bytes. */
   private static byte[] serializeBody(java.util.function.Consumer<CDRBuffer> body)
   {
      CDRBuffer buffer = new CDRBuffer();
      buffer.writePayloadHeader();
      body.accept(buffer);
      ByteBuffer bytes = buffer.getBufferUnsafe();
      byte[] result = new byte[bytes.position() - 4];
      for (int i = 0; i < result.length; i++)
         result[i] = bytes.get(4 + i);
      return result;
   }

   /** A CDRBuffer ready to read {@code payloadHex} (which includes the 4-byte header). */
   private static CDRBuffer readerOf(String payloadHex)
   {
      return readerOf(HEX.parseHex(payloadHex));
   }

   private static CDRBuffer readerOf(byte[] payload)
   {
      CDRBuffer buffer = new CDRBuffer();
      buffer.ensureRemainingCapacity(payload.length);
      buffer.getBufferUnsafe().put(payload);
      buffer.beginRead(payload.length);
      buffer.readPayloadHeader();
      return buffer;
   }

   private static String readString(String payloadHex)
   {
      StringBuilder result = new StringBuilder("previous content");
      readerOf(payloadHex).readString(result);
      return result.toString();
   }

   // ========== UTF-8 writing ==========

   @Test
   public void testRclcppStringByteVectors()
   {
      assertEquals("0600000068656c6c6f00", HEX.formatHex(serializeBody(b -> b.writeString(new StringBuilder("hello")))));
      assertEquals("0100000000", HEX.formatHex(serializeBody(b -> b.writeString(new StringBuilder()))));
      // "héllo wörld € 𝄞": 2-, 3- and 4-byte UTF-8 sequences, length = UTF-8 bytes + NUL
      assertEquals("1700000068c3a96c6c6f2077c3b6726c6420e282ac20f09d849e00",
                   HEX.formatHex(serializeBody(b -> b.writeString(new StringBuilder("héllo wörld € 𝄞")))));
   }

   @Test
   public void testUtf8LengthAndBytesMatchJavaEncoder()
   {
      String[] samples = {"", "a", "é", "€", "😀", "\u007f\u0080߿ࠀ￿", "mixed ü世😀 end",
                          "\uD800", "\uDC00", "a\uD800b", "􏿿", "\uDC00\uD800", "\uD83D"};

      for (String sample : samples)
      {
         byte[] expected = sample.getBytes(StandardCharsets.UTF_8); // unpaired surrogates become '?'
         assertEquals(expected.length, CDRBuffer.utf8Length(sample), "utf8Length of " + escape(sample));
         assertEquals(expected.length, CDRBuffer.utf8Length(new StringBuilder(sample)));

         byte[] body = serializeBody(b -> b.writeString(sample));
         ByteBuffer wrapped = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
         assertEquals(expected.length + 1, wrapped.getInt(), "length prefix of " + escape(sample));
         for (byte value : expected)
            assertEquals(value, wrapped.get());
         assertEquals(0, wrapped.get(), "NUL terminator");
         assertFalse(wrapped.hasRemaining());
      }
   }

   @Test
   public void testRandomStringsRoundTrip()
   {
      Random random = new Random(1234);
      for (int iteration = 0; iteration < 500; iteration++)
      {
         StringBuilder value = new StringBuilder();
         int length = random.nextInt(40);
         for (int i = 0; i < length; i++)
         {
            int codePoint;
            do
            {
               codePoint = switch (random.nextInt(4))
               {
                  case 0 -> 1 + random.nextInt(0x7F);
                  case 1 -> 0x80 + random.nextInt(0x800 - 0x80);
                  case 2 -> 0x800 + random.nextInt(0x10000 - 0x800);
                  default -> 0x10000 + random.nextInt(0x110000 - 0x10000);
               };
            }
            while (Character.isSurrogate((char) codePoint) && codePoint < 0x10000);
            value.appendCodePoint(codePoint);
         }

         CDRBuffer buffer = new CDRBuffer();
         buffer.writePayloadHeader();
         buffer.writeByte((byte) 7); // misalign
         buffer.writeString(value);
         int size = buffer.getBufferUnsafe().position();
         assertEquals(4 + 1 + 3 + 4 + CDRBuffer.utf8Length(value) + 1, size);

         buffer.beginRead(size);
         buffer.readPayloadHeader();
         assertEquals(7, buffer.readByte());
         StringBuilder result = new StringBuilder();
         buffer.readString(result);
         assertEquals(value.toString(), result.toString());
         assertFalse(buffer.getBufferUnsafe().hasRemaining());
      }
   }

   @Test
   public void testEmbeddedNulIsRejected()
   {
      CDRBuffer buffer = new CDRBuffer();
      buffer.writePayloadHeader();
      int position = buffer.getBufferUnsafe().position();

      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> buffer.writeString(new StringBuilder("a\0b")));
      assertTrue(exception.getMessage().contains("null characters"));
      assertEquals(position, buffer.getBufferUnsafe().position(), "Nothing may be written for a rejected string");
      assertThrows(IllegalArgumentException.class, () -> buffer.writeString("\0"));
   }

   // ========== UTF-8 reading ==========

   @Test
   public void testReadZeroLengthIsEmptyString()
   {
      CDRBuffer buffer = readerOf("00010000" + "00000000" + "05000000");
      StringBuilder result = new StringBuilder("old");
      buffer.readString(result);
      assertEquals("", result.toString());
      assertEquals(5, buffer.readInt(), "Only the 4-byte length may be consumed");
   }

   @Test
   public void testReadWithoutTrailingNul()
   {
      assertEquals("ab", readString("00010000" + "02000000" + "6162"));
      // Only one trailing NUL is dropped, an embedded NUL is kept (like rclcpp)
      assertEquals("a\0", readString("00010000" + "03000000" + "610000"));
      assertEquals("a\0b", readString("00010000" + "04000000" + "61006200"));
   }

   @Test
   public void testReadIgnoresUnalignedTrailingPadding()
   {
      // rmw pads payloads to a multiple of 4 bytes
      assertEquals("", readString("00010000" + "01000000" + "00000000"));
   }

   @Test
   public void testInvalidUtf8IsReplacedLikeRclpy()
   {
      // rclpy: b'A\xff\xc3(' -> 'A��('
      assertEquals("A��(", readString("00010000" + "05000000" + "41ffc32800"));
      // Truncated bytes as sent by jros2 1.5 for "é€" (rclpy received '�')
      assertEquals("�", readString("00010000" + "03000000" + "e9ac00"));
   }

   @Test
   public void testInvalidUtf8MatchesPython()
   {
      // Expected: Python bytes.decode('utf-8', 'replace') (what rclpy uses), one U+FFFD per maximal invalid subpart
      String[][] cases = {{"c080", "��"}, // overlong
                          {"e08080", "���"}, // overlong
                          {"eda080", "���"}, // encoded surrogate (Java's own decoder gives one U+FFFD here)
                          {"edbfbf", "���"},
                          {"eda0bdedb880", "������"}, // CESU-8 surrogate pair
                          {"f4908080", "����"}, // above U+10FFFF
                          {"f8888080", "����"}, // 5-byte form
                          {"c3", "�"}, // truncated at the end
                          {"e282", "�"},
                          {"f09d84", "�"},
                          {"e9ac", "�"},
                          {"80", "�"}, // lone continuation
                          {"bf41", "�A"},
                          {"c328", "�("}, // offending byte is decoded again
                          {"e228a1", "�(�"},
                          {"f0288cbc", "�(��"},
                          {"fe", "�"},
                          {"ff", "�"},
                          {"c1bf", "��"},
                          {"e0a0", "�"},
                          {"f09f", "�"},
                          {"f09f9800", "�\0"},
                          {"41f09f98", "A�"},
                          {"41ffc328", "A��("}};
      for (String[] testCase : cases)
      {
         assertEquals(testCase[1], decode(HEX.parseHex(testCase[0])), "Decoding " + testCase[0]);
      }
   }

   @Test
   public void testRandomBytesMatchJavaDecoderOutsideEncodedSurrogates()
   {
      // Java's decoder follows the same maximal-subpart rule as Python except for encoded surrogates (ED A0..BF ..)
      Random random = new Random(42);
      int compared = 0;
      for (int i = 0; i < 4000; i++)
      {
         byte[] bytes = new byte[random.nextInt(12)];
         random.nextBytes(bytes);
         if (random.nextBoolean())
         {
            for (int j = 0; j < bytes.length; j++)
               bytes[j] = (byte) (0x80 | bytes[j]); // mostly continuation / lead bytes
         }
         if (bytes.length > 0 && bytes[bytes.length - 1] == 0)
            bytes[bytes.length - 1] = 1;
         if (containsEncodedSurrogate(bytes))
            continue;
         assertEquals(new String(bytes, StandardCharsets.UTF_8), decode(bytes), "Decoding " + HEX.formatHex(bytes));
         compared++;
      }
      assertTrue(compared > 3000);
   }

   private static boolean containsEncodedSurrogate(byte[] bytes)
   {
      for (int i = 0; i + 1 < bytes.length; i++)
      {
         if ((bytes[i] & 0xFF) == 0xED && (bytes[i + 1] & 0xFF) >= 0xA0 && (bytes[i + 1] & 0xFF) <= 0xBF)
            return true;
      }
      return false;
   }

   private static String decode(byte[] bytes)
   {
      ByteBuffer payload = ByteBuffer.allocate(8 + bytes.length + 1).order(ByteOrder.LITTLE_ENDIAN);
      payload.put(new byte[] {0, 1, 0, 0});
      payload.putInt(bytes.length + 1);
      payload.put(bytes);
      payload.put((byte) 0);
      StringBuilder result = new StringBuilder();
      readerOf(payload.array()).readString(result);
      return result.toString();
   }

   @Test
   public void testBigEndianString()
   {
      assertEquals("hé", readString("00000000" + "00000004" + "68c3a900"));
   }

   @Test
   public void testStringAlignmentAtEveryOffset()
   {
      for (int offset = 0; offset < 8; offset++)
      {
         CDRBuffer buffer = new CDRBuffer();
         buffer.writePayloadHeader();
         for (int i = 0; i < offset; i++)
            buffer.writeByte((byte) 0x55);
         buffer.writeString("über");
         int size = buffer.getBufferUnsafe().position();
         int padding = (4 - offset % 4) % 4;
         assertEquals(4 + offset + padding + 4 + 6, size, "offset " + offset);
         for (int i = 0; i < padding; i++)
            assertEquals(0, buffer.getBufferUnsafe().get(4 + offset + i), "padding must be zero");

         buffer.beginRead(size);
         buffer.readPayloadHeader();
         for (int i = 0; i < offset; i++)
            buffer.readByte();
         StringBuilder result = new StringBuilder();
         buffer.readString(result);
         assertEquals("über", result.toString());
      }
   }

   // ========== Malformed lengths ==========

   @Test
   public void testMalformedStringLengths()
   {
      String[] payloads = {"00010000" + "ffffffff" + "61620000", // uint32 max ("negative")
                           "00010000" + "ffffff7f" + "61620000", // Integer.MAX_VALUE
                           "00010000" + "64000000" + "6162630000000000", // 100 > remaining 8
                           "00010000" + "0500", // truncated length prefix
                           "00010000"}; // nothing at all
      for (String payload : payloads)
      {
         StringBuilder destination = new StringBuilder();
         int capacityBefore = destination.capacity();
         assertThrows(CDRDeserializationException.class, () -> readerOf(payload).readString(destination), payload);
         assertEquals(capacityBefore, destination.capacity(), "No allocation may happen before the length is validated");
      }
   }

   @Test
   public void testHugeLengthsDoNotAllocate()
   {
      ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
      long threadId = Thread.currentThread().getId();

      StringBuilder destination = new StringBuilder(16);
      // Warm up the exception path (class loading, message formatting)
      assertThrows(CDRDeserializationException.class, () -> readerOf("00010000" + "f0ffff7f" + "61626364").readString(destination));
      assertThrows(CDRDeserializationException.class, () -> readerOf("00010000" + "f0ffff3f" + "61000000").readWString(destination));

      CDRBuffer string = readerOf("00010000" + "f0ffff7f" + "61626364");
      CDRBuffer wstring = readerOf("00010000" + "f0ffff3f" + "61000000");

      long before = threadMXBean.getThreadAllocatedBytes(threadId);
      assertThrows(CDRDeserializationException.class, () -> string.readString(destination));
      assertThrows(CDRDeserializationException.class, () -> wstring.readWString(destination));
      long allocated = threadMXBean.getThreadAllocatedBytes(threadId) - before;

      assertTrue(allocated < 64 * 1024, "Rejecting a huge wire length must not allocate it, allocated " + allocated + " bytes");
   }

   @Test
   public void testReadPastPayloadEndIsDetectedWithBeginRead()
   {
      // The buffer capacity is larger than the payload: stale bytes after the payload must not be read
      CDRBuffer buffer = new CDRBuffer();
      buffer.ensureRemainingCapacity(64);
      for (int i = 0; i < 64; i++)
         buffer.getBufferUnsafe().put((byte) 0x41);
      buffer.rewind();
      buffer.getBufferUnsafe().put(HEX.parseHex("00010000" + "05000000" + "4142"));
      buffer.beginRead(10);
      buffer.readPayloadHeader();
      assertThrows(CDRDeserializationException.class, () -> buffer.readString(new StringBuilder()));

      buffer.beginRead(6);
      buffer.readPayloadHeader();
      assertThrows(CDRDeserializationException.class, buffer::readInt);
      assertThrows(IllegalArgumentException.class, () -> buffer.beginRead(1 << 20));
   }

   // ========== Wide strings ==========

   @Test
   public void testWStringWireFormatMatchesRmwFastrtps()
   {
      // Captures from an rclpy publisher (rmw_fastrtps, Jazzy): count of UTF-16 code units, one uint32 per unit
      assertEquals("0300000041000000e9000000ac200000", HEX.formatHex(serializeBody(b -> b.writeWString(new StringBuilder("Aé€")))));
      assertEquals("050000003dd8000000de00007800000001d8000037dc0000",
                   HEX.formatHex(serializeBody(b -> b.writeWString(new StringBuilder("😀x𐐷")))));
      assertEquals("00000000", HEX.formatHex(serializeBody(b -> b.writeWString(new StringBuilder()))));
      assertEquals("020000006100000062000000", HEX.formatHex(serializeBody(b -> b.writeWString(new StringBuilder("ab")))));
   }

   @Test
   public void testWStringSurrogatePairsRoundTrip()
   {
      String value = "😀x𐐷 世界";
      CDRBuffer buffer = new CDRBuffer();
      buffer.writePayloadHeader();
      buffer.writeByte((byte) 1);
      buffer.writeWString(new StringBuilder(value));
      int size = buffer.getBufferUnsafe().position();
      assertEquals(4 + 4 + 4 + 4 * value.length(), size);

      buffer.beginRead(size);
      buffer.readPayloadHeader();
      buffer.readByte();
      StringBuilder result = new StringBuilder();
      buffer.readWString(result);
      assertEquals(value, result.toString());
      assertEquals(8, result.length(), "UTF-16 code units");
      assertEquals(6, result.codePointCount(0, result.length()), "code points (two supplementary)");
   }

   @Test
   public void testWStringUnpairedSurrogatesAreReplaced()
   {
      assertEquals("03000000fdff000061000000fdff0000", HEX.formatHex(serializeBody(b -> b.writeWString(new StringBuilder("\uD800a\uDC00")))));
   }

   @Test
   public void testWStringBigEndian()
   {
      CDRBuffer buffer = readerOf("00000000" + "00000002" + "0000d83d" + "0000de00");
      StringBuilder result = new StringBuilder();
      buffer.readWString(result);
      assertEquals("😀", result.toString());
   }

   @Test
   public void testMalformedWStrings()
   {
      // A code unit above 0xFFFF is rejected by rmw_fastrtps (BadParamException) and by jros2
      assertThrows(CDRDeserializationException.class, () -> readerOf("00010000" + "01000000" + "00f60100").readWString(new StringBuilder()));
      // Count larger than the remaining payload
      assertThrows(CDRDeserializationException.class, () -> readerOf("00010000" + "03000000" + "6100000062000000").readWString(new StringBuilder()));
      assertThrows(CDRDeserializationException.class, () -> readerOf("00010000" + "ffffffff").readWString(new StringBuilder()));
   }

   // ========== Other primitives touched by the UTF-8 change ==========

   @Test
   public void testCharIsUnsigned()
   {
      CDRBuffer buffer = readerOf("00010000" + "c8ff41");
      assertEquals(200, buffer.readChar());
      assertEquals(255, buffer.readChar());
      assertEquals('A', buffer.readChar());
   }

   @Test
   public void testInvalidBooleanAndHeader()
   {
      assertThrows(CDRDeserializationException.class, () -> readerOf("00010000" + "02").readBoolean());
      assertThrows(CDRDeserializationException.class, () -> readerOf("000100"));
      assertThrows(CDRDeserializationException.class, () -> readerOf("12340000"));
   }

   @Test
   public void testStructureDummyMember()
   {
      assertEquals("00", HEX.formatHex(serializeBody(CDRBuffer::writeStructureDummyMember)));

      CDRBuffer rosPayload = readerOf("0001000000000000");
      rosPayload.readStructureDummyMember();
      assertEquals(5, rosPayload.getBufferUnsafe().position());

      // jros2 1.5 sent no member at all: accepted
      CDRBuffer legacyPayload = readerOf("00010000");
      assertDoesNotThrow(legacyPayload::readStructureDummyMember);
   }

   @Test
   public void testEveryWriteGrowsTheBuffer()
   {
      CDRBuffer buffer = new CDRBuffer();
      assertEquals(1, buffer.getBufferUnsafe().capacity());
      buffer.writePayloadHeader();
      StringBuilder longString = new StringBuilder();
      for (int i = 0; i < 1000; i++)
         longString.append('€');

      buffer.writeByte((byte) 1);
      buffer.writeBoolean(true);
      buffer.writeChar('c');
      buffer.writeShort((short) 2);
      buffer.writeUInt16(65535);
      buffer.writeInt(3);
      buffer.writeLong(4L);
      buffer.writeFloat(5.0f);
      buffer.writeDouble(6.0);
      buffer.writeWchar(7);
      buffer.writeString(longString);
      buffer.writeWString(longString);
      buffer.writeStructureDummyMember();
      buffer.writeDoubleArray(new double[300]);
      assertTrue(buffer.getBufferUnsafe().isDirect());

      int size = buffer.getBufferUnsafe().position();
      buffer.beginRead(size);
      buffer.readPayloadHeader();
      assertEquals(1, buffer.readByte());
      assertTrue(buffer.readBoolean());
      assertEquals('c', buffer.readChar());
      assertEquals(2, buffer.readShort());
      assertEquals(65535, buffer.readUInt16());
      assertEquals(3, buffer.readInt());
      assertEquals(4L, buffer.readLong());
      assertEquals(5.0f, buffer.readFloat());
      assertEquals(6.0, buffer.readDouble());
      assertEquals(7, buffer.readWchar());
      StringBuilder result = new StringBuilder();
      buffer.readString(result);
      assertEquals(longString.toString(), result.toString());
      buffer.readWString(result);
      assertEquals(longString.toString(), result.toString());
      buffer.readStructureDummyMember();
      double[] doubles = new double[300];
      buffer.readDoubleArray(doubles);
      assertFalse(buffer.getBufferUnsafe().hasRemaining());
   }

   @Test
   public void testPaddingIsZeroedOverStaleBytes()
   {
      CDRBuffer buffer = new CDRBuffer();
      buffer.ensureRemainingCapacity(64);
      for (int i = 0; i < 64; i++)
         buffer.getBufferUnsafe().put((byte) 0xEE);
      buffer.rewind();
      buffer.writePayloadHeader();
      buffer.writeByte((byte) 1);
      buffer.writeDouble(1.0);
      assertEquals("01" + "00".repeat(7) + "000000000000f03f", HEX.formatHex(bodyOf(buffer)));
   }

   private static byte[] bodyOf(CDRBuffer buffer)
   {
      byte[] result = new byte[buffer.getBufferUnsafe().position() - 4];
      for (int i = 0; i < result.length; i++)
         result[i] = buffer.getBufferUnsafe().get(4 + i);
      return result;
   }

   private static String escape(String value)
   {
      StringBuilder builder = new StringBuilder();
      for (char c : value.toCharArray())
         builder.append(String.format("\\u%04x", (int) c));
      return builder.toString();
   }
}
