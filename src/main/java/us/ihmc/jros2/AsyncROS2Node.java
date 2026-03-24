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
import us.ihmc.log.LogTools;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * A ROS 2-compatible node which provides functionality for managing ROS 2-compatible publishers, subscriptions.
 * Uses Fast-DDS middleware via the {@link us.ihmc.fastddsjava} package. Fully thread-safe.
 * <p>
 * This type of node will create {@link AsyncROS2Publisher} publishers, which are non-blocking, allocation-free, and realtime safe.
 * In general, it will use more memory and CPU cycles than {@link ROS2Node}.
 * <p>
 * Performance optimizations:
 * - Lock-free SPMC queue using atomic operations
 * - Zero-allocation publish path
 * - Busy-spin with backoff for minimal latency
 * - Cache-line padding to avoid false sharing
 */
public class AsyncROS2Node extends ROS2Node
{
   static
   {
      jros2.load();
   }

   private static final int QUEUE_CAPACITY = 256;
   private static final AtomicLong publisherIdCounter = new AtomicLong(0);

   // Spin parameters for latency optimization
   private static final int MAX_SPIN_ITERATIONS = 100; // Limited spin before parking
   private static final long PARK_NANOS = 1000L; // 1 microsecond park

   /*
    * Publish thread
    */
   private final Thread publishThread;
   private final String threadName;

   /*
    * Lock-free circular buffer for publish tasks
    * Each publisher manages its own queue, so we just wake the thread
    */
   private volatile boolean running = true;

   public AsyncROS2Node(String name, int domainId, TransportDescriptorType... fastddsTransports)
   {
      super(name, domainId, fastddsTransports);

      // Pre-allocate thread name during construction to avoid allocation during runtime
      threadName = "AsyncROS2NodePublishThread-" + name;
      publishThread = new Thread(this::publishLoop, threadName);
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
      if (!closed)
      {
         synchronized (closeLock)
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
                  LogTools.error(e);
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
      running = false;
      publishThread.interrupt();
      try
      {
         publishThread.join(100);
      }
      catch (InterruptedException interruptedException)
      {
         LogTools.error("Publish thread did not join.");
      }

      super.close();
   }

   /**
    * Wakes up the publish thread. Called by publishers when they have work.
    */
   protected void signalPublishThread()
   {
      LockSupport.unpark(publishThread);
   }

   /**
    * High-performance publish loop that iterates over all publishers.
    * Uses limited busy-spin with efficient parking for balanced CPU/latency.
    * <p>
    * Performance characteristics:
    * - Brief spin (100 iterations) before parking
    * - 1 microsecond park time for reasonable wakeup latency
    * - Processes all publishers in single pass (batching)
    * - Minimizes synchronized block time
    * - Low CPU usage when idle
    */
   private void publishLoop()
   {
      int spinCount = 0;

      while (running)
      {
         boolean didWork = false;

         // Iterate over all publishers and process pending messages
         // Keep synchronized section minimal
         synchronized (publishers)
         {
            final int publisherCount = publishers.size();
            for (int i = 0; i < publisherCount; i++)
            {
               ROS2Publisher<?> publisher = publishers.get(i);
               if (publisher instanceof AsyncROS2Publisher)
               {
                  if (((AsyncROS2Publisher<?>) publisher).processPendingMessages())
                  {
                     didWork = true;
                  }
               }
            }
         }

         if (didWork)
         {
            spinCount = 0; // Reset spin count - we're actively processing
         }
         else
         {
            spinCount++;

            // Adaptive backoff: limited spin, then efficient park
            // Balances latency (~1-2μs) with reasonable CPU usage
            if (spinCount > MAX_SPIN_ITERATIONS)
            {
               LockSupport.parkNanos(PARK_NANOS);
               spinCount = 0; // Reset after parking
            }
         }
      }
   }
}
