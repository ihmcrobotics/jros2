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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * A ROS 2-compatible publisher for publishing {@link ROS2Message} types. This publisher is non-blocking,
 * allocation-free, and realtime safe: {@link #publish(ROS2Message)} copies the message into a preallocated slot of a
 * bounded lock-free queue and returns; the publish thread of the owning {@link AsyncROS2Node} serializes and writes it.
 * In general, it will use more memory and CPU cycles than {@link ROS2Publisher}.
 * <p>
 * When the queue is full the message is dropped and counted (see {@link #getDroppedMessageCount()}); use
 * {@link #offer(ROS2Message)} to find out whether a particular message was queued. Messages from one publisher are
 * written in the order in which their {@code publish()} calls claimed a queue slot.
 */
public class AsyncROS2Publisher<T extends ROS2Message<T>> extends ROS2Publisher<T>
{
   static
   {
      jros2.load();
   }

   /** Maximum number of messages one publisher writes before the publish thread serves the other publishers. */
   private static final int DRAIN_BATCH = 64;

   private final AsyncROS2Node node;

   private final int queueCapacity;
   private final EndpointSupport.BoundedMpscSequencer queue;
   private final T[] messagesToPublish;
   private final boolean[] slotValid;
   private final AtomicLong droppedMessageCount;
   private final EndpointSupport.RateLimiter publishErrorLog;

   /**
    * Use {@link AsyncROS2Node#createPublisher(ROS2Topic, ROS2QoSProfile, int)}.
    *
    * @param queueCapacity the number of messages that can wait to be published, at least 1
    */
   protected AsyncROS2Publisher(AsyncROS2Node node,
                                long fastddsParticipant,
                                String publisherProfileName,
                                ROS2Topic<T> topic,
                                TopicData topicData,
                                int queueCapacity)
   {
      super(fastddsParticipant, checkCapacity(queueCapacity, publisherProfileName), topic, topicData);

      this.node = node;
      this.queueCapacity = queueCapacity;

      try
      {
         queue = new EndpointSupport.BoundedMpscSequencer(queueCapacity);
         slotValid = new boolean[queueCapacity];
         droppedMessageCount = new AtomicLong();
         publishErrorLog = new EndpointSupport.RateLimiter(5, TimeUnit.SECONDS);

         //noinspection unchecked
         messagesToPublish = (T[]) new ROS2Message[queueCapacity];
         for (int i = 0; i < queueCapacity; ++i)
         {
            messagesToPublish[i] = ROS2Message.createInstance(topic.getType());
         }
      }
      catch (RuntimeException | Error e)
      {
         // The DataWriter already exists (for example an OutOfMemoryError for a huge capacity): never leave it behind,
         // the node does not know this publisher and could not delete its participant otherwise.
         super.close(fastddsParticipant);
         throw e;
      }
   }

   private static String checkCapacity(int queueCapacity, String publisherProfileName)
   {
      if (queueCapacity < 1)
      {
         throw new IllegalArgumentException(HotPathText.queueCapacityTooSmall(queueCapacity));
      }
      return publisherProfileName;
   }

   /**
    * Queues a copy of {@code message} for publishing and returns immediately. If the queue is full the message is
    * dropped and counted in {@link #getDroppedMessageCount()}. Does nothing once the publisher or its node is closed or
    * closing.
    */
   @Override
   public void publish(T message)
   {
      offer(message);
   }

   /**
    * Queues a copy of {@code message} for publishing and returns immediately. Thread-safe, lock-free and
    * allocation-free.
    *
    * @return true if the message was queued; false if the queue was full (the message is counted as dropped) or the
    *       publisher or its node is closed or closing
    */
   public boolean offer(T message)
   {
      if (closed || node.isClosing())
      {
         return false;
      }

      long position = queue.claim();
      if (position < 0)
      {
         droppedMessageCount.incrementAndGet();
         return false;
      }

      int index = queue.index(position);
      slotValid[index] = false;
      try
      {
         messagesToPublish[index].set(message);
         slotValid[index] = true;
      }
      finally
      {
         // Always commit a claimed slot (an invalid one is skipped by the consumer), otherwise the queue would stall.
         queue.commit(position);
         node.signalWork();
      }
      return true;
   }

   /**
    * @return the number of messages dropped because the queue was full (or because they failed to serialize on the
    *       publish thread)
    */
   public long getDroppedMessageCount()
   {
      return droppedMessageCount.get();
   }

   /**
    * @return the maximum number of messages that can wait to be published
    */
   public int getQueueCapacity()
   {
      return queueCapacity;
   }

   /**
    * @return an estimate of the number of messages waiting to be published
    */
   public int getQueuedMessageCount()
   {
      return queue.size();
   }

   /**
    * Publish thread only: writes up to {@link #DRAIN_BATCH} queued messages.
    *
    * @return the number of queue slots processed
    */
   int drainQueue()
   {
      int processed = 0;
      long position;
      while (processed < DRAIN_BATCH && (position = queue.peek()) >= 0)
      {
         int index = queue.index(position);
         try
         {
            if (slotValid[index])
            {
               // The slot is owned by this thread until it is released: no copy needed.
               publishAsync(messagesToPublish[index]);
            }
            else
            {
               droppedMessageCount.incrementAndGet();
            }
         }
         catch (RuntimeException e)
         {
            droppedMessageCount.incrementAndGet();
            EndpointSupport.logRateLimited(publishErrorLog,
                                           Level.SEVERE,
                                           HotPathText.queuedPublishFailed(getTopicName()),
                                           e);
         }
         finally
         {
            queue.release(position);
         }
         processed++;
      }
      return processed;
   }

   /**
    * @return true if a committed message is waiting
    */
   boolean hasQueuedMessages()
   {
      return queue.peek() >= 0;
   }

   @Override
   protected void close(long fastddsParticipant)
   {
      node.unregisterPublisher(this);
      super.close(fastddsParticipant);
   }
}
