/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;
import us.ihmc.log.LogTools;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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

   /*
    * Write buffer
    */
   private final CDRBuffer writeBuffer;

   /*
    * Statistics
    */
   private final StatisticsCalculator[] statisticsCalculators;
   private final int statisticsCalculatorCount;
   private long lastPublishTime;
   private Method getHeaderMethod;

   /*
    * Locks
    */
   protected final Object closeLock;
   protected volatile boolean closed;

   /**
    * Use {@link ROS2Node#createPublisher(ROS2Topic, ROS2QoSProfile)}
    */
   ROS2Publisher(Pointer fastddsParticipant, String publisherProfileName, ROS2Topic<T> topic, TopicData topicData)
   {
      this.topicData = topicData;
      this.topic = topic;

      topicDataWrapper = new fastddsjava_TopicDataWrapper(topicData.topicDataWrapperType.create_data());
      fastddsPublisher = fastddsjava_create_publisher(fastddsParticipant, publisherProfileName);
      fastddsDataWriter = fastddsjava_create_datawriter(fastddsPublisher, topicData.fastddsTopic, publisherProfileName);
      writeBuffer = new CDRBuffer();

      statisticsCalculatorCount = values.length;
      statisticsCalculators = new StatisticsCalculator[statisticsCalculatorCount];
      for (int i = 0; i < statisticsCalculatorCount; ++i)
      {
         statisticsCalculators[i] = new StatisticsCalculator();
      }
      getHeaderMethod = ROS2Message.getHeaderMethod(topic.getType());
      lastPublishTime = Long.MIN_VALUE;

      closed = false;
      closeLock = new Object();
   }

   public void publish(T message)
   {
      int payloadSizeBytes;

      synchronized (writeBuffer)
      {
         if (!closed)
         {
            payloadSizeBytes = CDRBuffer.PAYLOAD_HEADER.length + message.calculateSizeBytes(CDRBuffer.PAYLOAD_HEADER.length);
            boolean resized = writeBuffer.ensureRemainingCapacity(payloadSizeBytes);
            // Rewind buffer to ensure we're starting at position = 0
            writeBuffer.rewind();

            // TODO: Possibly check if we can shrink the writeBuffer to save memory
            writeBuffer.writePayloadHeader();
            message.serialize(writeBuffer);

            if (resized)
            {
               topicDataWrapper.data_vector().resize(payloadSizeBytes);
            }

            topicDataWrapper.data_ptr().put(writeBuffer.getBufferUnsafe().array(), 0, payloadSizeBytes);

            retcodePrintOnError(fastddsjava_datawriter_write(fastddsDataWriter, topicDataWrapper));

            recordStatistics(message, payloadSizeBytes, System.currentTimeMillis());
         }
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
         // Note: Reflection invoke() may cause some allocation, but this is only for statistics
         // and can be disabled if needed. The alternative would be to require messages to implement
         // a getHeader() interface method, but that would break compatibility.
         if (getHeaderMethod != null)
         {
            try
            {
               std_msgs.Header header = (std_msgs.Header) getHeaderMethod.invoke(message);
               long timestampMillis = (1000L * header.getStamp().getSec()) + (header.getStamp().getNanosec() / 1000000L);
               statisticsCalculators[AGE.ordinal()].record(publishTimeMillis - timestampMillis);
            }
            catch (IllegalAccessException | InvocationTargetException e)
            {
               // Only log if logging is enabled to avoid allocation
               if (LogTools.isErrorEnabled())
               {
                  LogTools.error("Failed to get the message header. Not recording message age statistics from now on.");
               }
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
      final boolean wasClosed;
      synchronized (closeLock)
      {
         wasClosed = closed;
         closed = true;
      }

      if (!wasClosed)
      {
         // Wait for any ongoing publish to complete
         // noinspection EmptySynchronizedStatement
         synchronized (writeBuffer)
         {
         }

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
    *
    * @return the type class held in the {@link ROS2Topic}
    */
   public Class<T> getTopicType()
   {
      return topic.getType();
   }

   /**
    * Get the topic name for which this publisher will use when publishing.
    *
    * @return the topic name held in the {@link ROS2Topic}
    */
   public String getTopicName()
   {
      return topic.getName();
   }
}
