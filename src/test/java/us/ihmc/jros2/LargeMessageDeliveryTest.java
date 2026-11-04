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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import us.ihmc.fastddsjava.fastddsjavaTestTools;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Large messages between two nodes of one process with intraprocess delivery OFF, i.e. through the shared-memory
 * transport, with jros2's default transports ({@link LargeMessageDeliveryChild}, in its own JVM so that intraprocess
 * delivery is OFF whatever this JVM uses).
 * <p>
 * With Fast-DDS's default 512 KiB shared-memory segment, the fragments of a larger message that do not fit the
 * publisher's segment are discarded: RELIABLE samples arrive only after the RTPS repair (the reader's 5 ms heartbeat
 * response delay plus the writer's 5 ms NACK response delay, about 11 ms for a 1 MB image and more for larger ones) and
 * BEST_EFFORT samples are lost. jros2's default segment (4 MiB) holds them. The thresholds are far from both regimes: a
 * delivered 1 MB or 2.7 MB image takes about 0.6 or 1.6 ms here, a repaired one at least 10 ms. The same run with a
 * publisher that uses Fast-DDS's default segment is the control that shows the test detects the delay.
 */
public class LargeMessageDeliveryTest
{
   /** Median one-way latency below which no systematic repair happens (a repaired sample takes at least 10 ms). */
   static final double MAX_P50_MS = 5.0;

   private static String output;
   private static Map<String, Map<String, String>> results;

   @BeforeAll
   public static void runChild() throws Exception
   {
      Assumptions.assumeTrue(ROS2TestTools.isNotAndroid());
      // The child's two nodes with jros2's default segment need twice the segment free when each is created (otherwise
      // they fall back to Fast-DDS's default segment, see ROS2SharedMemoryConfiguration), e.g. in a container with Docker's
      // default 64 MB /dev/shm that killed test processes have filled with their segments
      long free = ROS2SharedMemoryConfiguration.sharedMemoryFreeBytes();
      Assumptions.assumeTrue(free < 0 || free >= 4 * ROS2NodeOptions.DEFAULT_SHM_SEGMENT_SIZE,
                             "only " + free + " bytes free in /dev/shm; jros2 would use Fast-DDS's default segment");
      String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
      List<String> command = new ArrayList<>();
      command.add(java);
      command.addAll(fastddsjavaTestTools.coverageAgentArguments());
      // Intraprocess delivery OFF (the system property overrides FASTDDS_INTRAPROCESS_DELIVERY), and jros2's default
      // segment size whatever the environment or a jros2.properties file says (an empty value counts as unset)
      command.addAll(List.of("-Xmx512m", "-Dfastdds.intraprocess.delivery=false", "-Djros2.shm.segment.size=", "-cp", System.getProperty("java.class.path"),
                             LargeMessageDeliveryChild.class.getName()));
      ProcessBuilder builder = new ProcessBuilder(command);
      builder.environment().remove(ROS2SharedMemoryConfiguration.FASTDDS_BUILTIN_TRANSPORTS);
      builder.redirectErrorStream(true);
      File file = File.createTempFile("jros2-large-delivery", ".txt");
      try
      {
         builder.redirectOutput(file);
         Process process = builder.start();
         if (!process.waitFor(150, TimeUnit.SECONDS))
         {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
         }
         output = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
      }
      finally
      {
         Files.deleteIfExists(file.toPath());
      }
      results = new HashMap<>();
      for (String line : output.split("\n"))
      {
         if (line.startsWith("RESULT ") || line.startsWith("SEGMENT "))
         {
            System.out.println(line);
            String[] parts = line.trim().split(" ");
            Map<String, String> values = new HashMap<>();
            for (int i = 2; i < parts.length; ++i)
            {
               int equals = parts[i].indexOf('=');
               values.put(parts[i].substring(0, equals), parts[i].substring(equals + 1));
            }
            results.put(parts[0] + " " + parts[1], values);
         }
      }
      assertTrue(output.contains("DONE"), output);
   }

   private static Map<String, String> result(String name)
   {
      Map<String, String> result = results.get("RESULT " + name);
      assertNotNull(result, () -> name + " missing:\n" + output);
      return result;
   }

   private static void assertDeliveredWithoutRepair(String name, boolean reliable)
   {
      Map<String, String> result = result(name);
      int sent = Integer.parseInt(result.get("sent"));
      int received = Integer.parseInt(result.get("received"));
      if (reliable)
         assertEquals(sent, received, name + ": RELIABLE delivers every sample; " + result);
      else
         assertTrue(received >= sent * 9 / 10, name + ": BEST_EFFORT must not lose large samples; " + result);
      assertTrue(Double.parseDouble(result.get("p50Ms")) < MAX_P50_MS, name + ": median latency must be far below the RTPS repair delay; " + result);
      assertTrue(Integer.parseInt(result.get("atLeast10Ms")) < received / 4, name + ": no systematic repair; " + result);
   }

   @Test
   @Timeout(30)
   public void testReliable1MBImageWithoutRepairDelay()
   {
      assertDeliveredWithoutRepair("default-reliable-1mb", true);
   }

   @Test
   @Timeout(30)
   public void testReliable2_7MBImageWithoutRepairDelay()
   {
      assertDeliveredWithoutRepair("default-reliable-2.7mb", true);
   }

   @Test
   @Timeout(30)
   public void testBestEffort2_7MBImageDelivered()
   {
      assertDeliveredWithoutRepair("default-best_effort-2.7mb", false);
   }

   /** The control: a publisher with Fast-DDS's default 512 KiB segment repairs every sample, or loses it with BEST_EFFORT. */
   @Test
   @Timeout(30)
   public void testFastDdsDefaultSegmentDelaysOrLosesLargeImages()
   {
      Map<String, String> reliable = result("fastdds-reliable-2.7mb");
      assertEquals(reliable.get("sent"), reliable.get("received"), reliable.toString());
      assertTrue(Double.parseDouble(reliable.get("p50Ms")) > 8.0, "the 512 KiB segment must delay the samples through the repair; " + reliable);

      Map<String, String> bestEffort = result("fastdds-best_effort-2.7mb");
      assertTrue(Integer.parseInt(bestEffort.get("received")) <= Integer.parseInt(bestEffort.get("sent")) / 2,
                 "the 512 KiB segment must lose most BEST_EFFORT samples; " + bestEffort);
   }

   /** The publisher's participant created a segment of the configured size (Linux, where Fast-DDS uses /dev/shm). */
   @Test
   @Timeout(30)
   public void testSegmentSizes()
   {
      Assumptions.assumeTrue(new File(ROS2SharedMemoryConfiguration.SHARED_MEMORY_DIRECTORY).isDirectory(), "no /dev/shm");
      assertSegment("default", ROS2NodeOptions.DEFAULT_SHM_SEGMENT_SIZE);
      assertSegment("fastdds", 512 * 1024);
   }

   private static void assertSegment(String label, long size)
   {
      Map<String, String> segment = results.get("SEGMENT " + label);
      assertNotNull(segment, () -> "no new segment for the " + label + " publisher:\n" + output);
      long bytes = Long.parseLong(segment.get("bytes"));
      // The segment holds the payload plus Fast-DDS's buffer nodes and allocator overhead (about 5 % here)
      assertTrue(bytes >= size && bytes <= size + size / 8, label + " segment of " + bytes + " bytes, expected about " + size);
   }
}
