package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.fastddsjavaTools;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;

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

   public void publish(T message)
   {
      // TODO: remove +4 payload header
      int messageSizeBytes = CDRBuffer.PAYLOAD_HEADER.length + message.calculateSizeBytes();

      cdrBuffer.ensureRemainingCapacity(messageSizeBytes);

      // TODO: check if we can shrink the writeBuffer to save memory

      cdrBuffer.writePayloadHeader();
      message.serialize(cdrBuffer);

      topicDataWrapper.data_vector().resize(messageSizeBytes);
      topicDataWrapper.data_ptr().put(cdrBuffer.getBufferUnsafe().array(), 0, messageSizeBytes);

      int ret = fastddsjava_datawriter_write(fastddsDataWriter, topicDataWrapper);
      fastddsjavaTools.retcodePrintOnError(ret);
   }

   protected void close(Pointer fastddsParticipant)
   {
      topicData.topicDataWrapperType.delete_data(topicDataWrapper);

      int ret = fastddsjava_delete_datawriter(fastddsPublisher, fastddsDataWriter);
      fastddsjavaTools.retcodePrintOnError(ret);

      ret = fastddsjava_delete_publisher(fastddsParticipant, fastddsPublisher);
      fastddsjavaTools.retcodePrintOnError(ret);
   }
}
