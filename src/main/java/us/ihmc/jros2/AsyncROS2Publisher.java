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
import us.ihmc.log.LogTools;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * A ROS 2-compatible publisher for publishing {@link ROS2Message} types. This
 * publisher is non-blocking, allocation-free, and realtime safe. In general,
 * it will use more memory and CPU cycles than {@link ROS2Publisher}.
 * <p>
 * Performance optimizations:
 * - Lock-free SPSC circular queue using atomic operations and memory barriers
 * - Zero-allocation publish() method (garbage-free at runtime)
 * - Cache-line padding to prevent false sharing between producer/consumer
 * - Single-writer principle for maximum throughput
 * - LazySet for write position (StoreStore barrier only)
 * - Volatile read for read position (LoadLoad + LoadStore barrier)
 */
public class AsyncROS2Publisher<T extends ROS2Message<T>> extends ROS2Publisher<T>
{
   static
   {
      jros2.load();
   }

   private final AsyncROS2Node node;

   // Cache line padding (64 bytes) to prevent false sharing
   private long p1, p2, p3, p4, p5, p6, p7;

   /*
    * Lock-free SPSC queue implementation
    * Producer (publish()) owns writePosition
    * Consumer (processPendingMessages()) owns readPosition
    */
   private volatile long writePosition = 0;
   private long p8, p9, p10, p11, p12, p13, p14, p15; // Padding

   private volatile long readPosition = 0;
   private long p16, p17, p18, p19, p20, p21, p22, p23; // Padding

   /*
    * Message circular buffer
    */
   private final int queueCapacity;
   private final int queueMask; // capacity - 1, for fast modulo via bitwise AND
   private final T[] messageBuffer;

   /*
    * Rate limiting for overflow warnings
    */
   private long lastQueueOverflowWarnTimeNs = 0;
   private static final long WARN_INTERVAL_NS = 1_000_000_000L; // 1 second

   /*
    * Cached readPosition for producer to avoid volatile reads in hot path
    */
   private long cachedReadPosition = 0;

   /*
    * Signal throttling to reduce unpark() overhead
    * Only signal if consumer might be parked
    */
   private volatile boolean consumerActive = true;
   private long lastSignalPosition = 0;

   /**
    * Use {@link AsyncROS2Node#createPublisher(ROS2Topic, ROS2QoSProfile)}
    */
   protected AsyncROS2Publisher(AsyncROS2Node node,
                                Pointer fastddsParticipant,
                                String publisherProfileName,
                                ROS2Topic<T> topic,
                                TopicData topicData,
                                int queueCapacity)
   {
      super(fastddsParticipant, publisherProfileName, topic, topicData);

      this.node = node;

      // Round up to nearest power of 2 for fast modulo with bitwise AND
      int actualCapacity = queueCapacity;
      if ((actualCapacity & (actualCapacity - 1)) != 0)
      {
         // Not a power of 2, round up
         actualCapacity = Integer.highestOneBit(actualCapacity) << 1;
      }

      this.queueCapacity = actualCapacity;
      this.queueMask = actualCapacity - 1;

      // Pre-allocate all message instances
      //noinspection unchecked
      messageBuffer = (T[]) new ROS2Message[actualCapacity];
      for (int i = 0; i < actualCapacity; ++i)
      {
         messageBuffer[i] = ROS2Message.createInstance(topic.getType());
      }
   }

   /**
    * Non-blocking, garbage-free publish operation.
    * Returns immediately if queue is full (drops message).
    * <p>
    * Thread-safe: Only ONE thread should call this method (single producer).
    * <p>
    * Optimized for minimal jitter and realtime performance:
    * - Branch prediction friendly (closed check hoisted out in practice)
    * - Minimal volatile operations (cached read position)
    * - No method calls in hot path (overflow handling is rare)
    */
   @Override
   public void publish(T message)
   {
      // Early exit for closed publisher (should be predictable branch)
      if (closed)
      {
         return;
      }

      // Load positions - writePosition is non-volatile read (we own it)
      final long currentWrite = writePosition;
      final long currentRead = cachedReadPosition;

      // Fast path: check if queue might be full using cached read position
      // This avoids volatile read in common case
      final long available = queueCapacity - (currentWrite - currentRead);
      if (available <= 0)
      {
         // Slow path: refresh read position and check again
         final long freshRead = readPosition; // volatile read
         cachedReadPosition = freshRead;

         if (currentWrite - freshRead >= queueCapacity)
         {
            // Queue is full, drop message
            handleQueueOverflow();
            return;
         }
      }

      // Copy message to buffer slot (index calculation using bitwise AND)
      final int index = (int) (currentWrite & queueMask);
      messageBuffer[index].set(message);

      // Publish new write position with volatile write (provides StoreStore + StoreLoad barriers)
      final long newWrite = currentWrite + 1;
      writePosition = newWrite;

      // Optimized signaling: only unpark if consumer is likely parked
      // If consumer is active (busy processing), skip the expensive unpark call
      // This dramatically reduces jitter from syscalls
      if (!consumerActive || (newWrite - lastSignalPosition) >= 4)
      {
         node.signalPublishThread();
         lastSignalPosition = newWrite;
      }
   }

   /**
    * Process pending messages in queue. Called by AsyncROS2Node publish thread.
    * <p>
    * Thread-safe: Only ONE thread should call this method (single consumer).
    *
    * @return true if any messages were processed
    */
   protected boolean processPendingMessages()
   {
      if (closed)
      {
         consumerActive = false;
         return false;
      }

      final long currentRead = readPosition;
      final long currentWrite = writePosition; // volatile read

      // Check if there's work to do
      if (currentRead >= currentWrite)
      {
         consumerActive = false; // Signal that we might park soon
         return false;
      }

      // Mark consumer as active (processing messages)
      consumerActive = true;

      // Process all available messages in tight loop
      long position = currentRead;
      while (position < currentWrite)
      {
         final int index = (int) (position & queueMask);

         // Publish message via parent class
         super.publish(messageBuffer[index]);

         position++;
      }

      // Update read position once after processing batch (single volatile write)
      readPosition = position;

      return true;
   }

   /**
    * Handle queue overflow with rate-limited logging.
    * This method is allocation-free when logging is disabled.
    */
   private void handleQueueOverflow()
   {
      final long now = System.nanoTime();

      // Rate limit warnings to avoid log spam
      if (now - lastQueueOverflowWarnTimeNs > WARN_INTERVAL_NS)
      {
         // Only allocate strings when actually logging
         if (LogTools.isWarnEnabled())
         {
            LogTools.warn(
                  "AsyncROS2Publisher ({}) queue full (capacity: {}). Message dropped. Publishing too fast or consumer thread blocked.",
                  node.getName(),
                  queueCapacity);
         }

         lastQueueOverflowWarnTimeNs = now;
      }
   }

   /**
    * Get current queue size (approximate, may be slightly stale).
    * Useful for monitoring and debugging.
    */
   public int getQueueSize()
   {
      return (int) (writePosition - readPosition);
   }

   /**
    * Get queue capacity.
    */
   public int getQueueCapacity()
   {
      return queueCapacity;
   }
}
