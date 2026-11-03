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

import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;

import java.util.Arrays;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;

/**
 * A ROS 2-compatible node which provides functionality for managing ROS 2-compatible publishers, subscriptions.
 * Uses Fast-DDS middleware via the {@link us.ihmc.fastddsjava} package. Fully thread-safe.
 * <p>
 * This type of node will create {@link AsyncROS2Publisher} publishers, which are non-blocking, allocation-free, and realtime safe.
 * In general, it will use more memory and CPU cycles than {@link ROS2Node}.
 * <p>
 * All publishers of one node share a single publish thread, which takes turns between the publishers. A publisher whose
 * writes block (for example a RELIABLE publisher whose history is full, up to its max blocking time) delays the other
 * publishers of the node; use separate nodes to isolate such publishers. The publish thread is created by the
 * {@link ThreadFactory} given to {@link #AsyncROS2Node(String, ROS2NodeOptions, ThreadFactory)}, which is the place to set
 * real-time scheduling, priority or CPU affinity ({@link Thread#setPriority(int)} alone has no effect on Linux).
 */
public class AsyncROS2Node extends ROS2Node
{
   static
   {
      jros2.load();
   }

   /** Queue capacity of publishers created without an explicit capacity. */
   public static final int DEFAULT_QUEUE_CAPACITY = 64;
   private static final int TASK_QUEUE_CAPACITY = 256;
   private static final AsyncROS2Publisher<?>[] NO_PUBLISHERS = new AsyncROS2Publisher<?>[0];

   /*
    * Publish thread
    */
   private final Thread publishThread;
   private final String threadName;
   private volatile boolean running;
   private volatile boolean rejectTasks;
   private volatile boolean workPending;

   /*
    * Work sources: generic tasks and the publishers' queues
    */
   private final EndpointSupport.BoundedMpscSequencer taskQueue;
   private final Runnable[] tasks;
   private final Object publishersLock;
   private volatile AsyncROS2Publisher<?>[] asyncPublishers;

   public AsyncROS2Node(String name, int domainId, TransportDescriptorType... fastddsTransports)
   {
      this(name, ROS2NodeOptions.defaults().domainId(domainId).transports(fastddsTransports), null);
   }

   public AsyncROS2Node(String name, int domainId)
   {
      this(name, domainId, (TransportDescriptorType[]) null);
   }

   public AsyncROS2Node(String name)
   {
      this(name, ROS2NodeOptions.defaults(), null);
   }

   /**
    * @param name    The node name.
    * @param options Node options. Unset options fall back to the jros2 settings and ROS 2 defaults.
    */
   public AsyncROS2Node(String name, ROS2NodeOptions options)
   {
      this(name, options, null);
   }

   /**
    * @param name                 The node name.
    * @param options              Node options. Unset options fall back to the jros2 settings and ROS 2 defaults.
    * @param publishThreadFactory creates the publish thread, e.g. a real-time thread; null for a daemon thread named
    *                             {@code AsyncROS2NodePublishThread-<name>}. A non-daemon thread keeps the JVM alive until
    *                             the node is closed.
    */
   public AsyncROS2Node(String name, ROS2NodeOptions options, ThreadFactory publishThreadFactory)
   {
      super(name, options);

      running = true;
      rejectTasks = false;
      workPending = false;
      taskQueue = new EndpointSupport.BoundedMpscSequencer(TASK_QUEUE_CAPACITY);
      tasks = new Runnable[TASK_QUEUE_CAPACITY];
      publishersLock = new Object();
      asyncPublishers = NO_PUBLISHERS;

      // Pre-allocate thread name during construction to avoid allocation during runtime
      threadName = HotPathText.publishThreadName(name);

      Thread thread;
      try
      {
         if (publishThreadFactory == null)
         {
            thread = new Thread(this::publishLoop, threadName);
            thread.setDaemon(true); // Allow JVM to exit even if this thread is running
            thread.setPriority(Thread.NORM_PRIORITY + 1); // A hint only; ignored by Linux. Use a ThreadFactory for real-time settings.
         }
         else
         {
            thread = publishThreadFactory.newThread(this::publishLoop);
            if (thread == null)
            {
               throw new IllegalStateException(HotPathText.publishThreadFactoryReturnedNull(name));
            }
         }
         thread.start();
      }
      catch (RuntimeException | Error e)
      {
         running = false;
         rejectTasks = true;
         super.close();
         throw e;
      }
      publishThread = thread;
   }

   /**
    * Create an asynchronous publisher.
    *
    * @param queueCapacity the number of messages that can wait to be published before {@link AsyncROS2Publisher#publish}
    *                      starts dropping them; at least 1
    * @return the publisher, or null if the node is closed
    */
   public <T extends ROS2Message<T>> AsyncROS2Publisher<T> createPublisher(ROS2Topic<T> topic, ROS2QoSProfile qosProfile, int queueCapacity)
   {
      if (queueCapacity < 1)
      {
         throw new IllegalArgumentException(HotPathText.queueCapacityTooSmall(queueCapacity));
      }

      return (AsyncROS2Publisher<T>) createPublisherInternal(topic, qosProfile, queueCapacity);
   }

   @Override
   public <T extends ROS2Message<T>> AsyncROS2Publisher<T> createPublisher(ROS2Topic<T> topic, ROS2QoSProfile qosProfile)
   {
      return createPublisher(topic, qosProfile, DEFAULT_QUEUE_CAPACITY);
   }

   @Override
   public <T extends ROS2Message<T>> AsyncROS2Publisher<T> createPublisher(ROS2Topic<T> topic)
   {
      return createPublisher(topic, topic.getQoS());
   }

   @Override
   protected <T extends ROS2Message<T>> ROS2Publisher<T> newPublisher(long fastddsParticipant,
                                                                      String publisherProfileName,
                                                                      ROS2Topic<T> topic,
                                                                      TopicData topicData,
                                                                      int asyncQueueCapacity)
   {
      int queueCapacity = asyncQueueCapacity > 0 ? asyncQueueCapacity : DEFAULT_QUEUE_CAPACITY;
      AsyncROS2Publisher<T> publisher = new AsyncROS2Publisher<>(this, fastddsParticipant, publisherProfileName, topic, topicData, queueCapacity);
      registerPublisher(publisher);
      return publisher;
   }

   @Override
   public boolean isAsync()
   {
      return true;
   }

   /**
    * @return the thread that publishes the queued messages of this node's publishers
    */
   public Thread getPublishThread()
   {
      return publishThread;
   }

   /**
    * Stops the publish thread after it has written the messages that are already queued (waiting up to 2 seconds, then
    * interrupting it), and then closes the node like {@link ROS2Node#close()}. Even if the publish thread does not stop
    * in time, a write that is in flight finishes before its publisher's native resources are released.
    */
   @Override
   public void close()
   {
      if (publishersLock == null)
      {
         // Only possible if the JVM shutdown hook runs while the constructor is still running.
         super.close();
         return;
      }

      boolean stopThread;
      synchronized (publishersLock)
      {
         stopThread = running;
         running = false;
         rejectTasks = true;
      }

      Thread thread = publishThread;
      if (stopThread && thread != null)
      {
         LockSupport.unpark(thread);

         // Never wait for the publish thread from itself or from a callback (it may be blocked delivering to us).
         if (Thread.currentThread() != thread && !CallbackContext.isInCallback())
         {
            joinQuietly(thread, 2000);
            if (thread.isAlive())
            {
               thread.interrupt();
               joinQuietly(thread, 1000);
            }
            if (thread.isAlive())
            {
               jros2.getLogger().warning(HotPathText.publishThreadDidNotStop(threadName));
            }
         }
      }

      super.close();
   }

   private static void joinQuietly(Thread thread, long millis)
   {
      try
      {
         thread.join(millis);
      }
      catch (InterruptedException interruptedException)
      {
         Thread.currentThread().interrupt();
      }
   }

   /**
    * @return true once {@link #close()} has started: publishers stop accepting messages so that the publish thread can
    *       finish writing the queued ones
    */
   boolean isClosing()
   {
      return rejectTasks;
   }

   /**
    * Runs {@code task} on the publish thread, in order with other tasks. Allocation-free.
    *
    * @return false if the node is closed or the task queue is full
    */
   protected boolean addTask(Runnable task)
   {
      if (task == null || rejectTasks)
      {
         return false;
      }

      long position = taskQueue.claim();
      if (position < 0)
      {
         return false;
      }
      tasks[taskQueue.index(position)] = task;
      taskQueue.commit(position);
      signalWork();
      return true;
   }

   /**
    * Wakes the publish thread. Called after a message or task was committed.
    */
   void signalWork()
   {
      if (!workPending)
      {
         workPending = true;
         Thread thread = publishThread;
         if (thread != null)
         {
            LockSupport.unpark(thread);
         }
      }
   }

   void registerPublisher(AsyncROS2Publisher<?> publisher)
   {
      synchronized (publishersLock)
      {
         AsyncROS2Publisher<?>[] current = asyncPublishers;
         AsyncROS2Publisher<?>[] updated = Arrays.copyOf(current, current.length + 1);
         updated[current.length] = publisher;
         asyncPublishers = updated;
      }
   }

   void unregisterPublisher(AsyncROS2Publisher<?> publisher)
   {
      synchronized (publishersLock)
      {
         AsyncROS2Publisher<?>[] current = asyncPublishers;
         for (int i = 0; i < current.length; ++i)
         {
            if (current[i] == publisher)
            {
               AsyncROS2Publisher<?>[] updated = new AsyncROS2Publisher<?>[current.length - 1];
               System.arraycopy(current, 0, updated, 0, i);
               System.arraycopy(current, i + 1, updated, i, current.length - i - 1);
               asyncPublishers = updated;
               return;
            }
         }
      }
   }

   private void publishLoop()
   {
      // With intraprocess delivery, subscription callbacks run on this thread.
      CallbackContext.prepareThread();
      while (running)
      {
         // Cleared before looking for work: a producer that commits after this point sets it again and unparks us.
         workPending = false;
         boolean didWork = runOnce();
         if (!didWork && !workPending && running)
         {
            LockSupport.park(this);
            Thread.interrupted(); // A stray interrupt must not turn park() into a busy loop.
         }
      }

      // Write what was queued before close() (bounded by the queue capacities).
      while (runOnce())
      {
         if (Thread.currentThread().isInterrupted())
         {
            break;
         }
      }
   }

   /**
    * @return true if any task or message was processed
    */
   private boolean runOnce()
   {
      boolean didWork = false;

      long position;
      while ((position = taskQueue.peek()) >= 0)
      {
         int index = taskQueue.index(position);
         Runnable task = tasks[index];
         tasks[index] = null;
         taskQueue.release(position);
         didWork = true;
         try
         {
            task.run();
         }
         catch (Throwable throwable)
         {
            jros2.getLogger().log(Level.SEVERE, HotPathText.publishThreadTaskFailed(threadName), throwable);
         }
      }

      AsyncROS2Publisher<?>[] publishers = asyncPublishers;
      for (int i = 0; i < publishers.length; ++i)
      {
         try
         {
            if (publishers[i].drainQueue() > 0)
            {
               didWork = true;
            }
         }
         catch (Throwable throwable)
         {
            jros2.getLogger().log(Level.SEVERE, HotPathText.publishThreadPublishingFailed(threadName), throwable);
         }
      }

      return didWork;
   }
}
