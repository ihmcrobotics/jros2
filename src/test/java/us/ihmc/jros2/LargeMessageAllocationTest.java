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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static us.ihmc.jros2.PublishSubscribeAllocationTest.assertTickAllocationFree;
import static us.ihmc.jros2.PublishSubscribeAllocationTest.bestEffort;
import static us.ihmc.jros2.PublishSubscribeAllocationTest.reliable;

/**
 * Allocation gates of the control tick with large messages that Fast-DDS fragments: a 2.76 MB sensor_msgs/Image
 * (1280 x 720 rgb8, RELIABLE, also through an AsyncROS2Publisher), a 480 kB image (BEST_EFFORT) and a 320 kB
 * sensor_msgs/PointCloud2, callback and polling, both native ABI paths. The subscription's receive buffers and the
 * received message grow to the payload size during the warm-up (allowed: growing beyond the previous high-water mark);
 * after that nothing may allocate on any thread.
 */
@Tag("allocation")
public class LargeMessageAllocationTest
{
   private static final int IMAGE_WARMUP = 600;
   private static final int IMAGE_ITERATIONS = 150;
   private static final int CLOUD_WARMUP = 1_000;
   private static final int CLOUD_ITERATIONS = 400;

   private static ROS2Node publisherNode;
   private static ROS2Node subscriptionNode;

   @BeforeAll
   public static void createNodes()
   {
      publisherNode = new ROS2Node("alloc_gate_large_publisher");
      subscriptionNode = new ROS2Node("alloc_gate_large_subscriber");
   }

   @AfterAll
   public static void closeNodes()
   {
      publisherNode.close();
      subscriptionNode.close();
   }

   @Test
   public void testImageCallback()
   {
      assertTickAllocationFree(publisherNode, subscriptionNode, HotPathMessages.IMAGE, reliable(), ReceiveMode.CALLBACK_READ, false, false, IMAGE_WARMUP, IMAGE_ITERATIONS);
   }

   @Test
   public void testImagePollRead()
   {
      assertTickAllocationFree(publisherNode, subscriptionNode, HotPathMessages.IMAGE, reliable(), ReceiveMode.POLL_READ, false, false, IMAGE_WARMUP, IMAGE_ITERATIONS);
   }

   /**
    * BEST_EFFORT with a fragmented image that fits Fast-DDS's default 512 kB shared-memory segment. The 2.76 MB image is
    * not reliably delivered BEST_EFFORT between two participants of one process with the default transports (in one
    * stress run none of 114 samples sent at 0.2 to 3 s intervals arrived; in other runs most did): its fragments do not fit
    * the segment at once, and best-effort never repairs a lost fragment.
    */
   @Test
   public void testSegmentSizedImageBestEffortCallback()
   {
      assertTickAllocationFree(publisherNode, subscriptionNode, HotPathMessages.SEGMENT_SIZED_IMAGE, bestEffort(), ReceiveMode.CALLBACK_READ, false, false,
                               CLOUD_WARMUP, CLOUD_ITERATIONS);
   }

   @Test
   public void testImageForcedAbiV1Callback()
   {
      assertTickAllocationFree(publisherNode, subscriptionNode, HotPathMessages.IMAGE, reliable(), ReceiveMode.CALLBACK_READ, true, false, IMAGE_WARMUP, IMAGE_ITERATIONS);
   }

   @Test
   public void testImageForcedAbiV1PollReadSampleInfo()
   {
      assertTickAllocationFree(publisherNode, subscriptionNode, HotPathMessages.IMAGE, reliable(), ReceiveMode.POLL_READ_SAMPLE_INFO, true, false, IMAGE_WARMUP, IMAGE_ITERATIONS);
   }

   @Test
   public void testPointCloudCallback()
   {
      assertTickAllocationFree(publisherNode, subscriptionNode, HotPathMessages.POINT_CLOUD, reliable(), ReceiveMode.CALLBACK_READ, false, false, CLOUD_WARMUP, CLOUD_ITERATIONS);
   }

   @Test
   public void testPointCloudBestEffortPollReadLatest()
   {
      assertTickAllocationFree(publisherNode, subscriptionNode, HotPathMessages.POINT_CLOUD, bestEffort(), ReceiveMode.POLL_READ_LATEST, false, false, CLOUD_WARMUP, CLOUD_ITERATIONS);
   }

   @Test
   public void testPointCloudForcedAbiV1StatisticsCallbackReadSampleInfo()
   {
      assertTickAllocationFree(publisherNode, subscriptionNode, HotPathMessages.POINT_CLOUD, reliable(), ReceiveMode.CALLBACK_READ_SAMPLE_INFO, true, true, CLOUD_WARMUP, CLOUD_ITERATIONS);
   }

   /**
    * The 2.76 MB image through an {@link AsyncROS2Publisher}: the caller copies it into a queue slot, the node's publish
    * thread serializes and writes it. The queue has 4 slots, so that the warm-up grows every slot's message to the image
    * size (allowed: high-water mark) without needing 64 copies of the image.
    */
   @Test
   public void testImageAsyncPublisherCallback()
   {
      AsyncROS2Node asyncNode = new AsyncROS2Node("alloc_gate_large_async");
      try (HotPathPubSub<sensor_msgs.Image> loop = new HotPathPubSub<>(asyncNode, subscriptionNode, HotPathMessages.IMAGE, reliable(), ReceiveMode.CALLBACK_READ, 4))
      {
         int[] last = new int[1];
         AllocationGate.Result result = AllocationGate.named("sensor_msgs/Image through an AsyncROS2Publisher").warmup(IMAGE_WARMUP).iterations(IMAGE_ITERATIONS).run(i ->
         {
            loop.tick(i);
            last[0] = i;
         });
         loop.verify(last[0]);
         result.assertAllocationFree();
         assertTrue(result.hotPathThreadNames().contains(asyncNode.getPublishThread().getName()), "the publish thread was measured: " + result.report());
      }
      finally
      {
         asyncNode.close();
      }
   }
}
