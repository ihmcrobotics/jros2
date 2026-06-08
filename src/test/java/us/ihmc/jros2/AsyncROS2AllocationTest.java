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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AsyncROS2AllocationTest
{
   private static final int WARMUP_MESSAGES = 2_000;
   private static final int MEASURED_MESSAGES = 10_000;

   @Test
   public void testAsyncPublishCallerThreadIsAllocationFree() throws InterruptedException
   {
      ThreadMXBean threadMXBean = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
      Assumptions.assumeTrue(threadMXBean != null && threadMXBean.isThreadAllocatedMemorySupported(),
                             "Thread allocation measurement requires HotSpot ThreadMXBean support");

      AsyncROS2Node asyncNode = new AsyncROS2Node("async_alloc_test");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/async_alloc_test", example_interfaces.Bool.class);
      ROS2Publisher<example_interfaces.Bool> publisher = asyncNode.createPublisher(topic);

      example_interfaces.Bool message = new example_interfaces.Bool();
      message.setData(true);

      for (int i = 0; i < WARMUP_MESSAGES; ++i)
      {
         publisher.publish(message);
      }

      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));

      long threadId = Thread.currentThread().getId();

      long shortLoopBefore = threadMXBean.getThreadAllocatedBytes(threadId);
      for (int i = 0; i < 1_000; ++i)
      {
         publisher.publish(message);
      }
      long shortLoopAllocated = threadMXBean.getThreadAllocatedBytes(threadId) - shortLoopBefore;

      long longLoopBefore = threadMXBean.getThreadAllocatedBytes(threadId);
      for (int i = 0; i < MEASURED_MESSAGES; ++i)
      {
         publisher.publish(message);
      }
      long longLoopAllocated = threadMXBean.getThreadAllocatedBytes(threadId) - longLoopBefore;

      assertEquals(shortLoopAllocated, longLoopAllocated, "Async publish allocation scaled with message count");

      asyncNode.close();
   }
}
