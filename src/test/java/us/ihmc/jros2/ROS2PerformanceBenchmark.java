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

import std_msgs.msg.dds.Bool;
import std_msgs.msg.dds.Float64;
import us.ihmc.log.LogTools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Performance benchmark comparing ROS2Node/ROS2Publisher vs AsyncROS2Node/AsyncROS2Publisher.
 * This is not a unit test but a benchmark tool that outputs CSV files for analysis.
 *
 * Run this benchmark with: ./gradlew test --tests ROS2PerformanceBenchmark
 */
public class ROS2PerformanceBenchmark
{
   private static final int WARMUP_MESSAGES = 1000;
   private static final int BENCHMARK_MESSAGES = 10000;
   private static final long PUBLISH_DELAY_NS = 100_000; // 0.1ms between publishes

   private static final java.lang.String OUTPUT_DIR = System.getProperty("user.home") + File.separator + "jros2_benchmarks" + File.separator;

   public static void main(String[] args) throws Exception
   {
      LogTools.info("Starting ROS2 Performance Benchmark");
      LogTools.info("Output directory: {}", OUTPUT_DIR);

      new File(OUTPUT_DIR).mkdirs();

      ROS2PerformanceBenchmark benchmark = new ROS2PerformanceBenchmark();

      // Run benchmarks for different message types
      LogTools.info("\n=== Bool Message Benchmarks ===");
      benchmark.runBoolBenchmark();

      LogTools.info("\n=== Float64 Message Benchmarks ===");
      benchmark.runFloat64Benchmark();

      LogTools.info("\n=== String Message Benchmarks ===");
      benchmark.runStringBenchmark();

      LogTools.info("\n=== Multi-threaded Publisher Benchmarks ===");
      benchmark.runMultiThreadedBenchmark();

      LogTools.info("\nAll benchmarks complete! Check {} for CSV files", OUTPUT_DIR);
   }

   public void runBoolBenchmark() throws Exception
   {
      LogTools.info("Running Bool message benchmark...");

      // Standard ROS2Node benchmark
      BenchmarkResult standardResult = benchmarkStandardNode(Bool.class, "/benchmark/bool/standard", WARMUP_MESSAGES, BENCHMARK_MESSAGES);
      LogTools.info("Standard Node - Bool: {}", standardResult);

      // Async ROS2Node benchmark
      BenchmarkResult asyncResult = benchmarkAsyncNode(Bool.class, "/benchmark/bool/async", WARMUP_MESSAGES, BENCHMARK_MESSAGES);
      LogTools.info("Async Node - Bool:    {}", asyncResult);

      // Write CSV
      writeBenchmarkCSV("bool_benchmark", standardResult, asyncResult);

      LogTools.info("Bool benchmark complete - Async is {:.2f}x faster",
                    standardResult.averageLatencyNs / (double) asyncResult.averageLatencyNs);
   }

   public void runFloat64Benchmark() throws Exception
   {
      LogTools.info("Running Float64 message benchmark...");

      BenchmarkResult standardResult = benchmarkStandardNode(Float64.class, "/benchmark/float64/standard", WARMUP_MESSAGES, BENCHMARK_MESSAGES);
      LogTools.info("Standard Node - Float64: {}", standardResult);

      BenchmarkResult asyncResult = benchmarkAsyncNode(Float64.class, "/benchmark/float64/async", WARMUP_MESSAGES, BENCHMARK_MESSAGES);
      LogTools.info("Async Node - Float64:    {}", asyncResult);

      writeBenchmarkCSV("float64_benchmark", standardResult, asyncResult);

      LogTools.info("Float64 benchmark complete - Async is {:.2f}x faster",
                    standardResult.averageLatencyNs / (double) asyncResult.averageLatencyNs);
   }

   public void runStringBenchmark() throws Exception
   {
      LogTools.info("Running String message benchmark...");

      BenchmarkResult standardResult = benchmarkStandardNode(std_msgs.msg.dds.String.class, "/benchmark/string/standard", WARMUP_MESSAGES, BENCHMARK_MESSAGES);
      LogTools.info("Standard Node - String: {}", standardResult);

      BenchmarkResult asyncResult = benchmarkAsyncNode(std_msgs.msg.dds.String.class, "/benchmark/string/async", WARMUP_MESSAGES, BENCHMARK_MESSAGES);
      LogTools.info("Async Node - String:    {}", asyncResult);

      writeBenchmarkCSV("string_benchmark", standardResult, asyncResult);

      LogTools.info("String benchmark complete - Async is {:.2f}x faster",
                    standardResult.averageLatencyNs / (double) asyncResult.averageLatencyNs);
   }

   public void runMultiThreadedBenchmark() throws Exception
   {
      LogTools.info("Running multi-threaded publisher benchmark...");

      int[] threadCounts = {1, 2, 4, 8, 16};
      List<MultiThreadBenchmarkResult> results = new ArrayList<>();

      for (int threadCount : threadCounts)
      {
         LogTools.info("Testing with {} threads...", threadCount);

         MultiThreadBenchmarkResult standardResult = benchmarkStandardNodeMultiThreaded(threadCount, BENCHMARK_MESSAGES / threadCount);
         MultiThreadBenchmarkResult asyncResult = benchmarkAsyncNodeMultiThreaded(threadCount, BENCHMARK_MESSAGES / threadCount);

         LogTools.info("  Standard: {:.2f} msg/s, Async: {:.2f} msg/s",
                      standardResult.throughputMsgPerSecond, asyncResult.throughputMsgPerSecond);

         results.add(standardResult);
         results.add(asyncResult);
      }

      writeMultiThreadedBenchmarkCSV("multithreaded_benchmark", results);
   }

   private <T extends ROS2Message<T>> BenchmarkResult benchmarkStandardNode(Class<T> messageType,
                                                                            java.lang.String topicName,
                                                                            int warmupMessages,
                                                                            int benchmarkMessages) throws Exception
   {
      ROS2Node node = new ROS2Node("benchmark_standard_node");
      ROS2Topic<T> topic = new ROS2Topic<>(topicName, messageType);
      ROS2Publisher<T> publisher = node.createPublisher(topic);

      AtomicInteger receivedCount = new AtomicInteger(0);
      CountDownLatch latch = new CountDownLatch(warmupMessages + benchmarkMessages);

      node.createSubscription(topic, reader -> {
         reader.read();
         receivedCount.incrementAndGet();
         latch.countDown();
      });

      T message = ROS2Message.createInstance(messageType);
      setMessageData(message);

      // Warmup
      for (int i = 0; i < warmupMessages; i++)
      {
         publisher.publish(message);
         LockSupport.parkNanos(PUBLISH_DELAY_NS);
      }

      // Benchmark
      long[] latencies = new long[benchmarkMessages];
      long startTime = System.nanoTime();

      for (int i = 0; i < benchmarkMessages; i++)
      {
         long publishStart = System.nanoTime();
         publisher.publish(message);
         latencies[i] = System.nanoTime() - publishStart;
         LockSupport.parkNanos(PUBLISH_DELAY_NS);
      }

      long endTime = System.nanoTime();

      // Wait for all messages to be received
      latch.await();

      node.close();

      return new BenchmarkResult("Standard", messageType.getSimpleName(), latencies, endTime - startTime, receivedCount.get() - warmupMessages);
   }

   private <T extends ROS2Message<T>> BenchmarkResult benchmarkAsyncNode(Class<T> messageType,
                                                                         java.lang.String topicName,
                                                                         int warmupMessages,
                                                                         int benchmarkMessages) throws Exception
   {
      AsyncROS2Node node = new AsyncROS2Node("benchmark_async_node");
      ROS2Topic<T> topic = new ROS2Topic<>(topicName, messageType);
      AsyncROS2Publisher<T> publisher = (AsyncROS2Publisher<T>) node.createPublisher(topic);

      AtomicInteger receivedCount = new AtomicInteger(0);
      CountDownLatch latch = new CountDownLatch(warmupMessages + benchmarkMessages);

      node.createSubscription(topic, reader -> {
         reader.read();
         receivedCount.incrementAndGet();
         latch.countDown();
      });

      T message = ROS2Message.createInstance(messageType);
      setMessageData(message);

      // Warmup
      for (int i = 0; i < warmupMessages; i++)
      {
         publisher.publish(message);
         LockSupport.parkNanos(PUBLISH_DELAY_NS);
      }

      // Benchmark
      long[] latencies = new long[benchmarkMessages];
      long startTime = System.nanoTime();

      for (int i = 0; i < benchmarkMessages; i++)
      {
         long publishStart = System.nanoTime();
         publisher.publish(message);
         latencies[i] = System.nanoTime() - publishStart;
         LockSupport.parkNanos(PUBLISH_DELAY_NS);
      }

      long endTime = System.nanoTime();

      // Wait for all messages to be received
      latch.await();

      node.close();

      return new BenchmarkResult("Async", messageType.getSimpleName(), latencies, endTime - startTime, receivedCount.get() - warmupMessages);
   }

   private MultiThreadBenchmarkResult benchmarkStandardNodeMultiThreaded(int threadCount, int messagesPerThread) throws Exception
   {
      ROS2Node node = new ROS2Node("benchmark_mt_standard");
      ROS2Topic<Bool> topic = new ROS2Topic<>("/benchmark/mt/standard", Bool.class);

      CountDownLatch startLatch = new CountDownLatch(threadCount);
      CountDownLatch endLatch = new CountDownLatch(threadCount);
      AtomicInteger receivedCount = new AtomicInteger(0);

      node.createSubscription(topic, reader -> {
         reader.read();
         receivedCount.incrementAndGet();
      });

      Thread[] threads = new Thread[threadCount];
      long[] threadTimes = new long[threadCount];

      for (int i = 0; i < threadCount; i++)
      {
         final int threadIndex = i;
         threads[i] = new Thread(() -> {
            ROS2Publisher<Bool> publisher = node.createPublisher(topic);
            Bool message = new Bool();
            message.setData(true);

            startLatch.countDown();
            try { startLatch.await(); } catch (InterruptedException e) { return; }

            long start = System.nanoTime();
            for (int j = 0; j < messagesPerThread; j++)
            {
               publisher.publish(message);
               LockSupport.parkNanos(PUBLISH_DELAY_NS);
            }
            threadTimes[threadIndex] = System.nanoTime() - start;

            endLatch.countDown();
         });
         threads[i].start();
      }

      long benchmarkStart = System.nanoTime();
      for (Thread thread : threads) thread.join();
      long benchmarkEnd = System.nanoTime();

      endLatch.await();

      // Wait a bit for all messages to be received
      Thread.sleep(500);

      node.close();

      long totalTimeNs = benchmarkEnd - benchmarkStart;
      int totalMessages = threadCount * messagesPerThread;
      double throughput = (totalMessages * 1e9) / totalTimeNs;

      return new MultiThreadBenchmarkResult("Standard", threadCount, totalMessages, totalTimeNs, throughput, receivedCount.get());
   }

   private MultiThreadBenchmarkResult benchmarkAsyncNodeMultiThreaded(int threadCount, int messagesPerThread) throws Exception
   {
      AsyncROS2Node node = new AsyncROS2Node("benchmark_mt_async");
      ROS2Topic<Bool> topic = new ROS2Topic<>("/benchmark/mt/async", Bool.class);

      CountDownLatch startLatch = new CountDownLatch(threadCount);
      CountDownLatch endLatch = new CountDownLatch(threadCount);
      AtomicInteger receivedCount = new AtomicInteger(0);

      node.createSubscription(topic, reader -> {
         reader.read();
         receivedCount.incrementAndGet();
      });

      Thread[] threads = new Thread[threadCount];
      long[] threadTimes = new long[threadCount];

      for (int i = 0; i < threadCount; i++)
      {
         final int threadIndex = i;
         threads[i] = new Thread(() -> {
            AsyncROS2Publisher<Bool> publisher = (AsyncROS2Publisher<Bool>) node.createPublisher(topic);
            Bool message = new Bool();
            message.setData(true);

            startLatch.countDown();
            try { startLatch.await(); } catch (InterruptedException e) { return; }

            long start = System.nanoTime();
            for (int j = 0; j < messagesPerThread; j++)
            {
               publisher.publish(message);
               LockSupport.parkNanos(PUBLISH_DELAY_NS);
            }
            threadTimes[threadIndex] = System.nanoTime() - start;

            endLatch.countDown();
         });
         threads[i].start();
      }

      long benchmarkStart = System.nanoTime();
      for (Thread thread : threads) thread.join();
      long benchmarkEnd = System.nanoTime();

      endLatch.await();

      // Wait a bit for all messages to be received
      Thread.sleep(500);

      node.close();

      long totalTimeNs = benchmarkEnd - benchmarkStart;
      int totalMessages = threadCount * messagesPerThread;
      double throughput = (totalMessages * 1e9) / totalTimeNs;

      return new MultiThreadBenchmarkResult("Async", threadCount, totalMessages, totalTimeNs, throughput, receivedCount.get());
   }

   @SuppressWarnings("unchecked")
   private <T extends ROS2Message<T>> void setMessageData(T message)
   {
      if (message instanceof Bool)
      {
         ((Bool) message).setData(true);
      }
      else if (message instanceof Float64)
      {
         ((Float64) message).setData(3.14159);
      }
      else if (message instanceof std_msgs.msg.dds.String)
      {
         ((std_msgs.msg.dds.String) message).setData("Benchmark test message with some data");
      }
   }

   private void writeBenchmarkCSV(java.lang.String name, BenchmarkResult standard, BenchmarkResult async) throws IOException
   {
      java.lang.String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmmss"));
      java.lang.String filename = OUTPUT_DIR + name + "_" + timestamp + ".csv";

      try (FileWriter writer = new FileWriter(filename))
      {
         // Header
         writer.append("Message Index,Standard Latency (ns),Async Latency (ns)\n");

         // Data rows
         int maxLength = Math.max(standard.latencies.length, async.latencies.length);
         for (int i = 0; i < maxLength; i++)
         {
            writer.append(java.lang.String.valueOf(i));
            writer.append(",");
            writer.append(i < standard.latencies.length ? java.lang.String.valueOf(standard.latencies[i]) : "");
            writer.append(",");
            writer.append(i < async.latencies.length ? java.lang.String.valueOf(async.latencies[i]) : "");
            writer.append("\n");
         }

         // Summary
         writer.append("\nSummary\n");
         writer.append("Metric,Standard,Async\n");
         writer.append("Average Latency (ns),").append(java.lang.String.valueOf(standard.averageLatencyNs)).append(",")
               .append(java.lang.String.valueOf(async.averageLatencyNs)).append("\n");
         writer.append("Min Latency (ns),").append(java.lang.String.valueOf(standard.minLatencyNs)).append(",")
               .append(java.lang.String.valueOf(async.minLatencyNs)).append("\n");
         writer.append("Max Latency (ns),").append(java.lang.String.valueOf(standard.maxLatencyNs)).append(",")
               .append(java.lang.String.valueOf(async.maxLatencyNs)).append("\n");
         writer.append("Std Dev Latency (ns),").append(java.lang.String.valueOf(standard.stdDevLatencyNs)).append(",")
               .append(java.lang.String.valueOf(async.stdDevLatencyNs)).append("\n");
         writer.append("P50 Latency (ns),").append(java.lang.String.valueOf(standard.p50LatencyNs)).append(",")
               .append(java.lang.String.valueOf(async.p50LatencyNs)).append("\n");
         writer.append("P95 Latency (ns),").append(java.lang.String.valueOf(standard.p95LatencyNs)).append(",")
               .append(java.lang.String.valueOf(async.p95LatencyNs)).append("\n");
         writer.append("P99 Latency (ns),").append(java.lang.String.valueOf(standard.p99LatencyNs)).append(",")
               .append(java.lang.String.valueOf(async.p99LatencyNs)).append("\n");
         writer.append("Messages Received,").append(java.lang.String.valueOf(standard.messagesReceived)).append(",")
               .append(java.lang.String.valueOf(async.messagesReceived)).append("\n");
         writer.append("Total Time (ms),").append(java.lang.String.valueOf(standard.totalTimeNs / 1_000_000.0)).append(",")
               .append(java.lang.String.valueOf(async.totalTimeNs / 1_000_000.0)).append("\n");
      }

      LogTools.info("Wrote benchmark results to: {}", filename);
   }

   private void writeMultiThreadedBenchmarkCSV(java.lang.String name, List<MultiThreadBenchmarkResult> results) throws IOException
   {
      java.lang.String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmmss"));
      java.lang.String filename = OUTPUT_DIR + name + "_" + timestamp + ".csv";

      try (FileWriter writer = new FileWriter(filename))
      {
         writer.append("Node Type,Thread Count,Total Messages,Total Time (ms),Throughput (msg/s),Messages Received\n");

         for (MultiThreadBenchmarkResult result : results)
         {
            writer.append(result.nodeType).append(",");
            writer.append(java.lang.String.valueOf(result.threadCount)).append(",");
            writer.append(java.lang.String.valueOf(result.totalMessages)).append(",");
            writer.append(java.lang.String.valueOf(result.totalTimeNs / 1_000_000.0)).append(",");
            writer.append(java.lang.String.format("%.2f", result.throughputMsgPerSecond)).append(",");
            writer.append(java.lang.String.valueOf(result.messagesReceived)).append("\n");
         }
      }

      LogTools.info("Wrote multi-threaded benchmark results to: {}", filename);
   }

   private static class BenchmarkResult
   {
      final java.lang.String nodeType;
      final java.lang.String messageType;
      final long[] latencies;
      final long totalTimeNs;
      final int messagesReceived;

      final long averageLatencyNs;
      final long minLatencyNs;
      final long maxLatencyNs;
      final double stdDevLatencyNs;
      final long p50LatencyNs;
      final long p95LatencyNs;
      final long p99LatencyNs;

      BenchmarkResult(java.lang.String nodeType, java.lang.String messageType, long[] latencies, long totalTimeNs, int messagesReceived)
      {
         this.nodeType = nodeType;
         this.messageType = messageType;
         this.latencies = latencies;
         this.totalTimeNs = totalTimeNs;
         this.messagesReceived = messagesReceived;

         // Calculate statistics
         long sum = 0;
         long min = Long.MAX_VALUE;
         long max = Long.MIN_VALUE;

         for (long latency : latencies)
         {
            sum += latency;
            min = Math.min(min, latency);
            max = Math.max(max, latency);
         }

         this.averageLatencyNs = sum / latencies.length;
         this.minLatencyNs = min;
         this.maxLatencyNs = max;

         // Calculate standard deviation
         double sumSquaredDiff = 0;
         for (long latency : latencies)
         {
            double diff = latency - averageLatencyNs;
            sumSquaredDiff += diff * diff;
         }
         this.stdDevLatencyNs = Math.sqrt(sumSquaredDiff / latencies.length);

         // Calculate percentiles
         long[] sortedLatencies = latencies.clone();
         java.util.Arrays.sort(sortedLatencies);
         this.p50LatencyNs = sortedLatencies[sortedLatencies.length / 2];
         this.p95LatencyNs = sortedLatencies[(int) (sortedLatencies.length * 0.95)];
         this.p99LatencyNs = sortedLatencies[(int) (sortedLatencies.length * 0.99)];
      }

      @Override
      public java.lang.String toString()
      {
         return java.lang.String.format("%s/%s - Avg: %.2fµs, Min: %.2fµs, Max: %.2fµs, StdDev: %.2fµs, P95: %.2fµs, P99: %.2fµs",
               nodeType, messageType,
               averageLatencyNs / 1000.0, minLatencyNs / 1000.0, maxLatencyNs / 1000.0,
               stdDevLatencyNs / 1000.0, p95LatencyNs / 1000.0, p99LatencyNs / 1000.0);
      }
   }

   private static class MultiThreadBenchmarkResult
   {
      final java.lang.String nodeType;
      final int threadCount;
      final int totalMessages;
      final long totalTimeNs;
      final double throughputMsgPerSecond;
      final int messagesReceived;

      MultiThreadBenchmarkResult(java.lang.String nodeType, int threadCount, int totalMessages, long totalTimeNs,
                                 double throughputMsgPerSecond, int messagesReceived)
      {
         this.nodeType = nodeType;
         this.threadCount = threadCount;
         this.totalMessages = totalMessages;
         this.totalTimeNs = totalTimeNs;
         this.throughputMsgPerSecond = throughputMsgPerSecond;
         this.messagesReceived = messagesReceived;
      }
   }
}
