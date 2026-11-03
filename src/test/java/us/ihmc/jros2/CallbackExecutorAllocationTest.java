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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import us.ihmc.jros2.HotPathPubSub.ReceiveMode;
import us.ihmc.jros2.allocation.AllocationGate;
import us.ihmc.jros2.allocation.SpinWait;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Allocation gates of subscription callbacks that run on a callback executor ({@link ROS2NodeOptions#callbackExecutor}):
 * the Fast-DDS thread (or, with intraprocess delivery, the publishing thread) schedules a drain task and a worker of
 * {@link ROS2CallbackExecutor} runs the callbacks. Also proves that the gate catches a JDK executor, which allocates.
 */
@Tag("allocation")
public class CallbackExecutorAllocationTest
{
   private static final int WARMUP = PublishSubscribeAllocationTest.WARMUP;
   private static final int ITERATIONS = PublishSubscribeAllocationTest.ITERATIONS;
   private static volatile long sink;

   private static void assertExecutorTick(Executor executor, String workerNamePrefix, ReceiveMode mode, boolean forceV1)
   {
      ROS2Node publisherNode = new ROS2Node("alloc_gate_executor_publisher");
      ROS2Node subscriptionNode = new ROS2Node("alloc_gate_executor_subscriber", ROS2NodeOptions.defaults().callbackExecutor(executor));
      try
      {
         Thread callbackThread = PublishSubscribeAllocationTest.assertTickAllocationFree(publisherNode,
                                                                 subscriptionNode,
                                                                 HotPathMessages.JOINT_STATE,
                                                                 PublishSubscribeAllocationTest.reliable(),
                                                                 mode,
                                                                 forceV1,
                                                                 false,
                                                                 WARMUP,
                                                                 ITERATIONS);
         assertTrue(callbackThread.getName().startsWith(workerNamePrefix), "callbacks run on the executor: " + callbackThread.getName());
      }
      finally
      {
         publisherNode.close();
         subscriptionNode.close();
      }
   }

   @Test
   public void testSingleWorkerCallbackRead()
   {
      try (ROS2CallbackExecutor executor = new ROS2CallbackExecutor("alloc-gate-callbacks"))
      {
         assertExecutorTick(executor, "alloc-gate-callbacks", ReceiveMode.CALLBACK_READ, false);
      }
   }

   @Test
   public void testSingleWorkerCallbackReadSampleInfoForcedAbiV1()
   {
      try (ROS2CallbackExecutor executor = new ROS2CallbackExecutor("alloc-gate-callbacks"))
      {
         assertExecutorTick(executor, "alloc-gate-callbacks", ReceiveMode.CALLBACK_READ_SAMPLE_INFO, true);
      }
   }

   @Test
   public void testTwoWorkersCallbackReadLatest()
   {
      try (ROS2CallbackExecutor executor = new ROS2CallbackExecutor("alloc-gate-callbacks", 2, 64, null))
      {
         assertExecutorTick(executor, "alloc-gate-callbacks", ReceiveMode.CALLBACK_READ_LATEST, false);
      }
   }

   /**
    * Four subscriptions of two nodes share one executor with two workers; every tick publishes on all four topics and
    * waits for the four callbacks.
    */
   @Test
   public void testSharedExecutorSeveralSubscriptions()
   {
      int topics = 4;
      try (ROS2CallbackExecutor executor = new ROS2CallbackExecutor("alloc-gate-shared", 2, 64, null))
      {
         ROS2Node publisherNode = new ROS2Node("alloc_gate_shared_publisher");
         ROS2Node subscriptionNodeA = new ROS2Node("alloc_gate_shared_a", ROS2NodeOptions.defaults().callbackExecutor(executor));
         ROS2Node subscriptionNodeB = new ROS2Node("alloc_gate_shared_b", ROS2NodeOptions.defaults().callbackExecutor(executor));
         @SuppressWarnings("unchecked") HotPathPubSub<sensor_msgs.JointState>[] loops = new HotPathPubSub[topics];
         try
         {
            for (int t = 0; t < topics; ++t)
            {
               loops[t] = new HotPathPubSub<>(publisherNode,
                                              t % 2 == 0 ? subscriptionNodeA : subscriptionNodeB,
                                              HotPathMessages.JOINT_STATE,
                                              PublishSubscribeAllocationTest.reliable(),
                                              ReceiveMode.CALLBACK_READ);
            }
            int[] last = new int[1];
            AllocationGate.Result result = AllocationGate.named("shared executor, 4 subscriptions").warmup(WARMUP).iterations(ITERATIONS).run(i ->
            {
               for (int t = 0; t < topics; ++t)
                  loops[t].publishOnly(i);
               for (int t = 0; t < topics; ++t)
                  loops[t].awaitReceived();
               last[0] = i;
            });
            for (int t = 0; t < topics; ++t)
            {
               loops[t].verify(last[0]);
               assertTrue(loops[t].callbackThread.getName().startsWith("alloc-gate-shared-"), loops[t].callbackThread.getName());
            }
            result.assertAllocationFree();
            assertEquals(0, executor.getQueuedTaskCount());
         }
         finally
         {
            for (HotPathPubSub<?> loop : loops)
            {
               if (loop != null)
                  loop.close();
            }
            publisherNode.close();
            subscriptionNodeA.close();
            subscriptionNodeB.close();
         }
      }
   }

   /**
    * The executor alone: tasks submitted from another thread run on the worker, which parks between tasks.
    */
   @Test
   public void testExecutorAlone()
   {
      try (ROS2CallbackExecutor executor = new ROS2CallbackExecutor("alloc-gate-plain"))
      {
         AtomicLong ran = new AtomicLong();
         long[] submitted = new long[1];
         Runnable task = ran::incrementAndGet;
         BooleanSupplier done = () -> ran.get() == submitted[0];
         AllocationGate.named("ROS2CallbackExecutor execute + idle worker").warmup(20_000).iterations(20_000).assertAllocationFree(i ->
         {
            submitted[0]++;
            executor.execute(task);
            if ((i & 7) == 0)
            {
               // Let the worker go idle (park) now and then.
               SpinWait.await(done, 5_000, "task");
            }
         });
         SpinWait.await(done, 5_000, "task");
      }
   }

   /**
    * Mutation check of the executor path: a JDK ThreadPoolExecutor allocates a queue node per task (and a condition node
    * whenever its worker waits), which the gate reports.
    */
   @Test
   public void testJdkExecutorIsCaught()
   {
      ExecutorService executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
      try
      {
         ROS2Node publisherNode = new ROS2Node("alloc_gate_jdk_executor_publisher");
         ROS2Node subscriptionNode = new ROS2Node("alloc_gate_jdk_executor_subscriber", ROS2NodeOptions.defaults().callbackExecutor(executor));
         try (HotPathPubSub<sensor_msgs.JointState> loop = new HotPathPubSub<>(publisherNode,
                                                                                subscriptionNode,
                                                                                HotPathMessages.JOINT_STATE,
                                                                                PublishSubscribeAllocationTest.reliable(),
                                                                                ReceiveMode.CALLBACK_READ))
         {
            AllocationGate.Result result = AllocationGate.named("JDK ThreadPoolExecutor").warmup(WARMUP).iterations(ITERATIONS).run(i ->
            {
               loop.tick(i);
               sink += i;
            });
            assertFalse(result.isAllocationFree(), result.report());
            assertTrue(result.report().contains("LinkedBlockingQueue") || result.threadBytesPass.hotPathBytes >= ITERATIONS * 24L, result.report());
         }
         finally
         {
            publisherNode.close();
            subscriptionNode.close();
         }
      }
      finally
      {
         executor.shutdownNow();
      }
   }
}
