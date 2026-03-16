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

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import us.ihmc.jros2.ROS2QoSProfile.Durability;
import us.ihmc.jros2.ROS2QoSProfile.History;
import us.ihmc.jros2.ROS2QoSProfile.Reliability;
import us.ihmc.log.LogTools;

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

public class ROS2PublishSubscribeTest
{
   private static final Random RANDOM = new Random(1881108);

   @Test
   @Timeout(30)
   public void testROS2PublisherAPI()
   {
      String topicName = "/ihmc/test_bool";

      // Create ROS 2 node and topic
      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(topicName, example_interfaces.Bool.class);

      assertDoesNotThrow(() ->
      {
         // Create publishers
         ROS2Publisher<example_interfaces.Bool> publisher1 = ros2Node.createPublisher(topic);
         ROS2Publisher<example_interfaces.Bool> publisher2 = ros2Node.createPublisher(topic);

         // Try publishing concurrently
         Thread[] publishThreads = new Thread[10];
         for (int i = 0; i < publishThreads.length; ++i)
         {
            Thread thread = new Thread(() ->
            {
               LockSupport.parkNanos(RANDOM.nextLong((long) 1E8));
               publisher1.publish(new example_interfaces.Bool());
            }, "PublishThread" + i);
            thread.start();
            publishThreads[i] = thread;
         }

         // Ensure all threads die
         for (Thread thread : publishThreads)
            thread.join(1000);

         // Ensure we can destroy a publisher
         ros2Node.destroyPublisher(publisher1);

         // Oops, I "accidentally" destroyed it again
         ros2Node.destroyPublisher(publisher1);

         // Try destroying while publishing
         Thread publishThread = new Thread(() ->
         {
            while (!Thread.interrupted())
            {
               publisher2.publish(new example_interfaces.Bool());
            }
         }, ":PublishThread");
         publishThread.start();

         // Destroy the publisher while it's publishing
         ros2Node.destroyPublisher(publisher2);

         // Tell the thread to stop, and make sure it dies
         publishThread.interrupt();
         publishThread.join(1000);
      });

      ros2Node.close();
   }

   @Test
   @EnabledOnOs(OS.LINUX)
   @Timeout(30)
   public void testROS2SubscriptionAPI()
   {
      String topicName = "/ihmc/test_bool";

      // Create ROS 2 node and topic
      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(topicName, example_interfaces.Bool.class);

      assertDoesNotThrow(() ->
      {
         ROS2QoSProfile subscriptionQos = new ROS2QoSProfile();
         subscriptionQos.reliability(Reliability.RELIABLE);

         // Create subscriptions
         ROS2Subscription<example_interfaces.Bool> subscription1 = ros2Node.createSubscription(topic, reader ->
         {
            example_interfaces.Bool message = new example_interfaces.Bool();
            reader.read(message);
            assert false; // Should never reach here since we don't publish anything
         }, subscriptionQos);

         // Ensure we can destroy subscriptions
         ros2Node.destroySubscription(subscription1);

         // Oops, I "accidentally" destroyed it again
         ros2Node.destroySubscription(subscription1);

         // Publish a message to ensure subscriptions don't receive anything after being destroyed
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
   @EnabledOnOs(OS.LINUX)
   @Timeout(30)
   public void testROS2Publisher() throws InterruptedException, IOException
   {
      final boolean expectedValue = true;
      String topicName = "/ihmc/test_bool";

      // Create ROS 2 node, topic, and publisher
      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(topicName, example_interfaces.Bool.class);

      ROS2QoSProfile publisherQos = new ROS2QoSProfile();
      publisherQos.durability(Durability.TRANSIENT_LOCAL);
      ROS2Publisher<example_interfaces.Bool> publisher = ros2Node.createPublisher(topic, publisherQos);

      // Create a Bool message and publish it
      example_interfaces.Bool bool = new example_interfaces.Bool();
      bool.setData(expectedValue);
      publisher.publish(bool);

      // Use ros2 topic echo --once to get the value of the published message
      String result = ROS2TestTools.ros2EchoOnce(ros2Node.getDomainId(), topicName);

      // Ensure the value received by ros2 matches the value we published
      assertTrue(result.contains(String.valueOf(expectedValue)), result);

      ros2Node.close();
   }

   @Test
   @EnabledOnOs(OS.LINUX)
   @Timeout(30)
   // Allocation-free subscription
   public void testROS2Subscription1() throws InterruptedException, IOException
   {
      final String data = "This is a test. This is only a test.";
      final String topicName = "/ihmc/test_string";

      // Create the ROS 2 node, topic, and subscription
      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<std_msgs.String> topic = new ROS2Topic<>(topicName, std_msgs.String.class);

      ROS2QoSProfile subscriptionQos = new ROS2QoSProfile();
      subscriptionQos.reliability(Reliability.RELIABLE);

      // This subscription is allocation-free, so we allocate the message object once and reuse it for each subscription callback
      std_msgs.String msg = new std_msgs.String();
      final Object sync = new Object();
      ros2Node.createSubscription(topic, reader ->
      {
         reader.read(msg);

         synchronized (sync)
         {
            sync.notify();
         }
      }, subscriptionQos);

      // Launch a ROS 2 process to publish a String message
      Process process = ROS2TestTools.launchROS2PublishProcess(ros2Node.getDomainId(),
                                                               "--once",
                                                               topicName,
                                                               "std_msgs/msg/String",
                                                               "{data: " + data + "}",
                                                               Redirect.INHERIT,
                                                               Redirect.INHERIT);
      // Wait for subscription to receive the String message
      synchronized (sync)
      {
         if (msg.getData().isEmpty())
         {
            sync.wait();
         }
      }

      // Assert the received value is correct
      assertEquals(data, msg.getData().toString());

      // Ensure the ROS 2 publish process ends
      process.waitFor();

      ros2Node.close();
   }

   @Test
   @EnabledOnOs(OS.LINUX)
   @Timeout(30)
   // Allocation subscription
   public void testROS2Subscription2() throws InterruptedException, IOException
   {
      final String data = "This is a test. This is only a test.";
      final String topicName = "/ihmc/test_string";

      // Create the ROS 2 node, topic, and subscription
      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<std_msgs.String> topic = new ROS2Topic<>(topicName, std_msgs.String.class);

      AtomicReference<String> receivedString = new AtomicReference<>("");

      ROS2QoSProfile subscriptionQos = new ROS2QoSProfile();
      subscriptionQos.reliability(Reliability.RELIABLE);

      final Object sync = new Object();
      ros2Node.createSubscription(topic, reader ->
      {
         std_msgs.String msg = reader.read();

         synchronized (sync)
         {
            receivedString.set(msg.getData().toString());
            sync.notify();
         }
      }, subscriptionQos);

      // Launch a ROS 2 process to publish a String message
      Process process = ROS2TestTools.launchROS2PublishProcess(ros2Node.getDomainId(),
                                                               "--once",
                                                               topicName,
                                                               "std_msgs/msg/String",
                                                               "{data: " + data + "}",
                                                               Redirect.INHERIT,
                                                               Redirect.INHERIT);
      // Wait for subscription to receive the String message
      synchronized (sync)
      {
         if (receivedString.get().isEmpty())
         {
            sync.wait();
         }
      }

      // Assert the received value is correct
      assertEquals(data, receivedString.get());

      // Ensure the ROS 2 publish process ends
      process.waitFor();

      ros2Node.close();
   }

   @Test
   @EnabledOnOs(OS.LINUX)
   @Timeout(30)
   // Subscription sampler
   public void testROS2Subscription3() throws InterruptedException, IOException
   {
      final String data = "This is a test. This is only a test.";
      final String topicName = "/ihmc/test_string";

      // Create the ROS 2 node, topic, and subscription
      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<std_msgs.String> topic = new ROS2Topic<>(topicName, std_msgs.String.class);

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

      // Launch a ROS 2 process to publish a String message
      Process process = ROS2TestTools.launchROS2PublishProcess(ros2Node.getDomainId(),
                                                               "--once",
                                                               topicName,
                                                               "std_msgs/msg/String",
                                                               "{data: " + data + "}",
                                                               Redirect.INHERIT,
                                                               Redirect.INHERIT);
      // Wait for subscription to receive the String message
      synchronized (sync)
      {
         if (receivedString.get().isEmpty())
         {
            sync.wait();
         }
      }

      // Assert the received value is correct
      assertEquals(data, receivedString.get());

      // Ensure the ROS 2 publish process ends
      process.waitFor();

      ros2Node.close();
   }

   @Test
   @EnabledOnOs(OS.LINUX)
   @Timeout(30)
   // Callback-less subscription
   public void testROS2Subscription4() throws InterruptedException, IOException
   {
      final String data = "This is a test. This is only a test.";
      final String topicName = "/ihmc/test_string";
      final int publishCount = 20;

      // Create the ROS 2 node, topic, and subscription
      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<std_msgs.String> topic = new ROS2Topic<>(topicName, std_msgs.String.class);

      ROS2QoSProfile subscriptionQos = new ROS2QoSProfile();
      subscriptionQos.history(History.KEEP_LAST);
      subscriptionQos.depth(publishCount);
      subscriptionQos.reliability(Reliability.RELIABLE);

      ROS2Subscription<std_msgs.String> subscription = ros2Node.createSubscription(topic, subscriptionQos);

      // Launch a ROS 2 process to publish a String message
      Process process = ROS2TestTools.launchROS2PublishProcess(ros2Node.getDomainId(),
                                                               "--times " + publishCount + " -r 100000 --qos-depth " + publishCount,
                                                               // -r sets the publish frequency, just set to some very high number to get all the messages at once
                                                               topicName,
                                                               "std_msgs/msg/String",
                                                               "{data: " + data + "}",
                                                               Redirect.INHERIT,
                                                               Redirect.INHERIT);

      // Wait until the subscription receives all the messages
      long startTime = System.nanoTime();
      while (subscription.getUnreadMessageCount() < publishCount && System.nanoTime() - startTime < TimeUnit.SECONDS.toNanos(5))
      {
         LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
      }
      assertEquals(publishCount, subscription.getUnreadMessageCount());

      // By this point, the subscription should have received all the messages, let's read them all
      int totalRead = 0;
      std_msgs.String msg = subscription.read();
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

      // Ensure the ROS 2 publish process ends
      process.waitFor();

      ros2Node.close();
   }

   @Test
   @EnabledOnOs(OS.LINUX)
   @Timeout(30)
   // Callback-less AND callback subscription
   public void testROS2Subscription5() throws InterruptedException, IOException
   {
      final String data = "This is a test. This is only a test.";
      final String topicName = "/ihmc/test_string";
      final int publishCount = 20;

      // Create the ROS 2 node, topic, and subscription
      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<std_msgs.String> topic = new ROS2Topic<>(topicName, std_msgs.String.class);

      ROS2QoSProfile subscriptionQos = new ROS2QoSProfile();
      subscriptionQos.history(History.KEEP_LAST);
      subscriptionQos.depth(publishCount);
      subscriptionQos.reliability(Reliability.RELIABLE);

      AtomicInteger callbackRun = new AtomicInteger();
      ROS2Subscription<std_msgs.String> subscription = ros2Node.createSubscription(topic, reader ->
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

      // Launch a ROS 2 process to publish a String message
      Process process = ROS2TestTools.launchROS2PublishProcess(ros2Node.getDomainId(),
                                                               "--times " + publishCount + " -r 100000 --qos-depth " + publishCount,
                                                               // -r sets the publish frequency, just set to some very high number to get all the messages at once
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

      // By this point, the subscription should have received all the messages, let's read them all
      int totalRead = 0;
      std_msgs.String msg = subscription.read();
      while (msg != null)
      {
         assertEquals(data, msg.getData().toString());
         totalRead++;
         msg = subscription.read();
      }
      // Non callback reads should total half of the publish count
      assertEquals(publishCount / 2, totalRead);

      assertNull(subscription.read());
      assertNull(subscription.readLatest());

      // Ensure the ROS 2 publish process ends
      process.waitFor();

      ros2Node.close();
   }

   @RepeatedTest(25)
   @Timeout(30)
   /*
     Test description:
        There are 2 nodes: [publisherNode, subscriberNode]
        There is 1 topic: /ihmc/test_topic of type Bool
        
        The goal of this test is to destroy a subscription while it is reading data from a publisher.
        To do this, we create 100 subscriptions, each on a separate thread, wait a random and small
        amount of time, then destroy the subscription within its thread while a separate publisher
        thread is publishing messages which are being read by the subscription.
    */
   public void testCrazyMultithreading()
   {
      Instant start = Instant.now();

      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/ihmc/test_topic", example_interfaces.Bool.class);
      ROS2Node publisherNode = new ROS2Node("publisher_node");
      ROS2Node subscriberNode = new ROS2Node("subscriber_node");

      ROS2Publisher<example_interfaces.Bool> publisher = publisherNode.createPublisher(topic);

      Thread destroyThread = new Thread(() ->
      {
         int threadCount = 100;
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
               subscriberNode.destroySubscription(subscription); // Call destroy multiple times for better test coverage
            }, "thread_" + i);
            thread.start();
            threads.add(thread);
         }

         for (int i = 0; i < threadCount; ++i)
         {
            try
            {
               threads.get(i).join();
            }
            catch (InterruptedException e)
            {
               throw new RuntimeException(e);
            }
         }
      }, "destroyThread");
      destroyThread.start();

      example_interfaces.Bool messageToPublish = new example_interfaces.Bool();
      messageToPublish.setData(true);
      Thread publishThread = new Thread(() ->
      {
         while (destroyThread.isAlive())
         {
            LockSupport.parkNanos(RANDOM.nextLong((long) 1E8)); // park up to 0.1 seconds

            publisher.publish(messageToPublish);
         }
      }, "publishThread");
      publishThread.start();

      try
      {
         destroyThread.join();
         publishThread.join();
      }
      catch (InterruptedException interruptedException)
      {
         throw new RuntimeException(interruptedException);
      }

      publisherNode.close();
      subscriberNode.close();

      long durationMillis = start.until(Instant.now(), ChronoUnit.MILLIS);
      LogTools.debug("Test Duration: {}s{}ms", durationMillis / 1000, durationMillis % 1000);
   }

   @Test
   @Timeout(30)
   public void testHang() throws InterruptedException
   {
      final String topicName = "/ihmc/test_bool";

      // Create the ROS 2 node, topic, and subscription
      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(topicName, example_interfaces.Bool.class);

      // Create a publisher
      ROS2Publisher<example_interfaces.Bool> publisher = ros2Node.createPublisher(topic);

      // Publisher will publish in a free loop until subscription is created and destroyed
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

      // Create a subscription
      ROS2Subscription<example_interfaces.Bool> subscription = ros2Node.createSubscription(topic, subscriber ->
      {
         example_interfaces.Bool data = new example_interfaces.Bool();
         subscriber.read(data);
      }, ROS2QoSProfile.DEFAULT);

      // Destroy it
      ros2Node.destroySubscription(subscription);

      // Tell the publish thread to stop
      publishThread.interrupt();
      publishThread.join();

      ros2Node.close();
   }
}
