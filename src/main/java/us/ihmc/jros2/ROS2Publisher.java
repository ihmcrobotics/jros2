package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import std_msgs.msg.dds.Header;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;
import us.ihmc.log.LogTools;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static us.ihmc.fastddsjava.fastddsjavaTools.retcodePrintOnError;
import static us.ihmc.fastddsjava.pointers.fastddsjava.*;
import static us.ihmc.jros2.MessageStatisticsProvider.MessageMetadataType.*;

/**
 * A ROS 2-compatible publisher for publishing {@link ROS2Message} types.
 */
public class ROS2Publisher<T extends ROS2Message<T>> implements MessageStatisticsProvider
{
   static
   {
      jros2.load();
   }

   /*
    * Debug
    */
   private final ROS2Topic<T> topic;

   /*
    * Fast-DDS pointers
    */
   private final Pointer fastddsPublisher;
   private final Pointer fastddsDataWriter;
   private final TopicData topicData;
   private final fastddsjava_TopicDataWrapper topicDataWrapper;

   private final CDRBuffer cdrBuffer;

   /*
    * Locks
    */
   protected final ReadWriteLock closeLock;
   protected boolean closed;

   /*
    * Statistics
    */
   private final StatisticsCalculator[] statisticsCalculators;
   private final int statisticsCalculatorCount;
   private long lastPublishTime;
   private Method getHeaderMethod;

   /**
    * Use {@link ROS2Node#createPublisher(ROS2Topic, ROS2QoSProfile)}
    */
   protected ROS2Publisher(Pointer fastddsParticipant, String publisherProfileName, ROS2Topic<T> topic, TopicData topicData)
   {
      this.topicData = topicData;
      this.topic = topic;

      closeLock = new ReentrantReadWriteLock(true);
      closed = false;

      topicDataWrapper = new fastddsjava_TopicDataWrapper(topicData.topicDataWrapperType.create_data());
      fastddsPublisher = fastddsjava_create_publisher(fastddsParticipant, publisherProfileName);
      fastddsDataWriter = fastddsjava_create_datawriter(fastddsPublisher, topicData.fastddsTopic, publisherProfileName);
      cdrBuffer = new CDRBuffer();

      statisticsCalculatorCount = MessageMetadataType.values.length;
      statisticsCalculators = new StatisticsCalculator[statisticsCalculatorCount];
      for (int i = 0; i < statisticsCalculatorCount; ++i)
      {
         statisticsCalculators[i] = new StatisticsCalculator();
      }
      getHeaderMethod = ROS2Message.getHeaderMethod(topic.getType());
      lastPublishTime = Long.MIN_VALUE;
   }

   public void publish(T message)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            int payloadSizeBytes;

            synchronized (cdrBuffer)
            {
               // Rewind buffer to ensure we're starting at position = 0
               cdrBuffer.rewind();

               payloadSizeBytes = CDRBuffer.PAYLOAD_HEADER.length + message.calculateSizeBytes();
               cdrBuffer.ensureRemainingCapacity(payloadSizeBytes);

               // TODO: check if we can shrink the writeBuffer to save memory

               cdrBuffer.writePayloadHeader();
               message.serialize(cdrBuffer);

               topicDataWrapper.data_vector().resize(payloadSizeBytes);
               topicDataWrapper.data_ptr().put(cdrBuffer.getBufferUnsafe().array(), 0, payloadSizeBytes);
            }

            retcodePrintOnError(fastddsjava_datawriter_write(fastddsDataWriter, topicDataWrapper));
            recordStatistics(message, payloadSizeBytes, System.currentTimeMillis());
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }
   }

   private void recordStatistics(T message, long messageSizeBytes, long publishTimeMillis)
   {
      synchronized (statisticsCalculators)
      {
         // Record message size
         statisticsCalculators[SIZE.ordinal()].record(messageSizeBytes);

         // Record publish period if available
         if (lastPublishTime != Long.MIN_VALUE)
         {
            statisticsCalculators[PERIOD.ordinal()].record(publishTimeMillis - lastPublishTime);
         }
         lastPublishTime = publishTimeMillis;

         // Record publish age
         if (getHeaderMethod != null)
         {
            try
            {
               Header header = (Header) getHeaderMethod.invoke(message);
               long timestampMillis = (1000L * header.getStamp().getSec()) + (header.getStamp().getNanosec() / 1000000L);
               statisticsCalculators[AGE.ordinal()].record(publishTimeMillis - timestampMillis);
            }
            catch (IllegalAccessException | InvocationTargetException e)
            {
               LogTools.error("Failed to get the message header. Not recording message age statistics from now on.");
               getHeaderMethod = null;
            }
         }
      }
   }

   /**
    * Use {@link ROS2Node#destroyPublisher(ROS2Publisher)}
    */
   protected void close(Pointer fastddsParticipant)
   {
      closeLock.writeLock().lock();
      boolean wasClosed = closed;
      closed = true;
      closeLock.writeLock().unlock();

      if (!wasClosed)
      {
         topicData.topicDataWrapperType.delete_data(topicDataWrapper);

         retcodePrintOnError(fastddsjava_delete_datawriter(fastddsPublisher, fastddsDataWriter));
         retcodePrintOnError(fastddsjava_delete_publisher(fastddsParticipant, fastddsPublisher));
      }
   }

   @Override
   public void resetStatistics()
   {
      for (int i = 0; i < statisticsCalculatorCount; ++i)
      {
         statisticsCalculators[i].reset();
      }
   }

   @Override
   public void readStatistics(MessageMetadataType messageMetadataType, Statistics statisticToPack)
   {
      statisticsCalculators[messageMetadataType.ordinal()].read(statisticToPack);
   }

   public boolean isClosed()
   {
      return closed;
   }

   /**
    * Get the topic type class for which this publisher can publish.
    * @return the type class held in the {@link ROS2Topic}
    */
   public Class<T> getTopicType()
   {
      return topic.getType();
   }

   /**
    * Get the topic name for which this publisher will use when publishing.
    * @return the topic name held in the {@link ROS2Topic}
    */
   public String getTopicName()
   {
      return topic.getName();
   }
}
