/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static us.ihmc.fastddsjava.natives.fastddsjava.*;

/**
 * A buffer wrapper for reading and writing data using Common Data Representation (CDR) encoding,
 * as specified by the OMG DDS-RTPS and CORBA specifications.
 * <p>
 * This class provides methods to serialize and deserialize primitive types and strings according to
 * CDR encoding rules, including proper alignment and endianness handling. It automatically manages
 * buffer capacity and handles the RTPS payload header format.
 * </p>
 *
 * @see <a href="https://www.omg.org/spec/DDSI-RTPS/2.3/PDF">DDS-RTPS 2.3 Specification</a>
 * @see <a href="https://www.omg.org/spec/CORBA/3.3/Interoperability/PDF">CORBA 3.3 Interoperability Specification</a>
 */
public final class CDRBuffer
{
   // RepresentationIdentifier, RepresentationOptions
   public static final byte[] PAYLOAD_HEADER = {0, 1, 0, 0};

   private ByteBuffer buffer;

   public CDRBuffer()
   {
      // Must be direct: JNI topicData*Buffer uses GetDirectBufferAddress.
      // Do not call ByteBuffer.array(); sequential get/put on this buffer is the supported path.
      buffer = ByteBuffer.allocateDirect(1);
   }

   /**
    * Returns the underlying CDR {@link ByteBuffer}.
    * <p>
    * The buffer is always direct ({@link ByteBuffer#isDirect()} is {@code true}).
    * JNI publish/subscribe paths use {@code GetDirectBufferAddress}, which has no heap
    * fallback, so callers must not replace this with a heap buffer or call
    * {@link ByteBuffer#array()}. Use sequential {@code get}/{@code put} on this buffer.
    *
    * @return the live direct buffer; position, limit, and capacity are owned by this {@link CDRBuffer}
    */
   public ByteBuffer getBufferUnsafe()
   {
      return buffer;
   }

   public boolean ensureRemainingCapacity(int capacity)
   {
      int requiredCapacity = buffer.position() + capacity;

      if (buffer.capacity() < requiredCapacity)
      {
         int oldPosition = buffer.position();
         ByteOrder oldOrder = buffer.order();
         // Grow by powers of two to reduce repeated reallocations for variable-size payloads.
         int newCapacity = 1;
         while (newCapacity < requiredCapacity)
         {
            newCapacity <<= 1;
         }
         ByteBuffer newBuffer = ByteBuffer.allocateDirect(newCapacity);
         newBuffer.order(oldOrder);

         buffer.flip();
         newBuffer.put(buffer);
         newBuffer.position(oldPosition);

         buffer = newBuffer;

         return true;
      }

      return false;
   }

   public void rewind()
   {
      buffer.rewind();
   }

   public void writePayloadHeader()
   {
      buffer.put(PAYLOAD_HEADER);

      buffer.order(ByteOrder.LITTLE_ENDIAN);
   }

   public void readPayloadHeader()
   {
      buffer.order(ByteOrder.BIG_ENDIAN); // BE for reading header
      // RepresentationIdentifier (encapsulation)
      short encapsulation = buffer.getShort();
      buffer.order(byteOrder(encapsulation));
      // RepresentationOptions
      buffer.getShort();
   }

   public void writeByte(byte value)
   {
      buffer.put(value);
   }

   public byte readByte()
   {
      return buffer.get();
   }

   public void writeChar(char value)
   {
      writeByte((byte) value);
   }

   public char readChar()
   {
      return (char) readByte();
   }

   public void writeWchar(int value)
   {
      writeInt(value);
   }

   public int readWchar()
   {
      return readInt();
   }

   public void writeShort(short value)
   {
      alignBuffer(2);
      buffer.putShort(value);
   }

   public short readShort()
   {
      alignBuffer(2);
      return buffer.getShort();
   }

   /** Reads a CDR uint16 value as an unsigned 16-bit integer in the range [0, 65535]. */
   public int readUInt16()
   {
      return Short.toUnsignedInt(readShort());
   }

   /** Writes a CDR uint16 value. Values outside [0, 65535] are truncated to 16 bits. */
   public void writeUInt16(int value)
   {
      writeShort((short) value);
   }

   public void writeInt(int value)
   {
      alignBuffer(4);
      buffer.putInt(value);
   }

   public int readInt()
   {
      alignBuffer(4);
      return buffer.getInt();
   }

   public void writeLong(long value)
   {
      alignBuffer(8);
      buffer.putLong(value);
   }

   public long readLong()
   {
      alignBuffer(8);
      return buffer.getLong();
   }

   public void writeFloat(float value)
   {
      alignBuffer(4);
      buffer.putFloat(value);
   }

   public float readFloat()
   {
      alignBuffer(4);
      return buffer.getFloat();
   }

   public void writeDouble(double value)
   {
      alignBuffer(8);
      buffer.putDouble(value);
   }

   public double readDouble()
   {
      alignBuffer(8);
      return buffer.getDouble();
   }

   public void writeBoolean(boolean value)
   {
      buffer.put((byte) (value ? 1 : 0));
   }

   public boolean readBoolean()
   {
      return switch (buffer.get())
      {
         case 0 -> false;
         case 1 -> true;
         default -> throw new RuntimeException("Unknown boolean value");
      };
   }

   public void readString(StringBuilder destination)
   {
      if (destination == null)
      {
         throw new NullPointerException("CDRBuffer.readString destination StringBuilder is null");
      }

      // Get the length of the string
      int length = readInt() - 1; // -1 to remove null terminator

      // Clear the destination and read all characters into it
      destination.setLength(length);
      for (int i = 0; i < length; ++i)
      {
         char c = readChar();
         destination.setCharAt(i, c);
      }

      // Read the null terminator
      readChar();
   }

   public void writeString(StringBuilder value)
   {
      // Write length of string
      int length = value.length();
      writeInt(length + 1); // Length of string + null terminator

      // Write the string
      for (int i = 0; i < length; ++i)
      {
         char c = value.charAt(i);
         writeChar(c);
      }

      // Add null terminator
      writeChar('\0');
   }

   public void readWString(StringBuilder destination)
   {
      if (destination == null)
      {
         throw new NullPointerException("CDRBuffer.readWString destination StringBuilder is null");
      }

      int charLength = readInt();

      // Clear the destination and read all characters into it
      destination.setLength(charLength);
      for (int i = 0; i < charLength; ++i)
      {
         int wchar = readWchar();
         destination.setCharAt(i, (char) wchar); // This is safe - last 2 bytes are always unused
      }

      // wstring has no null terminator
   }

   public void writeWString(StringBuilder value)
   {
      int charLength = value.length();
      writeWchar(charLength);

      for (int i = 0; i < charLength; i++)
      {
         writeInt(value.codePointAt(i));
      }

      // wstring has no null terminator
   }

   public void alignBuffer(int byteBoundary)
   {
      int adv = ((buffer.position() - PAYLOAD_HEADER.length) % byteBoundary);

      if (adv != 0)
      {
         int offset = byteBoundary - adv;
         buffer.position(buffer.position() + offset);
      }
   }

   public static int alignment(int currentAlignment, int bytes)
   {
      return (bytes - (currentAlignment % bytes)) & (bytes - 1);
   }

   public static ByteOrder byteOrder(short encapsulation)
   {
      return switch (encapsulation)
      {
         case CDR_LE, PL_CDR_LE -> ByteOrder.LITTLE_ENDIAN;
         case CDR_BE, PL_CDR_BE -> ByteOrder.BIG_ENDIAN;
         default -> throw new RuntimeException("Unsupported encapsulation");
      };
   }
}