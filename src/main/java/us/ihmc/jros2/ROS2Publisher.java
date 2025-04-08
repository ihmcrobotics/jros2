package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;

import static us.ihmc.fastddsjava.fastddsjavaTools.retcodePrintOnError;
import static us.ihmc.fastddsjava.pointers.fastddsjava.*;

public class ROS2Publisher<T extends ROS2Message<T>>
{
   static
   {
      fastddsjavaNativeLibrary.load();
   }

   private final Pointer fastddsPublisher;
   private final Pointer fastddsDataWriter;
   private final ROS2TopicData topicData;
   private final fastddsjava_TopicDataWrapper topicDataWrapper;
   private final CDRBuffer cdrBuffer;

   protected ROS2Publisher(Pointer fastddsParticipant, String publisherProfileName, ROS2TopicData topicData)
   {
      this.topicData = topicData;
      topicDataWrapper = new fastddsjava_TopicDataWrapper(topicData.topicDataWrapperType.create_data());
      this.fastddsPublisher = fastddsjava_create_publisher(fastddsParticipant, publisherProfileName);
      this.fastddsDataWriter = fastddsjava_create_datawriter(fastddsPublisher, topicData.fastddsTopic, publisherProfileName);
      cdrBuffer = new CDRBuffer();
   }

   public synchronized void publish(T message)
   {
      if (!isClosed())
      {
         int messageSizeBytes = CDRBuffer.PAYLOAD_HEADER.length + message.calculateSizeBytes();
         cdrBuffer.ensureRemainingCapacity(messageSizeBytes);

         // TODO: check if we can shrink the writeBuffer to save memory

         cdrBuffer.writePayloadHeader();
         message.serialize(cdrBuffer);

         topicDataWrapper.data_vector().resize(messageSizeBytes);
         topicDataWrapper.data_ptr().put(cdrBuffer.getBufferUnsafe().array(), 0, messageSizeBytes);

         retcodePrintOnError(fastddsjava_datawriter_write(fastddsDataWriter, topicDataWrapper));
      }
   }

   protected synchronized void close(Pointer fastddsParticipant)
   {
      if (!isClosed())
      {
         topicData.topicDataWrapperType.delete_data(topicDataWrapper);

         retcodePrintOnError(fastddsjava_delete_datawriter(fastddsPublisher, fastddsDataWriter));
         retcodePrintOnError(fastddsjava_delete_publisher(fastddsParticipant, fastddsPublisher));

         fastddsPublisher.setNull();
      }
   }

   protected boolean isClosed()
   {
      return fastddsPublisher.isNull();
   }
}
