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
package us.ihmc.fastddsjava.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import us.ihmc.fastddsjava.fastddsjavaTestTools;
import us.ihmc.fastddsjava.natives.fastddsjava;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Loads the natives in separate JVMs ({@link NativeLoaderChildMain}): the extraction cache across and between
 * processes, {@code JROS2_NATIVE_PATH} direct loading, error messages and legacy cleanup. No DDS entities are created.
 */
public class fastddsjavaNativeLibraryProcessTest
{
   private static final long CHILD_TIMEOUT_SECONDS = 90;

   /** Result of one child JVM. */
   private static final class Child
   {
      final Process process;
      final ByteArrayOutputStream output = new ByteArrayOutputStream();
      final Thread pump;

      Child(Process process)
      {
         this.process = process;
         this.pump = new Thread(() ->
         {
            try (InputStream in = process.getInputStream())
            {
               byte[] buffer = new byte[8192];
               int read;
               while ((read = in.read(buffer)) >= 0)
               {
                  synchronized (output)
                  {
                     output.write(buffer, 0, read);
                  }
               }
            }
            catch (IOException ignored)
            {
               // Process ended.
            }
         });
         pump.start();
      }

      /** @return the child's last line starting with {@code prefix} (output is dumped in the failure message otherwise) */
      String await(String prefix) throws InterruptedException
      {
         assertTrue(process.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS), "child timed out");
         pump.join(10000);
         String text;
         synchronized (output)
         {
            text = new String(output.toByteArray(), StandardCharsets.UTF_8);
         }
         String found = null;
         for (String line : text.split("\\R"))
         {
            if (line.startsWith(prefix))
               found = line;
         }
         assertNotNull(found, "no '" + prefix + "' line from the child (exit " + process.exitValue() + "):\n" + text);
         return found;
      }
   }

   private static Child start(List<String> jvmOptions, Map<String, String> environment, String... arguments) throws IOException
   {
      List<String> command = new ArrayList<>();
      command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
      command.add("-Xmx128m");
      command.addAll(fastddsjavaTestTools.coverageAgentArguments());
      command.addAll(jvmOptions);
      command.add("-cp");
      command.add(System.getProperty("java.class.path"));
      command.add(NativeLoaderChildMain.class.getName());
      command.addAll(Arrays.asList(arguments));

      ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
      builder.environment().remove(fastddsjavaNativeLibrary.NATIVE_PATH_ENV);
      builder.environment().remove(fastddsjavaNativeLibrary.EXTRACT_DIR_ENV);
      builder.environment().putAll(environment);
      return new Child(builder.start());
   }

   private static List<String> options(String... options)
   {
      return Arrays.asList(options);
   }

   private static Map<String, String> noEnvironment()
   {
      return new HashMap<>();
   }

   /** This platform's bundled natives (manifest order), read from the classpath, which may be a jar or a directory. */
   private static Map<String, byte[]> bundledNatives() throws Exception
   {
      String platform = fastddsjavaNativeLibrary.detectPlatform();
      String directory = "/" + fastddsjavaNativeLibrary.RESOURCE_ROOT + platform + "/";
      URL manifest = fastddsjavaNativeLibrary.class.getResource(directory + fastddsjavaNativeLibrary.MANIFEST_NAME);
      assumeTrue(manifest != null, "no bundled natives for " + platform);
      List<String> names;
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(manifest.openStream(), StandardCharsets.UTF_8)))
      {
         names = fastddsjavaNativeLibrary.parseManifest(reader, manifest.toString());
      }
      Map<String, byte[]> natives = new LinkedHashMap<>();
      for (String name : names)
      {
         try (InputStream in = fastddsjavaNativeLibrary.class.getResourceAsStream(directory + name))
         {
            assertNotNull(in, name);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) >= 0)
               bytes.write(buffer, 0, read);
            natives.put(name, bytes.toByteArray());
         }
      }
      return natives;
   }

   /** Writes the natives and their manifest into {@code directory} (a directory usable with JROS2_NATIVE_PATH). */
   private static Path writeNativeDirectory(Path directory, Map<String, byte[]> natives) throws IOException
   {
      Files.createDirectories(directory);
      for (Map.Entry<String, byte[]> entry : natives.entrySet())
         Files.write(directory.resolve(entry.getKey()), entry.getValue());
      Files.write(directory.resolve(fastddsjavaNativeLibrary.MANIFEST_NAME), new ArrayList<>(natives.keySet()));
      return directory;
   }

   private static Path loadedDirectory(String loadedLine)
   {
      // LOADED <dir> ABI <n>
      String withoutPrefix = loadedLine.substring("LOADED ".length());
      return Paths.get(withoutPrefix.substring(0, withoutPrefix.lastIndexOf(" ABI ")));
   }

   private static Map<String, FileTime> modificationTimes(Path directory) throws IOException
   {
      Map<String, FileTime> times = new HashMap<>();
      try (Stream<Path> entries = Files.list(directory))
      {
         for (Path entry : entries.collect(Collectors.toList()))
            times.put(entry.getFileName().toString(), Files.getLastModifiedTime(entry));
      }
      return times;
   }

   private static Set<String> listNames(Path directory) throws IOException
   {
      try (Stream<Path> entries = Files.list(directory))
      {
         return entries.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
      }
   }

   @Test
   @Timeout(300)
   public void extractionCacheIsReusedByLaterProcesses(@TempDir Path base) throws Exception
   {
      Map<String, byte[]> bundled = bundledNatives();
      List<String> jvm = options("-D" + fastddsjavaNativeLibrary.EXTRACT_DIR_PROPERTY + "=" + base);

      Path first = loadedDirectory(start(jvm, noEnvironment(), "load").await("LOADED "));
      assertTrue(first.startsWith(base), first.toString());
      Map<String, FileTime> times = modificationTimes(first);
      for (Map.Entry<String, byte[]> entry : bundled.entrySet())
         assertArrayEquals(entry.getValue(), Files.readAllBytes(first.resolve(entry.getKey())), entry.getKey());

      Thread.sleep(50);
      Path second = loadedDirectory(start(jvm, noEnvironment(), "load").await("LOADED "));
      assertEquals(first, second, "the second process reuses the cache entry");
      assertEquals(times, modificationTimes(second), "nothing is extracted again");
   }

   @Test
   @Timeout(300)
   public void concurrentProcessesLoadFromOneExtraction(@TempDir Path base) throws Exception
   {
      Map<String, byte[]> bundled = bundledNatives();
      List<String> jvm = options("-D" + fastddsjavaNativeLibrary.EXTRACT_DIR_PROPERTY + "=" + base);
      List<Child> children = new ArrayList<>();
      for (int i = 0; i < 3; i++)
         children.add(start(jvm, noEnvironment(), "load"));

      Set<Path> directories = new HashSet<>();
      for (Child child : children)
         directories.add(loadedDirectory(child.await("LOADED ")));
      assertEquals(1, directories.size(), "all processes use the same cache entry");

      Path directory = directories.iterator().next();
      Set<String> expected = new HashSet<>(bundled.keySet());
      expected.add(fastddsjavaNativeLibrary.LOCK_FILE_NAME);
      assertEquals(expected, listNames(directory), "exactly the libraries, no temporary files");
      for (Map.Entry<String, byte[]> entry : bundled.entrySet())
         assertArrayEquals(entry.getValue(), Files.readAllBytes(directory.resolve(entry.getKey())), entry.getKey());
   }

   @Test
   @Timeout(300)
   public void concurrentProcessesExtractExactlyOnce(@TempDir Path base) throws Exception
   {
      // Slow fake libraries make the processes overlap inside the extraction.
      List<Child> children = new ArrayList<>();
      for (int i = 0; i < 3; i++)
         children.add(start(options(), noEnvironment(), "extract", base.toString(), "3"));

      Set<String> directories = new HashSet<>();
      int opens = 0;
      for (Child child : children)
      {
         String[] parts = child.await("EXTRACTED ").split(" ");
         directories.add(parts[1]);
         opens += Integer.parseInt(parts[2]);
         assertEquals("true", parts[3], "content verified by the child");
      }
      assertEquals(1, directories.size());
      assertEquals(3, opens, "the three libraries are written by exactly one process");

      InMemoryNativeBundle expected = InMemoryNativeBundle.deterministic(NativeLoaderChildMain.TEST_PLATFORM, NativeLoaderChildMain.TEST_LIBRARY_SIZE,
                                                                         NativeLoaderChildMain.TEST_SEED, 0);
      Path directory = Paths.get(directories.iterator().next());
      assertTrue(expected.matches(directory));
      Set<String> names = new HashSet<>(expected.names);
      names.add(fastddsjavaNativeLibrary.LOCK_FILE_NAME);
      assertEquals(names, listNames(directory));
   }

   @Test
   @Timeout(300)
   public void nativePathLoadsDirectlyWithoutExtracting(@TempDir Path scratch) throws Exception
   {
      Path bundled = writeNativeDirectory(scratch.resolve("natives"), bundledNatives());
      Path temporary = Files.createDirectory(scratch.resolve("tmp"));
      Path extractBase = Files.createDirectory(scratch.resolve("extract"));
      List<String> jvm = options("-Djava.io.tmpdir=" + temporary, "-D" + fastddsjavaNativeLibrary.EXTRACT_DIR_PROPERTY + "=" + extractBase);

      // Environment variable.
      Map<String, String> environment = new HashMap<>();
      environment.put(fastddsjavaNativeLibrary.NATIVE_PATH_ENV, bundled.toString());
      assertEquals(bundled, loadedDirectory(start(jvm, environment, "load").await("LOADED ")));

      // The system property wins over the environment variable.
      environment.put(fastddsjavaNativeLibrary.NATIVE_PATH_ENV, scratch.resolve("does-not-exist").toString());
      List<String> withProperty = new ArrayList<>(jvm);
      withProperty.add("-D" + fastddsjavaNativeLibrary.NATIVE_PATH_PROPERTY + "=" + bundled);
      assertEquals(bundled, loadedDirectory(start(withProperty, environment, "load").await("LOADED ")));

      try (Stream<Path> extracted = Files.list(extractBase))
      {
         assertEquals(0, extracted.count(), "direct mode extracts nothing");
      }
   }

   @Test
   @Timeout(300)
   public void nativePathErrorsNameTheDirectoryAndLibrary(@TempDir Path scratch) throws Exception
   {
      Path missing = scratch.resolve("no-such-directory");
      String failure = start(options("-D" + fastddsjavaNativeLibrary.NATIVE_PATH_PROPERTY + "=" + missing), noEnvironment(), "load").await("FAILED ");
      assertTrue(failure.contains(missing.toString()), failure);

      Path incomplete = Files.createDirectory(scratch.resolve("incomplete"));
      Files.write(incomplete.resolve(fastddsjavaNativeLibrary.MANIFEST_NAME), Arrays.asList("libpresent.so", "libabsent.so"));
      Files.write(incomplete.resolve("libpresent.so"), new byte[] {0});
      failure = start(options("-D" + fastddsjavaNativeLibrary.NATIVE_PATH_PROPERTY + "=" + incomplete), noEnvironment(), "load").await("FAILED ");
      assertTrue(failure.contains("libabsent.so") && failure.contains(incomplete.toString()), failure);
   }

   @Test
   @Timeout(300)
   public void platformWithoutBundledNativesFailsWithAClearMessage(@TempDir Path base) throws Exception
   {
      // A platform directory that is not on the classpath (forced with the platform override).
      String platform = "linux-ppc64le";
      String failure = start(options("-D" + fastddsjavaNativeLibrary.PLATFORM_PROPERTY + "=" + platform,
                                     "-D" + fastddsjavaNativeLibrary.EXTRACT_DIR_PROPERTY + "=" + base), noEnvironment(), "load").await("FAILED ");
      assertTrue(failure.contains(platform), failure);
      assertTrue(failure.contains(fastddsjavaNativeLibrary.MANIFEST_NAME), failure);
   }

   @Test
   @Timeout(300)
   public void nativePathWithoutManifestAcceptsTheSonameLayout(@TempDir Path scratch) throws Exception
   {
      String platform = fastddsjavaNativeLibrary.detectPlatform();
      assumeTrue(platform.startsWith("linux-"), "SONAME layout is a Linux convention");
      Map<String, byte[]> bundled = bundledNatives();
      List<String> sonames = fastddsjavaNativeLibrary.sonameLibraryNames(platform);
      assertEquals(bundled.size(), sonames.size());

      // The bundled libraries under their SONAMEs and without natives.list, as in a Fast-DDS install's lib/ directory.
      Path directory = Files.createDirectory(scratch.resolve("soname-layout"));
      int i = 0;
      for (byte[] content : bundled.values())
         Files.write(directory.resolve(sonames.get(i++)), content);
      assertEquals(directory, loadedDirectory(start(options("-D" + fastddsjavaNativeLibrary.NATIVE_PATH_PROPERTY + "=" + directory), noEnvironment(), "load")
                                                    .await("LOADED ")));
   }

   @Test
   @Timeout(300)
   public void bundledLibrariesWithAMissingFileFailWithAClearMessage(@TempDir Path scratch) throws Exception
   {
      // Direct mode without a manifest falls back to the platform's historical names; a missing one is named.
      Path directory = Files.createDirectory(scratch.resolve("partial"));
      Map<String, byte[]> bundled = bundledNatives();
      String first = bundled.keySet().iterator().next();
      Files.write(directory.resolve(first), bundled.get(first));
      String failure = start(options("-D" + fastddsjavaNativeLibrary.NATIVE_PATH_PROPERTY + "=" + directory), noEnvironment(), "load").await("FAILED ");
      assertTrue(failure.contains(directory.toString()), failure);
      assertTrue(failure.contains("is missing"), failure);
   }

   @Test
   @Timeout(300)
   public void abiQueriedBeforeLoadingIsNotPinnedToV1(@TempDir Path base) throws Exception
   {
      // Code that runs before the natives are loaded (static initializers, settings checks) may ask for the ABI or for
      // DDS-Security support. That early answer must not be cached, or v2 natives loaded later would be treated as v1
      // (and SROS2 as unsupported) for the rest of the process.
      assertTrue(fastddsjavaNativeLibrary.load(), "natives must load in the test JVM");
      List<String> jvm = new ArrayList<>(options("-D" + fastddsjavaNativeLibrary.EXTRACT_DIR_PROPERTY + "=" + base));
      Map<String, String> environment = new HashMap<>();
      String property = System.getProperty(fastddsjavaNativeLibrary.NATIVE_PATH_PROPERTY);
      String variable = System.getenv(fastddsjavaNativeLibrary.NATIVE_PATH_ENV);
      if (property != null)
         jvm.add("-D" + fastddsjavaNativeLibrary.NATIVE_PATH_PROPERTY + "=" + property);
      else if (variable != null)
         environment.put(fastddsjavaNativeLibrary.NATIVE_PATH_ENV, variable); // the same natives as this JVM

      Child child = start(jvm, environment, "early");
      String[] early = child.await("EARLY ").split(" ");
      String[] after = child.await("AFTER ").split(" ");
      assertEquals(Integer.toString(fastddsjava.ABI_V1), early[1], "nothing is loaded yet");
      assertEquals("false", early[2]);
      assertEquals(Integer.toString(fastddsjava.abiVersion()), after[1], "the ABI of the natives loaded afterward");
      assertEquals(Boolean.toString(fastddsjava.hasSecurity()), after[2], "the DDS-Security support of the natives loaded afterward");
   }

   /**
    * The JVM binds a native library file to one class loader, so a second copy of the jros2 classes in another class loader
    * cannot load the cached natives the first copy loaded: it gets its own private copy, and both work.
    */
   @Test
   @Timeout(300)
   public void aSecondClassLoaderLoadsItsOwnCopy(@TempDir Path base) throws Exception
   {
      bundledNatives();
      String[] two = start(options("-D" + fastddsjavaNativeLibrary.EXTRACT_DIR_PROPERTY + "=" + base), noEnvironment(), "twoLoaders").await("TWO ").split(" ");
      assertEquals("true", two[1], "the second class loader loads the natives");
      Path first = Paths.get(two[2]);
      Path second = Paths.get(two[3]);
      assertTrue(first.startsWith(base), first.toString());
      assertNotEquals(first, second, "the second class loader needs its own copy");
      assertEquals(two[4], two[5], "both copies report the same native ABI");
      assertTrue(Integer.parseInt(two[5]) >= fastddsjava.ABI_V1);
   }

   @Test
   @Timeout(300)
   public void loadingRemovesStaleLegacyTemporaryDirectories(@TempDir Path scratch) throws Exception
   {
      bundledNatives();
      Path temporary = Files.createDirectory(scratch.resolve("tmp"));
      Path extractBase = Files.createDirectory(scratch.resolve("extract"));
      Path stale = Files.createDirectory(temporary.resolve(fastddsjavaNativeLibrary.LEGACY_TEMP_DIR_PREFIX + "987654321"));
      Files.write(stale.resolve("libfastdds.so.3.6.2"), new byte[1024]);
      Files.setLastModifiedTime(stale, FileTime.fromMillis(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)));

      start(options("-Djava.io.tmpdir=" + temporary, "-D" + fastddsjavaNativeLibrary.EXTRACT_DIR_PROPERTY + "=" + extractBase), noEnvironment(), "load")
            .await("LOADED ");
      assertFalse(Files.exists(stale), "the crashed legacy extraction directory is removed");
   }
}
