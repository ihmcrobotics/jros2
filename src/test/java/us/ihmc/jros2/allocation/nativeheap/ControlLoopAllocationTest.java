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
package us.ihmc.jros2.allocation.nativeheap;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Combined control-loop gate: Java heap on ALL threads and native heap on every hot-path thread, in the same windows, for
 * 10 s of a realistic robot process at 1 kHz (see {@link ControlLoopAllocationChild}): three nodes in one ROS2Context and
 * one separate node, sync publish of sensor_msgs/JointState at 1 kHz, async publish of a ~1 MB sensor_msgs/Image
 * (RELIABLE, 30 Hz), inline and ROS2CallbackExecutor callbacks, polling readLatest, statistics enabled everywhere,
 * native ABI v2.
 * <p>
 * The child JVM runs with the alloccount LD_PRELOAD shim and with this JVM's gate configuration (the
 * java-allocation-instrumenter agent in the allocationTest tasks, -XX:-DoEscapeAnalysis in the NoEA tasks, the JIT and
 * monitor deflation options), and with this JVM's intraprocess mode: the allocationTest task covers intraprocess
 * delivery OFF, allocationTestIntraprocess USER_DATA_ONLY. The test fails unless the Java gate found zero allocations on every
 * thread in every pass, every native window has zero library, Java-native, monitor-inflation, unclassified JVM-internal
 * and discovery calls on hot-path threads (see NativeHeapAllocationChild), and the last JointState, estimate and image of
 * every window arrived (at least 99 % of the JointStates and 90 % of the images overall: RELIABLE KEEP_LAST replaces
 * samples a stalled reader has not acknowledged). JIT, handshake and GC calls on hot-path threads and calls on JVM service threads are
 * reported ("CLA NATIVE" lines, NHA-SUMMARY lines), not gated.
 */
@Tag("allocation")
public class ControlLoopAllocationTest
{
   private static final long CHILD_TIMEOUT_MINUTES = 6;

   @Test
   public void testControlLoopJavaAndNativeHeap() throws Exception
   {
      String[] skipReason = new String[1];
      Path shim = NativeHeapAllocationTest.compileShim(skipReason);
      Assumptions.assumeTrue(shim != null, () -> "control loop gate skipped: " + skipReason[0]);
      boolean intraprocess = Boolean.parseBoolean(System.getProperty("fastdds.intraprocess.delivery", "false"));
      String label = "control loop, intraprocess " + (intraprocess ? "USER_DATA_ONLY" : "OFF") + ", mode " + System.getProperty("jros2.allocation.mode", "?");

      Path countsFile = Files.createTempFile("jros2-alloccount", ".bin");
      List<String> command = new ArrayList<>();
      command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
      command.add("-Xms1g");
      command.add("-Xmx1g");
      // The gate configuration of this JVM: agent, JIT and deflation options, module access, gate properties
      for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments())
      {
         if ((argument.startsWith("-javaagent:") && !argument.contains("jacoco")) || argument.equals("-Xbatch") || argument.equals("-Xint")
             || argument.equals("-Xcomp") || argument.startsWith("-XX:") || argument.startsWith("--add-opens") || argument.startsWith("--add-exports")
             || argument.startsWith("-Djros2.") || argument.startsWith("-Dfastdds."))
            command.add(argument);
      }
      command.add("-D" + NativeAllocationCounter.FILE_PROPERTY + "=" + countsFile);
      String symbolDirs = System.getenv("JROS2_NATIVE_SYMBOL_DIRS");
      if (symbolDirs != null)
         command.add("-D" + NativeStackResolver.SYMBOL_DIRS_PROPERTY + "=" + symbolDirs);
      command.add("-cp");
      command.add(System.getProperty("java.class.path"));
      command.add(ControlLoopAllocationChild.class.getName());

      ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
      Map<String, String> environment = builder.environment();
      String preload = environment.get("LD_PRELOAD");
      environment.put("LD_PRELOAD", preload == null || preload.isEmpty() ? shim.toString() : shim + ":" + preload);
      environment.put(NativeAllocationCounter.FILE_ENV, countsFile.toString());
      environment.put("FASTDDS_INTRAPROCESS_DELIVERY", Boolean.toString(intraprocess));

      Path log = Files.createTempFile("jros2-control-loop-child", ".log");
      List<String> lines;
      boolean finished;
      try
      {
         builder.redirectOutput(log.toFile());
         Process process = builder.start();
         finished = process.waitFor(CHILD_TIMEOUT_MINUTES, TimeUnit.MINUTES);
         if (!finished)
         {
            process.destroyForcibly();
            process.waitFor(30, TimeUnit.SECONDS);
         }
         lines = Files.readAllLines(log, StandardCharsets.UTF_8);
      }
      finally
      {
         Files.deleteIfExists(log);
         Files.deleteIfExists(countsFile);
      }

      // Everything goes to the test output (the report reads the CLA lines); the failure message carries the summary.
      StringBuilder digest = new StringBuilder();
      List<String> gatedStacks = new ArrayList<>();
      for (String line : lines)
      {
         System.out.println(line);
         if (line.startsWith(ControlLoopAllocationChild.PREFIX))
            digest.append(line).append('\n');
         if (line.startsWith(NativeHeapAllocationChild.PREFIX + "STACK ") && (line.contains(" library | ") || line.contains(" java-native | ")
                                                                               || line.contains(" jvm-monitor | ") || line.contains(" jvm-internal | ")
                                                                               || line.contains(" discovery | "))
             && NativeHeapAllocationTest.isHotPathStack(line, ControlLoopAllocationChild::classify))
            gatedStacks.add(line);
      }
      NativeHeapAllocationTest.printJvmInternalSummary(label, lines, ControlLoopAllocationChild::classify);

      String done = lines.stream().filter(line -> line.startsWith(ControlLoopAllocationChild.PREFIX + "DONE")).findFirst().orElse("");
      assertTrue(finished && !done.isEmpty(), label + ": the child did not finish\n" + digest);
      assertTrue(lines.stream().noneMatch(line -> line.startsWith(ControlLoopAllocationChild.PREFIX + "ERROR ")), label + ": the gate did not complete\n" + digest);
      long nativeWindows = lines.stream().filter(line -> line.startsWith(ControlLoopAllocationChild.PREFIX + "NATIVE ")).count();
      String mode = System.getProperty("jros2.allocation.mode", "instrumented");
      // The native counter covers the instrumenter window (agent JVMs) and the thread-allocated-bytes window
      assertTrue(nativeWindows == (mode.equals("instrumented") ? 2 : 1), label + ": native windows measured: " + nativeWindows + "\n" + digest);
      assertTrue(lines.stream().anyMatch(line -> line.startsWith(ControlLoopAllocationChild.PREFIX + "CONFIG ") && line.contains(" nativeAbiV2=true ")),
                 label + ": ABI v2 not in use\n" + digest);
      if (!done.contains("failures=0 "))
      {
         StringBuilder message = new StringBuilder(label + ": " + done + "\n" + digest);
         message.append("\ngated native stacks (first 40):\n");
         for (int i = 0; i < Math.min(40, gatedStacks.size()); ++i)
            message.append("  ").append(gatedStacks.get(i)).append('\n');
         fail(message.toString());
      }
   }
}
