package us.ihmc.jros2;

import org.junit.jupiter.api.Test;
import std_msgs.msg.dds.Bool;
import us.ihmc.jros2.ROS2QoSProfile.Durability;
import us.ihmc.log.LogTools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.DoubleSummaryStatistics;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"ConstantValue", "ExtractMethodRecommender", "StringConcatenationInsideStringBufferAppend"})
public class AsyncROS2Test
{
   @Test
   public void testAsyncROS2Publisher() throws IOException, InterruptedException
   {
      boolean expectedValue = true;
      final String topicName = "/ihmc/test_bool";

      AsyncROS2Node asyncNode = new AsyncROS2Node("test_async_node");
      ROS2Topic<Bool> topic = new ROS2Topic<>(topicName, Bool.class);

      ROS2QoSProfile qosProfile = new ROS2QoSProfile();
      qosProfile.durability(Durability.TRANSIENT_LOCAL);
      ROS2Publisher<Bool> publisher = asyncNode.createPublisher(topic, qosProfile);

      Bool bool = new Bool();
      bool.setData(expectedValue);
      publisher.publish(bool);

      String result = ROS2TestTools.ros2EchoOnce(asyncNode.getDomainId(), topicName);

      // Ensure the value received by ros2 matches the value we published
      assertTrue(result.contains(String.valueOf(expectedValue)), result);

      asyncNode.close();
   }

   @Test
   public void testPublishingManyMessages() throws InterruptedException
   {
      int messagesToPublish = 1000;
      AtomicInteger messagesReceived = new AtomicInteger(0);
      boolean publish = true;
      AtomicBoolean expectedValue = new AtomicBoolean(publish);
      final String topicName = "/ihmc/test_bool";

      AsyncROS2Node asyncNode = new AsyncROS2Node("test_async_node");
      ROS2Topic<Bool> topic = new ROS2Topic<>(topicName, Bool.class);

      asyncNode.createSubscription(topic, reader ->
      {
         Bool value = reader.read();
         assertEquals(expectedValue.get(), value.getData());
         expectedValue.set(!expectedValue.get());

         if (messagesReceived.incrementAndGet() >= messagesToPublish)
         {
            synchronized (messagesReceived)
            {
               messagesReceived.notify();
            }
         }
      });

      ROS2Publisher<Bool> publisher = asyncNode.createPublisher(topic);

      Bool bool = new Bool();
      for (int i = 0; i < messagesToPublish; ++i)
      {
         bool.setData(publish);
         publisher.publish(bool);
         publish = !publish;
         LockSupport.parkNanos(100000); // park for 0.1ms
      }

      synchronized (messagesReceived)
      {
         if (messagesReceived.get() < messagesToPublish)
         {
            messagesReceived.wait(1000);
         }
      }

      assertEquals(messagesToPublish, messagesReceived.get());

      asyncNode.close();
   }

   @Test
   public void testMultipleAsyncPublishers() throws InterruptedException
   {
      final int publisherCount = 10;
      final int messagesToPublish = 1000;
      final boolean expected = true;

      AsyncROS2Node asyncNode = new AsyncROS2Node("async_node");
      ROS2Topic<Bool> topic = new ROS2Topic<>("test_topic", Bool.class);

      ROS2Publisher<?>[] publishers = new ROS2Publisher[publisherCount];
      Thread[] publisherThreads = new Thread[publisherCount];

      for (int i = 0; i < publisherCount; ++i)
      {
         ROS2Publisher<Bool> asyncPublisher = asyncNode.createPublisher(topic);
         publishers[i] = asyncPublisher;
         publisherThreads[i] = new Thread(() ->
         {
            for (int j = 0; j < messagesToPublish; j++)
            {
               Bool message = new Bool();
               message.setData(expected);
               asyncPublisher.publish(message);
               LockSupport.parkNanos(500000); // park for 0.5ms
            }
         });
      }

      AtomicInteger receivedMessageCount = new AtomicInteger(0);
      asyncNode.createSubscription(topic, reader ->
      {
         Bool received = reader.read();
         assertEquals(expected, received.getData());
         receivedMessageCount.incrementAndGet();
      });

      for (int i = 0; i < publisherCount; ++i)
      {
         publisherThreads[i].start();
      }

      for (int i = 0; i < publisherCount; ++i)
      {
         publisherThreads[i].join();
      }

      assertEquals(publisherCount * messagesToPublish, receivedMessageCount.get());

      asyncNode.close();
   }

   @Test
   public void compareStandardAndAsyncPublisher() throws InterruptedException, IOException
   {
      final boolean generateCSV = false;
      final String fileDirectory = System.getProperty("user.home") + File.separator + "Documents" + File.separator;

      final int messagesToPublish = 10000;
      final boolean expected = true;

      // Create normal and async nodes
      ROS2Node ros2Node = new ROS2Node("standard_node");
      AsyncROS2Node asyncROS2Node = new AsyncROS2Node("async_node");

      // Create topics to publish on
      ROS2Topic<Bool> standardTopic = new ROS2Topic<>("standard_topic", Bool.class);
      ROS2Topic<Bool> asyncTopic = new ROS2Topic<>("async_topic", Bool.class);

      // Create normal and async publishers
      ROS2Publisher<Bool> standardPublisher = ros2Node.createPublisher(standardTopic);
      DoubleStatisticsHelper standardPublisherStatistics = new DoubleStatisticsHelper(messagesToPublish);

      ROS2Publisher<Bool> asyncPublisher = asyncROS2Node.createPublisher(asyncTopic);
      DoubleStatisticsHelper asyncPublisherStatistics = new DoubleStatisticsHelper(messagesToPublish);

      // Create subscribers
      ROS2SubscriptionCallback<Bool> callback = reader -> assertEquals(expected, reader.read().getData());
      ros2Node.createSubscription(standardTopic, callback);
      asyncROS2Node.createSubscription(asyncTopic, callback);

      Bool message = new Bool();
      message.setData(expected);

      BiFunction<ROS2Publisher<Bool>, DoubleStatisticsHelper, Runnable> runnableProvider = (ros2Publisher, statistics) -> () ->
      {
         // Warm up publishers
         for (int i = 0; i < messagesToPublish / 10; ++i)
         {
            ros2Publisher.publish(message);
            LockSupport.parkNanos(100000); // park for 0.1ms
         }

         // Start publishing and recording statistics
         for (int i = 0; i < messagesToPublish; ++i)
         {
            long start = System.nanoTime();
            ros2Publisher.publish(message);
            long duration = System.nanoTime() - start;
            statistics.accept(duration * 1E-9);
            LockSupport.parkNanos(100000); // park for 0.1ms
         }
      };

      // Create two threads
      Thread standardPublisherThread = new Thread(runnableProvider.apply(standardPublisher, standardPublisherStatistics));
      Thread asyncPublisherThread = new Thread(runnableProvider.apply(asyncPublisher, asyncPublisherStatistics));

      // Start both threads
      LogTools.info("Starting publish threads...");
      standardPublisherThread.start();
      asyncPublisherThread.start();

      // Wait until they finish
      standardPublisherThread.join();
      asyncPublisherThread.join();
      LogTools.info("Publish threads finished");

      // Log statistics
      LogTools.info("Standard publisher statistics: {}", standardPublisherStatistics);
      LogTools.info("Async publisher statistics:    {}", asyncPublisherStatistics);

      // Ensure both threads published all messages
      assertEquals(messagesToPublish, asyncPublisherStatistics.getCount());
      assertEquals(messagesToPublish, standardPublisherStatistics.getCount());

      // Ensure async publisher is faster and more consistent
      assertTrue(asyncPublisherStatistics.getAverage() < standardPublisherStatistics.getAverage());
      assertTrue(asyncPublisherStatistics.getStandardDeviation() < standardPublisherStatistics.getStandardDeviation());

      // Cleanup
      ros2Node.close();
      asyncROS2Node.close();

      if (generateCSV)
      {
         String fileName = "AsyncPublisherComparison" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmm")) + ".csv";
         File csv = new File(fileDirectory + fileName);

         double[] standardHistory = standardPublisherStatistics.getHistory();
         double[] asyncHistory = asyncPublisherStatistics.getHistory();

         try (FileWriter fileWriter = new FileWriter(csv))
         {
            fileWriter.append("Standard Publish Time (s),Async Publish Time (s)\n");
            for (int i = 0; i < messagesToPublish; ++i)
            {
               fileWriter.append(standardHistory[i] + ",").append(asyncHistory[i] + "\n");
            }
         }
      }
   }

   private static class DoubleStatisticsHelper extends DoubleSummaryStatistics
   {
      private double squaredSum;
      private double standardDeviation;
      private final double[] history;

      public DoubleStatisticsHelper(int maxSize)
      {
         super();
         squaredSum = 0.0;
         standardDeviation = 0.0;
         history = new double[maxSize];
      }

      @Override
      public void accept(double value)
      {
         super.accept(value);

         squaredSum += value * value;
         standardDeviation = Math.sqrt(getCount() * squaredSum - getSum() * getSum()) / getCount();
         history[(int) getCount() - 1] = value;
      }

      public double getStandardDeviation()
      {
         return standardDeviation;
      }

      public double[] getHistory()
      {
         return history;
      }

      @Override
      public String toString()
      {
         StringBuilder result = new StringBuilder(super.toString());

         result.replace(result.length() - 1, result.length(), ", ");
         result.append(standardDeviation).append("}");

         return result.toString();
      }
   }
}
