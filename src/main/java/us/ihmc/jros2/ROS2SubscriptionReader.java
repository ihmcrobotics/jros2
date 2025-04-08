package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.pointers.SampleInfo;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;
import us.ihmc.log.LogTools;

import static us.ihmc.fastddsjava.fastddsjavaTools.retcodePrintOnError;
import static us.ihmc.fastddsjava.pointers.fastddsjava.fastddsjava_datareader_get_unread_count;
import static us.ihmc.fastddsjava.pointers.fastddsjava.fastddsjava_datareader_take_next_sample;

public class ROS2SubscriptionReader<T extends ROS2Message<T>>
{
   static
   {
      fastddsjavaNativeLibrary.load();
   }

   private final Pointer fastddsDataReader;
   private final ROS2TopicData topicData;
   private final fastddsjava_TopicDataWrapper topicDataWrapper;
   private final CDRBuffer cdrBuffer;
   private final SampleInfo sampleInfo;

   protected ROS2SubscriptionReader(Pointer fastddsDataReader, ROS2TopicData topicData)
   {
      this.fastddsDataReader = fastddsDataReader;
      this.topicData = topicData;
      topicDataWrapper = new fastddsjava_TopicDataWrapper(topicData.topicDataWrapperType.create_data());
      cdrBuffer = new CDRBuffer();
      sampleInfo = new SampleInfo();
   }

   public int getUnreadCount()
   {
      return fastddsjava_datareader_get_unread_count(fastddsDataReader);
   }

   public void takeNextSample(T data)
   {
      if (cdrBuffer.getBufferUnsafe().position() != 0)
      {
         LogTools.error("Unsafe buffer position");
         return;
      }

      retcodePrintOnError(fastddsjava_datareader_take_next_sample(fastddsDataReader, topicDataWrapper, sampleInfo));

      int sampleSize = (int) topicDataWrapper.data_vector().size();
      cdrBuffer.ensureRemainingCapacity(sampleSize);
      topicDataWrapper.data_ptr().get(cdrBuffer.getBufferUnsafe().array(), 0, sampleSize);

      cdrBuffer.readPayloadHeader();
      data.deserialize(cdrBuffer);

      cdrBuffer.getBufferUnsafe().rewind();
   }

   protected synchronized void close()
   {
      if (!isClosed())
      {
         topicData.topicDataWrapperType.delete_data(topicDataWrapper);

         sampleInfo.close();

         topicDataWrapper.setNull();
      }
   }

   protected boolean isClosed()
   {
      return topicDataWrapper.isNull();
   }
}
