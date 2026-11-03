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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.jros2.PubSubTestTools.*;

/**
 * Concurrency tests for {@link ROS2Publisher}: concurrent publishes, re-entrant publishes from callbacks, closing under load
 * and creation failures. Parameterized over the native ABI paths ({@code forceAbiV1=false} uses ABI v2 when the loaded
 * natives provide it).
 */
public class ROS2PublisherConcurrencyTest
{
   private static final int[] SIZES = {8, 300, 4000, 20000};

   /**
    * Four threads publish messages of very different sizes on one publisher. Before the write moved into the buffer's
    * critical section this crashed the JVM or produced mixed samples. The subscription validates every message.
    */
   @Test
   @Timeout(180)
   public void testConcurrentPublishFromFourThreadsWithDifferentSizesDefaultNativeAbi() throws Exception
   {
      testConcurrentPublishFromFourThreadsWithDifferentSizes(false);
   }

   @Test
   @Timeout(180)
   public void testConcurrentPublishFromFourThreadsWithDifferentSizesForcedNativeAbiV1() throws Exception
   {
      testConcurrentPublishFromFourThreadsWithDifferentSizes(true);
   }

   private void testConcurrentPublishFromFourThreadsWithDifferentSizes(boolean forceV1) throws Exception
   {
      final int threadCount = SIZES.length;
      final int messagesPerThread = 250;
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(uniqueTopicName("concurrent_publish"), std_msgs.String_.class);

      ROS2Node subscriberNode = new ROS2Node("concurrent_publish_sub");
      ROS2Node publisherNode = new ROS2Node("concurrent_publish_pub");
      try (AbiScope abi = abi(forceV1))
      {
         ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
         AtomicInteger received = new AtomicInteger();
         AtomicIntegerArray lastSequence = new AtomicIntegerArray(threadCount);
         for (int i = 0; i < threadCount; ++i)
         {
            lastSequence.set(i, -1);
         }
         std_msgs.String_ incoming = new std_msgs.String_();

         ROS2Subscription<std_msgs.String_> subscription = subscriberNode.createSubscription(topic, reader ->
         {
            if (!reader.read(incoming))
            {
               errors.add("read() returned false inside the callback");
               return;
            }
            String error = validate(incoming.getDataAsString(), lastSequence);
            if (error != null)
            {
               errors.add(error);
            }
            received.incrementAndGet();
         }, reliableKeepAll());
         abi.assertPath(subscription);

         ROS2Publisher<std_msgs.String_> publisher = publisherNode.createPublisher(topic, reliableKeepAll());
         abi.assertPath(publisher);
         awaitMatched(publisher, subscription);

         CyclicBarrier start = new CyclicBarrier(threadCount);
         List<Thread> threads = new ArrayList<>();
         AtomicReference<Throwable> publishFailure = new AtomicReference<>();
         for (int t = 0; t < threadCount; ++t)
         {
            final int threadIndex = t;
            Thread thread = new Thread(() ->
            {
               std_msgs.String_ message = new std_msgs.String_();
               try
               {
                  start.await();
                  for (int sequence = 0; sequence < messagesPerThread; ++sequence)
                  {
                     message.setData(content(threadIndex, sequence));
                     publisher.publish(message);
                  }
               }
               catch (Throwable e)
               {
                  publishFailure.compareAndSet(null, e);
               }
            }, "concurrent-publisher-" + t);
            threads.add(thread);
            thread.start();
         }
         for (Thread thread : threads)
         {
            thread.join();
         }
         assertNull(publishFailure.get(), () -> "publish threw: " + publishFailure.get());

         int expected = threadCount * messagesPerThread;
         awaitCondition(() -> received.get() >= expected || !errors.isEmpty(), 60_000, "received " + received.get() + " of " + expected);
         assertTrue(errors.isEmpty(), () -> errors.size() + " corrupted messages, first: " + errors.peek());
         assertEquals(expected, received.get());
      }
      finally
      {
         publisherNode.close();
         subscriberNode.close();
      }
   }

   private static String content(int threadIndex, int sequence)
   {
      String prefix = threadIndex + ":" + sequence + ":";
      StringBuilder builder = new StringBuilder(SIZES[threadIndex]);
      builder.append(prefix);
      char fill = (char) ('a' + threadIndex);
      while (builder.length() < SIZES[threadIndex])
      {
         builder.append(fill);
      }
      return builder.toString();
   }

   private static String validate(String data, AtomicIntegerArray lastSequence)
   {
      int firstColon = data.indexOf(':');
      int secondColon = firstColon < 0 ? -1 : data.indexOf(':', firstColon + 1);
      if (secondColon < 0)
      {
         return "no header in a " + data.length() + " character message";
      }
      int threadIndex;
      int sequence;
      try
      {
         threadIndex = Integer.parseInt(data.substring(0, firstColon));
         sequence = Integer.parseInt(data.substring(firstColon + 1, secondColon));
      }
      catch (NumberFormatException e)
      {
         return "bad header: " + data.substring(0, Math.min(20, data.length()));
      }
      if (threadIndex < 0 || threadIndex >= SIZES.length)
      {
         return "bad thread index " + threadIndex;
      }
      if (data.length() != SIZES[threadIndex])
      {
         return "thread " + threadIndex + " message " + sequence + " has length " + data.length() + ", expected " + SIZES[threadIndex];
      }
      char fill = (char) ('a' + threadIndex);
      for (int i = secondColon + 1; i < data.length(); ++i)
      {
         if (data.charAt(i) != fill)
         {
            return "thread " + threadIndex + " message " + sequence + " is mixed at index " + i;
         }
      }
      int previous = lastSequence.getAndSet(threadIndex, sequence);
      if (sequence != previous + 1)
      {
         return "thread " + threadIndex + " message " + sequence + " follows " + previous;
      }
      return null;
   }

   /**
    * A subscription callback publishes the next message on the same publisher. With intraprocess delivery the callback
    * runs inside the publisher's own {@code write()}, so this exercises the re-entrant path; without it, it exercises a
    * publish from a Fast-DDS thread. All messages must arrive in order and nothing may deadlock.
    */
   @Test
   @Timeout(120)
   public void testReentrantPublishFromCallbackOnTheSamePublisherDefaultNativeAbi()
   {
      testReentrantPublishFromCallbackOnTheSamePublisher(false);
   }

   @Test
   @Timeout(120)
   public void testReentrantPublishFromCallbackOnTheSamePublisherForcedNativeAbiV1()
   {
      testReentrantPublishFromCallbackOnTheSamePublisher(true);
   }

   private void testReentrantPublishFromCallbackOnTheSamePublisher(boolean forceV1)
   {
      final int chainLength = 200;
      ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("reentrant_publish"), example_interfaces.Int64.class);
      ROS2Node node = new ROS2Node("reentrant_publish");
      try (AbiScope abi = abi(forceV1))
      {
         AtomicReference<ROS2Publisher<example_interfaces.Int64>> publisherReference = new AtomicReference<>();
         List<Long> values = new ArrayList<>();
         AtomicBoolean callbackOnPublishingThread = new AtomicBoolean();
         Thread mainThread = Thread.currentThread();

         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(topic, reader ->
         {
            example_interfaces.Int64 incoming = new example_interfaces.Int64();
            if (!reader.read(incoming))
            {
               return;
            }
            if (Thread.currentThread() == mainThread)
            {
               callbackOnPublishingThread.set(true);
            }
            synchronized (values)
            {
               values.add(incoming.getData());
            }
            if (incoming.getData() < chainLength - 1)
            {
               example_interfaces.Int64 next = new example_interfaces.Int64();
               next.setData(incoming.getData() + 1);
               publisherReference.get().publish(next);
            }
         }, reliableKeepAll());
         ROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic, reliableKeepAll());
         publisherReference.set(publisher);
         abi.assertPath(publisher);
         awaitMatched(publisher, subscription);

         example_interfaces.Int64 first = new example_interfaces.Int64();
         first.setData(0);
         publisher.publish(first);

         awaitCondition(() ->
                        {
                           synchronized (values)
                           {
                              return values.size() >= chainLength;
                           }
                        }, 60_000, "re-entrant publish chain did not complete");
         synchronized (values)
         {
            assertEquals(chainLength, values.size());
            for (int i = 0; i < chainLength; ++i)
            {
               assertEquals(i, values.get(i).longValue());
            }
         }
         if (intraprocessDelivery())
         {
            assertTrue(callbackOnPublishingThread.get(), "with intraprocess delivery the callback runs inside publish()");
         }
      }
      finally
      {
         node.close();
      }
   }

   /**
    * Closing a publisher while several threads publish must not crash; publishes after close are ignored.
    */
   @Test
   @Timeout(60)
   public void testDestroyPublisherWhileThreadsPublishDefaultNativeAbi() throws Exception
   {
      testDestroyPublisherWhileThreadsPublish(false);
   }

   @Test
   @Timeout(60)
   public void testDestroyPublisherWhileThreadsPublishForcedNativeAbiV1() throws Exception
   {
      testDestroyPublisherWhileThreadsPublish(true);
   }

   private void testDestroyPublisherWhileThreadsPublish(boolean forceV1) throws Exception
   {
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(uniqueTopicName("destroy_while_publishing"), std_msgs.String_.class);
      ROS2Node node = new ROS2Node("destroy_while_publishing");
      try (AbiScope abi = abi(forceV1))
      {
         AtomicInteger received = new AtomicInteger();
         node.createSubscription(topic, reader ->
         {
            if (reader.read() != null)
            {
               received.incrementAndGet();
            }
         }, reliableKeepLast(10));
         ROS2Publisher<std_msgs.String_> publisher = node.createPublisher(topic, reliableKeepLast(10));
         abi.assertPath(publisher);

         AtomicBoolean running = new AtomicBoolean(true);
         AtomicReference<Throwable> failure = new AtomicReference<>();
         List<Thread> threads = new ArrayList<>();
         for (int t = 0; t < 4; ++t)
         {
            final int threadIndex = t;
            Thread thread = new Thread(() ->
            {
               std_msgs.String_ message = new std_msgs.String_();
               int sequence = 0;
               try
               {
                  while (running.get())
                  {
                     message.setData(content(threadIndex, sequence++));
                     publisher.publish(message);
                  }
               }
               catch (Throwable e)
               {
                  failure.compareAndSet(null, e);
               }
            }, "destroy-while-publishing-" + t);
            threads.add(thread);
            thread.start();
         }

         awaitCondition(() -> received.get() > 10, 30_000, "nothing received before destroy");
         assertTrue(node.destroyPublisher(publisher));
         assertTrue(publisher.isClosed());
         assertTrue(publisher.isNativeReleased(), "destroy from a non-callback thread releases the natives synchronously");
         assertTrue(publisher.isListenerTrampolineCleared());
         assertEquals(0, publisher.getPublicationMatchedStatus());

         Thread.sleep(100); // publishes on the closed publisher are ignored
         running.set(false);
         for (Thread thread : threads)
         {
            thread.join();
         }
         assertNull(failure.get(), () -> "publish threw: " + failure.get());
         assertFalse(node.destroyPublisher(publisher), "already destroyed");
         assertNotNull(publisher.getGuid(), "the GUID stays available after close");
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(60)
   public void testPublisherCreationFailureThrowsAndReleasesPartialResources()
   {
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(uniqueTopicName("publisher_creation_failure"), example_interfaces.Bool.class);
      ROS2Node node = new ROS2Node("publisher_creation_failure");
      try
      {
         ROS2Publisher<example_interfaces.Bool> valid = node.createPublisher(topic, reliableKeepLast(1));
         TopicData topicData = valid.getTopicData();

         // Unknown profile: Fast-DDS returns no Publisher
         IllegalStateException unknownProfile = assertThrows(IllegalStateException.class,
                                                             () -> new ROS2Publisher<>(node.fastddsParticipant, "jros2_no_such_profile", topic, topicData));
         assertTrue(unknownProfile.getMessage().contains(topic.getName()), unknownProfile.getMessage());

         // Missing topic: rejected before any native allocation
         TopicData missingTopic = new TopicData(topicData.fastddsTopicDataWrapperType, topicData.fastddsTypeSupport, 0);
         assertThrows(IllegalStateException.class, () -> new ROS2Publisher<>(node.fastddsParticipant, "jros2_no_such_profile", topic, missingTopic));

         // The node keeps working
         ROS2Publisher<example_interfaces.Bool> another = node.createPublisher(topic, reliableKeepLast(1));
         example_interfaces.Bool message = new example_interfaces.Bool();
         another.publish(message);
         valid.publish(message);
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(60)
   public void testPublisherStatisticsAreOptIn()
   {
      ROS2Topic<geometry_msgs.Point> topic = new ROS2Topic<>(uniqueTopicName("publisher_statistics"), geometry_msgs.Point.class);
      ROS2Node node = new ROS2Node("publisher_statistics");
      try
      {
         ROS2Publisher<geometry_msgs.Point> publisher = node.createPublisher(topic, reliableKeepLast(1));
         geometry_msgs.Point point = new geometry_msgs.Point();
         Statistics statistics = new Statistics();

         assertFalse(publisher.isStatisticsEnabled());
         publisher.publish(point);
         publisher.readMessageSizeStatistics(statistics);
         assertEquals(0, statistics.get(Statistics.StatisticDataType.SAMPLE_COUNT), "disabled by default");

         publisher.setStatisticsEnabled(true);
         publisher.publish(point);
         publisher.publish(point);
         publisher.readMessageSizeStatistics(statistics);
         assertEquals(2, statistics.get(Statistics.StatisticDataType.SAMPLE_COUNT));
         assertEquals(4 + point.calculateSizeBytes(0), statistics.get(Statistics.StatisticDataType.LATEST), 1e-9);
         publisher.readMessagePeriodStatistics(statistics);
         assertEquals(1, statistics.get(Statistics.StatisticDataType.SAMPLE_COUNT));

         publisher.setStatisticsEnabled(false);
         publisher.publish(point);
         publisher.readMessageSizeStatistics(statistics);
         assertEquals(2, statistics.get(Statistics.StatisticDataType.SAMPLE_COUNT), "disabling stops recording and keeps the values");

         publisher.resetStatistics();
         publisher.readMessageSizeStatistics(statistics);
         assertEquals(0, statistics.get(Statistics.StatisticDataType.SAMPLE_COUNT));
      }
      finally
      {
         node.close();
      }
   }
}
