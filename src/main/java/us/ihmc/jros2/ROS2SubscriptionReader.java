package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.pointers.SampleInfo;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;

import java.nio.ByteBuffer;

import static us.ihmc.fastddsjava.pointers.fastddsjava.fastddsjava_datareader_get_unread_count;
import static us.ihmc.fastddsjava.pointers.fastddsjava.fastddsjava_datareader_take_next_sample;

public class ROS2SubscriptionReader<T extends ROS2Message<T>>
{
   private final Pointer fastddsDataReader;
   private final ROS2TopicData topicData;
   private final fastddsjava_TopicDataWrapper topicDataWrapper;
   private final CDRBuffer cdrBuffer = new CDRBuffer();
   private ByteBuffer readBuffer;

   private final SampleInfo sampleInfo;

   public ROS2SubscriptionReader(Pointer fastddsDataReader, ROS2TopicData topicData)
   {
      this.fastddsDataReader = fastddsDataReader;
      this.topicData = topicData;
      topicDataWrapper = new fastddsjava_TopicDataWrapper(topicData.topicDataWrapperType.create_data());

      sampleInfo = new SampleInfo();
   }

   public int getUnreadCount()
   {
      return fastddsjava_datareader_get_unread_count(fastddsDataReader);
   }

   public void takeNextSample(T data)
   {
      int messageSizeBytes = CDRBuffer.PAYLOAD_HEADER.length + data.calculateSizeBytes();

      if (readBuffer == null || readBuffer.capacity() < messageSizeBytes)
      {
         readBuffer = ByteBuffer.allocate(messageSizeBytes);
         cdrBuffer.setBuffer(readBuffer);
      }

      fastddsjava_datareader_take_next_sample(fastddsDataReader, topicDataWrapper, sampleInfo);

      cdrBuffer.readPayloadHeader();

      data.deserialize(cdrBuffer);

      readBuffer.rewind();
   }

   protected void close()
   {
      topicData.topicDataWrapperType.delete_data(topicDataWrapper);

      sampleInfo.close();
   }
}
