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
import us.ihmc.fastddsjava.pointers.fastddsjavaInfoMapper.fastddsjava_OnDataCallback;
import us.ihmc.fastddsjava.pointers.fastddsjava_DataReaderListener;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;
import us.ihmc.fastddsjava.pointers.rtps_Time_t;

import java.util.concurrent.TimeUnit;
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
   private static final int OK = RETCODE_OK();

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
   private final TopicData topicData;
   private final fastddsjava_TopicDataWrapper topicDataWrapper;
   private final SampleInfo sampleInfo;

   /*
    * Callback and reader
    */
   private final ROS2SubscriptionCallback<T> callback; // The callback may be null
   protected final CDRBuffer readBuffer;
   protected T latest; // Will be null if the message reader has never been used on a sample
   private final ROS2MessageReader<T> messageReader;

   /*
    * Locks
    */
   private final ReadWriteLock closeLock;
   private boolean closed;

   /*
    * Flags
    */
   private boolean flagHadData;

   /*
    * Statistics
    */
   private final StatisticsCalculator[] statisticsCalculators;
   private final int statisticsCalculatorCount;
   private long lastReceiveTime;

   /**
    * Use {@link ROS2Node#createSubscription(ROS2Topic, ROS2SubscriptionCallback, ROS2QoSProfile)}
    */
   ROS2Subscription(Pointer fastddsParticipant,
                    String subscriberProfileName,
                    ROS2SubscriptionCallback<T> callback /* May be null */,
                    ROS2Topic<T> topic,
                    TopicData topicData)
   {
      this.callback = callback;
      this.topic = topic;
      this.topicData = topicData;

      readBuffer = new CDRBuffer();
      messageReader = new ROS2MessageReader<>(this);

      closeLock = new ReentrantReadWriteLock(true);
      closed = false;

      sampleInfo = new SampleInfo();
      topicDataWrapper = new fastddsjava_TopicDataWrapper(topicData.topicDataWrapperType.create_data());
      fastddsDataCallback = new fastddsjava_OnDataCallbackImpl();
      listener = new fastddsjava_DataReaderListener();
      listener.set_on_data_available_callback(fastddsDataCallback);
      fastddsSubscriber = fastddsjava_create_subscriber(fastddsParticipant, subscriberProfileName);
      fastddsDataReader = fastddsjava_create_datareader(fastddsSubscriber, topicData.fastddsTopic, null, subscriberProfileName);
      fastddsjava_datareader_set_listener(fastddsDataReader, listener);

      statisticsCalculatorCount = MessageMetadataType.values.length;
      statisticsCalculators = new StatisticsCalculator[statisticsCalculatorCount];
      for (int i = 0; i < statisticsCalculatorCount; ++i)
      {
         statisticsCalculators[i] = new StatisticsCalculator();
      }
      lastReceiveTime = Long.MIN_VALUE;
   }

   /**
    * Callback which is invoked by Fast-DDS when there is new data available. There may be multiple samples available to read upon invocation.
    * <p>
    * on_data_available(): There is new data available for the application on the DataReader. There is no queuing of invocations to this callback, meaning that
    * if several new data changes are received at once, only one callback invocation may be issued for all of them, instead of one per change. If the
    * application is retrieving the received data on this callback, it must keep reading data until no new changes are left.
    * </p>
    * Refer to documentation here:
    * <a href="https://fast-dds.docs.eprosima.com/en/v3.2.2/fastdds/dds_layer/subscriber/dataReaderListener/dataReaderListener.html">DataReaderListener</a>
    */
   private class fastddsjava_OnDataCallbackImpl extends fastddsjava_OnDataCallback
   {
      @Override
      public void call()
      {
         closeLock.readLock().lock();
         try
         {
            if (callback != null && !closed)
            {
               /*
                * The Java callback will be called for every new sample
                */
               synchronized (readBuffer)
               {
                  while (OK == nextSample(readBuffer))
                  {
                     callback.onMessage(messageReader);
                  }
               }
            }

            flagHadData = true;
         }
         finally
         {
            closeLock.readLock().unlock();
         }
      }
   }

   public boolean hasHadData()
   {
      return flagHadData;
   }

   public boolean read(T data)
   {
      if (callback != null)
      {
         throw new IllegalStateException("Cannot use ROS2Subscription.read() if the subscription was created with a callback.");
      }

      synchronized (readBuffer)
      {
         if (OK == nextSample(readBuffer))
         {
            messageReader.read(data);

            return true;
         }
      }

      return false;
   }

   public T read()
   {
      T data = ROS2Message.createInstance(topic.getType());

      return read(data) ? data : null;
   }

   public int readFully(T data)
   {
      if (callback != null)
      {
         throw new IllegalStateException("Cannot use ROS2Subscription.readFully() if the subscription was created with a callback.");
      }

      int totalRead = 0;

      synchronized (readBuffer)
      {
         while (OK == nextSample(readBuffer))
         {
            totalRead++;
         }

         if (totalRead > 0)
         {
            // Read the last one from the buffer into the data
            messageReader.read(data);
         }
      }

      return totalRead;
   }

   public T readFully()
   {
      T data = ROS2Message.createInstance(topic.getType());

      return (readFully(data) > 0) ? data : null;
   }

   public T getLatest()
   {
      return latest;
   }

   public boolean getLatest(T data)
   {
      if (latest != null)
      {
         data.set(latest);

         return true;
      }

      return false;
   }

   private int nextSample(CDRBuffer readBuffer)
   {
      int retCode = fastddsjava_datareader_take_next_sample(fastddsDataReader, topicDataWrapper, sampleInfo);

      if (OK == retCode)
      {
         // Time when the sample was published
         rtps_Time_t sourceTimestamp = sampleInfo.source_timestamp();
         // Time when the sample was received
         rtps_Time_t receptionTimestamp = sampleInfo.reception_timestamp();
         // The size of the entire payload (including the header) in bytes
         int payloadSizeBytes = (int) topicDataWrapper.data_vector().size();

         readBuffer.ensureRemainingCapacity(payloadSizeBytes);
         readBuffer.rewind();

         topicDataWrapper.data_ptr().get(readBuffer.getBufferUnsafe().array(), 0, payloadSizeBytes);

         recordStatistics(payloadSizeBytes, TimeUnit.NANOSECONDS.toMillis(sourceTimestamp.to_ns()), TimeUnit.NANOSECONDS.toMillis(receptionTimestamp.to_ns()));
      }

      return retCode;
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

         topicData.topicDataWrapperType.delete_data(topicDataWrapper);
         sampleInfo.close();
         topicDataWrapper.close();

         retcodePrintOnError(fastddsjava_delete_subscriber(fastddsParticipant, fastddsSubscriber));
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
