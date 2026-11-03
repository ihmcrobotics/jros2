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
package us.ihmc.jros2.allocation.messages;

import us.ihmc.jros2.allocation.ClassFileStrings;

import java.lang.management.ManagementFactory;

/**
 * Zero-tolerance allocation gate on HotSpot's allocated-bytes counter of the calling thread
 * ({@code com.sun.management.ThreadMXBean#getCurrentThreadAllocatedBytes}), exact to the byte. It sees every heap
 * allocation of the thread, including those that bytecode instrumentation cannot see: objects created by the JVM itself
 * (string literals resolved when a method is first compiled, class loading, JNI) and objects re-materialized on
 * deoptimization. Escape analysis can only make it see less than the bytecode allocates, so it complements
 * {@link SingleThreadAllocationGate} rather than replacing it.
 * <p>
 * It is meant for a JVM without the java-allocation-instrumenter agent (see {@link HotSpotAllocationProbe}): the agent
 * adds a string literal (the allocated type) at every allocation site of every class it instruments, and HotSpot creates
 * those literals, on the running thread, when it first compiles a method of the class with C2, so in the agent's JVM this
 * counter also counts the instrumentation. Like the other gate, this class has no string literal (see
 * {@link ClassFileStrings}).
 * </p>
 */
public final class ThreadAllocatedBytesGate implements CallingThreadAllocationGate
{
   private static final com.sun.management.ThreadMXBean THREADS = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
   /** Calls of the counter per warm-up batch: above the C2 compile thresholds (10000 without tiered compilation). */
   private static final int WARM_UP_BATCH = 20_000;
   private static final int WARM_UP_CLEAN_BATCHES = 3;
   private static final int WARM_UP_MAX_BATCHES = 50;
   private static boolean warmedUp;

   private long heapBytesAtBegin;
   private long heapBytes;

   public ThreadAllocatedBytesGate()
   {
      SingleThreadAllocationGate.Text.assertThreadAllocatedBytesMeasurable(THREADS);
      warmUpCounter();
      heapBytesAtBegin = THREADS.getCurrentThreadAllocatedBytes();
      heapBytes = THREADS.getCurrentThreadAllocatedBytes() - heapBytesAtBegin;
   }

   /**
    * Calls the counter until it is compiled and reading it allocates nothing. The counter is itself code that runs in the
    * measured thread at both ends of a window: when a compilation of it is requested there (for example C2 creating the
    * wrapper of the native method sun.management.ThreadImpl.getThreadAllocatedMemory0 with -XX:-TieredCompilation, which
    * first resolves the string literals of ThreadImpl: 992 bytes on the requesting thread), the gate would count the
    * measuring tool. Fails if reading the counter does not become allocation free.
    */
   private static synchronized void warmUpCounter()
   {
      if (warmedUp)
         return;
      int cleanBatches = 0;
      for (int batch = 0; batch < WARM_UP_MAX_BATCHES && cleanBatches < WARM_UP_CLEAN_BATCHES; batch++)
      {
         long before = THREADS.getCurrentThreadAllocatedBytes();
         long sum = 0;
         for (int i = 0; i < WARM_UP_BATCH; i++)
            sum += THREADS.getCurrentThreadAllocatedBytes();
         long allocated = THREADS.getCurrentThreadAllocatedBytes() - before;
         cleanBatches = allocated == 0 && sum != 0 ? cleanBatches + 1 : 0;
      }
      if (cleanBatches < WARM_UP_CLEAN_BATCHES)
         throw new AssertionError(SingleThreadAllocationGate.Text.counterNotAllocationFree());
      warmedUp = true;
   }

   @Override
   public void assertNoAllocation(String what, Runnable body)
   {
      countAllocations(body);
      if (heapBytes != 0)
         throw new AssertionError(SingleThreadAllocationGate.Text.heapReport(what, heapBytes));
   }

   @Override
   public long countAllocations(Runnable body)
   {
      heapBytesAtBegin = THREADS.getCurrentThreadAllocatedBytes();
      try
      {
         body.run();
      }
      finally
      {
         heapBytes = THREADS.getCurrentThreadAllocatedBytes() - heapBytesAtBegin;
      }
      return heapBytes;
   }

   public long getHeapBytes()
   {
      return heapBytes;
   }

   @Override
   public void close()
   {
   }
}
