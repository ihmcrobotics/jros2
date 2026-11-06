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

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.jros2.HotPathPubSub.ReceiveMode;
import us.ihmc.jros2.allocation.AllocationGate;

import java.util.ArrayList;
import java.util.List;

/**
 * The child JVM of {@link ROS2AllocationTest}: a fast version of the allocation gates (see
 * {@code PublishSubscribeAllocationTest} and the other classes tagged "allocation") that runs in the normal test suite.
 * It measures every thread with HotSpot's allocated-bytes counters and the monitor pass, with zero tolerance, but with
 * escape analysis on and without the instrumenter, so a scalar-replaced allocation could escape it: the allocation test
 * tasks are the real gate. Prints one {@code RESULT} line per gate and exits with 1 if any allocated.
 */
public final class ROS2AllocationQuickChild
{
   private static final int WARMUP = 3_000;
   private static final int ITERATIONS = 1_000;
   private static volatile long sink;

   private ROS2AllocationQuickChild()
   {
   }

   private interface Gate
   {
      AllocationGate.Result run() throws Exception;
   }

   private static <T extends ROS2Message<T>> AllocationGate.Result tick(String name,
                                                                       ROS2Node publisherNode,
                                                                       ROS2Node subscriptionNode,
                                                                       HotPathMessages.MessageKind<T> kind,
                                                                       ReceiveMode mode,
                                                                       boolean forceV1)
   {
      try (PubSubTestTools.AbiScope abi = PubSubTestTools.abi(forceV1);
           HotPathPubSub<T> loop = new HotPathPubSub<>(publisherNode, subscriptionNode, kind, PubSubTestTools.reliableKeepLast(10), mode))
      {
         abi.assertPath(loop.publisher);
         int[] last = new int[1];
         AllocationGate.Result result = AllocationGate.named(name).quick().warmup(WARMUP).iterations(ITERATIONS).run(i ->
         {
            loop.tick(i);
            sink += loop.queries();
            last[0] = i;
         });
         loop.verify(last[0]);
         return result;
      }
   }

   public static void main(String[] args) throws Exception
   {
      List<Gate> gates = new ArrayList<>();
      ROS2Node publisherNode = new ROS2Node("alloc_quick_publisher");
      ROS2Node subscriptionNode = new ROS2Node("alloc_quick_subscriber");
      AsyncROS2Node asyncNode = new AsyncROS2Node("alloc_quick_async");
      ROS2CallbackExecutor executor = new ROS2CallbackExecutor("alloc-quick-callbacks");
      ROS2Node executorNode = new ROS2Node("alloc_quick_executor", ROS2NodeOptions.defaults().callbackExecutor(executor));

      gates.add(() -> tick("sync publish, inline callback", publisherNode, subscriptionNode, HotPathMessages.JOINT_STATE, ReceiveMode.CALLBACK_READ, false));
      gates.add(() -> tick("sync publish, polling read with SampleInfo, ABI v1", publisherNode, subscriptionNode, HotPathMessages.MARKER_ARRAY,
                           ReceiveMode.POLL_READ_SAMPLE_INFO, true));
      gates.add(() -> tick("async publish, polling readLatest", asyncNode, subscriptionNode, HotPathMessages.CUSTOM, ReceiveMode.POLL_READ_LATEST, false));
      gates.add(() -> tick("callback on ROS2CallbackExecutor", publisherNode, executorNode, HotPathMessages.STRING, ReceiveMode.CALLBACK_READ_SAMPLE_INFO,
                           false));
      gates.add(() ->
      {
         sensor_msgs.JointState message = HotPathMessages.JOINT_STATE.create();
         sensor_msgs.JointState decoded = HotPathMessages.JOINT_STATE.create();
         CDRBuffer buffer = new CDRBuffer();
         buffer.ensureRemainingCapacity(CDRBuffer.PAYLOAD_HEADER.length + message.calculateSizeBytes(0));
         return AllocationGate.named("CDR round trip").quick().warmup(20_000).iterations(20_000).run(i ->
         {
            HotPathMessages.JOINT_STATE.update(message, i);
            buffer.rewind();
            buffer.writePayloadHeader();
            message.serialize(buffer);
            buffer.rewind();
            buffer.readPayloadHeader();
            decoded.deserialize(buffer);
            sink += HotPathMessages.JOINT_STATE.sequence(decoded);
         });
      });

      boolean allocationFree = true;
      try
      {
         for (Gate gate : gates)
         {
            AllocationGate.Result result = gate.run();
            allocationFree &= result.isAllocationFree();
            System.out.println("RESULT " + (result.isAllocationFree() ? "ok " : "ALLOCATES ") + result.name);
            if (!result.isAllocationFree())
               System.out.println(result.report());
         }
      }
      finally
      {
         publisherNode.close();
         subscriptionNode.close();
         asyncNode.close();
         executorNode.close();
         executor.close();
      }
      System.out.println(allocationFree ? "ALLOCATION-QUICK OK" : "ALLOCATION-QUICK FAILED");
      System.out.flush();
      System.exit(allocationFree ? 0 : 1);
   }
}
