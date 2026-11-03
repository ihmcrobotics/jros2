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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.jros2.PubSubTestTools.*;

/**
 * Queueing, drop accounting, threading and shutdown of {@link AsyncROS2Publisher} / {@link AsyncROS2Node}.
 */
public class AsyncROS2PublisherTest
{
   private static example_interfaces.Int64 int64(long value)
   {
      example_interfaces.Int64 message = new example_interfaces.Int64();
      message.setData(value);
      return message;
   }

   /** Blocks the node's publish thread until the returned latch is released. */
   private static CountDownLatch blockPublishThread(AsyncROS2Node node) throws InterruptedException
   {
      CountDownLatch running = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      assertTrue(node.addTask(() ->
                              {
                                 running.countDown();
                                 try
                                 {
                                    release.await(60, TimeUnit.SECONDS);
                                 }
                                 catch (InterruptedException e)
                                 {
                                    Thread.currentThread().interrupt();
                                 }
                              }));
      assertTrue(running.await(30, TimeUnit.SECONDS), "publish thread did not run the task");
      return release;
   }

   @Test
   @Timeout(90)
   public void testDropsAreCountedExactlyAndQueuedMessagesArePublishedInOrderDefaultNativeAbi() throws Exception
   {
      testDropsAreCountedExactlyAndQueuedMessagesArePublishedInOrder(false);
   }

   @Test
   @Timeout(90)
   public void testDropsAreCountedExactlyAndQueuedMessagesArePublishedInOrderForcedNativeAbiV1() throws Exception
   {
      testDropsAreCountedExactlyAndQueuedMessagesArePublishedInOrder(true);
   }

   private void testDropsAreCountedExactlyAndQueuedMessagesArePublishedInOrder(boolean forceV1) throws Exception
   {
      AsyncROS2Node node = new AsyncROS2Node("async_drop_count");
      ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("async_drop_count"), example_interfaces.Int64.class);
      try (AbiScope abi = abi(forceV1))
      {
         List<Long> values = new ArrayList<>();
         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(topic, reader ->
         {
            example_interfaces.Int64 data = reader.read();
            synchronized (values)
            {
               values.add(data.getData());
            }
         }, reliableKeepAll());
         AsyncROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic, reliableKeepAll(), 8);
         abi.assertPath(publisher);
         assertEquals(8, publisher.getQueueCapacity());
         awaitMatched(publisher, subscription);

         CountDownLatch release = blockPublishThread(node);
         int accepted = 0;
         for (int i = 0; i < 20; ++i)
         {
            if (publisher.offer(int64(i)))
            {
               accepted++;
            }
         }
         assertEquals(8, accepted);
         assertEquals(12, publisher.getDroppedMessageCount());
         assertEquals(8, publisher.getQueuedMessageCount());
         publisher.publish(int64(99)); // void variant drops and counts as well
         assertEquals(13, publisher.getDroppedMessageCount());

         release.countDown();
         awaitCondition(() ->
                        {
                           synchronized (values)
                           {
                              return values.size() >= 8;
                           }
                        }, 30_000, "queued messages not published");
         waitFor(() -> false, 200);
         synchronized (values)
         {
            assertEquals(java.util.Arrays.asList(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L), values);
         }
         assertEquals(0, publisher.getQueuedMessageCount());
      }
      finally
      {
         node.close();
      }
   }

   private static geometry_msgs.Point point(long value)
   {
      geometry_msgs.Point point = new geometry_msgs.Point();
      point.setX(value);
      point.setY(2.0 * value);
      point.setZ(-value);
      return point;
   }

   /**
    * One or several producers publish as fast as they can into a small queue. Every published message must be consistent
    * (a torn copy would mix two messages), arrive at most once and in per-producer order, and received + dropped must
    * equal offered.
    */
   @Test
   @Timeout(180)
   public void testNoTornOrDuplicateMessagesAndExactAccountingSingleProducer() throws Exception
   {
      testNoTornOrDuplicateMessagesAndExactAccounting(1);
   }

   @Test
   @Timeout(180)
   public void testNoTornOrDuplicateMessagesAndExactAccountingFourProducers() throws Exception
   {
      testNoTornOrDuplicateMessagesAndExactAccounting(4);
   }

   private void testNoTornOrDuplicateMessagesAndExactAccounting(int producers) throws Exception
   {
      final int perProducer = producers == 1 ? 5000 : 2000;
      final long producerStride = 1_000_000L;
      AsyncROS2Node node = new AsyncROS2Node("async_torn_" + producers);
      ROS2Topic<geometry_msgs.Point> topic = new ROS2Topic<>(uniqueTopicName("async_torn"), geometry_msgs.Point.class);
      try
      {
         ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
         AtomicInteger received = new AtomicInteger();
         AtomicLongArray lastValue = new AtomicLongArray(producers);
         for (int p = 0; p < producers; ++p)
         {
            lastValue.set(p, -1);
         }
         geometry_msgs.Point incoming = new geometry_msgs.Point();
         ROS2Subscription<geometry_msgs.Point> subscription = node.createSubscription(topic, reader ->
         {
            if (!reader.read(incoming))
            {
               errors.add("read returned false");
               return;
            }
            long value = (long) incoming.getX();
            if (incoming.getY() != 2.0 * value || incoming.getZ() != -value)
            {
               errors.add("torn message: " + incoming.getX() + ", " + incoming.getY() + ", " + incoming.getZ());
            }
            int producer = (int) (value / producerStride);
            long sequence = value % producerStride;
            if (producer < 0 || producer >= lastValue.length())
            {
               errors.add("unknown producer in " + value);
            }
            else if (sequence <= lastValue.getAndSet(producer, sequence))
            {
               errors.add("duplicate or reordered message " + value);
            }
            received.incrementAndGet();
         }, reliableKeepAll());
         AsyncROS2Publisher<geometry_msgs.Point> publisher = node.createPublisher(topic, reliableKeepAll(), 16);
         awaitMatched(publisher, subscription);

         CyclicBarrier start = new CyclicBarrier(producers);
         List<Thread> threads = new ArrayList<>();
         AtomicReference<Throwable> failure = new AtomicReference<>();
         for (int p = 0; p < producers; ++p)
         {
            final long base = p * producerStride;
            Thread thread = new Thread(() ->
            {
               geometry_msgs.Point message = new geometry_msgs.Point();
               try
               {
                  start.await();
                  for (long i = 0; i < perProducer; ++i)
                  {
                     long value = base + i;
                     message.setX(value);
                     message.setY(2.0 * value);
                     message.setZ(-value);
                     publisher.publish(message); // copied: the message is reused immediately
                  }
               }
               catch (Throwable e)
               {
                  failure.compareAndSet(null, e);
               }
            }, "async-producer-" + p);
            threads.add(thread);
            thread.start();
         }
         for (Thread thread : threads)
         {
            thread.join();
         }
         assertNull(failure.get(), () -> "producer failed: " + failure.get());

         long total = (long) producers * perProducer;
         awaitCondition(() -> received.get() + publisher.getDroppedMessageCount() >= total || !errors.isEmpty(),
                        60_000,
                        "received " + received.get() + " + dropped " + publisher.getDroppedMessageCount() + " of " + total);
         waitFor(() -> false, 200);
         assertTrue(errors.isEmpty(), () -> errors.size() + " errors, first: " + errors.peek());
         assertEquals(total, received.get() + publisher.getDroppedMessageCount());
         assertTrue(received.get() > 0);
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(60)
   public void testOfferAfterDestroyReturnsFalseAndOtherPublishersKeepWorking() throws Exception
   {
      AsyncROS2Node node = new AsyncROS2Node("async_destroy");
      ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("async_destroy"), example_interfaces.Int64.class);
      try
      {
         AtomicInteger received = new AtomicInteger();
         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(topic, reader ->
         {
            if (reader.read() != null)
            {
               received.incrementAndGet();
            }
         }, reliableKeepAll());
         AsyncROS2Publisher<example_interfaces.Int64> doomed = node.createPublisher(topic, reliableKeepAll(), 32);
         AsyncROS2Publisher<example_interfaces.Int64> survivor = node.createPublisher(topic, reliableKeepAll(), 32);
         awaitMatched(doomed, subscription);
         awaitMatched(survivor, subscription);

         AtomicBoolean running = new AtomicBoolean(true);
         AtomicReference<Throwable> failure = new AtomicReference<>();
         Thread producer = new Thread(() ->
         {
            example_interfaces.Int64 message = int64(1);
            try
            {
               while (running.get())
               {
                  doomed.publish(message);
                  Thread.yield();
               }
            }
            catch (Throwable e)
            {
               failure.compareAndSet(null, e);
            }
         }, "async-destroy-producer");
         producer.start();
         awaitCondition(() -> received.get() > 20, 30_000, "nothing published before destroy");

         assertTrue(node.destroyPublisher(doomed));
         assertTrue(doomed.isClosed());
         assertTrue(doomed.isNativeReleased());
         long droppedBefore = doomed.getDroppedMessageCount();
         assertFalse(doomed.offer(int64(2)), "closed publishers reject offers");
         assertEquals(droppedBefore, doomed.getDroppedMessageCount(), "rejections after close are not drops");
         running.set(false);
         producer.join();
         assertNull(failure.get(), () -> "publish threw: " + failure.get());

         int before = received.get();
         for (int i = 0; i < 10; ++i)
         {
            assertTrue(survivor.offer(int64(i)));
         }
         awaitCondition(() -> received.get() >= before + 10, 30_000, "the other publisher stopped working");
      }
      finally
      {
         node.close();
      }
   }

   /**
    * close() lets the publish thread write what is already queued before the publishers are destroyed.
    */
   @Test
   @Timeout(60)
   public void testCloseWritesQueuedMessagesFirst() throws Exception
   {
      AsyncROS2Node node = new AsyncROS2Node("async_close_drain");
      ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("async_close_drain"), example_interfaces.Int64.class);
      AsyncROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic, reliableKeepAll(), 32);
      publisher.setStatisticsEnabled(true);

      CountDownLatch release = blockPublishThread(node);
      for (int i = 0; i < 10; ++i)
      {
         assertTrue(publisher.offer(int64(i)));
      }
      Thread releaser = new Thread(() ->
      {
         try
         {
            Thread.sleep(200);
         }
         catch (InterruptedException e)
         {
            Thread.currentThread().interrupt();
         }
         release.countDown();
      });
      releaser.start();
      node.close();
      releaser.join();

      Statistics statistics = new Statistics();
      publisher.readMessageSizeStatistics(statistics);
      assertEquals(10, statistics.get(Statistics.StatisticDataType.SAMPLE_COUNT), "queued messages were written before close");
      assertTrue(publisher.isClosed());
      assertTrue(publisher.isNativeReleased());
      assertFalse(node.getPublishThread().isAlive());
      assertFalse(publisher.offer(int64(1)));
      assertFalse(node.addTask(() -> { }), "no tasks after close");
   }

   /**
    * If the publish thread does not stop in time (here: blocked in a task that ignores interrupts), close() interrupts it,
    * gives up waiting and still closes safely.
    */
   @Test
   @Timeout(60)
   public void testCloseIsSafeWhenThePublishThreadIsStuck() throws Exception
   {
      AsyncROS2Node node = new AsyncROS2Node("async_close_stuck");
      ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("async_close_stuck"), example_interfaces.Int64.class);
      AsyncROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic, reliableKeepAll(), 8);

      CountDownLatch stuck = new CountDownLatch(1);
      AtomicInteger interrupts = new AtomicInteger();
      assertTrue(node.addTask(() ->
                              {
                                 stuck.countDown();
                                 long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                                 long remaining;
                                 while ((remaining = deadline - System.nanoTime()) > 0)
                                 {
                                    try
                                    {
                                       Thread.sleep(TimeUnit.NANOSECONDS.toMillis(remaining) + 1);
                                    }
                                    catch (InterruptedException e)
                                    {
                                       interrupts.incrementAndGet();
                                    }
                                 }
                              }));
      assertTrue(stuck.await(30, TimeUnit.SECONDS));
      for (int i = 0; i < 5; ++i)
      {
         assertTrue(publisher.offer(int64(i)));
      }

      long start = System.nanoTime();
      node.close();
      long closeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      assertTrue(closeMs >= 2_500, "close waited " + closeMs + " ms for the publish thread");
      assertTrue(interrupts.get() > 0, "close interrupted the stuck publish thread");
      assertTrue(publisher.isClosed());
      assertTrue(publisher.isNativeReleased());

      // The thread wakes up later, finds closed publishers and exits without touching freed natives.
      node.getPublishThread().join(30_000);
      assertFalse(node.getPublishThread().isAlive());
   }

   @Test
   @Timeout(60)
   public void testPublishThreadComesFromTheThreadFactory()
   {
      AtomicInteger created = new AtomicInteger();
      ThreadFactory factory = runnable ->
      {
         created.incrementAndGet();
         Thread thread = new Thread(runnable, "jros2-test-rt-publisher");
         thread.setDaemon(true);
         return thread;
      };
      AsyncROS2Node node = new AsyncROS2Node("async_thread_factory", ROS2NodeOptions.defaults(), factory);
      ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("async_thread_factory"), example_interfaces.Int64.class);
      try
      {
         assertEquals(1, created.get());
         assertEquals("jros2-test-rt-publisher", node.getPublishThread().getName());
         assertTrue(node.isAsync());

         AtomicReference<String> deliveryThread = new AtomicReference<>();
         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(topic, reader ->
         {
            reader.read();
            deliveryThread.compareAndSet(null, Thread.currentThread().getName());
         }, reliableKeepAll());
         AsyncROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic, reliableKeepAll());
         assertEquals(AsyncROS2Node.DEFAULT_QUEUE_CAPACITY, publisher.getQueueCapacity());
         awaitMatched(publisher, subscription);
         publisher.publish(int64(1));
         awaitCondition(() -> deliveryThread.get() != null, 30_000, "message not delivered");
         if (intraprocessDelivery())
         {
            assertEquals("jros2-test-rt-publisher", deliveryThread.get(), "intraprocess delivery runs on the publish thread");
         }
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(60)
   public void testConstructorAndCreationValidation()
   {
      int domainId = jros2.get().rosDomainId();
      AsyncROS2Node node = new AsyncROS2Node("async_validation", ROS2NodeOptions.defaults().domainId(domainId));
      ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("async_validation"), example_interfaces.Int64.class);
      try
      {
         assertEquals(domainId, node.getDomainId());
         assertThrows(IllegalArgumentException.class, () -> node.createPublisher(topic, reliableKeepAll(), 0));
         ROS2Publisher<example_interfaces.Int64> viaBaseSignature = node.createPublisher(topic);
         assertTrue(viaBaseSignature instanceof AsyncROS2Publisher);
         assertEquals(1, node.createPublisher(topic, reliableKeepAll(), 1).getQueueCapacity());
      }
      finally
      {
         node.close();
      }

      assertThrows(IllegalStateException.class, () -> new AsyncROS2Node("async_null_thread", ROS2NodeOptions.defaults(), runnable -> null));
   }

   /**
    * The queue is allocated after the DataWriter exists. If that fails (here: a capacity no array can hold), the writer
    * must be deleted again; a leaked writer stays matched to subscriptions and keeps its participant alive.
    */
   @Test
   @Timeout(60)
   public void testQueueAllocationFailureReleasesTheDataWriter() throws Exception
   {
      AsyncROS2Node node = new AsyncROS2Node("async_queue_allocation_failure");
      ROS2Node subscriberNode = new ROS2Node("async_queue_allocation_failure_sub");
      ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("async_queue_allocation_failure"), example_interfaces.Int64.class);
      try
      {
         ROS2Subscription<example_interfaces.Int64> subscription = subscriberNode.createSubscription(topic, reliableKeepAll());
         assertThrows(OutOfMemoryError.class, () -> node.createPublisher(topic, reliableKeepAll(), Integer.MAX_VALUE));
         assertTrue(node.getPublishers().isEmpty());

         AsyncROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic, reliableKeepAll(), 4);
         awaitMatched(publisher, subscription);
         // The failed writer was created first, so it would have been discovered by now.
         Thread.sleep(500);
         assertEquals(1, subscription.getSubscriptionMatchedStatus(), "the writer of the failed publisher was not deleted");

         assertTrue(publisher.offer(new example_interfaces.Int64()));
         awaitCondition(() -> subscription.getUnreadMessageCount() == 1, 30_000, "the node keeps working");
      }
      finally
      {
         node.close();
         subscriberNode.close();
      }
   }
}
