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

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import static us.ihmc.fastddsjava.natives.fastddsjava.*;
import static us.ihmc.jros2.MessageStatisticsProvider.MessageMetadataType.*;

/**
 * A ROS 2-compatible subscription for ingesting {@link ROS2Message} types.
 * <p>
 * <b>Callback mode</b> (created with a {@link ROS2SubscriptionCallback}): every received sample is taken from Fast-DDS
 * once and the callback is invoked once per sample with this subscription as the {@link ROS2MessageReader}. Inside the
 * callback, {@link #read(ROS2Message)} deserializes the sample that triggered the callback; calling it again reads the
 * next sample that is already available (which then gets no callback of its own), so {@code while (reader.read(msg))}
 * drains everything. A sample the callback does not read is consumed nonetheless, as in rclcpp. By default the callback
 * runs on the Fast-DDS thread that received the data; with a callback executor (see
 * {@link ROS2NodeOptions#callbackExecutor(Executor)}) the Fast-DDS thread only schedules a drain task and the callbacks run
 * on the executor, one at a time per subscription; a drain task runs at most {@value #EXECUTOR_DRAIN_BATCH} callbacks and
 * then schedules a new task, so that busy subscriptions take turns on a shared executor.
 * <p>
 * <b>Polling mode</b> (no callback): {@link #read(ROS2Message)} and {@link #readLatest(ROS2Message)} take samples from the
 * Fast-DDS history (bounded by the QoS history depth); {@link #getUnreadMessageCount()} reports how many are waiting.
 * <p>
 * Samples without data (Fast-DDS notifies them when the last matched publisher goes away) are never returned or counted.
 * Malformed samples that fail to deserialize are logged (rate-limited), counted by {@link #getMalformedSampleCount()} and
 * dropped.
 */
public class ROS2Subscription<T extends ROS2Message<T>> implements ROS2MessageReader<T>, MessageStatisticsProvider
{
   static
   {
      jros2.load();
   }

   /** Maximum number of callbacks one drain task runs on a callback executor before it hands the thread back. */
   static final int EXECUTOR_DRAIN_BATCH = 64;

   /*
    * Callback
    */
   private final ROS2SubscriptionCallback<T> callback; // The callback may be null
   private final ROS2SubscriptionMatchedCallback subscriptionMatchedInfoCallback;
   private final Executor callbackExecutor; // null: callbacks run inline on the Fast-DDS thread

   /*
    * Debug
    */
   private final ROS2Topic<T> topic;

   /*
    * Lifecycle
    */
   private volatile boolean closed;
   private final EndpointSupport.Gate gate;
   private final Object teardownLock;
   private boolean nativesReleased;
   private final AtomicBoolean teardownScheduled;

   /*
    * Discovery
    */
   private final Object discoveryLock;
   private volatile boolean publisherDiscovered;
   private final Object matchLock;
   private int previousMatchedPublicationCount;
   private final byte[] subscriptionMatchedGuidBytes;
   private volatile Runnable subscriptionMatchedCallback; // Internal callback for subscription matched events

   /*
    * Fast-DDS pointers and buffers of the polling path (read()/readLatest() outside of a callback), guarded by pollLock
    * (allocation-free when several threads poll at once, unlike a contended monitor)
    */
   private final TopicData topicData;
   private final boolean useTakeToBufferAbi;
   private final AllocationFreeLock pollLock;
   protected final long fastddsUserSampleData;
   protected final SampleInfo userSampleInfo;
   protected final CDRBuffer readBuffer;

   /*
    * Callback dispatch: the thread holding dispatchLock owns the callback sample objects below
    */
   private final ReentrantLock dispatchLock;
   private volatile boolean dispatchPending;
   private final AtomicBoolean drainScheduled;
   private final Runnable drainTask;
   private final long fastddsCallbackSampleData;
   private final SampleInfo callbackSampleInfo;
   private final CDRBuffer callbackBuffer;
   private boolean currentSampleUnread;
   private int currentSampleSizeBytes;

   /*
    * Flags
    */
   private volatile boolean flagHadData;

   /*
    * Statistics (opt-in)
    */
   private volatile boolean statisticsEnabled;
   private final int statisticsCalculatorCount;
   private final StatisticsCalculator[] statisticsCalculators;
   private final AllocationFreeLock statisticsLock;
   private long lastReceiveTime;

   /*
    * Errors
    */
   private final AtomicLong malformedSampleCount;
   private final EndpointSupport.RateLimiter malformedSampleLog;
   private final EndpointSupport.RateLimiter takeErrorLog;
   private final EndpointSupport.RateLimiter executorErrorLog;

   /*
    * GUID
    */
   private final byte[] guidBytes;
   private final Guid guid;

   /*
    * Fast-DDS pointers (endpoints)
    */
   private final EndpointSupport.Trampoline dataAvailableTrampoline;
   private final EndpointSupport.Trampoline subscriptionMatchedTrampoline;
   private volatile boolean listenerReady;
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
      this(fastddsParticipant, subscriberProfileName, callback, subscriptionMatchedInfoCallback, topic, topicData, null);
   }

   /**
    * Use {@link ROS2Node#createSubscription(ROS2Topic, ROS2SubscriptionCallback, ROS2QoSProfile)}
    *
    * @param callbackExecutor executor that runs callbacks, or null to run them on the Fast-DDS thread
    * @throws IllegalStateException if Fast-DDS refuses to create the subscriber or data reader (for example an invalid
    *                               QoS profile, or a topic name already used with a different type); nothing is leaked
    */
   ROS2Subscription(long fastddsParticipant,
                    String subscriberProfileName,
                    ROS2SubscriptionCallback<T> callback, // May be null
                    ROS2SubscriptionMatchedCallback subscriptionMatchedInfoCallback,
                    ROS2Topic<T> topic,
                    TopicData topicData,
                    Executor callbackExecutor)
   {
      this.callback = callback;
      this.subscriptionMatchedInfoCallback = subscriptionMatchedInfoCallback;
      this.callbackExecutor = callback == null ? null : callbackExecutor;
      this.topic = topic;
      this.topicData = topicData;

      closed = false;
      gate = new EndpointSupport.Gate();
      teardownLock = new Object();
      teardownScheduled = new AtomicBoolean(false);

      discoveryLock = new Object();
      publisherDiscovered = false;
      matchLock = new Object();
      previousMatchedPublicationCount = 0;
      subscriptionMatchedGuidBytes = new byte[16];
      subscriptionMatchedCallback = null;

      if (topicData == null || topicData.fastddsTopic == 0)
      {
         throw new IllegalStateException(HotPathText.subscriptionTopicMissing(topic.getName()));
      }

      useTakeToBufferAbi = EndpointSupport.useNativeAbiV2();
      pollLock = new AllocationFreeLock();
      dispatchLock = new ReentrantLock();
      dispatchPending = false;
      drainScheduled = new AtomicBoolean(false);
      drainTask = this::runDrainTask;
      currentSampleUnread = false;
      flagHadData = false;

      // Pre-size for an empty message payload so fixed-size reads do not allocate on first use.
      int initialPayloadSizeBytes = ROS2Publisher.initialPayloadSizeBytes(topic.getType());
      readBuffer = new CDRBuffer();
      readBuffer.ensureRemainingCapacity(initialPayloadSizeBytes);
      if (callback != null)
      {
         callbackBuffer = new CDRBuffer();
         callbackBuffer.ensureRemainingCapacity(initialPayloadSizeBytes);
      }
      else
      {
         callbackBuffer = null;
      }

      statisticsEnabled = false;
      statisticsCalculatorCount = MessageMetadataType.values.length;
      statisticsCalculators = new StatisticsCalculator[statisticsCalculatorCount];
      statisticsLock = new AllocationFreeLock();
      for (int i = 0; i < statisticsCalculatorCount; ++i)
      {
         statisticsCalculators[i] = new StatisticsCalculator();
      }
      lastReceiveTime = Long.MIN_VALUE;

      malformedSampleCount = new AtomicLong();
      malformedSampleLog = new EndpointSupport.RateLimiter(5, TimeUnit.SECONDS);
      takeErrorLog = new EndpointSupport.RateLimiter(5, TimeUnit.SECONDS);
      executorErrorLog = new EndpointSupport.RateLimiter(5, TimeUnit.SECONDS);

      guidBytes = new byte[16];
      guid = new Guid();

      // The native listener only references these trampolines, never this subscription directly.
      dataAvailableTrampoline = new EndpointSupport.Trampoline(this::onDataAvailable);
      subscriptionMatchedTrampoline = new EndpointSupport.Trampoline(this::onSubscriptionMatched);

      long userData = 0;
      SampleInfo userInfo = null;
      long callbackData = 0;
      SampleInfo callbackInfo = null;
      long listener = 0;
      long subscriber = 0;
      long reader;
      try
      {
         userData = createTopicData(topicData, topic);
         userInfo = new SampleInfo();
         if (callback != null)
         {
            callbackData = createTopicData(topicData, topic);
            callbackInfo = new SampleInfo();
         }

         listener = fastddsjava.createDataReaderListener();
         if (listener == 0)
         {
            throw new IllegalStateException(HotPathText.dataReaderListenerCreationFailed(topic.getName()));
         }
         fastddsjava.dataReaderListenerSetOnDataAvailable(listener, dataAvailableTrampoline);
         fastddsjava.dataReaderListenerSetOnSubscriptionMatched(listener, subscriptionMatchedTrampoline);

         subscriber = fastddsjava.createSubscriber(fastddsParticipant, subscriberProfileName);
         if (subscriber == 0)
         {
            throw new IllegalStateException(HotPathText.subscriberCreationFailed(topic.getName(), subscriberProfileName));
         }

         reader = fastddsjava.createDataReader(subscriber, topicData.fastddsTopic, 0, subscriberProfileName);
         if (reader == 0)
         {
            throw new IllegalStateException(HotPathText.dataReaderCreationFailed(topic.getName(), topic.getType().getName(), subscriberProfileName));
         }
      }
      catch (RuntimeException | Error e)
      {
         dataAvailableTrampoline.clear();
         subscriptionMatchedTrampoline.clear();
         if (subscriber != 0)
         {
            int ret = fastddsjava.deleteSubscriber(fastddsParticipant, subscriber);
            if (ret != RETCODE_OK)
            {
               jros2.getLogger().warning(HotPathText.partialSubscriberDeletionFailed(ret));
            }
         }
         if (listener != 0)
         {
            // Never attached to an entity, so no callback can be running.
            fastddsjava.deleteDataReaderListener(listener);
         }
         if (callbackInfo != null)
         {
            callbackInfo.close();
         }
         if (callbackData != 0)
         {
            fastddsjava.deleteData(topicData.fastddsTopicDataWrapperType, callbackData);
         }
         if (userInfo != null)
         {
            userInfo.close();
         }
         if (userData != 0)
         {
            fastddsjava.deleteData(topicData.fastddsTopicDataWrapperType, userData);
         }
         gate.close();
         closed = true;
         throw e;
      }

      fastddsUserSampleData = userData;
      userSampleInfo = userInfo;
      fastddsCallbackSampleData = callbackData;
      callbackSampleInfo = callbackInfo;
      fastddsDataReaderListener = listener;
      fastddsSubscriber = subscriber;
      fastddsDataReader = reader;
      fastddsjava.getReaderGuid(fastddsDataReader, guidBytes);

      // Publish all fields to the callback threads before the listener can run.
      listenerReady = true;
      int setListenerRet = fastddsjava.dataReaderSetListener(fastddsDataReader, fastddsDataReaderListener);
      if (setListenerRet != RETCODE_OK)
      {
         jros2.getLogger().warning(HotPathText.subscriptionListenerAttachFailed(topic.getName(), setListenerRet));
      }

      // Fast-DDS does not notify matches or samples (e.g. TRANSIENT_LOCAL history) that arrived before the listener was set.
      if (getSubscriptionMatchedStatus() > 0)
      {
         synchronized (discoveryLock)
         {
            publisherDiscovered = true;
            discoveryLock.notifyAll();
         }

         notifySubscriptionMatchedInfoForExistingMatches();
      }

      if (getUnreadMessageCount() > 0)
      {
         onDataAvailable();
      }
   }

   private static long createTopicData(TopicData topicData, ROS2Topic<?> topic)
   {
      long data = fastddsjava.createData(topicData.fastddsTopicDataWrapperType);
      if (data == 0)
      {
         throw new IllegalStateException(HotPathText.sampleBufferAllocationFailed(topic.getName()));
      }
      return data;
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
      if (subscriptionMatchedInfoCallback != null)
      {
         onSubscriptionMatched();
      }
   }

   /**
    * Called by Fast-DDS (through {@link #dataAvailableTrampoline}) when new data is available. There may be several
    * samples available; Fast-DDS does not queue invocations, so every invocation drains all available samples.
    * <p>
    * Never blocks: with intraprocess delivery this runs inside another thread's {@code DataWriter::write()}, and with
    * network delivery it runs on a Fast-DDS receive thread that holds the reader's internal lock.
    * Refer to <a href="https://fast-dds.docs.eprosima.com/en/v3.6.2/fastdds/dds_layer/subscriber/dataReaderListener/dataReaderListener.html">DataReaderListener</a>
    */
   private void onDataAvailable()
   {
      if (!listenerReady || closed)
      {
         return;
      }

      if (callback == null)
      {
         // Polling mode: no data work here, read()/readLatest() take the samples.
         flagHadData = true;
      }
      else if (callbackExecutor == null)
      {
         dispatchAvailable(Integer.MAX_VALUE);
      }
      else
      {
         scheduleDrain();
      }
   }

   /**
    * Schedules one coalesced drain task on the callback executor. Allocation-free on our side.
    */
   private void scheduleDrain()
   {
      if (!drainScheduled.get() && drainScheduled.compareAndSet(false, true))
      {
         try
         {
            callbackExecutor.execute(drainTask);
         }
         catch (RuntimeException e)
         {
            drainScheduled.set(false);
            EndpointSupport.logRateLimited(executorErrorLog,
                                           Level.SEVERE,
                                           HotPathText.callbackExecutorRejected(topic.getName()),
                                           e);
         }
      }
   }

   private void runDrainTask()
   {
      // Cleared before draining so that data arriving during the drain schedules another drain.
      drainScheduled.set(false);
      if (!closed && dispatchAvailable(EXECUTOR_DRAIN_BATCH))
      {
         // More samples may be waiting: continue in a new task so that a busy subscription takes turns with the other
         // work of the executor instead of monopolizing one of its threads.
         scheduleDrain();
      }
   }

   /**
    * Takes the available samples and runs the callback for each. At most one thread dispatches at a time; a thread that
    * finds another one dispatching leaves a flag for it instead of waiting, so Fast-DDS threads never block here.
    *
    * @param maxSamples the maximum number of callbacks to run before returning
    * @return true if {@code maxSamples} callbacks ran and more samples may be available
    */
   private boolean dispatchAvailable(int maxSamples)
   {
      dispatchPending = true;
      if (dispatchLock.isHeldByCurrentThread())
      {
         // Re-entrant notification (e.g. the callback published on this topic with intraprocess delivery): the running
         // drain loop picks the sample up after the callback returns.
         return false;
      }

      while (dispatchPending && !closed && dispatchLock.tryLock())
      {
         try
         {
            dispatchPending = false;
            if (drainCallbacks(maxSamples))
            {
               return true;
            }
         }
         finally
         {
            dispatchLock.unlock();
         }
      }
      return false;
   }

   /**
    * @return true if {@code maxSamples} callbacks ran (more samples may be available)
    */
   private boolean drainCallbacks(int maxSamples)
   {
      CallbackContext.enter();
      try
      {
         int dispatched = 0;
         while (!closed)
         {
            if (dispatched == maxSamples)
            {
               return true;
            }

            int sizeBytes = takeNextValidSample(fastddsCallbackSampleData, callbackSampleInfo.fastddsSampleInfo, callbackBuffer);
            if (sizeBytes < 0)
            {
               break;
            }
            dispatched++;

            currentSampleSizeBytes = sizeBytes;
            currentSampleUnread = true;
            try
            {
               callback.onMessage(this);
            }
            catch (Throwable e)
            {
               jros2.logSubscriptionCallbackError(topic.getName(), topic.getType().getSimpleName(), e);
            }
            finally
            {
               currentSampleUnread = false;
            }
         }
         return false;
      }
      finally
      {
         CallbackContext.exit();
      }
   }

   /**
    * Takes the next sample that carries data into {@code buffer}, skipping samples without data.
    *
    * @param fastddsTopicData  native topic data wrapper (ABI v1 destination, ABI v2 fallback for large payloads)
    * @param fastddsSampleInfo native sample info to fill
    * @return the payload size in bytes (CDR header included), or -1 if no sample is available or the subscription is closed
    */
   private int takeNextValidSample(long fastddsTopicData, long fastddsSampleInfo, CDRBuffer buffer)
   {
      if (!gate.enter())
      {
         return -1;
      }

      try
      {
         for (;;)
         {
            ByteBuffer destination = buffer.getBufferUnsafe();
            int sizeBytes;

            if (useTakeToBufferAbi)
            {
               sizeBytes = fastddsjava.dataReaderTakeNextToBuffer(fastddsDataReader, fastddsTopicData, fastddsSampleInfo, destination, destination.capacity());
               if (sizeBytes == TAKE_NO_DATA)
               {
                  return -1;
               }
               if (sizeBytes == TAKE_INVALID_SAMPLE)
               {
                  continue;
               }
               if (sizeBytes < 0)
               {
                  int retcode = sizeBytes <= -TAKE_ERROR_BASE ? -sizeBytes - TAKE_ERROR_BASE : sizeBytes;
                  EndpointSupport.logRetcode(takeErrorLog, EndpointSupport.TAKE, topic.getName(), retcode);
                  return -1;
               }
               if (sizeBytes > destination.capacity())
               {
                  // The payload did not fit and was kept in the native wrapper.
                  destination = grow(buffer, sizeBytes);
                  fastddsjava.topicDataReadBuffer(fastddsTopicData, destination, 0, sizeBytes);
               }
            }
            else
            {
               int ret = fastddsjava.dataReaderTakeNextCustom(fastddsDataReader, fastddsTopicData, fastddsSampleInfo);
               if (ret != RETCODE_OK)
               {
                  if (ret != RETCODE_NO_DATA)
                  {
                     EndpointSupport.logRetcode(takeErrorLog, EndpointSupport.TAKE, topic.getName(), ret);
                  }
                  return -1;
               }
               if (!fastddsjava.sampleInfoValidData(fastddsSampleInfo))
               {
                  // Instance state notification (e.g. the last publisher went away); the wrapper still holds the previous payload.
                  continue;
               }
               sizeBytes = fastddsjava.topicDataSize(fastddsTopicData);
               if (sizeBytes > destination.capacity())
               {
                  destination = grow(buffer, sizeBytes);
               }
               fastddsjava.topicDataReadBuffer(fastddsTopicData, destination, 0, sizeBytes);
            }

            flagHadData = true;
            if (statisticsEnabled)
            {
               recordStatistics(sizeBytes, fastddsSampleInfo);
            }
            return sizeBytes;
         }
      }
      finally
      {
         gate.exit();
      }
   }

   private static ByteBuffer grow(CDRBuffer buffer, int sizeBytes)
   {
      // Position 0: nothing is copied into the new buffer.
      buffer.rewind();
      buffer.ensureRemainingCapacity(sizeBytes);
      return buffer.getBufferUnsafe();
   }

   /**
    * Deserializes a payload of {@code sizeBytes} bytes held in {@code buffer} into {@code data}. Reads are bounded by the
    * payload size, so a truncated or malformed payload fails instead of reading stale bytes.
    *
    * @return false if the payload is malformed (it is then logged and counted)
    */
   private boolean deserialize(T data, CDRBuffer buffer, int sizeBytes)
   {
      try
      {
         buffer.beginRead(sizeBytes);
         buffer.readPayloadHeader();
         data.deserialize(buffer);
         return true;
      }
      catch (RuntimeException e)
      {
         malformedSampleCount.incrementAndGet();
         EndpointSupport.logRateLimited(malformedSampleLog,
                                        Level.WARNING,
                                        HotPathText.malformedSample(topic.getType().getSimpleName(), sizeBytes, topic.getName()),
                                        e);
         return false;
      }
      finally
      {
         buffer.rewind();
      }
   }

   private boolean isDispatchThread()
   {
      return callback != null && dispatchLock.isHeldByCurrentThread();
   }

   private static void checkSampleInfo(SampleInfo sampleInfo)
   {
      if (sampleInfo != null && sampleInfo.isClosed())
      {
         throw new IllegalArgumentException(HotPathText.sampleInfoClosed());
      }
   }

   /**
    * Read inside this subscription's callback: the sample that triggered the callback first, then samples that are
    * already available.
    */
   private boolean readInCallback(T data, SampleInfo sampleInfo)
   {
      if (currentSampleUnread)
      {
         currentSampleUnread = false;
         if (sampleInfo != null && !copyCallbackSampleInfo(sampleInfo))
         {
            return false;
         }
         return deserialize(data, callbackBuffer, currentSampleSizeBytes);
      }

      for (;;)
      {
         long fastddsSampleInfo = sampleInfo != null ? sampleInfo.nativeHandleForFill() : callbackSampleInfo.fastddsSampleInfo;
         int sizeBytes = takeNextValidSample(fastddsCallbackSampleData, fastddsSampleInfo, callbackBuffer);
         if (sizeBytes < 0)
         {
            return false;
         }
         if (deserialize(data, callbackBuffer, sizeBytes))
         {
            return true;
         }
      }
   }

   private boolean copyCallbackSampleInfo(SampleInfo destination)
   {
      if (!gate.enter())
      {
         return false;
      }
      try
      {
         destination.copyFrom(callbackSampleInfo);
         return true;
      }
      finally
      {
         gate.exit();
      }
   }

   private int readLatestInCallback(T data)
   {
      int count = 0;
      int lastSizeBytes = -1;
      if (currentSampleUnread)
      {
         currentSampleUnread = false;
         count = 1;
         lastSizeBytes = currentSampleSizeBytes;
      }

      int sizeBytes;
      while ((sizeBytes = takeNextValidSample(fastddsCallbackSampleData, callbackSampleInfo.fastddsSampleInfo, callbackBuffer)) >= 0)
      {
         count++;
         lastSizeBytes = sizeBytes;
      }

      if (count == 0)
      {
         return 0;
      }
      return deserialize(data, callbackBuffer, lastSizeBytes) ? count : 0;
   }

   /**
    * Called by Fast-DDS (through {@link #subscriptionMatchedTrampoline}) when a publisher is matched or unmatched, and
    * once from the constructor for publishers matched before the listener was set.
    */
   private void onSubscriptionMatched()
   {
      if (!listenerReady || closed)
      {
         return;
      }

      CallbackContext.enter();
      try
      {
         boolean hasMatchChange = false;
         boolean matched = false;
         Guid publicationGuid = null;

         synchronized (matchLock)
         {
            if (!gate.enter())
            {
               return;
            }
            try
            {
               int currentCount = fastddsjava.dataReaderGetSubscriptionMatchedStatus(fastddsDataReader, subscriptionMatchedGuidBytes);
               if (currentCount != previousMatchedPublicationCount)
               {
                  hasMatchChange = true;
                  matched = currentCount > previousMatchedPublicationCount;
                  // Matched events are rare: give every invocation its own GUID instance.
                  publicationGuid = new Guid(subscriptionMatchedGuidBytes);
               }
               previousMatchedPublicationCount = currentCount;
            }
            finally
            {
               gate.exit();
            }
         }

         synchronized (discoveryLock)
         {
            publisherDiscovered = true;
            discoveryLock.notifyAll();
         }

         Runnable matchedCallback = subscriptionMatchedCallback;
         if (matchedCallback != null && !closed)
         {
            try
            {
               matchedCallback.run();
            }
            catch (Exception e)
            {
               jros2.logError(e);
            }
         }

         if (hasMatchChange && subscriptionMatchedInfoCallback != null && !closed)
         {
            try
            {
               subscriptionMatchedInfoCallback.onSubscriptionMatched(this, publicationGuid, matched);
            }
            catch (Exception e)
            {
               jros2.logError(e);
            }
         }
      }
      finally
      {
         CallbackContext.exit();
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
    * @return The number of publishers currently matched to this subscription, 0 once closed
    */
   int getSubscriptionMatchedStatus()
   {
      int count = 0;

      if (gate.enter())
      {
         try
         {
            count = fastddsjava.dataReaderGetSubscriptionMatchedCount(fastddsDataReader);
         }
         finally
         {
            gate.exit();
         }
      }

      return count;
   }

   /**
    * Wait for a publisher to be discovered.
    * <p>
    * This method blocks until a publisher is discovered, the subscription is closed, or the timeout expires.
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
    * <p>
    * Inside this subscription's callback the sample that triggered the callback is read first. Allocation-free after
    * warm-up for messages whose size does not grow.
    */
   @Override
   public boolean read(T data)
   {
      return read(data, null);
   }

   /**
    * {@inheritDoc}
    *
    * @throws IllegalArgumentException if {@code sampleInfo} has been closed
    */
   @Override
   public boolean read(T data, SampleInfo sampleInfo)
   {
      checkSampleInfo(sampleInfo);

      if (isDispatchThread())
      {
         return readInCallback(data, sampleInfo);
      }

      pollLock.lock();
      try
      {
         for (;;)
         {
            long fastddsSampleInfo = sampleInfo != null ? sampleInfo.nativeHandleForFill() : userSampleInfo.fastddsSampleInfo;
            int sizeBytes = takeNextValidSample(fastddsUserSampleData, fastddsSampleInfo, readBuffer);
            if (sizeBytes < 0)
            {
               return false;
            }
            if (deserialize(data, readBuffer, sizeBytes))
            {
               return true;
            }
         }
      }
      finally
      {
         pollLock.unlock();
      }
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
    * <p>
    * Only samples that carry data are counted. If the latest sample is malformed it is dropped and 0 is returned.
    */
   @Override
   public int readLatest(T data)
   {
      if (isDispatchThread())
      {
         return readLatestInCallback(data);
      }

      pollLock.lock();
      try
      {
         int count = 0;
         int lastSizeBytes = -1;
         int sizeBytes;
         while ((sizeBytes = takeNextValidSample(fastddsUserSampleData, userSampleInfo.fastddsSampleInfo, readBuffer)) >= 0)
         {
            count++;
            lastSizeBytes = sizeBytes;
         }

         if (count == 0)
         {
            return 0;
         }
         return deserialize(data, readBuffer, lastSizeBytes) ? count : 0;
      }
      finally
      {
         pollLock.unlock();
      }
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
    * Returns a cached {@link Guid} instance owned by this subscription; its bytes are refreshed on each call.
    * Copy with {@link Guid#set(Guid)} if you need an independent snapshot. Still valid after the subscription is closed.
    */
   public Guid getGuid()
   {
      guid.set(guidBytes);
      return guid;
   }

   /**
    * Use {@link ROS2Node#destroySubscription(ROS2Subscription)}
    * <p>
    * Marks the subscription closed immediately: no callback starts afterwards and reads return no data. Called from a
    * jros2 callback (see {@link CallbackContext}), including this subscription's own callback, the native teardown is
    * deferred because deleting a DataReader from a Fast-DDS callback deadlocks; otherwise it completes before this method
    * returns, after waiting for a callback of this subscription that is running on another thread (inline or on the
    * callback executor), so that no callback runs once this method returned. The native listener is deleted only after
    * Fast-DDS confirmed the DataReader deletion, which releases the references that the native layer holds.
    */
   protected void close(long fastddsParticipant)
   {
      if (gate.close())
      {
         closed = true;
         dataAvailableTrampoline.clear();
         subscriptionMatchedTrampoline.clear();
         synchronized (discoveryLock)
         {
            discoveryLock.notifyAll();
         }
      }

      if (CallbackContext.isInCallback())
      {
         if (teardownScheduled.compareAndSet(false, true))
         {
            CallbackContext.runDeferred(() -> releaseNatives(fastddsParticipant));
         }
      }
      else
      {
         awaitRunningCallback();
         releaseNatives(fastddsParticipant);
      }
   }

   /**
    * Waits until no thread dispatches callbacks of this subscription. Only valid once {@link #closed} is set: a dispatch
    * that is still running stops after its current callback and no new one starts.
    * <p>
    * Inline callbacks are also fenced by Fast-DDS (deleting the DataReader waits for its listener), but a callback running
    * on a callback executor is not, so without this a callback could still run after {@code destroySubscription()}
    * returned. Never called on a dispatching thread: those are inside a {@link CallbackContext} and defer the teardown.
    */
   private void awaitRunningCallback()
   {
      if (callback == null || dispatchLock.isHeldByCurrentThread())
      {
         return;
      }

      boolean interrupted = false;
      try
      {
         for (;;)
         {
            try
            {
               if (dispatchLock.tryLock(5, TimeUnit.SECONDS))
               {
                  dispatchLock.unlock();
                  return;
               }
            }
            catch (InterruptedException e)
            {
               interrupted = true;
               continue;
            }
            jros2.getLogger().warning(HotPathText.stillWaitingForCallback(topic.getName()));
         }
      }
      finally
      {
         if (interrupted)
         {
            Thread.currentThread().interrupt();
         }
      }
   }

   private void releaseNatives(long fastddsParticipant)
   {
      synchronized (teardownLock)
      {
         if (nativesReleased)
         {
            return;
         }
         nativesReleased = true;

         // Wait for native calls (takes, status queries) that entered before the gate closed.
         gate.awaitIdle();

         // Called on a thread that is not a Fast-DDS callback thread: an OK return guarantees that no listener callback
         // is running or will run, so the listener (and its JNI global references) can be freed.
         int ret = fastddsjava.deleteDataReader(fastddsSubscriber, fastddsDataReader);
         if (ret == RETCODE_OK)
         {
            fastddsjava.deleteDataReaderListener(fastddsDataReaderListener);
         }
         else
         {
            jros2.getLogger().log(Level.SEVERE, HotPathText.dataReaderDeletionFailed(topic.getName(), ret));
         }

         // Nothing in Java touches these any more: every use goes through the closed gate.
         userSampleInfo.close();
         fastddsjava.deleteData(topicData.fastddsTopicDataWrapperType, fastddsUserSampleData);
         if (callbackSampleInfo != null)
         {
            callbackSampleInfo.close();
            fastddsjava.deleteData(topicData.fastddsTopicDataWrapperType, fastddsCallbackSampleData);
         }

         ret = fastddsjava.deleteSubscriber(fastddsParticipant, fastddsSubscriber);
         if (ret != RETCODE_OK)
         {
            jros2.getLogger().log(Level.WARNING, HotPathText.subscriberDeletionFailed(topic.getName(), ret));
         }
      }
   }

   /**
    * @return true once the native resources of this subscription have been released (always eventually true after close)
    */
   boolean isNativeReleased()
   {
      synchronized (teardownLock)
      {
         return nativesReleased;
      }
   }

   /**
    * @return true if the callbacks registered with the native listener no longer reference this subscription
    */
   boolean isListenerTrampolineCleared()
   {
      return dataAvailableTrampoline.isCleared() && subscriptionMatchedTrampoline.isCleared();
   }

   /**
    * @return the Fast-DDS topic handles this subscription reads from
    */
   TopicData getTopicData()
   {
      return topicData;
   }

   /**
    * @return true if this subscription uses the ABI v2 single-call take path
    */
   boolean usesNativeAbiV2()
   {
      return useTakeToBufferAbi;
   }

   private void recordStatistics(int payloadSizeBytes, long fastddsSampleInfo)
   {
      // Time when the sample was published
      long sourceTimestampMs = TimeUnit.NANOSECONDS.toMillis(fastddsjava.sampleInfoSourceTimestampNanos(fastddsSampleInfo));

      // Time when the sample was received
      long receptionTimestampMs = TimeUnit.NANOSECONDS.toMillis(fastddsjava.sampleInfoReceptionTimestampNanos(fastddsSampleInfo));

      statisticsLock.lock();
      try
      {
         // Record message size (the entire payload including the header)
         statisticsCalculators[SIZE.ordinal()].record(payloadSizeBytes);

         // Record receive period if available
         if (lastReceiveTime != Long.MIN_VALUE)
         {
            statisticsCalculators[PERIOD.ordinal()].record(receptionTimestampMs - lastReceiveTime);
         }
         lastReceiveTime = receptionTimestampMs;

         // Record message age
         statisticsCalculators[AGE.ordinal()].record(receptionTimestampMs - sourceTimestampMs);
      }
      finally
      {
         statisticsLock.unlock();
      }
   }

   /**
    * Enables or disables recording of message size, period and age statistics (see {@link MessageStatisticsProvider}).
    * Disabled by default. Samples are recorded when they are taken from Fast-DDS (before the callback in callback mode,
    * in {@code read()}/{@code readLatest()} in polling mode). Previously recorded values are kept when recording is
    * disabled; use {@link #resetStatistics()} to clear them.
    */
   @Override
   public void setStatisticsEnabled(boolean enabled)
   {
      statisticsEnabled = enabled;
   }

   /**
    * @return true if statistics are recorded for every received sample
    */
   @Override
   public boolean isStatisticsEnabled()
   {
      return statisticsEnabled;
   }

   @Override
   public void resetStatistics()
   {
      statisticsLock.lock();
      try
      {
         for (int i = 0; i < statisticsCalculatorCount; ++i)
         {
            statisticsCalculators[i].reset();
         }
         lastReceiveTime = Long.MIN_VALUE;
      }
      finally
      {
         statisticsLock.unlock();
      }
   }

   @Override
   public void readStatistics(MessageMetadataType messageMetadataType, Statistics statisticToPack)
   {
      statisticsCalculators[messageMetadataType.ordinal()].read(statisticToPack);
   }

   @Override
   public double getStatistic(MessageMetadataType messageMetadataType, Statistics.StatisticDataType statisticType)
   {
      return statisticsCalculators[messageMetadataType.ordinal()].get(statisticType);
   }

   /**
    * @return the number of received samples that failed to deserialize and were dropped
    */
   public long getMalformedSampleCount()
   {
      return malformedSampleCount.get();
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

   /**
    * @return the number of received samples that have not been taken yet (polling mode), as reported by Fast-DDS. In
    *       callback mode samples are taken as they arrive, so this is usually 0. Returns 0 once closed.
    */
   public int getUnreadMessageCount()
   {
      int count = 0;

      if (gate.enter())
      {
         try
         {
            count = fastddsjava.dataReaderGetUnreadCount(fastddsDataReader);
         }
         finally
         {
            gate.exit();
         }
      }

      return count;
   }
}
