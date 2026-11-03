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

import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Allocation gate of a paced control loop over a long window: 1 kHz for 10 s per pass, sleeping until the next period
 * between ticks like a real controller (the other gates tick back to back for a fraction of a second). Each tick publishes
 * and receives on four topics (sync publisher with an inline callback and statistics, AsyncROS2Publisher polled with a
 * SampleInfo, a subscription on a callback executor, BEST_EFFORT) and makes the per-tick queries. Between ticks every
 * thread goes idle, so this covers what only happens with idle gaps or after seconds: parking and unparking of the
 * publish thread and of the executor worker on every tick, time-based code (rate limiters, statistics periods), periodic
 * JVM work (monitor deflation, safepoints) and anything Fast-DDS does periodically on threads attached to the JVM.
 */
@Tag("allocation")
public class SustainedLoopAllocationTest
{
   private static final long PERIOD_NANOS = 1_000_000L;
   private static final int WARMUP = 5_000;
   private static final int ITERATIONS = 10_000;

   @Test
   public void testPacedControlLoop()
   {
      ROS2CallbackExecutor executor = new ROS2CallbackExecutor("alloc-gate-sustained-callbacks");
      ROS2Node publisherNode = new ROS2Node("alloc_gate_sustained_publisher");
      ROS2Node subscriptionNode = new ROS2Node("alloc_gate_sustained_subscriber");
      ROS2Node executorNode = new ROS2Node("alloc_gate_sustained_executor", ROS2NodeOptions.defaults().callbackExecutor(executor));
      AsyncROS2Node asyncNode = new AsyncROS2Node("alloc_gate_sustained_async");
      HotPathPubSub<?>[] loops = new HotPathPubSub[4];
      try
      {
         loops[0] = new HotPathPubSub<>(publisherNode, subscriptionNode, HotPathMessages.JOINT_STATE, PublishSubscribeAllocationTest.reliable(),
                                        ReceiveMode.CALLBACK_READ);
         loops[0].setStatisticsEnabled(true);
         loops[1] = new HotPathPubSub<>(asyncNode, subscriptionNode, HotPathMessages.POSE_STAMPED, PublishSubscribeAllocationTest.reliable(),
                                        ReceiveMode.POLL_READ_SAMPLE_INFO);
         loops[2] = new HotPathPubSub<>(publisherNode, executorNode, HotPathMessages.MARKER_ARRAY, PublishSubscribeAllocationTest.reliable(),
                                        ReceiveMode.CALLBACK_READ_SAMPLE_INFO);
         loops[3] = new HotPathPubSub<>(asyncNode, executorNode, HotPathMessages.STRING, PublishSubscribeAllocationTest.bestEffort(),
                                        ReceiveMode.CALLBACK_READ_LATEST);
         long[] nextTick = new long[1];
         long[] overruns = new long[1];
         int[] last = new int[1];
         AllocationGate.Result result = AllocationGate.named("1 kHz paced control loop, 10 s per pass")
                                                      .warmup(WARMUP)
                                                      .iterations(ITERATIONS)
                                                      .beforeWindow(() -> nextTick[0] = 0)
                                                      .run(i ->
                                                      {
                                                         if (nextTick[0] == 0)
                                                            nextTick[0] = System.nanoTime();
                                                         for (HotPathPubSub<?> loop : loops)
                                                            loop.tick(i);
                                                         for (HotPathPubSub<?> loop : loops)
                                                            loop.queries();
                                                         last[0] = i;
                                                         // Sleep until the next period; after an overrun, start the next tick at once.
                                                         nextTick[0] += PERIOD_NANOS;
                                                         long sleep = nextTick[0] - System.nanoTime();
                                                         if (sleep > 0)
                                                         {
                                                            LockSupport.parkNanos(sleep);
                                                         }
                                                         else
                                                         {
                                                            overruns[0]++;
                                                            nextTick[0] = System.nanoTime();
                                                         }
                                                      });
         for (HotPathPubSub<?> loop : loops)
            loop.verify(last[0]);
         result.assertAllocationFree();
         assertTrue(result.hotPathThreadNames().contains(asyncNode.getPublishThread().getName()), "publish thread measured: " + result.report());
         assertTrue(result.hotPathThreadNames().contains(executor.getWorkerThreads()[0].getName()), "executor worker measured: " + result.report());
         // Most ticks must have slept, or this is just another back-to-back gate (the interpreter is too slow for 1 kHz).
         if (!System.getProperty("java.vm.info", "").contains("interpreted"))
            assertTrue(overruns[0] < (WARMUP + 3L * ITERATIONS) / 2, overruns[0] + " overruns");
      }
      finally
      {
         for (HotPathPubSub<?> loop : loops)
         {
            if (loop != null)
               loop.close();
         }
         publisherNode.close();
         subscriptionNode.close();
         executorNode.close();
         asyncNode.close();
         executor.close();
      }
   }
}
