package us.ihmc.jros2;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import std_msgs.msg.dds.Bool;
import us.ihmc.jros2.ROS2QoSProfile.Durability;
import us.ihmc.log.LogTools;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
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
      ROS2Topic<Bool> topic = new ROS2Topic<>(topicName, Bool.class);

      assertDoesNotThrow(() ->
      {
         // Create publishers
         ROS2Publisher<Bool> publisher1 = ros2Node.createPublisher(topic);
         ROS2Publisher<Bool> publisher2 = ros2Node.createPublisher(topic);

         // Try publishing concurrently
         Thread[] publishThreads = new Thread[10];
         for (int i = 0; i < publishThreads.length; ++i)
         {
            Thread thread = new Thread(() ->
            {
               LockSupport.parkNanos(RANDOM.nextLong((long) 1E8));
               publisher1.publish(new Bool());
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
               publisher2.publish(new Bool());
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
   @Timeout(30)
   public void testROS2SubscriptionAPI()
   {
      String topicName = "/ihmc/test_bool";

      // Create ROS 2 node and topic
      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<Bool> topic = new ROS2Topic<>(topicName, Bool.class);

      assertDoesNotThrow(() ->
      {
         // Create subscriptions
         ROS2Subscription<Bool> subscription1 = ros2Node.createSubscription(topic, reader ->
         {
            Bool message = new Bool();
            reader.read(message);
            assert false; // Should never reach here since we don't publish anything
         }, ROS2QoSProfile.DEFAULT);

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
   @Timeout(30)
   public void testROS2Publisher() throws InterruptedException, IOException
   {
      final boolean expectedValue = true;
      String topicName = "/ihmc/test_bool";

      // Create ROS 2 node, topic, and publisher
      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<Bool> topic = new ROS2Topic<>(topicName, Bool.class);

      ROS2QoSProfile qosProfile = new ROS2QoSProfile();
      qosProfile.durability(Durability.TRANSIENT_LOCAL);
      ROS2Publisher<Bool> publisher = ros2Node.createPublisher(topic, qosProfile);

      // Create a Bool message and publish it
      Bool bool = new Bool();
      bool.setData(expectedValue);
      publisher.publish(bool);

      // Use ros2 topic echo --once to get the value of the published message
      String result = ROS2TestTools.ros2EchoOnce(ros2Node.getDomainId(), topicName);

      // Ensure the value received by ros2 matches the value we published
      assertTrue(result.contains(String.valueOf(expectedValue)), result);

      ros2Node.close();
   }

   @Test
   @Timeout(30)
   // Allocation-free subscription
   public void testROS2Subscription1() throws InterruptedException, IOException
   {
      final boolean expectedValue = true;
      final String topicName = "/ihmc/test_bool";

      // Create the ROS 2 node, topic, and subscription
      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<Bool> topic = new ROS2Topic<>(topicName, Bool.class);

      final AtomicBoolean valueReceived = new AtomicBoolean(!expectedValue); // Initialize to opposite of expected value to make sure it's received correctly
      ROS2Subscription<Bool> subscription = ros2Node.createSubscription(topic, reader ->
      {
         Bool msg = new Bool();
         reader.read(msg);

         synchronized (valueReceived)
         {
            valueReceived.set(msg.getData());
            valueReceived.notify();
         }
      }, ROS2QoSProfile.DEFAULT);

      // Launch a ROS 2 process to publish a Bool message
      Process process = ROS2TestTools.launchROS2PublishProcess(ros2Node.getDomainId(),
                                                               "--once",
                                                               topicName,
                                                               "std_msgs/msg/Bool",
                                                               "{data: " + expectedValue + "}",
                                                               Redirect.INHERIT,
                                                               Redirect.INHERIT);
      // Wait for subscription to receive the Bool message
      synchronized (valueReceived)
      {
         if (valueReceived.get() != expectedValue)
         {
            valueReceived.wait();
         }
      }

      // Assert the received value is correct
      assertEquals(expectedValue, valueReceived.get());

      // Ensure the ROS 2 publish process ends
      process.waitFor();

      ros2Node.close();
   }

   @Test
   @Timeout(30)
   // Allocation subscription
   public void testROS2Subscription2() throws InterruptedException, IOException
   {
      final boolean expectedValue = true;
      final String topicName = "/ihmc/test_bool";

      // Create the ROS 2 node, topic, and subscription
      ROS2Node ros2Node = new ROS2Node("test_node");
      ROS2Topic<Bool> topic = new ROS2Topic<>(topicName, Bool.class);

      final AtomicBoolean valueReceived = new AtomicBoolean(!expectedValue); // Initialize to opposite of expected value to make sure it's received correctly
      ROS2Subscription<Bool> subscription = ros2Node.createSubscription(topic, reader ->
      {
         Bool msg = reader.read();
         synchronized (valueReceived)
         {
            valueReceived.set(msg.getData());
            valueReceived.notify();
         }
      }, ROS2QoSProfile.DEFAULT);

      // Launch a ROS 2 process to publish a Bool message
      Process process = ROS2TestTools.launchROS2PublishProcess(ros2Node.getDomainId(),
                                                               "--once",
                                                               topicName,
                                                               "std_msgs/msg/Bool",
                                                               "{data: " + expectedValue + "}",
                                                               Redirect.INHERIT,
                                                               Redirect.INHERIT);
      // Wait for subscription to receive the Bool message
      synchronized (valueReceived)
      {
         if (valueReceived.get() != expectedValue)
         {
            valueReceived.wait();
         }
      }

      // Assert the received value is correct
      assertEquals(expectedValue, valueReceived.get());

      // Ensure the ROS 2 publish process ends
      process.waitFor();

      ros2Node.close();
   }

   @RepeatedTest(25)
   @Timeout(30)
   /*
     Test description:
        There are 2 nodes: [publisherNode, subscriberNode].
        There is 1 topic: /ihmc/test_topic of type Bool
     <p>
        The goal of this test is to destroy a subscription while it is reading data from a publisher.
        To do this, we create 100 subscriptions, each on a separate thread, wait a random and small
        amount of time, then destroy the subscription within its thread while a separate publisher
        thread is publishing messages which are being read by the subscription.
    */
   public void testCrazyMultithreading()
   {
      Instant start = Instant.now();

      ROS2Topic<Bool> topic = new ROS2Topic<>("/ihmc/test_topic", Bool.class);
      ROS2Node publisherNode = new ROS2Node("publisher_node");
      ROS2Node subscriberNode = new ROS2Node("subscriber_node");

      ROS2Publisher<Bool> publisher = publisherNode.createPublisher(topic);

      Thread destroyThread = new Thread(() ->
      {
         int threadCount = 100;
         List<Thread> threads = new ArrayList<>();
         for (int i = 0; i < threadCount; ++i)
         {
            Thread thread = new Thread(() ->
            {
               LockSupport.parkNanos(RANDOM.nextLong((long) 1E8)); // park up to 0.1 seconds

               ROS2Subscription<Bool> subscription = subscriberNode.createSubscription(topic, subscriber ->
               {
                  Bool data = new Bool();
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

      Bool messageToPublish = new Bool();
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
      ROS2Topic<Bool> topic = new ROS2Topic<>(topicName, Bool.class);

      // Create a publisher
      ROS2Publisher<Bool> publisher = ros2Node.createPublisher(topic);

      // Publisher will publish in a free loop until subscription is created and destroyed
      Bool messageToPublish = new Bool();
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
      ROS2Subscription<Bool> subscription = ros2Node.createSubscription(topic, subscriber ->
      {
         Bool data = new Bool();
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
