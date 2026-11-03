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

/**
 * Background activity: nodes with matched RELIABLE and BEST_EFFORT endpoints (callback, polling, executor and async
 * publisher) sit idle for 6 seconds, which covers two SPDP announcement periods, writer heartbeats and liveliness. No
 * thread attached to the JVM may allocate meanwhile (Fast-DDS threads that never call into Java are not visible here;
 * their native allocations are measured by the native gate).
 */
@Tag("allocation")
public class BackgroundActivityAllocationTest
{
   @Test
   public void testIdleNodesDoNotAllocate()
   {
      ROS2CallbackExecutor executor = new ROS2CallbackExecutor("alloc-gate-idle-callbacks");
      ROS2Node publisherNode = new ROS2Node("alloc_gate_idle_publisher");
      ROS2Node subscriptionNode = new ROS2Node("alloc_gate_idle_subscriber");
      ROS2Node executorNode = new ROS2Node("alloc_gate_idle_executor", ROS2NodeOptions.defaults().callbackExecutor(executor));
      AsyncROS2Node asyncNode = new AsyncROS2Node("alloc_gate_idle_async");
      HotPathPubSub<?>[] loops = new HotPathPubSub[4];
      try
      {
         loops[0] = new HotPathPubSub<>(publisherNode, subscriptionNode, HotPathMessages.JOINT_STATE, PublishSubscribeAllocationTest.reliable(),
                                        ReceiveMode.CALLBACK_READ);
         loops[1] = new HotPathPubSub<>(asyncNode, subscriptionNode, HotPathMessages.POSE_STAMPED, PublishSubscribeAllocationTest.reliable(),
                                        ReceiveMode.POLL_READ);
         loops[2] = new HotPathPubSub<>(publisherNode, executorNode, HotPathMessages.STRING, PublishSubscribeAllocationTest.bestEffort(),
                                        ReceiveMode.CALLBACK_READ);
         loops[3] = new HotPathPubSub<>(asyncNode, executorNode, HotPathMessages.CUSTOM, PublishSubscribeAllocationTest.reliable(),
                                        ReceiveMode.CALLBACK_READ_SAMPLE_INFO);
         // Some traffic first, so that every thread that delivers data has done so before the idle windows.
         for (int i = 0; i < 2_000; ++i)
         {
            for (HotPathPubSub<?> loop : loops)
               loop.tick(i);
         }
         AllocationGate.named("idle nodes, 6 s").warmup(0).iterations(600).run(i -> LockSupport.parkNanos(10_000_000L)).assertAllocationFree();
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
