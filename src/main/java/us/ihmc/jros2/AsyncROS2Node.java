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

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

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

   private final Thread publishThread;
   private final BlockingQueue<Runnable> tasks;

   public AsyncROS2Node(String name)
   {
      this(name, jros2.get().rosDomainId());
   }

   public AsyncROS2Node(String name, int domainId)
   {
      this(name, domainId, (TransportDescriptorType[]) null);
   }

   public AsyncROS2Node(String name, int domainId, TransportDescriptorType... fastddsTransports)
   {
      super(name, domainId, fastddsTransports);

      int capacity = 64;

      // TODO: Name, daemon, etc
      tasks = new ArrayBlockingQueue<>(capacity, true);
      publishThread = new Thread(this::publishLoop);
      publishThread.start();
   }

   @Override
   public <T extends ROS2Message<T>> AsyncROS2Publisher<T> createPublisher(ROS2Topic<T> topic, ROS2QoSProfile qosProfile)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            ProfilesXML profilesXML = new ProfilesXML();
            PublisherProfileType publisherProfile = new PublisherProfileType();
            String publisherProfileName = UUID.randomUUID().toString();
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
            AsyncROS2Publisher<T> publisher = new AsyncROS2Publisher<>(this, fastddsParticipant, publisherProfileName, topic, topicData);

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
   public void close()
   {
      publishThread.interrupt();
      try
      {
         publishThread.join(1000);
      }
      catch (InterruptedException interruptedException)
      {
         LogTools.error("Publish thread did not join.");
      }

      super.close();
   }

   protected boolean addTask(Runnable task)
   {
      return tasks.offer(task);
   }

   private void publishLoop()
   {
      try
      {
         while (!publishThread.isInterrupted())
         {
            tasks.take().run();
         }
      }
      catch (InterruptedException ignored)
      {
      }
   }
}
