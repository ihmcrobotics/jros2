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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.Test;

/**
 * Latency-focused benchmark for {@link AsyncROS2Node}, {@link AsyncROS2Publisher}, and async subscription delivery.
 * <p>
 * Run with: {@code ./gradlew test --tests us.ihmc.jros2.AsyncROS2LatencyBenchmark}
 * or invoke {@link #main(String[])} from the test classpath.
 * <p>
 * Output CSV files are written to {@code ~/jros2_benchmarks/}.
 */
public class AsyncROS2LatencyBenchmark
{
   private static final int WARMUP_MESSAGES = 5_000;
   private static final int BENCHMARK_MESSAGES = 50_000;
   private static final int END_TO_END_MESSAGES = 10_000;
   private static final long END_TO_END_PACE_NS = 10_000L;
   private static final String OUTPUT_DIR = System.getProperty("user.home") + File.separator + "jros2_benchmarks" + File.separator;

   public static void main(String[] args) throws Exception
   {
      new File(OUTPUT_DIR).mkdirs();

      jros2.getLogger().info("AsyncROS2 Latency Benchmark");
      jros2.getLogger().info("Output: " + OUTPUT_DIR);

      AsyncROS2LatencyBenchmark benchmark = new AsyncROS2LatencyBenchmark();

      LatencyResult asyncCallerBurst = benchmark.benchmarkAsyncCallerLatencyBurst();
      jros2.getLogger().info("Async caller burst: " + asyncCallerBurst);

      LatencyResult syncCallerBurst = benchmark.benchmarkSyncCallerLatencyBurst();
      jros2.getLogger().info("Sync caller burst:  " + syncCallerBurst);

      LatencyResult endToEnd = benchmark.benchmarkAsyncEndToEndLatency();
      jros2.getLogger().info("Async end-to-end:   " + endToEnd);

      LatencyResult subscriptionCallback = benchmark.benchmarkAsyncSubscriptionCallbackLatency();
      jros2.getLogger().info("Async subscription callback latency: " + subscriptionCallback);

      ThroughputResult throughput = benchmark.benchmarkAsyncMaxThroughput();
      jros2.getLogger().info("Async max throughput: " + throughput);

      writeSummaryCsv(asyncCallerBurst, syncCallerBurst, endToEnd, subscriptionCallback, throughput);

      jros2.getLogger().info("Benchmark complete.");
   }

   /** Reduced benchmark for CI; full benchmark uses {@link #main(String[])}. */
   @Test
   public void runReducedLatencyBenchmark() throws Exception
   {
      AsyncROS2LatencyBenchmark benchmark = new AsyncROS2LatencyBenchmark();
      int warmup = WARMUP_MESSAGES / 10;
      int n = BENCHMARK_MESSAGES / 10;

      LatencyResult asyncCaller = benchmark.benchmarkAsyncCallerLatencyBurst(warmup, n);
      LatencyResult syncCaller = benchmark.benchmarkSyncCallerLatencyBurst(warmup, n);

      jros2.getLogger().info("Async caller burst: " + asyncCaller);
      jros2.getLogger().info("Sync caller burst:  " + syncCaller);

      org.junit.jupiter.api.Assertions.assertTrue(asyncCaller.averageNs < syncCaller.averageNs);
   }

   LatencyResult benchmarkAsyncCallerLatencyBurstReduced() throws InterruptedException
   {
      return benchmarkAsyncCallerLatencyBurst(WARMUP_MESSAGES / 10, BENCHMARK_MESSAGES / 10);
   }

   LatencyResult benchmarkSyncCallerLatencyBurstReduced() throws InterruptedException
   {
      return benchmarkSyncCallerLatencyBurst(WARMUP_MESSAGES / 10, BENCHMARK_MESSAGES / 10);
   }

   LatencyResult benchmarkAsyncCallerLatencyBurst() throws InterruptedException
   {
      return benchmarkAsyncCallerLatencyBurst(WARMUP_MESSAGES, BENCHMARK_MESSAGES);
   }

   LatencyResult benchmarkAsyncCallerLatencyBurst(int warmupMessages, int benchmarkMessages) throws InterruptedException
   {
      AsyncROS2Node node = new AsyncROS2Node("latency_async_caller");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/jros2/benchmark/async/caller_burst", example_interfaces.Bool.class);
      AsyncROS2Publisher<example_interfaces.Bool> publisher = node.createPublisher(topic, ROS2QoSProfile.DEFAULT, 512);

      node.createSubscription(topic, reader -> reader.read());

      example_interfaces.Bool message = new example_interfaces.Bool();
      message.setData(true);

      for (int i = 0; i < warmupMessages; i++)
         publisher.publish(message);

      Thread.sleep(100);

      long[] latencies = new long[benchmarkMessages];
      for (int i = 0; i < benchmarkMessages; i++)
      {
         long start = System.nanoTime();
         publisher.publish(message);
         latencies[i] = System.nanoTime() - start;
      }

      node.close();
      return new LatencyResult("async_caller_burst", latencies);
   }

   LatencyResult benchmarkSyncCallerLatencyBurst() throws InterruptedException
   {
      return benchmarkSyncCallerLatencyBurst(WARMUP_MESSAGES, BENCHMARK_MESSAGES);
   }

   LatencyResult benchmarkSyncCallerLatencyBurst(int warmupMessages, int benchmarkMessages) throws InterruptedException
   {
      ROS2Node node = new ROS2Node("latency_sync_caller");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/jros2/benchmark/sync/caller_burst", example_interfaces.Bool.class);
      ROS2Publisher<example_interfaces.Bool> publisher = node.createPublisher(topic);

      node.createSubscription(topic, reader -> reader.read());

      example_interfaces.Bool message = new example_interfaces.Bool();
      message.setData(true);

      for (int i = 0; i < warmupMessages; i++)
         publisher.publish(message);

      Thread.sleep(100);

      long[] latencies = new long[benchmarkMessages];
      for (int i = 0; i < benchmarkMessages; i++)
      {
         long start = System.nanoTime();
         publisher.publish(message);
         latencies[i] = System.nanoTime() - start;
      }

      node.close();
      return new LatencyResult("sync_caller_burst", latencies);
   }

   /**
    * End-to-end latency from caller {@code publish()} return to subscription callback invocation.
    * Uses {@link example_interfaces.Float64} to carry the publish timestamp in nanoseconds.
    */
   public LatencyResult benchmarkAsyncEndToEndLatency() throws InterruptedException
   {
      AsyncROS2Node node = new AsyncROS2Node("latency_async_e2e");
      ROS2Topic<example_interfaces.Float64> topic = new ROS2Topic<>("/jros2/benchmark/async/e2e", example_interfaces.Float64.class);
      AsyncROS2Publisher<example_interfaces.Float64> publisher = node.createPublisher(topic, ROS2QoSProfile.DEFAULT, 512);

      long[] latencies = new long[END_TO_END_MESSAGES];
      AtomicInteger index = new AtomicInteger();
      CountDownLatch latch = new CountDownLatch(END_TO_END_MESSAGES);

      node.createSubscription(topic, reader ->
      {
         example_interfaces.Float64 sample = reader.read();
         if (sample != null)
         {
            long sentTime = (long) sample.getData();
            int i = index.getAndIncrement();
            if (i < latencies.length)
               latencies[i] = System.nanoTime() - sentTime;
            latch.countDown();
         }
      });

      publisher.waitForSubscription(5000);

      example_interfaces.Float64 message = new example_interfaces.Float64();

      for (int i = 0; i < WARMUP_MESSAGES; i++)
      {
         message.setData(System.nanoTime());
         publisher.publish(message);
      }

      Thread.sleep(100);
      index.set(0);

      for (int i = 0; i < END_TO_END_MESSAGES; i++)
      {
         long publishTime = System.nanoTime();
         message.setData(publishTime);
         publisher.publish(message);
         LockSupport.parkNanos(END_TO_END_PACE_NS);
      }

      latch.await(60, TimeUnit.SECONDS);
      node.close();

      int count = index.get();
      return new LatencyResult("async_end_to_end", Arrays.copyOf(latencies, count));
   }

   /**
    * Time from publish to user callback on the async subscription thread (excludes user read/deserialize work).
    */
   public LatencyResult benchmarkAsyncSubscriptionCallbackLatency() throws InterruptedException
   {
      AsyncROS2Node node = new AsyncROS2Node("latency_async_sub_callback");
      ROS2Topic<example_interfaces.Float64> topic = new ROS2Topic<>("/jros2/benchmark/async/sub_callback", example_interfaces.Float64.class);
      AsyncROS2Publisher<example_interfaces.Float64> publisher = node.createPublisher(topic, ROS2QoSProfile.DEFAULT, 512);

      long[] latencies = new long[END_TO_END_MESSAGES];
      AtomicInteger index = new AtomicInteger();
      CountDownLatch latch = new CountDownLatch(END_TO_END_MESSAGES);

      node.createSubscription(topic, reader ->
      {
         long callbackTime = System.nanoTime();
         example_interfaces.Float64 sample = reader.read();
         if (sample != null)
         {
            long sentTime = (long) sample.getData();
            int i = index.getAndIncrement();
            if (i < latencies.length)
               latencies[i] = callbackTime - sentTime;
            latch.countDown();
         }
      });

      publisher.waitForSubscription(5000);

      example_interfaces.Float64 message = new example_interfaces.Float64();

      for (int i = 0; i < WARMUP_MESSAGES; i++)
      {
         message.setData(System.nanoTime());
         publisher.publish(message);
      }

      Thread.sleep(100);
      index.set(0);

      for (int i = 0; i < END_TO_END_MESSAGES; i++)
      {
         message.setData(System.nanoTime());
         publisher.publish(message);
         LockSupport.parkNanos(END_TO_END_PACE_NS);
      }

      latch.await(60, TimeUnit.SECONDS);
      node.close();

      int count = index.get();
      return new LatencyResult("async_subscription_callback", Arrays.copyOf(latencies, count));
   }

   /**
    * Maximum sustained publish rate with minimal message type until the publisher ring buffer drops samples.
    */
   public ThroughputResult benchmarkAsyncMaxThroughput() throws InterruptedException
   {
      AsyncROS2Node node = new AsyncROS2Node("latency_async_throughput");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/jros2/benchmark/async/throughput", example_interfaces.Bool.class);
      AsyncROS2Publisher<example_interfaces.Bool> publisher = node.createPublisher(topic, ROS2QoSProfile.DEFAULT, 4096);

      AtomicLong received = new AtomicLong();
      node.createSubscription(topic, reader ->
      {
         reader.read();
         received.incrementAndGet();
      });

      publisher.waitForSubscription(5000);

      example_interfaces.Bool message = new example_interfaces.Bool();
      message.setData(true);

      int messagesToSend = 200_000;
      long start = System.nanoTime();
      for (int i = 0; i < messagesToSend; i++)
         publisher.publish(message);
      long publishDuration = System.nanoTime() - start;

      Thread.sleep(500);

      node.close();

      double publishRate = messagesToSend * 1.0e9 / publishDuration;
      return new ThroughputResult(messagesToSend, publishDuration, received.get(), publishRate);
   }

   private static void writeSummaryCsv(LatencyResult asyncCaller,
                                       LatencyResult syncCaller,
                                       LatencyResult endToEnd,
                                       LatencyResult subscriptionCallback,
                                       ThroughputResult throughput) throws IOException
   {
      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmmss"));
      String filename = OUTPUT_DIR + "async_latency_summary_" + timestamp + ".csv";

      try (FileWriter writer = new FileWriter(filename))
      {
         writer.append("Benchmark,Avg (us),P50 (us),P95 (us),P99 (us),P999 (us),Max (us),Count\n");
         appendLatencyRow(writer, asyncCaller);
         appendLatencyRow(writer, syncCaller);
         appendLatencyRow(writer, endToEnd);
         appendLatencyRow(writer, subscriptionCallback);

         writer.append("\nThroughput benchmark\n");
         writer.append("Messages sent,Publish duration (ms),Messages received,Publish rate (msg/s)\n");
         writer.append(String.valueOf(throughput.messagesSent)).append(",");
         writer.append(String.valueOf(throughput.publishDurationNs / 1_000_000.0)).append(",");
         writer.append(String.valueOf(throughput.messagesReceived)).append(",");
         writer.append(String.valueOf(throughput.publishRateMsgPerSecond)).append("\n");
      }

      jros2.getLogger().info("Wrote summary to " + filename);
   }

   private static void appendLatencyRow(FileWriter writer, LatencyResult result) throws IOException
   {
      writer.append(result.name).append(",");
      writer.append(String.format("%.3f", result.averageNs / 1000.0)).append(",");
      writer.append(String.format("%.3f", result.p50Ns / 1000.0)).append(",");
      writer.append(String.format("%.3f", result.p95Ns / 1000.0)).append(",");
      writer.append(String.format("%.3f", result.p99Ns / 1000.0)).append(",");
      writer.append(String.format("%.3f", result.p999Ns / 1000.0)).append(",");
      writer.append(String.format("%.3f", result.maxNs / 1000.0)).append(",");
      writer.append(String.valueOf(result.count)).append("\n");
   }

   static final class LatencyResult
   {
      final String name;
      final long[] latencies;
      final int count;
      final long averageNs;
      final long minNs;
      final long maxNs;
      final long p50Ns;
      final long p95Ns;
      final long p99Ns;
      final long p999Ns;
      final double stdDevNs;

      LatencyResult(String name, long[] latencies)
      {
         this.name = name;
         this.latencies = latencies;
         this.count = latencies.length;

         long sum = 0;
         long min = Long.MAX_VALUE;
         long max = Long.MIN_VALUE;
         for (long latency : latencies)
         {
            sum += latency;
            min = Math.min(min, latency);
            max = Math.max(max, latency);
         }

         averageNs = count > 0 ? sum / count : 0;
         minNs = count > 0 ? min : 0;
         maxNs = count > 0 ? max : 0;

         long[] sorted = latencies.clone();
         Arrays.sort(sorted);
         p50Ns = percentile(sorted, 0.50);
         p95Ns = percentile(sorted, 0.95);
         p99Ns = percentile(sorted, 0.99);
         p999Ns = percentile(sorted, 0.999);

         double sumSquaredDiff = 0;
         for (long latency : latencies)
         {
            double diff = latency - averageNs;
            sumSquaredDiff += diff * diff;
         }
         stdDevNs = count > 0 ? Math.sqrt(sumSquaredDiff / count) : 0;
      }

      private static long percentile(long[] sorted, double fraction)
      {
         if (sorted.length == 0)
            return 0;
         int index = (int) Math.min(sorted.length - 1, Math.round(fraction * (sorted.length - 1)));
         return sorted[index];
      }

      @Override
      public String toString()
      {
         return String.format("%s avg=%.2f us p50=%.2f us p95=%.2f us p99=%.2f us p999=%.2f us stddev=%.2f us max=%.2f us (n=%d)",
                            name,
                            averageNs / 1000.0,
                            p50Ns / 1000.0,
                            p95Ns / 1000.0,
                            p99Ns / 1000.0,
                            p999Ns / 1000.0,
                            stdDevNs / 1000.0,
                            maxNs / 1000.0,
                            count);
      }
   }

   static final class ThroughputResult
   {
      final int messagesSent;
      final long publishDurationNs;
      final long messagesReceived;
      final double publishRateMsgPerSecond;

      ThroughputResult(int messagesSent, long publishDurationNs, long messagesReceived, double publishRateMsgPerSecond)
      {
         this.messagesSent = messagesSent;
         this.publishDurationNs = publishDurationNs;
         this.messagesReceived = messagesReceived;
         this.publishRateMsgPerSecond = publishRateMsgPerSecond;
      }

      @Override
      public String toString()
      {
         return String.format("%d msgs in %.2f ms (%.0f msg/s publish), %d received",
                            messagesSent,
                            publishDurationNs / 1_000_000.0,
                            publishRateMsgPerSecond,
                            messagesReceived);
      }
   }
}
