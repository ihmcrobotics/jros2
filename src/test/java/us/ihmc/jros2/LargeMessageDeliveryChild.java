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

import sensor_msgs.Image;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

/**
 * Child JVM of {@link LargeMessageDeliveryTest}, started with intraprocess delivery OFF: large images between two nodes
 * (participants) of this process go through the shared-memory transport. Publishes images at a fixed rate with the
 * {@link System#nanoTime()} of the publish call in the header stamp; an inline callback records the one-way latency.
 * Prints, per case:
 * <pre>
 * RESULT &lt;case&gt; size=.. sent=.. received=.. p50Ms=.. p90Ms=.. maxMs=.. atLeast10Ms=..
 * SEGMENT &lt;publisher&gt; bytes=..   (Linux: size of the shared-memory segment the publisher's participant created)
 * </pre>
 * Cases: "default-*" publish from a node with jros2's default transports, "fastdds-*" from a node with Fast-DDS's default
 * shared-memory transport (512 KiB segment, {@code shmSegmentSize(0)}); the subscriptions are on a node with the defaults.
 */
public final class LargeMessageDeliveryChild
{
   static final int SIZE_1MB = 983040;     // 640x512 rgb8
   static final int SIZE_2_7MB = 2764800;  // 1280x720 rgb8
   static final int RATE_HZ = 25;
   static final int WARMUP = 30;
   static final int MEASURED = 60;

   private LargeMessageDeliveryChild()
   {
   }

   public static void main(String[] args) throws Exception
   {
      System.setProperty("jros2.node.printout", "false");
      jros2.load();
      if (jros2.get().intraprocessDelivery())
      {
         System.out.println("ERROR intraprocess delivery is on");
         System.exit(2);
      }

      String prefix = "/jros2_large_delivery_" + ProcessHandle.current().pid();
      ROS2Node subscriberNode = new ROS2Node("large_delivery_sub");
      ROS2Node defaultPublisherNode = newPublisherNode("large_delivery_pub", ROS2NodeOptions.defaults(), "default");
      ROS2Node fastddsPublisherNode = newPublisherNode("large_delivery_pub_fastdds",
                                                       ROS2NodeOptions.defaults().shmSegmentSize(ROS2NodeOptions.FASTDDS_DEFAULT_SHM_SEGMENT_SIZE),
                                                       "fastdds");
      try
      {
         run("default-reliable-1mb", defaultPublisherNode, subscriberNode, prefix + "/a", SIZE_1MB, ROS2QoSProfile.RELIABLE);
         run("default-reliable-2.7mb", defaultPublisherNode, subscriberNode, prefix + "/b", SIZE_2_7MB, ROS2QoSProfile.RELIABLE);
         run("default-best_effort-2.7mb", defaultPublisherNode, subscriberNode, prefix + "/c", SIZE_2_7MB, ROS2QoSProfile.BEST_EFFORT);
         // Fast-DDS's 512 KiB segment: the RTPS repair delays every RELIABLE sample, BEST_EFFORT loses them
         run("fastdds-reliable-2.7mb", fastddsPublisherNode, subscriberNode, prefix + "/d", SIZE_2_7MB, ROS2QoSProfile.RELIABLE);
         run("fastdds-best_effort-2.7mb", fastddsPublisherNode, subscriberNode, prefix + "/e", SIZE_2_7MB, ROS2QoSProfile.BEST_EFFORT);
      }
      finally
      {
         fastddsPublisherNode.close();
         defaultPublisherNode.close();
         subscriberNode.close();
      }
      System.out.println("DONE");
      System.exit(0);
   }

   /** Creates a node and prints the size of the shared-memory segment its participant created (Linux). */
   private static ROS2Node newPublisherNode(String name, ROS2NodeOptions options, String label)
   {
      Map<String, Long> before = segments();
      ROS2Node node = new ROS2Node(name, options);
      Map<String, Long> after = segments();
      after.keySet().removeAll(before.keySet());
      for (Map.Entry<String, Long> segment : after.entrySet())
         System.out.println("SEGMENT " + label + " name=" + segment.getKey() + " bytes=" + segment.getValue());
      return node;
   }

   /** Fast-DDS segments in /dev/shm: "fastdds_" followed by 16 hex digits (not the lock files, ports or mutexes). */
   private static Map<String, Long> segments()
   {
      Map<String, Long> segments = new HashMap<>();
      File[] files = new File(ROS2SharedMemoryConfiguration.SHARED_MEMORY_DIRECTORY).listFiles();
      if (files != null)
      {
         for (File file : files)
         {
            if (file.getName().matches("fastdds_[0-9a-f]{16}"))
               segments.put(file.getName(), file.length());
         }
      }
      return segments;
   }

   private static void run(String name, ROS2Node publisherNode, ROS2Node subscriberNode, String topicName, int size, ROS2QoSProfile qos) throws Exception
   {
      Image tx = image(size);
      Image rx = image(size);
      long[] latencies = new long[MEASURED];
      int[] received = new int[1];
      ROS2Topic<Image> topic = new ROS2Topic<>(topicName, Image.class);
      ROS2Subscription<Image> subscription = subscriberNode.createSubscription(topic, reader ->
      {
         if (!reader.read(rx))
            return;
         long now = System.nanoTime();
         if (rx.getIsBigendian() == 1 && received[0] < latencies.length)
            latencies[received[0]++] = now - stamp(rx);
      }, qos);
      ROS2Publisher<Image> publisher = publisherNode.createPublisher(topic, qos);
      if (!publisher.waitForSubscription(30_000) || !subscription.waitForPublisher(30_000))
         throw new IllegalStateException("no match on " + topicName);
      Thread.sleep(300);

      long period = 1_000_000_000L / RATE_HZ;
      long next = System.nanoTime() + period;
      for (int i = 0; i < WARMUP + MEASURED; ++i)
      {
         long sleep = next - System.nanoTime();
         if (sleep > 0)
            LockSupport.parkNanos(sleep);
         tx.setIsBigendian((byte) (i < WARMUP ? 0 : 1));
         long start = System.nanoTime();
         tx.getHeader().getStamp().setSec((int) (start >>> 32));
         tx.getHeader().getStamp().setNanosec((int) start);
         publisher.publish(tx);
         next += period;
      }
      long deadline = System.nanoTime() + 2_000_000_000L;
      while (received[0] < MEASURED && System.nanoTime() < deadline)
         Thread.sleep(10);
      Thread.sleep(100);
      publisherNode.destroyPublisher(publisher);
      subscriberNode.destroySubscription(subscription);

      int count = received[0];
      long[] sorted = Arrays.copyOf(latencies, count);
      Arrays.sort(sorted);
      int atLeast10Ms = 0;
      for (long latency : sorted)
      {
         if (latency >= 10_000_000L)
            atLeast10Ms++;
      }
      System.out.println("RESULT " + name + " size=" + size + " sent=" + MEASURED + " received=" + count + " p50Ms=" + millis(percentile(sorted, 0.5)) + " p90Ms="
                         + millis(percentile(sorted, 0.9)) + " maxMs=" + millis(count == 0 ? -1 : sorted[count - 1]) + " atLeast10Ms=" + atLeast10Ms);
   }

   private static long stamp(Image image)
   {
      return ((long) image.getHeader().getStamp().getSec() << 32) | (image.getHeader().getStamp().getNanosec() & 0xFFFFFFFFL);
   }

   private static long percentile(long[] sorted, double quantile)
   {
      if (sorted.length == 0)
         return -1;
      int rank = (int) Math.ceil(quantile * sorted.length);
      return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
   }

   private static String millis(long nanos)
   {
      return String.format(Locale.ROOT, "%.3f", nanos / 1e6);
   }

   static Image image(int size)
   {
      Image image = new Image();
      image.getHeader().setFrameId("camera");
      image.setHeight(1);
      image.setWidth(size);
      image.setEncoding("mono8");
      image.setStep(size);
      image.getData().ensureMinCapacity(size);
      for (int i = 0; i < size; ++i)
         image.getData().add((byte) i);
      return image;
   }
}
