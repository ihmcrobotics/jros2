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

import us.ihmc.jros2.HotPathMessages.MessageKind;
import us.ihmc.jros2.allocation.SpinWait;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One publisher and one subscription of a {@link MessageKind}, and an allocation-free control tick for the allocation
 * gates: {@link #tick(int)} updates the message, publishes it and waits (allocation-free) until the subscription
 * received it the way {@link ReceiveMode} says, so that every window covers the whole publish and receive path on every
 * thread involved and ends quiescent.
 */
final class HotPathPubSub<T extends ROS2Message<T>> implements AutoCloseable
{
   static final long RECEIVE_TIMEOUT_MS = 10_000;
   /**
    * BEST_EFFORT may drop a sample (large fragmented ones in particular): the tick publishes it again after this long, then
    * after twice as long, and so on up to {@link #BEST_EFFORT_MAX_RETRY_NANOS}. Backing off matters: a reader that drops a
    * fragment (for example a CPU-starved receive thread that let the shared-memory segment fill up) still reassembles the
    * next sample, but it abandons that reassembly whenever a newer sample starts arriving, so republishing too often can
    * starve a slow reader for good.
    */
   static final long BEST_EFFORT_RETRY_NANOS = 200_000_000L;
   static final long BEST_EFFORT_MAX_RETRY_NANOS = 3_200_000_000L;
   /** How long the tick keeps republishing a BEST_EFFORT sample before it waits {@link #RECEIVE_TIMEOUT_MS} a last time. */
   static final long BEST_EFFORT_TIMEOUT_NANOS = 60_000_000_000L;
   /** Not a compile-time constant, which javac would copy into this class as a string literal. */
   private static final String DELIVERY = Text.delivery();

   /** How the subscription receives. */
   enum ReceiveMode
   {
      /** Callback (inline on the Fast-DDS thread, or on the node's callback executor) that calls read(message). */
      CALLBACK_READ(true),
      /** Callback that calls read(message, sampleInfo) and reads the sample info. */
      CALLBACK_READ_SAMPLE_INFO(true),
      /** Callback that calls readLatest(message). */
      CALLBACK_READ_LATEST(true),
      /** No callback: the tick waits for getUnreadMessageCount() > 0 and calls read(message). */
      POLL_READ(false),
      /** No callback: read(message, sampleInfo). */
      POLL_READ_SAMPLE_INFO(false),
      /** No callback: readLatest(message). */
      POLL_READ_LATEST(false);

      final boolean callback;

      ReceiveMode(boolean callback)
      {
         this.callback = callback;
      }
   }

   final MessageKind<T> kind;
   final ReceiveMode mode;
   private final ROS2Node publisherNode;
   private final ROS2Node subscriptionNode;
   final ROS2Publisher<T> publisher;
   final ROS2Subscription<T> subscription;
   final T message;
   final T received;
   final SampleInfo sampleInfo;
   final AtomicLong receivedCount = new AtomicLong();
   final AtomicLong failures = new AtomicLong();
   final Statistics statistics = new Statistics();
   private volatile int lastSequence = -1;
   /** The thread that ran the last callback (null in polling modes). */
   volatile Thread callbackThread;
   private long publishedCount;
   private final boolean bestEffort;
   /** Number of BEST_EFFORT samples that were published again because the first one was dropped. */
   final AtomicLong republished = new AtomicLong();
   private final BooleanSupplier allReceived = () -> receivedCount.get() >= publishedCount;
   private final BooleanSupplier unread;

   HotPathPubSub(ROS2Node publisherNode, ROS2Node subscriptionNode, MessageKind<T> kind, ROS2QoSProfile qos, ReceiveMode mode)
   {
      this(publisherNode, subscriptionNode, kind, qos, mode, 0);
   }

   /**
    * @param asyncQueueCapacity queue capacity of the publisher if {@code publisherNode} is an {@link AsyncROS2Node} (0 for
    *                           its default)
    */
   HotPathPubSub(ROS2Node publisherNode, ROS2Node subscriptionNode, MessageKind<T> kind, ROS2QoSProfile qos, ReceiveMode mode, int asyncQueueCapacity)
   {
      this.kind = kind;
      this.mode = mode;
      this.publisherNode = publisherNode;
      this.subscriptionNode = subscriptionNode;
      message = kind.create();
      received = ROS2Message.createInstance(kind.type());
      sampleInfo = new SampleInfo();
      bestEffort = qos.getReliability() == ROS2QoSProfile.Reliability.BEST_EFFORT;
      ROS2Topic<T> topic = new ROS2Topic<>(Text.topicName(kind), kind.type());
      if (mode.callback)
      {
         subscription = subscriptionNode.createSubscription(topic, this::onMessage, qos);
      }
      else
      {
         subscription = subscriptionNode.createSubscription(topic, qos);
      }
      if (asyncQueueCapacity > 0)
      {
         publisher = ((AsyncROS2Node) publisherNode).createPublisher(topic, qos, asyncQueueCapacity);
      }
      else
      {
         publisher = publisherNode.createPublisher(topic, qos);
      }
      PubSubTestTools.awaitMatched(publisher, subscription);
      ROS2Subscription<T> polled = subscription;
      unread = () -> polled.getUnreadMessageCount() > 0;
      // Execute the bytecodes of the BEST_EFFORT retry once now: their first execution resolves constant pool entries
      // (which allocates), and a first retry may only happen inside a window.
      SpinWait.until(unread, 0);
      republished.incrementAndGet();
      republished.set(Math.min(0, BEST_EFFORT_MAX_RETRY_NANOS));
   }

   private void onMessage(ROS2MessageReader<T> reader)
   {
      callbackThread = Thread.currentThread();
      boolean ok;
      switch (mode)
      {
         case CALLBACK_READ:
            ok = reader.read(received);
            break;
         case CALLBACK_READ_SAMPLE_INFO:
            ok = reader.read(received, sampleInfo) && sampleInfo.hasValidData() && sampleInfo.getSourceTimestampNanos() > 0
                 && sampleInfo.getReceptionTimestampNanos() > 0 && sampleInfo.getInstanceState() == 1 && sampleInfo.getSampleState() > 0
                 && sampleInfo.getViewState() > 0;
            break;
         default:
            ok = reader.readLatest(received) > 0;
            break;
      }
      if (ok)
      {
         lastSequence = kind.sequence(received);
         receivedCount.incrementAndGet();
      }
      else
      {
         failures.incrementAndGet();
      }
   }

   /**
    * One control tick: update, publish, wait until received. Allocation-free once warmed up.
    */
   void tick(int i)
   {
      kind.update(message, i);
      publishedCount++;
      publisher.publish(message);
      if (bestEffort)
      {
         // Allocation-free retry with back-off: publish the same tick again until it arrives.
         long deadline = System.nanoTime() + BEST_EFFORT_TIMEOUT_NANOS;
         long interval = BEST_EFFORT_RETRY_NANOS;
         while (!SpinWait.until(mode.callback ? allReceived : unread, interval) && System.nanoTime() - deadline < 0)
         {
            republished.incrementAndGet();
            publisher.publish(message);
            interval = Math.min(2 * interval, BEST_EFFORT_MAX_RETRY_NANOS);
         }
      }
      awaitReceived();
   }

   /** Publishes the current message without waiting; {@link #awaitReceived(int)} must follow. */
   void publishOnly(int i)
   {
      kind.update(message, i);
      publishedCount++;
      publisher.publish(message);
   }

   /** Waits until every published sample was received (callback modes), or until a sample is available and reads it (polling modes). */
   void awaitReceived()
   {
      if (mode.callback)
      {
         SpinWait.await(allReceived, RECEIVE_TIMEOUT_MS, DELIVERY);
         return;
      }
      SpinWait.await(unread, RECEIVE_TIMEOUT_MS, DELIVERY);
      boolean ok;
      switch (mode)
      {
         case POLL_READ:
            ok = subscription.read(received);
            break;
         case POLL_READ_SAMPLE_INFO:
            ok = subscription.read(received, sampleInfo) && sampleInfo.hasValidData() && sampleInfo.getSourceTimestampNanos() > 0
                 && sampleInfo.getReceptionTimestampNanos() > 0 && sampleInfo.getSampleState() > 0 && sampleInfo.getViewState() > 0;
            break;
         default:
            ok = subscription.readLatest(received) > 0;
            break;
      }
      if (ok)
      {
         lastSequence = kind.sequence(received);
         receivedCount.incrementAndGet();
      }
      else
      {
         failures.incrementAndGet();
      }
   }

   /** Per-tick queries a control loop may make; allocation-free. */
   long queries()
   {
      long sum = subscription.getUnreadMessageCount() + publisher.getPublicationMatchedStatus();
      sum += subscription.hasHadData() ? 1 : 0;
      sum += subscription.getMalformedSampleCount();
      sum += publisher.isClosed() || subscription.isClosed() ? 1 : 0;
      sum += publisher.getGuid().hashCode() == 0 ? 1 : 0;
      sum += subscription.getGuid().equals(publisher.getGuid()) ? 1 : 0;
      sum += publisher.getTopicName().length() + subscription.getTopicName().length();
      sum += subscription.getTopicType() == publisher.getTopicType() ? 1 : 0;
      if (publisher instanceof AsyncROS2Publisher)
      {
         AsyncROS2Publisher<T> async = (AsyncROS2Publisher<T>) publisher;
         sum += async.getDroppedMessageCount() + async.getQueuedMessageCount() + async.getQueueCapacity();
      }
      if (publisher.isStatisticsEnabled())
      {
         publisher.readStatistics(MessageStatisticsProvider.MessageMetadataType.SIZE, statistics);
         subscription.readStatistics(MessageStatisticsProvider.MessageMetadataType.AGE, statistics);
         sum += (long) statistics.get(Statistics.StatisticDataType.SAMPLE_COUNT);
         sum += (long) publisher.getMessagePeriod(Statistics.StatisticDataType.AVERAGE);
         sum += (long) subscription.getMessageAge(Statistics.StatisticDataType.STDDEV);
         sum += (long) subscription.getMessageSize(Statistics.StatisticDataType.SAMPLE_COUNT);
      }
      return sum;
   }

   void setStatisticsEnabled(boolean enabled)
   {
      publisher.setStatisticsEnabled(enabled);
      subscription.setStatisticsEnabled(enabled);
   }

   /** Checks (outside the windows) that every tick was delivered, in full, and that the last one was received last. */
   void verify(int lastTick)
   {
      Text.verify(this, lastTick, lastSequence, publishedCount);
   }

   /**
    * The texts of this class, which runs inside the gate windows: a class with string literals allocates them when the JIT
    * first compiles one of its methods with C2 (see HotPathText), which could happen inside a window.
    */
   private static final class Text
   {
      static String delivery()
      {
         return "sample delivery";
      }

      static String topicName(MessageKind<?> kind)
      {
         return PubSubTestTools.uniqueTopicName("hot_path_" + kind.type().getSimpleName());
      }

      static void verify(HotPathPubSub<?> loop, int lastTick, int lastSequence, long publishedCount)
      {
         assertEquals(0, loop.failures.get(), loop.kind + ": reads that returned no data");
         assertEquals(lastTick, lastSequence, loop.kind + ": last received tick");
         assertTrue(loop.receivedCount.get() >= publishedCount, loop.kind + ": received " + loop.receivedCount.get() + " of " + publishedCount);
         assertEquals(0, loop.subscription.getMalformedSampleCount(), loop.kind + ": malformed samples");
      }
   }

   @Override
   public void close()
   {
      publisherNode.destroyPublisher(publisher);
      subscriptionNode.destroySubscription(subscription);
      sampleInfo.close();
   }
}
