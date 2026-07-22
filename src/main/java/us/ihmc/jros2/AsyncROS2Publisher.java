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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A ROS 2-compatible publisher for publishing {@link ROS2Message} types. This
 * publisher is non-blocking, allocation-free, and realtime safe. In general,
 * it will use more memory and CPU cycles than {@link ROS2Publisher}.
 */
public class AsyncROS2Publisher<T extends ROS2Message<T>> extends ROS2Publisher<T>
{
   static
   {
      jros2.load();
   }

   private final AsyncROS2Node node;

   private final int queueCapacity;
   private final AtomicInteger queueSize;
   private int insertPosition;
   private int publishPosition;
   private final T[] messagesToPublish;

   private final Runnable publishTask;
   private final AtomicBoolean publishTaskScheduled;

   protected AsyncROS2Publisher(AsyncROS2Node node,
                                long fastddsParticipant,
                                String publisherProfileName,
                                ROS2Topic<T> topic,
                                TopicData topicData,
                                int queueCapacity)
   {
      super(fastddsParticipant, publisherProfileName, topic, topicData, false);

      this.node = node;
      this.queueCapacity = queueCapacity;

      queueSize = new AtomicInteger();
      insertPosition = 0;
      publishPosition = 0;

      //noinspection unchecked
      messagesToPublish = (T[]) new ROS2Message[queueCapacity];
      for (int i = 0; i < queueCapacity; ++i)
      {
         messagesToPublish[i] = ROS2Message.createInstance(topic.getType());
      }

      publishTask = this::publishTask;
      publishTaskScheduled = new AtomicBoolean(false);

      int payloadSizeBytes = CDRBuffer.PAYLOAD_HEADER.length + messagesToPublish[0].calculateSizeBytes(0);
      preallocateWriteBuffer(payloadSizeBytes);
   }

   @Override
   public void publish(T message)
   {
      if (!closed)
      {
         int sizeBefore = queueSize.getAndIncrement();
         if (sizeBefore < queueCapacity)
         {
            messagesToPublish[insertPosition].set(message);

            if (sizeBefore == 0 && !schedulePublishTaskIfNeeded())
            {
               queueSize.decrementAndGet();
            }
            else
            {
               insertPosition = (insertPosition + 1) % queueCapacity;
            }
         }
         else
         {
            queueSize.decrementAndGet();
         }
      }
   }

   private boolean schedulePublishTaskIfNeeded()
   {
      boolean scheduled = true;

      if (publishTaskScheduled.compareAndSet(false, true))
      {
         if (!node.addTask(publishTask))
         {
            publishTaskScheduled.set(false);
            scheduled = false;
         }
      }

      return scheduled;
   }

   private void publishTask()
   {
      try
      {
         while (!closed && queueSize.get() > 0)
         {
            publishAsync(messagesToPublish[publishPosition]);
            publishPosition = (publishPosition + 1) % queueCapacity;
            queueSize.decrementAndGet();
         }
      }
      finally
      {
         publishTaskScheduled.set(false);

         if (!closed && queueSize.get() > 0)
         {
            schedulePublishTaskIfNeeded();
         }
      }
   }
}
