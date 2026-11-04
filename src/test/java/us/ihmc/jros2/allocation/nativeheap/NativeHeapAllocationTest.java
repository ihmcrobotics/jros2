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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Native heap gate: no malloc / calloc / realloc / posix_memalign / aligned_alloc / memalign / valloc / operator new,
 * no direct mmap, and no free / munmap, on any hot-path thread in steady state, in the JNI layer, inside Fast-DDS and in
 * the JVM; and no Java object created by JNI code.
 * <p>
 * Builds the {@code alloccount} LD_PRELOAD shim ({@code src/test/native/alloccount/alloccount.c}) with the host C
 * compiler (skipped with a message when there is none, or when the platform is not Linux/glibc), then runs
 * {@link NativeHeapAllocationChild} in a separate JVM per configuration: intraprocess delivery OFF with jros2's default
 * transports (shared memory with a 4 MiB segment plus UDPv4, and messages that overflow the 512 KiB segment of a node with
 * Fast-DDS's default transports), OFF with custom shared memory (an 8500 KiB segment) plus UDPv4, OFF with UDPv4 only (the
 * inter-host path), and FULL with the default transports; each with the ABI v2 natives and with the forced ABI v1 path. See the
 * child for the windows and how calls are attributed. The test
 * fails on any native heap call on a hot-path thread made by the JNI layer, Fast-DDS or Fast-CDR (also through libstdc++
 * or a JNI function), by a JVM / JDK native method that Java code called, by HotSpot monitor inflation (contended
 * synchronized), or by unclassified JVM-internal code, in ANY attempt (an attempt is repeated only when Fast-DDS
 * processed discovery data in it, e.g. a participant joining the domain; the window fails if no attempt was
 * undisturbed); on Java heap bytes allocated by JNI calls; and when the shim lost track of a thread or allocated itself.
 * Purely JVM-internal calls of the JIT (deoptimization, on-stack replacement, compile requests), of handshakes and of the
 * garbage collector's barrier buffers are counted and reported separately ("NHA JVMINTERNAL" summary lines), not gated.
 * Set
 * {@code JROS2_NATIVE_SYMBOL_DIRS} to directories with unstripped copies of the natives (same build ID) for function
 * names in the stacks of Fast-DDS and the JNI layer.
 */
@Tag("allocation")
public class NativeHeapAllocationTest
{
   private static final String SOURCE = "native/alloccount/alloccount.c";
   private static final long CHILD_TIMEOUT_MINUTES = 10;
   private static final int EXPECTED_MATRIX_WINDOWS = 24;
   private static final String MATRIX_WINDOW = "(rel|be)-(joints|image)-(sync|async)-(inline|executor|polling)";
   /** Windows that must receive messages in every attempt: the matrix, fan-out / fan-in, soak and slow-reader windows. */
   private static final String RECEIVING_WINDOW = MATRIX_WINDOW + "|fan-joints|fan-image|soak-reliable|slow-reader";
   /**
    * Extra JVM options for the child JVMs (whitespace separated), e.g. {@code -XX:-DoEscapeAnalysis},
    * {@code -XX:TieredStopAtLevel=1} or {@code -Xint}: the gate must pass in every JIT configuration. JIT options of this
    * JVM ({@code -Xint}, {@code -Xcomp}, {@code -XX:...}) are passed on to the children as well.
    */
   static final String CHILD_JVM_ARGS_ENV = "JROS2_NHA_CHILD_JVM_ARGS";
   /** Window selection for development ({@code nha.only} system property or this environment variable); see the child. */
   static final String ONLY_ENV = "JROS2_NHA_ONLY";
   /** Child timeout in minutes (default {@value #CHILD_TIMEOUT_MINUTES}), e.g. for {@code -Xint}. */
   static final String CHILD_TIMEOUT_ENV = "JROS2_NHA_CHILD_TIMEOUT_MINUTES";

   private static Path shim;
   private static String skipReason;

   @BeforeAll
   public static void buildShim() throws Exception
   {
      // The child JVMs set the intraprocess mode and the native ABI themselves and run without the instrumenter agent, so
      // the gate runs once: not again in allocation test tasks that only change these for the test JVM itself.
      String mode = System.getProperty("jros2.allocation.mode", "instrumented");
      if (!mode.equals("instrumented") || Boolean.parseBoolean(System.getProperty("fastdds.intraprocess.delivery", "false")))
      {
         skipReason = "runs in the allocation test task with the instrumenter and intraprocess delivery OFF (jros2.allocation.mode=" + mode
                      + ", fastdds.intraprocess.delivery=" + System.getProperty("fastdds.intraprocess.delivery") + "); its child JVMs cover both modes";
         return;
      }
      String[] reason = new String[1];
      shim = compileShim(reason);
      skipReason = reason[0];
   }

   /**
    * Builds the alloccount LD_PRELOAD shim with the host C compiler.
    *
    * @param skipReason receives, in element 0, why the shim cannot be used here (then null is returned)
    * @return the shared library, or null
    */
   static Path compileShim(String[] skipReason) throws Exception
   {
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      if (!os.contains("linux"))
      {
         skipReason[0] = "the alloccount LD_PRELOAD shim needs Linux (os.name=" + os + ")";
         return null;
      }
      if (Files.exists(Paths.get("/etc/alpine-release")))
      {
         skipReason[0] = "the alloccount shim needs glibc (backtrace, RTLD_NEXT interposition); this is musl";
         return null;
      }
      Path source = findSource();
      if (source == null)
      {
         skipReason[0] = "alloccount.c not found from " + System.getProperty("user.dir");
         return null;
      }
      String compiler = System.getenv().getOrDefault("CC", "cc");
      Path directory = Files.createTempDirectory("jros2-alloccount");
      Path library = directory.resolve("liballoccount.so");
      List<String> command = List.of(compiler, "-shared", "-fPIC", "-O2", "-Wall", "-o", library.toString(), source.toString(), "-ldl", "-lpthread");
      Process process;
      try
      {
         process = new ProcessBuilder(command).redirectErrorStream(true).start();
      }
      catch (IOException e)
      {
         skipReason[0] = "no C compiler to build the alloccount shim ('" + compiler + "': " + e.getMessage() + "); set CC or install cc";
         return null;
      }
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (!process.waitFor(2, TimeUnit.MINUTES) || process.exitValue() != 0)
         fail("building the alloccount shim failed: " + String.join(" ", command) + "\n" + output);
      return library;
   }

   /**
    * JVM options of the child JVMs that change JIT compilation: those of this JVM ({@code -Xint}, {@code -Xcomp},
    * {@code -Xmixed}, {@code -XX:...}) and those of the environment variable {@value #CHILD_JVM_ARGS_ENV}.
    */
   static List<String> childJitArguments()
   {
      List<String> arguments = new ArrayList<>();
      for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments())
      {
         if (argument.equals("-Xint") || argument.equals("-Xcomp") || argument.equals("-Xmixed") || argument.startsWith("-XX:"))
            arguments.add(argument);
      }
      String extra = System.getenv(CHILD_JVM_ARGS_ENV);
      if (extra != null)
      {
         for (String argument : extra.trim().split("\\s+"))
         {
            if (!argument.isEmpty())
               arguments.add(argument);
         }
      }
      return arguments;
   }

   private static Path findSource()
   {
      Path directory = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
      for (int i = 0; i < 6 && directory != null; ++i)
      {
         for (String candidate : new String[] {SOURCE, "src/test/" + SOURCE})
         {
            Path path = directory.resolve(candidate);
            if (Files.isRegularFile(path))
               return path;
         }
         directory = directory.getParent();
      }
      return null;
   }

   @Test
   public void testIntraprocessOffDefaultTransportsNativeAbiV2() throws Exception
   {
      runChild(false, "default", false);
   }

   @Test
   public void testIntraprocessOffSharedMemoryNativeAbiV2() throws Exception
   {
      runChild(false, "shm", false);
   }

   @Test
   public void testIntraprocessOffUdpNativeAbiV2() throws Exception
   {
      runChild(false, "udp", false);
   }

   @Test
   public void testIntraprocessFullNativeAbiV2() throws Exception
   {
      runChild(true, "default", false);
   }

   @Test
   public void testIntraprocessOffDefaultTransportsForcedNativeAbiV1() throws Exception
   {
      runChild(false, "default", true);
   }

   @Test
   public void testIntraprocessOffSharedMemoryForcedNativeAbiV1() throws Exception
   {
      runChild(false, "shm", true);
   }

   @Test
   public void testIntraprocessOffUdpForcedNativeAbiV1() throws Exception
   {
      runChild(false, "udp", true);
   }

   @Test
   public void testIntraprocessFullForcedNativeAbiV1() throws Exception
   {
      runChild(true, "default", true);
   }

   private static void runChild(boolean intraprocess, String transport, boolean forceAbiV1) throws Exception
   {
      Assumptions.assumeTrue(skipReason == null, () -> "native heap gate skipped: " + skipReason);
      String label = "intraprocess " + (intraprocess ? "FULL" : "OFF") + ", transports " + transport + ", " + (forceAbiV1 ? "forced ABI v1" : "ABI v2");

      List<String> jitArguments = childJitArguments();
      if (!jitArguments.isEmpty())
         label += ", JVM " + String.join(" ", jitArguments);
      String only = System.getProperty("nha.only", System.getenv().getOrDefault(ONLY_ENV, ""));

      Path countsFile = Files.createTempFile("jros2-alloccount", ".bin");
      List<String> command = new ArrayList<>();
      command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
      command.add("-Xms1g");
      command.add("-Xmx1g");
      command.addAll(jitArguments);
      command.add("-D" + NativeAllocationCounter.FILE_PROPERTY + "=" + countsFile);
      command.add("-Djros2.native.forceAbiV1=" + forceAbiV1);
      command.add("-D" + NativeHeapAllocationChild.TRANSPORT_PROPERTY + "=" + transport);
      String symbolDirs = System.getenv("JROS2_NATIVE_SYMBOL_DIRS");
      if (symbolDirs != null)
         command.add("-D" + NativeStackResolver.SYMBOL_DIRS_PROPERTY + "=" + symbolDirs);
      if (!only.isEmpty())
         command.add("-Dnha.only=" + only);
      command.add("-cp");
      command.add(System.getProperty("java.class.path"));
      command.add(NativeHeapAllocationChild.class.getName());

      ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
      Map<String, String> environment = builder.environment();
      String preload = environment.get("LD_PRELOAD");
      environment.put("LD_PRELOAD", preload == null || preload.isEmpty() ? shim.toString() : shim + ":" + preload);
      environment.put(NativeAllocationCounter.FILE_ENV, countsFile.toString());
      environment.put("FASTDDS_INTRAPROCESS_DELIVERY", Boolean.toString(intraprocess));

      Path log = Files.createTempFile("jros2-alloccount-child", ".log");
      List<String> lines;
      try
      {
         builder.redirectOutput(log.toFile());
         Process process = builder.start();
         long timeoutMinutes = Long.parseLong(System.getenv().getOrDefault(CHILD_TIMEOUT_ENV, Long.toString(CHILD_TIMEOUT_MINUTES)));
         if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES))
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

      // The full transcript (every stack) goes to the test output; failure messages carry the results and library stacks.
      StringBuilder digest = new StringBuilder();
      for (String line : lines)
      {
         System.out.println("[" + label + "] " + line);
         if (!line.startsWith(NativeHeapAllocationChild.PREFIX + "STACK ") && !line.startsWith(NativeHeapAllocationChild.PREFIX + "THREAD ")
             && !line.startsWith(NativeHeapAllocationChild.PREFIX + "JAVA "))
            digest.append(line).append('\n');
      }
      String context = label + " (full transcript in the test output):\n" + digest;

      assertTrue(lines.stream().anyMatch(line -> line.startsWith(NativeHeapAllocationChild.PREFIX + "DONE")), "the child did not finish; " + context);

      // The shim must see a native allocation of the measured thread (ByteBuffer.allocateDirect -> malloc), and not allocate itself.
      String selfTest = lines.stream().filter(line -> line.startsWith(NativeHeapAllocationChild.PREFIX + "SELFTEST")).findFirst().orElse("");
      assertTrue(selfTest.contains("tidMatched=true") && selfTest.contains("sawRequestedSize=true") && !selfTest.contains("mallocs=0")
                 && selfTest.contains("shimInternalCalls=0"), "alloccount self test failed: '" + selfTest + "'; " + context);

      List<String> failures = new ArrayList<>();
      List<String> libraryStacks = new ArrayList<>();
      int matrixWindows = 0;
      Map<String, Map<String, String>> results = new HashMap<>();
      for (String line : lines)
      {
         if (line.startsWith(NativeHeapAllocationChild.PREFIX + "STACK ") && (line.contains(" library | ") || line.contains(" java-native | ")
                                                                                  || line.contains(" jvm-monitor | ") || line.contains(" jvm-internal | "))
             && isHotPathStack(line, NativeHeapAllocationChild::classify))
            libraryStacks.add(line);
         if (line.startsWith(NativeHeapAllocationChild.PREFIX + "WINDOW ") && line.contains(" received=0 ") && line.matches(".*WINDOW (" + RECEIVING_WINDOW + ")#.*"))
            failures.add("nothing was received: " + line);
         if (!line.startsWith(NativeHeapAllocationChild.PREFIX + "RESULT "))
            continue;
         String[] fields = line.substring((NativeHeapAllocationChild.PREFIX + "RESULT ").length()).split(" ");
         Map<String, String> values = new HashMap<>();
         for (int i = 1; i < fields.length; ++i)
         {
            String[] keyValue = fields[i].split("=", 2);
            values.put(keyValue[0], keyValue.length > 1 ? keyValue[1] : "");
         }
         results.put(fields[0], values);
         if (fields[0].matches(MATRIX_WINDOW))
            matrixWindows++;
         if (!"true".equals(values.get("gated")))
            continue;
         if (!"0".equals(values.get("hotPathCalls")))
            failures.add(fields[0] + ": " + values.get("hotPathCalls") + " native heap calls from the JNI layer / Fast-DDS on hot-path threads in "
                         + values.get("attempts") + " attempt(s)");
         if (!"0".equals(values.get("javaNativeCalls")))
            failures.add(fields[0] + ": " + values.get("javaNativeCalls") + " native heap calls of JVM / JDK native methods called by Java code on hot-path threads");
         if (!"0".equals(values.getOrDefault("monitorInflationCalls", "")))
            failures.add(fields[0] + ": " + values.get("monitorInflationCalls")
                         + " native heap calls of HotSpot monitor inflation on hot-path threads (a contended synchronized block or Object.wait)");
         if (!"0".equals(values.get("hotPathJvmInternalCalls")))
            failures.add(fields[0] + ": " + values.get("hotPathJvmInternalCalls") + " unclassified JVM-internal native heap calls on hot-path threads in "
                         + values.get("attempts") + " attempt(s)");
         if (!"0".equals(values.get("droppedThreads")) || !"0".equals(values.get("unslottedCalls")))
            failures.add(fields[0] + ": the shim ran out of thread slots (" + values.get("droppedThreads") + " threads, " + values.get("unslottedCalls")
                         + " calls not attributed to a thread)");
         if (!"0".equals(values.get("shimInternalCalls")))
            failures.add(fields[0] + ": the shim itself made " + values.get("shimInternalCalls") + " heap calls");
         long jniJavaBytes = Long.parseLong(values.getOrDefault("jniJavaBytes", "-1"));
         if (jniJavaBytes > 0)
            failures.add(fields[0] + ": JNI calls allocated " + jniJavaBytes + " Java heap bytes");
      }

      // Every selected window ran, and was gated (the selection is everything unless nha.only is set for development).
      int expectedMatrixWindows = 0;
      boolean smallMessageWindow = false;
      for (String name : NativeHeapAllocationChild.matrixWindowNames())
      {
         if (NativeHeapAllocationChild.selectedMatrixWindow(only, name))
         {
            expectedMatrixWindows++;
            smallMessageWindow |= name.contains("-joints-");
         }
      }
      if (only.isEmpty())
         assertEquals(EXPECTED_MATRIX_WINDOWS, expectedMatrixWindows, "matrix definition");
      assertEquals(expectedMatrixWindows, matrixWindows, "hot-path matrix windows reported; " + context);
      List<String> expectedWindows = new ArrayList<>();
      if (NativeHeapAllocationChild.selected(only, "raw"))
         expectedWindows.add("raw-jni");
      if (NativeHeapAllocationChild.selected(only, "combined") && smallMessageWindow)
         expectedWindows.add("combined-concurrent");
      if (NativeHeapAllocationChild.selected(only, "unmatched"))
         expectedWindows.add("unmatched-publish");
      if (NativeHeapAllocationChild.selected(only, "fan"))
      {
         expectedWindows.add("fan-joints");
         expectedWindows.add("fan-image");
      }
      if (NativeHeapAllocationChild.selected(only, "soak"))
         expectedWindows.add("soak-reliable");
      if (NativeHeapAllocationChild.selected(only, "slow"))
         expectedWindows.add("slow-reader");
      if (NativeHeapAllocationChild.selected(only, "quiet"))
         expectedWindows.add("quiet-background");
      if (!intraprocess && transport.equals("default") && NativeHeapAllocationChild.selected(only, "overflow"))
      {
         for (String overflow : new String[] {"rel", "be"})
            expectedWindows.add(NativeHeapAllocationChild.OVERFLOW_PREFIX + overflow + "-image-sync-inline");
      }
      for (String window : expectedWindows)
      {
         assertTrue(results.containsKey(window), window + " window missing; " + context);
         assertEquals("true", results.get(window).get("gated"), window + " window not gated; " + context);
      }
      for (Map<String, String> values : results.values())
         assertEquals("true", values.get("gated"), "every window is gated; " + context);
      if (expectedWindows.contains("raw-jni"))
         assertEquals("0", results.get("raw-jni").get("jniJavaBytes"), "raw JNI loop: Java heap bytes allocated by JNI; " + context);

      // The configuration is in effect: the library uses the requested native ABI path and intraprocess mode, and inline
      // callbacks run where that mode delivers them (FULL: on the publishing thread; OFF: on a Fast-DDS thread).
      String config = lines.stream().filter(line -> line.startsWith(NativeHeapAllocationChild.PREFIX + "CONFIG ")).findFirst().orElse("");
      assertTrue(config.contains(" intraprocessDelivery=" + (intraprocess ? "FULL" : "OFF") + " "), "intraprocess mode not in effect: '" + config + "'; " + context);
      assertTrue(config.contains(" nativeAbiV2=" + !forceAbiV1 + " "), "native ABI path not in effect: '" + config + "'; " + context);
      for (String line : lines)
      {
         if (!line.startsWith(NativeHeapAllocationChild.PREFIX + "CALLBACK "))
            continue;
         String[] fields = line.split(" ");
         String window = fields[2];
         String thread = fields[3].substring("thread=".length());
         String expected;
         if (window.endsWith("-executor"))
            expected = "hp-exec";
         else if (intraprocess)
            expected = window.contains("-async-") ? "hp-async-pub" : "hp-pub";
         else
            expected = "fastdds-callback";
         assertEquals(expected, thread, "thread of the callbacks of " + window + "; " + context);
      }

      printJvmInternalSummary(label, lines);

      if (!failures.isEmpty())
      {
         StringBuilder message = new StringBuilder(label + ": " + failures.size() + " native allocation violation(s):\n  " + String.join("\n  ", failures));
         message.append("\n\ngated stacks on hot-path threads (first 40):\n");
         for (int i = 0; i < Math.min(40, libraryStacks.size()); ++i)
            message.append("  ").append(libraryStacks.get(i)).append('\n');
         fail(message + "\n" + context);
      }
   }


   /**
    * Prints, per category and cause, the purely JVM-internal native heap calls on hot-path threads (jvm-jit, jvm-handshake,
    * jvm-gc: reported, not gated): "NHA-SUMMARY JVMINTERNAL &lt;label&gt; &lt;category&gt; calls=.. stacks=.. cause=&lt;first
    * JVM frame above the allocator&gt;".
    */
   static void printJvmInternalSummary(String label, List<String> lines)
   {
      printJvmInternalSummary(label, lines, NativeHeapAllocationChild::classify);
   }

   static void printJvmInternalSummary(String label, List<String> lines, java.util.function.Function<String, String> classifier)
   {
      Map<String, long[]> causes = new java.util.TreeMap<>();
      for (String line : lines)
      {
         if (!line.startsWith(NativeHeapAllocationChild.PREFIX + "STACK "))
            continue;
         String[] fields = line.split(" ");
         // NHA STACK <window#attempt> <tid> <thread> <kind> size=.. count=.. <category> | frames
         if (fields.length < 9 || classifier.apply(fields[4]).equals("jvm"))
            continue;
         String category = fields[8];
         if (!category.equals("jvm-jit") && !category.equals("jvm-handshake") && !category.equals("jvm-gc"))
            continue;
         long count = Long.parseLong(fields[7].substring("count=".length()));
         String cause = "?";
         for (String frame : line.split(" \\| "))
         {
            if (frame.startsWith("libjvm.so!") && !frame.contains("os::malloc") && !frame.contains("os::free") && !frame.contains("AllocateHeap")
                && !frame.contains("FreeHeap") && !frame.contains("os::realloc") && !frame.contains("ReallocateHeap") && !frame.contains("Arena::")
                && !frame.contains("ChunkPool") && !frame.contains("Chunk::"))
            {
               cause = frame.substring("libjvm.so!".length()).replaceAll("\\(.*", "").replace(' ', '_');
               break;
            }
         }
         long[] totals = causes.computeIfAbsent(category + " " + cause, key -> new long[2]);
         totals[0] += count;
         totals[1]++;
      }
      for (Map.Entry<String, long[]> entry : causes.entrySet())
      {
         String[] key = entry.getKey().split(" ", 2);
         System.out.println("NHA-SUMMARY JVMINTERNAL " + label.replace(' ', '_') + " " + key[0] + " calls=" + entry.getValue()[0] + " stacks=" + entry.getValue()[1]
                            + " cause=" + key[1]);
      }
   }

   /** @return true if a "NHA STACK" line is of a hot-path thread (the classifier does not call its thread "jvm") */
   static boolean isHotPathStack(String stackLine, java.util.function.Function<String, String> classifier)
   {
      String[] fields = stackLine.substring(stackLine.indexOf(NativeHeapAllocationChild.PREFIX + "STACK ")).split(" ");
      return fields.length > 4 && !classifier.apply(fields[4]).equals("jvm");
   }
}
