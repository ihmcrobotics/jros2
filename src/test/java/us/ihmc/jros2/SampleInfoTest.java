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
import us.ihmc.jros2.PubSubTestTools.AbiScope;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.jros2.PubSubTestTools.*;

/**
 * {@link SampleInfo}: the DDS sample metadata handed out with {@link ROS2MessageReader#read(ROS2Message, SampleInfo)}, on
 * both native ABI paths. Polling reads fill the SampleInfo natively; reads inside a callback copy the metadata of the
 * sample that triggered the callback into it, so it stays valid after the callback returns.
 */
public class SampleInfoTest
{
   /** DDS SampleStateKind, ViewStateKind and InstanceStateKind values (DDS 1.4, 2.2.2.5.1). */
   static final short NOT_READ_SAMPLE_STATE = 0x0002;
   static final short NEW_VIEW_STATE = 0x0001;
   static final short NOT_NEW_VIEW_STATE = 0x0002;
   static final short ALIVE_INSTANCE_STATE = 0x0001;

   /** Metadata of one sample, read through the public getters. */
   private record Metadata(boolean validData, long sourceTimestampNanos, long receptionTimestampNanos, short sampleState, short viewState, short instanceState)
   {
      static Metadata of(SampleInfo sampleInfo)
      {
         return new Metadata(sampleInfo.hasValidData(),
                             sampleInfo.getSourceTimestampNanos(),
                             sampleInfo.getReceptionTimestampNanos(),
                             sampleInfo.getSampleState(),
                             sampleInfo.getViewState(),
                             sampleInfo.getInstanceState());
      }
   }

   /** The Java and Fast-DDS clocks differ in resolution: on Windows, System.currentTimeMillis() can advance only every 15.6 ms. */
   private static final long CLOCK_MARGIN_NANOS = TimeUnit.MILLISECONDS.toNanos(50);

   private static example_interfaces.Int64 int64(long value)
   {
      example_interfaces.Int64 message = new example_interfaces.Int64();
      message.setData(value);
      return message;
   }

   private static void assertFirstAndSecondSample(List<Metadata> metadata, long publishedAfterNanos, long readBeforeNanos)
   {
      assertEquals(2, metadata.size());
      for (Metadata sample : metadata)
      {
         assertTrue(sample.validData(), sample.toString());
         assertTrue(sample.sourceTimestampNanos() >= publishedAfterNanos && sample.sourceTimestampNanos() <= readBeforeNanos,
                    "the source timestamp is the publication time: " + sample + " not in [" + publishedAfterNanos + ", " + readBeforeNanos + "]");
         assertTrue(sample.receptionTimestampNanos() >= sample.sourceTimestampNanos() && sample.receptionTimestampNanos() <= readBeforeNanos,
                    "the sample is received after it is published: " + sample);
         assertEquals(NOT_READ_SAMPLE_STATE, sample.sampleState(), "a sample is taken once, so it was never read before: " + sample);
         assertEquals(ALIVE_INSTANCE_STATE, sample.instanceState(), "the publisher is alive: " + sample);
      }
      assertEquals(NEW_VIEW_STATE, metadata.get(0).viewState(), "the first sample of the (keyless) instance is NEW");
      assertEquals(NOT_NEW_VIEW_STATE, metadata.get(1).viewState(), "later samples of the instance are NOT_NEW");
      assertTrue(metadata.get(1).sourceTimestampNanos() >= metadata.get(0).sourceTimestampNanos());
   }

   @Test
   @Timeout(60)
   public void testPolledReadFillsTheSampleInfoDefaultNativeAbi()
   {
      testPolledReadFillsTheSampleInfo(false);
   }

   @Test
   @Timeout(60)
   public void testPolledReadFillsTheSampleInfoForcedNativeAbiV1()
   {
      testPolledReadFillsTheSampleInfo(true);
   }

   private void testPolledReadFillsTheSampleInfo(boolean forceV1)
   {
      ROS2Node node = new ROS2Node("sample_info_polled");
      try (AbiScope abi = abi(forceV1))
      {
         ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("sample_info_polled"), example_interfaces.Int64.class);
         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(topic, reliableKeepLast(10));
         ROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic, reliableKeepLast(10));
         abi.assertPath(subscription);
         awaitMatched(publisher, subscription);

         long publishedAfter = System.currentTimeMillis() * 1_000_000L - CLOCK_MARGIN_NANOS;
         publisher.publish(int64(1));
         publisher.publish(int64(2));
         awaitCondition(() -> subscription.getUnreadMessageCount() == 2, 30_000, "samples not received");

         example_interfaces.Int64 data = new example_interfaces.Int64();
         SampleInfo sampleInfo = new SampleInfo();
         List<Metadata> metadata = new ArrayList<>();
         for (int i = 1; i <= 2; ++i)
         {
            assertTrue(subscription.read(data, sampleInfo));
            assertEquals(i, data.getData());
            metadata.add(Metadata.of(sampleInfo));
         }
         long readBefore = System.currentTimeMillis() * 1_000_000L + CLOCK_MARGIN_NANOS;
         assertFirstAndSecondSample(metadata, publishedAfter, readBefore);

         // No sample: the SampleInfo is not changed
         assertFalse(subscription.read(data, sampleInfo));
         assertEquals(metadata.get(1), Metadata.of(sampleInfo));

         // A closed SampleInfo filled natively has no memory to read from any more
         sampleInfo.close();
         sampleInfo.close();
         assertTrue(sampleInfo.isClosed());
         assertThrows(IllegalStateException.class, sampleInfo::hasValidData);
         assertThrows(IllegalStateException.class, sampleInfo::getSourceTimestampNanos);
         assertThrows(IllegalStateException.class, sampleInfo::getReceptionTimestampNanos);
         assertThrows(IllegalStateException.class, sampleInfo::getSampleState);
         assertThrows(IllegalStateException.class, sampleInfo::getViewState);
         assertThrows(IllegalStateException.class, sampleInfo::getInstanceState);
         assertThrows(IllegalArgumentException.class, () -> subscription.read(data, sampleInfo));
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(60)
   public void testCallbackReadSnapshotsTheSampleInfoDefaultNativeAbi() throws InterruptedException
   {
      testCallbackReadSnapshotsTheSampleInfo(false);
   }

   @Test
   @Timeout(60)
   public void testCallbackReadSnapshotsTheSampleInfoForcedNativeAbiV1() throws InterruptedException
   {
      testCallbackReadSnapshotsTheSampleInfo(true);
   }

   private void testCallbackReadSnapshotsTheSampleInfo(boolean forceV1) throws InterruptedException
   {
      ROS2Node node = new ROS2Node("sample_info_callback");
      SampleInfo[] sampleInfos = {new SampleInfo(), new SampleInfo()};
      try (AbiScope abi = abi(forceV1))
      {
         ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("sample_info_callback"), example_interfaces.Int64.class);
         List<Metadata> inCallback = new ArrayList<>();
         AtomicReference<String> error = new AtomicReference<>();
         CountDownLatch received = new CountDownLatch(2);
         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(topic, reader ->
         {
            example_interfaces.Int64 data = new example_interfaces.Int64();
            int index = (int) (2 - received.getCount());
            if (index > 1 || !reader.read(data, sampleInfos[index]))
            {
               error.compareAndSet(null, "unexpected callback " + index);
               return;
            }
            if (data.getData() != index + 1)
            {
               error.compareAndSet(null, "sample " + data.getData() + " in callback " + index);
            }
            synchronized (inCallback)
            {
               inCallback.add(Metadata.of(sampleInfos[index]));
            }
            received.countDown();
         }, reliableKeepLast(10));
         ROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic, reliableKeepLast(10));
         abi.assertPath(subscription);
         awaitMatched(publisher, subscription);

         long publishedAfter = System.currentTimeMillis() * 1_000_000L - CLOCK_MARGIN_NANOS;
         publisher.publish(int64(1));
         assertTrue(waitFor(() -> received.getCount() == 1, 30_000), "first sample not received");
         publisher.publish(int64(2));
         assertTrue(received.await(30, TimeUnit.SECONDS), "second sample not received");
         long readBefore = System.currentTimeMillis() * 1_000_000L + CLOCK_MARGIN_NANOS;
         assertNull(error.get(), error.get());

         List<Metadata> afterCallback = new ArrayList<>();
         synchronized (inCallback)
         {
            assertFirstAndSecondSample(inCallback, publishedAfter, readBefore);
            for (int i = 0; i < 2; ++i)
            {
               afterCallback.add(Metadata.of(sampleInfos[i]));
            }
            assertEquals(inCallback, afterCallback, "the callback's SampleInfo keeps its values after the callback returned");
         }

         // A snapshot needs no native memory: it stays readable after close
         sampleInfos[0].close();
         assertEquals(afterCallback.get(0), Metadata.of(sampleInfos[0]));

         // Copying a snapshot copies its values. Both samples are copied: only the first one has a view state (NEW) that
         // differs from its sample state (NOT_READ has the same value as NOT_NEW).
         for (int i = 0; i < 2; ++i)
         {
            SampleInfo copy = new SampleInfo();
            copy.copyFrom(sampleInfos[i]);
            assertEquals(afterCallback.get(i), Metadata.of(copy), "copy of the snapshot of sample " + (i + 1));
            copy.close();
         }
      }
      finally
      {
         node.close();
         for (SampleInfo sampleInfo : sampleInfos)
         {
            sampleInfo.close();
         }
      }
   }
}
