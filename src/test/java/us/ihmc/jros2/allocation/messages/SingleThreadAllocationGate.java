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

import com.google.monitoring.runtime.instrumentation.AllocationRecorder;
import com.google.monitoring.runtime.instrumentation.Sampler;

import us.ihmc.jros2.allocation.ClassFileStrings;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Zero-tolerance allocation gate for code that runs on the calling thread (message and CDR code is single threaded),
 * on the java-allocation-instrumenter agent (the {@code allocationTest} task).
 * <p>
 * Every {@code new}, array creation and reflective/native-method allocation done by bytecode, JDK classes included, is
 * reported synchronously on the allocating thread, before and independent of escape analysis or JIT state (an
 * instrumented allocation escapes into the recorder, so it is never scalar-replaced). Only allocations of the measured
 * thread between {@link #begin()} and {@link #end()} are counted; the first ones are kept with their stack traces.
 * </p>
 * <p>
 * Not reported by the agent: allocations in hidden classes, which it cannot transform (the instance of a capturing lambda
 * or method reference, created by a class that LambdaMetafactory generates), arrays created by
 * {@code Unsafe.allocateUninitializedArray} (e.g. the {@code byte[]} of a string concatenation; its String is reported),
 * and objects created by the JVM itself or by JNI. A capturing lambda added to CDRBuffer.writeString or to the template's
 * set() passes this gate; {@link HotSpotAllocationTest} catches it in every one of its agent-free probe JVMs, including the
 * ones without escape analysis and with C1 only ({@link HotSpotCounterSelfTest} checks that its counter sees these
 * allocations).
 * </p>
 * <p>
 * HotSpot's allocated-bytes counter of the thread is measured too and reported, but it is not a pass criterion in this
 * JVM: the agent adds a string literal (the allocated type) at every allocation site it instruments, and HotSpot creates
 * the not yet resolved literals of a class, on the running thread, when it first requests a C2 compilation of one of its
 * methods, so here the counter also counts the instrumentation. {@link HotSpotAllocationTest} runs the same gates with
 * that counter as the criterion ({@link ThreadAllocatedBytesGate}) in a JVM without the agent.
 * </p>
 * <p>
 * The code that runs in a measured region follows the rule it checks: this class has no string literal (they are in
 * {@link Text}), because HotSpot creates the unresolved string literals of a class on the thread that first requests a
 * C2 compilation of one of its methods (see {@link ClassFileStrings}); {@link StringConstantsAllocationTest} checks it.
 * </p>
 */
public final class SingleThreadAllocationGate implements CallingThreadAllocationGate, AutoCloseable
{
   private static final int MAX_RECORDED = 8;
   private static final com.sun.management.ThreadMXBean THREADS = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

   private volatile Thread measuredThread;
   private long count;
   private long bytes;
   private long heapBytesAtBegin;
   private long heapBytes;
   private final List<String> recorded = new ArrayList<>();
   private final Sampler sampler = this::sample;

   public SingleThreadAllocationGate()
   {
      Text.assertAgentLoaded();
      Text.assertThreadAllocatedBytesMeasurable(THREADS);
      // First calls before any measurement (class initialization, linkage)
      heapBytesAtBegin = THREADS.getCurrentThreadAllocatedBytes();
      heapBytes = THREADS.getCurrentThreadAllocatedBytes() - heapBytesAtBegin;
      AllocationRecorder.addSampler(sampler);
   }

   public static void assertAgentLoaded()
   {
      Text.assertAgentLoaded();
   }

   private void sample(int arrayLength, String description, Object newObject, long size)
   {
      if (Thread.currentThread() != measuredThread)
         return;

      // Allocations made here are not reported back to this sampler (the recorder has a per-thread recursion guard).
      ++count;
      bytes += size;
      if (recorded.size() < MAX_RECORDED)
         recorded.add(Text.entry(arrayLength, description, size));
   }

   /** Starts counting the allocations of the calling thread. Allocates nothing. */
   public void begin()
   {
      count = 0;
      bytes = 0;
      recorded.clear();
      measuredThread = Thread.currentThread();
      heapBytesAtBegin = THREADS.getCurrentThreadAllocatedBytes();
   }

   /** Stops counting. Allocates nothing. */
   public long end()
   {
      long heapBytesAtEnd = THREADS.getCurrentThreadAllocatedBytes();
      measuredThread = null;
      heapBytes = heapBytesAtEnd - heapBytesAtBegin;
      return count;
   }

   /** @return the bytes HotSpot counted for the measured thread between {@link #begin()} and {@link #end()} */
   public long getHeapBytes()
   {
      return heapBytes;
   }

   /** @return true if the instrumentation saw no allocation in the last measurement */
   public boolean isAllocationFree()
   {
      return count == 0;
   }

   public long getCount()
   {
      return count;
   }

   public String report(String what)
   {
      return Text.report(what, count, bytes, heapBytes, recorded);
   }

   /**
    * Runs {@code body} (created by the caller before this call) and fails if it allocated anything on this thread.
    */
   @Override
   public void assertNoAllocation(String what, Runnable body)
   {
      begin();
      try
      {
         body.run();
      }
      finally
      {
         end();
      }
      if (!isAllocationFree())
         fail(report(what));
   }

   /**
    * Runs {@code body} and returns the number of allocations it made on this thread (for paths documented as allocating).
    */
   @Override
   public long countAllocations(Runnable body)
   {
      begin();
      try
      {
         body.run();
      }
      finally
      {
         end();
      }
      return count;
   }

   @Override
   public void close()
   {
      measuredThread = null;
      AllocationRecorder.removeSampler(sampler);
   }

   /** Everything that needs string literals, out of the measured code. */
   static final class Text
   {
      private Text()
      {
      }

      static boolean isAgentLoaded()
      {
         return ManagementFactory.getRuntimeMXBean()
                                 .getInputArguments()
                                 .stream()
                                 .anyMatch(argument -> argument.startsWith("-javaagent:") && argument.contains("java-allocation-instrumenter"));
      }

      static void assertAgentLoaded()
      {
         assertTrue(isAgentLoaded(), "allocation tests must run in the allocationTest task (java-allocation-instrumenter agent)");
      }

      static void assertThreadAllocatedBytesMeasurable(com.sun.management.ThreadMXBean threads)
      {
         assertTrue(threads.isThreadAllocatedMemorySupported() && threads.isThreadAllocatedMemoryEnabled(), "per-thread allocated bytes must be measurable");
      }

      static String entry(int arrayLength, String description, long size)
      {
         StringBuilder entry = new StringBuilder();
         entry.append(description);
         if (arrayLength >= 0)
            entry.append('[').append(arrayLength).append(']');
         entry.append(" (").append(size).append(" bytes)");
         StackTraceElement[] stack = new Throwable().getStackTrace();
         int printed = 0;
         for (StackTraceElement element : stack)
         {
            String className = element.getClassName();
            if (className.startsWith("com.google.monitoring.runtime.instrumentation") || className.startsWith(SingleThreadAllocationGate.class.getName()))
               continue;
            entry.append("\n      at ").append(element);
            if (++printed == 14)
               break;
         }
         return entry.toString();
      }

      static String counterNotAllocationFree()
      {
         return "reading HotSpot's allocated-bytes counter of the thread still allocates after warm-up";
      }

      static String heapReport(String what, long heapBytes)
      {
         return what + ": " + heapBytes + " bytes allocated by the thread (HotSpot counter), in steady state";
      }

      static String report(String what, long count, long bytes, long heapBytes, List<String> recorded)
      {
         StringBuilder report = new StringBuilder();
         report.append(what).append(": ").append(count).append(" instrumented allocation(s) of ").append(bytes).append(" bytes, ").append(heapBytes)
               .append(" bytes counted by HotSpot for the thread, in steady state");
         for (String entry : recorded)
            report.append("\n   ").append(entry);
         return report.toString();
      }
   }
}
