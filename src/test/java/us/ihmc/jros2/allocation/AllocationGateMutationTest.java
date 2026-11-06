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
package us.ihmc.jros2.allocation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.natives.fastddsjava;
import us.ihmc.jros2.ROS2QoSProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation checks of {@link AllocationGate}: an allocation injected in each place the gate must cover makes it fail, and
 * an allocation-free body passes (no false positive from the gate's own machinery). Runs in both gate JVMs
 * (allocationTest: agent; allocationTestNoEA: no agent, escape analysis off) and checks the passes each JVM runs.
 */
@Tag("allocation")
public class AllocationGateMutationTest
{
   private static final int ITERATIONS = 1_000;

   private static volatile Object sink;
   private static volatile long longSink;

   private static final class Point
   {
      final double x, y;

      Point(double x, double y)
      {
         this.x = x;
         this.y = y;
      }
   }

   /** Allocates a Point that never escapes: with escape analysis on, C2 scalar-replaces it. */
   private static double nonEscaping(int i)
   {
      Point p = new Point(i, i + 1);
      return p.x + p.y;
   }

   /** Holds a string constant that no code executes. */
   private static final class StringConstantFixture
   {
      static String text()
      {
         return "jros2 allocation gate fixture constant, never executed";
      }
   }

   private static AllocationGate.Builder gate(String name)
   {
      return AllocationGate.named(name).warmup(20_000).iterations(ITERATIONS);
   }

   private static boolean instrumented()
   {
      return AllocationGate.mode(false) == AllocationGate.Mode.INSTRUMENTED;
   }

   private static long threadBytes(AllocationGate.Result result, String threadName)
   {
      long bytes = 0;
      for (AllocationGate.ThreadBytes thread : result.threadBytesPass.threads)
      {
         if (thread.name.equals(threadName))
            bytes += thread.bytes;
      }
      return bytes;
   }

   @Test
   public void testGateModeIsStrict()
   {
      AllocationGate.Mode mode = AllocationGate.mode(false);
      if (mode == AllocationGate.Mode.NO_ESCAPE_ANALYSIS)
      {
         assertFalse(AllocationGate.isEscapeAnalysisEnabled(), "noEA mode must run with -XX:-DoEscapeAnalysis");
      }
      else
      {
         assertEquals(AllocationGate.Mode.INSTRUMENTED, mode);
         assertTrue(AllocationGate.isAgentLoaded());
      }
   }

   /** The gate's own machinery (loop, observer handshake, sampler, settle wait) does not allocate on a hot-path thread. */
   @Test
   public void testAllocationFreeBodyPasses()
   {
      long[] values = new long[64];
      AtomicInteger settled = new AtomicInteger();
      // Lambdas that capture state allocate when the capturing expression runs: create them outside the window.
      BooleanSupplier isSettled = () -> settled.get() > 0;
      AllocationGate.Result result = gate("clean body").settle(() ->
                                                        {
                                                           settled.incrementAndGet();
                                                           SpinWait.await(isSettled, 1_000, "settled");
                                                        })
                                                       .assertAllocationFree(i -> values[i & 63] += i);
      assertTrue(result.hotPathThreadNames().contains(Thread.currentThread().getName()), "the caller thread is measured");
      if (instrumented())
         assertEquals(0, result.instrumenterPass.count);
   }

   /** An escaping allocation on the caller thread is caught by every pass, with its stack. */
   @Test
   public void testCallerAllocationIsCaught()
   {
      AllocationGate.Result result = gate("caller allocation").run(i -> sink = new byte[16]);
      assertFalse(result.isAllocationFree());
      assertTrue(threadBytes(result, Thread.currentThread().getName()) >= ITERATIONS * 16L, result.report());
      if (instrumented())
      {
         assertEquals(ITERATIONS, result.instrumenterPass.count, result.report());
         assertNotNull(result.capturePass);
         assertTrue(result.report().contains("AllocationGateMutationTest.lambda$testCallerAllocationIsCaught"), result.report());
      }
   }

   /**
    * An allocation that escape analysis removes is caught: by the agent (it makes the object escape) and, with escape
    * analysis disabled, by the allocated-bytes counters.
    */
   @Test
   public void testScalarReplaceableAllocationIsCaught()
   {
      AllocationGate.Result result = gate("non-escaping allocation").warmup(200_000).run(i -> longSink += (long) nonEscaping(i));
      assertFalse(result.isAllocationFree(), result.report());
      if (instrumented())
         assertEquals(ITERATIONS, result.instrumenterPass.count, result.report());
      else
         assertTrue(threadBytes(result, Thread.currentThread().getName()) >= ITERATIONS * 16L, result.report());
   }

   /**
    * A capturing lambda is instantiated by its hidden class, which the agent does not instrument (the JVM does not hand
    * hidden classes to class file transformers): only the allocated-bytes pass sees it, which is why that pass runs in
    * every mode.
    */
   @Test
   public void testCapturingLambdaIsCaught()
   {
      long[] captured = new long[1];
      AllocationGate.Result result = gate("capturing lambda").run(i -> sink = (Runnable) () -> captured[0] += i);
      assertFalse(result.isAllocationFree(), result.report());
      assertTrue(threadBytes(result, Thread.currentThread().getName()) >= ITERATIONS * 16L, result.report());
   }

   /** String concatenation (invokedynamic, StringConcatFactory) allocates the result through JDK internals: caught. */
   @Test
   public void testStringConcatenationIsCaught()
   {
      AllocationGate.Result result = gate("string concatenation").run(i -> sink = "value " + i);
      assertFalse(result.isAllocationFree(), result.report());
      assertTrue(threadBytes(result, Thread.currentThread().getName()) >= ITERATIONS * 24L, result.report());
   }

   /** Autoboxing outside the Integer cache allocates inside a JDK class: caught by every pass. */
   @Test
   public void testBoxingIsCaught()
   {
      AllocationGate.Result result = gate("boxing").run(i -> sink = Integer.valueOf(1_000_000 + i));
      assertFalse(result.isAllocationFree(), result.report());
      assertTrue(threadBytes(result, Thread.currentThread().getName()) >= ITERATIONS * 16L, result.report());
      if (instrumented())
         assertEquals(ITERATIONS, result.instrumenterPass.count, result.report());
   }

   /**
    * The diagnostic JFR pass names the source of an allocation the instrumenter cannot see (here a capturing lambda). With
    * TLABs on it only sees the allocations that start a new TLAB, so the body allocates enough to take many.
    */
   @Test
   public void testJfrPassNamesTheSourceOfHiddenAllocations() throws Exception
   {
      long[] captured = new long[1];
      AllocationGate.Builder builder = gate("JFR pass").iterations(200_000);
      AllocationGate.JfrPass pass = AllocationGate.JfrPass.record(builder, i -> sink = (Runnable) () -> captured[0] += i);
      assertTrue(pass.events > 0, "no allocation event recorded");
      boolean named = false;
      for (String group : pass.groups)
         named |= group.contains("AllocationGateMutationTest.lambda$testJfrPassNamesTheSourceOfHiddenAllocations");
      assertTrue(named, String.join("\n", pass.groups));
   }

   /**
    * A contended {@code synchronized} block allocates no Java object, but HotSpot inflates the monitor (a native
    * {@code malloc} of an {@code ObjectMonitor}): the monitor pass catches it.
    */
   @Test
   public void testContendedMonitorIsCaught() throws InterruptedException
   {
      Object monitor = new Object();
      AtomicInteger stop = new AtomicInteger();
      Thread holder = new Thread(() ->
      {
         while (stop.get() == 0)
         {
            synchronized (monitor)
            {
               long until = System.nanoTime() + 20_000;
               while (System.nanoTime() < until)
                  Thread.onSpinWait();
            }
            Thread.onSpinWait();
         }
      }, "monitor-holder");
      holder.setDaemon(true);
      holder.start();
      long[] counter = new long[1];
      try
      {
         AllocationGate.Result result = gate("contended monitor").warmup(100).run(i ->
                                                                              {
                                                                                 synchronized (monitor)
                                                                                 {
                                                                                    counter[0]++;
                                                                                 }
                                                                              });
         assertFalse(result.isAllocationFree(), result.report());
         assertTrue(result.monitorPass.events > 0, result.report());
         assertTrue(result.report().contains("AllocationGateMutationTest.lambda$testContendedMonitorIsCaught"), result.report());
      }
      finally
      {
         stop.set(1);
         holder.join(5_000);
      }
   }

   /** An allocation on another (non-infrastructure) thread is caught and attributed to that thread. */
   @Test
   public void testAllocationOnAnotherThreadIsCaught() throws InterruptedException
   {
      AtomicInteger requested = new AtomicInteger();
      AtomicInteger done = new AtomicInteger();
      Thread helper = new Thread(() ->
      {
         int handled = 0;
         while (!Thread.currentThread().isInterrupted())
         {
            if (requested.get() != handled)
            {
               handled++;
               sink = new Object();
               done.incrementAndGet();
            }
            else
            {
               LockSupport.parkNanos(10_000);
            }
         }
      }, "fastdds-callback-mutation");
      helper.setDaemon(true);
      helper.start();
      BooleanSupplier helperDone = () -> done.get() == requested.get();
      try
      {
         // Warm-up past the C2 thresholds: the first C2 request for the body resolves this class's string constants on the caller.
         AllocationGate.Result result = gate("other thread allocation").warmup(5_000).run(i ->
                                                                                     {
                                                                                        requested.incrementAndGet();
                                                                                        SpinWait.await(helperDone, 5_000, "helper");
                                                                                     });
         assertFalse(result.isAllocationFree());
         assertTrue(threadBytes(result, "fastdds-callback-mutation") >= ITERATIONS * 16L, result.report());
         assertEquals(0, threadBytes(result, Thread.currentThread().getName()), "the caller only waited: " + result.report());
         if (instrumented())
         {
            assertEquals(ITERATIONS, result.instrumenterPass.count, result.report());
            assertTrue(result.instrumenterPass.summary.containsKey("fastdds-callback-mutation :: java/lang/Object"), result.report());
         }
      }
      finally
      {
         helper.interrupt();
         helper.join(5_000);
      }
   }

   /**
    * An object created by JNI code ({@code NewStringUTF}) is invisible to the instrumenter but caught by the allocated-bytes
    * pass.
    */
   @Test
   public void testJniAllocationIsCaught()
   {
      assertTrue(fastddsjavaNativeLibrary.load());
      long wrapperType = fastddsjava.createTopicDataWrapperType("jros2_gate::msg::dds_::Mutation_", (short) 1);
      assertTrue(wrapperType != 0);
      AllocationGate.Result result = gate("JNI NewStringUTF").warmup(1_000).run(i -> sink = fastddsjava.topicDataWrapperTypeGetName(wrapperType));
      assertFalse(result.isAllocationFree());
      assertTrue(threadBytes(result, Thread.currentThread().getName()) >= ITERATIONS * 24L, result.report());
      if (instrumented())
         assertEquals(0, result.instrumenterPass.count, "the agent cannot see JNI allocations, which is why the bytes pass exists");
   }

   /** A thread that starts and ends inside the window is a violation even though its own counter is lost. */
   @Test
   public void testThreadChurnIsCaught()
   {
      AllocationGate.Result result = gate("thread churn").warmup(0).iterations(1).run(i ->
                                                                                     {
                                                                                        Thread thread = new Thread(() -> sink = new Object(), "short-lived");
                                                                                        thread.start();
                                                                                        thread.join();
                                                                                     });
      assertFalse(result.isAllocationFree());
      assertEquals(1, result.threadBytesPass.hiddenThreadStarts, result.report());
      assertTrue(threadBytes(result, Thread.currentThread().getName()) > 0, "creating the thread allocated on the caller: " + result.report());
   }

   /** A thread that is started during the window and still runs at its end is reported as started. */
   @Test
   public void testThreadStartedInWindowIsCaught() throws InterruptedException
   {
      // One thread per window (every pass runs the body once); all of them run until the end of the test.
      List<Thread> started = new ArrayList<>();
      AtomicInteger stop = new AtomicInteger();
      AllocationGate.Result result = gate("thread start").warmup(0).iterations(1).gcBeforeWindow(false).run(i ->
      {
         Thread thread = new Thread(() ->
         {
            while (stop.get() == 0)
               LockSupport.parkNanos(1_000_000);
         }, "started-in-window");
         thread.setDaemon(true);
         thread.start();
         started.add(thread);
      });
      stop.set(1);
      for (Thread thread : started)
         thread.join(5_000);
      boolean reported = false;
      for (AllocationGate.ThreadBytes thread : result.threadBytesPass.threads)
         reported |= "started-in-window".equals(thread.name) && "started".equals(thread.churn) && thread.isViolation();
      assertTrue(reported, result.report());
      assertFalse(result.isAllocationFree());
   }

   /** Without the agent and with escape analysis on, the gate refuses to run unless a quick check is requested. */
   @Test
   public void testGateRefusesAnUnsoundJvm()
   {
      String previous = System.getProperty(AllocationGate.MODE_PROPERTY);
      try
      {
         System.setProperty(AllocationGate.MODE_PROPERTY, "bogus");
         assertThrows(IllegalStateException.class, () -> AllocationGate.mode(false));
         if (!AllocationGate.isAgentLoaded())
         {
            System.setProperty(AllocationGate.MODE_PROPERTY, "instrumented");
            assertThrows(IllegalStateException.class, () -> AllocationGate.mode(false));
         }
         if (AllocationGate.isEscapeAnalysisEnabled())
         {
            System.setProperty(AllocationGate.MODE_PROPERTY, "noEA");
            assertThrows(IllegalStateException.class, () -> AllocationGate.mode(false));
         }
      }
      finally
      {
         if (previous == null)
            System.clearProperty(AllocationGate.MODE_PROPERTY);
         else
            System.setProperty(AllocationGate.MODE_PROPERTY, previous);
      }
   }

   /** The allowlist is explicit: library and unknown thread names are hot-path threads. */
   @Test
   public void testThreadClassification()
   {
      String[] hotPath = {"Test worker", "main", "fastdds-callback", "AsyncROS2NodePublishThread-x", "jros2-deferred-teardown", "ROS2CallbackExecutor-1",
                          "pool-1-thread-1", "Thread-0", "", "Reference Handler 2", "C3 CompilerThread0", "/127.0.0.1:1 workers"};
      for (String name : hotPath)
         assertFalse(AllocationGate.isInfrastructureThread(name), name);
      String[] infrastructure = {"Reference Handler", "Finalizer", "Common-Cleaner", "Signal Dispatcher", "Notification Thread",
                                 "/127.0.0.1:40623 to /127.0.0.1:37105 workers Thread 2", AllocationGate.OBSERVER_THREAD_NAME, "JFR Periodic Tasks"};
      for (String name : infrastructure)
         assertTrue(AllocationGate.isInfrastructureThread(name), name);
   }

   /**
    * Before the warm-up the gate creates the string constants of the test class that declares the body and of its nested
    * classes (so that the JIT cannot create them inside a window, whatever its configuration), and never those of a
    * library class: a library class with string constants on the hot path must stay visible to the gates.
    */
   @Test
   public void testStringConstantsOfTestClassesAreCreatedBeforeTheWarmup()
   {
      AllocationGate.Body body = i -> longSink += i;
      ROS2QoSProfile libraryObject = new ROS2QoSProfile();
      AllocationGate.createTestClassStringConstants(true, body, libraryObject);
      // String.intern() returns its argument only if the string table did not have that text yet.
      String fixture = new StringBuilder("jros2 allocation gate fixture").append(" constant, never executed").toString();
      assertTrue(fixture.intern() != fixture, "the constant of a nested class of the test class was created");
      String library = new StringBuilder("ROS2QoSProfile presets are immutable;").append(" use new ROS2QoSProfile(preset) to create a modifiable copy")
                                                                             .toString();
      assertTrue(library.intern() == library, "the constant of a library class was left alone");
   }

   /**
    * A monitor that threads contend for only briefly: it is inflated during the warm-up, and in the window the threads
    * acquire it by spinning, which posts no event. The gate JVMs deflate idle monitors every millisecond
    * (AllocationGate.MONITOR_DEFLATION_OPTIONS), so the monitor is inflated (malloc'd) again inside the window and caught.
    */
   @Test
   public void testBrieflyContendedMonitorIsCaught() throws InterruptedException
   {
      assertTrue(AllocationGate.isMonitorDeflationFrequent(), "the gate JVM deflates idle monitors every millisecond");
      Object monitor = new Object();
      AtomicInteger stop = new AtomicInteger();
      long[] counter = new long[1];
      Thread contender = new Thread(() ->
      {
         while (stop.get() == 0)
         {
            synchronized (monitor)
            {
               counter[0]++;
            }
            for (int k = 0; k < 20; ++k)
               Thread.onSpinWait();
         }
      }, "monitor-contender");
      contender.setDaemon(true);
      contender.start();
      try
      {
         // A long window (about 100 ms): idle monitors are deflated every millisecond
         AllocationGate.Result result = gate("briefly contended monitor").iterations(200_000).run(i ->
                                                                         {
                                                                            synchronized (monitor)
                                                                            {
                                                                               counter[0]++;
                                                                            }
                                                                            for (int k = 0; k < 20; ++k)
                                                                               Thread.onSpinWait();
                                                                         });
         assertFalse(result.isAllocationFree(), result.report());
         assertTrue(result.monitorPass.events > 0, result.report());
      }
      finally
      {
         stop.set(1);
         contender.join(5_000);
      }
   }
}
