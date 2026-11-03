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
import us.ihmc.fastddsjava.cdr.CDRBuffer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.jros2.PubSubTestTools.*;

/**
 * Reading semantics of {@link ROS2Subscription} in polling and callback mode. Parameterized over the native ABI paths
 * ({@code forceAbiV1=false} uses ABI v2 when the loaded natives provide it).
 */
public class ROS2SubscriptionSemanticsTest
{
   private static example_interfaces.Int64 int64(long value)
   {
      example_interfaces.Int64 message = new example_interfaces.Int64();
      message.setData(value);
      return message;
   }

   /**
    * When the last publisher goes away Fast-DDS queues a sample without data. Polling must skip it: before the fix read()
    * returned true again with the previous payload.
    */
   @Test
   @Timeout(90)
   public void testPollingReadAfterPublisherGoneReturnsNoStaleSampleDefaultNativeAbi()
   {
      testPollingReadAfterPublisherGoneReturnsNoStaleSample(false);
   }

   @Test
   @Timeout(90)
   public void testPollingReadAfterPublisherGoneReturnsNoStaleSampleForcedNativeAbiV1()
   {
      testPollingReadAfterPublisherGoneReturnsNoStaleSample(true);
   }

   private void testPollingReadAfterPublisherGoneReturnsNoStaleSample(boolean forceV1)
   {
      ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("polling_writer_gone"), example_interfaces.Int64.class);
      ROS2Node subscriberNode = new ROS2Node("polling_writer_gone_sub");
      ROS2Node publisherNode = new ROS2Node("polling_writer_gone_pub");
      try (AbiScope abi = abi(forceV1))
      {
         ROS2Subscription<example_interfaces.Int64> subscription = subscriberNode.createSubscription(topic, reliableKeepLast(10));
         abi.assertPath(subscription);
         assertFalse(subscription.hasHadData());
         ROS2Publisher<example_interfaces.Int64> publisher = publisherNode.createPublisher(topic, reliableKeepLast(10));
         awaitMatched(publisher, subscription);

         publisher.publish(int64(42));
         awaitCondition(() -> subscription.getUnreadMessageCount() == 1, 30_000, "sample not received");
         assertTrue(subscription.hasHadData());

         example_interfaces.Int64 data = new example_interfaces.Int64();
         assertTrue(subscription.read(data));
         assertEquals(42, data.getData());

         publisherNode.close();
         awaitCondition(() -> subscription.getSubscriptionMatchedStatus() == 0, 30_000, "publisher did not go away");

         data.setData(-1);
         assertFalse(subscription.read(data), "the no-writers notification must not be returned as data");
         assertEquals(-1, data.getData());
         assertNull(subscription.read());
         assertEquals(0, subscription.readLatest(data));
         assertEquals(0, subscription.getUnreadMessageCount());
      }
      finally
      {
         publisherNode.close();
         subscriberNode.close();
      }
   }

   /**
    * readLatest() counts only samples with data (it used to count the no-writers notification) and the unread count
    * follows reads exactly.
    */
   @Test
   @Timeout(90)
   public void testReadLatestAndUnreadCountCountOnlyValidSamplesDefaultNativeAbi()
   {
      testReadLatestAndUnreadCountCountOnlyValidSamples(false);
   }

   @Test
   @Timeout(90)
   public void testReadLatestAndUnreadCountCountOnlyValidSamplesForcedNativeAbiV1()
   {
      testReadLatestAndUnreadCountCountOnlyValidSamples(true);
   }

   private void testReadLatestAndUnreadCountCountOnlyValidSamples(boolean forceV1)
   {
      ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("read_latest_valid"), example_interfaces.Int64.class);
      ROS2Node subscriberNode = new ROS2Node("read_latest_valid_sub");
      ROS2Node publisherNode = new ROS2Node("read_latest_valid_pub");
      try (AbiScope abi = abi(forceV1))
      {
         ROS2Subscription<example_interfaces.Int64> subscription = subscriberNode.createSubscription(topic, reliableKeepLast(20));
         ROS2Publisher<example_interfaces.Int64> publisher = publisherNode.createPublisher(topic, reliableKeepLast(20));
         awaitMatched(publisher, subscription);

         for (int i = 1; i <= 5; ++i)
         {
            publisher.publish(int64(i));
         }
         awaitCondition(() -> subscription.getUnreadMessageCount() == 5, 30_000, "samples not received");

         example_interfaces.Int64 data = new example_interfaces.Int64();
         SampleInfo sampleInfo = new SampleInfo();
         assertTrue(subscription.read(data, sampleInfo));
         assertEquals(1, data.getData());
         assertTrue(sampleInfo.hasValidData());
         assertTrue(sampleInfo.getSourceTimestampNanos() > 0);
         assertTrue(sampleInfo.getReceptionTimestampNanos() > 0);
         assertEquals(4, subscription.getUnreadMessageCount(), "read() takes exactly one sample");

         publisherNode.close();
         awaitCondition(() -> subscription.getSubscriptionMatchedStatus() == 0, 30_000, "publisher did not go away");

         assertEquals(4, subscription.readLatest(data), "only the four remaining data samples are counted");
         assertEquals(5, data.getData());
         assertEquals(0, subscription.getUnreadMessageCount());
         assertEquals(0, subscription.readLatest(data));
         sampleInfo.close();
         assertThrows(IllegalArgumentException.class, () -> subscription.read(data, sampleInfo), "a closed SampleInfo is rejected");
      }
      finally
      {
         publisherNode.close();
         subscriberNode.close();
      }
   }

   /**
    * In callback mode samples are taken by the listener: a publisher restart must deliver A1..A3 then B1..B3 exactly once,
    * in order, without the no-writers notification in between.
    */
   @Test
   @Timeout(120)
   public void testCallbackModeAcrossPublisherRestartDeliversInOrderDefaultNativeAbi()
   {
      testCallbackModeAcrossPublisherRestartDeliversInOrder(false);
   }

   @Test
   @Timeout(120)
   public void testCallbackModeAcrossPublisherRestartDeliversInOrderForcedNativeAbiV1()
   {
      testCallbackModeAcrossPublisherRestartDeliversInOrder(true);
   }

   private void testCallbackModeAcrossPublisherRestartDeliversInOrder(boolean forceV1)
   {
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(uniqueTopicName("callback_restart"), std_msgs.String_.class);
      ROS2Node subscriberNode = new ROS2Node("callback_restart_sub");
      try (AbiScope abi = abi(forceV1))
      {
         List<String> received = new ArrayList<>();
         AtomicInteger callbacks = new AtomicInteger();
         ROS2Subscription<std_msgs.String_> subscription = subscriberNode.createSubscription(topic, reader ->
         {
            callbacks.incrementAndGet();
            std_msgs.String_ message = reader.read();
            synchronized (received)
            {
               received.add(message == null ? "<null>" : message.getDataAsString());
            }
         }, reliableKeepLast(10));
         abi.assertPath(subscription);

         for (String generation : new String[] {"A", "B"})
         {
            ROS2Node publisherNode = new ROS2Node("callback_restart_pub_" + generation);
            try
            {
               ROS2Publisher<std_msgs.String_> publisher = publisherNode.createPublisher(topic, reliableKeepLast(10));
               awaitMatched(publisher, subscription);
               std_msgs.String_ message = new std_msgs.String_();
               for (int i = 1; i <= 3; ++i)
               {
                  message.setData(generation + i);
                  publisher.publish(message);
               }
               int expected = generation.equals("A") ? 3 : 6;
               awaitCondition(() ->
                              {
                                 synchronized (received)
                                 {
                                    return received.size() >= expected;
                                 }
                              }, 30_000, "generation " + generation + " not received");
            }
            finally
            {
               publisherNode.close();
            }
            awaitCondition(() -> subscription.getSubscriptionMatchedStatus() == 0, 30_000, "publisher " + generation + " did not go away");
         }

         // Give a stale duplicate (the old bug) a chance to show up
         waitFor(() -> false, 300);
         synchronized (received)
         {
            assertEquals(java.util.Arrays.asList("A1", "A2", "A3", "B1", "B2", "B3"), received);
         }
         assertEquals(6, callbacks.get(), "one callback per data sample, none for the no-writers notifications");
      }
      finally
      {
         subscriberNode.close();
      }
   }

   /**
    * Payloads that fail to deserialize are logged, counted and dropped; the listener keeps working (callback mode) and
    * polling skips them.
    */
   @Test
   @Timeout(90)
   public void testMalformedSamplesAreDroppedDefaultNativeAbi()
   {
      testMalformedSamplesAreDropped(false);
   }

   @Test
   @Timeout(90)
   public void testMalformedSamplesAreDroppedForcedNativeAbiV1()
   {
      testMalformedSamplesAreDropped(true);
   }

   private void testMalformedSamplesAreDropped(boolean forceV1)
   {
      ROS2Topic<example_interfaces.Bool> callbackTopic = new ROS2Topic<>(uniqueTopicName("malformed_callback"), example_interfaces.Bool.class);
      ROS2Topic<example_interfaces.Bool> pollingTopic = new ROS2Topic<>(uniqueTopicName("malformed_polling"), example_interfaces.Bool.class);
      ROS2Node node = new ROS2Node("malformed_samples");
      try (AbiScope abi = abi(forceV1))
      {
         List<Boolean> reads = new ArrayList<>();
         ROS2Subscription<example_interfaces.Bool> callbackSubscription = node.createSubscription(callbackTopic, reader ->
         {
            example_interfaces.Bool data = new example_interfaces.Bool();
            boolean ok = reader.read(data);
            synchronized (reads)
            {
               reads.add(ok && data.getData());
            }
         }, reliableKeepLast(10));
         ROS2Subscription<example_interfaces.Bool> pollingSubscription = node.createSubscription(pollingTopic, reliableKeepLast(10));
         ROS2Publisher<example_interfaces.Bool> callbackPublisher = node.createPublisher(callbackTopic, reliableKeepLast(10));
         ROS2Publisher<example_interfaces.Bool> pollingPublisher = node.createPublisher(pollingTopic, reliableKeepLast(10));
         awaitMatched(callbackPublisher, callbackSubscription);
         awaitMatched(pollingPublisher, pollingSubscription);

         ByteBuffer truncated = ByteBuffer.allocateDirect(8);
         truncated.put(CDRBuffer.PAYLOAD_HEADER); // header only: the boolean is missing
         ByteBuffer invalidBoolean = ByteBuffer.allocateDirect(8);
         invalidBoolean.put(CDRBuffer.PAYLOAD_HEADER).put((byte) 7);
         example_interfaces.Bool valid = new example_interfaces.Bool();
         valid.setData(true);

         assertEquals(0, callbackPublisher.writeSerializedPayload(truncated, 4));
         assertEquals(0, callbackPublisher.writeSerializedPayload(invalidBoolean, 5));
         callbackPublisher.publish(valid);
         awaitCondition(() ->
                        {
                           synchronized (reads)
                           {
                              return reads.size() >= 3;
                           }
                        }, 30_000, "callbacks not invoked");
         synchronized (reads)
         {
            assertEquals(java.util.Arrays.asList(false, false, true), reads, "malformed samples read as false, the listener survives");
         }
         assertEquals(2, callbackSubscription.getMalformedSampleCount());

         assertEquals(0, pollingPublisher.writeSerializedPayload(truncated, 4));
         assertEquals(0, pollingPublisher.writeSerializedPayload(invalidBoolean, 5));
         pollingPublisher.publish(valid);
         awaitCondition(() -> pollingSubscription.getUnreadMessageCount() == 3, 30_000, "samples not received");
         example_interfaces.Bool data = new example_interfaces.Bool();
         assertTrue(pollingSubscription.read(data), "polling skips the malformed samples");
         assertTrue(data.getData());
         assertFalse(pollingSubscription.read(data));
         assertEquals(2, pollingSubscription.getMalformedSampleCount());
      }
      finally
      {
         node.close();
      }
   }

   /**
    * Inside a callback the first read() returns the sample that triggered the callback; further reads drain samples that
    * are already available, which then get no callback of their own. Uses a single-thread callback executor so the
    * callback can block without blocking the publisher in any delivery mode.
    */
   @Test
   @Timeout(90)
   public void testReadInsideCallbackDrainsAvailableSamplesDefaultNativeAbi() throws Exception
   {
      testReadInsideCallbackDrainsAvailableSamples(false);
   }

   @Test
   @Timeout(90)
   public void testReadInsideCallbackDrainsAvailableSamplesForcedNativeAbiV1() throws Exception
   {
      testReadInsideCallbackDrainsAvailableSamples(true);
   }

   private void testReadInsideCallbackDrainsAvailableSamples(boolean forceV1) throws Exception
   {
      ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("callback_drain"), example_interfaces.Int64.class);
      ExecutorService executor = Executors.newSingleThreadExecutor();
      ROS2Node node = new ROS2Node("callback_drain", ROS2NodeOptions.defaults().callbackExecutor(executor));
      try (AbiScope abi = abi(forceV1))
      {
         CountDownLatch firstCallbackRunning = new CountDownLatch(1);
         CountDownLatch release = new CountDownLatch(1);
         List<Long> values = new ArrayList<>();
         AtomicInteger callbacks = new AtomicInteger();
         AtomicReference<String> sampleInfoError = new AtomicReference<>();
         SampleInfo sampleInfo = new SampleInfo();

         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(topic, reader ->
         {
            callbacks.incrementAndGet();
            firstCallbackRunning.countDown();
            try
            {
               release.await(30, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
            example_interfaces.Int64 data = new example_interfaces.Int64();
            while (reader.read(data, sampleInfo))
            {
               if (!sampleInfo.hasValidData() || sampleInfo.getReceptionTimestampNanos() <= 0)
               {
                  sampleInfoError.set("bad sample info for " + data.getData());
               }
               synchronized (values)
               {
                  values.add(data.getData());
               }
            }
         }, reliableKeepAll());
         ROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic, reliableKeepAll());
         awaitMatched(publisher, subscription);

         publisher.publish(int64(0));
         assertTrue(firstCallbackRunning.await(30, TimeUnit.SECONDS));
         for (int i = 1; i <= 5; ++i)
         {
            publisher.publish(int64(i));
         }
         awaitCondition(() -> subscription.getUnreadMessageCount() == 5, 30_000, "samples not queued");
         release.countDown();

         awaitCondition(() ->
                        {
                           synchronized (values)
                           {
                              return values.size() >= 6;
                           }
                        }, 30_000, "samples not drained");
         waitFor(() -> false, 200);
         synchronized (values)
         {
            assertEquals(java.util.Arrays.asList(0L, 1L, 2L, 3L, 4L, 5L), values);
         }
         assertEquals(1, callbacks.get(), "samples drained inside the callback get no callback of their own");
         assertNull(sampleInfoError.get(), sampleInfoError.get());
         sampleInfo.close();
      }
      finally
      {
         node.close();
         executor.shutdownNow();
      }
   }

   /**
    * readLatest() inside the callback consumes everything that is available and returns the latest sample.
    */
   @Test
   @Timeout(90)
   public void testReadLatestInsideCallback() throws Exception
   {
      ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("callback_read_latest"), example_interfaces.Int64.class);
      ExecutorService executor = Executors.newSingleThreadExecutor();
      ROS2Node node = new ROS2Node("callback_read_latest", ROS2NodeOptions.defaults().callbackExecutor(executor));
      try
      {
         CountDownLatch firstCallbackRunning = new CountDownLatch(1);
         CountDownLatch release = new CountDownLatch(1);
         AtomicReference<String> result = new AtomicReference<>();
         AtomicInteger callbacks = new AtomicInteger();

         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(topic, reader ->
         {
            callbacks.incrementAndGet();
            firstCallbackRunning.countDown();
            try
            {
               release.await(30, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
            example_interfaces.Int64 data = new example_interfaces.Int64();
            int count = reader.readLatest(data);
            result.compareAndSet(null, count + ":" + data.getData());
         }, reliableKeepAll());
         ROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic, reliableKeepAll());
         awaitMatched(publisher, subscription);

         publisher.publish(int64(10));
         assertTrue(firstCallbackRunning.await(30, TimeUnit.SECONDS));
         publisher.publish(int64(11));
         publisher.publish(int64(12));
         awaitCondition(() -> subscription.getUnreadMessageCount() == 2, 30_000, "samples not queued");
         release.countDown();
         awaitCondition(() -> result.get() != null, 30_000, "callback did not finish");
         waitFor(() -> false, 200);
         assertEquals("3:12", result.get());
         assertEquals(1, callbacks.get());
      }
      finally
      {
         node.close();
         executor.shutdownNow();
      }
   }

   /**
    * A TRANSIENT_LOCAL sample that is already in the history when the subscription's listener is attached must still
    * trigger the callback.
    */
   @Test
   @Timeout(90)
   public void testLateJoiningCallbackSubscriptionReceivesTransientLocalSample()
   {
      ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("late_joiner"), example_interfaces.Int64.class);
      ROS2Node publisherNode = new ROS2Node("late_joiner_pub");
      ROS2Node subscriberNode = new ROS2Node("late_joiner_sub");
      try
      {
         ROS2Publisher<example_interfaces.Int64> publisher = publisherNode.createPublisher(topic, reliableTransientLocal(1));
         publisher.publish(int64(7));

         AtomicReference<Long> received = new AtomicReference<>();
         subscriberNode.createSubscription(topic, reader ->
         {
            example_interfaces.Int64 data = reader.read();
            if (data != null)
            {
               received.compareAndSet(null, data.getData());
            }
         }, reliableTransientLocal(1));
         awaitCondition(() -> received.get() != null, 30_000, "latched sample not delivered");
         assertEquals(7L, received.get().longValue());
      }
      finally
      {
         publisherNode.close();
         subscriberNode.close();
      }
   }

   @Test
   @Timeout(90)
   public void testSubscriptionStatisticsAreOptInDefaultNativeAbi()
   {
      testSubscriptionStatisticsAreOptIn(false);
   }

   @Test
   @Timeout(90)
   public void testSubscriptionStatisticsAreOptInForcedNativeAbiV1()
   {
      testSubscriptionStatisticsAreOptIn(true);
   }

   private void testSubscriptionStatisticsAreOptIn(boolean forceV1)
   {
      ROS2Topic<geometry_msgs.Point> topic = new ROS2Topic<>(uniqueTopicName("subscription_statistics"), geometry_msgs.Point.class);
      ROS2Node node = new ROS2Node("subscription_statistics");
      try (AbiScope abi = abi(forceV1))
      {
         AtomicInteger received = new AtomicInteger();
         ROS2Subscription<geometry_msgs.Point> subscription = node.createSubscription(topic, reader ->
         {
            if (reader.read() != null)
            {
               received.incrementAndGet();
            }
         }, reliableKeepAll());
         ROS2Publisher<geometry_msgs.Point> publisher = node.createPublisher(topic, reliableKeepAll());
         awaitMatched(publisher, subscription);
         geometry_msgs.Point point = new geometry_msgs.Point();
         Statistics statistics = new Statistics();

         assertFalse(subscription.isStatisticsEnabled());
         publisher.publish(point);
         awaitCondition(() -> received.get() == 1, 30_000, "first sample");
         subscription.readMessageSizeStatistics(statistics);
         assertEquals(0, statistics.get(Statistics.StatisticDataType.SAMPLE_COUNT), "disabled by default");

         subscription.setStatisticsEnabled(true);
         publisher.publish(point);
         publisher.publish(point);
         awaitCondition(() -> received.get() == 3, 30_000, "more samples");
         subscription.readMessageSizeStatistics(statistics);
         assertEquals(2, statistics.get(Statistics.StatisticDataType.SAMPLE_COUNT));
         assertEquals(4 + point.calculateSizeBytes(0), statistics.get(Statistics.StatisticDataType.LATEST), 1e-9);
         subscription.readMessageAgeStatistics(statistics);
         assertEquals(2, statistics.get(Statistics.StatisticDataType.SAMPLE_COUNT));
         subscription.resetStatistics();
         subscription.readMessageSizeStatistics(statistics);
         assertEquals(0, statistics.get(Statistics.StatisticDataType.SAMPLE_COUNT));
      }
      finally
      {
         node.close();
      }
   }

   /**
    * Variable-size messages larger than the read buffer grow it (ABI v2 falls back to the native copy) and smaller ones
    * afterwards still read correctly.
    */
   @Test
   @Timeout(90)
   public void testGrowingAndShrinkingPayloadsDefaultNativeAbi()
   {
      testGrowingAndShrinkingPayloads(false);
   }

   @Test
   @Timeout(90)
   public void testGrowingAndShrinkingPayloadsForcedNativeAbiV1()
   {
      testGrowingAndShrinkingPayloads(true);
   }

   private void testGrowingAndShrinkingPayloads(boolean forceV1)
   {
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(uniqueTopicName("growing_payloads"), std_msgs.String_.class);
      ROS2Node node = new ROS2Node("growing_payloads");
      try (AbiScope abi = abi(forceV1))
      {
         List<Integer> callbackLengths = new ArrayList<>();
         ROS2Subscription<std_msgs.String_> callbackSubscription = node.createSubscription(topic, reader ->
         {
            std_msgs.String_ message = reader.read();
            synchronized (callbackLengths)
            {
               callbackLengths.add(message == null ? -1 : message.getData().length());
            }
         }, reliableKeepAll());
         ROS2Subscription<std_msgs.String_> pollingSubscription = node.createSubscription(topic, reliableKeepAll());
         ROS2Publisher<std_msgs.String_> publisher = node.createPublisher(topic, reliableKeepAll());
         awaitMatched(publisher, callbackSubscription);
         awaitMatched(publisher, pollingSubscription);

         int[] lengths = {1, 100_000, 3, 250_000, 0};
         std_msgs.String_ message = new std_msgs.String_();
         for (int length : lengths)
         {
            StringBuilder builder = new StringBuilder(length);
            for (int i = 0; i < length; ++i)
            {
               builder.append((char) ('a' + i % 26));
            }
            message.setData(builder.toString());
            publisher.publish(message);
         }

         awaitCondition(() ->
                        {
                           synchronized (callbackLengths)
                           {
                              return callbackLengths.size() >= lengths.length;
                           }
                        }, 30_000, "callback samples");
         synchronized (callbackLengths)
         {
            for (int i = 0; i < lengths.length; ++i)
            {
               assertEquals(lengths[i], callbackLengths.get(i).intValue());
            }
         }

         awaitCondition(() -> pollingSubscription.getUnreadMessageCount() == lengths.length, 30_000, "polling samples");
         std_msgs.String_ data = new std_msgs.String_();
         for (int length : lengths)
         {
            assertTrue(pollingSubscription.read(data));
            assertEquals(length, data.getData().length());
            for (int i = 0; i < length; i += 997)
            {
               assertEquals((char) ('a' + i % 26), data.getData().charAt(i));
            }
         }
      }
      finally
      {
         node.close();
      }
   }
}
