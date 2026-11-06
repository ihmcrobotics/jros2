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

import com.google.monitoring.runtime.instrumentation.AllocationRecorder;
import com.google.monitoring.runtime.instrumentation.Sampler;
import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.ThreadMXBean;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * Zero-tolerance steady-state allocation gate for every thread of the JVM.
 * <p>
 * A gate run executes a body {@code warmup} times, then measures {@code iterations} further executions (plus an optional
 * {@link Builder#settle settle} step that waits, allocation-free, until asynchronous work such as sample delivery has
 * finished) in up to three passes:
 * <ol>
 * <li><b>Instrumenter pass</b> (only when the java-allocation-instrumenter agent is loaded): a {@link Sampler} counts every
 * allocation the agent reports, on every thread, while the window is open. The counting sampler itself never allocates:
 * it keeps references and primitives in preallocated arrays. This pass is independent of escape analysis and of the JIT
 * state, because the agent passes every new object to the sampler (it escapes).</li>
 * <li><b>Capture pass</b> (only if the instrumenter pass found a violation): the same window again, with a sampler that
 * records the full stack of the first {@value #MAX_STACKS} violations. Recording allocates (inside the agent's recursion
 * guard, so it is not reported), which is why it is a separate pass.</li>
 * <li><b>Thread-allocated-bytes pass</b>: HotSpot's per-thread allocated-bytes counters of ALL live threads are snapshot
 * before and after the window by a dedicated observer thread (taking the snapshot allocates, on the observer only, which
 * is excluded). This catches objects created by JNI code ({@code NewObject}, {@code NewStringUTF}, thrown exceptions,
 * attaching a thread) that the instrumenter cannot see. A thread that starts or ends during the window is a violation
 * too: creating a thread allocates, and the counter of a thread that ended is lost. Run in a JVM with
 * {@code -XX:-DoEscapeAnalysis} (the {@code allocationTestNoEA} task), escape analysis cannot hide an allocation from it
 * either.</li>
 * <li><b>Monitor pass</b> (on unless {@link Builder#monitorPass(boolean) disabled}): the same window again under a JFR
 * recording of the {@code jdk.JavaMonitorInflate} and {@code jdk.JavaMonitorEnter} events without threshold. A contended
 * {@code synchronized} block, {@code Object.wait()} and the like make HotSpot inflate the object's monitor, which
 * {@code malloc}s an {@code ObjectMonitor} (and the JVM deflates and frees idle monitors again, so this recurs): a native
 * allocation on a hot-path thread that neither counting pass can see. Every such event on a hot-path thread is a
 * violation. A monitor that was inflated before the window and stays inflated produces no event when threads contend for
 * it briefly (they spin until it is free), although the JVM deflates it again whenever it is idle (at least once a minute)
 * and the next contention mallocs anew; the gate JVMs therefore deflate idle monitors every millisecond
 * ({@link #MONITOR_DEFLATION_OPTIONS}), so that a contended monitor is inflated again, and seen, inside every window.</li>
 * <li><b>JFR pass</b> (only with {@value #DIAGNOSE_PROPERTY}{@code =true}, set by {@code -Pjros2.allocation.diagnose}):
 * the window of the thread-allocated-bytes pass runs under a JFR recording of the allocation events, with their stacks,
 * which is reported if that pass found a violation. With {@code -XX:-UseTLAB} (which the diagnose property adds) every
 * allocation is a {@code jdk.ObjectAllocationOutsideTLAB} event, so this names the source of the allocations that the
 * instrumenter cannot see, also of one that happens only once. Diagnostic only: it never decides the result.</li>
 * </ol>
 * <b>Why both counting passes are needed.</b> The instrumenter only sees allocations made by bytecode of classes it
 * could rewrite. It does not see objects created by JNI code, by JVM intrinsics and internals ({@code Unsafe}
 * allocations behind indy string concatenation), by hidden classes (the JVM does not hand them to class file
 * transformers, so a capturing lambda's instantiation is invisible), or by the JVM itself, for example the strings that
 * the class loader allocates the first time a class name is resolved through it (lazy constant pool resolution on the
 * first execution of a bytecode). The allocated-bytes counters see all of these; the mutation tests in
 * {@code AllocationGateMutationTest} prove each case.
 * <b>Thread classification.</b> Every thread counts as hot-path thread unless it is in {@link #isInfrastructureThread
 * the infrastructure allowlist}: JVM service threads and the Gradle test worker's messaging threads, which never run
 * library code, plus this gate's observer. Hot-path threads therefore include the test (caller) thread ("Test worker"),
 * every thread jros2 creates (async publish threads {@code AsyncROS2NodePublishThread-*}, {@code jros2-deferred-teardown},
 * {@code ROS2CallbackExecutor} workers), every Fast-DDS thread attached to the JVM through JNI ({@code fastdds-callback}),
 * test helper threads (producers, executors) and any thread nobody expected. Allocations on infrastructure threads are
 * still reported (not hidden), they just do not fail the gate.
 * <p>
 * <b>Modes</b> (system property {@value #MODE_PROPERTY}, set by the Gradle tasks): {@code instrumented} requires the agent
 * (allocationTest task); {@code noEA} requires {@code -XX:-DoEscapeAnalysis} (allocationTestNoEA task). Without the
 * property the gate picks {@code instrumented} if the agent is loaded and {@code noEA} if escape analysis is off; with
 * neither it refuses to run (a ThreadMXBean count with escape analysis on can be hidden by the JIT) unless the caller
 * explicitly asked for a {@link Builder#quick() quick} check, which is what the normal test suite uses.
 * <p>
 * The body, the settle step and anything they call must themselves be allocation-free; use
 * {@link SpinWait#await(BooleanSupplier, long, String)} to wait inside a window.
 * <p>
 * <b>JIT warm-up.</b> The warm-up must take every method on the measured path past the C2 compile thresholds (the
 * allocation test tasks run with {@code -Xbatch -XX:CompileThresholdScaling=0.1}: synchronous compilation at a tenth of the
 * usual invocation counts, so that the JIT reaches its steady state at a fixed point early in the warm-up). When HotSpot first queues a method of a class for C2 compilation it
 * resolves all string constants of that class on the requesting thread, a one-time allocation of a few hundred bytes to
 * a few kilobytes per class that would otherwise land in a window. (The string constants of the test classes that declare
 * the body and the steps are created before the warm-up, so their lambdas cannot trip over this under any JIT
 * configuration; library classes are left alone.) Likewise the first execution of a bytecode that
 * names a class the class loader has not resolved before, and the first call of each native method (JNI linking),
 * allocate once.
 */
public final class AllocationGate
{
   public static final String MODE_PROPERTY = "jros2.allocation.mode";
   public static final String REPORT_DIR_PROPERTY = "jros2.allocation.reportDir";
   public static final String OBSERVER_THREAD_NAME = "jros2-allocation-gate-observer";
   /** System property that enables the diagnostic JFR pass. */
   public static final String DIAGNOSE_PROPERTY = "jros2.allocation.diagnose";

   /** Number of violations kept (thread, type, size) per instrumenter pass. */
   static final int MAX_RECORDS = 256;
   /** Number of violations whose full stack is captured in the capture pass. */
   static final int MAX_STACKS = 16;

   private static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);

   static
   {
      // Run every branch of the waiting code once, so that the first spin or park inside a window does not resolve
      // constant pool entries (the class loader allocates the first time it resolves a class name).
      int[] calls = new int[1];
      SpinWait.await(() -> ++calls[0] > 2_500, 10_000, "gate warm-up");
   }

   public enum Mode
   {
      /** Agent loaded: instrumenter pass + thread-allocated-bytes pass. */
      INSTRUMENTED,
      /** Escape analysis disabled: thread-allocated-bytes pass. */
      NO_ESCAPE_ANALYSIS,
      /** Escape analysis on, no agent: thread-allocated-bytes pass only; only allowed for explicit quick checks. */
      QUICK
   }

   @FunctionalInterface
   public interface Body
   {
      void run(int iteration) throws Exception;
   }

   @FunctionalInterface
   public interface Step
   {
      void run() throws Exception;
   }

   /** The passes of a gate run, as passed to a {@link WindowListener}; PASS_WARMUP: once after the warm-up, unmeasured. */
   public static final int PASS_WARMUP = -1;
   public static final int PASS_INSTRUMENTER = 0;
   public static final int PASS_CAPTURE = 1;
   public static final int PASS_THREAD_BYTES = 2;
   public static final int PASS_MONITOR = 3;

   /**
    * Lets another measurement cover exactly the windows of this gate (e.g. a native heap counter): {@link #opened(int)}
    * and {@link #closed(int)} run inside every window, on the thread that runs the body, right before its first iteration
    * and right after the settle step, and must be allocation-free; {@link #finished(int)} runs after the window, outside
    * every measurement, and may allocate. Both run once more after the warm-up, unmeasured, with {@link #PASS_WARMUP}, so
    * that their own first execution (class loading, linking) happens before the first window.
    */
   public interface WindowListener
   {
      void opened(int pass);

      void closed(int pass);

      void finished(int pass) throws Exception;
   }

   private AllocationGate()
   {
   }

   public static Builder named(String name)
   {
      return new Builder(name);
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Thread classification
    * ------------------------------------------------------------------------------------------------------------ */

   /**
    * The explicit infrastructure allowlist. Each entry is a thread that never runs jros2 or Fast-DDS code:
    * <ul>
    * <li>"Reference Handler", "Finalizer", "Common-Cleaner": JDK reference processing after a GC (a GC is not an
    * allocation; the cleaners of buffers freed by an earlier GC run here).</li>
    * <li>"Signal Dispatcher", "Attach Listener", "Notification Thread", "Service Thread", "Monitor Deflation Thread",
    * compiler and sweeper threads: JVM service threads (most are hidden from Java anyway).</li>
    * <li>"/127.0.0.1:p1 to /127.0.0.1:p2 workers" and "... workers Thread N": the Gradle test worker's connection to the
    * Gradle daemon (test events and captured output).</li>
    * <li>{@value #OBSERVER_THREAD_NAME}: this gate's observer, which allocates to take the snapshots.</li>
    * <li>"JFR ..." (JFR Periodic Tasks, JFR Recorder Thread, ...): Java Flight Recorder, which only the monitor and JFR
    * passes of this gate start.</li>
    * </ul>
    */
   public static boolean isInfrastructureThread(String name)
   {
      if (name == null)
         return false;
      switch (name)
      {
         case "Reference Handler":
         case "Finalizer":
         case "Common-Cleaner":
         case "Signal Dispatcher":
         case "Attach Listener":
         case "Notification Thread":
         case "Service Thread":
         case "Monitor Deflation Thread":
         case "Sweeper thread":
         case OBSERVER_THREAD_NAME:
            return true;
         default:
            break;
      }
      if (name.startsWith("C1 CompilerThread") || name.startsWith("C2 CompilerThread") || name.startsWith("JFR "))
         return true;
      // Gradle worker messaging: "/127.0.0.1:40623 to /127.0.0.1:37105 workers" and "... workers Thread 2"
      return name.startsWith("/") && name.contains(" to /") && (name.endsWith(" workers") || name.contains(" workers Thread "));
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Mode
    * ------------------------------------------------------------------------------------------------------------ */

   public static boolean isAgentLoaded()
   {
      for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments())
      {
         if (argument.startsWith("-javaagent:") && argument.contains("java-allocation-instrumenter"))
            return true;
      }
      return false;
   }

   public static boolean isEscapeAnalysisEnabled()
   {
      HotSpotDiagnosticMXBean diagnostic = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
      return Boolean.parseBoolean(diagnostic.getVMOption("DoEscapeAnalysis").getValue());
   }

   /**
    * @param quickAllowed true if an EA-on JVM without agent may run a (weaker) quick check
    * @return the mode of this JVM
    * @throws IllegalStateException if the JVM cannot run a meaningful gate
    */
   public static Mode mode(boolean quickAllowed)
   {
      String requested = System.getProperty(MODE_PROPERTY);
      boolean agent = isAgentLoaded();
      boolean escapeAnalysis = isEscapeAnalysisEnabled();
      if ("instrumented".equals(requested))
      {
         if (!agent)
            throw new IllegalStateException(MODE_PROPERTY + "=instrumented but the java-allocation-instrumenter agent is not loaded");
         return Mode.INSTRUMENTED;
      }
      if ("noEA".equals(requested))
      {
         if (escapeAnalysis)
            throw new IllegalStateException(MODE_PROPERTY + "=noEA but escape analysis is enabled (-XX:-DoEscapeAnalysis missing)");
         return Mode.NO_ESCAPE_ANALYSIS;
      }
      if (requested != null)
         throw new IllegalStateException("Unknown " + MODE_PROPERTY + ": " + requested);
      if (agent)
         return Mode.INSTRUMENTED;
      if (!escapeAnalysis)
         return Mode.NO_ESCAPE_ANALYSIS;
      if (quickAllowed)
         return Mode.QUICK;
      throw new IllegalStateException("Allocation gate needs the java-allocation-instrumenter agent (:jros2-test:allocationTest) or "
                                      + "-XX:-DoEscapeAnalysis (:jros2-test:allocationTestNoEA); escape analysis could hide allocations otherwise");
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Allocation-free waiting
    * ------------------------------------------------------------------------------------------------------------ */

   /**
    * Same as {@link SpinWait#await(BooleanSupplier, long, String)}, which hot loops should call directly (see there).
    */
   public static void awaitCondition(BooleanSupplier condition, long timeoutMillis, String what)
   {
      SpinWait.await(condition, timeoutMillis, what);
   }

   static void timedOut(long timeoutMillis, String what)
   {
      throw new IllegalStateException("Timed out after " + timeoutMillis + " ms waiting for: " + what);
   }

   /* ------------------------------------------------------------------------------------------------------------
    * String constants of the test code
    * ------------------------------------------------------------------------------------------------------------ */

   /**
    * JVM options that make HotSpot deflate idle monitors every millisecond instead of at least once a minute, so that the
    * monitor pass sees every contended monitor inside its window (the allocation test tasks pass them). Required by the
    * monitor pass outside quick checks.
    */
   public static final String[] MONITOR_DEFLATION_OPTIONS = {"-XX:+UnlockDiagnosticVMOptions",
                                                             "-XX:GuaranteedSafepointInterval=1",
                                                             "-XX:GuaranteedAsyncDeflationInterval=1",
                                                             "-XX:AsyncDeflationInterval=1"};

   /** @return true if the JVM deflates idle monitors at least every 10 ms (see {@link #MONITOR_DEFLATION_OPTIONS}) */
   public static boolean isMonitorDeflationFrequent()
   {
      HotSpotDiagnosticMXBean diagnostic = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
      for (String option : new String[] {"GuaranteedSafepointInterval", "GuaranteedAsyncDeflationInterval"})
      {
         long value = Long.parseLong(diagnostic.getVMOption(option).getValue());
         if (value <= 0 || value > 10)
            return false;
      }
      return true;
   }

   /**
    * @throws IllegalStateException unless {@link #isMonitorDeflationFrequent()}. (Not in {@link Builder}, whose code runs
    *                               inside the windows and must have no string literals: see {@link SpinWait}.)
    */
   static void requireFrequentMonitorDeflation()
   {
      if (!isMonitorDeflationFrequent())
         throw new IllegalStateException("The monitor pass of the allocation gate needs the JVM options " + String.join(" ", MONITOR_DEFLATION_OPTIONS)
                                         + ": otherwise a monitor inflated before the window is not seen when threads contend for it");
   }

   /** @return true if the diagnostic JFR pass is enabled ({@value #DIAGNOSE_PROPERTY}) */
   static boolean isDiagnoseEnabled()
   {
      return Boolean.getBoolean(DIAGNOSE_PROPERTY);
   }

   /** JVM options that {@link #createTestClassStringConstants} needs (the allocation test tasks pass them). */
   public static final String[] CONSTANT_POOL_ACCESS_OPTIONS = {"--add-opens=java.base/java.lang=ALL-UNNAMED",
                                                                "--add-exports=java.base/jdk.internal.reflect=ALL-UNNAMED"};

   /**
    * Creates now, before the warm-up, every string constant of the test classes that declare the given gate functions
    * (body, settle and beforeWindow lambdas): of the outermost declaring class and of all its nested, local and anonymous
    * classes.
    * <p>
    * When HotSpot first queues a method of a class for C2 compilation, it creates all string constants of that class
    * that no executed {@code ldc} has created yet, on the requesting thread. Test classes have many (gate names, assertion
    * messages), and their lambdas and helper threads run inside the windows, so a window would fail whenever the JIT
    * happens to compile one of them there for the first time. With {@code -Xbatch -XX:CompileThresholdScaling=0.1} that
    * is during the warm-up, but with the default JIT thresholds it happens inside windows (seen: 512 bytes in the async
    * burst gate, from AsyncPublishAllocationTest's constants). This makes the gates independent of the JIT configuration.
    * <p>
    * Only classes loaded from the same location as this class (the test classes) are prepared, never library classes:
    * a string constant in a library class on the hot path is a real one-time allocation on the hot thread, which the
    * gates must keep seeing (and which HotPathClassesTest checks deterministically).
    *
    * @param required true to fail if the JVM does not grant access to the constant pools (see
    *                 {@link #CONSTANT_POOL_ACCESS_OPTIONS}); false to do nothing then (quick checks)
    */
   static void createTestClassStringConstants(boolean required, Object... functions)
   {
      Method getConstantPool;
      Method getSize;
      Method getTagAt;
      Method getStringAt;
      try
      {
         getConstantPool = Class.class.getDeclaredMethod("getConstantPool");
         getConstantPool.setAccessible(true);
         Class<?> constantPoolClass = getConstantPool.invoke(AllocationGate.class).getClass();
         getSize = constantPoolClass.getMethod("getSize");
         getTagAt = constantPoolClass.getMethod("getTagAt", int.class);
         getStringAt = constantPoolClass.getMethod("getStringAt", int.class);
         getSize.invoke(getConstantPool.invoke(AllocationGate.class));
      }
      catch (ReflectiveOperationException | RuntimeException e)
      {
         if (required)
            throw new IllegalStateException("The allocation gate needs the JVM options " + String.join(" ", CONSTANT_POOL_ACCESS_OPTIONS)
                                            + " to create the string constants of the test classes before the warm-up", e);
         return;
      }

      CodeSource testCode = AllocationGate.class.getProtectionDomain().getCodeSource();
      Set<Class<?>> classes = new LinkedHashSet<>();
      // The gate's own code runs inside the windows too (Builder.window, the observer, SpinWait).
      addWithNestedClasses(AllocationGate.class, classes);
      addWithNestedClasses(SpinWait.class, classes);
      for (Object function : functions)
      {
         if (function == null)
            continue;
         Class<?> type = function.getClass();
         // A lambda or method reference is an instance of a hidden class named <declaring class>$$Lambda$<n>/<address>.
         int lambda = type.getName().indexOf("$$Lambda");
         try
         {
            if (lambda >= 0)
               type = Class.forName(type.getName().substring(0, lambda), false, type.getClassLoader());
         }
         catch (ClassNotFoundException e)
         {
            throw new IllegalStateException(e);
         }
         while (type.getEnclosingClass() != null)
            type = type.getEnclosingClass();
         CodeSource code = type.getProtectionDomain().getCodeSource();
         if (code != null && testCode != null && code.getLocation().equals(testCode.getLocation()))
            addWithNestedClasses(type, classes);
      }

      try
      {
         for (Class<?> type : classes)
         {
            // The class must be linked: HotSpot crashes reading a string constant of a class that is only loaded (a nested
            // class nothing used yet). Listing its methods links it without initializing it.
            type.getDeclaredMethods();
            Object pool = getConstantPool.invoke(type);
            int size = (Integer) getSize.invoke(pool);
            for (int i = 1; i < size; ++i)
            {
               Object tag;
               try
               {
                  tag = getTagAt.invoke(pool, i);
               }
               catch (InvocationTargetException e)
               {
                  // A tag that jdk.internal.reflect.ConstantPool does not know (e.g. CONSTANT_Dynamic, which the JaCoCo agent
                  // adds to the classes it instruments): not a string constant.
                  continue;
               }
               if ("STRING".equals(String.valueOf(tag)))
                  getStringAt.invoke(pool, i);
            }
         }
      }
      catch (ReflectiveOperationException e)
      {
         throw new IllegalStateException(e);
      }
   }

   private static void addWithNestedClasses(Class<?> type, Set<Class<?>> classes)
   {
      if (!classes.add(type))
         return;
      for (Class<?> nested : type.getDeclaredClasses())
         addWithNestedClasses(nested, classes);
      // Anonymous classes are not declared classes: javac numbers them Outer$1, Outer$2, ...
      for (int i = 1; ; ++i)
      {
         String anonymous = type.getName() + "$" + i;
         if (type.getClassLoader() == null || type.getClassLoader().getResource(anonymous.replace('.', '/') + ".class") == null)
            break;
         try
         {
            addWithNestedClasses(Class.forName(anonymous, false, type.getClassLoader()), classes);
         }
         catch (ClassNotFoundException e)
         {
            throw new IllegalStateException(e);
         }
      }
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Builder
    * ------------------------------------------------------------------------------------------------------------ */

   public static final class Builder
   {
      private final String name;
      private int warmup = 2_000;
      private int iterations = 10_000;
      private Step settle;
      private Step beforeWindow;
      private boolean quick;
      private boolean gcBeforeWindow = true;
      private boolean monitorPass = true;
      private WindowListener windowListener;
      private int currentPass;

      private Builder(String name)
      {
         this.name = name;
      }

      /** Unmeasured executions of the body before the first window (default 2000). */
      public Builder warmup(int warmup)
      {
         this.warmup = warmup;
         return this;
      }

      /** Measured executions of the body per pass (default 10000). */
      public Builder iterations(int iterations)
      {
         this.iterations = iterations;
         return this;
      }

      /**
       * Runs after the body iterations of the warm-up and of every pass, INSIDE the window: waits (allocation-free) until
       * the asynchronous work started by the body has completed, so that it is measured and the window ends quiescent.
       */
      public Builder settle(Step settle)
      {
         this.settle = settle;
         return this;
      }

      /** Runs before every window opens, outside the window (e.g. to reset counters). May allocate. */
      public Builder beforeWindow(Step beforeWindow)
      {
         this.beforeWindow = beforeWindow;
         return this;
      }

      /** Allows running in an EA-on JVM without agent (thread-allocated-bytes pass only). For the normal suite only. */
      public Builder quick()
      {
         this.quick = true;
         return this;
      }

      /** Whether to run System.gc() before each window (default true) so a GC is unlikely inside the window. */
      public Builder gcBeforeWindow(boolean gcBeforeWindow)
      {
         this.gcBeforeWindow = gcBeforeWindow;
         return this;
      }

      /**
       * Whether to run the monitor pass (default true). Only for bodies that cannot contend on a monitor by construction
       * and whose JFR overhead matters.
       */
      public Builder monitorPass(boolean monitorPass)
      {
         this.monitorPass = monitorPass;
         return this;
      }

      /** See {@link WindowListener}. */
      public Builder windowListener(WindowListener windowListener)
      {
         this.windowListener = windowListener;
         return this;
      }

      public Result run(Body body)
      {
         try
         {
            return runChecked(body);
         }
         catch (RuntimeException | Error e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new IllegalStateException(e);
         }
      }

      /** Runs the gate and throws an {@link AssertionError} with the full report unless every pass found zero allocations. */
      public Result assertAllocationFree(Body body)
      {
         Result result = run(body);
         result.assertAllocationFree();
         return result;
      }

      private Result runChecked(Body body) throws Exception
      {
         Mode mode = mode(quick);
         Result result = new Result(name, mode, iterations);

         if (monitorPass && mode != Mode.QUICK)
            requireFrequentMonitorDeflation();
         createTestClassStringConstants(mode != Mode.QUICK, body, settle, beforeWindow, windowListener);
         for (int i = 0; i < warmup; ++i)
            body.run(i);
         if (settle != null)
            settle.run();
         if (windowListener != null)
         {
            windowListener.opened(PASS_WARMUP);
            windowListener.closed(PASS_WARMUP);
            windowListener.finished(PASS_WARMUP);
         }

         if (mode == Mode.INSTRUMENTED)
         {
            CountingSampler counting = new CountingSampler(false);
            prepareWindow();
            currentPass = PASS_INSTRUMENTER;
            AllocationRecorder.addSampler(counting);
            counting.active = true;
            try
            {
               window(body);
            }
            finally
            {
               counting.active = false;
               AllocationRecorder.removeSampler(counting);
            }
            result.instrumenterPass = counting.summarize();
            if (windowListener != null)
               windowListener.finished(PASS_INSTRUMENTER);

            if (counting.count.get() > 0)
            {
               CountingSampler capture = new CountingSampler(true);
               prepareWindow();
               currentPass = PASS_CAPTURE;
               AllocationRecorder.addSampler(capture);
               capture.active = true;
               try
               {
                  window(body);
               }
               finally
               {
                  capture.active = false;
                  AllocationRecorder.removeSampler(capture);
               }
               result.capturePass = capture.summarize();
               if (windowListener != null)
                  windowListener.finished(PASS_CAPTURE);
            }
         }

         Observer observer = Observer.get();
         Snapshot start = new Snapshot(false);
         Snapshot end = new Snapshot(true);
         prepareWindow();
         currentPass = PASS_THREAD_BYTES;
         // Diagnose mode records the allocation events of this very window, so that a one-time allocation is named too.
         Recording diagnosis = isDiagnoseEnabled() ? JfrPass.start() : null;
         observer.snapshot(start);
         Instant from = diagnosis == null ? null : Instant.now();
         window(body);
         Instant to = diagnosis == null ? null : Instant.now();
         observer.snapshot(end);
         result.threadBytesPass = ThreadBytesPass.compute(start, end);
         if (windowListener != null)
            windowListener.finished(PASS_THREAD_BYTES);
         if (diagnosis != null)
         {
            JfrPass pass = JfrPass.finish(diagnosis, from, to);
            if (result.threadBytesPass.hasViolation())
            {
               result.jfrPass = pass;
            }
         }

         if (monitorPass)
         {
            currentPass = PASS_MONITOR;
            result.monitorPass = MonitorPass.record(this, body);
            if (windowListener != null)
               windowListener.finished(PASS_MONITOR);
         }

         result.writeReport();
         return result;
      }

      void prepareWindow() throws Exception
      {
         if (beforeWindow != null)
            beforeWindow.run();
         if (gcBeforeWindow)
         {
            System.gc();
            // Let reference processing (cleaners) of that GC finish before the window opens.
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
         }
      }

      void window(Body body) throws Exception
      {
         int offset = warmup;
         WindowListener listener = windowListener;
         int pass = currentPass;
         if (listener != null)
            listener.opened(pass);
         for (int i = 0; i < iterations; ++i)
            body.run(offset + i);
         if (settle != null)
            settle.run();
         if (listener != null)
            listener.closed(pass);
      }
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Instrumenter pass
    * ------------------------------------------------------------------------------------------------------------ */

   /** One allocation reported by the instrumenter. */
   public static final class Violation
   {
      public final String thread;
      public final String type;
      public final long sizeBytes;
      public final StackTraceElement[] stack;

      Violation(String thread, String type, long sizeBytes, StackTraceElement[] stack)
      {
         this.thread = thread;
         this.type = type;
         this.sizeBytes = sizeBytes;
         this.stack = stack;
      }
   }

   public static final class InstrumenterPass
   {
      public final boolean capture;
      /** Allocations on hot-path threads: must be 0. */
      public final long count;
      public final long bytes;
      /** Allocations on allowlisted infrastructure threads (reported, not failing). */
      public final long infrastructureCount;
      /** Distinct (thread, type) pairs of the first {@value #MAX_RECORDS} hot-path allocations, with counts. */
      public final Map<String, Integer> summary;
      public final Map<String, Integer> infrastructureSummary;
      public final List<Violation> violations;

      InstrumenterPass(boolean capture,
                       long count,
                       long bytes,
                       long infrastructureCount,
                       Map<String, Integer> summary,
                       Map<String, Integer> infrastructureSummary,
                       List<Violation> violations)
      {
         this.capture = capture;
         this.count = count;
         this.bytes = bytes;
         this.infrastructureCount = infrastructureCount;
         this.summary = summary;
         this.infrastructureSummary = infrastructureSummary;
         this.violations = violations;
      }
   }

   /**
    * Counts every allocation the agent reports. {@link #sampleAllocation} never allocates in counting mode: it stores
    * references (the thread and the type descriptor) and primitives in preallocated arrays.
    */
   static final class CountingSampler implements Sampler
   {
      private final boolean captureStacks;
      /** Set once the sampler is installed and cleared before it is removed: add/removeSampler allocate themselves. */
      volatile boolean active;
      final AtomicLong count = new AtomicLong();
      final AtomicLong bytes = new AtomicLong();
      final AtomicLong infrastructureCount = new AtomicLong();
      private final AtomicInteger recorded = new AtomicInteger();
      private final AtomicInteger infrastructureRecorded = new AtomicInteger();
      private final Thread[] threads = new Thread[MAX_RECORDS];
      private final String[] threadNames = new String[MAX_RECORDS];
      private final String[] types = new String[MAX_RECORDS];
      private final int[] arrayLengths = new int[MAX_RECORDS];
      private final long[] sizes = new long[MAX_RECORDS];
      private final StackTraceElement[][] stacks = new StackTraceElement[MAX_RECORDS][];
      private final String[] infrastructureThreadNames = new String[MAX_RECORDS];
      private final String[] infrastructureTypes = new String[MAX_RECORDS];

      CountingSampler(boolean captureStacks)
      {
         this.captureStacks = captureStacks;
      }

      @Override
      public void sampleAllocation(int arrayCount, String desc, Object newObject, long size)
      {
         if (!active)
            return;
         Thread thread = Thread.currentThread();
         String threadName = thread.getName();
         if (isInfrastructureThread(threadName))
         {
            infrastructureCount.incrementAndGet();
            int index = infrastructureRecorded.getAndIncrement();
            if (index < MAX_RECORDS)
            {
               infrastructureThreadNames[index] = threadName;
               infrastructureTypes[index] = desc;
            }
            return;
         }

         count.incrementAndGet();
         bytes.addAndGet(Math.max(size, 0));
         int index = recorded.getAndIncrement();
         if (index < MAX_RECORDS)
         {
            threads[index] = thread;
            threadNames[index] = threadName;
            types[index] = desc;
            arrayLengths[index] = arrayCount;
            sizes[index] = size;
            if (captureStacks && index < MAX_STACKS)
            {
               stacks[index] = new Throwable().getStackTrace();
            }
         }
      }

      InstrumenterPass summarize()
      {
         Map<String, Integer> summary = new LinkedHashMap<>();
         List<Violation> violations = new ArrayList<>();
         int n = Math.min(recorded.get(), MAX_RECORDS);
         for (int i = 0; i < n; ++i)
         {
            String type = arrayLengths[i] >= 0 ? types[i] + "[]" : types[i];
            summary.merge(threadNames[i] + " :: " + type, 1, Integer::sum);
            if (captureStacks && i < MAX_STACKS)
            {
               String exactType = arrayLengths[i] >= 0 ? types[i] + "[" + arrayLengths[i] + "]" : types[i];
               violations.add(new Violation(threadNames[i], exactType, sizes[i], stacks[i]));
            }
         }
         Map<String, Integer> infrastructureSummary = new LinkedHashMap<>();
         int m = Math.min(infrastructureRecorded.get(), MAX_RECORDS);
         for (int i = 0; i < m; ++i)
         {
            infrastructureSummary.merge(infrastructureThreadNames[i] + " :: " + infrastructureTypes[i], 1, Integer::sum);
         }
         return new InstrumenterPass(captureStacks, count.get(), bytes.get(), infrastructureCount.get(), summary, infrastructureSummary, violations);
      }
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Thread-allocated-bytes pass
    * ------------------------------------------------------------------------------------------------------------ */

   static final class Snapshot
   {
      final boolean endOfWindow;
      long totalStartedThreads;

      Snapshot(boolean endOfWindow)
      {
         this.endOfWindow = endOfWindow;
      }

      long[] ids;
      long[] allocatedBytes;
      String[] names;

      int indexOf(long id)
      {
         for (int i = 0; i < ids.length; ++i)
         {
            if (ids[i] == id)
               return i;
         }
         return -1;
      }
   }

   /** One thread's allocation during the window. */
   public static final class ThreadBytes
   {
      public final long id;
      public final String name;
      public final boolean infrastructure;
      /** Bytes allocated in the window; for a thread that started in the window, everything it allocated. */
      public final long bytes;
      /** "started" or "ended" if the thread was created or terminated during the window, else null. */
      public final String churn;

      ThreadBytes(long id, String name, boolean infrastructure, long bytes, String churn)
      {
         this.id = id;
         this.name = name;
         this.infrastructure = infrastructure;
         this.bytes = bytes;
         this.churn = churn;
      }

      boolean isViolation()
      {
         return !infrastructure && (bytes != 0 || churn != null);
      }

      @Override
      public String toString()
      {
         return "'" + name + "' (id " + id + (infrastructure ? ", infrastructure" : "") + "): " + bytes + " bytes" + (churn == null ? "" : ", " + churn + " during the window");
      }
   }

   public static final class ThreadBytesPass
   {
      /** All threads alive at the start or the end of the window, in snapshot order. */
      public final List<ThreadBytes> threads;
      public final long hotPathBytes;
      public final long infrastructureBytes;
      public final int hotPathThreadCount;
      /**
       * Threads that were started (or attached through JNI) and ended inside the window: neither snapshot sees them, but
       * the JVM's started-thread counter does. Always a violation (their allocations are lost).
       */
      public final long hiddenThreadStarts;

      ThreadBytesPass(List<ThreadBytes> threads, long hiddenThreadStarts)
      {
         this.threads = threads;
         this.hiddenThreadStarts = hiddenThreadStarts;
         long hot = 0;
         long infrastructure = 0;
         int hotThreads = 0;
         for (ThreadBytes thread : threads)
         {
            if (thread.infrastructure)
            {
               infrastructure += Math.max(thread.bytes, 0);
            }
            else
            {
               hot += Math.max(thread.bytes, 0);
               hotThreads++;
            }
         }
         hotPathBytes = hot;
         infrastructureBytes = infrastructure;
         hotPathThreadCount = hotThreads;
      }

      public boolean hasViolation()
      {
         if (hiddenThreadStarts != 0)
            return true;
         for (ThreadBytes thread : threads)
         {
            if (thread.isViolation())
               return true;
         }
         return false;
      }

      static ThreadBytesPass compute(Snapshot start, Snapshot end)
      {
         List<ThreadBytes> threads = new ArrayList<>();
         for (int i = 0; i < end.ids.length; ++i)
         {
            int startIndex = start.indexOf(end.ids[i]);
            // A thread that ended between the enumeration and the counter read has no name at the end of the window.
            String name = end.allocatedBytes[i] < 0 && startIndex >= 0 ? start.names[startIndex] : end.names[i];
            boolean infrastructure = isInfrastructureThread(name);
            if (end.allocatedBytes[i] < 0)
            {
               // Terminated between enumeration and the counter read.
               threads.add(new ThreadBytes(end.ids[i], name, infrastructure, 0, "ended"));
            }
            else if (startIndex < 0 || start.allocatedBytes[startIndex] < 0)
            {
               threads.add(new ThreadBytes(end.ids[i], name, infrastructure, end.allocatedBytes[i], "started"));
            }
            else
            {
               threads.add(new ThreadBytes(end.ids[i], name, infrastructure, end.allocatedBytes[i] - start.allocatedBytes[startIndex], null));
            }
         }
         for (int i = 0; i < start.ids.length; ++i)
         {
            if (end.indexOf(start.ids[i]) < 0 && start.allocatedBytes[i] >= 0)
            {
               threads.add(new ThreadBytes(start.ids[i], start.names[i], isInfrastructureThread(start.names[i]), 0, "ended"));
            }
         }
         long visibleStarts = 0;
         for (ThreadBytes thread : threads)
         {
            if ("started".equals(thread.churn))
               visibleStarts++;
         }
         return new ThreadBytesPass(threads, Math.max(0, end.totalStartedThreads - start.totalStartedThreads - visibleStarts));
      }
   }

   /**
    * The thread that takes the allocated-bytes snapshots. Enumerating threads and reading other threads' counters
    * allocates, so it must not happen on a measured thread. The requesting thread waits for the snapshot by spinning,
    * which does not allocate.
    */
   static final class Observer implements Runnable
   {
      private static Observer instance;

      private final Thread thread;
      private volatile int requested;
      private volatile int completed;
      private volatile Snapshot target;
      private volatile Throwable failure;
      private final BooleanSupplier isCompleted = () -> completed == requested;

      private Observer()
      {
         thread = new Thread(this, observerThreadName());
         thread.setDaemon(true);
         thread.start();
      }

      static synchronized Observer get()
      {
         if (!THREAD_MX_BEAN.isThreadAllocatedMemorySupported())
            throw new IllegalStateException(observerError(0));
         if (!THREAD_MX_BEAN.isThreadAllocatedMemoryEnabled())
            THREAD_MX_BEAN.setThreadAllocatedMemoryEnabled(true);
         if (instance == null)
            instance = new Observer();
         return instance;
      }

      /** Takes a snapshot on the observer thread. Allocation-free on the calling thread. */
      void snapshot(Snapshot into)
      {
         target = into;
         requested = requested + 1;
         LockSupport.unpark(thread);
         if (!SpinWait.until(isCompleted, 30_000_000_000L))
            throw new IllegalStateException(observerError(1));
         if (failure != null)
            throw new IllegalStateException(observerError(2), failure);
      }

      @Override
      public void run()
      {
         for (;;)
         {
            int request = requested;
            if (request != completed)
            {
               try
               {
                  take(target);
               }
               catch (Throwable t)
               {
                  failure = t;
               }
               completed = request;
            }
            else
            {
               LockSupport.park(this);
            }
         }
      }

      private static void take(Snapshot snapshot)
      {
         // At the end of the window the counter is read before the enumeration, at the start after it: a thread started
         // between the two reads of one snapshot is then either visible as started or not counted at all, never lost.
         if (snapshot.endOfWindow)
            snapshot.totalStartedThreads = THREAD_MX_BEAN.getTotalStartedThreadCount();
         long[] ids = THREAD_MX_BEAN.getAllThreadIds();
         long[] bytes = THREAD_MX_BEAN.getThreadAllocatedBytes(ids);
         // Names come from the Java thread objects. ThreadMXBean.getThreadInfo is only asked for the threads that no thread
         // group lists (threads the JVM started itself, such as JFR's): asked for every thread it made measured threads
         // allocate now and then (seen as String allocations at their next safepoint poll).
         ThreadGroup root = Thread.currentThread().getThreadGroup();
         while (root.getParent() != null)
            root = root.getParent();
         Thread[] threads = new Thread[root.activeCount() + 64];
         int threadCount = root.enumerate(threads, true);
         String[] names = new String[ids.length];
         for (int i = 0; i < ids.length; ++i)
         {
            names[i] = null;
            for (int j = 0; j < threadCount; ++j)
            {
               if (threads[j].getId() == ids[i])
               {
                  names[i] = threads[j].getName();
                  break;
               }
            }
         }
         int missing = 0;
         for (int i = 0; i < ids.length; ++i)
         {
            if (names[i] == null)
               missing++;
         }
         if (missing > 0)
         {
            long[] missingIds = new long[missing];
            for (int i = 0, j = 0; i < ids.length; ++i)
            {
               if (names[i] == null)
                  missingIds[j++] = ids[i];
            }
            ThreadInfo[] infos = THREAD_MX_BEAN.getThreadInfo(missingIds, 0);
            for (int i = 0, j = 0; i < ids.length; ++i)
            {
               if (names[i] == null)
               {
                  ThreadInfo info = infos[j++];
                  if (info == null)
                  {
                     names[i] = observerError(3);
                     bytes[i] = -1;
                  }
                  else
                  {
                     names[i] = info.getThreadName();
                  }
               }
            }
         }
         snapshot.ids = ids;
         snapshot.allocatedBytes = bytes;
         snapshot.names = names;
         if (!snapshot.endOfWindow)
            snapshot.totalStartedThreads = THREAD_MX_BEAN.getTotalStartedThreadCount();
      }
   }

   private static String observerThreadName()
   {
      return OBSERVER_THREAD_NAME;
   }

   /** The texts of {@link Observer}, which runs inside the windows and must not have string literals of its own. */
   private static String observerError(int which)
   {
      switch (which)
      {
         case 0:
            return "HotSpot thread allocated memory measurement is not supported by this JVM";
         case 1:
            return "allocation gate observer did not respond";
         case 2:
            return "allocation gate observer failed";
         default:
            return "<terminated>";
      }
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Monitor pass
    * ------------------------------------------------------------------------------------------------------------ */

   /** Monitor inflations and contended monitor entries on hot-path threads during one more window. */
   public static final class MonitorPass
   {
      /** Events on hot-path threads: must be 0. */
      public final long events;
      /** "count x thread :: event monitor class at stack" in order of first occurrence. */
      public final List<String> groups;

      MonitorPass(long events, List<String> groups)
      {
         this.events = events;
         this.groups = groups;
      }

      static MonitorPass record(Builder builder, Body body) throws Exception
      {
         Path file = Files.createTempFile("jros2-allocation-gate-monitors", ".jfr");
         Instant from;
         Instant to;
         try (Recording recording = new Recording())
         {
            recording.enable("jdk.JavaMonitorInflate").withStackTrace().withoutThreshold();
            recording.enable("jdk.JavaMonitorEnter").withStackTrace().withoutThreshold();
            builder.prepareWindow();
            recording.start();
            from = Instant.now();
            builder.window(body);
            to = Instant.now();
            recording.stop();
            recording.dump(file);
         }

         Map<String, long[]> grouped = new LinkedHashMap<>();
         long events = 0;
         try
         {
            for (RecordedEvent event : RecordingFile.readAllEvents(file))
            {
               // Other recordings running at the same time (e.g. -XX:StartFlightRecording) add their events to the dump.
               String type = event.getEventType().getName();
               if (!type.equals("jdk.JavaMonitorInflate") && !type.equals("jdk.JavaMonitorEnter"))
                  continue;
               RecordedThread thread = event.getThread();
               String threadName = thread == null ? null : thread.getJavaName();
               if (threadName == null || isInfrastructureThread(threadName) || event.getStartTime().isBefore(from) || event.getStartTime().isAfter(to))
                  continue;
               events++;
               StringBuilder key = new StringBuilder();
               key.append(threadName).append(" :: ").append(event.getEventType().getName()).append(" on ").append(event.getClass("monitorClass").getName());
               appendStack(key, event);
               grouped.computeIfAbsent(key.toString(), k -> new long[1])[0]++;
            }
         }
         finally
         {
            Files.deleteIfExists(file);
         }
         return new MonitorPass(events, summarize(grouped));
      }
   }

   static void appendStack(StringBuilder key, RecordedEvent event)
   {
      if (event.getStackTrace() == null)
         return;
      List<RecordedFrame> frames = event.getStackTrace().getFrames();
      for (int i = 0; i < Math.min(frames.size(), JfrPass.MAX_STACK_DEPTH); ++i)
      {
         RecordedFrame frame = frames.get(i);
         key.append("\n          at ").append(frame.getMethod().getType().getName()).append('.').append(frame.getMethod().getName()).append(" line ")
            .append(frame.getLineNumber());
      }
   }

   static List<String> summarize(Map<String, long[]> grouped)
   {
      List<String> groups = new ArrayList<>();
      int n = 0;
      for (Map.Entry<String, long[]> entry : grouped.entrySet())
      {
         if (n++ == JfrPass.MAX_GROUPS)
            break;
         groups.add(entry.getValue()[0] + "x " + entry.getKey());
      }
      return groups;
   }

   /* ------------------------------------------------------------------------------------------------------------
    * JFR pass (diagnostic)
    * ------------------------------------------------------------------------------------------------------------ */

   /**
    * The allocation events JFR recorded on hot-path threads during one more window, grouped by thread, type and stack.
    * Exact with {@code -XX:-UseTLAB} (every allocation is an event), sampled otherwise.
    */
   public static final class JfrPass
   {
      static final int MAX_STACK_DEPTH = 24;
      static final int MAX_GROUPS = 32;

      public final boolean exact;
      public final long events;
      public final long bytes;
      /** "count x thread :: type (size) at stack" in order of first occurrence. */
      public final List<String> groups;

      JfrPass(boolean exact, long events, long bytes, List<String> groups)
      {
         this.exact = exact;
         this.events = events;
         this.bytes = bytes;
         this.groups = groups;
      }

      /** @return true for the allocation of the window's timestamps themselves. */
      private static boolean isGateBoundary(RecordedEvent event)
      {
         if (event.getStackTrace() == null)
            return false;
         for (RecordedFrame frame : event.getStackTrace().getFrames())
         {
            if (frame.getMethod().getType().getName().equals(Builder.class.getName()) && frame.getMethod().getName().equals("window"))
               return false;
            if (frame.getMethod().getType().getName().equals(JfrPass.class.getName())
                || frame.getMethod().getType().getName().equals(Builder.class.getName()) && frame.getMethod().getName().equals("runChecked"))
               return true;
         }
         return false;
      }

      /** Records one more window (only allocations that start a new TLAB are seen unless TLABs are off). */
      static JfrPass record(Builder builder, Body body) throws Exception
      {
         builder.prepareWindow();
         Recording recording = start();
         Instant from = Instant.now();
         builder.window(body);
         Instant to = Instant.now();
         return finish(recording, from, to);
      }

      static Recording start()
      {
         Recording recording = new Recording();
         recording.enable("jdk.ObjectAllocationOutsideTLAB").withStackTrace();
         recording.enable("jdk.ObjectAllocationInNewTLAB").withStackTrace();
         recording.start();
         return recording;
      }

      /** Stops {@code recording} and groups its allocation events on hot-path threads between {@code from} and {@code to}. */
      static JfrPass finish(Recording recording, Instant from, Instant to) throws IOException
      {
         boolean exact = !Boolean.parseBoolean(ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class).getVMOption("UseTLAB").getValue());
         Path file = Files.createTempFile("jros2-allocation-gate", ".jfr");
         try
         {
            recording.stop();
            recording.dump(file);
         }
         finally
         {
            recording.close();
         }

         Map<String, long[]> grouped = new LinkedHashMap<>();
         long events = 0;
         long bytes = 0;
         try
         {
            for (RecordedEvent event : RecordingFile.readAllEvents(file))
            {
               String type = event.getEventType().getName();
               if (!type.equals("jdk.ObjectAllocationOutsideTLAB") && !type.equals("jdk.ObjectAllocationInNewTLAB"))
                  continue;
               RecordedThread thread = event.getThread();
               String threadName = thread == null ? "<none>" : thread.getJavaName();
               if (threadName == null || isInfrastructureThread(threadName) || event.getStartTime().isBefore(from) || event.getStartTime().isAfter(to)
                   || isGateBoundary(event))
                  continue;
               long size = event.getLong("allocationSize");
               events++;
               bytes += size;
               StringBuilder key = new StringBuilder();
               key.append(threadName).append(" :: ").append(event.getClass("objectClass").getName()).append(" (").append(size).append(" bytes)");
               appendStack(key, event);
               grouped.computeIfAbsent(key.toString(), k -> new long[1])[0]++;
            }
         }
         finally
         {
            Files.deleteIfExists(file);
         }
         return new JfrPass(exact, events, bytes, summarize(grouped));
      }
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Result
    * ------------------------------------------------------------------------------------------------------------ */

   public static final class Result
   {
      public final String name;
      public final Mode mode;
      public final int iterations;
      public InstrumenterPass instrumenterPass;
      public InstrumenterPass capturePass;
      public ThreadBytesPass threadBytesPass;
      public MonitorPass monitorPass;
      /** Diagnostic only, see {@link JfrPass}. */
      public JfrPass jfrPass;

      Result(String name, Mode mode, int iterations)
      {
         this.name = name;
         this.mode = mode;
         this.iterations = iterations;
      }

      public boolean isAllocationFree()
      {
         boolean instrumenterClean = instrumenterPass == null || instrumenterPass.count == 0;
         boolean captureClean = capturePass == null || capturePass.count == 0;
         boolean monitorsClean = monitorPass == null || monitorPass.events == 0;
         return instrumenterClean && captureClean && !threadBytesPass.hasViolation() && monitorsClean;
      }

      public void assertAllocationFree()
      {
         if (!isAllocationFree())
            throw new AssertionError(report());
      }

      /** @return the hot-path threads that were alive during the window (for tests that check coverage). */
      public List<String> hotPathThreadNames()
      {
         List<String> names = new ArrayList<>();
         for (ThreadBytes thread : threadBytesPass.threads)
         {
            if (!thread.infrastructure)
               names.add(thread.name);
         }
         return names;
      }

      public String report()
      {
         StringBuilder sb = new StringBuilder();
         sb.append("Allocation gate '").append(name).append("' [").append(mode).append(", ").append(iterations).append(" iterations per pass, java ")
           .append(System.getProperty("java.version")).append(", EA ").append(isEscapeAnalysisEnabled() ? "on" : "off").append("]: ")
           .append(isAllocationFree() ? "ALLOCATION FREE" : "ALLOCATES").append('\n');
         appendInstrumenter(sb, "instrumenter pass", instrumenterPass);
         appendInstrumenter(sb, "capture pass", capturePass);
         if (threadBytesPass != null)
         {
            sb.append("  thread-allocated-bytes pass: ").append(threadBytesPass.hotPathBytes).append(" bytes on ").append(threadBytesPass.hotPathThreadCount)
              .append(" hot-path threads, ").append(threadBytesPass.infrastructureBytes).append(" bytes on infrastructure threads, ")
              .append(threadBytesPass.hiddenThreadStarts).append(" threads started and ended inside the window\n");
            for (ThreadBytes thread : threadBytesPass.threads)
            {
               sb.append(thread.isViolation() ? "    VIOLATION " : "    ").append(thread).append('\n');
            }
         }
         if (monitorPass != null)
         {
            sb.append("  monitor pass: ").append(monitorPass.events).append(" monitor inflations or contended monitor entries on hot-path threads\n");
            for (String group : monitorPass.groups)
               sb.append("    VIOLATION ").append(group).append('\n');
         }
         if (jfrPass != null)
         {
            sb.append("  JFR pass (diagnostic, ").append(jfrPass.exact ? "every allocation, -XX:-UseTLAB" : "TLAB samples only, add -XX:-UseTLAB for every allocation")
              .append("): ").append(jfrPass.events).append(" allocation events (").append(jfrPass.bytes).append(" bytes) on hot-path threads\n");
            for (String group : jfrPass.groups)
               sb.append("    ").append(group).append('\n');
         }
         return sb.toString();
      }

      private static void appendInstrumenter(StringBuilder sb, String label, InstrumenterPass pass)
      {
         if (pass == null)
            return;
         sb.append("  ").append(label).append(": ").append(pass.count).append(" allocations (").append(pass.bytes).append(" bytes) on hot-path threads, ")
           .append(pass.infrastructureCount).append(" on infrastructure threads\n");
         for (Map.Entry<String, Integer> entry : pass.summary.entrySet())
            sb.append("    ").append(entry.getValue()).append("x ").append(entry.getKey()).append('\n');
         for (Map.Entry<String, Integer> entry : pass.infrastructureSummary.entrySet())
            sb.append("    (infrastructure) ").append(entry.getValue()).append("x ").append(entry.getKey()).append('\n');
         for (Violation violation : pass.violations)
         {
            sb.append("    stack of ").append(violation.type).append(" (").append(violation.sizeBytes).append(" bytes) on '").append(violation.thread).append("':\n");
            if (violation.stack != null)
            {
               // Skip the sampler and the agent frames.
               boolean inAgent = true;
               for (StackTraceElement element : violation.stack)
               {
                  String className = element.getClassName();
                  if (inAgent && (className.equals(CountingSampler.class.getName()) || className.startsWith("com.google.monitoring.runtime.instrumentation")))
                     continue;
                  inAgent = false;
                  sb.append("        at ").append(element).append('\n');
               }
            }
         }
      }

      void writeReport()
      {
         String directory = System.getProperty(REPORT_DIR_PROPERTY);
         if (directory == null)
            return;
         try
         {
            Path dir = Paths.get(directory);
            Files.createDirectories(dir);
            String fileName = (mode + "_" + name).replaceAll("[^A-Za-z0-9_.-]+", "_") + ".txt";
            Files.write(dir.resolve(fileName), report().getBytes(StandardCharsets.UTF_8));
         }
         catch (IOException e)
         {
            throw new IllegalStateException(e);
         }
      }

      @Override
      public String toString()
      {
         return report();
      }
   }
}
