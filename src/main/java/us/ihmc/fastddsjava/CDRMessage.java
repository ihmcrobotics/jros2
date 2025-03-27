package us.ihmc.fastddsjava;

import us.ihmc.fastddsjava.pointers.fastddsjava;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;

import java.nio.ByteBuffer;

public class CDRMessage
{
   // DDSI-RTPS/2.3 10.2
   public static void writeSerializationPayloadHeader(fastddsjava_TopicDataWrapper data)
   {
      // RepresentationIdentifier
      byte[] identifier = new byte[16];
      // TODO: creates garbage
      ByteBuffer identifierBuffer = ByteBuffer.wrap(identifier);
      identifierBuffer.putLong(0);
      identifierBuffer.putLong(fastddsjava.CDR_LE);
      data.data_vector().put(identifier);

      // RepresentationOptions
      byte[] options = new byte[16];
      data.data_vector().put(options);
   }

   public static void writeBoolean(fastddsjava_TopicDataWrapper data, boolean value)
   {
      data.data_vector().put((byte) (value ? 0x1 : 0x0));
   }
}
