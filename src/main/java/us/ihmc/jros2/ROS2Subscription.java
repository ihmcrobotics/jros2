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
import us.ihmc.fastddsjava.pointers.SampleInfo;
import us.ihmc.fastddsjava.pointers.SubscriptionMatchedStatus;
import us.ihmc.fastddsjava.pointers.fastddsjavaInfoMapper.fastddsjava_OnDataCallback;
import us.ihmc.fastddsjava.pointers.fastddsjavaInfoMapper.fastddsjava_OnSubscriptionCallback;
import us.ihmc.fastddsjava.pointers.fastddsjava_DataReaderListener;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static us.ihmc.fastddsjava.fastddsjavaTools.retcodePrintOnError;
import static us.ihmc.fastddsjava.pointers.fastddsjava.*;
import static us.ihmc.jros2.MessageStatisticsProvider.MessageMetadataType.*;

/**
 * A ROS 2-compatible subscription for ingesting {@link ROS2Message} types.
 */
public class ROS2Subscription<T extends ROS2Message<T>> implements MessageStatisticsProvider
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
   private final Pointer fastddsSubscriber;
   private final Pointer fastddsDataReader;
   private final fastddsjava_DataReaderListener listener;
   private final fastddsjava_OnDataCallback fastddsDataCallback;
   private final fastddsjava_OnSubscriptionCallback fastddsSubscriptionCallback;
   private final TopicData topicData;
   private final fastddsjava_TopicDataWrapper topicDataWrapper;
   private final SampleInfo sampleInfo;
   private final SubscriptionMatchedStatus subscriptionMatchedStatus;

   /*
    * Read buffer and readers
    */
   protected final CDRBuffer readBuffer;
   private final ROS2SubscriptionCallback<T> callback; // The callback may be null
   private final ROS2SubscriptionReader<T> subscriptionReader;

   /*
    * Locks
    */
   private final ReadWriteLock closeLock;
   private boolean closed;

   /*
    * Statistics
    */
   private final StatisticsCalculator[] statisticsCalculators;
   private final int statisticsCalculatorCount;
   private long lastReceiveTime;

   /*
    * Flags relating to received data from the native callback
    */
   protected boolean flagHadData;
   protected boolean flagNewData;

   /**
    * Use {@link ROS2Node#createSubscription(ROS2Topic, ROS2SubscriptionCallback, ROS2QoSProfile)}
    */
   protected ROS2Subscription(Pointer fastddsParticipant,
                              String subscriberProfileName,
                              ROS2SubscriptionCallback<T> callback /* Can be null */,
                              ROS2Topic<T> topic,
                              TopicData topicData)
   {
      this.callback = callback;
      this.topic = topic;
      this.topicData = topicData;

      closeLock = new ReentrantReadWriteLock(true);
      closed = false;

      readBuffer = new CDRBuffer();
      sampleInfo = new SampleInfo();
      subscriptionReader = new ROS2SubscriptionReader<>(this);

      statisticsCalculatorCount = MessageMetadataType.values.length;
      statisticsCalculators = new StatisticsCalculator[statisticsCalculatorCount];
      for (int i = 0; i < statisticsCalculatorCount; ++i)
      {
         statisticsCalculators[i] = new StatisticsCalculator();
      }
      lastReceiveTime = Long.MIN_VALUE;

      topicDataWrapper = new fastddsjava_TopicDataWrapper(topicData.topicDataWrapperType.create_data());

      fastddsDataCallback = new fastddsjava_OnDataCallback()
      {
         @Override
         public void call()
         {
            onDataCallback();
         }
      };
      fastddsSubscriptionCallback = new fastddsjava_OnSubscriptionCallback()
      {
         @Override
         public void call()
         {
            onSubscriptionCallback();
         }
      };
      listener = new fastddsjava_DataReaderListener();
      listener.set_on_data_available_callback(fastddsDataCallback);
      listener.set_on_subscription_callback(fastddsSubscriptionCallback);
      subscriptionMatchedStatus = new SubscriptionMatchedStatus();

      fastddsSubscriber = fastddsjava_create_subscriber(fastddsParticipant, subscriberProfileName);
      fastddsDataReader = fastddsjava_create_datareader(fastddsSubscriber, topicData.fastddsTopic, null, subscriberProfileName);
      fastddsjava_datareader_set_listener(fastddsDataReader, listener);
   }

   private static final int OK = RETCODE_OK();

   private void onDataCallback()
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            while (OK == fastddsjava_datareader_take_next_sample(fastddsDataReader, topicDataWrapper, sampleInfo))
            {
               long receptionTime = System.currentTimeMillis();
               int payloadSizeBytes = (int) topicDataWrapper.data_vector().size();

               synchronized (readBuffer)
               {
                  readBuffer.ensureRemainingCapacity(payloadSizeBytes);
                  // Rewind buffer to ensure we're starting at position = 0
                  readBuffer.rewind();

                  topicDataWrapper.data_ptr().get(readBuffer.getBufferUnsafe().array(), 0, payloadSizeBytes);

                  flagHadData = true;
                  flagNewData = true;
               }

               if (callback != null)
               {
                  callback.onMessage(subscriptionReader);
               }

               long timestampMillis = subscriptionReader.getLastMessageTimestamp();
               recordStatistics(payloadSizeBytes, timestampMillis, receptionTime);
            }
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }
   }

   private void recordStatistics(long messageSizeBytes, long messageTimestampMillis, long receptionTimeMillis)
   {
      synchronized (statisticsCalculators)
      {
         // Record message size
         statisticsCalculators[SIZE.ordinal()].record(messageSizeBytes);

         // Record publish period if available
         if (lastReceiveTime != Long.MIN_VALUE)
         {
            statisticsCalculators[PERIOD.ordinal()].record(receptionTimeMillis - lastReceiveTime);
         }
         lastReceiveTime = receptionTimeMillis;

         // Record publish age
         if (messageTimestampMillis != Long.MIN_VALUE)
         {
            statisticsCalculators[AGE.ordinal()].record(receptionTimeMillis - messageTimestampMillis);
         }
      }
   }

   private void onSubscriptionCallback()
   {
      // TODO:
      //      retcodePrintOnError(fastddsjava_datareader_get_subscription_matched_status(fastddsDataReader, subscriptionMatchedStatus));
   }

   /**
    * Use {@link ROS2Node#destroySubscription(ROS2Subscription)}
    */
   protected void close(Pointer fastddsParticipant)
   {
      closeLock.writeLock().lock();
      boolean wasClosed = closed;
      closed = true;
      closeLock.writeLock().unlock();

      if (!wasClosed)
      {
         retcodePrintOnError(fastddsjava_delete_datareader(fastddsSubscriber, fastddsDataReader));

         listener.close();
         fastddsDataCallback.close();
         fastddsSubscriptionCallback.close();

         topicData.topicDataWrapperType.delete_data(topicDataWrapper);
         sampleInfo.close();
         topicDataWrapper.close();

         retcodePrintOnError(fastddsjava_delete_subscriber(fastddsParticipant, fastddsSubscriber));
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

   /**
    * If this subscription has been closed. A closed subscription will not receive any new data from publishers. Once closed, a subscription cannot be reused.
    * Subscriptions are closed automatically when the parent {@link ROS2Node} is destroyed. To manually close this subscription, use
    * {@link ROS2Node#destroySubscription(ROS2Subscription)}.
    *
    * @return true if closed, false if not closed
    */
   public boolean isClosed()
   {
      return closed;
   }

   /**
    * If this subscription has received data at least one time.
    *
    * @return true if received data at least once, false if never received data
    */
   public boolean hasHadData()
   {
      return flagHadData;
   }

   /**
    * If this subscription has received data that has not been read by the subscription reader.
    *
    * @return true if there exists unread data, false if not
    */
   public boolean hasNewData()
   {
      return flagNewData;
   }

   /**
    * Get the reader responsible for packing the CDR read buffer into {@link ROS2Message} type classes.
    * Allows you to read messages received by this subscription.
    *
    * @return the subscription reader
    */
   public ROS2SubscriptionReader<T> getReader()
   {
      return subscriptionReader;
   }

   /**
    * Get the topic type class for which this subscription can consume.
    *
    * @return the type class held in the {@link ROS2Topic}
    */
   public Class<T> getTopicType()
   {
      return topic.getType();
   }

   /**
    * Get the topic name for which this subscription will subscribe to.
    *
    * @return the topic name held in the {@link ROS2Topic}
    */
   public String getTopicName()
   {
      return topic.getName();
   }
}
