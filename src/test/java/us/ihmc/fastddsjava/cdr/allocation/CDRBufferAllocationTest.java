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
package us.ihmc.fastddsjava.cdr.allocation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.jros2.allocation.messages.CallingThreadAllocationGate;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static us.ihmc.fastddsjava.natives.fastddsjava.CDR_BE;
import static us.ihmc.fastddsjava.natives.fastddsjava.CDR_LE;
import static us.ihmc.fastddsjava.natives.fastddsjava.PL_CDR_BE;
import static us.ihmc.fastddsjava.natives.fastddsjava.PL_CDR_LE;

/**
 * Every public read/write method of {@link CDRBuffer}, in both byte orders, allocates nothing once the buffer and the
 * destination StringBuilders have reached their working size: primitives, strings with 1- to 4-byte UTF-8 characters,
 * unpaired surrogates and malformed UTF-8 (replacement paths), wide strings with surrogate pairs, every bulk array with
 * and without offsets (also before the CDR origin, the element-by-element fallback), sequence lengths, alignment,
 * the payload header (CDR and PL_CDR, both byte orders), and the buffer management calls of the runtime.
 */
@Tag("allocation")
public class CDRBufferAllocationTest
{
   private static final String UTF8 = "aé€😀中ह✓ß"; // 1-, 2-, 3- and 4-byte UTF-8
   private static final byte[] MALFORMED_UTF8 = {'o', 'k', (byte) 0xC3, (byte) 0x28, (byte) 0xE2, (byte) 0x82, (byte) 0xF0, (byte) 0x9F, (byte) 0x98, 'z', (byte) 0xFF, 0};
   private static final int L = 23;

   private static CallingThreadAllocationGate gate;
   private static volatile long sink;

   /** The sampler is registered only while a test runs (never between tests). */
   @BeforeEach
   public void installGate()
   {
      gate = CallingThreadAllocationGate.create();
   }

   @AfterEach
   public void removeGate()
   {
      gate.close();
      gate = null;
   }

   /** Source data and read destinations, allocated once. */
   private static final class Data
   {
      final StringBuilder utf8 = new StringBuilder(UTF8);
      final StringBuilder unpaired = new StringBuilder("x\uD800y\uDC00z");
      final StringBuilder wide = new StringBuilder("wïde 😀 €\uDC00");
      final StringBuilder empty = new StringBuilder();
      final boolean[] booleans = new boolean[L];
      final byte[] bytes = new byte[L];
      final char[] chars = new char[L];
      final short[] shorts = new short[L];
      final int[] uint16s = new int[L];
      final int[] ints = new int[L];
      final long[] longs = new long[L];
      final float[] floats = new float[L];
      final double[] doubles = new double[L];

      Data(int seed)
      {
         for (int i = 0; i < L; i++)
         {
            booleans[i] = (i + seed) % 3 == 0;
            bytes[i] = (byte) (i * 37 + seed);
            chars[i] = (char) ((i * 11 + seed) & 0xFF);
            shorts[i] = (short) (i * 3001 + seed);
            uint16s[i] = (i * 2999 + seed) & 0xFFFF;
            ints[i] = i * 100003 + seed;
            longs[i] = i * 10000000019L + seed;
            floats[i] = i * 0.37f + seed;
            doubles[i] = i * 1.0e-3 + seed;
         }
      }

      static Data empty()
      {
         return new Data(0);
      }
   }

   private static final class Destination
   {
      final StringBuilder[] strings = new StringBuilder[8];
      final Data data = Data.empty();
      long checksum;

      Destination()
      {
         for (int i = 0; i < strings.length; i++)
            strings[i] = new StringBuilder(64);
      }
   }

   private static void write(CDRBuffer buffer, Data data)
   {
      buffer.writeByte((byte) -3);
      buffer.writeChar('é');
      buffer.writeWchar(0x20AC);
      buffer.writeShort((short) -12345);
      buffer.writeUInt16(65000);
      buffer.writeInt(-123456789);
      buffer.writeLong(-1234567890123456789L);
      buffer.writeFloat(1.5f);
      buffer.writeDouble(-2.25);
      buffer.writeBoolean(true);
      buffer.writeStructureDummyMember();
      buffer.writeString(data.utf8);
      buffer.writeString((CharSequence) UTF8);
      buffer.writeString(data.unpaired);
      buffer.writeString(data.empty);
      buffer.writeWString(data.wide);
      buffer.writeWString(data.empty);
      buffer.writeSequenceLength(MALFORMED_UTF8.length); // a string with malformed UTF-8: length + raw bytes
      buffer.writeByteArray(MALFORMED_UTF8);
      buffer.writeSequenceLength(L);
      buffer.writeBooleanArray(data.booleans);
      buffer.writeBooleanArray(data.booleans, 2, L - 5);
      buffer.writeByteArray(data.bytes);
      buffer.writeByteArray(data.bytes, 1, L - 2);
      buffer.writeCharArray(data.chars);
      buffer.writeCharArray(data.chars, 3, 4);
      buffer.writeShortArray(data.shorts);
      buffer.writeShortArray(data.shorts, 1, L - 1);
      buffer.writeUInt16Array(data.uint16s);
      buffer.writeUInt16Array(data.uint16s, 2, 3);
      buffer.writeIntArray(data.ints);
      buffer.writeIntArray(data.ints, 5, 0);
      buffer.writeIntArray(data.ints, 1, 7);
      buffer.writeLongArray(data.longs);
      buffer.writeLongArray(data.longs, 3, 9);
      buffer.writeFloatArray(data.floats);
      buffer.writeFloatArray(data.floats, 0, 1);
      buffer.writeDoubleArray(data.doubles);
      buffer.writeDoubleArray(data.doubles, 4, L - 4);
      buffer.writeByte((byte) 1);
   }

   private static void read(CDRBuffer buffer, Destination destination)
   {
      Data data = destination.data;
      StringBuilder[] strings = destination.strings;
      long checksum = buffer.readByte();
      checksum += buffer.readChar();
      checksum += buffer.readWchar();
      checksum += buffer.readShort();
      checksum += buffer.readUInt16();
      checksum += buffer.readInt();
      checksum += buffer.readLong();
      checksum += (long) buffer.readFloat();
      checksum += (long) buffer.readDouble();
      checksum += buffer.readBoolean() ? 1 : 0;
      buffer.readStructureDummyMember();
      buffer.readString(strings[0]);
      buffer.readString(strings[1]);
      buffer.readString(strings[2]);
      buffer.readString(strings[3]);
      buffer.readWString(strings[4]);
      buffer.readWString(strings[5]);
      buffer.readString(strings[6]);
      checksum += buffer.readSequenceLength(1, Integer.MAX_VALUE);
      buffer.readBooleanArray(data.booleans);
      buffer.readBooleanArray(data.booleans, 2, L - 5);
      buffer.readByteArray(data.bytes);
      buffer.readByteArray(data.bytes, 1, L - 2);
      buffer.readCharArray(data.chars);
      buffer.readCharArray(data.chars, 3, 4);
      buffer.readShortArray(data.shorts);
      buffer.readShortArray(data.shorts, 1, L - 1);
      buffer.readUInt16Array(data.uint16s);
      buffer.readUInt16Array(data.uint16s, 2, 3);
      buffer.readIntArray(data.ints);
      buffer.readIntArray(data.ints, 5, 0);
      buffer.readIntArray(data.ints, 1, 7);
      buffer.readLongArray(data.longs);
      buffer.readLongArray(data.longs, 3, 9);
      buffer.readFloatArray(data.floats);
      buffer.readFloatArray(data.floats, 0, 1);
      buffer.readDoubleArray(data.doubles);
      buffer.readDoubleArray(data.doubles, 4, L - 4);
      checksum += buffer.readByte();
      buffer.alignBuffer(1);
      destination.checksum = checksum;
   }

   /** Writes a payload with the given encapsulation (as Fast-DDS or rmw would send it) and reads it back. */
   private static void writeAndRead(CDRBuffer buffer, Data source, Destination destination, short encapsulation)
   {
      buffer.rewind();
      buffer.writePayloadHeader();
      ByteBuffer byteBuffer = buffer.getBufferUnsafe();
      byteBuffer.put(0, (byte) (encapsulation >> 8));
      byteBuffer.put(1, (byte) encapsulation);
      byteBuffer.order(CDRBuffer.byteOrder(encapsulation));
      write(buffer, source);
      int size = buffer.getBufferUnsafe().position(); // the buffer object is replaced when it grows
      buffer.beginRead(size);
      buffer.readPayloadHeader();
      read(buffer, destination);
      sink += destination.checksum + size;
      buffer.rewind();
   }

   @Test
   public void testEveryMethodInBothByteOrders()
   {
      CDRBuffer buffer = new CDRBuffer();
      Data source = new Data(7);
      Destination destination = new Destination();
      CharSequence plain = UTF8;

      Runnable body = () ->
      {
         writeAndRead(buffer, source, destination, CDR_LE);
         writeAndRead(buffer, source, destination, CDR_BE);
         writeAndRead(buffer, source, destination, PL_CDR_LE);
         writeAndRead(buffer, source, destination, PL_CDR_BE);

         // Bulk arrays written before the CDR origin (no payload header): element-by-element fallback
         buffer.rewind();
         buffer.writeShortArray(source.shorts, 0, 3);
         buffer.writeIntArray(source.ints, 0, 3);
         buffer.writeLongArray(source.longs, 0, 3);
         buffer.writeFloatArray(source.floats, 0, 3);
         buffer.writeDoubleArray(source.doubles, 0, 3);
         buffer.beginRead(buffer.getBufferUnsafe().position());
         buffer.readShortArray(destination.data.shorts, 0, 3);
         buffer.readIntArray(destination.data.ints, 0, 3);
         buffer.readLongArray(destination.data.longs, 0, 3);
         buffer.readFloatArray(destination.data.floats, 0, 3);
         buffer.readDoubleArray(destination.data.doubles, 0, 3);

         // Buffer management calls of ROS2Publisher / ROS2Subscription, below the capacity
         buffer.rewind();
         sink += buffer.ensureRemainingCapacity(64) ? 1 : 0;
         sink += buffer.getBufferUnsafe().capacity();
         sink += CDRBuffer.utf8Length(plain) + CDRBuffer.utf8Length(source.utf8) + CDRBuffer.alignment(5, 8);
         sink += CDRBuffer.byteOrder(CDR_LE) == ByteOrder.LITTLE_ENDIAN ? 1 : 0;
      };

      body.run();
      gate.assertNoAllocation("CDRBuffer read/write, every method, LE/BE/PL_CDR", () ->
      {
         body.run();
         body.run();
      });

      // What was read is what was written
      assertEquals(UTF8, destination.strings[0].toString());
      assertEquals(UTF8, destination.strings[1].toString());
      assertEquals("x?y?z", destination.strings[2].toString());
      assertEquals("", destination.strings[3].toString());
      assertEquals("wïde 😀 €�", destination.strings[4].toString());
      assertEquals("ok�(��z�", destination.strings[6].toString());
      Data expected = new Data(7);
      assertArrayEquals(expected.booleans, destination.data.booleans);
      assertArrayEquals(expected.bytes, destination.data.bytes);
      assertArrayEquals(expected.chars, destination.data.chars);
      assertArrayEquals(expected.shorts, destination.data.shorts);
      assertArrayEquals(expected.uint16s, destination.data.uint16s);
      assertArrayEquals(expected.ints, destination.data.ints);
      assertArrayEquals(expected.longs, destination.data.longs);
      assertArrayEquals(expected.floats, destination.data.floats);
      assertArrayEquals(expected.doubles, destination.data.doubles);
   }

   @Test
   public void testSmallerStringsAfterTheHighWaterMark()
   {
      // A destination StringBuilder that once held a long non-Latin-1 string takes any shorter string without allocating
      CDRBuffer buffer = new CDRBuffer();
      StringBuilder[] sources = {new StringBuilder("€".repeat(40)), new StringBuilder("ascii"), new StringBuilder("😀"), new StringBuilder(), new StringBuilder("ß".repeat(39))};
      StringBuilder destination = new StringBuilder();
      StringBuilder wideDestination = new StringBuilder();
      Runnable body = () ->
      {
         for (StringBuilder source : sources)
         {
            buffer.rewind();
            buffer.writePayloadHeader();
            buffer.writeString(source);
            buffer.writeWString(source);
            buffer.beginRead(buffer.getBufferUnsafe().position());
            buffer.readPayloadHeader();
            buffer.readString(destination);
            buffer.readWString(wideDestination);
            sink += destination.length() + wideDestination.length();
         }
      };
      body.run();
      gate.assertNoAllocation("CDRBuffer strings below the high-water mark", () ->
      {
         body.run();
         body.run();
      });
      assertEquals(sources[sources.length - 1].toString(), destination.toString());
      assertEquals(sources[sources.length - 1].toString(), wideDestination.toString());
   }

   /** Writes {@code source} as a string and a wide string and reads them back into the destinations. */
   private static void stringRoundTrip(CDRBuffer buffer, CharSequence source, StringBuilder destination, StringBuilder wideDestination)
   {
      buffer.rewind();
      buffer.writePayloadHeader();
      buffer.writeString(source);
      buffer.writeWString(CDRBuffer.newStringBuilder(0).append(source));
      buffer.beginRead(buffer.getBufferUnsafe().position());
      buffer.readPayloadHeader();
      buffer.readString(destination);
      buffer.readWString(wideDestination);
      sink += destination.length() + wideDestination.length();
   }

   @Test
   public void testNonLatin1ContentAfterAsciiWarmUp()
   {
      // A destination that held ASCII only for a long time receives a shorter string with non-Latin-1 characters
      CDRBuffer buffer = new CDRBuffer();
      String ascii = "ascii_only_frame_id_0123456789";
      String unicode = "fr\u00e2me_\u20ac_\uD83D\uDE00";
      StringBuilder destination = CDRBuffer.newStringBuilder(8);
      StringBuilder wideDestination = CDRBuffer.newStringBuilder(8);
      for (int i = 0; i < 3; i++)
         stringRoundTrip(buffer, ascii, destination, wideDestination);
      StringBuilder unicodeSource = new StringBuilder(unicode); // created outside the measured region
      buffer.rewind();
      buffer.writePayloadHeader();
      buffer.writeString(unicodeSource);
      buffer.writeWString(unicodeSource);
      int size = buffer.getBufferUnsafe().position();

      gate.assertNoAllocation("CDRBuffer.readString/readWString of non-Latin-1 content into newStringBuilder destinations after ASCII content", () ->
      {
         buffer.beginRead(size);
         buffer.readPayloadHeader();
         buffer.readString(destination);
         buffer.readWString(wideDestination);
      });
      assertEquals(unicode, destination.toString());
      assertEquals(unicode, wideDestination.toString());

      // Control: a plain JDK StringBuilder in the same situation reallocates its storage (Latin-1 to UTF-16), which is why
      // the library creates its builders with CDRBuffer.newStringBuilder. This also shows the gate sees that allocation.
      StringBuilder plain = new StringBuilder(64);
      StringBuilder plainWide = new StringBuilder(64);
      for (int i = 0; i < 3; i++)
         stringRoundTrip(buffer, ascii, plain, plainWide);
      buffer.rewind();
      buffer.writePayloadHeader();
      buffer.writeString(unicodeSource);
      buffer.writeWString(unicodeSource);
      long allocations = gate.countAllocations(() ->
      {
         buffer.beginRead(size);
         buffer.readPayloadHeader();
         buffer.readString(plain);
      });
      assertTrue(allocations > 0, "a JDK StringBuilder that only held Latin-1 reallocates for the first character above U+00FF");
      assertEquals(unicode, plain.toString());
   }

   @Test
   public void testNewStringBuilder()
   {
      StringBuilder empty = CDRBuffer.newStringBuilder(0);
      StringBuilder sized = CDRBuffer.newStringBuilder(40);
      StringBuilder content = CDRBuffer.newStringBuilder("d\u00e9faut");
      assertEquals(0, empty.length());
      assertEquals(0, sized.length());
      assertEquals(40, sized.capacity());
      assertEquals("d\u00e9faut", content.toString());
      assertEquals(6 + 16, content.capacity());
      String unicode = "\u4e2d\uD83D\uDE00"; // loaded (and created by the JVM) before the measured region
      gate.assertNoAllocation("non-Latin-1 content up to the capacity of a newStringBuilder", () ->
      {
         for (int i = 0; i < 40; i++)
            sized.append(i % 2 == 0 ? '\u20ac' : 'a');
         content.setLength(0);
         content.append(unicode);
         empty.append('\u0100');
      });
      assertEquals(40, sized.length());
   }
}
