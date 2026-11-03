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
import us.ihmc.fastddsjava.profiles.TransportDescriptorTypeTools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Thread settings must reach the native Fast-DDS threads, not only be accepted by the XML parser (Fast-DDS silently ignores
 * {@code builtin_transports_reception_threads} unless an explicit {@code <builtinTransports>} follows it). The CPU affinity of
 * the threads a new node starts is read back from {@code /proc/self/task}: the reception threads ({@code dds.udp.<port>},
 * {@code dds.shm.<port>}) must run on the RECEPTION CPU and the event thread ({@code dds.ev.<n>}) on the TIMED_EVENTS CPU.
 * Linux only; needs two CPUs this process may use. Creates DDS participants: run under the shared DDS lock.
 */
public class ROS2ThreadSettingsFunctionalTest
{
   private static final Path TASKS = Paths.get("/proc/self/task");
   private static final Pattern RECEPTION_THREAD = Pattern.compile("dds\\.(udp|shm)\\.[0-9]+");
   private static final Pattern EVENT_THREAD = Pattern.compile("dds\\.ev\\.[0-9]+");

   static
   {
      jros2.load();
   }

   /** @return thread id -> thread name of every thread of this process */
   private static Map<String, String> threads() throws IOException
   {
      Map<String, String> threads = new HashMap<>();
      try (DirectoryStream<Path> tasks = Files.newDirectoryStream(TASKS))
      {
         for (Path task : tasks)
         {
            try
            {
               threads.put(task.getFileName().toString(), new String(Files.readAllBytes(task.resolve("comm")), StandardCharsets.UTF_8).trim());
            }
            catch (IOException exitedMeanwhile)
            {
               // The thread ended while listing
            }
         }
      }
      return threads;
   }

   /** @return the Cpus_allowed_list of a thread, e.g. "0-19" or "3", or null if it ended */
   private static String cpusAllowed(String threadId)
   {
      try
      {
         for (String line : Files.readAllLines(TASKS.resolve(threadId).resolve("status"), StandardCharsets.UTF_8))
         {
            if (line.startsWith("Cpus_allowed_list:"))
            {
               return line.substring("Cpus_allowed_list:".length()).trim();
            }
         }
      }
      catch (IOException exitedMeanwhile)
      {
         // The thread ended
      }
      return null;
   }

   /** @return two CPUs this process may run on, or null */
   private static int[] twoUsableCpus() throws IOException
   {
      String allowed = cpusAllowed(Long.toString(currentThreadId()));
      if (allowed == null)
      {
         return null;
      }
      Set<Integer> cpus = new HashSet<>();
      for (String range : allowed.split(","))
      {
         String[] bounds = range.split("-");
         int first = Integer.parseInt(bounds[0].trim());
         int last = bounds.length > 1 ? Integer.parseInt(bounds[1].trim()) : first;
         for (int cpu = first; cpu <= last && cpu < 64; ++cpu)
         {
            cpus.add(cpu);
         }
      }
      int[] result = cpus.stream().sorted().limit(2).mapToInt(Integer::intValue).toArray();
      return result.length == 2 ? result : null;
   }

   private static long currentThreadId() throws IOException
   {
      // The kernel thread id of the calling thread: /proc/thread-self points to /proc/<pid>/task/<tid>
      return Long.parseLong(Paths.get("/proc/thread-self").toRealPath().getFileName().toString());
   }

   /**
    * Creates a node with {@code options} (plus a publisher and a subscription), then checks the affinity of the reception and
    * event threads it started.
    */
   private static void assertAffinityApplied(ROS2NodeOptions options, int receptionCpu, int eventCpu) throws Exception
   {
      Set<String> before = threads().keySet();
      ROS2Node node = new ROS2Node("g2_thread_settings", options);
      try
      {
         ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>("/g2_thread_settings_" + System.nanoTime(), std_msgs.String_.class);
         node.createPublisher(topic);
         node.createSubscription(topic, reader -> reader.read());

         Map<String, String> reception = new HashMap<>();
         Map<String, String> events = new HashMap<>();
         String expectedReception = Integer.toString(receptionCpu);
         String expectedEvent = Integer.toString(eventCpu);
         long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
         boolean applied = false;
         while (!applied && System.nanoTime() < deadline)
         {
            reception.clear();
            events.clear();
            for (Map.Entry<String, String> thread : threads().entrySet())
            {
               if (before.contains(thread.getKey()))
               {
                  continue;
               }
               if (RECEPTION_THREAD.matcher(thread.getValue()).matches())
               {
                  reception.put(thread.getValue() + "/" + thread.getKey(), cpusAllowed(thread.getKey()));
               }
               else if (EVENT_THREAD.matcher(thread.getValue()).matches())
               {
                  events.put(thread.getValue() + "/" + thread.getKey(), cpusAllowed(thread.getKey()));
               }
            }
            applied = !reception.isEmpty() && !events.isEmpty() && reception.values().stream().allMatch(expectedReception::equals)
                      && events.values().stream().allMatch(expectedEvent::equals);
            if (!applied)
            {
               Thread.sleep(100);
            }
         }
         assertFalse(reception.isEmpty(), "The node started no reception thread");
         assertFalse(events.isEmpty(), "The node started no event thread");
         for (Map.Entry<String, String> thread : reception.entrySet())
         {
            assertEquals(expectedReception, thread.getValue(), () -> "Reception thread " + thread.getKey() + " ignores its thread settings: " + reception);
         }
         for (Map.Entry<String, String> thread : events.entrySet())
         {
            assertEquals(expectedEvent, thread.getValue(), () -> "Event thread " + thread.getKey() + " ignores its thread settings: " + events);
         }
      }
      finally
      {
         node.close();
      }
   }

   private static ROS2ThreadSettings threadSettings(int receptionCpu, int eventCpu)
   {
      return new ROS2ThreadSettings().stackSizeBytes(1048576)
                                     .forThreads(ROS2ThreadSettings.Kind.RECEPTION, new ROS2ThreadSettings().affinityMask(1L << receptionCpu))
                                     .forThreads(ROS2ThreadSettings.Kind.TIMED_EVENTS, new ROS2ThreadSettings().affinityMask(1L << eventCpu));
   }

   @Test
   @Timeout(120)
   public void testThreadSettingsReachTheNativeThreads() throws Exception
   {
      assumeTrue(Files.isDirectory(TASKS) && Files.exists(Paths.get("/proc/thread-self")), "needs Linux /proc");
      assumeTrue(!TransportDescriptorTypeTools.isAndroid(), "not on Android");
      int[] cpus = twoUsableCpus();
      assumeTrue(cpus != null, "needs two usable CPUs");

      // Builtin transports (SUBNET): only applied through the explicit <builtinTransports> written after the thread settings
      assertAffinityApplied(ROS2NodeOptions.defaults().discoveryRange(ROS2DiscoveryRange.SUBNET).threadSettings(threadSettings(cpus[0], cpus[1])),
                            cpus[0],
                            cpus[1]);
      // User transports (LOCALHOST: SHM + UDPv4 descriptors derived with default_reception_threads); swap the CPUs so a
      // descriptor reused from the previous case would be detected
      assertAffinityApplied(ROS2NodeOptions.defaults().discoveryRange(ROS2DiscoveryRange.LOCALHOST).threadSettings(threadSettings(cpus[1], cpus[0])),
                            cpus[1],
                            cpus[0]);
      // OFF keeps the builtin transports
      assertAffinityApplied(ROS2NodeOptions.defaults().discoveryRange(ROS2DiscoveryRange.OFF).threadSettings(threadSettings(cpus[0], cpus[0])), cpus[0], cpus[0]);
   }
}
