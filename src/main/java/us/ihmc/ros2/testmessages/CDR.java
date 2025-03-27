package us.ihmc.ros2.testmessages;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static us.ihmc.fastddsjava.pointers.fastddsjava.*;

public final class CDR
{
   public static ByteOrder byteOrder(short encapsulation)
   {
      return switch (encapsulation)
      {
         case CDR_LE, PL_CDR_LE -> ByteOrder.LITTLE_ENDIAN;
         case CDR_BE, PL_CDR_BE -> ByteOrder.BIG_ENDIAN;
         default -> throw new RuntimeException("Unsupported encapsulation");
      };
   }

   // DDSI-RTPS/2.3 10.2
   public static void writeSerializationPayloadHeader(ByteBuffer buffer)
   {
      // RepresentationIdentifier
      buffer.putShort(CDR_LE);
      // RepresentationOptions
      buffer.putShort((short) 0);
   }

   public static short readSerializationPayloadHeader(ByteBuffer buffer)
   {
      // RepresentationIdentifier
      short encapsulation = buffer.getShort();
      // RepresentationOptions
      buffer.getShort();
      return encapsulation;
   }

   public static void writeBoolean(boolean value, ByteBuffer buffer)
   {
      buffer.put((byte) (value ? 1 : 0));
   }

   public static boolean readBoolean(ByteBuffer buffer)
   {
      return switch (buffer.get())
      {
         case 0 -> false;
         case 1 -> true;
         default -> throw new RuntimeException("Unknown value");
      };
   }
}
