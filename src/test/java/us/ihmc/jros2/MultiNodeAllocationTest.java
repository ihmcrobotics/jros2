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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Allocation gate of a whole robot-like process: several nodes (separate participants, nodes sharing one participant
 * through a {@link ROS2Context}, an {@link AsyncROS2Node}, a node with a callback executor) and eight topics of
 * different types and QoS, with callbacks and polling, all ticking at once.
 */
@Tag("allocation")
public class MultiNodeAllocationTest
{
   @Test
   public void testSeveralNodesAndTopics()
   {
      List<AutoCloseable> resources = new ArrayList<>();
      List<HotPathPubSub<?>> loops = new ArrayList<>();
      try
      {
         ROS2Context context = ROS2Context.create();
         resources.add(context);
         ROS2Node contextNodeA = new ROS2Node("alloc_gate_context_a", ROS2NodeOptions.defaults().context(context));
         ROS2Node contextNodeB = new ROS2Node("alloc_gate_context_b", ROS2NodeOptions.defaults().namespace("/robot").context(context));
         ROS2CallbackExecutor executor = new ROS2CallbackExecutor("alloc-gate-multi-callbacks");
         ROS2Node executorNode = new ROS2Node("alloc_gate_multi_executor", ROS2NodeOptions.defaults().callbackExecutor(executor));
         AsyncROS2Node asyncNode = new AsyncROS2Node("alloc_gate_multi_async");
         ROS2Node plainNode = new ROS2Node("alloc_gate_multi_plain");
         // Closed in reverse order: nodes first, then the executor and the context.
         resources.add(executor);
         resources.add(contextNodeA);
         resources.add(contextNodeB);
         resources.add(executorNode);
         resources.add(asyncNode);
         resources.add(plainNode);

         ROS2QoSProfile reliable = PublishSubscribeAllocationTest.reliable();
         ROS2QoSProfile bestEffort = PublishSubscribeAllocationTest.bestEffort();
         // Within the shared participant
         loops.add(new HotPathPubSub<>(contextNodeA, contextNodeB, HotPathMessages.JOINT_STATE, reliable, ReceiveMode.CALLBACK_READ));
         loops.add(new HotPathPubSub<>(contextNodeB, contextNodeA, HotPathMessages.POSE_STAMPED, reliable, ReceiveMode.POLL_READ_LATEST));
         // Across participants
         loops.add(new HotPathPubSub<>(contextNodeA, executorNode, HotPathMessages.MARKER_ARRAY, reliable, ReceiveMode.CALLBACK_READ_SAMPLE_INFO));
         loops.add(new HotPathPubSub<>(asyncNode, plainNode, HotPathMessages.STRING, reliable, ReceiveMode.CALLBACK_READ));
         loops.add(new HotPathPubSub<>(asyncNode, contextNodeB, HotPathMessages.CUSTOM, reliable, ReceiveMode.POLL_READ));
         loops.add(new HotPathPubSub<>(plainNode, executorNode, HotPathMessages.JOINT_STATE, bestEffort, ReceiveMode.CALLBACK_READ_LATEST));
         loops.add(new HotPathPubSub<>(plainNode, asyncNode, HotPathMessages.POSE_STAMPED, reliable, ReceiveMode.CALLBACK_READ));
         loops.add(new HotPathPubSub<>(executorNode, contextNodeA, HotPathMessages.STRING, bestEffort, ReceiveMode.POLL_READ_SAMPLE_INFO));
         for (HotPathPubSub<?> loop : loops)
            loop.setStatisticsEnabled(true);

         @SuppressWarnings("unchecked") HotPathPubSub<?>[] ticking = loops.toArray(new HotPathPubSub[0]);
         int[] last = new int[1];
         AllocationGate.Result result = AllocationGate.named("6 nodes, 8 topics").warmup(PublishSubscribeAllocationTest.WARMUP).iterations(1_000).run(i ->
         {
            for (HotPathPubSub<?> loop : ticking)
               loop.tick(i);
            for (HotPathPubSub<?> loop : ticking)
               loop.queries();
            last[0] = i;
         });
         for (HotPathPubSub<?> loop : loops)
            loop.verify(last[0]);
         result.assertAllocationFree();
      }
      finally
      {
         for (HotPathPubSub<?> loop : loops)
            loop.close();
         for (int r = resources.size() - 1; r >= 0; --r)
         {
            try
            {
               resources.get(r).close();
            }
            catch (Exception e)
            {
               throw new IllegalStateException(e);
            }
         }
      }
   }

   /**
    * Fan-out: one publisher, four subscriptions of its topic: one in the publisher's own node (same participant), one
    * inline callback and one on a callback executor in other participants, and one that is polled with a SampleInfo. Every
    * tick waits until all four have the sample.
    */
   @Test
   public void testOnePublisherManySubscriptions()
   {
      ROS2CallbackExecutor executor = new ROS2CallbackExecutor("alloc-gate-fan-out-callbacks");
      ROS2Node publisherNode = new ROS2Node("alloc_gate_fan_out_publisher");
      ROS2Node inlineNode = new ROS2Node("alloc_gate_fan_out_inline");
      ROS2Node executorNode = new ROS2Node("alloc_gate_fan_out_executor", ROS2NodeOptions.defaults().callbackExecutor(executor));
      ROS2Node pollingNode = new ROS2Node("alloc_gate_fan_out_polling");
      SampleInfo sampleInfo = new SampleInfo();
      try
      {
         ROS2Topic<sensor_msgs.JointState> topic = new ROS2Topic<>(PubSubTestTools.uniqueTopicName("alloc_gate_fan_out"), sensor_msgs.JointState.class);
         ROS2QoSProfile qos = PublishSubscribeAllocationTest.reliable();
         ROS2Node[] callbackNodes = {publisherNode, inlineNode, executorNode};
         AtomicLong[] received = new AtomicLong[callbackNodes.length];
         Thread[] callbackThreads = new Thread[callbackNodes.length];
         List<ROS2Subscription<sensor_msgs.JointState>> subscriptions = new ArrayList<>();
         for (int s = 0; s < callbackNodes.length; ++s)
         {
            int index = s;
            AtomicLong count = new AtomicLong();
            received[s] = count;
            sensor_msgs.JointState message = HotPathMessages.JOINT_STATE.create();
            subscriptions.add(callbackNodes[s].createSubscription(topic, reader ->
            {
               callbackThreads[index] = Thread.currentThread();
               if (reader.read(message))
                  count.incrementAndGet();
            }, qos));
         }
         ROS2Subscription<sensor_msgs.JointState> polled = pollingNode.createSubscription(topic, qos);
         ROS2Publisher<sensor_msgs.JointState> publisher = publisherNode.createPublisher(topic, qos);
         for (ROS2Subscription<sensor_msgs.JointState> subscription : subscriptions)
            PubSubTestTools.awaitMatched(publisher, subscription);
         PubSubTestTools.awaitMatched(publisher, polled);

         sensor_msgs.JointState message = HotPathMessages.JOINT_STATE.create();
         sensor_msgs.JointState polledMessage = HotPathMessages.JOINT_STATE.create();
         long[] published = new long[1];
         long[] polledCount = new long[1];
         BooleanSupplier allReceived = () -> received[0].get() >= published[0] && received[1].get() >= published[0] && received[2].get() >= published[0];
         BooleanSupplier unread = () -> polled.getUnreadMessageCount() > 0;
         int[] last = new int[1];
         AllocationGate.Result result = AllocationGate.named("fan-out: 1 publisher, 4 subscriptions").warmup(PublishSubscribeAllocationTest.WARMUP).iterations(2_000).run(i ->
         {
            HotPathMessages.JOINT_STATE.update(message, i);
            published[0]++;
            publisher.publish(message);
            SpinWait.await(allReceived, 10_000, "delivery to the callbacks");
            SpinWait.await(unread, 10_000, "delivery to the polled subscription");
            if (polled.read(polledMessage, sampleInfo) && sampleInfo.hasValidData())
               polledCount[0]++;
            last[0] = i;
         });
         assertEquals(published[0], polledCount[0]);
         assertEquals(last[0], HotPathMessages.JOINT_STATE.sequence(polledMessage));
         result.assertAllocationFree();
         for (Thread thread : callbackThreads)
            assertTrue(result.hotPathThreadNames().contains(thread.getName()), thread.getName() + " measured: " + result.report());
         assertTrue(callbackThreads[2].getName().startsWith("alloc-gate-fan-out-callbacks"), callbackThreads[2].getName());
      }
      finally
      {
         sampleInfo.close();
         publisherNode.close();
         inlineNode.close();
         executorNode.close();
         pollingNode.close();
         executor.close();
      }
   }
}
