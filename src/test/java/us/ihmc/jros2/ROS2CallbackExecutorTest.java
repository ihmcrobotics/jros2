/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.jros2.PubSubTestTools.*;

/**
 * Subscription callbacks dispatched on a user-provided {@link Executor} (see {@link ROS2NodeOptions#callbackExecutor}).
 */
public class ROS2CallbackExecutorTest
{
   private static ThreadFactory namedThreads(String prefix)
   {
      AtomicInteger counter = new AtomicInteger();
      return runnable ->
      {
         Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
         thread.setDaemon(true);
         return thread;
      };
   }

   private static example_interfaces.Int64 int64(long value)
   {
      example_interfaces.Int64 message = new example_interfaces.Int64();
      message.setData(value);
      return message;
   }

   @Test
   @Timeout(90)
   public void testCallbacksRunOnTheExecutorThread()
   {
      ExecutorService executor = Executors.newSingleThreadExecutor(namedThreads("jros2-test-executor-"));
      ROS2Node node = new ROS2Node("executor_thread", ROS2NodeOptions.defaults().callbackExecutor(executor));
      ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("executor_thread"), example_interfaces.Int64.class);
      try
      {
         ConcurrentLinkedQueue<String> threadNames = new ConcurrentLinkedQueue<>();
         AtomicBoolean inCallbackContext = new AtomicBoolean(true);
         List<Long> values = new ArrayList<>();
         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(topic, reader ->
         {
            threadNames.add(Thread.currentThread().getName());
            if (!CallbackContext.isInCallback())
            {
               inCallbackContext.set(false);
            }
            example_interfaces.Int64 data = reader.read();
            synchronized (values)
            {
               values.add(data == null ? -1 : data.getData());
            }
         }, reliableKeepAll());
         ROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic, reliableKeepAll());
         awaitMatched(publisher, subscription);

         for (int i = 0; i < 50; ++i)
         {
            publisher.publish(int64(i));
         }
         awaitCondition(() ->
                        {
                           synchronized (values)
                           {
                              return values.size() >= 50;
                           }
                        }, 30_000, "callbacks");
         synchronized (values)
         {
            for (int i = 0; i < 50; ++i)
            {
               assertEquals(i, values.get(i).longValue());
            }
         }
         for (String name : threadNames)
         {
            assertTrue(name.startsWith("jros2-test-executor-"), "callback ran on " + name);
         }
         assertTrue(inCallbackContext.get(), "executor callbacks run inside the jros2 callback context");
      }
      finally
      {
         node.close();
         executor.shutdownNow();
      }
   }

   /**
    * With a two-thread executor, a callback that blocks on topic A must not delay the callbacks of topic B. Inline dispatch
    * would block the Fast-DDS thread that delivers both topics.
    */
   @Test
   @Timeout(90)
   public void testSlowCallbackOnOneTopicDoesNotDelayAnother() throws Exception
   {
      ExecutorService executor = Executors.newFixedThreadPool(2, namedThreads("jros2-test-pool-"));
      ROS2Node node = new ROS2Node("executor_isolation", ROS2NodeOptions.defaults().callbackExecutor(executor));
      ROS2Topic<example_interfaces.Int64> slowTopic = new ROS2Topic<>(uniqueTopicName("executor_slow"), example_interfaces.Int64.class);
      ROS2Topic<example_interfaces.Int64> fastTopic = new ROS2Topic<>(uniqueTopicName("executor_fast"), example_interfaces.Int64.class);
      CountDownLatch releaseSlow = new CountDownLatch(1);
      try
      {
         CountDownLatch slowCallbackRunning = new CountDownLatch(1);
         AtomicInteger slowCallbacksFinished = new AtomicInteger();
         ROS2Subscription<example_interfaces.Int64> slowSubscription = node.createSubscription(slowTopic, reader ->
         {
            reader.read();
            slowCallbackRunning.countDown();
            try
            {
               releaseSlow.await(60, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
            slowCallbacksFinished.incrementAndGet();
         }, reliableKeepAll());

         AtomicInteger fastReceived = new AtomicInteger();
         ROS2Subscription<example_interfaces.Int64> fastSubscription = node.createSubscription(fastTopic, reader ->
         {
            if (reader.read() != null)
            {
               fastReceived.incrementAndGet();
            }
         }, reliableKeepAll());

         ROS2Publisher<example_interfaces.Int64> slowPublisher = node.createPublisher(slowTopic, reliableKeepAll());
         ROS2Publisher<example_interfaces.Int64> fastPublisher = node.createPublisher(fastTopic, reliableKeepAll());
         awaitMatched(slowPublisher, slowSubscription);
         awaitMatched(fastPublisher, fastSubscription);

         slowPublisher.publish(int64(1));
         assertTrue(slowCallbackRunning.await(30, TimeUnit.SECONDS));
         slowPublisher.publish(int64(2)); // queued behind the blocked callback

         long start = System.nanoTime();
         for (int i = 0; i < 20; ++i)
         {
            fastPublisher.publish(int64(i));
         }
         awaitCondition(() -> fastReceived.get() == 20, 20_000, "fast topic was delayed by the slow callback");
         long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
         assertEquals(0, slowCallbacksFinished.get(), "the slow callback is still blocked while the fast topic is served");
         assertTrue(elapsedMs < 20_000, "fast topic took " + elapsedMs + " ms");

         releaseSlow.countDown();
         awaitCondition(() -> slowCallbacksFinished.get() == 2, 30_000, "the queued slow sample is delivered after the release");
      }
      finally
      {
         releaseSlow.countDown();
         node.close();
         executor.shutdownNow();
      }
   }

   /**
    * A multi-threaded executor must still run the callbacks of one subscription one at a time, in order, each sample
    * exactly once.
    */
   @Test
   @Timeout(90)
   public void testCallbacksOfOneSubscriptionAreSerializedOnAThreadPool()
   {
      ExecutorService executor = Executors.newFixedThreadPool(4, namedThreads("jros2-test-serial-"));
      ROS2Node node = new ROS2Node("executor_serialized", ROS2NodeOptions.defaults().callbackExecutor(executor));
      ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("executor_serialized"), example_interfaces.Int64.class);
      try
      {
         AtomicInteger concurrent = new AtomicInteger();
         AtomicInteger maxConcurrent = new AtomicInteger();
         AtomicLong expectedNext = new AtomicLong();
         AtomicInteger outOfOrder = new AtomicInteger();
         AtomicInteger received = new AtomicInteger();
         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(topic, reader ->
         {
            int now = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            example_interfaces.Int64 data = reader.read();
            if (data == null || data.getData() != expectedNext.getAndIncrement())
            {
               outOfOrder.incrementAndGet();
            }
            received.incrementAndGet();
            concurrent.decrementAndGet();
         }, reliableKeepAll());
         ROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic, reliableKeepAll());
         awaitMatched(publisher, subscription);

         final int count = 2000;
         for (int i = 0; i < count; ++i)
         {
            publisher.publish(int64(i));
         }
         awaitCondition(() -> received.get() >= count, 60_000, "received " + received.get());
         waitFor(() -> false, 100);
         assertEquals(count, received.get());
         assertEquals(0, outOfOrder.get());
         assertEquals(1, maxConcurrent.get(), "callbacks of one subscription overlapped");
      }
      finally
      {
         node.close();
         executor.shutdownNow();
      }
   }

   /**
    * An executor that rejects tasks loses no samples for good: the next notification schedules a drain that takes them.
    */
   @Test
   @Timeout(90)
   public void testRejectingExecutorRecoversOnTheNextNotification()
   {
      ExecutorService delegate = Executors.newSingleThreadExecutor(namedThreads("jros2-test-rejecting-"));
      AtomicBoolean reject = new AtomicBoolean(true);
      AtomicInteger rejected = new AtomicInteger();
      Executor executor = task ->
      {
         if (reject.get())
         {
            rejected.incrementAndGet();
            throw new RejectedExecutionException("test");
         }
         delegate.execute(task);
      };
      ROS2Node node = new ROS2Node("executor_rejecting", ROS2NodeOptions.defaults().callbackExecutor(executor));
      ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("executor_rejecting"), example_interfaces.Int64.class);
      try
      {
         List<Long> values = new ArrayList<>();
         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(topic, reader ->
         {
            example_interfaces.Int64 data;
            while ((data = reader.read()) != null)
            {
               synchronized (values)
               {
                  values.add(data.getData());
               }
            }
         }, reliableKeepAll());
         ROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic, reliableKeepAll());
         awaitMatched(publisher, subscription);

         publisher.publish(int64(1));
         awaitCondition(() -> rejected.get() > 0 && subscription.getUnreadMessageCount() == 1, 30_000, "rejected drain");
         reject.set(false);
         publisher.publish(int64(2));
         awaitCondition(() ->
                        {
                           synchronized (values)
                           {
                              return values.size() >= 2;
                           }
                        }, 30_000, "samples after recovery");
         synchronized (values)
         {
            assertEquals(java.util.Arrays.asList(1L, 2L), values);
         }
      }
      finally
      {
         node.close();
         delegate.shutdownNow();
      }
   }

   /**
    * On a single-thread executor shared by two subscriptions, a subscription with a large backlog runs its callbacks in
    * batches and hands the thread back in between, so another subscription's sample does not wait for the whole backlog.
    */
   @Test
   @Timeout(120)
   public void testBusySubscriptionTakesTurnsOnASharedSingleThreadExecutor() throws Exception
   {
      ExecutorService executor = Executors.newSingleThreadExecutor(namedThreads("jros2-test-shared-"));
      ROS2Node node = new ROS2Node("executor_fairness", ROS2NodeOptions.defaults().callbackExecutor(executor));
      ROS2Topic<example_interfaces.Int64> busyTopic = new ROS2Topic<>(uniqueTopicName("executor_busy"), example_interfaces.Int64.class);
      ROS2Topic<example_interfaces.Int64> otherTopic = new ROS2Topic<>(uniqueTopicName("executor_other"), example_interfaces.Int64.class);
      CountDownLatch releaseExecutor = new CountDownLatch(1);
      try
      {
         final int backlog = 5 * ROS2Subscription.EXECUTOR_DRAIN_BATCH;
         AtomicInteger busyReceived = new AtomicInteger();
         AtomicInteger busyReceivedWhenOtherRan = new AtomicInteger(-1);
         AtomicLong expectedNext = new AtomicLong();
         AtomicInteger outOfOrder = new AtomicInteger();
         ROS2Subscription<example_interfaces.Int64> busySubscription = node.createSubscription(busyTopic, reader ->
         {
            example_interfaces.Int64 data = reader.read();
            if (data == null || data.getData() != expectedNext.getAndIncrement())
            {
               outOfOrder.incrementAndGet();
            }
            busyReceived.incrementAndGet();
         }, reliableKeepAll());
         ROS2Subscription<example_interfaces.Int64> otherSubscription = node.createSubscription(otherTopic, reader ->
         {
            if (reader.read() != null)
            {
               busyReceivedWhenOtherRan.compareAndSet(-1, busyReceived.get());
            }
         }, reliableKeepAll());
         ROS2Publisher<example_interfaces.Int64> busyPublisher = node.createPublisher(busyTopic, reliableKeepAll());
         ROS2Publisher<example_interfaces.Int64> otherPublisher = node.createPublisher(otherTopic, reliableKeepAll());
         awaitMatched(busyPublisher, busySubscription);
         awaitMatched(otherPublisher, otherSubscription);

         // Occupy the executor so that both backlogs build up before any drain runs.
         CountDownLatch executorBlocked = new CountDownLatch(1);
         executor.execute(() ->
         {
            executorBlocked.countDown();
            try
            {
               releaseExecutor.await(60, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
         });
         assertTrue(executorBlocked.await(30, TimeUnit.SECONDS));

         for (int i = 0; i < backlog; ++i)
         {
            busyPublisher.publish(int64(i));
         }
         awaitCondition(() -> busySubscription.getUnreadMessageCount() == backlog, 30_000, "busy backlog not received");
         otherPublisher.publish(int64(1));
         awaitCondition(() -> otherSubscription.getUnreadMessageCount() == 1, 30_000, "other sample not received");

         releaseExecutor.countDown();
         awaitCondition(() -> busyReceived.get() == backlog && busyReceivedWhenOtherRan.get() >= 0, 60_000, "callbacks did not complete");
         assertEquals(0, outOfOrder.get());
         assertTrue(busyReceivedWhenOtherRan.get() < backlog,
                    "the other subscription waited for the whole backlog (" + busyReceivedWhenOtherRan.get() + " of " + backlog + ")");
         assertEquals(ROS2Subscription.EXECUTOR_DRAIN_BATCH, busyReceivedWhenOtherRan.get(), "the busy subscription yields after one batch");
      }
      finally
      {
         releaseExecutor.countDown();
         node.close();
         executor.shutdownNow();
      }
   }
}
