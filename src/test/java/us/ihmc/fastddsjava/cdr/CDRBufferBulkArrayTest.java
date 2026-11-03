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

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bulk primitive array (de)serialization of {@link CDRBuffer}: the bulk methods must produce exactly the bytes of the
 * equivalent element-by-element writes, at every alignment offset, and read little and big endian payloads.
 */
public class CDRBufferBulkArrayTest
{
   private static final int[] LENGTHS = {0, 1, 2, 3, 7, 8, 33, 1000};

   /**
    * One primitive array type: how to create random content, write/read it in bulk and element by element.
    */
   private record ArrayType<A>(String name,
                               java.util.function.BiFunction<Random, Integer, A> random,
                               BiConsumer<CDRBuffer, A> bulkWrite,
                               BiConsumer<CDRBuffer, A> elementWrite,
                               BiConsumer<CDRBuffer, A> bulkRead,
                               java.util.function.Function<A, String> format)
   {
   }

   private static final ArrayType<?>[] TYPES = {new ArrayType<byte[]>("byte", (r, n) ->
   {
      byte[] a = new byte[n];
      r.nextBytes(a);
      return a;
   }, CDRBuffer::writeByteArray, (b, a) ->
   {
      for (byte v : a)
         b.writeByte(v);
   }, CDRBuffer::readByteArray, Arrays::toString), new ArrayType<boolean[]>("bool", (r, n) ->
   {
      boolean[] a = new boolean[n];
      for (int i = 0; i < n; i++)
         a[i] = r.nextBoolean();
      return a;
   }, CDRBuffer::writeBooleanArray, (b, a) ->
   {
      for (boolean v : a)
         b.writeBoolean(v);
   }, CDRBuffer::readBooleanArray, Arrays::toString), new ArrayType<char[]>("char", (r, n) ->
   {
      char[] a = new char[n];
      for (int i = 0; i < n; i++)
         a[i] = (char) r.nextInt(256);
      return a;
   }, CDRBuffer::writeCharArray, (b, a) ->
   {
      for (char v : a)
         b.writeChar(v);
   }, CDRBuffer::readCharArray, Arrays::toString), new ArrayType<short[]>("int16", (r, n) ->
   {
      short[] a = new short[n];
      for (int i = 0; i < n; i++)
         a[i] = (short) r.nextInt();
      return a;
   }, CDRBuffer::writeShortArray, (b, a) ->
   {
      for (short v : a)
         b.writeShort(v);
   }, CDRBuffer::readShortArray, Arrays::toString), new ArrayType<int[]>("uint16", (r, n) ->
   {
      int[] a = new int[n];
      for (int i = 0; i < n; i++)
         a[i] = r.nextInt(65536);
      return a;
   }, CDRBuffer::writeUInt16Array, (b, a) ->
   {
      for (int v : a)
         b.writeUInt16(v);
   }, CDRBuffer::readUInt16Array, Arrays::toString), new ArrayType<int[]>("int32", (r, n) ->
   {
      int[] a = new int[n];
      for (int i = 0; i < n; i++)
         a[i] = r.nextInt();
      return a;
   }, CDRBuffer::writeIntArray, (b, a) ->
   {
      for (int v : a)
         b.writeInt(v);
   }, CDRBuffer::readIntArray, Arrays::toString), new ArrayType<long[]>("int64", (r, n) ->
   {
      long[] a = new long[n];
      for (int i = 0; i < n; i++)
         a[i] = r.nextLong();
      return a;
   }, CDRBuffer::writeLongArray, (b, a) ->
   {
      for (long v : a)
         b.writeLong(v);
   }, CDRBuffer::readLongArray, Arrays::toString), new ArrayType<float[]>("float32", (r, n) ->
   {
      float[] a = new float[n];
      for (int i = 0; i < n; i++)
         a[i] = r.nextFloat() * 1e6f - 5e5f;
      return a;
   }, CDRBuffer::writeFloatArray, (b, a) ->
   {
      for (float v : a)
         b.writeFloat(v);
   }, CDRBuffer::readFloatArray, Arrays::toString), new ArrayType<double[]>("float64", (r, n) ->
   {
      double[] a = new double[n];
      for (int i = 0; i < n; i++)
         a[i] = r.nextGaussian() * 1e9;
      return a;
   }, CDRBuffer::writeDoubleArray, (b, a) ->
   {
      for (double v : a)
         b.writeDouble(v);
   }, CDRBuffer::readDoubleArray, Arrays::toString)};

   @Test
   public void testBulkWriteMatchesElementWritesAtEveryOffset()
   {
      Random random = new Random(7);
      for (ArrayType<?> type : TYPES)
      {
         for (int length : LENGTHS)
         {
            for (int offset = 0; offset < 8; offset++)
            {
               checkWrite(type, random, length, offset);
            }
         }
      }
   }

   private static <A> void checkWrite(ArrayType<A> type, Random random, int length, int offset)
   {
      A values = type.random().apply(random, length);
      String context = type.name() + " length " + length + " offset " + offset;

      byte[] bulk = serialize(offset, buffer -> type.bulkWrite().accept(buffer, values), true);
      byte[] elementwise = serialize(offset, buffer -> type.elementWrite().accept(buffer, values), true);
      assertArrayEquals(elementwise, bulk, context);

      // Little endian read back
      A readBack = type.random().apply(random, length);
      CDRBuffer reader = reader(bulk, offset);
      type.bulkRead().accept(reader, readBack);
      assertEquals(type.format().apply(values), type.format().apply(readBack), context);
      writeTrailer(reader, bulk.length);

      // Big endian payload written element by element, read in bulk
      byte[] bigEndian = serialize(offset, buffer -> type.elementWrite().accept(buffer, values), false);
      A readBackBigEndian = type.random().apply(random, length);
      CDRBuffer bigEndianReader = reader(bigEndian, offset);
      assertEquals(ByteOrder.BIG_ENDIAN, bigEndianReader.getBufferUnsafe().order());
      type.bulkRead().accept(bigEndianReader, readBackBigEndian);
      assertEquals(type.format().apply(values), type.format().apply(readBackBigEndian), context + " (big endian)");
      writeTrailer(bigEndianReader, bigEndian.length);
   }

   /** Checks that a trailing int written after the array is read back correctly, i.e. the position is right. */
   private static void writeTrailer(CDRBuffer reader, int payloadLength)
   {
      assertEquals(0x0A0B0C0D, reader.readInt());
      assertEquals(payloadLength, reader.getBufferUnsafe().position());
   }

   /** Payload: header, {@code offset} bytes, the array, a trailing int 0x0A0B0C0D. */
   private static byte[] serialize(int offset, Consumer<CDRBuffer> writer, boolean littleEndian)
   {
      CDRBuffer buffer = new CDRBuffer();
      if (littleEndian)
      {
         buffer.writePayloadHeader();
      }
      else
      {
         buffer.ensureRemainingCapacity(4);
         buffer.getBufferUnsafe().put(new byte[] {0, 0, 0, 0}); // CDR_BE
         buffer.getBufferUnsafe().order(ByteOrder.BIG_ENDIAN);
      }
      for (int i = 0; i < offset; i++)
         buffer.writeByte((byte) (0x30 + i));
      writer.accept(buffer);
      buffer.writeInt(0x0A0B0C0D);

      byte[] bytes = new byte[buffer.getBufferUnsafe().position()];
      for (int i = 0; i < bytes.length; i++)
         bytes[i] = buffer.getBufferUnsafe().get(i);
      return bytes;
   }

   private static CDRBuffer reader(byte[] payload, int offset)
   {
      CDRBuffer buffer = new CDRBuffer();
      buffer.ensureRemainingCapacity(payload.length + 64);
      buffer.getBufferUnsafe().put(payload);
      buffer.beginRead(payload.length);
      buffer.readPayloadHeader();
      for (int i = 0; i < offset; i++)
         assertEquals(0x30 + i, buffer.readByte());
      return buffer;
   }

   @Test
   public void testSameBufferAlternatingByteOrdersAndGrowth()
   {
      double[] values = {1.5, -2.25, 3.0, Double.NaN, Double.MAX_VALUE};
      byte[] littleEndian = serialize(3, b -> b.writeDoubleArray(values), true);
      byte[] bigEndian = serialize(3, b -> b.writeDoubleArray(values), false);

      CDRBuffer buffer = new CDRBuffer();
      for (int round = 0; round < 4; round++)
      {
         byte[] payload = round % 2 == 0 ? littleEndian : bigEndian;
         if (round == 2)
         {
            // Force reallocation: cached views must be dropped
            buffer.rewind();
            buffer.ensureRemainingCapacity(1 << 16);
         }
         buffer.rewind();
         buffer.ensureRemainingCapacity(payload.length);
         buffer.getBufferUnsafe().put(payload);
         buffer.beginRead(payload.length);
         buffer.readPayloadHeader();
         for (int i = 0; i < 3; i++)
            buffer.readByte();
         double[] result = new double[values.length];
         buffer.readDoubleArray(result);
         assertArrayEquals(values, result, "round " + round);
      }
   }

   @Test
   public void testWriteGrowsWithoutPresizing()
   {
      long[] values = new long[10000];
      for (int i = 0; i < values.length; i++)
         values[i] = i * 31L;

      CDRBuffer buffer = new CDRBuffer();
      buffer.writePayloadHeader();
      buffer.writeByte((byte) 1);
      buffer.writeLongArray(values);
      buffer.writeLongArray(values, 10, 20);
      int size = buffer.getBufferUnsafe().position();
      assertEquals(4 + 8 + 8 * 10000 + 8 * 20, size);

      buffer.beginRead(size);
      buffer.readPayloadHeader();
      buffer.readByte();
      long[] result = new long[10000];
      buffer.readLongArray(result);
      assertArrayEquals(values, result);
      long[] slice = new long[25];
      buffer.readLongArray(slice, 5, 20);
      assertArrayEquals(Arrays.copyOfRange(values, 10, 30), Arrays.copyOfRange(slice, 5, 25));
   }

   @Test
   public void testZeroLengthArraysDoNotAlign()
   {
      CDRBuffer buffer = new CDRBuffer();
      buffer.writePayloadHeader();
      buffer.writeByte((byte) 1);
      buffer.writeDoubleArray(new double[0]);
      buffer.writeIntArray(new int[0]);
      buffer.writeShortArray(new short[0]);
      buffer.writeUInt16Array(new int[0]);
      buffer.writeLongArray(new long[0]);
      buffer.writeFloatArray(new float[0]);
      assertEquals(5, buffer.getBufferUnsafe().position());
   }

   @Test
   public void testRangeChecks()
   {
      CDRBuffer buffer = new CDRBuffer();
      buffer.writePayloadHeader();
      assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeDoubleArray(new double[3], 2, 2));
      assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeIntArray(new int[3], -1, 1));
      assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeByteArray(new byte[3], 0, -1));
      assertEquals(4, buffer.getBufferUnsafe().position(), "nothing may be written");
   }

   @Test
   public void testTruncatedArraysThrow()
   {
      byte[] payload = serialize(0, b -> b.writeDoubleArray(new double[] {1, 2}), true);
      CDRBuffer buffer = new CDRBuffer();
      buffer.ensureRemainingCapacity(payload.length);
      buffer.getBufferUnsafe().put(payload);
      buffer.beginRead(payload.length - 6); // cut into the second double
      buffer.readPayloadHeader();
      assertThrows(CDRDeserializationException.class, () -> buffer.readDoubleArray(new double[2]));

      CDRBuffer booleans = new CDRBuffer();
      booleans.ensureRemainingCapacity(8);
      booleans.getBufferUnsafe().put(new byte[] {0, 1, 0, 0, 1, 2, 0});
      booleans.beginRead(7);
      booleans.readPayloadHeader();
      assertThrows(CDRDeserializationException.class, () -> booleans.readBooleanArray(new boolean[3]));
   }

   @Test
   public void testWireBytesAreLittleEndian()
   {
      byte[] payload = serialize(1, b -> b.writeIntArray(new int[] {0x01020304, 0x0A0B0C0D}), true);
      ByteBuffer expected = ByteBuffer.allocate(payload.length).order(ByteOrder.LITTLE_ENDIAN);
      expected.put(new byte[] {0, 1, 0, 0, 0x30, 0, 0, 0});
      expected.putInt(0x01020304).putInt(0x0A0B0C0D).putInt(0x0A0B0C0D);
      assertArrayEquals(expected.array(), payload);
   }
}
