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
import us.ihmc.fastddsjava.pointers.PublicationMatchedStatus;
import us.ihmc.fastddsjava.pointers.fastddsjava_DataWriterListener;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
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
   private final PublicationMatchedStatus publicationMatchedStatus;
   private final fastddsjava_DataWriterListener listener;
   private final fastddsjava_OnPublicationCallback fastddsPublicationMatchedCallback;

   /*
    * Discovery
    */
   private final Object discoveryLock;
   private volatile boolean subscriberDiscovered;

   /*
    * Write buffer
    */
   private final CDRBuffer writeBuffer;

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
   ROS2Publisher(Pointer fastddsParticipant, String publisherProfileName, ROS2Topic<T> topic, TopicData topicData)
   {
      this.topic = topic;
      this.topicData = topicData;

      closeLock = new ReentrantReadWriteLock(true);
      closed = false;

      discoveryLock = new Object();
      subscriberDiscovered = false;

      topicDataWrapper = new fastddsjava_TopicDataWrapper(topicData.topicDataWrapperType.create_data());
      publicationMatchedStatus = new PublicationMatchedStatus();

      writeBuffer = new CDRBuffer();

      statisticsCalculatorCount = MessageMetadataType.values.length;
      statisticsCalculators = new StatisticsCalculator[statisticsCalculatorCount];
      for (int i = 0; i < statisticsCalculatorCount; ++i)
      {
         statisticsCalculators[i] = new StatisticsCalculator();
      }
      getHeaderMethod = ROS2Message.getHeaderMethod(topic.getType());
      lastPublishTime = Long.MIN_VALUE;

      // Initialize callback and listener last to ensure the rest of the state exists before they run
      fastddsPublicationMatchedCallback = new fastddsjava_OnPublicationMatchedCallbackImpl();
      listener = new fastddsjava_DataWriterListener();
      listener.set_on_publication_callback(fastddsPublicationMatchedCallback);
      fastddsPublisher = fastddsjava_create_publisher(fastddsParticipant, publisherProfileName);
      fastddsDataWriter = fastddsjava_create_datawriter(fastddsPublisher, topicData.fastddsTopic, publisherProfileName);
      fastddsjava_datawriter_set_listener(fastddsDataWriter, listener);

      // Check if subscriber is already matched (outside of discovery lock to avoid nested synchronization)
      if (getPublicationMatchedStatus() > 0)
      {
         synchronized (discoveryLock)
         {
            subscriberDiscovered = true;
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
            int payloadSizeBytes;

            synchronized (writeBuffer)
            {
               payloadSizeBytes = CDRBuffer.PAYLOAD_HEADER.length + message.calculateSizeBytes(CDRBuffer.PAYLOAD_HEADER.length);
               boolean resized = writeBuffer.ensureRemainingCapacity(payloadSizeBytes);
               // Rewind buffer to ensure we're starting at position = 0
               writeBuffer.rewind();

               // TODO: check if we can shrink the writeBuffer to save memory

               writeBuffer.writePayloadHeader();
               message.serialize(writeBuffer);

               if (resized)
               {
                  topicDataWrapper.data_vector().resize(payloadSizeBytes);
               }

               topicDataWrapper.data_ptr().put(writeBuffer.getBufferUnsafe().array(), 0, payloadSizeBytes);
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

   private class fastddsjava_OnPublicationMatchedCallbackImpl extends fastddsjava_OnPublicationCallback
   {
      @Override
      public void call()
      {
         synchronized (discoveryLock)
         {
            subscriberDiscovered = true;
            discoveryLock.notifyAll();
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
         // Clear listener from datawriter to prevent further callbacks
         fastddsjava_datawriter_set_listener(fastddsDataWriter, null);

         // Delete the datawriter to ensure Fast-DDS stops using the listener
         retcodePrintOnError(fastddsjava_delete_datawriter(fastddsPublisher, fastddsDataWriter));

         // Note: We intentionally do NOT close the listener and callback objects here.
         // Due to Fast-DDS's asynchronous nature, callbacks may still be in-flight when we delete the datawriter.
         // Calling close() immediately can cause JVM crashes as Fast-DDS tries to invoke callbacks on freed memory.
         // Instead, we rely on Java garbage collection to clean up these objects after Fast-DDS is done with them.
         // listener.close();
         // fastddsPublicationMatchedCallback.close();

         publicationMatchedStatus.close();

         topicData.topicDataWrapperType.delete_data(topicDataWrapper);
         topicDataWrapper.close();

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

   /**
    * Get the current number of matched subscribers for this publisher.
    *
    * @return The number of subscribers currently matched to this publisher
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
            synchronized (publicationMatchedStatus)
            {
               fastddsjava_datawriter_get_publication_matched_status(fastddsDataWriter, publicationMatchedStatus);
               count = publicationMatchedStatus.current_count();
            }
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return count;
   }

   /**
    * Wait for a subscriber to be discovered.
    * <p>
    * This method blocks until a subscriber is discovered or the timeout expires.
    *
    * @param timeoutMs Timeout in milliseconds to wait for subscriber discovery
    * @return true if a subscriber was discovered, false if timeout occurred
    */
   public boolean waitForSubscriber(long timeoutMs)
   {
      boolean discovered = false;
      long startTime = System.nanoTime();
      long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);

      synchronized (discoveryLock)
      {
         boolean timedOut = false;
         while (!subscriberDiscovered && !closed && !timedOut)
         {
            long elapsedNanos = System.nanoTime() - startTime;
            if (elapsedNanos >= timeoutNanos)
            {
               timedOut = true;
            }
            else
            {
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
         }

         discovered = subscriberDiscovered;
      }

      return discovered;
   }
}
