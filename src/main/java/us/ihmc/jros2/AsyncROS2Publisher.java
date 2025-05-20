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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A ROS 2-compatible publisher for publishing {@link ROS2Message} types. This
 * publisher is non-blocking, allocation-free, and realtime safe. In general,
 * it will use more memory and CPU cycles than {@link ROS2Publisher}.
 */
public class AsyncROS2Publisher<T extends ROS2Message<T>> extends ROS2Publisher<T>
{
   private static final int CAPACITY = 32;

   private final AsyncROS2Node node;

   private final T[] messagesToPublish;
   private int insertPosition;
   private int publishPosition;
   private final AtomicInteger queueSize;

   private final Runnable publishTask;

   /**
    * Use {@link AsyncROS2Node#createPublisher(ROS2Topic, ROS2QoSProfile)}
    */
   protected AsyncROS2Publisher(AsyncROS2Node node, Pointer fastddsParticipant, String publisherProfileName, ROS2Topic<T> topic, TopicData topicData)
   {
      super(fastddsParticipant, publisherProfileName, topic, topicData);

      this.node = node;

      // TODO: how should we set the capacity?
      insertPosition = 0;
      publishPosition = 0;
      queueSize = new AtomicInteger(0);
      //noinspection unchecked
      messagesToPublish = (T[]) new ROS2Message[CAPACITY];
      for (int i = 0; i < CAPACITY; ++i)
      {
         messagesToPublish[i] = ROS2Message.createInstance(topic.getType());
      }

      publishTask = this::publishTask;
   }

   @Override
   public void publish(T message)
   {
      if (!closed)
      {
         if (queueSize.getAndIncrement() >= CAPACITY)
         {
            // TODO: Better behavior/message
            queueSize.decrementAndGet();
            throw new RuntimeException("Exceeded queue size :(");
         }

         T messageToPublish = messagesToPublish[insertPosition];
         messageToPublish.set(message);

         if (node.addTask(publishTask))
         {
            insertPosition = (insertPosition + 1) % CAPACITY;
         }
         else
         {
            queueSize.decrementAndGet();
            throw new RuntimeException("AsyncROS2Node did not accept the task");
         }
      }
   }

   private void publishTask()
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            super.publish(messagesToPublish[publishPosition]);
            publishPosition = (publishPosition + 1) % CAPACITY;
         }
      }
      finally
      {
         queueSize.decrementAndGet();
         closeLock.readLock().unlock();
      }
   }
}
