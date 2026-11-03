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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import us.ihmc.fastddsjava.fastddsjavaTestTools;
import us.ihmc.jros2.allocation.AllocationGate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fast smoke version of the allocation gates for the normal test suite: sync and async publish, inline, polling and
 * executor receive, both native ABI paths and a CDR round trip must allocate zero bytes on every thread (no tolerance).
 * <p>
 * The gates run in a child JVM ({@link ROS2AllocationSmokeChild}) with {@code -Xbatch -XX:CompileThresholdScaling=0.1},
 * so that the one-time JIT warm-up effects happen at fixed points of the warm-up (see {@code AllocationGate}); this JVM's
 * settings (JaCoCo, parallel tests) do not matter. Escape analysis is on and the instrumenter is not loaded, so this is a
 * smoke test only: the real gate is {@code :jros2-test:allocationTest}, which runs every test tagged "allocation" with the
 * instrumenter, without escape analysis and with intraprocess delivery off and on.
 */
public class ROS2AllocationTest
{
   private static final long CHILD_TIMEOUT_SECONDS = 300;

   @Test
   @Timeout(CHILD_TIMEOUT_SECONDS + 30)
   public void testHotPathsAreAllocationFree() throws IOException, InterruptedException
   {
      List<String> command = new ArrayList<>();
      command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
      command.add("-Xmx512m");
      command.add("-Xbatch");
      command.add("-XX:CompileThresholdScaling=0.1");
      command.addAll(List.of(AllocationGate.CONSTANT_POOL_ACCESS_OPTIONS));
      command.addAll(fastddsjavaTestTools.coverageAgentArguments());
      for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments())
      {
         // Settings that Gradle or the environment pass as system properties (e.g. intraprocess delivery, the native path)
         if (argument.startsWith("-Dfastdds.") || argument.startsWith("-Djros2.") || argument.startsWith("-Dros."))
            command.add(argument);
      }
      command.add("-cp");
      command.add(System.getProperty("java.class.path"));
      command.add(ROS2AllocationSmokeChild.class.getName());

      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      Thread pump = new Thread(() ->
      {
         try (InputStream in = process.getInputStream())
         {
            in.transferTo(output);
         }
         catch (IOException ignored)
         {
            // the process ended
         }
      }, "allocation-smoke-output");
      pump.start();
      boolean finished = process.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!finished)
         process.destroyForcibly();
      pump.join(10_000);
      String text = output.toString(StandardCharsets.UTF_8);
      assertTrue(finished, "the allocation smoke child timed out:\n" + text);
      assertEquals(0, process.exitValue(), "allocation smoke child failed:\n" + text);
      assertTrue(text.contains("ALLOCATION-SMOKE OK"), text);
      assertEquals(5, text.split("RESULT ok ", -1).length - 1, text);
   }
}
