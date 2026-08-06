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

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.CDRBuffer;

import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Steady-state allocation checks for the jros2 publish/subscribe and CDR paths.
 * Uses HotSpot {@link ThreadMXBean#getThreadAllocatedBytes(long)} so these run in the default
 * test category (no allocation javaagent required).
 * <p>
 * Fixed-size messages are expected to be allocation-free after warmup. Variable-size payloads
 * (growing strings/sequences) may still allocate when buffers grow.
 */
public class ROS2AllocationTest
{
   private static final int WARMUP_MESSAGES = 2_000;
   private static final int MEASURED_MESSAGES = 10_000;
   /** JVM/JIT bookkeeping can show up as a few small allocations over long loops. */
   private static final long MAX_TOTAL_ALLOCATED_BYTES = 2_048L;

   @Test
   public void testSyncPublishCallerThreadIsAllocationFree()
   {
      ThreadMXBean threadMXBean = requireThreadAllocationBean();

      ROS2Node node = new ROS2Node("sync_alloc_pub");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/sync_alloc_pub", example_interfaces.Bool.class);
      ROS2Publisher<example_interfaces.Bool> publisher = node.createPublisher(topic);

      example_interfaces.Bool message = new example_interfaces.Bool();
      message.setData(true);

      for (int i = 0; i < WARMUP_MESSAGES; ++i)
      {
         publisher.publish(message);
      }

      assertSteadyStateAllocationFree(threadMXBean, MEASURED_MESSAGES, () -> publisher.publish(message));

      node.close();
   }

   @Test
   public void testAsyncPublishCallerThreadIsAllocationFree()
   {
      ThreadMXBean threadMXBean = requireThreadAllocationBean();

      AsyncROS2Node asyncNode = new AsyncROS2Node("async_alloc_pub");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/async_alloc_pub", example_interfaces.Bool.class);
      ROS2Publisher<example_interfaces.Bool> publisher = asyncNode.createPublisher(topic);

      example_interfaces.Bool message = new example_interfaces.Bool();
      message.setData(true);

      for (int i = 0; i < WARMUP_MESSAGES; ++i)
      {
         publisher.publish(message);
      }

      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));

      assertSteadyStateAllocationFree(threadMXBean, MEASURED_MESSAGES, () -> publisher.publish(message));

      asyncNode.close();
   }

   @Test
   public void testAsyncPublishThreadIsAllocationFreeWhileIdleWaiting()
   {
      ThreadMXBean threadMXBean = requireThreadAllocationBean();

      String nodeName = "async_alloc_pub_thread";
      AsyncROS2Node asyncNode = new AsyncROS2Node(nodeName);
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/async_alloc_pub_thread", example_interfaces.Bool.class);
      ROS2Publisher<example_interfaces.Bool> publisher = asyncNode.createPublisher(topic);

      example_interfaces.Bool message = new example_interfaces.Bool();
      message.setData(true);

      Thread publishThread = findThreadByName("AsyncROS2NodePublishThread-" + nodeName);
      long publishThreadId = publishThread.getId();

      // Warmup including idle waits so park/unpark paths are JIT'd.
      for (int i = 0; i < WARMUP_MESSAGES; ++i)
      {
         publisher.publish(message);
         LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(50));
      }
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));

      long before = threadMXBean.getThreadAllocatedBytes(publishThreadId);
      for (int i = 0; i < MEASURED_MESSAGES; ++i)
      {
         publisher.publish(message);
         // Force the publish thread to drain and go idle between publishes (former take() allocation path).
         LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(50));
      }
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));
      long allocated = threadMXBean.getThreadAllocatedBytes(publishThreadId) - before;

      assertTrue(allocated <= MAX_TOTAL_ALLOCATED_BYTES,
                 "Async publish thread allocated " + allocated + " bytes over " + MEASURED_MESSAGES
                 + " publish/idle cycles (expected <= " + MAX_TOTAL_ALLOCATED_BYTES + ")");

      asyncNode.close();
   }

   @Test
   public void testAsyncPublishThreadIsAllocationFreeUnderBurstLoad() throws InterruptedException
   {
      ThreadMXBean threadMXBean = requireThreadAllocationBean();

      String nodeName = "async_alloc_burst";
      AsyncROS2Node asyncNode = new AsyncROS2Node(nodeName);
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/async_alloc_burst", example_interfaces.Bool.class);

      final int publisherCount = 4;
      @SuppressWarnings("unchecked")
      ROS2Publisher<example_interfaces.Bool>[] publishers = new ROS2Publisher[publisherCount];
      for (int i = 0; i < publisherCount; ++i)
      {
         publishers[i] = asyncNode.createPublisher(topic);
      }

      example_interfaces.Bool message = new example_interfaces.Bool();
      message.setData(true);

      Thread publishThread = findThreadByName("AsyncROS2NodePublishThread-" + nodeName);
      long publishThreadId = publishThread.getId();

      // Warmup with concurrent publishers offering tasks.
      runConcurrentPublishes(publishers, message, WARMUP_MESSAGES / publisherCount);
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));

      long before = threadMXBean.getThreadAllocatedBytes(publishThreadId);
      runConcurrentPublishes(publishers, message, MEASURED_MESSAGES / publisherCount);
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));
      long allocated = threadMXBean.getThreadAllocatedBytes(publishThreadId) - before;

      assertTrue(allocated <= MAX_TOTAL_ALLOCATED_BYTES,
                 "Async publish thread allocated " + allocated + " bytes under burst load from "
                 + publisherCount + " publishers (expected <= " + MAX_TOTAL_ALLOCATED_BYTES + ")");

      asyncNode.close();
   }

   @Test
   public void testReuseSubscriptionReadIsAllocationFree() throws InterruptedException
   {
      ThreadMXBean threadMXBean = requireThreadAllocationBean();

      ROS2Node node = new ROS2Node("alloc_sub_reuse");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/alloc_sub_reuse", example_interfaces.Bool.class);

      ROS2QoSProfile qos = new ROS2QoSProfile();
      qos.reliability(ROS2QoSProfile.Reliability.RELIABLE);
      qos.history(ROS2QoSProfile.History.KEEP_ALL);
      qos.depth(MEASURED_MESSAGES);

      AtomicBoolean measuring = new AtomicBoolean(false);
      AtomicInteger measuredReads = new AtomicInteger();
      AtomicLong allocatedDuringRead = new AtomicLong();
      example_interfaces.Bool reusable = new example_interfaces.Bool();

      ROS2Subscription<example_interfaces.Bool> subscription = node.createSubscription(topic, reader ->
      {
         if (!measuring.get())
         {
            reader.read(reusable);
            return;
         }

         long threadId = Thread.currentThread().getId();
         long before = threadMXBean.getThreadAllocatedBytes(threadId);
         boolean ok = reader.read(reusable);
         long after = threadMXBean.getThreadAllocatedBytes(threadId);
         if (ok)
         {
            allocatedDuringRead.addAndGet(after - before);
            measuredReads.incrementAndGet();
         }
      }, qos);

      ROS2Publisher<example_interfaces.Bool> publisher = node.createPublisher(topic, qos);
      assertTrue(publisher.waitForSubscription(5_000));
      assertTrue(subscription.waitForPublisher(5_000));

      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));

      example_interfaces.Bool message = new example_interfaces.Bool();
      message.setData(true);

      for (int i = 0; i < WARMUP_MESSAGES; ++i)
      {
         publisher.publish(message);
         if ((i & 63) == 0)
            LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(200));
      }
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(500));

      measuring.set(true);
      for (int i = 0; i < MEASURED_MESSAGES; ++i)
      {
         publisher.publish(message);
         if ((i & 63) == 0)
            LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(200));
      }

      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
      while (measuredReads.get() < MEASURED_MESSAGES / 2 && System.nanoTime() < deadline)
      {
         LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
      }

      assertTrue(measuredReads.get() >= MEASURED_MESSAGES / 2,
                 "Not enough measured reads: " + measuredReads.get());
      assertTrue(allocatedDuringRead.get() <= MAX_TOTAL_ALLOCATED_BYTES,
                 "Subscription read allocated " + allocatedDuringRead.get() + " bytes over " + measuredReads.get() + " reads");

      node.close();
   }

   @Test
   public void testFixedSizeCDRSerializeDeserializeIsAllocationFree()
   {
      ThreadMXBean threadMXBean = requireThreadAllocationBean();

      example_interfaces.Bool message = new example_interfaces.Bool();
      example_interfaces.Bool decoded = new example_interfaces.Bool();
      message.setData(true);

      CDRBuffer buffer = new CDRBuffer();
      int payloadSizeBytes = CDRBuffer.PAYLOAD_HEADER.length + message.calculateSizeBytes(0);
      buffer.ensureRemainingCapacity(payloadSizeBytes);

      for (int i = 0; i < WARMUP_MESSAGES; ++i)
      {
         roundTripSerialize(message, decoded, buffer);
      }

      assertSteadyStateAllocationFree(threadMXBean, MEASURED_MESSAGES, () -> roundTripSerialize(message, decoded, buffer));
   }

   private static void roundTripSerialize(example_interfaces.Bool message, example_interfaces.Bool decoded, CDRBuffer buffer)
   {
      buffer.rewind();
      buffer.writePayloadHeader();
      message.serialize(buffer);
      buffer.rewind();
      buffer.readPayloadHeader();
      decoded.deserialize(buffer);
   }

   private static ThreadMXBean requireThreadAllocationBean()
   {
      ThreadMXBean threadMXBean = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
      Assumptions.assumeTrue(threadMXBean != null && threadMXBean.isThreadAllocatedMemorySupported(),
                             "Thread allocation measurement requires HotSpot ThreadMXBean support");
      return threadMXBean;
   }

   private static Thread findThreadByName(String name)
   {
      for (Thread thread : Thread.getAllStackTraces().keySet())
      {
         if (name.equals(thread.getName()))
         {
            return thread;
         }
      }
      throw new IllegalStateException("Thread not found: " + name);
   }

   private static void runConcurrentPublishes(ROS2Publisher<example_interfaces.Bool>[] publishers,
                                              example_interfaces.Bool message,
                                              int messagesPerPublisher)
         throws InterruptedException
   {
      Thread[] threads = new Thread[publishers.length];
      for (int i = 0; i < publishers.length; ++i)
      {
         ROS2Publisher<example_interfaces.Bool> publisher = publishers[i];
         threads[i] = new Thread(() ->
         {
            for (int j = 0; j < messagesPerPublisher; ++j)
            {
               publisher.publish(message);
            }
         }, "alloc-burst-publisher-" + i);
      }
      for (Thread thread : threads)
      {
         thread.start();
      }
      for (Thread thread : threads)
      {
         thread.join();
      }
   }

   private static void assertSteadyStateAllocationFree(ThreadMXBean threadMXBean, int measuredCount, Runnable action)
   {
      long threadId = Thread.currentThread().getId();

      long shortLoopBefore = threadMXBean.getThreadAllocatedBytes(threadId);
      for (int i = 0; i < 1_000; ++i)
      {
         action.run();
      }
      long shortLoopAllocated = threadMXBean.getThreadAllocatedBytes(threadId) - shortLoopBefore;

      long longLoopBefore = threadMXBean.getThreadAllocatedBytes(threadId);
      for (int i = 0; i < measuredCount; ++i)
      {
         action.run();
      }
      long longLoopAllocated = threadMXBean.getThreadAllocatedBytes(threadId) - longLoopBefore;

      assertTrue(shortLoopAllocated <= MAX_TOTAL_ALLOCATED_BYTES,
                 "Short loop allocated " + shortLoopAllocated + " bytes");
      assertTrue(longLoopAllocated <= MAX_TOTAL_ALLOCATED_BYTES,
                 "Long loop allocated " + longLoopAllocated + " bytes");
      // Must not grow roughly linearly with iteration count.
      assertTrue(longLoopAllocated <= Math.max(shortLoopAllocated * 2, MAX_TOTAL_ALLOCATED_BYTES),
                 "Allocation scaled with message count: short=" + shortLoopAllocated + ", long=" + longLoopAllocated);
   }
}
