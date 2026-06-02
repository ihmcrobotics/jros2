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

import us.ihmc.fastddsjava.fastddsjavaException;
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A ROS 2-compatible node which provides functionality for managing ROS 2-compatible publishers, subscriptions.
 * Uses Fast-DDS middleware via the {@link us.ihmc.fastddsjava} package. Fully thread-safe.
 * <p>
 * This type of node will create {@link AsyncROS2Publisher} publishers, which are non-blocking, allocation-free, and realtime safe.
 * In general, it will use more memory and CPU cycles than {@link ROS2Node}.
 */
public class AsyncROS2Node extends ROS2Node
{
   static
   {
      jros2.load();
   }

   private static final int QUEUE_CAPACITY = 256;
   private static final AtomicLong publisherIdCounter = new AtomicLong(0);

   /*
    * Publish thread
    */
   private final Thread publishThread;
   private final BlockingQueue<Runnable> tasks;
   private final String threadName;

   public AsyncROS2Node(String name, int domainId, TransportDescriptorType... fastddsTransports)
   {
      super(name, domainId, fastddsTransports);

      tasks = new ArrayBlockingQueue<>(QUEUE_CAPACITY, false); // Unfair for better performance

      // Pre-allocate thread name during construction to avoid allocation during runtime
      threadName = "AsyncROS2NodePublishThread-" + name;
      publishThread = new Thread(this::publishLoop, threadName);
      publishThread.setDaemon(true); // Allow JVM to exit even if this thread is running
      publishThread.setPriority(Thread.NORM_PRIORITY + 1); // Slightly higher priority
      publishThread.start();
   }

   public AsyncROS2Node(String name, int domainId)
   {
      this(name, domainId, (TransportDescriptorType[]) null);
   }

   public AsyncROS2Node(String name)
   {
      this(name, jros2.get().rosDomainId());
   }

   public <T extends ROS2Message<T>> AsyncROS2Publisher<T> createPublisher(ROS2Topic<T> topic, ROS2QoSProfile qosProfile, int queueCapacity)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            ProfilesXML profilesXML = new ProfilesXML();
            PublisherProfileType publisherProfile = new PublisherProfileType();

            // Prefix with "apub_" to ensure valid XML identifier
            long publisherId = publisherIdCounter.getAndIncrement();
            String publisherProfileName = "apub_" + publisherId;

            publisherProfile.setProfileName(publisherProfileName);
            profilesXML.addPublisherProfile(publisherProfile);

            // Translate the ROS2QoSProfile into Fast-DDS publisher profile XML
            QoSTools.translateQoS(qosProfile, publisherProfile);

            try
            {
               profilesXML.load();
            }
            catch (fastddsjavaException e)
            {
               jros2.logError("Failed to load publisher profile '" + publisherProfileName + "' for topic '" + topic.getName() + "'", e);
               throw new RuntimeException("Failed to load publisher profile: " + publisherProfileName, e);
            }

            TopicData topicData = getOrCreateTopicData(topic);
            AsyncROS2Publisher<T> publisher = new AsyncROS2Publisher<>(this, fastddsParticipant, publisherProfileName, topic, topicData, queueCapacity);

            synchronized (publishers)
            {
               publishers.add(publisher);
            }

            return publisher;
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return null;
   }

   @Override
   public <T extends ROS2Message<T>> AsyncROS2Publisher<T> createPublisher(ROS2Topic<T> topic, ROS2QoSProfile qosProfile)
   {
      int defaultQueueCapacity = 32;

      return createPublisher(topic, qosProfile, defaultQueueCapacity);
   }

   @Override
   public void close()
   {
      closeLock.writeLock().lock();
      if (closed)
      {
         closeLock.writeLock().unlock();
         return;
      }
      closed = true;
      closeLock.writeLock().unlock();

      publishThread.interrupt();
      try
      {
         publishThread.join(2000);
      }
      catch (InterruptedException interruptedException)
      {
         Thread.currentThread().interrupt();
         jros2.logError("Publish thread did not join.", interruptedException);
      }

      super.close();
   }

   protected boolean addTask(Runnable task)
   {
      closeLock.readLock().lock();
      try
      {
         if (closed)
            return false;
         return tasks.offer(task);
      }
      finally
      {
         closeLock.readLock().unlock();
      }
   }

   private void publishLoop()
   {
      try
      {
         while (!Thread.currentThread().isInterrupted())
         {
            Runnable task = tasks.take();
            task.run();
         }
      }
      catch (InterruptedException ignored)
      {
         Thread.currentThread().interrupt();
      }
   }
}
