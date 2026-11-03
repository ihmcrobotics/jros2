/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.jros2;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static us.ihmc.jros2.AndroidTestSupport.DELIVERY_TIMEOUT_MS;
import static us.ihmc.jros2.AndroidTestSupport.DISCOVERY_TIMEOUT_MS;
import static us.ihmc.jros2.AndroidTestSupport.domainId;
import static us.ihmc.jros2.AndroidTestSupport.publishUntil;
import static us.ihmc.jros2.AndroidTestSupport.uniqueNodeName;
import static us.ihmc.jros2.AndroidTestSupport.uniqueTopicName;
import static us.ihmc.jros2.AndroidTestSupport.waitUntil;

/**
 * Exercises the real library (Java + JNI + Fast-DDS) on a device: loopback publish/subscribe through the DDS transports
 * of one process, the CDR code paths that differ per message type, the polling API, the async publisher and node teardown.
 * These are the paths that broke below API 33-35 (Thread.onSpinWait, absolute bulk Buffer.put, String.formatted).
 */
@RunWith(AndroidJUnit4.class)
public class ROS2PubSubOnDeviceTest
{
   @Rule
   public Timeout timeout = new Timeout(120, TimeUnit.SECONDS);

   private final List<ROS2Node> nodes = new ArrayList<>();

   @After
   public void closeNodes()
   {
      AndroidTestSupport.closeQuietly(nodes);
      nodes.clear();
   }

   private ROS2Node node(String name)
   {
      ROS2Node node = new ROS2Node(uniqueNodeName(name), domainId());
      nodes.add(node);
      return node;
   }

   private static ROS2QoSProfile reliable(int depth)
   {
      ROS2QoSProfile qos = new ROS2QoSProfile();
      qos.reliability(ROS2QoSProfile.Reliability.RELIABLE);
      qos.durability(ROS2QoSProfile.Durability.VOLATILE);
      qos.history(ROS2QoSProfile.History.KEEP_LAST);
      qos.depth(depth);
      return qos;
   }

   private static <T extends ROS2Message<T>> void awaitMatched(ROS2Publisher<T> publisher, ROS2Subscription<T> subscription)
   {
      assertTrue("publisher did not match the subscription", publisher.waitForSubscription(DISCOVERY_TIMEOUT_MS));
      assertTrue("subscription did not match the publisher", subscription.waitForPublisher(DISCOVERY_TIMEOUT_MS));
   }

   private String stringRoundTrip(String text)
   {
      ROS2Node node = node("string_loopback");
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(uniqueTopicName("chatter"), std_msgs.String_.class);
      AtomicReference<String> received = new AtomicReference<>();

      ROS2Subscription<std_msgs.String_> subscription = node.createSubscription(topic, reader ->
      {
         std_msgs.String_ message = reader.read();
         if (message != null)
         {
            received.compareAndSet(null, message.getDataAsString());
         }
      }, reliable(10));
      ROS2Publisher<std_msgs.String_> publisher = node.createPublisher(topic, reliable(10));
      awaitMatched(publisher, subscription);

      std_msgs.String_ message = new std_msgs.String_();
      message.getData().append(text);
      assertTrue("no message received within " + DELIVERY_TIMEOUT_MS + " ms", publishUntil(publisher, message, () -> received.get() != null, 200));
      return received.get();
   }

   @Test
   public void stringLoopbackAscii()
   {
      assertEquals("Hello from jros2 on Android", stringRoundTrip("Hello from jros2 on Android"));
   }

   /**
    * ROS 2 strings are UTF-8 on the wire. Latin-1, CJK and a supplementary code point (surrogate pair in Java) must survive.
    */
   @Test
   public void stringLoopbackWithNonAsciiText()
   {
      String text = "Grüße aus jros2 ✓ ロボット 🤖";
      String received = stringRoundTrip(text);
      // Compare escaped forms: a broken decoder produces control characters that would make the JUnit XML report unreadable.
      assertEquals(escape(text), escape(received));
   }

   private static String escape(String text)
   {
      StringBuilder escaped = new StringBuilder();
      for (int i = 0; i < text.length(); ++i)
      {
         char c = text.charAt(i);
         if (c >= 0x20 && c < 0x7f)
         {
            escaped.append(c);
         }
         else
         {
            escaped.append("\\u").append(String.format(Locale.ROOT, "%04x", (int) c));
         }
      }
      return escaped.toString();
   }

   /**
    * sensor_msgs/Image exercises nested messages, a string and a uint8[] sequence that has to grow between samples
    * (the IDL byte sequence and the publisher/subscription CDR buffers).
    */
   @Test
   public void imageLoopbackWithGrowingData()
   {
      ROS2Node node = node("image_loopback");
      ROS2Topic<sensor_msgs.Image> topic = new ROS2Topic<>(uniqueTopicName("image"), sensor_msgs.Image.class);
      ConcurrentLinkedQueue<sensor_msgs.Image> received = new ConcurrentLinkedQueue<>();

      ROS2Subscription<sensor_msgs.Image> subscription = node.createSubscription(topic, reader ->
      {
         sensor_msgs.Image image = reader.read();
         if (image != null)
         {
            received.add(image);
         }
      }, reliable(10));
      ROS2Publisher<sensor_msgs.Image> publisher = node.createPublisher(topic, reliable(10));
      awaitMatched(publisher, subscription);

      int[][] sizes = {{4, 4}, {64, 48}, {320, 240}, {640, 480}};
      for (int[] size : sizes)
      {
         int width = size[0];
         int height = size[1];
         byte[] pixels = new byte[width * height * 3];
         for (int i = 0; i < pixels.length; ++i)
         {
            pixels[i] = (byte) (i * 31 + width);
         }

         sensor_msgs.Image image = new sensor_msgs.Image();
         image.getHeader().getFrameId().append("camera_").append(width);
         image.setWidth(width);
         image.setHeight(height);
         image.setEncoding("rgb8");
         image.setStep(width * 3);
         image.getData().addAll(pixels);

         // publishUntil publishes again whenever a sample takes longer than the period to arrive (a 640x480 image is
         // ~900 KB of UDP fragments), so extra copies of the previous size can still arrive during this iteration. The
         // image of this size is therefore picked by its frame id, which encodes the width.
         String frameId = "camera_" + width;
         received.clear();
         assertTrue("no " + width + "x" + height + " image received",
                    publishUntil(publisher, image, () -> findByFrameId(received, frameId) != null, 500));

         sensor_msgs.Image copy = findByFrameId(received, frameId);
         assertNotNull(copy);
         assertEquals(width, copy.getWidth());
         assertEquals(height, copy.getHeight());
         assertEquals(width * 3, copy.getStep());
         assertEquals("rgb8", copy.getEncodingAsString());
         assertEquals("camera_" + width, copy.getHeader().getFrameId().toString());
         assertEquals(pixels.length, copy.getData().size());
         byte[] actual = new byte[pixels.length];
         for (int i = 0; i < actual.length; ++i)
         {
            actual[i] = copy.getData().get(i);
         }
         assertArrayEquals("pixel data of " + width + "x" + height, pixels, actual);
      }
   }

   private static sensor_msgs.Image findByFrameId(Iterable<sensor_msgs.Image> images, String frameId)
   {
      for (sensor_msgs.Image image : images)
      {
         if (frameId.equals(image.getHeader().getFrameId().toString()))
         {
            return image;
         }
      }
      return null;
   }

   /**
    * Subscription without callback: samples are taken with read(T) / readLatest(T) from the test thread.
    */
   @Test
   public void pollingRead()
   {
      ROS2Node node = node("polling");
      ROS2Topic<std_msgs.Int32> topic = new ROS2Topic<>(uniqueTopicName("polling"), std_msgs.Int32.class);
      ROS2Subscription<std_msgs.Int32> subscription = node.createSubscription(topic, reliable(100));
      ROS2Publisher<std_msgs.Int32> publisher = node.createPublisher(topic, reliable(100));
      awaitMatched(publisher, subscription);

      std_msgs.Int32 message = new std_msgs.Int32();
      int count = 20;
      for (int i = 1; i <= count; ++i)
      {
         message.setData(i);
         publisher.publish(message);
      }

      assertTrue("not all samples arrived", waitUntil(() -> subscription.getUnreadMessageCount() >= count, DELIVERY_TIMEOUT_MS));
      std_msgs.Int32 sample = new std_msgs.Int32();
      for (int i = 1; i <= count; ++i)
      {
         assertTrue("read() returned false for sample " + i, subscription.read(sample));
         assertEquals(i, sample.getData());
      }
      assertFalse("read() must return false once every sample was taken", subscription.read(sample));
      assertEquals(0, subscription.getUnreadMessageCount());
      assertNull(subscription.readLatest());

      for (int i = 1; i <= 3; ++i)
      {
         message.setData(100 + i);
         publisher.publish(message);
      }
      assertTrue(waitUntil(() -> subscription.getUnreadMessageCount() >= 3, DELIVERY_TIMEOUT_MS));
      assertEquals(3, subscription.readLatest(sample));
      assertEquals(103, sample.getData());
      // readLatest takes every pending sample, so nothing may be left unread (the count used to drift).
      assertEquals(0, subscription.getUnreadMessageCount());
      assertFalse(subscription.read(sample));
   }

   /**
    * AsyncROS2Publisher hands samples to the node's publish thread (uses the lock-free queue that called Thread.onSpinWait).
    */
   @Test
   public void asyncPublisherDeliversAllMessages()
   {
      AsyncROS2Node asyncNode = new AsyncROS2Node(uniqueNodeName("async"), domainId());
      nodes.add(asyncNode);
      ROS2Topic<std_msgs.Int32> topic = new ROS2Topic<>(uniqueTopicName("async"), std_msgs.Int32.class);
      Set<Integer> received = ConcurrentHashMap.newKeySet();

      ROS2Subscription<std_msgs.Int32> subscription = asyncNode.createSubscription(topic, reader ->
      {
         std_msgs.Int32 message = reader.read();
         if (message != null)
         {
            received.add(message.getData());
         }
      }, reliable(200));
      AsyncROS2Publisher<std_msgs.Int32> publisher = asyncNode.createPublisher(topic, reliable(200), 64);
      awaitMatched(publisher, subscription);

      std_msgs.Int32 message = new std_msgs.Int32();
      int count = 50;
      for (int i = 0; i < count; ++i)
      {
         message.setData(i);
         publisher.publish(message);
         waitUntil(() -> false, 2); // pace below the queue capacity
      }

      assertTrue("received " + received.size() + "/" + count, waitUntil(() -> received.size() == count, DELIVERY_TIMEOUT_MS));
   }

   /**
    * Several producer threads publish through several AsyncROS2Publishers of one AsyncROS2Node at full speed, so the node's
    * multi-producer task queue sees contention (the code path that used Thread.onSpinWait, API 33). Messages may be dropped
    * when a publisher queue is full, so the test checks that no jros2 thread died and that every topic still delivers.
    */
   @Test
   public void asyncPublishersUnderContention() throws InterruptedException
   {
      List<Throwable> uncaught = new CopyOnWriteArrayList<>();
      Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
      // Record instead of crashing the instrumentation process, so the failure is reported by this test.
      Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> uncaught.add(new AssertionError("uncaught in " + thread.getName(), throwable)));
      try
      {
         AsyncROS2Node asyncNode = new AsyncROS2Node(uniqueNodeName("async_contention"), domainId());
         nodes.add(asyncNode);

         int topics = 4;
         int messagesPerThread = 2000;
         int sentinel = -1;
         List<AsyncROS2Publisher<std_msgs.Int32>> publishers = new ArrayList<>();
         List<Set<Integer>> received = new ArrayList<>();
         for (int t = 0; t < topics; ++t)
         {
            ROS2Topic<std_msgs.Int32> topic = new ROS2Topic<>(uniqueTopicName("contention_" + t), std_msgs.Int32.class);
            Set<Integer> values = ConcurrentHashMap.newKeySet();
            received.add(values);
            ROS2Subscription<std_msgs.Int32> subscription = asyncNode.createSubscription(topic, reader ->
            {
               std_msgs.Int32 message = reader.read();
               if (message != null)
               {
                  values.add(message.getData());
               }
            }, reliable(100));
            AsyncROS2Publisher<std_msgs.Int32> publisher = asyncNode.createPublisher(topic, reliable(100), 64);
            awaitMatched(publisher, subscription);
            publishers.add(publisher);
         }

         List<Thread> producers = new ArrayList<>();
         for (int t = 0; t < topics; ++t)
         {
            AsyncROS2Publisher<std_msgs.Int32> publisher = publishers.get(t);
            Thread producer = new Thread(() ->
            {
               std_msgs.Int32 message = new std_msgs.Int32();
               for (int i = 0; i < messagesPerThread; ++i)
               {
                  message.setData(i);
                  publisher.publish(message);
               }
            }, "producer-" + t);
            producers.add(producer);
            producer.start();
         }
         for (Thread producer : producers)
         {
            producer.join(DELIVERY_TIMEOUT_MS);
            // publish() of an AsyncROS2Publisher only enqueues; a producer that is still running is stuck in it.
            assertFalse(producer.getName() + " is still blocked in publish() (uncaught: " + uncaught + ")", producer.isAlive());
         }

         for (int t = 0; t < topics; ++t)
         {
            Set<Integer> values = received.get(t);
            std_msgs.Int32 message = new std_msgs.Int32();
            message.setData(sentinel);
            assertTrue("topic " + t + " stopped delivering (uncaught: " + uncaught + ")",
                       publishUntil(publishers.get(t), message, () -> values.contains(sentinel), 100));
            assertTrue("topic " + t + " received nothing from the burst", values.size() > 1);
         }
         assertTrue("jros2 threads died: " + uncaught, uncaught.isEmpty());
      }
      finally
      {
         Thread.setDefaultUncaughtExceptionHandler(previous);
      }
   }

   /**
    * Discovery between two participants (two nodes) in one process goes through the device's network stack
    * (SPDP multicast or the localhost locators), unlike endpoints of one participant.
    */
   @Test
   public void twoNodesDiscoverEachOther()
   {
      ROS2Node publisherNode = node("talker");
      ROS2Node subscriptionNode = node("listener");
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(uniqueTopicName("two_nodes"), std_msgs.String_.class);
      AtomicInteger count = new AtomicInteger();

      ROS2Subscription<std_msgs.String_> subscription = subscriptionNode.createSubscription(topic, reader ->
      {
         if (reader.read() != null)
         {
            count.incrementAndGet();
         }
      }, reliable(10));
      ROS2Publisher<std_msgs.String_> publisher = publisherNode.createPublisher(topic, reliable(10));
      awaitMatched(publisher, subscription);

      std_msgs.String_ message = new std_msgs.String_();
      message.getData().append("two nodes");
      assertTrue(publishUntil(publisher, message, () -> count.get() > 0, 200));
   }

   @Test
   public void nodeCloseReleasesEndpoints()
   {
      ROS2Node node = node("close");
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(uniqueTopicName("close"), std_msgs.String_.class);
      ROS2Publisher<std_msgs.String_> publisher = node.createPublisher(topic, reliable(10));
      ROS2Subscription<std_msgs.String_> subscription = node.createSubscription(topic, reader -> reader.read(), reliable(10));
      assertFalse(node.isClosed());

      node.close();
      assertTrue(node.isClosed());
      assertTrue(publisher.isClosed());
      assertTrue(subscription.isClosed());
      assertTrue(node.getPublishers().isEmpty());
      assertTrue(node.getSubscriptions().isEmpty());

      node.close(); // idempotent
      assertTrue(node.isClosed());

      // A new node in the same process (and domain) still works after the teardown.
      assertEquals("still works", stringRoundTrip("still works"));
   }
}
