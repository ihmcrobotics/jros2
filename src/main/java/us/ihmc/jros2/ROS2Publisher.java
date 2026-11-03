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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

import static us.ihmc.fastddsjava.natives.fastddsjava.RETCODE_OK;
import static us.ihmc.jros2.MessageStatisticsProvider.MessageMetadataType.*;

/**
 * A ROS 2-compatible publisher for publishing {@link ROS2Message} types.
 * <p>
 * {@link #publish(ROS2Message)} is thread-safe: concurrent calls are serialized, and each call serializes the message and
 * hands it to Fast-DDS before the next one starts. It is allocation-free after warm-up for messages whose serialized size
 * does not grow. With intraprocess delivery, subscription callbacks in the same process may run inside
 * {@code publish()}; publishing again (also on this publisher) or closing endpoints from such a callback is safe.
 * <p>
 * With intraprocess delivery, Fast-DDS holds the writer's lock while it delivers to the subscription callbacks of the
 * same process. Two threads that publish on publishers A and B at the same time, whose callbacks publish on B and A
 * respectively, therefore deadlock inside Fast-DDS (the same applies to any lock the callbacks share). Use a callback
 * executor (see {@link ROS2NodeOptions#callbackExecutor(java.util.concurrent.Executor)}) or an {@link AsyncROS2Node} for
 * such request/response cycles.
 */
public class ROS2Publisher<T extends ROS2Message<T>> implements MessageStatisticsProvider
{
   static
   {
      jros2.load();
   }

   private static final int PAYLOAD_HEADER_SIZE = CDRBuffer.PAYLOAD_HEADER.length;
   private static final Object[] NO_ARGUMENTS = new Object[0];
   /** Reflective calls of the header getter made when statistics are enabled (see {@link #prepareHeaderMethod()}). */
   private static final int HEADER_METHOD_WARMUP_CALLS = 20;

   /*
    * Debug
    */
   private final ROS2Topic<T> topic;

   /*
    * Lifecycle
    */
   /**
    * @deprecated No longer used. Publishing and closing are coordinated by a non-blocking internal gate so that
    *       closing never deadlocks with publishes running inside Fast-DDS callbacks. Kept for subclass compatibility.
    */
   @Deprecated
   protected final ReadWriteLock closeLock;
   /** True once {@link #close(long)} has been called. */
   protected volatile boolean closed;
   private final EndpointSupport.Gate gate;
   private final Object teardownLock;
   private boolean nativesReleased;
   private final AtomicBoolean teardownScheduled;

   /*
    * Discovery
    */
   private final Object discoveryLock;
   private volatile boolean subscriptionDiscovered;

   /*
    * Fast-DDS pointers
    */
   private final TopicData topicData;
   private final boolean useWriteBufferAbi;
   /** Native topic data wrapper used by the ABI v1 write path; 0 with ABI v2. */
   private final long fastddsTopicData;

   /*
    * Write buffer, guarded by writeLock (re-entrant for intraprocess callbacks that publish again; allocation-free when
    * several threads publish at once, unlike a contended monitor)
    */
   private final AllocationFreeLock writeLock;
   private final CDRBuffer writeBuffer;
   private int lastPayloadSizeBytes;
   private final EndpointSupport.RateLimiter writeErrorLog;

   /*
    * Statistics (opt-in)
    */
   private volatile boolean statisticsEnabled;
   private final int statisticsCalculatorCount;
   private final StatisticsCalculator[] statisticsCalculators;
   private final AllocationFreeLock statisticsLock;
   private Method getHeaderMethod;
   /** The message type implements {@link ROS2Message.WithHeader}: no reflection for the age statistic. */
   private boolean headerFromInterface;
   private boolean headerMethodResolved;
   private long lastPublishTime;

   /*
    * GUID
    */
   private final byte[] guidBytes;
   private final Guid guid;

   /*
    * Fast-DDS pointers (endpoints)
    */
   private final EndpointSupport.Trampoline publicationMatchedTrampoline;
   private volatile boolean listenerReady;
   private final long fastddsDataWriterListener;
   private final long fastddsPublisher;
   private final long fastddsDataWriter;

   /**
    * Use {@link ROS2Node#createPublisher(ROS2Topic, ROS2QoSProfile)}
    *
    * @throws IllegalStateException if Fast-DDS refuses to create the publisher or data writer (for example an invalid QoS
    *                               profile, or a topic name already used with a different type); nothing is leaked
    */
   ROS2Publisher(long fastddsParticipant, String publisherProfileName, ROS2Topic<T> topic, TopicData topicData)
   {
      this.topic = topic;
      this.topicData = topicData;

      closeLock = new ReentrantReadWriteLock(true);
      closed = false;
      gate = new EndpointSupport.Gate();
      teardownLock = new Object();
      teardownScheduled = new AtomicBoolean(false);

      discoveryLock = new Object();
      subscriptionDiscovered = false;

      if (topicData == null || topicData.fastddsTopic == 0)
      {
         throw new IllegalStateException(HotPathText.publisherTopicMissing(topic.getName()));
      }

      useWriteBufferAbi = EndpointSupport.useNativeAbiV2();

      writeLock = new AllocationFreeLock();
      writeBuffer = new CDRBuffer();
      writeErrorLog = new EndpointSupport.RateLimiter(5, TimeUnit.SECONDS);

      // Grow the write buffer once up front for the empty message size so steady-state
      // fixed-size publishes do not allocate on first use.
      int initialPayloadSizeBytes = initialPayloadSizeBytes(topic.getType());
      writeBuffer.ensureRemainingCapacity(initialPayloadSizeBytes);
      lastPayloadSizeBytes = initialPayloadSizeBytes;

      statisticsEnabled = false;
      statisticsCalculatorCount = MessageMetadataType.values.length;
      statisticsCalculators = new StatisticsCalculator[statisticsCalculatorCount];
      statisticsLock = new AllocationFreeLock();
      for (int i = 0; i < statisticsCalculatorCount; ++i)
      {
         statisticsCalculators[i] = new StatisticsCalculator();
      }
      lastPublishTime = Long.MIN_VALUE;

      guidBytes = new byte[16];
      guid = new Guid();

      publicationMatchedTrampoline = new EndpointSupport.Trampoline(this::onPublicationMatched);

      long topicDataHandle = 0;
      long listener = 0;
      long publisher = 0;
      long writer;
      try
      {
         if (!useWriteBufferAbi)
         {
            topicDataHandle = fastddsjava.createData(topicData.fastddsTopicDataWrapperType);
            if (topicDataHandle == 0)
            {
               throw new IllegalStateException(HotPathText.writeBufferAllocationFailed(topic.getName()));
            }
            fastddsjava.topicDataResize(topicDataHandle, initialPayloadSizeBytes);
         }

         listener = fastddsjava.createDataWriterListener();
         if (listener == 0)
         {
            throw new IllegalStateException(HotPathText.dataWriterListenerCreationFailed(topic.getName()));
         }
         fastddsjava.dataWriterListenerSetOnPublicationMatched(listener, publicationMatchedTrampoline);

         publisher = fastddsjava.createPublisher(fastddsParticipant, publisherProfileName);
         if (publisher == 0)
         {
            throw new IllegalStateException(HotPathText.publisherCreationFailed(topic.getName(), publisherProfileName));
         }

         writer = fastddsjava.createDataWriter(publisher, topicData.fastddsTopic, publisherProfileName);
         if (writer == 0)
         {
            throw new IllegalStateException(HotPathText.dataWriterCreationFailed(topic.getName(), topic.getType().getName(), publisherProfileName));
         }
      }
      catch (RuntimeException | Error e)
      {
         publicationMatchedTrampoline.clear();
         if (publisher != 0)
         {
            int ret = fastddsjava.deletePublisher(fastddsParticipant, publisher);
            if (ret != RETCODE_OK)
            {
               jros2.getLogger().warning(HotPathText.partialPublisherDeletionFailed(ret));
            }
         }
         if (listener != 0)
         {
            // Never attached to an entity, so no callback can be running.
            fastddsjava.deleteDataWriterListener(listener);
         }
         if (topicDataHandle != 0)
         {
            fastddsjava.deleteData(topicData.fastddsTopicDataWrapperType, topicDataHandle);
         }
         gate.close();
         closed = true;
         throw e;
      }

      fastddsTopicData = topicDataHandle;
      fastddsDataWriterListener = listener;
      fastddsPublisher = publisher;
      fastddsDataWriter = writer;
      fastddsjava.getWriterGuid(fastddsDataWriter, guidBytes);

      // Publish all fields to the callback thread before the listener can run.
      listenerReady = true;
      int setListenerRet = fastddsjava.dataWriterSetListener(fastddsDataWriter, fastddsDataWriterListener);
      if (setListenerRet != RETCODE_OK)
      {
         jros2.getLogger().warning(HotPathText.publicationListenerAttachFailed(topic.getName(), setListenerRet));
      }

      // Check if subscription is already matched (outside of discovery lock to avoid nested synchronization)
      if (getPublicationMatchedStatus() > 0)
      {
         synchronized (discoveryLock)
         {
            subscriptionDiscovered = true;
         }
      }
   }

   /**
    * @return the serialized size (CDR header included) of a default-constructed message of {@code type}
    */
   static <M extends ROS2Message<M>> int initialPayloadSizeBytes(Class<M> type)
   {
      M emptyMessage = ROS2Message.createInstance(type);
      return PAYLOAD_HEADER_SIZE + (emptyMessage == null ? 0 : emptyMessage.calculateSizeBytes(0));
   }

   /**
    * Serializes and publishes {@code message}. Does nothing once the publisher is closed.
    * <p>
    * Thread-safe. The message is only read during the call and may be reused as soon as this method returns.
    *
    * @throws RuntimeException if the message cannot be serialized (for example a sequence longer than its bound)
    */
   public void publish(T message)
   {
      if (gate.enter())
      {
         try
         {
            writeAndPublish(message);
         }
         finally
         {
            gate.exit();
         }
      }
   }

   /**
    * Publishes {@code message} synchronously on behalf of {@link AsyncROS2Publisher}'s publish thread. Like
    * {@link #publish(ROS2Message)}, it goes through the publisher's close gate, so closing the publisher waits for an
    * in-flight write instead of releasing the native writer under it.
    */
   protected void publishAsync(T message)
   {
      if (gate.enter())
      {
         try
         {
            writeAndPublish(message);
         }
         finally
         {
            gate.exit();
         }
      }
   }

   /**
    * Grows the write buffer so that payloads of up to {@code payloadSizeBytes} (CDR header included) do not allocate.
    */
   protected void preallocateWriteBuffer(int payloadSizeBytes)
   {
      writeLock.lock();
      try
      {
         if (payloadSizeBytes > writeBuffer.getBufferUnsafe().capacity())
         {
            writeBuffer.rewind();
            writeBuffer.ensureRemainingCapacity(payloadSizeBytes);
         }
      }
      finally
      {
         writeLock.unlock();
      }
   }

   private void writeAndPublish(T message)
   {
      int payloadSizeBytes;
      int ret;

      // The serialization and the native write form one critical section: the buffer must not change while Fast-DDS
      // copies it. Fast-DDS serializes the payload before it delivers to intraprocess readers, so a callback that
      // publishes again on this thread (re-entering this lock) can safely reuse the buffer.
      writeLock.lock();
      try
      {
         writeBuffer.rewind();

         // Presize from calculateSizeBytes; actual payload length is the buffer position after serialize.
         int estimatedSizeBytes = PAYLOAD_HEADER_SIZE + message.calculateSizeBytes(0);
         if (estimatedSizeBytes > writeBuffer.getBufferUnsafe().capacity())
         {
            writeBuffer.ensureRemainingCapacity(estimatedSizeBytes);
         }

         writeBuffer.writePayloadHeader();
         message.serialize(writeBuffer);
         ByteBuffer buffer = writeBuffer.getBufferUnsafe();
         payloadSizeBytes = buffer.position();

         ret = writeNative(buffer, payloadSizeBytes);
      }
      finally
      {
         writeLock.unlock();
      }

      if (ret != RETCODE_OK)
      {
         EndpointSupport.logRetcode(writeErrorLog, EndpointSupport.WRITE, topic.getName(), ret);
      }

      if (statisticsEnabled)
      {
         recordStatistics(message, payloadSizeBytes, System.currentTimeMillis());
      }
   }

   /**
    * Writes {@code length} bytes of {@code buffer} (CDR header included). The caller holds {@link #writeLock} and the gate.
    */
   private int writeNative(ByteBuffer buffer, int length)
   {
      if (useWriteBufferAbi)
      {
         // One JNI call; Fast-DDS serializes straight from the direct buffer.
         return fastddsjava.dataWriterWriteBuffer(fastddsDataWriter, buffer, length);
      }

      if (length != lastPayloadSizeBytes)
      {
         fastddsjava.topicDataResize(fastddsTopicData, length);
         lastPayloadSizeBytes = length;
      }
      fastddsjava.topicDataWriteBuffer(fastddsTopicData, buffer, 0, length);
      return fastddsjava.dataWriterWrite(fastddsDataWriter, fastddsTopicData);
   }

   /**
    * Test hook: writes an already serialized payload ({@code length} bytes of {@code payload} starting at position 0,
    * CDR header included) without validation.
    *
    * @return the Fast-DDS retcode, or -1 if the publisher is closed
    */
   int writeSerializedPayload(ByteBuffer payload, int length)
   {
      if (!payload.isDirect())
      {
         throw new IllegalArgumentException(HotPathText.payloadNotDirect());
      }
      if (!gate.enter())
      {
         return -1;
      }
      try
      {
         writeLock.lock();
         try
         {
            return writeNative(payload, length);
         }
         finally
         {
            writeLock.unlock();
         }
      }
      finally
      {
         gate.exit();
      }
   }

   /**
    * @return the Fast-DDS topic handles this publisher writes to
    */
   TopicData getTopicData()
   {
      return topicData;
   }

   /**
    * @return true if this publisher uses the ABI v2 single-call write path
    */
   boolean usesNativeAbiV2()
   {
      return useWriteBufferAbi;
   }

   private void recordStatistics(T message, long messageSizeBytes, long publishTimeMillis)
   {
      statisticsLock.lock();
      try
      {
         // Record message size
         statisticsCalculators[SIZE.ordinal()].record(messageSizeBytes);

         // Record publish period if available
         if (lastPublishTime != Long.MIN_VALUE)
         {
            statisticsCalculators[PERIOD.ordinal()].record(publishTimeMillis - lastPublishTime);
         }
         lastPublishTime = publishTimeMillis;

         // Record publish age: generated stamped messages expose their header through an interface; other classes through
         // reflection (the empty argument array is passed explicitly: the varargs call would allocate one per call).
         if (headerFromInterface)
         {
            recordAge(((ROS2Message.WithHeader) message).getHeader(), publishTimeMillis);
         }
         else if (getHeaderMethod != null)
         {
            try
            {
               recordAge((std_msgs.Header) getHeaderMethod.invoke(message, NO_ARGUMENTS), publishTimeMillis);
            }
            catch (IllegalAccessException | InvocationTargetException | RuntimeException e)
            {
               jros2.logError(HotPathText.headerLookupFailed(), e);
               getHeaderMethod = null;
            }
         }
      }
      finally
      {
         statisticsLock.unlock();
      }
   }

   private void recordAge(std_msgs.Header header, long publishTimeMillis)
   {
      long timestampMillis = (1000L * header.getStamp().getSec()) + (header.getStamp().getNanosec() / 1000000L);
      statisticsCalculators[AGE.ordinal()].record(publishTimeMillis - timestampMillis);
   }

   /**
    * Enables or disables recording of message size, period and age statistics (see {@link MessageStatisticsProvider}).
    * Disabled by default: recording takes a lock on every publish (allocation free), and reads the message header for the
    * age statistic: through {@link ROS2Message.WithHeader} for generated stamped messages, through reflection for other
    * message classes that have a {@code std_msgs.Header} getter. Previously recorded values are kept when recording is
    * disabled; use {@link #resetStatistics()} to clear them.
    */
   @Override
   public void setStatisticsEnabled(boolean enabled)
   {
      if (enabled)
      {
         prepareHeaderMethod();
      }
      statisticsEnabled = enabled;
   }

   /**
    * Decides how the age statistic reads the header. For a message class that does not implement
    * {@link ROS2Message.WithHeader}: resolves the header getter that it calls through reflection and calls it a few times, so that the
    * reflective call is linked here, on the thread that enables statistics, rather than on a publishing thread (JDK 17
    * generates an accessor class on the 16th call of a method).
    */
   private void prepareHeaderMethod()
   {
      statisticsLock.lock();
      try
      {
         if (headerMethodResolved)
         {
            return;
         }
         headerMethodResolved = true;
         if (ROS2Message.WithHeader.class.isAssignableFrom(topic.getType()))
         {
            headerFromInterface = true;
            return;
         }
         getHeaderMethod = ROS2Message.getHeaderMethod(topic.getType());
         T sample = getHeaderMethod == null ? null : ROS2Message.createInstance(topic.getType());
         if (sample != null)
         {
            try
            {
               for (int i = 0; i < HEADER_METHOD_WARMUP_CALLS; ++i)
               {
                  getHeaderMethod.invoke(sample, NO_ARGUMENTS);
               }
            }
            catch (IllegalAccessException | InvocationTargetException | RuntimeException e)
            {
               jros2.logError(HotPathText.headerLookupFailed(), e);
               getHeaderMethod = null;
            }
         }
      }
      finally
      {
         statisticsLock.unlock();
      }
   }

   /**
    * @return true if statistics are recorded on every publish
    */
   @Override
   public boolean isStatisticsEnabled()
   {
      return statisticsEnabled;
   }

   /**
    * Get the GUID (Globally Unique Identifier) for this publisher.
    * The GUID is assigned by Fast-DDS and uniquely identifies this publisher instance.
    * <p>
    * Returns a cached {@link Guid} instance owned by this publisher; its bytes are refreshed on each call.
    * Copy with {@link Guid#set(Guid)} if you need an independent snapshot. Still valid after the publisher is closed.
    */
   public Guid getGuid()
   {
      guid.set(guidBytes);
      return guid;
   }

   /**
    * Use {@link ROS2Node#destroyPublisher(ROS2Publisher)}
    * <p>
    * Marks the publisher closed immediately. Called from a jros2 callback (see {@link CallbackContext}) the native
    * teardown is deferred, because deleting a DataWriter from a Fast-DDS callback deadlocks; otherwise it completes before
    * this method returns. In both cases in-flight publishes finish before the native writer is released.
    */
   protected void close(long fastddsParticipant)
   {
      if (gate.close())
      {
         closed = true;
         publicationMatchedTrampoline.clear();
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
         releaseNatives(fastddsParticipant);
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

         // Wait for publishes that entered before the gate closed.
         gate.awaitIdle();

         // Called on a thread that is not a Fast-DDS callback thread: an OK return guarantees that no listener callback
         // is running or will run, so the listener (and its JNI global reference) can be freed.
         int ret = fastddsjava.deleteDataWriter(fastddsPublisher, fastddsDataWriter);
         if (ret == RETCODE_OK)
         {
            fastddsjava.deleteDataWriterListener(fastddsDataWriterListener);
         }
         else
         {
            jros2.getLogger().log(Level.SEVERE, HotPathText.dataWriterDeletionFailed(topic.getName(), ret));
         }

         if (fastddsTopicData != 0)
         {
            fastddsjava.deleteData(topicData.fastddsTopicDataWrapperType, fastddsTopicData);
         }

         ret = fastddsjava.deletePublisher(fastddsParticipant, fastddsPublisher);
         if (ret != RETCODE_OK)
         {
            jros2.getLogger().log(Level.WARNING, HotPathText.publisherDeletionFailed(topic.getName(), ret));
         }
      }
   }

   /**
    * @return true once the native resources of this publisher have been released (always eventually true after close)
    */
   boolean isNativeReleased()
   {
      synchronized (teardownLock)
      {
         return nativesReleased;
      }
   }

   /**
    * @return true if the callback registered with the native listener no longer references this publisher
    */
   boolean isListenerTrampolineCleared()
   {
      return publicationMatchedTrampoline.isCleared();
   }

   private void onPublicationMatched()
   {
      if (!listenerReady)
      {
         return;
      }

      CallbackContext.enter();
      try
      {
         synchronized (discoveryLock)
         {
            subscriptionDiscovered = true;
            discoveryLock.notifyAll();
         }
      }
      finally
      {
         CallbackContext.exit();
      }
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
         lastPublishTime = Long.MIN_VALUE;
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
    * @return The number of subscriptions currently matched to this publisher, 0 once closed
    */
   public int getPublicationMatchedStatus()
   {
      int count = 0;

      if (gate.enter())
      {
         try
         {
            count = fastddsjava.dataWriterGetPublicationMatchedCount(fastddsDataWriter);
         }
         finally
         {
            gate.exit();
         }
      }

      return count;
   }

   /**
    * Wait for a subscription to be discovered.
    * <p>
    * This method blocks until a subscription is discovered, the publisher is closed, or the timeout expires.
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
