package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;

import java.io.Closeable;
import java.nio.ByteBuffer;

import static us.ihmc.fastddsjava.pointers.fastddsjava.*;

public class ROS2Publisher implements Closeable
{
   private final Pointer fastddsPublisher;
   private final Pointer fastddsDataWriter;
   private final ROS2TopicData topicData;
   private final fastddsjava_TopicDataWrapper topicDataWrapper;

   private ByteBuffer writeBuffer;
   private CDRBuffer cdrBuffer;

   protected ROS2Publisher(Pointer fastddsPublisher, String publisherProfileName, ROS2TopicData topicData)
   {
      this.fastddsPublisher = fastddsPublisher;
      this.fastddsDataWriter = fastddsjava_create_datawriter(fastddsPublisher, topicData.fastddsTopic, publisherProfileName);
      this.topicData = topicData;
      topicDataWrapper = new fastddsjava_TopicDataWrapper(topicData.topicDataWrapperType.create_data());

      writeBuffer = ByteBuffer.allocate(1);
      cdrBuffer = new CDRBuffer(writeBuffer);
   }

   public <T extends ROS2Message<T>> void publish(T message)
   {
      // TODO: remove +4 payload header
      int messageSizeBytes = CDRBuffer.PAYLOAD_HEADER.length + message.calculateSizeBytes();

      if (writeBuffer.capacity() < messageSizeBytes)
      {
         writeBuffer = ByteBuffer.allocate(messageSizeBytes);
         cdrBuffer = new CDRBuffer(writeBuffer);
      }

      // TODO: check if we can shrink the writeBuffer to save memory

      writeBuffer.rewind();
      cdrBuffer.writePayloadHeader();
      message.serialize(cdrBuffer);

      topicDataWrapper.data_vector().resize(messageSizeBytes);
      topicDataWrapper.data_ptr().put(writeBuffer.array(), 0, messageSizeBytes);

      fastddsjava_datawriter_write(fastddsDataWriter, topicDataWrapper);
   }

   @Override
   public void close()
   {
      topicData.topicDataWrapperType.delete_data(topicDataWrapper);

      fastddsjava_delete_datawriter(fastddsPublisher, fastddsDataWriter);
   }
}
