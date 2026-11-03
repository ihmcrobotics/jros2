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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import us.ihmc.jros2.HotPathPubSub.ReceiveMode;
import us.ihmc.jros2.allocation.AllocationGate;
import us.ihmc.jros2.allocation.SpinWait;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Allocation gates of contended hot paths: several threads publishing on one publisher (they serialize on its write
 * lock), on separate publishers, polling one subscription, and reading statistics while they are recorded. Contended
 * JDK locks allocate (queue nodes on the Java heap; a contended monitor is inflated, a native malloc that the monitor pass
 * of the gate reports), so these tests prove that the locks of jros2 do not.
 */
@Tag("allocation")
public class ConcurrencyAllocationTest
{
   private static final int WARMUP = PublishSubscribeAllocationTest.WARMUP;
   private static final int ITERATIONS = PublishSubscribeAllocationTest.ITERATIONS;
   private static final int HELPERS = 3;
   private static final int SAMPLES_PER_POLLING_ROUND = 8;

   private static ROS2Node publisherNode;
   private static ROS2Node subscriptionNode;

   @BeforeAll
   public static void createNodes()
   {
      publisherNode = new ROS2Node("alloc_gate_concurrency_publisher");
      subscriptionNode = new ROS2Node("alloc_gate_concurrency_subscriber");
   }

   @AfterAll
   public static void closeNodes()
   {
      publisherNode.close();
      subscriptionNode.close();
   }

   /**
    * Helper threads that run {@code work} once per round, allocation-free, and count completed rounds.
    */
   private static final class Helpers implements AutoCloseable
   {
      final AtomicInteger round = new AtomicInteger();
      final AtomicLong done = new AtomicLong();
      final AtomicInteger stop = new AtomicInteger();
      final Thread[] threads;
      private long expected;
      private final BooleanSupplier allDone = () -> done.get() >= expected;

      interface Work
      {
         void run(int helper, int round);
      }

      Helpers(String name, int count, Work work)
      {
         threads = new Thread[count];
         for (int h = 0; h < count; ++h)
         {
            int helper = h;
            threads[h] = new Thread(() ->
            {
               int handled = 0;
               BooleanSupplier next = () -> round.get() != 0 || stop.get() != 0;
               while (stop.get() == 0)
               {
                  int current = round.get();
                  if (current != handled)
                  {
                     handled = current;
                     work.run(helper, current);
                     done.incrementAndGet();
                  }
                  else
                  {
                     SpinWait.until(next, 1_000_000L);
                  }
               }
            }, name + "-" + h);
            threads[h].setDaemon(true);
            threads[h].start();
         }
      }

      /**
       * Starts the next round on every helper. Rounds are numbered on from the previous one, not from the gate's iteration
       * index, which starts over in every pass of the gate: counts derived from the round must keep growing.
       *
       * @return the number of the round, from 1
       */
      int start()
      {
         int r = round.get() + 1;
         expected += threads.length;
         round.set(r);
         return r;
      }

      void await()
      {
         SpinWait.await(allDone, 10_000, "helper threads");
      }

      void assertMeasured(AllocationGate.Result result)
      {
         for (Thread thread : threads)
            assertTrue(result.hotPathThreadNames().contains(thread.getName()), thread.getName() + " measured: " + result.report());
      }

      @Override
      public void close() throws InterruptedException
      {
         stop.set(1);
         for (Thread thread : threads)
            thread.join(5_000);
      }
   }

   /** Four threads publish on one ROS2Publisher at the same time, with and without the forced ABI v1 path. */
   private void testOnePublisherManyThreads(boolean forceV1, boolean statistics) throws InterruptedException
   {
      try (PubSubTestTools.AbiScope abi = PubSubTestTools.abi(forceV1);
           HotPathPubSub<sensor_msgs.JointState> loop = new HotPathPubSub<>(publisherNode, subscriptionNode, HotPathMessages.JOINT_STATE,
                                                                             PubSubTestTools.reliableKeepLast(64), ReceiveMode.CALLBACK_READ))
      {
         abi.assertPath(loop.publisher);
         loop.setStatisticsEnabled(statistics);
         sensor_msgs.JointState[] messages = new sensor_msgs.JointState[HELPERS];
         for (int h = 0; h < HELPERS; ++h)
            messages[h] = HotPathMessages.JOINT_STATE.create();
         try (Helpers helpers = new Helpers("alloc-gate-publisher", HELPERS, (helper, round) ->
         {
            HotPathMessages.JOINT_STATE.update(messages[helper], round);
            loop.publisher.publish(messages[helper]);
         }))
         {
            BooleanSupplier allReceived = () -> loop.receivedCount.get() >= helpers.done.get() + helpers.round.get();
            AllocationGate.Result result = AllocationGate.named("4 threads on one publisher" + (forceV1 ? " ABIv1" : "") + (statistics ? " statistics" : ""))
                                                         .warmup(WARMUP)
                                                         .iterations(ITERATIONS)
                                                         .run(i ->
                                                         {
                                                            int r = helpers.start();
                                                            loop.publishOnly(r);
                                                            helpers.await();
                                                            SpinWait.await(allReceived, 10_000, "delivery");
                                                            loop.queries();
                                                         });
            assertEquals(0, loop.failures.get());
            result.assertAllocationFree();
            helpers.assertMeasured(result);
         }
      }
   }

   @Test
   public void testOnePublisherManyThreads() throws InterruptedException
   {
      testOnePublisherManyThreads(false, false);
   }

   @Test
   public void testOnePublisherManyThreadsForcedAbiV1Statistics() throws InterruptedException
   {
      testOnePublisherManyThreads(true, true);
   }

   /** Four threads publish on four publishers (four topics) at the same time. */
   @Test
   public void testManyPublishersManyThreads() throws InterruptedException
   {
      @SuppressWarnings("unchecked") HotPathPubSub<geometry_msgs.PoseStamped>[] loops = new HotPathPubSub[HELPERS + 1];
      try
      {
         for (int t = 0; t <= HELPERS; ++t)
            loops[t] = new HotPathPubSub<>(publisherNode, subscriptionNode, HotPathMessages.POSE_STAMPED, PublishSubscribeAllocationTest.reliable(),
                                           ReceiveMode.CALLBACK_READ);
         try (Helpers helpers = new Helpers("alloc-gate-topic-publisher", HELPERS, (helper, round) -> loops[helper + 1].publishOnly(round)))
         {
            int[] last = new int[1];
            AllocationGate.Result result = AllocationGate.named("4 threads on 4 publishers").warmup(WARMUP).iterations(ITERATIONS).run(i ->
            {
               int r = helpers.start();
               loops[0].publishOnly(r);
               helpers.await();
               for (HotPathPubSub<?> loop : loops)
                  loop.awaitReceived();
               last[0] = r;
            });
            for (HotPathPubSub<?> loop : loops)
               loop.verify(last[0]);
            result.assertAllocationFree();
            helpers.assertMeasured(result);
         }
      }
      finally
      {
         for (HotPathPubSub<?> loop : loops)
         {
            if (loop != null)
               loop.close();
         }
      }
   }

   /**
    * Fan-in: four threads publish at the same time on four publishers of one topic (two of them in another participant),
    * and one subscription receives all of them: concurrent writers on one reader (with intraprocess delivery the
    * publishing threads deliver themselves; Fast-DDS calls the listener of one reader one thread at a time). On a callback
    * executor with two workers, the notifications compete to schedule the drain task, and a drain task that a worker
    * starts while the other worker still drains finds the dispatch busy and leaves a flag for it.
    */
   private void testManyPublishersOneSubscription(boolean onExecutor) throws InterruptedException
   {
      int publishers = HELPERS + 1;
      // Two workers: a drain task scheduled while another worker drains runs at the same time and finds the dispatch busy
      ROS2CallbackExecutor executor = onExecutor ? new ROS2CallbackExecutor("alloc-gate-fan-in-callbacks", 2, 64, null) : null;
      ROS2Node otherPublisherNode = new ROS2Node("alloc_gate_fan_in_publisher");
      ROS2Node receivingNode = onExecutor ? new ROS2Node("alloc_gate_fan_in_executor", ROS2NodeOptions.defaults().callbackExecutor(executor)) : subscriptionNode;
      ROS2Topic<sensor_msgs.JointState> topic = new ROS2Topic<>(PubSubTestTools.uniqueTopicName("alloc_gate_fan_in"), sensor_msgs.JointState.class);
      ROS2QoSProfile qos = PubSubTestTools.reliableKeepLast(64);
      AtomicLong received = new AtomicLong();
      AtomicLong failures = new AtomicLong();
      Thread[] callbackThread = new Thread[1];
      sensor_msgs.JointState receivedMessage = HotPathMessages.JOINT_STATE.create();
      ROS2Subscription<sensor_msgs.JointState> subscription = receivingNode.createSubscription(topic, reader ->
      {
         callbackThread[0] = Thread.currentThread();
         if (reader.read(receivedMessage))
            received.incrementAndGet();
         else
            failures.incrementAndGet();
      }, qos);
      @SuppressWarnings("unchecked") ROS2Publisher<sensor_msgs.JointState>[] writers = new ROS2Publisher[publishers];
      sensor_msgs.JointState[] messages = new sensor_msgs.JointState[publishers];
      try
      {
         for (int p = 0; p < publishers; ++p)
         {
            writers[p] = (p % 2 == 0 ? publisherNode : otherPublisherNode).createPublisher(topic, qos);
            messages[p] = HotPathMessages.JOINT_STATE.create();
            PubSubTestTools.awaitMatched(writers[p], subscription);
         }
         try (Helpers helpers = new Helpers("alloc-gate-fan-in-publisher", HELPERS, (helper, round) ->
         {
            HotPathMessages.JOINT_STATE.update(messages[helper + 1], round);
            writers[helper + 1].publish(messages[helper + 1]);
         }))
         {
            long[] expected = new long[1];
            BooleanSupplier allReceived = () -> received.get() >= expected[0];
            AllocationGate.Result result = AllocationGate.named("fan-in: 4 threads on 4 publishers of one topic, one subscription" + (onExecutor ? " on an executor" : ""))
                                                         .warmup(WARMUP)
                                                         .iterations(ITERATIONS)
                                                         .run(i ->
                                                         {
                                                            expected[0] += publishers;
                                                            int r = helpers.start();
                                                            HotPathMessages.JOINT_STATE.update(messages[0], r);
                                                            writers[0].publish(messages[0]);
                                                            helpers.await();
                                                            SpinWait.await(allReceived, 10_000, "delivery");
                                                         });
            assertEquals(0, failures.get());
            assertEquals(expected[0], received.get());
            result.assertAllocationFree();
            helpers.assertMeasured(result);
            assertTrue(result.hotPathThreadNames().contains(callbackThread[0].getName()), "callback thread measured: " + result.report());
            if (onExecutor)
               assertTrue(callbackThread[0].getName().startsWith("alloc-gate-fan-in-callbacks"), callbackThread[0].getName());
         }
      }
      finally
      {
         for (int p = 0; p < publishers; ++p)
         {
            if (writers[p] != null)
               (p % 2 == 0 ? publisherNode : otherPublisherNode).destroyPublisher(writers[p]);
         }
         receivingNode.destroySubscription(subscription);
         otherPublisherNode.close();
         if (onExecutor)
         {
            receivingNode.close();
            executor.close();
         }
      }
   }

   @Test
   public void testManyPublishersOneSubscription() throws InterruptedException
   {
      testManyPublishersOneSubscription(false);
   }

   @Test
   public void testManyPublishersOneSubscriptionOnExecutor() throws InterruptedException
   {
      testManyPublishersOneSubscription(true);
   }

   /**
    * Two threads poll one subscription at the same time (and read its statistics) while the caller publishes. Eight
    * samples per round, so that the two pollers really contend for the subscription's poll lock (with two, one poller
    * usually took both before the other one woke up).
    */
   @Test
   public void testConcurrentPolling() throws InterruptedException
   {
      try (HotPathPubSub<sensor_msgs.JointState> loop = new HotPathPubSub<>(publisherNode, subscriptionNode, HotPathMessages.JOINT_STATE,
                                                                             PubSubTestTools.reliableKeepLast(64), ReceiveMode.POLL_READ))
      {
         loop.setStatisticsEnabled(true);
         int pollers = 2;
         AtomicLong reads = new AtomicLong();
         sensor_msgs.JointState[] received = new sensor_msgs.JointState[pollers];
         Statistics[] statistics = new Statistics[pollers];
         SampleInfo[] sampleInfos = new SampleInfo[pollers];
         for (int p = 0; p < pollers; ++p)
         {
            received[p] = HotPathMessages.JOINT_STATE.create();
            statistics[p] = new Statistics();
            sampleInfos[p] = new SampleInfo();
         }
         try (Helpers helpers = new Helpers("alloc-gate-poller", pollers, (helper, round) ->
         {
            // Each poller takes what is there; together they take all samples of the round.
            while (reads.get() < (long) SAMPLES_PER_POLLING_ROUND * round)
            {
               boolean ok = helper == 0 ? loop.subscription.read(received[helper]) : loop.subscription.read(received[helper], sampleInfos[helper]);
               if (ok)
                  reads.incrementAndGet();
               else
                  Thread.onSpinWait();
               loop.subscription.readStatistics(MessageStatisticsProvider.MessageMetadataType.SIZE, statistics[helper]);
            }
         }))
         {
            AllocationGate.Result result = AllocationGate.named("2 threads poll one subscription").warmup(WARMUP).iterations(ITERATIONS).run(i ->
            {
               int r = helpers.start();
               for (int k = 0; k < SAMPLES_PER_POLLING_ROUND; ++k)
                  loop.publishOnly(SAMPLES_PER_POLLING_ROUND * r + k);
               helpers.await();
            });
            assertEquals((long) SAMPLES_PER_POLLING_ROUND * helpers.round.get(), reads.get());
            result.assertAllocationFree();
            helpers.assertMeasured(result);
         }
         finally
         {
            for (SampleInfo sampleInfo : sampleInfos)
               sampleInfo.close();
         }
      }
   }

   /**
    * Two threads read the statistics of a publisher and a subscription while the publish and the receive paths record
    * them (statistics locks contended).
    */
   @Test
   public void testStatisticsReadWhileRecording() throws InterruptedException
   {
      try (HotPathPubSub<geometry_msgs.PoseStamped> loop = new HotPathPubSub<>(publisherNode, subscriptionNode, HotPathMessages.POSE_STAMPED,
                                                                                PublishSubscribeAllocationTest.reliable(), ReceiveMode.CALLBACK_READ))
      {
         loop.setStatisticsEnabled(true);
         Statistics[] statistics = {new Statistics(), new Statistics()};
         AtomicLong sum = new AtomicLong();
         try (Helpers helpers = new Helpers("alloc-gate-statistics-reader", 2, (helper, round) ->
         {
            for (int k = 0; k < 20; ++k)
            {
               for (MessageStatisticsProvider.MessageMetadataType type : MessageStatisticsProvider.MessageMetadataType.values)
               {
                  MessageStatisticsProvider provider = helper == 0 ? loop.publisher : loop.subscription;
                  provider.readStatistics(type, statistics[helper]);
                  sum.addAndGet((long) statistics[helper].get(Statistics.StatisticDataType.SAMPLE_COUNT));
                  sum.addAndGet((long) provider.getStatistic(type, Statistics.StatisticDataType.MAXIMUM));
               }
            }
         }))
         {
            int[] last = new int[1];
            AllocationGate.Result result = AllocationGate.named("statistics read while recording").warmup(WARMUP).iterations(ITERATIONS).run(i ->
            {
               helpers.start();
               loop.tick(i);
               helpers.await();
               last[0] = i;
            });
            loop.verify(last[0]);
            result.assertAllocationFree();
            helpers.assertMeasured(result);
         }
      }
   }
}
