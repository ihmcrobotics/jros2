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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests of the native library loader: platform detection, manifests, the extraction cache and legacy cleanup.
 * Loading in other JVMs is covered by {@link fastddsjavaNativeLibraryProcessTest}.
 */
public class fastddsjavaNativeLibraryTest
{
   private static final String[] ALL_PLATFORMS = {"linux-x86_64", "linux-arm64", "linux-armhf", "linux-riscv64", "linux-musl-x86_64", "linux-musl-arm64",
                                                   "macos-arm64", "macos-x86_64", "windows-x86_64", "windows-arm64"};
   private static final String[] COMMITTED_PLATFORMS = {"linux-x86_64", "linux-arm64", "linux-armhf", "macos-arm64", "macos-x86_64", "windows-x86_64",
                                                         "windows-arm64"};

   private static List<String> parse(String manifest) throws IOException
   {
      return fastddsjavaNativeLibrary.parseManifest(new BufferedReader(new StringReader(manifest)), "test/natives.list");
   }

   private static boolean posix(Path path)
   {
      return Files.getFileAttributeView(path, PosixFileAttributeView.class) != null;
   }

   private static Path cacheRoot(Path base)
   {
      return base.resolve(fastddsjavaNativeLibrary.CACHE_ROOT_PREFIX + fastddsjavaNativeLibrary.sanitize(System.getProperty("user.name", "user")));
   }

   /* ---------------------------------------------------------------------------------------------------------- */

   @Test
   public void manifestListsNamesInLoadOrder() throws IOException
   {
      assertEquals(Arrays.asList("libfastcdr.so.2", "libfastdds.so.3.6", "libjnifastddsjava.so"),
                   parse("# comment\n\nlibfastcdr.so.2\n   libfastdds.so.3.6   \r\n\t\n#libignored.so\nlibjnifastddsjava.so"));
      assertEquals(Arrays.asList("fastcdr-2.3.dll", "jnifastddsjava.dll"), parse("\uFEFFfastcdr-2.3.dll\r\njnifastddsjava.dll\r\n"));
   }

   @Test
   public void manifestRejectsUnsafeDuplicateOrMissingNames()
   {
      String[] invalid = {"../evil.so", "/usr/lib/libc.so", "dir/lib.so", "dir\\lib.dll", ".hidden.so", "C:lib.dll", "lib\u0001.so", "a.so\na.so", "# only a comment\n\n",
                          ""};
      for (String manifest : invalid)
      {
         IOException e = assertThrows(IOException.class, () -> parse(manifest), manifest);
         assertTrue(e.getMessage().contains("test/natives.list"), "the error names the manifest: " + e.getMessage());
      }
   }

   @Test
   public void platformDirectoriesForEverySupportedPlatform() throws IOException
   {
      Object[][] cases = {{"Linux", "amd64", false, "linux-x86_64"},
                          {"Linux", "x86_64", false, "linux-x86_64"},
                          {"Linux", "aarch64", false, "linux-arm64"},
                          {"Linux", "arm64", false, "linux-arm64"},
                          {"Linux", "arm", false, "linux-armhf"},
                          {"Linux", "armv7l", false, "linux-armhf"},
                          {"Linux", "riscv64", false, "linux-riscv64"},
                          {"Linux", "amd64", true, "linux-musl-x86_64"},
                          {"Linux", "aarch64", true, "linux-musl-arm64"},
                          {"Mac OS X", "aarch64", false, "macos-arm64"},
                          {"Mac OS X", "x86_64", false, "macos-x86_64"},
                          {"Darwin", "arm64", false, "macos-arm64"},
                          {"Windows 11", "amd64", false, "windows-x86_64"},
                          {"Windows Server 2022", "aarch64", false, "windows-arm64"}};
      for (Object[] c : cases)
         assertEquals(c[3], fastddsjavaNativeLibrary.platformDirectory((String) c[0], (String) c[1], (Boolean) c[2]), Arrays.toString(c));
   }

   @Test
   public void unsupportedPlatformsAreNamedInTheError()
   {
      Object[][] cases = {{"Linux", "riscv64", true}, {"Linux", "arm", true}, {"Linux", "ppc64le", false}, {"SunOS", "sparcv9", false}, {"Windows 10", "x86", false},
                          {"Mac OS X", "ppc", false}};
      for (Object[] c : cases)
      {
         IOException e = assertThrows(IOException.class, () -> fastddsjavaNativeLibrary.platformDirectory((String) c[0], (String) c[1], (Boolean) c[2]));
         assertTrue(e.getMessage().contains("os.name=" + c[0]) && e.getMessage().contains("os.arch=" + c[1]), e.getMessage());
         assertTrue(e.getMessage().contains(fastddsjavaNativeLibrary.NATIVE_PATH_ENV), "the error tells how to load other natives");
      }
   }

   @Test
   public void muslIsDetectedFromTheMappedLoader() throws IOException
   {
      String alpineX64 = "7f1c2a400000-7f1c2a414000 r--p 00000000 00:2a 1863 /lib/ld-musl-x86_64.so.1\n"
                         + "7f1c2a414000-7f1c2a460000 r-xp 00014000 00:2a 1863 /lib/ld-musl-x86_64.so.1\n";
      String alpineArm = "ffff8a5f0000-ffff8a680000 r-xp 00000000 fd:00 4194 /lib/ld-musl-aarch64.so.1\n";
      String muslLibc = "7f00-7f10 r-xp 00000000 08:01 99 /usr/lib/libc.musl-x86_64.so.1\n";
      String glibc = "7f5d0e800000-7f5d0e828000 r--p 00000000 103:02 3149 /usr/lib/x86_64-linux-gnu/libc.so.6\n"
                     + "7f5d0eb2b000-7f5d0eb2d000 r--p 00000000 103:02 3110 /usr/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2\n"
                     + "7f5d0eb40000-7f5d0eb41000 r--p 00000000 103:02 9999 /home/me/musl-notes.txt\n";
      assertTrue(fastddsjavaNativeLibrary.isMusl(new BufferedReader(new StringReader(alpineX64))));
      assertTrue(fastddsjavaNativeLibrary.isMusl(new BufferedReader(new StringReader(alpineArm))));
      assertTrue(fastddsjavaNativeLibrary.isMusl(new BufferedReader(new StringReader(muslLibc))));
      assertFalse(fastddsjavaNativeLibrary.isMusl(new BufferedReader(new StringReader(glibc))));
      assertFalse(fastddsjavaNativeLibrary.isMusl(new BufferedReader(new StringReader(""))));
   }

   @Test
   public void thisJvmIsDetectedCorrectly() throws IOException
   {
      assumeTrue(System.getProperty(fastddsjavaNativeLibrary.PLATFORM_PROPERTY) == null, "platform overridden");
      String platform = fastddsjavaNativeLibrary.detectPlatform();
      assertTrue(Arrays.asList(ALL_PLATFORMS).contains(platform), platform);

      Path maps = Paths.get("/proc/self/maps");
      if (Files.isReadable(maps))
      {
         boolean glibc;
         try (Stream<String> lines = Files.lines(maps, StandardCharsets.ISO_8859_1))
         {
            glibc = lines.anyMatch(line -> line.contains("/libc.so.6") || line.contains("/libc-2."));
         }
         if (glibc)
         {
            // Even if the musl package is installed (/lib/ld-musl-*.so.1 exists), a glibc JVM is not musl.
            assertFalse(fastddsjavaNativeLibrary.isMusl());
            assertFalse(platform.startsWith("linux-musl-"), platform);
         }
      }
   }

   @Test
   public void legacyNamesExistForEveryPlatform()
   {
      for (String platform : ALL_PLATFORMS)
      {
         List<String> names = fastddsjavaNativeLibrary.legacyLibraryNames(platform);
         assertNotNull(names, platform);
         assertEquals(3, names.size());
         assertTrue(names.get(2).contains("jnifastddsjava"), "JNI library last: " + names);
      }
      assertEquals(Arrays.asList("libfastcdr.so.2", "libfastdds.so.3.6", "libjnifastddsjava.so"), fastddsjavaNativeLibrary.legacyLibraryNames("linux-musl-x86_64"));
      assertNull(fastddsjavaNativeLibrary.legacyLibraryNames("solaris-sparc"));

      // The SONAME alternative of manifest-less direct mode exists for Linux and macOS.
      for (String platform : ALL_PLATFORMS)
         assertEquals(platform.startsWith("linux-") || platform.startsWith("macos-"), fastddsjavaNativeLibrary.sonameLibraryNames(platform) != null, platform);
      assertEquals(Arrays.asList("libfastcdr.2.dylib", "libfastdds.3.6.dylib", "libjnifastddsjava.dylib"), fastddsjavaNativeLibrary.sonameLibraryNames("macos-arm64"));
      assertEquals(Arrays.asList("libfastcdr.so.2", "libfastdds.so.3.6", "libjnifastddsjava.so"), fastddsjavaNativeLibrary.sonameLibraryNames("linux-armhf"));
   }

   @Test
   public void committedManifestsDescribeTheBundledLibraries() throws Exception
   {
      int checked = 0;
      for (String platform : ALL_PLATFORMS)
      {
         String directory = "/" + fastddsjavaNativeLibrary.RESOURCE_ROOT + platform + "/";
         URL manifest = fastddsjavaNativeLibrary.class.getResource(directory + fastddsjavaNativeLibrary.MANIFEST_NAME);
         if (Arrays.asList(COMMITTED_PLATFORMS).contains(platform))
            assertNotNull(manifest, "manifest of " + platform);
         else if (manifest == null)
            continue; // not bundled (yet)
         checked++;
         List<String> names;
         try (BufferedReader reader = new BufferedReader(new InputStreamReader(manifest.openStream(), StandardCharsets.UTF_8)))
         {
            names = fastddsjavaNativeLibrary.parseManifest(reader, manifest.toString());
         }
         assertTrue(names.get(names.size() - 1).contains("jnifastddsjava"), platform + ": JNI library last in " + names);
         for (String name : names)
            assertNotNull(fastddsjavaNativeLibrary.class.getResource(directory + name), platform + ": " + name + " is listed but missing");

         // Every bundled library is listed (nothing unlisted would be silently left behind).
         Set<String> present = new HashSet<>();
         if ("file".equals(manifest.getProtocol()))
         {
            File[] files = new File(manifest.toURI()).getParentFile().listFiles();
            assertNotNull(files);
            Arrays.stream(files).map(File::getName).forEach(present::add);
         }
         else if ("jar".equals(manifest.getProtocol()))
         {
            JarURLConnection connection = (JarURLConnection) manifest.openConnection();
            connection.setUseCaches(false);
            String prefix = fastddsjavaNativeLibrary.RESOURCE_ROOT + platform + "/";
            try (JarFile jar = connection.getJarFile())
            {
               jar.stream()
                  .map(JarEntry::getName)
                  .filter(n -> n.startsWith(prefix) && n.length() > prefix.length() && n.indexOf('/', prefix.length()) < 0)
                  .map(n -> n.substring(prefix.length()))
                  .forEach(present::add);
            }
         }
         present.remove(fastddsjavaNativeLibrary.MANIFEST_NAME);
         assertEquals(new HashSet<>(names), present, platform);
      }
      assertTrue(checked >= COMMITTED_PLATFORMS.length);
   }

   /* ---------------------------------------------------------------------------------------------------------- */

   @Test
   public void extractionWritesOnceAndIsReused(@TempDir Path base) throws Exception
   {
      InMemoryNativeBundle bundle = InMemoryNativeBundle.deterministic("test-platform", 200_000, 1, 0);
      Path directory = fastddsjavaNativeLibrary.extract(bundle, base);
      assertEquals(3, bundle.opens.get());
      assertTrue(bundle.matches(directory));
      assertEquals(cacheRoot(base), directory.getParent());
      assertEquals("test-platform-" + bundle.fingerprint().substring(0, 16), directory.getFileName().toString());
      FileTime written = Files.getLastModifiedTime(directory.resolve(bundle.names.get(0)));

      Thread.sleep(20);
      InMemoryNativeBundle again = InMemoryNativeBundle.deterministic("test-platform", 200_000, 1, 0);
      assertEquals(directory, fastddsjavaNativeLibrary.extract(again, base));
      assertEquals(0, again.opens.get(), "a complete cache entry is reused without reading the libraries");
      assertEquals(written, Files.getLastModifiedTime(directory.resolve(bundle.names.get(0))));

      try (Stream<Path> entries = Files.list(directory))
      {
         Set<String> expected = new HashSet<>(bundle.names);
         expected.add(fastddsjavaNativeLibrary.LOCK_FILE_NAME);
         assertEquals(expected, entries.map(p -> p.getFileName().toString()).collect(Collectors.toSet()), "no temporary files are left");
      }
   }

   @Test
   public void differentContentsGetDifferentDirectories(@TempDir Path base) throws Exception
   {
      InMemoryNativeBundle bundle = InMemoryNativeBundle.deterministic("test-platform", 10_000, 2, 0);
      InMemoryNativeBundle changed = bundle.withOneByteChanged();
      assertNotEquals(bundle.fingerprint(), changed.fingerprint());
      Path first = fastddsjavaNativeLibrary.extract(bundle, base);
      Path second = fastddsjavaNativeLibrary.extract(changed, base);
      assertNotEquals(first, second);
      assertTrue(bundle.matches(first));
      assertTrue(changed.matches(second));
   }

   @Test
   public void damagedCacheEntriesAreRepaired(@TempDir Path base) throws Exception
   {
      InMemoryNativeBundle bundle = InMemoryNativeBundle.deterministic("test-platform", 50_000, 3, 0);
      Path directory = fastddsjavaNativeLibrary.extract(bundle, base);

      // A truncated file, a deleted file (e.g. a tmp cleaner) and a temporary file left by a crashed extraction.
      Files.write(directory.resolve(bundle.names.get(0)), new byte[10]);
      Files.delete(directory.resolve(bundle.names.get(2)));
      Path leftover = directory.resolve("." + bundle.names.get(1) + ".12345" + fastddsjavaNativeLibrary.TEMP_FILE_SUFFIX);
      Files.write(leftover, new byte[100]);

      InMemoryNativeBundle again = InMemoryNativeBundle.deterministic("test-platform", 50_000, 3, 0);
      assertEquals(directory, fastddsjavaNativeLibrary.extract(again, base));
      assertEquals(2, again.opens.get(), "only the damaged libraries are written again");
      assertTrue(bundle.matches(directory));
      assertFalse(Files.exists(leftover), "leftover temporary files are removed under the lock");
   }

   @Test
   public void cacheDirectoriesArePrivate(@TempDir Path base) throws Exception
   {
      assumeTrue(posix(base), "POSIX file system");
      InMemoryNativeBundle bundle = InMemoryNativeBundle.deterministic("test-platform", 1000, 4, 0);
      Path directory = fastddsjavaNativeLibrary.extract(bundle, base);
      assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(cacheRoot(base))));
      assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(directory)));
   }

   @Test
   public void untrustedSharedRootIsNeverUsed(@TempDir Path base) throws Exception
   {
      assumeTrue(posix(base), "POSIX file system");
      InMemoryNativeBundle bundle = InMemoryNativeBundle.deterministic("test-platform", 1000, 5, 0);

      // Writable by others: someone may have planted libraries in it.
      Path root = cacheRoot(base);
      Files.createDirectory(root);
      Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwxrwxrwx"));
      Path directory = fastddsjavaNativeLibrary.extract(bundle, base);
      assertFalse(directory.startsWith(root), directory.toString());
      assertTrue(directory.getFileName().toString().startsWith(fastddsjavaNativeLibrary.PRIVATE_TEMP_DIR_PREFIX), directory.toString());
      assertTrue(bundle.matches(directory));
      try (Stream<Path> entries = Files.list(root))
      {
         assertEquals(0, entries.count(), "nothing is written into the untrusted root");
      }

      // A symbolic link instead of a directory.
      Path other = Files.createDirectory(base.resolve("elsewhere"));
      Files.delete(root);
      Files.createSymbolicLink(root, other);
      Path viaLink = fastddsjavaNativeLibrary.extract(bundle, base);
      assertFalse(viaLink.startsWith(root));
      assertFalse(viaLink.startsWith(other));
      assertTrue(bundle.matches(viaLink));
   }

   @Test
   @Timeout(60)
   public void concurrentExtractionInOneJvmWritesOnce(@TempDir Path base) throws Exception
   {
      // Slow reads widen the race; the threads contend for the file lock inside one JVM.
      InMemoryNativeBundle bundle = InMemoryNativeBundle.deterministic("test-platform", 400_000, 6, 2);
      ExecutorService executor = Executors.newFixedThreadPool(4);
      try
      {
         List<Future<Path>> results = new ArrayList<>();
         Callable<Path> task = () -> fastddsjavaNativeLibrary.extract(bundle, base);
         for (int i = 0; i < 4; i++)
            results.add(executor.submit(task));
         Set<Path> directories = new HashSet<>();
         for (Future<Path> result : results)
            directories.add(result.get(50, TimeUnit.SECONDS));
         assertEquals(1, directories.size());
         assertEquals(3, bundle.opens.get(), "each library is extracted exactly once");
         assertTrue(bundle.matches(directories.iterator().next()));
      }
      finally
      {
         executor.shutdownNow();
      }
   }

   @Test
   public void staleLegacyTemporaryDirectoriesAreRemoved(@TempDir Path temporary) throws Exception
   {
      long now = System.currentTimeMillis();
      FileTime old = FileTime.fromMillis(now - TimeUnit.HOURS.toMillis(2));

      Path stale = Files.createDirectory(temporary.resolve(fastddsjavaNativeLibrary.LEGACY_TEMP_DIR_PREFIX + "1111"));
      for (String name : new String[] {"libfastcdr.so.2.3.5", "libfastdds.so.3.6.2", "libjnifastddsjava.so"})
         Files.write(stale.resolve(name), new byte[16]);
      Files.setLastModifiedTime(stale, old);

      Path recent = Files.createDirectory(temporary.resolve(fastddsjavaNativeLibrary.LEGACY_TEMP_DIR_PREFIX + "2222"));
      Files.write(recent.resolve("libfastcdr.so.2.3.5"), new byte[16]);

      Path nested = Files.createDirectory(temporary.resolve(fastddsjavaNativeLibrary.LEGACY_TEMP_DIR_PREFIX + "3333"));
      Files.createDirectory(nested.resolve("subdirectory"));
      Files.setLastModifiedTime(nested, old);

      Path plainFile = Files.write(temporary.resolve(fastddsjavaNativeLibrary.LEGACY_TEMP_DIR_PREFIX + "4444"), new byte[4]);
      Files.setLastModifiedTime(plainFile, old);

      Path unrelated = Files.createDirectory(temporary.resolve("something-else"));
      Files.setLastModifiedTime(unrelated, old);

      assertEquals(1, fastddsjavaNativeLibrary.cleanupLegacyTempDirectories(temporary, now));
      assertFalse(Files.exists(stale), "stale legacy directory removed");
      assertTrue(Files.exists(recent), "a recent directory may belong to a JVM that is starting");
      assertTrue(Files.exists(nested), "only flat directories (as created by the legacy loader) are removed");
      assertTrue(Files.exists(plainFile));
      assertTrue(Files.exists(unrelated));

      assertEquals(0, fastddsjavaNativeLibrary.cleanupLegacyTempDirectories(temporary.resolve("does-not-exist"), now));
   }

   @Test
   public void userNamesAreSanitized()
   {
      assertEquals("alice", fastddsjavaNativeLibrary.sanitize("alice"));
      assertEquals("DOMAIN_bob", fastddsjavaNativeLibrary.sanitize("DOMAIN\\bob"));
      assertEquals("a_b_c", fastddsjavaNativeLibrary.sanitize("a/b c"));
      assertEquals("user", fastddsjavaNativeLibrary.sanitize(""));
      assertEquals("user..", fastddsjavaNativeLibrary.sanitize(".."));
      assertEquals(64, fastddsjavaNativeLibrary.sanitize(String.join("", java.util.Collections.nCopies(100, "x"))).length());
      assertFalse(fastddsjavaNativeLibrary.sanitize("Jörg").toLowerCase(Locale.ROOT).contains("ö"));
   }
}
