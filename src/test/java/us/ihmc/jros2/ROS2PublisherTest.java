/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import us.ihmc.jros2.ROS2QoSProfile.Durability;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;

public class ROS2PublisherTest
{
   private static final Random RANDOM = new Random(1881108);

   @Test
   @Timeout(30)
   public void testPublisherBasicCreationAndDestruction()
   {
      String topicName = "/ihmc/test_bool";

      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(topicName, example_interfaces.Bool.class);

      assertDoesNotThrow(() ->
      {
         // Create publisher
         ROS2Publisher<example_interfaces.Bool> publisher = ros2Node.createPublisher(topic);

         // Ensure we can destroy a publisher
         ros2Node.destroyPublisher(publisher);

         // Destroying again should be safe
         ros2Node.destroyPublisher(publisher);
      });

      ros2Node.close();
   }

   @Test
   @Timeout(30)
   public void testPublisherConcurrentPublishing()
   {
      String topicName = "/ihmc/test_concurrent_publish";

      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(topicName, example_interfaces.Bool.class);

      assertDoesNotThrow(() ->
      {
         ROS2Publisher<example_interfaces.Bool> publisher = ros2Node.createPublisher(topic);

         // Try publishing concurrently from multiple threads
         Thread[] publishThreads = new Thread[10];
         for (int i = 0; i < publishThreads.length; ++i)
         {
            Thread thread = new Thread(() ->
            {
               LockSupport.parkNanos(RANDOM.nextLong((long) 1E8));
               publisher.publish(new example_interfaces.Bool());
            }, "PublishThread" + i);
            thread.start();
            publishThreads[i] = thread;
         }

         // Ensure all threads complete
         for (Thread thread : publishThreads)
         {
            thread.join(1000);
         }
      });

      ros2Node.close();
   }

   @Test
   @Timeout(30)
   public void testPublisherDestroyWhilePublishing()
   {
      String topicName = "/ihmc/test_destroy_while_publishing";

      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(topicName, example_interfaces.Bool.class);

      assertDoesNotThrow(() ->
      {
         ROS2Publisher<example_interfaces.Bool> publisher = ros2Node.createPublisher(topic);

         // Try destroying while publishing
         Thread publishThread = new Thread(() ->
         {
            while (!Thread.interrupted())
            {
               publisher.publish(new example_interfaces.Bool());
            }
         }, "PublishThread");
         publishThread.start();

         // Destroy the publisher while it's publishing
         ros2Node.destroyPublisher(publisher);

         // Tell the thread to stop
         publishThread.interrupt();
         publishThread.join(1000);
      });

      ros2Node.close();
   }

   @Test
   @EnabledIf("us.ihmc.jros2.ROS2TestTools#supportsROS2PublisherEcho")
   @Timeout(30)
   public void testPublisherTransientLocal() throws IOException, InterruptedException
   {
      final boolean expectedValue = true;
      String topicName = "/ihmc/test_transient_local";

      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(topicName, example_interfaces.Bool.class);

      ROS2QoSProfile publisherQos = new ROS2QoSProfile();
      publisherQos.durability(Durability.TRANSIENT_LOCAL);
      ROS2Publisher<example_interfaces.Bool> publisher = ros2Node.createPublisher(topic, publisherQos);

      // Publish message
      example_interfaces.Bool bool = new example_interfaces.Bool();
      bool.setData(expectedValue);
      publisher.publish(bool);

      // Use ros2 topic echo to verify the published message
      String result = ROS2TestTools.ros2EchoOnce(ros2Node.getDomainId(), topicName);
      assertTrue(result.contains(String.valueOf(expectedValue)), result);

      ros2Node.close();
   }

   @Test
   @Timeout(30)
   public void testPublisherWaitForSubscription() throws InterruptedException
   {
      String topicName = "/ihmc/test_wait_for_subscriber";

      ROS2Node publisherNode = new ROS2Node("publisher_node");
      ROS2Node subscriberNode = new ROS2Node("subscriber_node");
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(topicName, std_msgs.String_.class);

      // Create publisher first
      ROS2Publisher<std_msgs.String_> publisher = publisherNode.createPublisher(topic);

      // No subscribers yet - should timeout
      boolean foundBeforeSubscriber = publisher.waitForSubscription(500);
      assertFalse(foundBeforeSubscriber, "Should not find subscriber before it's created");

      // Now create subscriber
      ROS2Subscription<std_msgs.String_> subscription = subscriberNode.createSubscription(topic, ROS2QoSProfile.DEFAULT);

      // Should discover subscriber now
      boolean foundAfterSubscriber = publisher.waitForSubscription(5000);
      assertTrue(foundAfterSubscriber, "Should discover subscriber after it's created");

      // Subsequent calls should return immediately with true
      boolean foundAgain = publisher.waitForSubscription(100);
      assertTrue(foundAgain, "Should still show subscriber is discovered");

      publisherNode.close();
      subscriberNode.close();
   }

   @Test
   @Timeout(30)
   public void testPublisherGetPublicationMatchedStatus()
   {
      String topicName = "/ihmc/test_publication_matched";

      ROS2Node publisherNode = new ROS2Node("publisher_node");
      ROS2Node subscriberNode = new ROS2Node("subscriber_node");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(topicName, example_interfaces.Bool.class);

      ROS2Publisher<example_interfaces.Bool> publisher = publisherNode.createPublisher(topic);

      // Initially no matches
      assertEquals(0, publisher.getPublicationMatchedStatus(), "Should have 0 matched subscriptions initially");

      // Create first subscription
      ROS2Subscription<example_interfaces.Bool> subscription1 = subscriberNode.createSubscription(topic, ROS2QoSProfile.DEFAULT);
      assertTrue(publisher.waitForSubscription(5000), "Should discover first subscription");
      assertEquals(1, publisher.getPublicationMatchedStatus(), "Should have 1 matched subscription");

      // Create second subscription
      ROS2Subscription<example_interfaces.Bool> subscription2 = subscriberNode.createSubscription(topic, ROS2QoSProfile.DEFAULT);

      // Wait for second subscription to be discovered
      long startTime = System.nanoTime();
      while (publisher.getPublicationMatchedStatus() < 2 && (System.nanoTime() - startTime) < 5_000_000_000L)
      {
         LockSupport.parkNanos(10_000_000); // 10ms
      }

      assertEquals(2, publisher.getPublicationMatchedStatus(), "Should have 2 matched subscriptions");

      publisherNode.close();
      subscriberNode.close();
   }

   @Test
   @Timeout(30)
   public void testPublisherMultiplePublishers()
   {
      String topicName = "/ihmc/test_multiple_publishers";

      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(topicName, example_interfaces.Bool.class);

      assertDoesNotThrow(() ->
      {
         // Create multiple publishers on same topic
         ROS2Publisher<example_interfaces.Bool> publisher1 = ros2Node.createPublisher(topic);
         ROS2Publisher<example_interfaces.Bool> publisher2 = ros2Node.createPublisher(topic);
         ROS2Publisher<example_interfaces.Bool> publisher3 = ros2Node.createPublisher(topic);

         // All should be able to publish
         publisher1.publish(new example_interfaces.Bool());
         publisher2.publish(new example_interfaces.Bool());
         publisher3.publish(new example_interfaces.Bool());
      });

      ros2Node.close();
   }

   @Test
   @Timeout(30)
   public void testPublisherStressTestManyPublishers()
   {
      Instant start = Instant.now();

      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/ihmc/test_many_publishers", example_interfaces.Bool.class);
      ROS2Node publisherNode = new ROS2Node("publisher_node");
      ROS2Node subscriberNode = new ROS2Node("subscriber_node");

      // Create subscription that will receive from many publishers
      AtomicInteger messagesReceived = new AtomicInteger(0);
      ROS2Subscription<example_interfaces.Bool> subscription = subscriberNode.createSubscription(topic, subscriber ->
      {
         example_interfaces.Bool data = new example_interfaces.Bool();
         subscriber.read(data);
         messagesReceived.incrementAndGet();
      }, ROS2QoSProfile.DEFAULT);

      // Wait for subscription to discover publisher before publishing
      subscription.waitForPublisher(5000);

      // Create many publishers on separate threads and publish
      int publisherCount = 20;
      List<Thread> threads = new ArrayList<>();
      example_interfaces.Bool messageToPublish = new example_interfaces.Bool();
      messageToPublish.setData(true);

      for (int i = 0; i < publisherCount; ++i)
      {
         Thread thread = new Thread(() ->
         {
            ROS2Publisher<example_interfaces.Bool> publisher = publisherNode.createPublisher(topic);

            // Wait for subscription to be discovered
            publisher.waitForSubscription(5000);

            // Publish a few messages
            for (int j = 0; j < 5; ++j)
            {
               publisher.publish(messageToPublish);
               LockSupport.parkNanos(1_000_000); // 1ms
            }
         }, "publisher_thread_" + i);
         thread.start();
         threads.add(thread);
      }

      // Wait for all publisher threads to complete
      for (Thread thread : threads)
      {
         try
         {
            thread.join(10000);
         }
         catch (InterruptedException e)
         {
            throw new RuntimeException(e);
         }
      }

      // Should have received messages
      assertTrue(messagesReceived.get() > 0, "Should have received at least some messages from the publishers");

      publisherNode.close();
      subscriberNode.close();

      long durationMillis = start.until(Instant.now(), ChronoUnit.MILLIS);
      jros2.getLogger().fine("Test Duration: " + (durationMillis / 1000) + "s" + (durationMillis % 1000) + "ms");
   }

   @Test
   @Timeout(30)
   public void testPublisherGetTopicName()
   {
      String topicName = "/ihmc/test_topic_name";

      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(topicName, std_msgs.String_.class);

      ROS2Publisher<std_msgs.String_> publisher = ros2Node.createPublisher(topic);

      assertEquals(topicName, publisher.getTopicName(), "Topic name should match");

      ros2Node.close();
   }

   @Test
   @Timeout(30)
   public void testPublisherGetTopicType()
   {
      String topicName = "/ihmc/test_topic_type";

      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(topicName, std_msgs.String_.class);

      ROS2Publisher<std_msgs.String_> publisher = ros2Node.createPublisher(topic);

      assertEquals(std_msgs.String_.class, publisher.getTopicType(), "Topic type should match");

      ros2Node.close();
   }
}
