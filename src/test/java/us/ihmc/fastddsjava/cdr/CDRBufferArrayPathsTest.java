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

import static org.junit.jupiter.api.Assertions.*;

/**
 * The bulk array methods of {@link CDRBuffer} have two paths: after the 4-byte encapsulation header they copy through a typed
 * view, before it (a buffer used without {@code writePayloadHeader}) they fall back to element loops. Both must produce
 * exactly the bytes of the element-wise methods and read back the same values, for sub-ranges too; ranges outside the array
 * and impossible capacities are rejected.
 */
public class CDRBufferArrayPathsTest
{
   private interface Writer
   {
      void write(CDRBuffer buffer);
   }

   private interface Reader
   {
      void read(CDRBuffer buffer);
   }

   private static byte[] bytes(CDRBuffer buffer)
   {
      ByteBuffer byteBuffer = buffer.getBufferUnsafe();
      byte[] bytes = new byte[byteBuffer.position()];
      for (int i = 0; i < bytes.length; ++i)
      {
         bytes[i] = byteBuffer.get(i);
      }
      return bytes;
   }

   /**
    * Writes with {@code bulk} and {@code elementWise}, with and without the payload header and after 0 to 3 leading bytes (so
    * that both paths and every alignment are used), and compares the bytes.
    */
   private static void assertSameBytes(String type, Writer bulk, Writer elementWise)
   {
      for (boolean header : new boolean[] {false, true})
      {
         for (int lead = 0; lead < 4; ++lead)
         {
            CDRBuffer bulkBuffer = new CDRBuffer();
            CDRBuffer elementBuffer = new CDRBuffer();
            if (header)
            {
               bulkBuffer.writePayloadHeader();
               elementBuffer.writePayloadHeader();
            }
            for (int i = 0; i < lead; ++i)
            {
               bulkBuffer.writeByte((byte) 9);
               elementBuffer.writeByte((byte) 9);
            }
            bulk.write(bulkBuffer);
            elementWise.write(elementBuffer);
            assertArrayEquals(bytes(elementBuffer), bytes(bulkBuffer), type + (header ? " after the header" : " without a header") + ", " + lead + " leading bytes");
         }
      }
   }

   /** A buffer holding the bytes written by {@code writer}, positioned for reading (the header read if written). */
   private static CDRBuffer readable(boolean header, Writer writer)
   {
      CDRBuffer buffer = new CDRBuffer();
      if (header)
      {
         buffer.writePayloadHeader();
      }
      writer.write(buffer);
      int size = buffer.getBufferUnsafe().position();
      buffer.beginRead(size);
      if (header)
      {
         buffer.readPayloadHeader();
      }
      return buffer;
   }

   /**
    * Writes {@code leadBytes} bytes and then an array with {@code writer}, reads the leading bytes back and then the array
    * with {@code reader}, which must consume the rest of the payload.
    */
   private static void readBack(boolean header, int leadBytes, Writer writer, Reader reader, String context)
   {
      CDRBuffer buffer = readable(header, b ->
      {
         for (int i = 0; i < leadBytes; ++i)
            b.writeByte((byte) 1);
         writer.write(b);
      });
      for (int i = 0; i < leadBytes; ++i)
         assertEquals(1, buffer.readByte(), context);
      reader.read(buffer);
      assertEquals(buffer.getBufferUnsafe().limit(), buffer.getBufferUnsafe().position(), context + ": everything was read");
   }

   @Test
   public void testWritesMatchTheElementWiseEncoding()
   {
      short[] shorts = {1, -2, 300, Short.MIN_VALUE, Short.MAX_VALUE};
      int[] ints = {1, -2, 70000, Integer.MIN_VALUE};
      long[] longs = {1L, -2L, Long.MAX_VALUE};
      float[] floats = {1.5f, -2.25f, Float.MAX_VALUE};
      double[] doubles = {1.5, -2.25, Double.MIN_VALUE};
      char[] chars = {'a', 'b', 0xE9};
      boolean[] booleans = {true, false, true};
      byte[] bytes = {1, -1, 127};

      assertSameBytes("short", b -> b.writeShortArray(shorts, 1, 3), b ->
      {
         for (int i = 1; i < 4; ++i)
            b.writeShort(shorts[i]);
      });
      assertSameBytes("int", b -> b.writeIntArray(ints), b ->
      {
         for (int value : ints)
            b.writeInt(value);
      });
      assertSameBytes("long", b -> b.writeLongArray(longs, 1, 2), b ->
      {
         b.writeLong(longs[1]);
         b.writeLong(longs[2]);
      });
      assertSameBytes("float", b -> b.writeFloatArray(floats), b ->
      {
         for (float value : floats)
            b.writeFloat(value);
      });
      assertSameBytes("double", b -> b.writeDoubleArray(doubles), b ->
      {
         for (double value : doubles)
            b.writeDouble(value);
      });
      assertSameBytes("char", b -> b.writeCharArray(chars), b ->
      {
         for (char value : chars)
            b.writeChar(value);
      });
      assertSameBytes("boolean", b -> b.writeBooleanArray(booleans), b ->
      {
         for (boolean value : booleans)
            b.writeBoolean(value);
      });
      assertSameBytes("byte", b -> b.writeByteArray(bytes, 1, 2), b ->
      {
         b.writeByte(bytes[1]);
         b.writeByte(bytes[2]);
      });
   }

   @Test
   public void testReadsReturnTheWrittenValues()
   {
      for (boolean header : new boolean[] {false, true})
      {
         for (int lead = 0; lead < 2; ++lead)
         {
            int leadBytes = lead;
            String context = (header ? "after the header" : "without a header") + ", " + lead + " leading bytes";
            short[] shorts = {1, -2, 300};
            int[] ints = {5, -6, 70000};
            long[] longs = {7L, Long.MIN_VALUE};
            float[] floats = {1.5f, -0.0f};
            double[] doubles = {2.5, Double.NaN};
            char[] chars = {'x', 'y'};
            CDRBuffer buffer = readable(header, b ->
            {
               for (int i = 0; i < leadBytes; ++i)
                  b.writeByte((byte) 1);
               b.writeIntArray(ints);
               b.writeShortArray(shorts);
               b.writeLongArray(longs);
               b.writeFloatArray(floats);
               b.writeDoubleArray(doubles);
               b.writeCharArray(chars);
               b.writeShortArray(new short[0]);
            });

            for (int i = 0; i < lead; ++i)
               assertEquals(1, buffer.readByte());
            int[] intsRead = new int[3];
            buffer.readIntArray(intsRead);
            assertArrayEquals(ints, intsRead, context);
            short[] shortsRead = new short[5];
            buffer.readShortArray(shortsRead, 1, 3);
            assertArrayEquals(new short[] {0, 1, -2, 300, 0}, shortsRead, context);
            long[] longsRead = new long[2];
            buffer.readLongArray(longsRead);
            assertArrayEquals(longs, longsRead, context);
            float[] floatsRead = new float[2];
            buffer.readFloatArray(floatsRead);
            assertArrayEquals(floats, floatsRead, context);
            double[] doublesRead = new double[2];
            buffer.readDoubleArray(doublesRead);
            assertArrayEquals(doubles, doublesRead, context);
            char[] charsRead = new char[2];
            buffer.readCharArray(charsRead);
            assertArrayEquals(chars, charsRead, context);
            buffer.readShortArray(new short[0]);
            assertEquals(buffer.getBufferUnsafe().limit(), buffer.getBufferUnsafe().position(), context + ": everything was read");
         }
      }
   }

   /**
    * Every multi-byte array type as the first value of the payload, after 0 to 3 leading bytes, with and without the header,
    * read into a sub-range of the destination. Without the header the 2- and 4-byte arrays can start before the origin, where
    * the reads take the element loops (in the test above only the int array does); everywhere else they take the typed
    * views. 8-byte values are always aligned to the origin or beyond.
    */
   @Test
   public void testEveryArrayTypeReadsBackOnBothPaths()
   {
      short[] shorts = {1, -2, 300};
      int[] ints = {5, -6, 70000};
      float[] floats = {1.5f, -0.0f, Float.MIN_VALUE};
      long[] longs = {7L, Long.MIN_VALUE, -8L};
      double[] doubles = {2.5, Double.NaN, -0.0};
      for (boolean header : new boolean[] {false, true})
      {
         for (int lead = 0; lead < 4; ++lead)
         {
            String context = (header ? "after the header" : "without a header") + ", " + lead + " leading bytes";

            short[] shortsRead = new short[5];
            readBack(header, lead, b -> b.writeShortArray(shorts), b -> b.readShortArray(shortsRead, 1, 3), "short, " + context);
            assertArrayEquals(new short[] {0, 1, -2, 300, 0}, shortsRead, "short, " + context);

            int[] intsRead = new int[5];
            readBack(header, lead, b -> b.writeIntArray(ints), b -> b.readIntArray(intsRead, 1, 3), "int, " + context);
            assertArrayEquals(new int[] {0, 5, -6, 70000, 0}, intsRead, "int, " + context);

            float[] floatsRead = new float[5];
            readBack(header, lead, b -> b.writeFloatArray(floats), b -> b.readFloatArray(floatsRead, 1, 3), "float, " + context);
            assertArrayEquals(new float[] {0, 1.5f, -0.0f, Float.MIN_VALUE, 0}, floatsRead, "float, " + context);

            long[] longsRead = new long[5];
            readBack(header, lead, b -> b.writeLongArray(longs), b -> b.readLongArray(longsRead, 1, 3), "long, " + context);
            assertArrayEquals(new long[] {0, 7L, Long.MIN_VALUE, -8L, 0}, longsRead, "long, " + context);

            double[] doublesRead = new double[5];
            readBack(header, lead, b -> b.writeDoubleArray(doubles), b -> b.readDoubleArray(doublesRead, 1, 3), "double, " + context);
            assertArrayEquals(new double[] {0, 2.5, Double.NaN, -0.0, 0}, doublesRead, "double, " + context);
         }
      }
   }

   @Test
   public void testRangesAreChecked()
   {
      CDRBuffer buffer = new CDRBuffer();
      buffer.writePayloadHeader();
      int[] values = new int[4];
      assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeIntArray(values, -1, 2));
      assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeIntArray(values, 3, 2));
      assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeDoubleArray(new double[2], 0, -1));
      assertThrows(IndexOutOfBoundsException.class, () -> buffer.readLongArray(new long[2], 1, 2));
      assertThrows(IndexOutOfBoundsException.class, () -> buffer.readBooleanArray(new boolean[1], 0, 2));
      assertEquals(4, buffer.getBufferUnsafe().position(), "rejected ranges write nothing");

      assertThrows(IllegalArgumentException.class, () -> buffer.ensureRemainingCapacity(-1));
      assertThrows(IllegalArgumentException.class, () -> buffer.ensureRemainingCapacity(Integer.MAX_VALUE), "beyond the maximum CDR buffer size");
      assertThrows(IllegalArgumentException.class, () -> buffer.beginRead(-1));
      assertThrows(IllegalArgumentException.class, () -> buffer.beginRead(buffer.getBufferUnsafe().capacity() + 1));
   }

   @Test
   public void testReadingPastThePayloadIsRejected()
   {
      CDRBuffer buffer = readable(true, b -> b.writeIntArray(new int[] {1, 2}));
      int[] tooMany = new int[3];
      assertThrows(CDRDeserializationException.class, () -> buffer.readIntArray(tooMany));
      CDRBuffer headerless = readable(false, b -> b.writeShortArray(new short[] {1}));
      assertThrows(CDRDeserializationException.class, () -> headerless.readShortArray(new short[2]));
   }
}
