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

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.natives.fastddsjava;
import us.ihmc.fastddsjava.natives.fastddsjavaCallback;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static us.ihmc.fastddsjava.fastddsjavaTools.retcodePrintOnError;
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
    * Settings
    */
   private final boolean recordStatistics;

   /*
    * Locks
    */
   protected final ReadWriteLock closeLock;
   protected boolean closed;

   /*
    * Discovery
    */
   private final Object discoveryLock;
   private volatile boolean subscriptionDiscovered;

   /*
    * Fast-DDS pointers
    */
   private final TopicData topicData;
   private final long fastddsTopicData;

   /*
    * Write buffer
    */
   private final CDRBuffer writeBuffer;
   private int lastPayloadSizeBytes;

   /*
    * Statistics
    */
   private final int statisticsCalculatorCount;
   private final StatisticsCalculator[] statisticsCalculators;
   private Method getHeaderMethod;
   private long lastPublishTime;

   /*
    * GUID
    */
   private final Guid guid;

   /*
    * Fast-DDS pointers (endpoints)
    */
   private final fastddsjavaCallback fastddsPublicationMatchedCallback;
   private final long fastddsDataWriterListener;
   private final long fastddsPublisher;
   private final long fastddsDataWriter;

   /**
    * Use {@link ROS2Node#createPublisher(ROS2Topic, ROS2QoSProfile)}
    */
   ROS2Publisher(long fastddsParticipant, String publisherProfileName, ROS2Topic<T> topic, TopicData topicData)
   {
      // Statistics recording is opt-in: it adds lock + reflective header access on the publish path.
      this(fastddsParticipant, publisherProfileName, topic, topicData, false);
   }

   ROS2Publisher(long fastddsParticipant, String publisherProfileName, ROS2Topic<T> topic, TopicData topicData, boolean recordStatistics)
   {
      this.topic = topic;
      this.recordStatistics = recordStatistics;

      closeLock = new ReentrantReadWriteLock(true);
      closed = false;

      discoveryLock = new Object();
      subscriptionDiscovered = false;

      this.topicData = topicData;
      fastddsTopicData = fastddsjava.createData(topicData.fastddsTopicDataWrapperType);
      writeBuffer = new CDRBuffer();

      // Grow the write buffer once up front for the empty message size so steady-state
      // fixed-size publishes do not allocate on first use.
      T emptyMessage = ROS2Message.createInstance(topic.getType());
      int initialPayloadSizeBytes = CDRBuffer.PAYLOAD_HEADER.length + emptyMessage.calculateSizeBytes(0);
      writeBuffer.ensureRemainingCapacity(initialPayloadSizeBytes);
      fastddsjava.topicDataResize(fastddsTopicData, initialPayloadSizeBytes);
      lastPayloadSizeBytes = initialPayloadSizeBytes;

      statisticsCalculatorCount = MessageMetadataType.values.length;
      statisticsCalculators = new StatisticsCalculator[statisticsCalculatorCount];
      for (int i = 0; i < statisticsCalculatorCount; ++i)
      {
         statisticsCalculators[i] = new StatisticsCalculator();
      }
      getHeaderMethod = ROS2Message.getHeaderMethod(topic.getType());
      lastPublishTime = Long.MIN_VALUE;

      guid = new Guid();

      // Initialize callback and listener last to ensure the rest of the state exists before they run
      fastddsPublicationMatchedCallback = () ->
      {
         synchronized (discoveryLock)
         {
            subscriptionDiscovered = true;
            discoveryLock.notifyAll();
         }
      };
      fastddsDataWriterListener = fastddsjava.createDataWriterListener();
      fastddsjava.dataWriterListenerSetOnPublicationMatched(fastddsDataWriterListener, fastddsPublicationMatchedCallback);
      fastddsPublisher = fastddsjava.createPublisher(fastddsParticipant, publisherProfileName);
      fastddsDataWriter = fastddsjava.createDataWriter(fastddsPublisher, topicData.fastddsTopic, publisherProfileName);
      fastddsjava.dataWriterSetListener(fastddsDataWriter, fastddsDataWriterListener);

      // Check if subscription is already matched (outside of discovery lock to avoid nested synchronization)
      if (getPublicationMatchedStatus() > 0)
      {
         synchronized (discoveryLock)
         {
            subscriptionDiscovered = true;
         }
      }
   }

   public void publish(T message)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            writeAndPublish(message, recordStatistics);
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }
   }

   /** Used by {@link AsyncROS2Publisher} on the deferred publish thread (no close lock, no statistics). */
   protected void publishAsync(T message)
   {
      if (!closed)
      {
         writeAndPublish(message, false);
      }
   }

   protected void preallocateWriteBuffer(int payloadSizeBytes)
   {
      synchronized (writeBuffer)
      {
         writeBuffer.rewind();
         writeBuffer.ensureRemainingCapacity(payloadSizeBytes);
         fastddsjava.topicDataResize(fastddsTopicData, payloadSizeBytes);
         lastPayloadSizeBytes = payloadSizeBytes;
      }
   }

   private void writeAndPublish(T message, boolean recordStatistics)
   {
      int payloadSizeBytes;

      synchronized (writeBuffer)
      {
         writeBuffer.rewind();

         // Presize from calculateSizeBytes; actual payload length is the buffer position after serialize.
         int estimatedSizeBytes = CDRBuffer.PAYLOAD_HEADER.length + message.calculateSizeBytes(0);
         if (estimatedSizeBytes > writeBuffer.getBufferUnsafe().capacity())
         {
            writeBuffer.ensureRemainingCapacity(estimatedSizeBytes);
         }

         writeBuffer.writePayloadHeader();
         message.serialize(writeBuffer);
         payloadSizeBytes = writeBuffer.getBufferUnsafe().position();

         if (payloadSizeBytes != lastPayloadSizeBytes)
         {
            fastddsjava.topicDataResize(fastddsTopicData, payloadSizeBytes);
            lastPayloadSizeBytes = payloadSizeBytes;
         }

         fastddsjava.topicDataWriteBuffer(fastddsTopicData, writeBuffer.getBufferUnsafe(), 0, payloadSizeBytes);
      }

      retcodePrintOnError(fastddsjava.dataWriterWrite(fastddsDataWriter, fastddsTopicData));

      if (recordStatistics)
      {
         recordStatistics(message, payloadSizeBytes, System.currentTimeMillis());
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
               jros2.logError("Failed to get the message header. Not recording message age statistics from now on.", e);
               getHeaderMethod = null;
            }
         }
      }
   }

   /**
    * Get the GUID (Globally Unique Identifier) for this publisher.
    * The GUID is assigned by Fast-DDS and uniquely identifies this publisher instance.
    * <p>
    * Returns a cached {@link Guid} instance owned by this publisher; its bytes are refreshed from DDS on each call.
    * Copy with {@link Guid#set(Guid)} if you need an independent snapshot.
    */
   public Guid getGuid()
   {
      fastddsjava.getWriterGuid(fastddsDataWriter, guid.getValue());
      return guid;
   }

   /**
    * Use {@link ROS2Node#destroyPublisher(ROS2Publisher)}
    */
   protected void close(long fastddsParticipant)
   {
      closeLock.writeLock().lock();
      boolean wasClosed = closed;
      closed = true;
      closeLock.writeLock().unlock();

      if (!wasClosed)
      {
         fastddsjava.dataWriterSetListener(fastddsDataWriter, 0);
         retcodePrintOnError(fastddsjava.deleteDataWriter(fastddsPublisher, fastddsDataWriter));
         // Intentionally do not delete listener/callback yet; Fast-DDS may still invoke in-flight callbacks.
         fastddsjava.deleteData(topicData.fastddsTopicDataWrapperType, fastddsTopicData);
         retcodePrintOnError(fastddsjava.deletePublisher(fastddsParticipant, fastddsPublisher));
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

   /**
    * Get the current number of matched subscriptions for this publisher.
    *
    * @return The number of subscriptions currently matched to this publisher
    */
   public int getPublicationMatchedStatus()
   {
      int count;

      closeLock.readLock().lock();
      try
      {
         if (closed)
         {
            count = 0;
         }
         else
         {
            count = fastddsjava.dataWriterGetPublicationMatchedCount(fastddsDataWriter);
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return count;
   }

   /**
    * Wait for a subscription to be discovered.
    * <p>
    * This method blocks until a subscription is discovered or the timeout expires.
    *
    * @param timeoutMs Timeout in milliseconds to wait for subscription discovery
    * @return true if a subscription was discovered, false if timeout occurred
    */
   public boolean waitForSubscription(long timeoutMs)
   {
      boolean discovered;
      long startTime = System.nanoTime();
      long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);

      synchronized (discoveryLock)
      {
         boolean timedOut = false;
         while (!subscriptionDiscovered && !closed && !timedOut)
         {
            long elapsedNanos = System.nanoTime() - startTime;
            long remainingNanos = timeoutNanos - elapsedNanos;
            long remainingMs = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
            if (remainingMs <= 0)
            {
               timedOut = true;
            }
            else
            {
               try
               {
                  discoveryLock.wait(remainingMs);
               }
               catch (InterruptedException e)
               {
                  Thread.currentThread().interrupt();
                  timedOut = true;
               }
            }
         }

         discovered = subscriptionDiscovered;
      }

      return discovered;
   }
}
