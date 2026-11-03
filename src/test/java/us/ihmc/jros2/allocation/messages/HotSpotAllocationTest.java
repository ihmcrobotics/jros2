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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import us.ihmc.fastddsjava.cdr.allocation.CDRBufferAllocationTest;
import us.ihmc.fastddsjava.cdr.allocation.IDLSequenceAllocationTest;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the message and CDR allocation gates a second time, in separate JVMs started without the
 * java-allocation-instrumenter agent, where they check HotSpot's allocated-bytes counter of the thread
 * ({@link ThreadAllocatedBytesGate}): every heap allocation of the thread counts there, also the ones bytecode
 * instrumentation cannot see (objects created by the JVM: string literals resolved for a C2 compilation, class loading,
 * re-materialization on deoptimization, allocations inside C2 intrinsics, and the instances of capturing lambdas and
 * other hidden-class allocations, which the agent cannot see at all: for those, these probe JVMs are the only gate, see
 * {@link HotSpotCounterSelfTest}). Together with the instrumented run (zero
 * allocations at the bytecode level, without relying on escape analysis) this proves zero heap allocation at the JVM
 * level, in an unmodified JVM.
 * <p>
 * Each JIT configuration of {@link #CONFIGURATIONS} gets two probe JVMs. The first one runs without the JUnit engine
 * ({@link MinimalJvmAllocationProbe}), so that nothing but the setup of the tests has run when the first window starts,
 * {@link LongSteadyStateAllocationTest} first: the CDR code, every message type and the JDK methods under them cross their
 * compilation thresholds inside measured windows, with whatever the JVM then does on the calling thread (resolving the
 * string literals of a class, loading the classes of a signature). The second one runs the short gates of
 * {@link #TEST_CLASSES} with JUnit. The JIT options of this JVM (-XX:..., -Xint, -Xcomp) are passed on, so running
 * allocationTest with, e.g., -XX:-UseCompressedOops runs the probes in that configuration too.
 * </p>
 * <p>
 * Two configurations run without the archived heap of the JDK's class data sharing archive (another collector than G1,
 * and no class data sharing at all), where the JDK classes do not come with their string literals resolved and HotSpot
 * creates them at the first C2 compilation unless an equal string is interned already (see
 * {@link HotSpotAllocationProbe#isArchivedHeapUsable()} and us.ihmc.fastddsjava.cdr.JdkPreparation).
 * </p>
 */
@Tag("allocation")
public class HotSpotAllocationTest
{
   private static final Class<?>[] TEST_CLASSES = {CDRBufferAllocationTest.class,
                                                   IDLSequenceAllocationTest.class,
                                                   BundledMessagesAllocationTest.class,
                                                   AllFieldKindsAllocationTest.class,
                                                   StringAccessorsAllocationTest.class,
                                                   HotSpotCounterSelfTest.class};
   /** The tests of {@link #TEST_CLASSES} when this gate was written; may grow, never shrink. */
   private static final int MINIMUM_TESTS = 375;
   /** Run by {@link MinimalJvmAllocationProbe}, the long windows first. */
   private static final Class<?>[] MINIMAL_JVM_TEST_CLASSES = {LongSteadyStateAllocationTest.class,
                                                               CDRBufferAllocationTest.class,
                                                               IDLSequenceAllocationTest.class,
                                                               BundledMessagesAllocationTest.class,
                                                               HotSpotCounterSelfTest.class};
   /**
    * The tests of {@link #MINIMAL_JVM_TEST_CLASSES} when this gate was written (long windows: 179 types, their count,
    * sensors; then 4 + 3 + 361; then the counter self-test); may grow, never shrink.
    */
   private static final int MINIMUM_MINIMAL_JVM_TESTS = 550;
   /**
    * JIT configurations of the probe JVMs: the default (tiered C1+C2), without escape analysis (the counter must not
    * depend on it: escape analysis can hide an allocation from it), C2 only (no tiered compilation, as realtime
    * deployments often run; C2 then also compiles native method wrappers, with the same string-constant resolution), and
    * C1 only (no escape analysis at all, other inlining and intrinsics), the parallel collector (no archived heap: the JDK
    * classes' string literals are not resolved in advance) and no class data sharing at all (every JDK class loaded from
    * the runtime image, with its java.lang.Class object, its literals not resolved).
    */
   private static final String[][] CONFIGURATIONS = {{},
                                                     {"-XX:-DoEscapeAnalysis"},
                                                     {"-XX:-TieredCompilation"},
                                                     {"-XX:TieredStopAtLevel=1"},
                                                     {"-XX:+UseParallelGC"},
                                                     {"-Xshare:off"}};
   private static final Pattern SUMMARY = Pattern.compile("PROBE agent=(\\w+) found=(\\d+) succeeded=(\\d+) failed=(\\d+) skipped=(\\d+) archivedHeap=(\\w+) vm=\\[(.*)]");

   @TestFactory
   public List<DynamicTest> testGatesWithoutTheAgent()
   {
      List<DynamicTest> tests = new ArrayList<>();
      for (String[] configuration : CONFIGURATIONS)
      {
         String name = configuration.length == 0 ? "default JIT" : String.join(" ", configuration);
         tests.add(DynamicTest.dynamicTest("long steady state first, minimal JVM, " + name,
                                           () -> runProbe(configuration, MinimalJvmAllocationProbe.class, MINIMUM_MINIMAL_JVM_TESTS, MINIMAL_JVM_TEST_CLASSES)));
         tests.add(DynamicTest.dynamicTest("short gates, JUnit, " + name,
                                           () -> runProbe(configuration, HotSpotAllocationProbe.class, MINIMUM_TESTS, TEST_CLASSES)));
      }
      return tests;
   }

   /** The JIT options of this JVM, for the probe JVMs (not the agent, not the flight recorder, not the heap settings). */
   static List<String> inheritedJitOptions()
   {
      List<String> options = new ArrayList<>();
      for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments())
      {
         boolean jit = argument.equals("-Xint") || argument.equals("-Xcomp") || argument.equals("-Xmixed")
                       || (argument.startsWith("-XX:") && !argument.contains("FlightRecord") && !argument.contains("HeapDump"));
         if (jit)
            options.add(argument);
      }
      return options;
   }

   private static boolean isCollectorSelection(String option)
   {
      return option.startsWith("-XX:+Use") && option.endsWith("GC");
   }

   private static void runProbe(String[] configuration, Class<?> probe, int minimumTests, Class<?>... testClasses) throws IOException, InterruptedException
   {
      List<String> command = new ArrayList<>();
      command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
      boolean selectsCollector = Arrays.stream(configuration).anyMatch(HotSpotAllocationTest::isCollectorSelection);
      for (String option : inheritedJitOptions())
      {
         // Only one collector can be selected
         if (!(selectsCollector && isCollectorSelection(option)))
            command.add(option);
      }
      command.addAll(List.of(configuration));
      command.add("-cp");
      command.add(System.getProperty("java.class.path"));
      command.add(probe.getName());
      for (Class<?> testClass : testClasses)
         command.add(testClass.getName());

      Path output = Files.createTempFile("jros2-hotspot-allocation", ".log");
      try
      {
         Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
         boolean exited = process.waitFor(15, TimeUnit.MINUTES);
         if (!exited)
            process.destroyForcibly();
         String log = Files.readString(output, StandardCharsets.UTF_8);
         assertTrue(exited, "the probe JVM did not finish: " + command + "\n" + log);

         Matcher summary = SUMMARY.matcher(log);
         assertTrue(summary.find(), "no summary from the probe JVM: " + command + "\n" + log);
         assertFalse(Boolean.parseBoolean(summary.group(1)), "the probe JVM must run without the agent");
         assertEquals(0, Integer.parseInt(summary.group(4)), "allocations seen by HotSpot's per-thread counter without the agent: " + command + "\n" + log);
         assertEquals(0, process.exitValue(), log);
         int succeeded = Integer.parseInt(summary.group(3));
         assertEquals(Integer.parseInt(summary.group(2)), succeeded, log);
         assertTrue(succeeded >= minimumTests, "only " + succeeded + " tests ran in the probe JVM:\n" + log);
         System.out.println(summary.group() + " " + List.of(configuration));
      }
      finally
      {
         Files.deleteIfExists(output);
      }
   }
}
