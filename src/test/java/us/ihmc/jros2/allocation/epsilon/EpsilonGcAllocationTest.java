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
package us.ihmc.jros2.allocation.epsilon;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GC-free control process: the control loop of ControlLoopAllocationTest ({@link EpsilonControlLoopChild}) in a child JVM with
 * the Epsilon GC (no collector: memory is never freed) and a heap of {@value #HEAP_MB} MB, for {@value #MEASURED_SECONDS} s
 * after a {@value #WARMUP_SECONDS} s warm-up. The child runs without thread-local allocation buffers, so the used heap counts
 * every allocated byte: after the warm-up it must not grow at all, and the process must not run out of memory.
 * <p>
 * The child gets this JVM's JIT and escape analysis options (not the allocation instrumenter, which allocates itself) and its
 * intraprocess mode, so the allocation tasks run it with intraprocess delivery OFF and FULL, with and without escape analysis.
 */
@Tag("allocation")
public class EpsilonGcAllocationTest
{
   static final int HEAP_MB = 128;
   static final int WARMUP_SECONDS = 15;
   static final int MEASURED_SECONDS = 30;
   private static final long CHILD_TIMEOUT_MINUTES = 4;

   @Test
   public void testControlLoopRunsWithoutCollector() throws Exception
   {
      boolean intraprocess = Boolean.parseBoolean(System.getProperty("fastdds.intraprocess.delivery", "false"));
      List<String> command = new ArrayList<>();
      command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
      for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments())
      {
         if (argument.equals("-Xbatch") || argument.equals("-Xint") || argument.startsWith("-XX:") || argument.startsWith("--add-opens")
             || argument.startsWith("--add-exports") || argument.startsWith("-Djros2.") || argument.startsWith("-Dfastdds."))
            command.add(argument);
      }
      command.add("-XX:+UnlockExperimentalVMOptions");
      command.add("-XX:+UseEpsilonGC");
      command.add("-XX:-UseTLAB");
      command.add("-Xms" + HEAP_MB + "m");
      command.add("-Xmx" + HEAP_MB + "m");
      command.add("-cp");
      command.add(System.getProperty("java.class.path"));
      command.add(EpsilonControlLoopChild.class.getName());
      command.add(Integer.toString(WARMUP_SECONDS));
      command.add(Integer.toString(MEASURED_SECONDS));

      ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
      builder.environment().put("FASTDDS_INTRAPROCESS_DELIVERY", Boolean.toString(intraprocess));
      Path log = Files.createTempFile("jros2-epsilon-child", ".log");
      List<String> lines;
      boolean finished;
      int exit = -1;
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
         else
         {
            exit = process.exitValue();
         }
         lines = Files.readAllLines(log, StandardCharsets.UTF_8);
      }
      finally
      {
         Files.deleteIfExists(log);
      }

      StringBuilder output = new StringBuilder();
      String result = null;
      for (String line : lines)
      {
         if (line.startsWith(EpsilonControlLoopChild.PREFIX) || line.contains("OutOfMemoryError") || line.contains("Exception"))
            output.append(line).append('\n');
         if (line.startsWith(EpsilonControlLoopChild.PREFIX + "RESULT "))
            result = line;
      }
      String label = "Epsilon GC, " + HEAP_MB + " MB heap, intraprocess " + (intraprocess ? "FULL" : "OFF");
      System.out.println(label + "\n" + output);
      assertTrue(finished, label + ": the child did not finish\n" + output);
      assertTrue(lines.stream().noneMatch(line -> line.contains("OutOfMemoryError")), label + ": out of memory\n" + output);
      assertEquals(0, exit, label + ": exit code\n" + String.join("\n", lines));
      assertNotNull(result, label + ": no result\n" + output);
      assertEquals(0, value(result, "growth"), label + ": the used heap grew after the warm-up (every byte counts: no TLABs)\n" + output);
      assertEquals(0, value(result, "maxGrowth"), label + ": the used heap grew during the measurement\n" + output);
      assertEquals(0, value(result, "receiveFailures"), label + "\n" + output);
      long published = value(result, "jointsPublished");
      assertEquals(1000L * (WARMUP_SECONDS + MEASURED_SECONDS), published);
      assertTrue(100 * value(result, "monitorJoints") >= 99 * published && 100 * value(result, "estimator") >= 99 * published, label + "\n" + output);
      assertTrue(10 * value(result, "monitorImages") >= 9 * value(result, "imagesPublished"), label + "\n" + output);
   }

   private static long value(String line, String key)
   {
      Matcher matcher = Pattern.compile(" " + key + "=(-?\\d+)").matcher(line);
      assertTrue(matcher.find(), key + " missing in " + line);
      return Long.parseLong(matcher.group(1));
   }
}
