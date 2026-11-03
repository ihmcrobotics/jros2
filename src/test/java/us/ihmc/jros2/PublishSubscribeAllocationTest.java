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
import us.ihmc.jros2.HotPathMessages.MessageKind;
import us.ihmc.jros2.HotPathPubSub.ReceiveMode;
import us.ihmc.jros2.allocation.AllocationGate;
import us.ihmc.jros2.allocation.SpinWait;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Allocation gates of the synchronous publish/receive control tick, end to end: {@link ROS2Publisher#publish} on the
 * caller thread (serialization, native write, Fast-DDS), delivery to a subscription of another participant, and the
 * subscription side on whichever thread receives (callback inline on the Fast-DDS thread, or polling
 * read/readLatest/read with SampleInfo on the caller), with realistic message types, RELIABLE and BEST_EFFORT, both native
 * ABI paths and statistics on. Every thread of the JVM is measured (see {@link AllocationGate}); the Gradle tasks run this
 * class with intraprocess delivery OFF and FULL, with the agent and with escape analysis disabled.
 */
@Tag("allocation")
public class PublishSubscribeAllocationTest
{
   /** Past the C2 compile thresholds of every method on the path (see AllocationGate on JIT warm-up). */
   static final int WARMUP = 5_000;
   static final int ITERATIONS = 2_000;

   private static ROS2Node publisherNode;
   private static ROS2Node subscriptionNode;
   private static volatile long sink;

   @BeforeAll
   public static void createNodes()
   {
      publisherNode = new ROS2Node("alloc_gate_publisher");
      subscriptionNode = new ROS2Node("alloc_gate_subscriber");
   }

   @AfterAll
   public static void closeNodes()
   {
      publisherNode.close();
      subscriptionNode.close();
   }

   static ROS2QoSProfile reliable()
   {
      return PubSubTestTools.reliableKeepLast(10);
   }

   static ROS2QoSProfile bestEffort()
   {
      ROS2QoSProfile qos = PubSubTestTools.reliableKeepLast(10);
      qos.reliability(ROS2QoSProfile.Reliability.BEST_EFFORT);
      return qos;
   }

   /**
    * @return the thread that ran the last callback (null in polling modes)
    */
   static <T extends ROS2Message<T>> Thread assertTickAllocationFree(ROS2Node publisherNode,
                                                                   ROS2Node subscriptionNode,
                                                                   MessageKind<T> kind,
                                                                   ROS2QoSProfile qos,
                                                                   ReceiveMode mode,
                                                                   boolean forceV1,
                                                                   boolean statistics,
                                                                   int warmup,
                                                                   int iterations)
   {
      String name = kind + " " + mode + " " + qos.getReliability() + (forceV1 ? " ABIv1" : "") + (statistics ? " statistics" : "");
      try (PubSubTestTools.AbiScope abi = PubSubTestTools.abi(forceV1);
           HotPathPubSub<T> loop = new HotPathPubSub<>(publisherNode, subscriptionNode, kind, qos, mode))
      {
         abi.assertPath(loop.publisher);
         abi.assertPath(loop.subscription);
         loop.setStatisticsEnabled(statistics);
         int[] last = new int[1];
         AllocationGate.Result result = AllocationGate.named(name).warmup(warmup).iterations(iterations).run(i ->
                                                                                                         {
                                                                                                            loop.tick(i);
                                                                                                            sink += loop.queries();
                                                                                                            last[0] = i;
                                                                                                         });
         loop.verify(last[0]);
         result.assertAllocationFree();
         Thread callbackThread = loop.callbackThread;
         if (mode.callback)
         {
            assertTrue(result.hotPathThreadNames().contains(callbackThread.getName()),
                       "the callback thread '" + callbackThread.getName() + "' was measured: " + result.report());
         }
         return callbackThread;
      }
   }

   private static <T extends ROS2Message<T>> void assertTick(MessageKind<T> kind, ROS2QoSProfile qos, ReceiveMode mode, boolean forceV1)
   {
      assertTickAllocationFree(publisherNode, subscriptionNode, kind, qos, mode, forceV1, false, WARMUP, ITERATIONS);
   }

   /**
    * The allocation test tasks set the delivery mode with a system property: check that it is in effect, so that the
    * intraprocess tasks really measure intraprocess delivery (the callback runs inside publish() on the caller) and the
    * others network delivery (the callback runs on a Fast-DDS thread attached to the JVM).
    */
   @Test
   public void testDeliveryModeIsTheRequestedOne()
   {
      String requested = System.getProperty("fastdds.intraprocess.delivery");
      if (requested != null)
         assertEquals(Boolean.parseBoolean(requested), PubSubTestTools.intraprocessDelivery());
      try (HotPathPubSub<std_msgs.String_> loop = new HotPathPubSub<>(publisherNode, subscriptionNode, HotPathMessages.STRING, reliable(),
                                                                       ReceiveMode.CALLBACK_READ))
      {
         loop.tick(0);
         loop.verify(0);
         if (PubSubTestTools.intraprocessDelivery())
            assertSame(Thread.currentThread(), loop.callbackThread, "intraprocess delivery runs the callback inside publish()");
         else
            assertEquals(ROS2Participant.NATIVE_CALLBACK_THREAD_NAME, loop.callbackThread.getName());
      }
   }

   /* Message types, callback inline, RELIABLE */

   @Test
   public void testStringCallback()
   {
      assertTick(HotPathMessages.STRING, reliable(), ReceiveMode.CALLBACK_READ, false);
   }

   @Test
   public void testPoseStampedCallback()
   {
      assertTick(HotPathMessages.POSE_STAMPED, reliable(), ReceiveMode.CALLBACK_READ, false);
   }

   @Test
   public void testJointStateCallback()
   {
      assertTick(HotPathMessages.JOINT_STATE, reliable(), ReceiveMode.CALLBACK_READ, false);
   }

   @Test
   public void testMarkerArrayCallback()
   {
      assertTick(HotPathMessages.MARKER_ARRAY, reliable(), ReceiveMode.CALLBACK_READ, false);
   }

   @Test
   public void testCustomNestedTypeCallback()
   {
      assertTick(HotPathMessages.CUSTOM, reliable(), ReceiveMode.CALLBACK_READ, false);
   }

   /* Receive modes x reliability x native ABI path (JointState) */

   @Test
   public void testCallbackReadSampleInfo()
   {
      assertTick(HotPathMessages.JOINT_STATE, reliable(), ReceiveMode.CALLBACK_READ_SAMPLE_INFO, false);
   }

   @Test
   public void testCallbackReadLatest()
   {
      assertTick(HotPathMessages.JOINT_STATE, reliable(), ReceiveMode.CALLBACK_READ_LATEST, false);
   }

   @Test
   public void testPollRead()
   {
      assertTick(HotPathMessages.JOINT_STATE, reliable(), ReceiveMode.POLL_READ, false);
   }

   @Test
   public void testPollReadSampleInfo()
   {
      assertTick(HotPathMessages.JOINT_STATE, reliable(), ReceiveMode.POLL_READ_SAMPLE_INFO, false);
   }

   @Test
   public void testPollReadLatest()
   {
      assertTick(HotPathMessages.JOINT_STATE, reliable(), ReceiveMode.POLL_READ_LATEST, false);
   }

   @Test
   public void testBestEffortCallback()
   {
      assertTick(HotPathMessages.JOINT_STATE, bestEffort(), ReceiveMode.CALLBACK_READ, false);
   }

   @Test
   public void testBestEffortPollRead()
   {
      assertTick(HotPathMessages.JOINT_STATE, bestEffort(), ReceiveMode.POLL_READ, false);
   }

   @Test
   public void testForcedAbiV1Callback()
   {
      assertTick(HotPathMessages.JOINT_STATE, reliable(), ReceiveMode.CALLBACK_READ, true);
   }

   @Test
   public void testForcedAbiV1CallbackReadSampleInfo()
   {
      assertTick(HotPathMessages.JOINT_STATE, reliable(), ReceiveMode.CALLBACK_READ_SAMPLE_INFO, true);
   }

   @Test
   public void testForcedAbiV1PollRead()
   {
      assertTick(HotPathMessages.JOINT_STATE, reliable(), ReceiveMode.POLL_READ, true);
   }

   @Test
   public void testForcedAbiV1PollReadLatest()
   {
      assertTick(HotPathMessages.JOINT_STATE, reliable(), ReceiveMode.POLL_READ_LATEST, true);
   }

   @Test
   public void testForcedAbiV1BestEffortCallback()
   {
      assertTick(HotPathMessages.MARKER_ARRAY, bestEffort(), ReceiveMode.CALLBACK_READ, true);
   }

   /* Statistics on (publish age through the header, receive age through the sample info) */

   @Test
   public void testStatisticsCallback()
   {
      assertTickAllocationFree(publisherNode, subscriptionNode, HotPathMessages.POSE_STAMPED, reliable(), ReceiveMode.CALLBACK_READ, false, true, WARMUP, ITERATIONS);
   }

   @Test
   public void testStatisticsPollRead()
   {
      assertTickAllocationFree(publisherNode, subscriptionNode, HotPathMessages.JOINT_STATE, reliable(), ReceiveMode.POLL_READ, false, true, WARMUP, ITERATIONS);
   }

   @Test
   public void testStatisticsForcedAbiV1Callback()
   {
      assertTickAllocationFree(publisherNode, subscriptionNode, HotPathMessages.CUSTOM, reliable(), ReceiveMode.CALLBACK_READ, true, true, WARMUP, ITERATIONS);
   }

   /**
    * A callback that publishes: the subscription of the other node answers every JointState with a PoseStamped on a
    * second topic (on the Fast-DDS thread, or inside this thread's publish with intraprocess delivery), and the tick
    * waits for the answer.
    */
   @Test
   public void testCallbackPublishesResponse()
   {
      ROS2QoSProfile qos = reliable();
      ROS2Topic<sensor_msgs.JointState> requestTopic = new ROS2Topic<>(PubSubTestTools.uniqueTopicName("alloc_gate_request"), sensor_msgs.JointState.class);
      ROS2Topic<geometry_msgs.PoseStamped> responseTopic = new ROS2Topic<>(PubSubTestTools.uniqueTopicName("alloc_gate_response"),
                                                                           geometry_msgs.PoseStamped.class);
      ROS2Publisher<geometry_msgs.PoseStamped> responder = subscriptionNode.createPublisher(responseTopic, qos);
      sensor_msgs.JointState request = HotPathMessages.JOINT_STATE.create();
      geometry_msgs.PoseStamped response = HotPathMessages.POSE_STAMPED.create();
      ROS2Subscription<sensor_msgs.JointState> server = subscriptionNode.createSubscription(requestTopic, reader ->
      {
         if (reader.read(request))
         {
            HotPathMessages.POSE_STAMPED.update(response, HotPathMessages.JOINT_STATE.sequence(request));
            responder.publish(response);
         }
      }, qos);
      AtomicLong responses = new AtomicLong();
      int[] lastResponse = {-1};
      geometry_msgs.PoseStamped received = HotPathMessages.POSE_STAMPED.create();
      ROS2Subscription<geometry_msgs.PoseStamped> client = publisherNode.createSubscriptionSampler(responseTopic, sample ->
      {
         lastResponse[0] = HotPathMessages.POSE_STAMPED.sequence(sample);
         responses.incrementAndGet();
      }, qos);
      ROS2Publisher<sensor_msgs.JointState> requester = publisherNode.createPublisher(requestTopic, qos);
      try
      {
         PubSubTestTools.awaitMatched(requester, server);
         PubSubTestTools.awaitMatched(responder, client);
         sensor_msgs.JointState message = HotPathMessages.JOINT_STATE.create();
         long[] expected = new long[1];
         BooleanSupplier answered = () -> responses.get() >= expected[0];
         int[] last = new int[1];
         AllocationGate.Result result = AllocationGate.named("callback publishes a response").warmup(WARMUP).iterations(ITERATIONS).run(i ->
         {
            HotPathMessages.JOINT_STATE.update(message, i);
            expected[0]++;
            requester.publish(message);
            SpinWait.await(answered, 10_000, "response");
            last[0] = i;
         });
         assertEquals(last[0], lastResponse[0]);
         result.assertAllocationFree();
      }
      finally
      {
         publisherNode.destroyPublisher(requester);
         publisherNode.destroySubscription(client);
         subscriptionNode.destroySubscription(server);
         subscriptionNode.destroyPublisher(responder);
      }
   }
}
