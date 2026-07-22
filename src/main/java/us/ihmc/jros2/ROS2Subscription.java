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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static us.ihmc.fastddsjava.fastddsjavaTools.retcodePrintOnError;
import static us.ihmc.fastddsjava.natives.fastddsjava.RETCODE_OK;
import static us.ihmc.jros2.MessageStatisticsProvider.MessageMetadataType.*;

/**
 * A ROS 2-compatible subscription for ingesting {@link ROS2Message} types.
 */
public class ROS2Subscription<T extends ROS2Message<T>> implements ROS2MessageReader<T>, MessageStatisticsProvider
{
   private static final int OK = RETCODE_OK;

   static
   {
      jros2.load();
   }

   /*
    * Callback
    */
   private final ROS2SubscriptionCallback<T> callback; // The callback may be null
   private final ROS2SubscriptionMatchedCallback subscriptionMatchedInfoCallback;

   /*
    * Debug
    */
   private final ROS2Topic<T> topic;

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
   private int previousMatchedPublicationCount;

   /*
    * Fast-DDS pointers
    */
   private final TopicData topicData;
   private final long fastddsCallbackSampleData;
   private final SampleInfo callbackSampleInfo;
   private final Object callbackSampleLock;
   protected final long fastddsUserSampleData;
   protected final SampleInfo userSampleInfo;
   private final Object userSampleLock;

   /*
    * Read buffer
    */
   protected final CDRBuffer readBuffer;

   /*
    * Flags
    */
   private final AtomicInteger untakenMessageCount;
   private boolean flagHadData;

   /*
    * Statistics
    */
   private final int statisticsCalculatorCount;
   private final StatisticsCalculator[] statisticsCalculators;
   private long lastReceiveTime;

   /*
    * GUID
    */
   private final Guid guid;
   private final Guid subscriptionMatchedGuid;
   private final byte[] subscriptionMatchedGuidBytes;

   /*
    * Callback (matched)
    */
   private volatile Runnable subscriptionMatchedCallback; // User callback for subscription matched events

   /*
    * Fast-DDS pointers (endpoints)
    */
   private final fastddsjavaCallback fastddsDataCallback; // Keep as a field to avoid GC
   private final fastddsjavaCallback fastddsSubscriptionMatchedCallback; // Keep as a field to avoid GC
   private final long fastddsDataReaderListener;
   private final long fastddsSubscriber;
   private final long fastddsDataReader;

   /**
    * Use {@link ROS2Node#createSubscription(ROS2Topic, ROS2SubscriptionCallback, ROS2QoSProfile)}
    */
   ROS2Subscription(long fastddsParticipant,
                    String subscriberProfileName,
                    ROS2SubscriptionCallback<T> callback, // May be null
                    ROS2SubscriptionMatchedCallback subscriptionMatchedInfoCallback,
                    ROS2Topic<T> topic,
                    TopicData topicData)
   {
      this.callback = callback;
      this.subscriptionMatchedInfoCallback = subscriptionMatchedInfoCallback;
      this.topic = topic;

      closeLock = new ReentrantReadWriteLock(true);
      closed = false;

      discoveryLock = new Object();
      publisherDiscovered = false;
      previousMatchedPublicationCount = 0;

      this.topicData = topicData;
      fastddsCallbackSampleData = fastddsjava.createData(topicData.fastddsTopicDataWrapperType);
      callbackSampleInfo = new SampleInfo();
      callbackSampleLock = new Object();
      fastddsUserSampleData = fastddsjava.createData(topicData.fastddsTopicDataWrapperType);
      userSampleInfo = new SampleInfo();
      userSampleLock = new Object();

      readBuffer = new CDRBuffer();

      // Pre-size for an empty message payload so fixed-size reads do not allocate on first use.
      T emptyMessage = ROS2Message.createInstance(topic.getType());
      int initialPayloadSizeBytes = CDRBuffer.PAYLOAD_HEADER.length + emptyMessage.calculateSizeBytes(0);
      readBuffer.ensureRemainingCapacity(initialPayloadSizeBytes);
      fastddsjava.topicDataResize(fastddsCallbackSampleData, initialPayloadSizeBytes);
      fastddsjava.topicDataResize(fastddsUserSampleData, initialPayloadSizeBytes);

      untakenMessageCount = new AtomicInteger(0);
      flagHadData = false;

      statisticsCalculatorCount = MessageMetadataType.values.length;
      statisticsCalculators = new StatisticsCalculator[statisticsCalculatorCount];
      for (int i = 0; i < statisticsCalculatorCount; ++i)
      {
         statisticsCalculators[i] = new StatisticsCalculator();
      }
      lastReceiveTime = Long.MIN_VALUE;

      guid = new Guid();
      subscriptionMatchedGuid = new Guid();
      subscriptionMatchedGuidBytes = new byte[16];

      subscriptionMatchedCallback = null;

      // Initialize callbacks last to ensure the rest of the state of this class exists before they run
      fastddsDataCallback = this::onDataAvailable;
      fastddsSubscriptionMatchedCallback = this::onSubscriptionMatched;
      fastddsDataReaderListener = fastddsjava.createDataReaderListener();
      fastddsjava.dataReaderListenerSetOnDataAvailable(fastddsDataReaderListener, fastddsDataCallback);
      fastddsjava.dataReaderListenerSetOnSubscriptionMatched(fastddsDataReaderListener, fastddsSubscriptionMatchedCallback);
      fastddsSubscriber = fastddsjava.createSubscriber(fastddsParticipant, subscriberProfileName);
      fastddsDataReader = fastddsjava.createDataReader(fastddsSubscriber, topicData.fastddsTopic, 0, subscriberProfileName);
      fastddsjava.dataReaderSetListener(fastddsDataReader, fastddsDataReaderListener);

      // Check if publisher is already matched (outside of discovery lock to avoid nested synchronization)
      if (getSubscriptionMatchedStatus() > 0)
      {
         synchronized (discoveryLock)
         {
            publisherDiscovered = true;
         }

         notifySubscriptionMatchedInfoForExistingMatches();
      }
   }

   /**
    * Invokes {@link ROS2SubscriptionMatchedCallback} for publishers already matched when this subscription is created.
    * <p>
    * Fast-DDS does not retroactively deliver subscription-matched events for endpoints that were matched before the
    * listener was registered, so this is called from the constructor when {@link #getSubscriptionMatchedStatus()} is
    * already greater than zero.
    */
   private void notifySubscriptionMatchedInfoForExistingMatches()
   {
      if (subscriptionMatchedInfoCallback != null && !closed)
      {
         closeLock.readLock().lock();
         try
         {
            if (!closed)
            {
               previousMatchedPublicationCount = fastddsjava.dataReaderGetSubscriptionMatchedStatus(fastddsDataReader, subscriptionMatchedGuidBytes);
               System.arraycopy(subscriptionMatchedGuidBytes, 0, subscriptionMatchedGuid.getValue(), 0, 16);
               subscriptionMatchedInfoCallback.onSubscriptionMatched(this, subscriptionMatchedGuid, true);
            }
         }
         catch (Exception e)
         {
            jros2.logError(e);
         }
         finally
         {
            closeLock.readLock().unlock();
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
   private void onDataAvailable()
   {
      closeLock.readLock().lock();
      try
      {
         synchronized (callbackSampleLock)
         {
            int ret; // Keep for debugging
            while (!closed && OK == (ret = fastddsjava.dataReaderReadNextSample(fastddsDataReader, fastddsCallbackSampleData, callbackSampleInfo.fastddsSampleInfo))
                   && callbackSampleInfo.hasValidData())
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
                  catch (Throwable e)
                  {
                     jros2.logSubscriptionCallbackError(topic.getName(), topic.getType().getSimpleName(), e);
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

   private void onSubscriptionMatched()
   {
      boolean hasMatchChange = false;
      boolean matched = false;
      if (!closed)
      {
         int currentCount = fastddsjava.dataReaderGetSubscriptionMatchedStatus(fastddsDataReader, subscriptionMatchedGuidBytes);
         if (currentCount > previousMatchedPublicationCount)
         {
            hasMatchChange = true;
            matched = true;
         }
         else if (currentCount < previousMatchedPublicationCount)
         {
            hasMatchChange = true;
            matched = false;
         }

         if (hasMatchChange)
         {
            System.arraycopy(subscriptionMatchedGuidBytes, 0, subscriptionMatchedGuid.getValue(), 0, 16);
         }

         previousMatchedPublicationCount = currentCount;
      }

      synchronized (discoveryLock)
      {
         publisherDiscovered = true;
         discoveryLock.notifyAll();
      }

      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            if (subscriptionMatchedCallback != null)
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

            if (hasMatchChange && subscriptionMatchedInfoCallback != null)
            {
               try
               {
                  subscriptionMatchedInfoCallback.onSubscriptionMatched(ROS2Subscription.this, subscriptionMatchedGuid, matched);
               }
               catch (Exception e)
               {
                  jros2.logError(e);
               }
            }
         }
      }
      finally
      {
         closeLock.readLock().unlock();
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

      // If a publisher is already matched, fire the callback immediately
      if (callback != null && getSubscriptionMatchedStatus() > 0)
      {
         try
         {
            callback.run();
         }
         catch (Exception e)
         {
            jros2.logError(e);
         }
      }
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
            count = fastddsjava.dataReaderGetSubscriptionMatchedCount(fastddsDataReader);
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
      boolean discovered;
      long startTime = System.nanoTime();
      long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);

      synchronized (discoveryLock)
      {
         boolean timedOut = false;
         while (!publisherDiscovered && !closed && !timedOut)
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
      return read(data, null);
   }

   @Override
   public boolean read(T data, SampleInfo sampleInfo)
   {
      boolean read = false;

      closeLock.readLock().lock();
      try
      {
         if (!closed && hasHadData())
         {
            synchronized (userSampleLock)
            {
               SampleInfo info = sampleInfo != null ? sampleInfo : userSampleInfo;
               int ret = fastddsjava.dataReaderTakeNextCustom(fastddsDataReader, fastddsUserSampleData, info.fastddsSampleInfo);
               if (OK == ret)
               {
                  untakenMessageCount.decrementAndGet();

                  int payloadSizeBytes = fastddsjava.topicDataSize(fastddsUserSampleData);

                  // Resize Java heap buffer (if necessary) and rewind
                  readBuffer.ensureRemainingCapacity(payloadSizeBytes);
                  readBuffer.rewind();

                  // Copy sample from native memory to Java heap memory
                  fastddsjava.topicDataReadBuffer(fastddsUserSampleData, readBuffer.getBufferUnsafe(), 0, payloadSizeBytes);

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
            synchronized (userSampleLock)
            {
               int ret; // Keep for debugging
               while (OK == (ret = fastddsjava.dataReaderTakeNextCustom(fastddsDataReader, fastddsUserSampleData, userSampleInfo.fastddsSampleInfo)))
               {
                  totalRead++;
               }

               if (totalRead > 0)
               {
                  int payloadSizeBytes = fastddsjava.topicDataSize(fastddsUserSampleData);

                  // Resize Java heap buffer (if necessary) and rewind
                  readBuffer.ensureRemainingCapacity(payloadSizeBytes);
                  readBuffer.rewind();

                  // Copy sample from native memory to Java heap memory
                  fastddsjava.topicDataReadBuffer(fastddsUserSampleData, readBuffer.getBufferUnsafe(), 0, payloadSizeBytes);

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
    * Get the GUID (Globally Unique Identifier) for this subscription.
    * The GUID is assigned by Fast-DDS and uniquely identifies this subscription instance.
    * <p>
    * Returns a cached {@link Guid} instance owned by this subscription; its bytes are refreshed from DDS on each call.
    * Copy with {@link Guid#set(Guid)} if you need an independent snapshot.
    */
   public Guid getGuid()
   {
      fastddsjava.getReaderGuid(fastddsDataReader, guid.getValue());
      return guid;
   }

   /**
    * Use {@link ROS2Node#destroySubscription(ROS2Subscription)}
    */
   protected void close(long fastddsParticipant)
   {
      closeLock.writeLock().lock();
      boolean wasClosed = closed;
      closed = true;
      closeLock.writeLock().unlock();

      if (!wasClosed)
      {
         // Clear listener from datareader to prevent further callbacks
         fastddsjava.dataReaderSetListener(fastddsDataReader, 0);

         // Delete the datareader to ensure Fast-DDS stops using the listener
         retcodePrintOnError(fastddsjava.deleteDataReader(fastddsSubscriber, fastddsDataReader));

         // Note: We intentionally do NOT delete the listener and callback objects here.
         // Due to Fast-DDS's asynchronous nature, callbacks may still be in-flight when we delete the datareader.
         // Freeing them immediately can cause JVM crashes as Fast-DDS tries to invoke callbacks on freed memory.
         // Instead, we rely on Java garbage collection to clean up these objects after Fast-DDS is done with them.
         callbackSampleInfo.close();
         fastddsjava.deleteData(topicData.fastddsTopicDataWrapperType, fastddsCallbackSampleData);
         userSampleInfo.close();
         fastddsjava.deleteData(topicData.fastddsTopicDataWrapperType, fastddsUserSampleData);
         retcodePrintOnError(fastddsjava.deleteSubscriber(fastddsParticipant, fastddsSubscriber));
      }
   }

   private void recordStatistics()
   {
      // Time when the sample was published
      long sourceTimestampMs = TimeUnit.NANOSECONDS.toMillis(callbackSampleInfo.getSourceTimestampNanos());

      // Time when the sample was received
      long receptionTimestampMs = TimeUnit.NANOSECONDS.toMillis(callbackSampleInfo.getReceptionTimestampNanos());

      // The size of the entire payload (including the header) in bytes
      int payloadSizeBytes = fastddsjava.topicDataSize(fastddsCallbackSampleData);

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
