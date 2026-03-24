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

   /*
    * Message publish queue
    */
   private final int queueCapacity;
   private final AtomicInteger queueSize;
   private int insertPosition;
   private int publishPosition;
   private final T[] messagesToPublish;

   /*
    * Pre-allocated message publish method lambda
    */
   private final Runnable publishTask;

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
      this.queueCapacity = queueCapacity;

      queueSize = new AtomicInteger();

      //noinspection unchecked
      messagesToPublish = (T[]) new ROS2Message[queueCapacity];
      for (int i = 0; i < queueCapacity; ++i)
      {
         messagesToPublish[i] = ROS2Message.createInstance(topic.getType());
      }

      publishTask = this::publishTask;
   }

   private long lastQueueOverflowWarnTimeNs;

   @Override
   public void publish(T message)
   {
      if (!closed)
      {
         int currentSize = queueSize.get();
         if (currentSize >= queueCapacity)
         {
            long now = System.nanoTime();

            // Print a warning every 1 second (avoid string formatting in hot path)
            if (now - lastQueueOverflowWarnTimeNs > 1_000_000_000L)
            {
               // Only allocate strings when actually logging
               if (LogTools.isWarnEnabled())
               {
                  LogTools.warn(
                        "AsyncROS2Publisher ({}) has exceeded the queue capacity of {}. You may be either publishing messages too fast or using intraprocess mode with a time-consuming subscription callback.",
                        node.getName(),
                        queueCapacity);
               }

               lastQueueOverflowWarnTimeNs = now;
            }
            return; // Drop message instead of failing
         }

         queueSize.incrementAndGet();

         T messageToPublish = messagesToPublish[insertPosition];
         messageToPublish.set(message);

         if (node.addTask(publishTask))
         {
            insertPosition = (insertPosition + 1) % queueCapacity;
         }
         else
         {
            queueSize.decrementAndGet();

            throw new RuntimeException("AsyncROS2Node did not accept the task");
         }
      }
   }

   /**
    * Called from parent publish thread in {@link AsyncROS2Node}
    */
   private void publishTask()
   {
      closeLock.lock();
      try
      {
         if (!closed)
         {
            try
            {
               super.publish(messagesToPublish[publishPosition]);
               publishPosition = (publishPosition + 1) % queueCapacity;
            }
            finally
            {
               queueSize.decrementAndGet();
            }
         }
      }
      finally
      {
         closeLock.unlock();
      }
   }
}
