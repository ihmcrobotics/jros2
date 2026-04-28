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
import us.ihmc.fastddsjava.pointers.SubscriptionMatchedStatus;
import us.ihmc.fastddsjava.pointers.fastddsjavaInfoMapper.fastddsjava_OnDataCallback;
import us.ihmc.fastddsjava.pointers.fastddsjava_DataReaderListener;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static us.ihmc.fastddsjava.fastddsjavaTools.retcodePrintOnError;
import static us.ihmc.fastddsjava.pointers.fastddsjava.*;
import static us.ihmc.jros2.MessageStatisticsProvider.MessageMetadataType.*;

/**
 * A ROS 2-compatible subscription for ingesting {@link ROS2Message} types.
 */
public class ROS2Subscription<T extends ROS2Message<T>> implements ROS2MessageReader<T>, MessageStatisticsProvider
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
   private final fastddsjava_TopicDataWrapper callbackSampleData;
   private final Pointer fastddsCallbackSampleInfo;
   protected final fastddsjava_TopicDataWrapper userSampleData;
   protected final Pointer fastddsUserSampleInfo;
   private final SubscriptionMatchedStatus subscriptionMatchedStatus;
   private final fastddsjava_DataReaderListener listener;
   private final fastddsjava_OnDataCallback fastddsDataCallback;
   private final fastddsjava_OnSubscriptionCallback fastddsSubscriptionMatchedCallback;
   private final TopicData topicData;

   /*
    * Callback
    */
   private final ROS2SubscriptionCallback<T> callback; // The callback may be null
   private volatile Runnable subscriptionMatchedCallback; // User callback for subscription matched events

   /*
    * Read buffer
    */
   protected final CDRBuffer readBuffer;

   /*
    * Locks
    */
   private final ReadWriteLock closeLock;
   private boolean closed;

   /*
    * Discovery
    */
   private final Object discoveryLock;
   private volatile boolean publisherDiscovered;

   /*
    * Flags
    */
   private boolean flagHadData;
   private final AtomicInteger untakenMessageCount;

   /*
    * Statistics
    */
   private final StatisticsCalculator[] statisticsCalculators;
   private final int statisticsCalculatorCount;
   private long lastReceiveTime;

   /**
    * Use {@link ROS2Node#createSubscription(ROS2Topic, ROS2SubscriptionCallback, ROS2QoSProfile)}
    */
   ROS2Subscription(Pointer fastddsParticipant, String subscriberProfileName, ROS2SubscriptionCallback<T> callback, // May be null
                    ROS2Topic<T> topic, TopicData topicData)
   {
      this.callback = callback;
      this.topic = topic;
      this.topicData = topicData;

      closeLock = new ReentrantReadWriteLock(true);
      closed = false;

      discoveryLock = new Object();
      publisherDiscovered = false;

      callbackSampleData = new fastddsjava_TopicDataWrapper(topicData.topicDataWrapperType.create_data());
      fastddsCallbackSampleInfo = fastddsjava_create_sampleinfo();
      userSampleData = new fastddsjava_TopicDataWrapper(topicData.topicDataWrapperType.create_data());
      fastddsUserSampleInfo = fastddsjava_create_sampleinfo();
      subscriptionMatchedStatus = new SubscriptionMatchedStatus();

      readBuffer = new CDRBuffer();

      untakenMessageCount = new AtomicInteger(0);

      statisticsCalculatorCount = MessageMetadataType.values.length;
      statisticsCalculators = new StatisticsCalculator[statisticsCalculatorCount];
      for (int i = 0; i < statisticsCalculatorCount; ++i)
      {
         statisticsCalculators[i] = new StatisticsCalculator();
      }
      lastReceiveTime = Long.MIN_VALUE;

      // Initialize callbacks last to ensure the rest of the state of this class exists before they run
      fastddsDataCallback = new fastddsjava_OnDataCallbackImpl();
      fastddsSubscriptionMatchedCallback = new fastddsjava_OnSubscriptionMatchedCallbackImpl();
      listener = new fastddsjava_DataReaderListener();
      listener.set_on_data_available_callback(fastddsDataCallback);
      listener.set_on_subscription_callback(fastddsSubscriptionMatchedCallback);
      fastddsSubscriber = fastddsjava_create_subscriber(fastddsParticipant, subscriberProfileName);
      fastddsDataReader = fastddsjava_create_datareader(fastddsSubscriber, topicData.fastddsTopic, null, subscriberProfileName);
      fastddsjava_datareader_set_listener(fastddsDataReader, listener);

      // Check if publisher is already matched (outside of discovery lock to avoid nested synchronization)
      if (getSubscriptionMatchedStatus() > 0)
      {
         synchronized (discoveryLock)
         {
            publisherDiscovered = true;
         }
      }
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
            synchronized (callbackSampleData)
            {
               int ret; // Keep for debugging
               while (!closed && OK == (ret = fastddsjava_datareader_read_next_sample(fastddsDataReader, callbackSampleData, fastddsCallbackSampleInfo))
                      && fastddsjava_sampleinfo_valid_data(fastddsCallbackSampleInfo))
               {
                  flagHadData = true;
                  untakenMessageCount.incrementAndGet();

                  recordStatistics();

                  if (callback != null)
                  {
                     try
                     {
                        callback.onMessage(ROS2Subscription.this);
                     }
                     catch (Exception e)
                     {
                        jros2.logError(e);
                     }
                  }
               }
            }
         }
         finally
         {
            closeLock.readLock().unlock();
         }
      }
   }

   private class fastddsjava_OnSubscriptionMatchedCallbackImpl extends fastddsjava_OnSubscriptionCallback
   {
      @Override
      public void call()
      {
         synchronized (discoveryLock)
         {
            publisherDiscovered = true;
            discoveryLock.notifyAll();
         }

         closeLock.readLock().lock();
         try
         {
            if (!closed && subscriptionMatchedCallback != null)
            {
               try
               {
                  subscriptionMatchedCallback.run();
               }
               catch (Exception e)
               {
                  jros2.logError(e);
               }
            }
         }
         finally
         {
            closeLock.readLock().unlock();
         }
      }
   }

   /**
    * Set a callback to be invoked when a publisher is matched to this subscription.
    * This is useful for detecting when service servers become available.
    *
    * @param callback The callback to invoke when a publisher matches, or null to clear
    */
   void setOnSubscriptionMatchedCallback(Runnable callback)
   {
      this.subscriptionMatchedCallback = callback;
   }

   /**
    * Get the current number of matched publishers for this subscription.
    *
    * @return The number of publishers currently matched to this subscription
    */
   int getSubscriptionMatchedStatus()
   {
      int count;

      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            synchronized (subscriptionMatchedStatus)
            {
               fastddsjava_datareader_get_subscription_matched_status(fastddsDataReader, subscriptionMatchedStatus);
               count = subscriptionMatchedStatus.current_count();
            }
         }
         else
         {
            count = 0;
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return count;
   }

   /**
    * Wait for a publisher to be discovered.
    * <p>
    * This method blocks until a publisher is discovered or the timeout expires.
    *
    * @param timeoutMs Timeout in milliseconds to wait for publisher discovery
    * @return true if a publisher was discovered, false if timeout occurred
    */
   public boolean waitForPublisher(long timeoutMs)
   {
      boolean discovered = false;
      long startTime = System.nanoTime();
      long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);

      synchronized (discoveryLock)
      {
         boolean timedOut = false;
         while (!publisherDiscovered && !closed && !timedOut)
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

         discovered = publisherDiscovered;
      }

      return discovered;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean hasHadData()
   {
      return flagHadData;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean read(T data)
   {
      boolean read = false;

      closeLock.readLock().lock();
      try
      {
         if (!closed && hasHadData())
         {
            synchronized (userSampleData)
            {
               int ret = fastddsjava_datareader_take_next_custom(fastddsDataReader, userSampleData, fastddsUserSampleInfo);
               if (OK == ret)
               {
                  untakenMessageCount.decrementAndGet();

                  long payloadSizeBytes = userSampleData.data_vector().size();

                  // Resize Java heap buffer (if necessary) and rewind
                  readBuffer.ensureRemainingCapacity((int) payloadSizeBytes);
                  readBuffer.rewind();

                  // Copy sample from native memory to Java heap memory
                  userSampleData.data_ptr().get(readBuffer.getBufferUnsafe().array(), 0, (int) payloadSizeBytes);

                  // Deserialize sample into Java ROS2Message
                  readBuffer.readPayloadHeader();
                  data.deserialize(readBuffer);

                  read = true;
               }
            }
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return read;
   }

   /**
    * {@inheritDoc}
    * This method allocates a new message instance on every call and is not garbage-free.
    * Use {@link #read(ROS2Message)} for a garbage-free version.
    */
   @Override
   public T read()
   {
      T data = ROS2Message.createInstance(topic.getType());

      return read(data) ? data : null;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int readLatest(T data)
   {
      int totalRead = 0;

      closeLock.readLock().lock();
      try
      {
         if (!closed && hasHadData())
         {
            synchronized (userSampleData)
            {
               int ret; // Keep for debugging
               while (OK == (ret = fastddsjava_datareader_take_next_custom(fastddsDataReader, userSampleData, fastddsUserSampleInfo)))
               {
                  totalRead++;
               }

               if (totalRead > 0)
               {
                  long payloadSizeBytes = userSampleData.data_vector().size();

                  // Resize Java heap buffer (if necessary) and rewind
                  readBuffer.ensureRemainingCapacity((int) payloadSizeBytes);
                  readBuffer.rewind();

                  // Copy sample from native memory to Java heap memory
                  userSampleData.data_ptr().get(readBuffer.getBufferUnsafe().array(), 0, (int) payloadSizeBytes);

                  // Deserialize sample into Java ROS2Message
                  readBuffer.readPayloadHeader();
                  data.deserialize(readBuffer);
               }
            }
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return totalRead;
   }

   /**
    * {@inheritDoc}
    * This method allocates a new message instance on every call and is not garbage-free.
    * Use {@link #read(ROS2Message)} for a garbage-free version.
    */
   @Override
   public T readLatest()
   {
      T data = ROS2Message.createInstance(topic.getType());

      return (readLatest(data) > 0) ? data : null;
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
         // Clear listener from datareader to prevent further callbacks
         fastddsjava_datareader_set_listener(fastddsDataReader, null);

         // Delete the datareader to ensure Fast-DDS stops using the listener
         retcodePrintOnError(fastddsjava_delete_datareader(fastddsSubscriber, fastddsDataReader));

         // Note: We intentionally do NOT close the listener and callback objects here.
         // Due to Fast-DDS's asynchronous nature, callbacks may still be in-flight when we delete the datareader.
         // Calling close() immediately can cause JVM crashes as Fast-DDS tries to invoke callbacks on freed memory.
         // Instead, we rely on Java garbage collection to clean up these objects after Fast-DDS is done with them.
         // listener.close();
         // fastddsDataCallback.close();
         // fastddsSubscriptionMatchedCallback.close();

         subscriptionMatchedStatus.close();

         topicData.topicDataWrapperType.delete_data(callbackSampleData);
         fastddsjava_delete_sampleinfo(fastddsCallbackSampleInfo);
         callbackSampleData.close();
         topicData.topicDataWrapperType.delete_data(userSampleData);
         fastddsjava_delete_sampleinfo(fastddsUserSampleInfo);
         userSampleData.close();

         retcodePrintOnError(fastddsjava_delete_subscriber(fastddsParticipant, fastddsSubscriber));
      }
   }

   private void recordStatistics()
   {
      // Time when the sample was published
      long sourceTimestampMs = TimeUnit.NANOSECONDS.toMillis(fastddsjava_sampleinfo_source_timestamp_to_ns(fastddsCallbackSampleInfo));

      // Time when the sample was received
      long receptionTimestampMs = TimeUnit.NANOSECONDS.toMillis(fastddsjava_sampleinfo_reception_timestamp_to_ns(fastddsCallbackSampleInfo));

      // The size of the entire payload (including the header) in bytes
      int payloadSizeBytes = (int) callbackSampleData.data_vector().size();

      synchronized (statisticsCalculators)
      {
         // Record message size
         statisticsCalculators[SIZE.ordinal()].record(payloadSizeBytes);

         // Record publish period if available
         if (lastReceiveTime != Long.MIN_VALUE)
         {
            statisticsCalculators[PERIOD.ordinal()].record(receptionTimestampMs - lastReceiveTime);
         }
         lastReceiveTime = receptionTimestampMs;

         // Record publish age
         if (sourceTimestampMs != Long.MIN_VALUE)
         {
            statisticsCalculators[AGE.ordinal()].record(receptionTimestampMs - sourceTimestampMs);
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

   public int getUnreadMessageCount()
   {
      return untakenMessageCount.get();
   }
}