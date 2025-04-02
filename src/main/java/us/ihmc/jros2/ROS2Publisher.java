package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.pointers.fastddsjava;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;

import java.io.Closeable;
import java.nio.ByteBuffer;

import static us.ihmc.fastddsjava.pointers.fastddsjava.fastddsjava_datawriter_write;

public class ROS2Publisher implements Closeable
{
   private final Pointer fastddsPublisher;
   private final Pointer fastddsDataWriter;
   private final ROS2TopicData topicData;
   private final fastddsjava_TopicDataWrapper topicDataWrapper;

   private ByteBuffer writeBuffer;
   private CDRBuffer cdrBuffer;

   protected ROS2Publisher(Pointer fastddsPublisher, Pointer fastddsDataWriter, ROS2TopicData topicData)
   {
      this.fastddsPublisher = fastddsPublisher;
      this.fastddsDataWriter = fastddsDataWriter;
      this.topicData = topicData;
      topicDataWrapper = new fastddsjava_TopicDataWrapper(topicData.topicDataWrapperType.create_data());

      writeBuffer = ByteBuffer.allocate(1);
      cdrBuffer = new CDRBuffer(writeBuffer);
   }

   public <T extends ROS2Message<T>> void publish(T message)
   {
      // TODO: remove +4 payload header
      if (writeBuffer.capacity() < (message.calculateSizeBytes() + 4))
      {
         writeBuffer = ByteBuffer.allocate(message.calculateSizeBytes() + 4);
         cdrBuffer = new CDRBuffer(writeBuffer);
      }

      writeBuffer.rewind();
      cdrBuffer.writePayloadHeader();
      message.serialize(cdrBuffer);

      fastddsjava_datawriter_write(fastddsDataWriter, topicDataWrapper);
   }

   @Override
   public void close()
   {
      topicData.topicDataWrapperType.delete_data(topicDataWrapper);

      fastddsjava.fastddsjava_delete_datawriter(fastddsPublisher, fastddsDataWriter);
   }
}
