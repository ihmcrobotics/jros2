package us.ihmc;

import std_msgs.msg.dds.Bool;
import us.ihmc.log.LogTools;
import us.ihmc.ros2.ROS2Node;
import us.ihmc.ros2.ROS2NodeBuilder;
import us.ihmc.ros2.ROS2Publisher;
import us.ihmc.ros2.ROS2Topic;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.DoubleSummaryStatistics;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiFunction;

public class ihmcros2libraryBenchmark
{
   public static void main(String[] args) throws InterruptedException, IOException
   {
      final boolean generateCSV = true;
      final String fileDirectory = System.getProperty("user.home") + File.separator + "Documents" + File.separator;

      final int messagesToPublish = 100000;
      final boolean expected = true;

      // Create normal and async nodes
      ROS2Node ros2Node = new ROS2NodeBuilder().build("standard_node");

      // Create topics to publish on
      ROS2Topic<Bool> standardTopic = new ROS2Topic<>().withType(Bool.class).withSuffix("standard_topic");

      // Create normal and async publishers
      ROS2Publisher<Bool> standardPublisher = ros2Node.createPublisher(standardTopic);
      DoubleStatisticsHelper standardPublisherStatistics = new DoubleStatisticsHelper(messagesToPublish);

      // Create subscribers
      ros2Node.createSubscription(standardTopic, subscriber ->
      {
         if (expected != subscriber.takeNextData().getData())
         {
            throw new RuntimeException("Data mismatch");
         }
      });

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

      // Start both threads
      LogTools.info("Starting publish threads...");
      standardPublisherThread.start();

      // Wait until they finish
      standardPublisherThread.join();
      LogTools.info("Publish threads finished");

      // Log statistics
      LogTools.info("Standard publisher statistics: {}", standardPublisherStatistics);

      // Ensure both threads published all messages
      if (messagesToPublish != standardPublisherStatistics.getCount())
      {
         throw new RuntimeException("Did not publish all messages");
      }

      // Cleanup
      ros2Node.destroy();

      if (generateCSV)
      {
         String fileName = "Benchmark" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmm")) + ".csv";
         File csv = new File(fileDirectory + fileName);

         double[] standardHistory = standardPublisherStatistics.getHistory();

         try (FileWriter fileWriter = new FileWriter(csv))
         {
            fileWriter.append("Standard Publish Time (s)\n");
            for (int i = 0; i < messagesToPublish; ++i)
            {
               fileWriter.append(standardHistory[i] + ",");
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
