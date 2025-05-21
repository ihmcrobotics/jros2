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

import static us.ihmc.fastddsjava.pointers.fastddsjava.*;

/**
 * <a href="https://www.omg.org/spec/DDSI-RTPS/2.3/PDF">...</a>
 * <a href="https://www.omg.org/spec/CORBA/3.3/Interoperability/PDF">...</a>
 */
public final class CDRBuffer
{
   // RepresentationIdentifier, RepresentationOptions
   public static final byte[] PAYLOAD_HEADER = {0, 1, 0, 0};

   private ByteBuffer buffer;

   public CDRBuffer()
   {
      buffer = ByteBuffer.allocate(1);
   }

   public ByteBuffer getBufferUnsafe()
   {
      return buffer;
   }

   public void ensureRemainingCapacity(int capacity)
   {
      int remainingCapacity = buffer.capacity() - buffer.position();

      if (remainingCapacity < capacity)
      {
         ByteBuffer newBuffer = ByteBuffer.allocate(buffer.position() + capacity);

         newBuffer.put(buffer);

         buffer = newBuffer;

         buffer.rewind(); // TODO check
      }
   }

   public void rewind()
   {
      buffer.rewind();
   }

   public void writePayloadHeader()
   {
      buffer.put(PAYLOAD_HEADER);

      // TODO:
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
      int charLength = readInt();

      // Clear the destination and read all characters into it
      destination.setLength(charLength);
      for (int i = 0; i < charLength; ++i)
      {
         int wchar = readWchar();
         destination.append((char) wchar); // This is safe - last 2 bytes are always unused
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
