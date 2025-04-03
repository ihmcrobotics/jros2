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
   private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

   // RepresentationIdentifier, RepresentationOptions
   public static final byte[] PAYLOAD_HEADER = {0, 1, 0, 0};

   private ByteBuffer buffer;

   public CDRBuffer(ByteBuffer buffer)
   {
      this.buffer = buffer;
   }

   public CDRBuffer()
   {
      this(EMPTY_BUFFER);
   }

   public void writePayloadHeader()
   {
      if (buffer.position() == 0)
      {
         buffer.put(PAYLOAD_HEADER);

         // TODO:
         buffer.order(ByteOrder.LITTLE_ENDIAN);
      }
   }

   public void readPayloadHeader()
   {
      if (buffer.position() == 0)
      {
         // RepresentationIdentifier
         short encapsulation = buffer.getShort();
         // RepresentationOptions
         short options = buffer.getShort();
      }
   }

   public void writeByte(byte value)
   {
      buffer.put(value);
   }

   public byte readByte()
   {
      return buffer.get();
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

   public void setBuffer(ByteBuffer buffer)
   {
      this.buffer = buffer;
   }

   public ByteBuffer getBufferUnsafe()
   {
      return buffer;
   }
}
