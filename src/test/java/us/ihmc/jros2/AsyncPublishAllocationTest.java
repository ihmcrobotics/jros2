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
 * Allocation gates of {@link AsyncROS2Publisher}: the caller's {@code publish()} (copy into a queue slot), the node's
 * publish thread (serialize and write, then park until the next message) and the receiving side, idle (one message per
 * tick, the publish thread parks in between), in bursts, with several producer threads on one publisher, with several
 * publishers on one node, and with a full queue (dropped messages).
 */
@Tag("allocation")
public class AsyncPublishAllocationTest
{
   private static final int WARMUP = PublishSubscribeAllocationTest.WARMUP;
   private static final int ITERATIONS = PublishSubscribeAllocationTest.ITERATIONS;
   private static final String NODE_NAME = "alloc_gate_async";

   private static AsyncROS2Node asyncNode;
   private static ROS2Node subscriptionNode;

   @BeforeAll
   public static void createNodes()
   {
      asyncNode = new AsyncROS2Node(NODE_NAME);
      subscriptionNode = new ROS2Node("alloc_gate_async_subscriber");
   }

   @AfterAll
   public static void closeNodes()
   {
      asyncNode.close();
      subscriptionNode.close();
   }

   private static void assertPublishThreadMeasured(AllocationGate.Result result)
   {
      String publishThread = asyncNode.getPublishThread().getName();
      assertEquals("AsyncROS2NodePublishThread-" + NODE_NAME, publishThread);
      assertTrue(result.hotPathThreadNames().contains(publishThread), "the publish thread was measured: " + result.report());
   }

   @Test
   public void testIdleCallback()
   {
      PublishSubscribeAllocationTest.assertTickAllocationFree(asyncNode, subscriptionNode, HotPathMessages.JOINT_STATE,
                                                              PublishSubscribeAllocationTest.reliable(), ReceiveMode.CALLBACK_READ, false, false, WARMUP,
                                                              ITERATIONS);
   }

   @Test
   public void testIdlePollReadForcedAbiV1()
   {
      PublishSubscribeAllocationTest.assertTickAllocationFree(asyncNode, subscriptionNode, HotPathMessages.MARKER_ARRAY,
                                                              PublishSubscribeAllocationTest.reliable(), ReceiveMode.POLL_READ, true, false, WARMUP,
                                                              ITERATIONS);
   }

   @Test
   public void testIdleBestEffortStatistics()
   {
      PublishSubscribeAllocationTest.assertTickAllocationFree(asyncNode, subscriptionNode, HotPathMessages.POSE_STAMPED,
                                                              PublishSubscribeAllocationTest.bestEffort(), ReceiveMode.CALLBACK_READ, false, true, WARMUP,
                                                              ITERATIONS);
   }

   /** 32 messages per tick: the caller fills the queue while the publish thread drains it. */
   @Test
   public void testBurst()
   {
      int burst = 32;
      try (HotPathPubSub<sensor_msgs.JointState> loop = new HotPathPubSub<>(asyncNode,
                                                                             subscriptionNode,
                                                                             HotPathMessages.JOINT_STATE,
                                                                             PubSubTestTools.reliableKeepLast(2 * burst),
                                                                             ReceiveMode.CALLBACK_READ))
      {
         AsyncROS2Publisher<sensor_msgs.JointState> publisher = (AsyncROS2Publisher<sensor_msgs.JointState>) loop.publisher;
         int[] last = new int[1];
         AllocationGate.Result result = AllocationGate.named("async burst of 32").warmup(WARMUP / 10).iterations(ITERATIONS / 10).run(i ->
         {
            for (int k = 0; k < burst; ++k)
            {
               last[0] = i * burst + k;
               loop.publishOnly(last[0]);
            }
            loop.awaitReceived();
         });
         loop.verify(last[0]);
         assertEquals(0, publisher.getDroppedMessageCount());
         result.assertAllocationFree();
         assertPublishThreadMeasured(result);
      }
   }

   /**
    * Three producer threads and the caller publish on one AsyncROS2Publisher at the same time (lock-free multi-producer
    * queue), and two more publishers of the node on other topics are drained by the same publish thread.
    */
   @Test
   public void testConcurrentProducersAndPublishers() throws InterruptedException
   {
      int producers = 3;
      HotPathPubSub<sensor_msgs.JointState> shared = new HotPathPubSub<>(asyncNode, subscriptionNode, HotPathMessages.JOINT_STATE,
                                                                          PubSubTestTools.reliableKeepLast(64), ReceiveMode.CALLBACK_READ);
      HotPathPubSub<std_msgs.String_> second = new HotPathPubSub<>(asyncNode, subscriptionNode, HotPathMessages.STRING,
                                                                    PublishSubscribeAllocationTest.reliable(), ReceiveMode.CALLBACK_READ);
      HotPathPubSub<geometry_msgs.PoseStamped> third = new HotPathPubSub<>(asyncNode, subscriptionNode, HotPathMessages.POSE_STAMPED,
                                                                            PublishSubscribeAllocationTest.reliable(), ReceiveMode.POLL_READ_LATEST);
      AtomicInteger round = new AtomicInteger();
      AtomicLong producerPublishes = new AtomicLong();
      AtomicInteger stop = new AtomicInteger();
      Thread[] threads = new Thread[producers];
      try
      {
         for (int p = 0; p < producers; ++p)
         {
            sensor_msgs.JointState message = HotPathMessages.JOINT_STATE.create();
            threads[p] = new Thread(() ->
            {
               int handled = 0;
               BooleanSupplier next = () -> round.get() != 0 || stop.get() != 0;
               while (stop.get() == 0)
               {
                  int current = round.get();
                  if (current != handled)
                  {
                     handled = current;
                     HotPathMessages.JOINT_STATE.update(message, current);
                     shared.publisher.publish(message);
                     producerPublishes.incrementAndGet();
                  }
                  else
                  {
                     SpinWait.until(next, 1_000_000L);
                  }
               }
            }, "alloc-gate-producer-" + p);
            threads[p].setDaemon(true);
            threads[p].start();
         }
         long[] expectedProducerPublishes = new long[1];
         BooleanSupplier producersDone = () -> producerPublishes.get() >= expectedProducerPublishes[0];
         BooleanSupplier sharedReceived = () -> shared.receivedCount.get() >= expectedProducerPublishes[0] + round.get();
         int[] last = new int[1];
         AllocationGate.Result result = AllocationGate.named("async: 4 producers on one publisher + 2 publishers").warmup(WARMUP).iterations(ITERATIONS).run(i ->
         {
            // Not derived from i, which starts over in every pass of the gate: the expected counts must keep growing.
            int r = round.get() + 1;
            expectedProducerPublishes[0] += producers;
            round.set(r);
            shared.publishOnly(r);
            second.publishOnly(r);
            third.publishOnly(r);
            SpinWait.await(producersDone, 10_000, "producers");
            SpinWait.await(sharedReceived, 10_000, "shared topic delivery");
            second.awaitReceived();
            third.awaitReceived();
            last[0] = r;
         });
         second.verify(last[0]);
         third.verify(last[0]);
         assertEquals(0, ((AsyncROS2Publisher<?>) shared.publisher).getDroppedMessageCount());
         result.assertAllocationFree();
         assertPublishThreadMeasured(result);
         for (Thread thread : threads)
            assertTrue(result.hotPathThreadNames().contains(thread.getName()), "producer measured: " + result.report());
      }
      finally
      {
         stop.set(1);
         for (Thread thread : threads)
         {
            if (thread != null)
               thread.join(5_000);
         }
         shared.close();
         second.close();
         third.close();
      }
   }

   /** A full queue drops messages without allocating (the caller never blocks). */
   @Test
   public void testFullQueueDrops()
   {
      ROS2Topic<geometry_msgs.PoseStamped> topic = new ROS2Topic<>(PubSubTestTools.uniqueTopicName("alloc_gate_async_drop"), geometry_msgs.PoseStamped.class);
      AsyncROS2Publisher<geometry_msgs.PoseStamped> publisher = asyncNode.createPublisher(topic, PublishSubscribeAllocationTest.reliable(), 1);
      try
      {
         geometry_msgs.PoseStamped message = HotPathMessages.POSE_STAMPED.create();
         AllocationGate.named("async full queue").warmup(20_000).iterations(20_000).assertAllocationFree(i ->
         {
            HotPathMessages.POSE_STAMPED.update(message, i);
            publisher.publish(message);
            publisher.offer(message);
         });
         assertTrue(publisher.getDroppedMessageCount() > 0, "the queue of capacity 1 overflowed");
      }
      finally
      {
         asyncNode.destroyPublisher(publisher);
      }
   }
}
