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
import us.ihmc.jros2.ROS2QoSProfile.History;
import us.ihmc.jros2.ROS2QoSProfile.Reliability;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;

public class ROS2SubscriptionTest
{
   private static final Random RANDOM = new Random(1881108);

   @Test
   @EnabledIf("us.ihmc.jros2.ROS2TestTools#isROS2CLIAvailable")
   @Timeout(30)
   public void testSubscriptionBasicCreationAndDestruction()
   {
      String topicName = "/ihmc/test_bool";

      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(topicName, example_interfaces.Bool.class);

      assertDoesNotThrow(() ->
      {
         ROS2QoSProfile subscriptionQos = new ROS2QoSProfile();
         subscriptionQos.reliability(Reliability.RELIABLE);

         // Create subscription
         ROS2Subscription<example_interfaces.Bool> subscription = ros2Node.createSubscription(topic, reader ->
         {
            example_interfaces.Bool message = new example_interfaces.Bool();
            reader.read(message);
            fail("Should never reach here since we don't publish anything");
         }, subscriptionQos);

         // Ensure we can destroy subscription
         ros2Node.destroySubscription(subscription);

         // Destroying again should be safe
         ros2Node.destroySubscription(subscription);

         // Publish a message to ensure subscription doesn't receive anything after being destroyed
         Process process = ROS2TestTools.launchROS2PublishProcess(ros2Node.getDomainId(),
                                                                  "--once -w 0",
                                                                  topicName,
                                                                  "std_msgs/msg/Bool",
                                                                  "{data: true}",
                                                                  Redirect.INHERIT,
                                                                  Redirect.INHERIT);
         process.waitFor();
      });

      ros2Node.close();
   }

   @Test
   @EnabledIf("us.ihmc.jros2.ROS2TestTools#isROS2CLIAvailable")
   @Timeout(30)
   public void testSubscriptionAllocationFree() throws InterruptedException, IOException
   {
      final String data = "This is a test. This is only a test.";
      final String topicName = "/ihmc/test_allocation_free";

      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(topicName, std_msgs.String_.class);

      ROS2QoSProfile subscriptionQos = new ROS2QoSProfile();
      subscriptionQos.reliability(Reliability.RELIABLE);

      // Allocation-free subscription: allocate message once and reuse
      std_msgs.String_ msg = new std_msgs.String_();
      final Object sync = new Object();
      ros2Node.createSubscription(topic, reader ->
      {
         reader.read(msg);

         synchronized (sync)
         {
            sync.notify();
         }
      }, subscriptionQos);

      // Publish a String message
      Process process = ROS2TestTools.launchROS2PublishProcess(ros2Node.getDomainId(),
                                                               "--once",
                                                               topicName,
                                                               "std_msgs/msg/String",
                                                               "{data: " + data + "}",
                                                               Redirect.INHERIT,
                                                               Redirect.INHERIT);
      // Wait for subscription to receive the message
      synchronized (sync)
      {
         if (msg.getData().isEmpty())
         {
            sync.wait(5000);
         }
      }

      assertEquals(data, msg.getData().toString());

      process.waitFor();
      ros2Node.close();
   }

   @Test
   @EnabledIf("us.ihmc.jros2.ROS2TestTools#isROS2CLIAvailable")
   @Timeout(30)
   public void testSubscriptionWithAllocation() throws InterruptedException, IOException
   {
      final String data = "This is a test. This is only a test.";
      final String topicName = "/ihmc/test_with_allocation";

      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(topicName, std_msgs.String_.class);

      AtomicReference<String> receivedString = new AtomicReference<>("");

      ROS2QoSProfile subscriptionQos = new ROS2QoSProfile();
      subscriptionQos.reliability(Reliability.RELIABLE);

      final Object sync = new Object();
      ros2Node.createSubscription(topic, reader ->
      {
         std_msgs.String_ msg = reader.read();

         synchronized (sync)
         {
            receivedString.set(msg.getData().toString());
            sync.notify();
         }
      }, subscriptionQos);

      // Publish a String message
      Process process = ROS2TestTools.launchROS2PublishProcess(ros2Node.getDomainId(),
                                                               "--once",
                                                               topicName,
                                                               "std_msgs/msg/String",
                                                               "{data: " + data + "}",
                                                               Redirect.INHERIT,
                                                               Redirect.INHERIT);
      // Wait for subscription to receive the message
      synchronized (sync)
      {
         if (receivedString.get().isEmpty())
         {
            sync.wait(5000);
         }
      }

      assertEquals(data, receivedString.get());

      process.waitFor();
      ros2Node.close();
   }

   @Test
   @EnabledIf("us.ihmc.jros2.ROS2TestTools#isROS2CLIAvailable")
   @Timeout(30)
   public void testSubscriptionSampler() throws InterruptedException, IOException
   {
      final String data = "This is a test. This is only a test.";
      final String topicName = "/ihmc/test_sampler";

      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(topicName, std_msgs.String_.class);

      AtomicReference<String> receivedString = new AtomicReference<>("");

      ROS2QoSProfile subscriptionQos = new ROS2QoSProfile();
      subscriptionQos.reliability(Reliability.RELIABLE);

      final Object sync = new Object();
      ros2Node.createSubscriptionSampler(topic, msg ->
      {
         synchronized (sync)
         {
            receivedString.set(msg.getData().toString());
            sync.notify();
         }
      }, subscriptionQos);

      // Publish a String message
      Process process = ROS2TestTools.launchROS2PublishProcess(ros2Node.getDomainId(),
                                                               "--once",
                                                               topicName,
                                                               "std_msgs/msg/String",
                                                               "{data: " + data + "}",
                                                               Redirect.INHERIT,
                                                               Redirect.INHERIT);
      // Wait for subscription to receive the message
      synchronized (sync)
      {
         if (receivedString.get().isEmpty())
         {
            sync.wait(5000);
         }
      }

      assertEquals(data, receivedString.get());

      process.waitFor();
      ros2Node.close();
   }

   @Test
   @EnabledIf("us.ihmc.jros2.ROS2TestTools#isROS2CLIAvailable")
   @Timeout(30)
   public void testSubscriptionWithoutCallback() throws InterruptedException, IOException
   {
      final String data = "This is a test. This is only a test.";
      final String topicName = "/ihmc/test_no_callback";
      final int publishCount = 20;

      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(topicName, std_msgs.String_.class);

      ROS2QoSProfile subscriptionQos = new ROS2QoSProfile();
      subscriptionQos.history(History.KEEP_LAST);
      subscriptionQos.depth(publishCount);
      subscriptionQos.reliability(Reliability.RELIABLE);

      ROS2Subscription<std_msgs.String_> subscription = ros2Node.createSubscription(topic, subscriptionQos);

      // Publish messages
      Process process = ROS2TestTools.launchROS2PublishProcess(ros2Node.getDomainId(),
                                                               "--times " + publishCount + " -r 100000 --qos-depth " + publishCount,
                                                               topicName,
                                                               "std_msgs/msg/String",
                                                               "{data: " + data + "}",
                                                               Redirect.INHERIT,
                                                               Redirect.INHERIT);

      // Wait until subscription receives all messages
      long startTime = System.nanoTime();
      while (subscription.getUnreadMessageCount() < publishCount && System.nanoTime() - startTime < TimeUnit.SECONDS.toNanos(5))
      {
         LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
      }
      assertEquals(publishCount, subscription.getUnreadMessageCount());

      // Read all messages
      int totalRead = 0;
      std_msgs.String_ msg = subscription.read();
      while (msg != null)
      {
         assertEquals(data, msg.getData().toString());
         totalRead++;
         msg = subscription.read();
      }
      assertEquals(publishCount, totalRead);
      assertEquals(0, subscription.getUnreadMessageCount());

      assertNull(subscription.read());
      assertNull(subscription.readLatest());

      process.waitFor();
      ros2Node.close();
   }

   @Test
   @EnabledIf("us.ihmc.jros2.ROS2TestTools#isROS2CLIAvailable")
   @Timeout(30)
   public void testSubscriptionCallbackAndPolling() throws InterruptedException, IOException
   {
      final String data = "This is a test. This is only a test.";
      final String topicName = "/ihmc/test_callback_and_polling";
      final int publishCount = 20;

      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(topicName, std_msgs.String_.class);

      ROS2QoSProfile subscriptionQos = new ROS2QoSProfile();
      subscriptionQos.history(History.KEEP_LAST);
      subscriptionQos.depth(publishCount);
      subscriptionQos.reliability(Reliability.RELIABLE);

      AtomicInteger callbackRun = new AtomicInteger();
      ROS2Subscription<std_msgs.String_> subscription = ros2Node.createSubscription(topic, reader ->
      {
         // Only read in half of the callbacks
         int runNumber = callbackRun.getAndIncrement();
         if (runNumber % 2 == 0)
         {
            reader.read();
         }

         if (runNumber == publishCount - 1)
         {
            synchronized (callbackRun)
            {
               callbackRun.notify();
            }
         }
      }, subscriptionQos);

      // Publish messages
      Process process = ROS2TestTools.launchROS2PublishProcess(ros2Node.getDomainId(),
                                                               "--times " + publishCount + " -r 100000 --qos-depth " + publishCount,
                                                               topicName,
                                                               "std_msgs/msg/String",
                                                               "{data: " + data + "}",
                                                               Redirect.INHERIT,
                                                               Redirect.INHERIT);

      synchronized (callbackRun)
      {
         if (callbackRun.get() < publishCount)
         {
            callbackRun.wait(5000);
         }
      }

      assertEquals(publishCount, callbackRun.get());

      // Read remaining messages that weren't read in callback
      int totalRead = 0;
      std_msgs.String_ msg = subscription.read();
      while (msg != null)
      {
         assertEquals(data, msg.getData().toString());
         totalRead++;
         msg = subscription.read();
      }
      assertEquals(publishCount / 2, totalRead);

      assertNull(subscription.read());
      assertNull(subscription.readLatest());

      process.waitFor();
      ros2Node.close();
   }

   @Test
   @Timeout(30)
   public void testSubscriptionWaitForPublisher() throws InterruptedException
   {
      String topicName = "/ihmc/test_wait_for_publisher";

      ROS2Node subscriberNode = new ROS2Node("subscriber_node");
      ROS2Node publisherNode = new ROS2Node("publisher_node");
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(topicName, std_msgs.String_.class);

      // Create subscription first
      ROS2Subscription<std_msgs.String_> subscription = subscriberNode.createSubscription(topic, ROS2QoSProfile.DEFAULT);

      // No publishers yet - should timeout
      boolean foundBeforePublisher = subscription.waitForPublisher(500);
      assertFalse(foundBeforePublisher, "Should not find publisher before it's created");

      // Now create publisher
      ROS2Publisher<std_msgs.String_> publisher = publisherNode.createPublisher(topic);

      // Should discover publisher now
      boolean foundAfterPublisher = subscription.waitForPublisher(5000);
      assertTrue(foundAfterPublisher, "Should discover publisher after it's created");

      // Subsequent calls should return immediately with true
      boolean foundAgain = subscription.waitForPublisher(100);
      assertTrue(foundAgain, "Should still show publisher is discovered");

      publisherNode.close();
      subscriberNode.close();
   }

   @Test
   @Timeout(30)
   public void testSubscriptionGetSubscriptionMatchedStatus()
   {
      String topicName = "/ihmc/test_subscription_matched";

      ROS2Node subscriberNode = new ROS2Node("subscriber_node");
      ROS2Node publisherNode = new ROS2Node("publisher_node");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(topicName, example_interfaces.Bool.class);

      ROS2Subscription<example_interfaces.Bool> subscription = subscriberNode.createSubscription(topic, ROS2QoSProfile.DEFAULT);

      // Initially no matches
      assertEquals(0, subscription.getSubscriptionMatchedStatus(), "Should have 0 matched publishers initially");

      // Create first publisher
      ROS2Publisher<example_interfaces.Bool> publisher1 = publisherNode.createPublisher(topic);
      assertTrue(subscription.waitForPublisher(5000), "Should discover first publisher");
      assertEquals(1, subscription.getSubscriptionMatchedStatus(), "Should have 1 matched publisher");

      // Create second publisher
      ROS2Publisher<example_interfaces.Bool> publisher2 = publisherNode.createPublisher(topic);

      // Wait for second publisher to be discovered
      long startTime = System.nanoTime();
      while (subscription.getSubscriptionMatchedStatus() < 2 && (System.nanoTime() - startTime) < 5_000_000_000L)
      {
         LockSupport.parkNanos(10_000_000); // 10ms
      }

      assertEquals(2, subscription.getSubscriptionMatchedStatus(), "Should have 2 matched publishers");

      publisherNode.close();
      subscriberNode.close();
   }

   @Test
   @Timeout(30)
   public void testSubscriptionMatchedCallback() throws InterruptedException
   {
      final String topicName = "/ihmc/test_matched_callback";

      ROS2Node subscriberNode = new ROS2Node("subscriber_node");
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(topicName, std_msgs.String_.class);

      // Track when publisher is discovered
      AtomicInteger matchedCount = new AtomicInteger(0);
      final Object sync = new Object();

      // Create subscription with matched callback
      ROS2Subscription<std_msgs.String_> subscription = subscriberNode.createSubscription(topic, ROS2QoSProfile.DEFAULT);
      subscription.setOnSubscriptionMatchedCallback(() ->
      {
         matchedCount.incrementAndGet();
         synchronized (sync)
         {
            sync.notify();
         }
      });

      // Initially, no publishers matched
      assertEquals(0, matchedCount.get(), "No publishers should be matched initially");

      // Create a publisher - should trigger the callback
      ROS2Node publisherNode = new ROS2Node("publisher_node");
      ROS2Publisher<std_msgs.String_> publisher = publisherNode.createPublisher(topic);

      // Wait for the callback to be invoked
      synchronized (sync)
      {
         if (matchedCount.get() == 0)
         {
            sync.wait(5000);
         }
      }

      assertTrue(matchedCount.get() > 0, "Subscription matched callback should have been invoked when publisher was created");

      publisherNode.close();
      subscriberNode.close();
   }

   @Test
   @Timeout(30)
   public void testSubscriptionMatchedCallbackMultiplePublishers() throws InterruptedException
   {
      final String topicName = "/ihmc/test_multiple_matched";

      ROS2Node subscriberNode = new ROS2Node("subscriber_node");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(topicName, example_interfaces.Bool.class);

      // Track callback invocations
      AtomicInteger matchedCount = new AtomicInteger(0);
      final Object sync = new Object();

      // Create subscription with matched callback
      ROS2Subscription<example_interfaces.Bool> subscription = subscriberNode.createSubscription(topic, ROS2QoSProfile.DEFAULT);
      subscription.setOnSubscriptionMatchedCallback(() ->
      {
         matchedCount.incrementAndGet();
         synchronized (sync)
         {
            sync.notifyAll();
         }
      });

      // Create multiple publishers
      ROS2Node publisherNode1 = new ROS2Node("publisher_node1");
      ROS2Node publisherNode2 = new ROS2Node("publisher_node2");
      ROS2Node publisherNode3 = new ROS2Node("publisher_node3");

      ROS2Publisher<example_interfaces.Bool> publisher1 = publisherNode1.createPublisher(topic);

      // Wait for first match
      synchronized (sync)
      {
         if (matchedCount.get() < 1)
         {
            sync.wait(2000);
         }
      }
      assertTrue(matchedCount.get() >= 1, "Should have matched first publisher");

      ROS2Publisher<example_interfaces.Bool> publisher2 = publisherNode2.createPublisher(topic);

      // Wait for second match
      synchronized (sync)
      {
         if (matchedCount.get() < 2)
         {
            sync.wait(2000);
         }
      }
      assertTrue(matchedCount.get() >= 2, "Should have matched second publisher");

      ROS2Publisher<example_interfaces.Bool> publisher3 = publisherNode3.createPublisher(topic);

      // Wait for third match
      synchronized (sync)
      {
         if (matchedCount.get() < 3)
         {
            sync.wait(2000);
         }
      }
      assertTrue(matchedCount.get() >= 3, "Should have matched all three publishers");

      publisherNode1.close();
      publisherNode2.close();
      publisherNode3.close();
      subscriberNode.close();
   }

   @Test
   @Timeout(30)
   public void testSubscriptionMatchedCallbackBeforePublisher() throws InterruptedException
   {
      final String topicName = "/ihmc/test_callback_before_publisher";

      ROS2Node subscriberNode = new ROS2Node("subscriber_node");
      ROS2Node publisherNode = new ROS2Node("publisher_node");
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(topicName, std_msgs.String_.class);

      // Create subscription BEFORE publisher exists
      AtomicInteger matchedCount = new AtomicInteger(0);
      final Object sync = new Object();

      ROS2Subscription<std_msgs.String_> subscription = subscriberNode.createSubscription(topic, ROS2QoSProfile.DEFAULT);
      subscription.setOnSubscriptionMatchedCallback(() ->
      {
         matchedCount.incrementAndGet();
         synchronized (sync)
         {
            sync.notify();
         }
      });

      // Verify no matches yet
      LockSupport.parkNanos(100_000_000); // 100ms
      assertEquals(0, matchedCount.get(), "Should not match before publisher exists");

      // Create publisher - should trigger callback
      ROS2Publisher<std_msgs.String_> publisher = publisherNode.createPublisher(topic);

      // Wait for callback
      synchronized (sync)
      {
         if (matchedCount.get() == 0)
         {
            sync.wait(5000);
         }
      }

      assertTrue(matchedCount.get() > 0, "Callback should fire when publisher is created after subscription");

      publisherNode.close();
      subscriberNode.close();
   }

   @Test
   @Timeout(30)
   public void testSubscriptionMatchedCallbackAfterPublisher() throws InterruptedException
   {
      final String topicName = "/ihmc/test_callback_after_publisher";

      // Create publisher FIRST
      ROS2Node publisherNode = new ROS2Node("publisher_node");
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(topicName, std_msgs.String_.class);
      ROS2Publisher<std_msgs.String_> publisher = publisherNode.createPublisher(topic);

      // Create subscription with callback - publisher already exists
      ROS2Node subscriberNode = new ROS2Node("subscriber_node");
      AtomicInteger matchedCount = new AtomicInteger(0);
      final Object sync = new Object();

      ROS2Subscription<std_msgs.String_> subscription = subscriberNode.createSubscription(topic, ROS2QoSProfile.DEFAULT);
      subscription.setOnSubscriptionMatchedCallback(() ->
      {
         matchedCount.incrementAndGet();
         synchronized (sync)
         {
            sync.notify();
         }
      });

      // Wait for callback
      synchronized (sync)
      {
         if (matchedCount.get() == 0)
         {
            sync.wait(2000);
         }
      }

      assertTrue(matchedCount.get() > 0, "Callback should fire even when publisher exists before subscription");

      publisherNode.close();
      subscriberNode.close();
   }

   @Test
   @Timeout(30)
   public void testSubscriptionStressTestConcurrentCreationAndDestruction()
   {
      Instant start = Instant.now();

      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/ihmc/test_stress", example_interfaces.Bool.class);
      ROS2Node publisherNode = new ROS2Node("publisher_node");
      ROS2Node subscriberNode = new ROS2Node("subscriber_node");

      ROS2Publisher<example_interfaces.Bool> publisher = publisherNode.createPublisher(topic);

      // Continuously publish while creating/destroying subscriptions
      example_interfaces.Bool messageToPublish = new example_interfaces.Bool();
      messageToPublish.setData(true);

      int threadCount = 50;
      List<Thread> threads = new ArrayList<>();

      for (int i = 0; i < threadCount; ++i)
      {
         Thread thread = new Thread(() ->
         {
            LockSupport.parkNanos(RANDOM.nextLong((long) 1E8)); // park up to 0.1 seconds

            ROS2Subscription<example_interfaces.Bool> subscription = subscriberNode.createSubscription(topic, subscriber ->
            {
               example_interfaces.Bool data = new example_interfaces.Bool();
               subscriber.read(data);
            }, ROS2QoSProfile.DEFAULT);

            LockSupport.parkNanos(RANDOM.nextLong((long) 1E8)); // park up to 0.1 seconds

            subscriberNode.destroySubscription(subscription);
            subscriberNode.destroySubscription(subscription); // Call destroy multiple times for coverage
         }, "subscription_thread_" + i);
         thread.start();
         threads.add(thread);
      }

      // Publish while subscriptions are being created/destroyed
      Thread publishThread = new Thread(() ->
      {
         boolean allComplete = false;
         while (!allComplete)
         {
            publisher.publish(messageToPublish);
            LockSupport.parkNanos(RANDOM.nextLong((long) 1E7)); // park up to 0.01 seconds

            allComplete = true;
            for (Thread thread : threads)
            {
               if (thread.isAlive())
               {
                  allComplete = false;
                  break;
               }
            }
         }
      }, "publishThread");
      publishThread.start();

      // Wait for all threads to complete
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

      try
      {
         publishThread.join(10000);
      }
      catch (InterruptedException e)
      {
         throw new RuntimeException(e);
      }

      publisherNode.close();
      subscriberNode.close();

      long durationMillis = start.until(Instant.now(), ChronoUnit.MILLIS);
      jros2.getLogger().fine("Test Duration: " + (durationMillis / 1000) + "s" + (durationMillis % 1000) + "ms");
   }

   @Test
   @Timeout(30)
   public void testSubscriptionNoHangWhenDestroyedDuringPublish() throws InterruptedException
   {
      final String topicName = "/ihmc/test_no_hang";

      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(topicName, example_interfaces.Bool.class);

      // Create publisher
      ROS2Publisher<example_interfaces.Bool> publisher = ros2Node.createPublisher(topic);

      // Publisher will publish in continuous loop
      example_interfaces.Bool messageToPublish = new example_interfaces.Bool();
      messageToPublish.setData(true);
      Thread publishThread = new Thread(() ->
      {
         while (!Thread.interrupted())
         {
            publisher.publish(messageToPublish);
         }
      }, "publishThread");
      publishThread.start();

      // Create subscription
      ROS2Subscription<example_interfaces.Bool> subscription = ros2Node.createSubscription(topic, subscriber ->
      {
         example_interfaces.Bool data = new example_interfaces.Bool();
         subscriber.read(data);
      }, ROS2QoSProfile.DEFAULT);

      // Destroy subscription while publishing
      ros2Node.destroySubscription(subscription);

      // Stop publishing
      publishThread.interrupt();
      publishThread.join();

      ros2Node.close();
   }
}
