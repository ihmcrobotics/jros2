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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Native memory of a subscription to large messages ({@link SubscriptionPayloadMemoryChild}, in its own JVM). Every
 * preallocated payload of a topic grows to the largest message seen, and a DataReader preallocates
 * {@code allocated_samples} payloads whatever its depth: with Fast-DDS's default of 100, a subscription of depth 5 cost about
 * 100 times the message. jros2 preallocates the depth (about 12 payloads for this topic: 6 for the writer, 6 for the
 * reader). KEEP_ALL keeps the default and is the control that shows the test detects the growth (about 107 payloads).
 */
public class SubscriptionPayloadMemoryTest
{
   /** KEEP_LAST: above the 12 payloads plus the serialization buffers and the touched shared memory. */
   static final long MAX_KEEP_LAST_MESSAGES = 30;
   /** KEEP_ALL: well below its 107 payloads. */
   static final long MIN_KEEP_ALL_MESSAGES = 60;

   private static String output;
   private static Map<String, Map<String, String>> results;

   @BeforeAll
   public static void runChild() throws Exception
   {
      Assumptions.assumeTrue(ROS2TestTools.isNotAndroid());
      Assumptions.assumeTrue(Files.isReadable(Paths.get("/proc/self/status")), "needs /proc/self/status");
      String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
      List<String> command = new ArrayList<>();
      command.add(java);
      command.addAll(fastddsjavaTestTools.coverageAgentArguments());
      command.addAll(List.of("-Xms256m",
                             "-Xmx256m",
                             "-XX:+AlwaysPreTouch",
                             "-Dfastdds.intraprocess.delivery=false",
                             "-cp",
                             System.getProperty("java.class.path"),
                             SubscriptionPayloadMemoryChild.class.getName()));
      ProcessBuilder builder = new ProcessBuilder(command);
      builder.redirectErrorStream(true);
      File file = File.createTempFile("jros2-payload-memory", ".txt");
      try
      {
         builder.redirectOutput(file);
         Process process = builder.start();
         if (!process.waitFor(100, TimeUnit.SECONDS))
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
         if (line.startsWith("RESULT "))
         {
            System.out.println(line);
            String[] parts = line.trim().split(" ");
            Map<String, String> values = new HashMap<>();
            for (int i = 2; i < parts.length; ++i)
            {
               int equals = parts[i].indexOf('=');
               values.put(parts[i].substring(0, equals), parts[i].substring(equals + 1));
            }
            results.put(parts[1], values);
         }
      }
      assertTrue(output.contains("DONE"), output);
   }

   private static long growthInMessages(String name)
   {
      Map<String, String> result = results.get(name);
      assertNotNull(result, () -> name + " missing:\n" + output);
      assertEquals(result.get("sent"), result.get("received"), () -> name + ": RELIABLE delivers every sample; " + result);
      return Long.parseLong(result.get("growthBytes")) / Long.parseLong(result.get("size"));
   }

   @Test
   @Timeout(30)
   public void testKeepLastSubscriptionPreallocatesItsDepth()
   {
      long growth = growthInMessages("keep-last");
      assertTrue(growth < MAX_KEEP_LAST_MESSAGES, () -> "resident set grew by " + growth + " messages; " + results.get("keep-last"));
   }

   @Test
   @Timeout(30)
   public void testKeepAllSubscriptionControl()
   {
      long growth = growthInMessages("keep-all");
      assertTrue(growth > MIN_KEEP_ALL_MESSAGES, () -> "resident set grew by " + growth + " messages; " + results.get("keep-all"));
   }
}
