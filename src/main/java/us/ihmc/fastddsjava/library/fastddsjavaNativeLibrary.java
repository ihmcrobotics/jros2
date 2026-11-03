/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.fastddsjava.library;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;

/**
 * Loads the Fast-DDS and fastddsjava JNI shared libraries.
 * <p>
 * Android: {@link System#loadLibrary} of the libraries packaged in the AAR ({@code jni/<abi>/}).
 * <p>
 * Desktop: the libraries of the detected platform live in the classpath directory {@code fastddsjava/native/<platform>/}.
 * That directory contains a {@value #MANIFEST_NAME} manifest: one file name per line, in load order, the JNI library
 * last; blank lines and lines starting with {@code #} are ignored. Without a manifest, the historical file names of the
 * platform are used. The libraries are loaded in one of two ways:
 * <ul>
 *    <li>Direct mode: if the environment variable {@value #NATIVE_PATH_ENV} or the system property
 *    {@value #NATIVE_PATH_PROPERTY} (which wins) names a directory, the libraries are loaded from that directory
 *    without any extraction, using its {@value #MANIFEST_NAME} if present, otherwise the platform's historical file
 *    names or, on Linux, the SONAME names {@code libfastcdr.so.2 libfastdds.so.3.6 libjnifastddsjava.so}.</li>
 *    <li>Extraction cache: otherwise the libraries are copied to {@code <base>/jros2-natives-<user>/<platform>-<hash>/}
 *    and loaded from there. {@code <hash>} is derived from the library contents, so the directory is reused by later
 *    runs of the same natives and never mixes different builds. {@code <base>} is the environment variable
 *    {@value #EXTRACT_DIR_ENV} or the system property {@value #EXTRACT_DIR_PROPERTY} (which wins); by default
 *    {@code java.io.tmpdir}, falling back to {@code ~/.cache/jros2} if libraries cannot be loaded from there (for
 *    example a {@code noexec} mount). Files are written to a temporary name and atomically renamed, under a file lock,
 *    so concurrent processes never see partial files. Files keep their manifest names, which for new Linux builds
 *    are the SONAMEs (required by the musl dynamic loader).</li>
 * </ul>
 * The platform is detected from {@code os.name} / {@code os.arch} and, on Linux, whether the JVM runs on musl libc;
 * it can be overridden with the system property {@value #PLATFORM_PROPERTY} (for example {@code linux-musl-x86_64}).
 */
public final class fastddsjavaNativeLibrary
{
   /** Environment variable naming a directory to load the natives from directly (no extraction). */
   public static final String NATIVE_PATH_ENV = "JROS2_NATIVE_PATH";
   /** System property naming a directory to load the natives from directly; overrides {@value #NATIVE_PATH_ENV}. */
   public static final String NATIVE_PATH_PROPERTY = "jros2.native.path";
   /** Environment variable naming the base directory of the extraction cache. */
   public static final String EXTRACT_DIR_ENV = "JROS2_NATIVE_EXTRACT_DIR";
   /** System property naming the base directory of the extraction cache; overrides {@value #EXTRACT_DIR_ENV}. */
   public static final String EXTRACT_DIR_PROPERTY = "jros2.native.extract.dir";
   /** System property overriding the detected platform directory name, e.g. {@code linux-musl-arm64}. */
   public static final String PLATFORM_PROPERTY = "jros2.native.platform";
   /** File name of the per-platform manifest. */
   public static final String MANIFEST_NAME = "natives.list";

   static final String RESOURCE_ROOT = "fastddsjava/native/";
   static final String CACHE_ROOT_PREFIX = "jros2-natives-";
   static final String LEGACY_TEMP_DIR_PREFIX = "fastddsjava-native-";
   static final String PRIVATE_TEMP_DIR_PREFIX = "jros2-natives-tmp-";
   static final String LOCK_FILE_NAME = ".lock";
   static final String TEMP_FILE_SUFFIX = ".tmp";
   /** Legacy temporary directories younger than this may still be in use by a starting JVM and are kept. */
   static final long LEGACY_CLEANUP_MIN_AGE_MILLIS = TimeUnit.MINUTES.toMillis(10);
   static final long LOCK_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(2);

   private static final String FINGERPRINT_FORMAT = "jros2-natives-v1";
   private static final int COPY_BUFFER_SIZE = 64 * 1024;

   private static boolean loaded = false;
   private static Path loadedDirectory = null;
   private static Throwable loadError = null;

   private fastddsjavaNativeLibrary()
   {
   }

   /**
    * Loads the native libraries once. Thread-safe and idempotent.
    *
    * @return true if the libraries are loaded; on failure the reason is printed and available from {@link #getLoadError()}
    */
   public static synchronized boolean load()
   {
      boolean success = loaded;

      if (!loaded)
      {
         try
         {
            if (isAndroid())
            {
               // Packaged under jni/<abi>/ in the AAR (see android/src/main/jniLibs). Dependencies first.
               System.loadLibrary("log");
               System.loadLibrary("c++_shared");
               System.loadLibrary("fastcdr");
               System.loadLibrary("fastdds");
               System.loadLibrary("jnifastddsjava");
            }
            else
            {
               loadedDirectory = loadDesktop();
            }
            loaded = true;
            loadError = null;
            success = true;
         }
         catch (Throwable t)
         {
            loadError = t;
            System.err.println("Failed to load fastddsjava native libraries: " + t.getMessage());
            t.printStackTrace(System.err);
            success = false;
         }
      }

      return success;
   }

   /**
    * @return the directory the desktop natives were loaded from, or null if they are not loaded (or on Android)
    */
   public static synchronized Path getLoadedDirectory()
   {
      return loadedDirectory;
   }

   /**
    * @return the reason of the last failed {@link #load()}, or null
    */
   public static synchronized Throwable getLoadError()
   {
      return loadError;
   }

   private static boolean isAndroid()
   {
      boolean android;

      try
      {
         Class.forName("android.os.Build");
         android = true;
      }
      catch (ClassNotFoundException ignored)
      {
         android = System.getProperty("java.vendor", "").toLowerCase(Locale.ROOT).contains("android");
      }

      return android;
   }

   private static Path loadDesktop() throws IOException
   {
      Path directory;
      String nativePath = setting(NATIVE_PATH_PROPERTY, NATIVE_PATH_ENV);

      if (nativePath != null)
      {
         directory = Paths.get(nativePath);
         loadDirectory(directory, nativePath + " (" + NATIVE_PATH_PROPERTY + " / " + NATIVE_PATH_ENV + ")");
      }
      else
      {
         String platform = detectPlatform();
         ClasspathBundle bundle = ClasspathBundle.fromClasspath(platform);
         directory = extractAndLoad(bundle);
         cleanupLegacyTempDirectories(Paths.get(System.getProperty("java.io.tmpdir")), System.currentTimeMillis());
      }

      return directory;
   }

   /** Direct mode: loads the libraries listed in the directory's manifest (or the legacy names) from the directory. */
   static void loadDirectory(Path directory, String description) throws IOException
   {
      if (!Files.isDirectory(directory))
         throw new IOException("Native library directory " + description + " does not exist or is not a directory");

      List<String> names;
      Path manifest = directory.resolve(MANIFEST_NAME);
      if (Files.isRegularFile(manifest))
      {
         try (BufferedReader reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8))
         {
            names = parseManifest(reader, manifest.toString());
         }
      }
      else
      {
         String platform = detectPlatform();
         names = legacyLibraryNames(platform);
         if (names == null)
            throw new IOException("Native library directory " + description + " has no " + MANIFEST_NAME + " and platform " + platform
                                  + " has no default library names");
         // Without a manifest, a directory with the SONAME layout (e.g. a Fast-DDS install's lib/ plus the JNI library)
         // works as well as one with the historical file names.
         List<String> sonames = sonameLibraryNames(platform);
         if (sonames != null && !allRegularFiles(directory, names) && allRegularFiles(directory, sonames))
            names = sonames;
      }

      for (String name : names)
      {
         Path library = directory.resolve(name);
         if (!Files.isRegularFile(library))
            throw new IOException("Native library " + name + " is missing from " + description);
      }
      for (String name : names)
      {
         System.load(directory.resolve(name).toAbsolutePath().toString());
      }
   }

   /** Extraction mode: extracts into the first usable cache base directory and loads from there. */
   private static Path extractAndLoad(NativeBundle bundle) throws IOException
   {
      List<Path> bases = new ArrayList<>();
      String extractDir = setting(EXTRACT_DIR_PROPERTY, EXTRACT_DIR_ENV);
      if (extractDir != null)
      {
         bases.add(Paths.get(extractDir));
      }
      else
      {
         bases.add(Paths.get(System.getProperty("java.io.tmpdir")));
         String home = System.getProperty("user.home");
         if (home != null && !home.isEmpty())
            bases.add(Paths.get(home, ".cache", "jros2"));
      }

      IOException failure = null;
      for (int i = 0; i < bases.size(); i++)
      {
         Path base = bases.get(i);
         Path directory;
         try
         {
            directory = extract(bundle, base);
         }
         catch (IOException e)
         {
            failure = addSuppressed(failure, new IOException("Cannot extract the " + bundle.platform + " natives under " + base + ": " + e.getMessage(), e));
            continue;
         }

         boolean first = true;
         try
         {
            for (String name : bundle.names)
            {
               System.load(directory.resolve(name).toAbsolutePath().toString());
               first = false;
            }
            return directory;
         }
         catch (UnsatisfiedLinkError e)
         {
            if (first && String.valueOf(e.getMessage()).contains("another classloader"))
            {
               // The JVM binds a library path to one class loader. Another class loader of this JVM loaded the cached
               // copy, so this one needs its own copy.
               Path copy = extract(bundle, base, true);
               for (String name : bundle.names)
                  System.load(copy.resolve(name).toAbsolutePath().toString());
               return copy;
            }
            // Nothing loaded yet (typically a noexec mount): another base directory may work. Otherwise give up.
            if (!first || i == bases.size() - 1)
               throw new IOException("Cannot load the " + bundle.platform + " natives from " + directory + ": " + e.getMessage(), e);
            failure = addSuppressed(failure, new IOException("Cannot load the " + bundle.platform + " natives from " + directory + ": " + e.getMessage(), e));
         }
      }

      throw failure != null ? failure : new IOException("No extraction directory for the " + bundle.platform + " natives");
   }

   private static IOException addSuppressed(IOException first, IOException next)
   {
      if (first == null)
         return next;
      first.addSuppressed(next);
      return first;
   }

   private static String setting(String property, String environmentVariable)
   {
      String value = System.getProperty(property);
      if (value == null || value.trim().isEmpty())
         value = System.getenv(environmentVariable);
      return value == null || value.trim().isEmpty() ? null : value.trim();
   }

   /* -------------------------------------------------------------------------------------------------------------
    * Extraction cache
    * ------------------------------------------------------------------------------------------------------------- */

   /**
    * Ensures every library of the bundle is present in the content-addressed cache directory under {@code base} and
    * returns that directory. Safe against concurrent extraction by other threads and processes.
    */
   static Path extract(NativeBundle bundle, Path base) throws IOException
   {
      return extract(bundle, base, false);
   }

   /**
    * @param privateCopy true to extract into a fresh private directory instead of the shared cache
    */
   static Path extract(NativeBundle bundle, Path base, boolean privateCopy) throws IOException
   {
      Files.createDirectories(base);
      Path root = base.resolve(CACHE_ROOT_PREFIX + sanitize(System.getProperty("user.name", "user")));
      boolean privateRoot = false;
      if (!privateCopy)
      {
         try
         {
            privateRoot = ensurePrivateDirectory(root, base);
         }
         catch (IOException e)
         {
            privateRoot = false;
         }
      }

      Path directory;
      if (privateRoot)
      {
         directory = root.resolve(bundle.platform + "-" + bundle.fingerprint().substring(0, 16));
         if (!ensurePrivateDirectory(directory, base))
            throw new IOException(directory + " is not owned by the current user or is writable by others");
      }
      else
      {
         // The shared location is not trustworthy (not owned by us, writable by others, or a symbolic link):
         // never load code from it. Use a fresh private directory instead (no reuse across runs).
         directory = createPrivateTemporaryDirectory(base, bundle);
         if (!privateCopy)
            System.err.println("fastddsjava: " + root + " is not a private directory; extracting natives to " + directory);
      }

      if (isComplete(bundle, directory))
         return directory;

      try (FileChannel channel = FileChannel.open(directory.resolve(LOCK_FILE_NAME), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
           FileLock lock = acquire(channel))
      {
         // Holding the lock: nobody else is extracting here, so temporary files are leftovers of a crashed run.
         deleteTemporaryFiles(directory);
         for (String name : bundle.names)
         {
            Path target = directory.resolve(name);
            if (!hasExpectedSize(bundle, name, target))
               writeAtomically(bundle, name, directory, target);
         }
      }

      if (!isComplete(bundle, directory))
         throw new IOException("Extraction of the " + bundle.platform + " natives to " + directory + " is incomplete");
      return directory;
   }

   private static boolean isComplete(NativeBundle bundle, Path directory) throws IOException
   {
      for (String name : bundle.names)
      {
         if (!hasExpectedSize(bundle, name, directory.resolve(name)))
            return false;
      }
      return true;
   }

   private static boolean hasExpectedSize(NativeBundle bundle, String name, Path file) throws IOException
   {
      return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) == bundle.size(name);
   }

   private static void writeAtomically(NativeBundle bundle, String name, Path directory, Path target) throws IOException
   {
      Path temporary = Files.createTempFile(directory, "." + name + ".", TEMP_FILE_SUFFIX);
      try
      {
         long written = 0;
         try (InputStream in = bundle.open(name); OutputStream out = Files.newOutputStream(temporary))
         {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) >= 0)
            {
               out.write(buffer, 0, read);
               written += read;
            }
         }
         if (written != bundle.size(name))
            throw new IOException("Native library resource " + bundle.describe(name) + " changed while it was extracted");

         try
         {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         }
         catch (AtomicMoveNotSupportedException e)
         {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
         }
      }
      finally
      {
         Files.deleteIfExists(temporary);
      }
   }

   private static void deleteTemporaryFiles(Path directory)
   {
      try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory))
      {
         for (Path entry : entries)
         {
            String name = entry.getFileName().toString();
            if (name.startsWith(".") && name.endsWith(TEMP_FILE_SUFFIX))
               Files.deleteIfExists(entry);
         }
      }
      catch (IOException ignored)
      {
         // Best effort.
      }
   }

   /** Acquires an exclusive lock, also when another class loader of this JVM holds it (OverlappingFileLockException). */
   private static FileLock acquire(FileChannel channel) throws IOException
   {
      long deadline = System.currentTimeMillis() + LOCK_TIMEOUT_MILLIS;
      while (true)
      {
         try
         {
            return channel.lock();
         }
         catch (OverlappingFileLockException heldInThisJvm)
         {
            if (System.currentTimeMillis() > deadline)
               throw new IOException("Timed out waiting for the native library extraction lock", heldInThisJvm);
            try
            {
               Thread.sleep(20);
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
               throw new IOException("Interrupted while waiting for the native library extraction lock", e);
            }
         }
      }
   }

   /**
    * Creates {@code directory} (owner-only on POSIX) if needed and checks that it can be trusted: a real directory (not a
    * symbolic link) owned by the current user and not writable by group or others.
    *
    * @param ownerProbeDirectory a writable directory used to determine the current user's identity
    * @return true if trusted
    */
   static boolean ensurePrivateDirectory(Path directory, Path ownerProbeDirectory) throws IOException
   {
      boolean posix = Files.getFileAttributeView(ownerProbeDirectory, PosixFileAttributeView.class) != null;
      boolean created = false;
      if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS))
      {
         try
         {
            if (posix)
               Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(EnumSet.of(PosixFilePermission.OWNER_READ,
                                                                                                PosixFilePermission.OWNER_WRITE,
                                                                                                PosixFilePermission.OWNER_EXECUTE)));
            else
               Files.createDirectory(directory);
            created = true;
         }
         catch (FileAlreadyExistsException raced)
         {
            created = false;
         }
      }

      if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
         throw new IOException(directory + " is not a directory or is a symbolic link");

      if (posix && !created)
      {
         PosixFileAttributes attributes = Files.readAttributes(directory, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
         if (!attributes.owner().equals(currentUser(ownerProbeDirectory)))
            return false;
         // Writable by others: its contents may have been tampered with, so it is not trusted (and not "repaired").
         Set<PosixFilePermission> permissions = attributes.permissions();
         if (permissions.contains(PosixFilePermission.GROUP_WRITE) || permissions.contains(PosixFilePermission.OTHERS_WRITE))
            return false;
      }
      return true;
   }

   private static UserPrincipal currentUserCache;

   private static synchronized UserPrincipal currentUser(Path probeDirectory) throws IOException
   {
      if (currentUserCache == null)
      {
         // The owner of a file we just created is the identity the OS checks; user.name may not match it (containers).
         Path probe = Files.createTempFile(probeDirectory, ".jros2-owner-", TEMP_FILE_SUFFIX);
         try
         {
            currentUserCache = Files.getOwner(probe, LinkOption.NOFOLLOW_LINKS);
         }
         finally
         {
            Files.deleteIfExists(probe);
         }
      }
      return currentUserCache;
   }

   /** A fresh directory that is removed (best effort) when the JVM exits normally: the pre-cache behaviour. */
   static Path createPrivateTemporaryDirectory(Path base, NativeBundle bundle) throws IOException
   {
      Path directory = Files.createTempDirectory(base, PRIVATE_TEMP_DIR_PREFIX);
      deleteOnExit(directory, bundle.names);
      return directory;
   }

   private static void deleteOnExit(Path directory, List<String> names)
   {
      // Registered parent first: deleteOnExit runs in reverse order of registration.
      directory.toFile().deleteOnExit();
      directory.resolve(LOCK_FILE_NAME).toFile().deleteOnExit();
      for (String name : names)
         directory.resolve(name).toFile().deleteOnExit();
   }

   static String sanitize(String userName)
   {
      StringBuilder sanitized = new StringBuilder();
      for (int i = 0; i < userName.length() && sanitized.length() < 64; i++)
      {
         char c = userName.charAt(i);
         boolean safe = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.';
         sanitized.append(safe ? c : '_');
      }
      String result = sanitized.toString();
      return result.isEmpty() || result.startsWith(".") ? "user" + result : result;
   }

   /**
    * Best-effort removal of the {@code fastddsjava-native-*} temporary directories that jros2 versions up to 1.5 left
    * behind when the JVM did not exit normally. Only directories owned by the current user that are older than
    * {@link #LEGACY_CLEANUP_MIN_AGE_MILLIS} are removed.
    *
    * @return the number of directories removed
    */
   static int cleanupLegacyTempDirectories(Path temporaryDirectory, long nowMillis)
   {
      int removed = 0;
      try (DirectoryStream<Path> entries = Files.newDirectoryStream(temporaryDirectory, LEGACY_TEMP_DIR_PREFIX + "*"))
      {
         UserPrincipal me = null;
         for (Path entry : entries)
         {
            try
            {
               if (!Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS))
                  continue;
               FileTime modified = Files.getLastModifiedTime(entry, LinkOption.NOFOLLOW_LINKS);
               if (nowMillis - modified.toMillis() < LEGACY_CLEANUP_MIN_AGE_MILLIS)
                  continue;
               if (Files.getFileAttributeView(entry, PosixFileAttributeView.class) != null)
               {
                  if (me == null)
                     me = currentUser(temporaryDirectory);
                  if (!me.equals(Files.getOwner(entry, LinkOption.NOFOLLOW_LINKS)))
                     continue;
               }
               if (deleteFlatDirectory(entry))
                  removed++;
            }
            catch (IOException | RuntimeException ignored)
            {
               // Best effort (e.g. a library still mapped on Windows).
            }
         }
      }
      catch (IOException | RuntimeException ignored)
      {
         // Best effort.
      }
      return removed;
   }

   /** Deletes a directory that contains only regular files (as the legacy extraction directories do). */
   private static boolean deleteFlatDirectory(Path directory) throws IOException
   {
      List<Path> files = new ArrayList<>();
      try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory))
      {
         for (Path entry : entries)
         {
            if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS))
               return false; // not created by the legacy loader: leave it alone entirely
            files.add(entry);
         }
      }
      for (Path file : files)
         Files.deleteIfExists(file);
      return Files.deleteIfExists(directory);
   }

   /* -------------------------------------------------------------------------------------------------------------
    * Platform detection and manifests
    * ------------------------------------------------------------------------------------------------------------- */

   /**
    * @return the platform directory name for this JVM, e.g. {@code linux-x86_64} or {@code linux-musl-arm64}
    * @throws IOException if the platform is not supported
    */
   public static String detectPlatform() throws IOException
   {
      String override = System.getProperty(PLATFORM_PROPERTY);
      if (override != null && !override.trim().isEmpty())
         return override.trim();

      String osName = System.getProperty("os.name", "");
      String osArch = System.getProperty("os.arch", "");
      boolean musl = osName.toLowerCase(Locale.ROOT).startsWith("linux") && isMusl();
      return platformDirectory(osName, osArch, musl);
   }

   /**
    * Maps {@code os.name}, {@code os.arch} and the libc flavour to a platform directory name.
    *
    * @throws IOException naming the platform if it is not supported
    */
   static String platformDirectory(String osName, String osArch, boolean musl) throws IOException
   {
      String os = osName.toLowerCase(Locale.ROOT);
      String arch = normalizeArch(osArch.toLowerCase(Locale.ROOT).trim());
      String platform = null;

      if (os.startsWith("linux"))
      {
         if ("x86_64".equals(arch))
            platform = musl ? "linux-musl-x86_64" : "linux-x86_64";
         else if ("arm64".equals(arch))
            platform = musl ? "linux-musl-arm64" : "linux-arm64";
         else if ("armhf".equals(arch) && !musl)
            platform = "linux-armhf";
         else if ("riscv64".equals(arch) && !musl)
            platform = "linux-riscv64";
      }
      else if (os.startsWith("mac") || os.startsWith("darwin"))
      {
         if ("arm64".equals(arch))
            platform = "macos-arm64";
         else if ("x86_64".equals(arch))
            platform = "macos-x86_64";
      }
      else if (os.startsWith("windows"))
      {
         if ("x86_64".equals(arch))
            platform = "windows-x86_64";
         else if ("arm64".equals(arch))
            platform = "windows-arm64";
      }

      if (platform == null)
      {
         throw new IOException("Unsupported platform for the fastddsjava natives: os.name=" + osName + ", os.arch=" + osArch + (musl ? ", libc=musl" : "")
                               + ". Supported: linux-x86_64, linux-arm64, linux-armhf, linux-riscv64, linux-musl-x86_64, linux-musl-arm64,"
                               + " macos-arm64, macos-x86_64, windows-x86_64, windows-arm64 (override with -D" + PLATFORM_PROPERTY + "=<platform>"
                               + " or load your own build with " + NATIVE_PATH_ENV + ")");
      }
      return platform;
   }

   private static String normalizeArch(String arch)
   {
      switch (arch)
      {
         case "amd64":
         case "x86_64":
         case "x64":
            return "x86_64";
         case "aarch64":
         case "arm64":
            return "arm64";
         case "arm":
         case "armhf":
         case "armv7":
         case "armv7l":
         case "armv7hl":
         case "aarch32":
            return "armhf";
         case "riscv64":
            return "riscv64";
         default:
            return arch;
      }
   }

   /**
    * @return true if this JVM process runs on musl libc. Decided from the libraries mapped into the process, not from the
    *       presence of {@code /lib/ld-musl-*} (glibc distributions can have the musl package installed).
    */
   static boolean isMusl()
   {
      Path maps = Paths.get("/proc/self/maps");
      if (!Files.isReadable(maps))
         return false;
      try (BufferedReader reader = Files.newBufferedReader(maps, StandardCharsets.ISO_8859_1))
      {
         return isMusl(reader);
      }
      catch (IOException | RuntimeException e)
      {
         return false;
      }
   }

   /** @return true if a {@code /proc/<pid>/maps} listing maps the musl dynamic loader / libc. */
   static boolean isMusl(BufferedReader maps) throws IOException
   {
      String line;
      while ((line = maps.readLine()) != null)
      {
         if (line.contains("/ld-musl-") || line.contains("/libc.musl-"))
            return true;
      }
      return false;
   }

   private static boolean allRegularFiles(Path directory, List<String> names)
   {
      for (String name : names)
      {
         if (!Files.isRegularFile(directory.resolve(name)))
            return false;
      }
      return true;
   }

   /**
    * @return the SONAME file names (load order, JNI library last) of a Linux platform, which newer Linux builds ship and
    *       a Fast-DDS install provides as symbolic links, or null if the platform has no such alternative
    */
   static List<String> sonameLibraryNames(String platform)
   {
      if (!platform.startsWith("linux-"))
         return null;
      List<String> list = new ArrayList<>();
      Collections.addAll(list, "libfastcdr.so.2", "libfastdds.so.3.6", "libjnifastddsjava.so");
      return Collections.unmodifiableList(list);
   }

   /**
    * @return the library file names of a platform directory that has no manifest (load order, JNI library last),
    *       or null for an unknown platform
    */
   static List<String> legacyLibraryNames(String platform)
   {
      String[] names;
      switch (platform)
      {
         case "linux-x86_64":
         case "linux-arm64":
         case "linux-armhf":
            names = new String[] {"libfastcdr.so.2.3.5", "libfastdds.so.3.6.2", "libjnifastddsjava.so"};
            break;
         case "linux-riscv64":
         case "linux-musl-x86_64":
         case "linux-musl-arm64":
            names = new String[] {"libfastcdr.so.2", "libfastdds.so.3.6", "libjnifastddsjava.so"};
            break;
         case "macos-arm64":
         case "macos-x86_64":
            names = new String[] {"libfastcdr.2.3.5.dylib", "libfastdds.3.6.2.dylib", "libjnifastddsjava.dylib"};
            break;
         case "windows-x86_64":
         case "windows-arm64":
            names = new String[] {"fastcdr-2.3.dll", "fastdds-3.6.dll", "jnifastddsjava.dll"};
            break;
         default:
            return null;
      }
      List<String> list = new ArrayList<>();
      Collections.addAll(list, names);
      return Collections.unmodifiableList(list);
   }

   /**
    * Parses a {@value #MANIFEST_NAME} manifest: one plain file name per line in load order; blank lines and lines
    * starting with {@code #} are ignored.
    *
    * @throws IOException if the manifest is empty or contains an unsafe or duplicate name
    */
   static List<String> parseManifest(BufferedReader reader, String source) throws IOException
   {
      List<String> names = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null)
      {
         lineNumber++;
         String name = line.trim();
         if (lineNumber == 1 && name.startsWith("\uFEFF"))
            name = name.substring(1).trim();
         if (name.isEmpty() || name.startsWith("#"))
            continue;
         if (!isSafeFileName(name))
            throw new IOException(source + ":" + lineNumber + ": invalid native library file name '" + name + "'");
         if (!seen.add(name))
            throw new IOException(source + ":" + lineNumber + ": duplicate native library file name '" + name + "'");
         names.add(name);
      }
      if (names.isEmpty())
         throw new IOException(source + " lists no native libraries");
      return Collections.unmodifiableList(names);
   }

   private static boolean isSafeFileName(String name)
   {
      if (name.startsWith(".") || name.length() > 255)
         return false;
      for (int i = 0; i < name.length(); i++)
      {
         char c = name.charAt(i);
         if (c == '/' || c == '\\' || c == ':' || c < 0x20 || c == 0x7F)
            return false;
      }
      return true;
   }

   /* -------------------------------------------------------------------------------------------------------------
    * Library sources
    * ------------------------------------------------------------------------------------------------------------- */

   /** The libraries of one platform: names in load order, contents, sizes and a content fingerprint. */
   abstract static class NativeBundle
   {
      final String platform;
      final List<String> names;

      NativeBundle(String platform, List<String> names)
      {
         this.platform = platform;
         this.names = names;
      }

      abstract InputStream open(String name) throws IOException;

      abstract long size(String name) throws IOException;

      /** @return a lowercase hex digest of the bundle contents (at least 16 characters) */
      abstract String fingerprint() throws IOException;

      String describe(String name)
      {
         return platform + "/" + name;
      }
   }

   /** Libraries in {@code fastddsjava/native/<platform>/} on the classpath. */
   static final class ClasspathBundle extends NativeBundle
   {
      private final long[] sizes;
      private final String fingerprint;

      private ClasspathBundle(String platform, List<String> names, long[] sizes, String fingerprint)
      {
         super(platform, names);
         this.sizes = sizes;
         this.fingerprint = fingerprint;
      }

      static ClasspathBundle fromClasspath(String platform) throws IOException
      {
         String directory = "/" + RESOURCE_ROOT + platform + "/";
         List<String> names;
         URL manifest = fastddsjavaNativeLibrary.class.getResource(directory + MANIFEST_NAME);
         if (manifest != null)
         {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(openUncached(manifest), StandardCharsets.UTF_8)))
            {
               names = parseManifest(reader, directory + MANIFEST_NAME);
            }
         }
         else
         {
            names = legacyLibraryNames(platform);
            if (names == null)
               throw new IOException("No native libraries for platform " + platform + ": classpath resource " + directory + MANIFEST_NAME + " not found");
         }

         MessageDigest bundleDigest = sha256();
         bundleDigest.update((FINGERPRINT_FORMAT + "\n" + platform + "\n").getBytes(StandardCharsets.UTF_8));
         long[] sizes = new long[names.size()];
         for (int i = 0; i < names.size(); i++)
         {
            String name = names.get(i);
            URL resource = fastddsjavaNativeLibrary.class.getResource(directory + name);
            if (resource == null)
               throw new IOException("Native library " + name + " listed for platform " + platform + " is missing from the classpath (" + directory + ")");
            String identity = contentIdentity(resource);
            sizes[i] = Long.parseLong(identity.substring(0, identity.indexOf(':')));
            bundleDigest.update((name + "\n" + identity + "\n").getBytes(StandardCharsets.UTF_8));
         }
         return new ClasspathBundle(platform, names, sizes, toHex(bundleDigest.digest()));
      }

      /**
       * @return {@code <size>:<content checksum>}. Inside a jar the size and CRC-32 come from the jar directory (no
       *       decompression); otherwise the content is hashed.
       */
      private static String contentIdentity(URL resource) throws IOException
      {
         URLConnection connection = resource.openConnection();
         if (connection instanceof JarURLConnection)
         {
            try
            {
               // Default caching: the jar is shared with the class loader instead of opening (and leaking) another handle.
               JarEntry entry = ((JarURLConnection) connection).getJarEntry();
               if (entry != null && entry.getSize() >= 0 && entry.getCrc() >= 0)
                  return entry.getSize() + ":crc32-" + Long.toHexString(entry.getCrc());
            }
            catch (IOException | RuntimeException unavailable)
            {
               // Some jar URL handlers (e.g. nested jars) cannot provide the entry: hash the content instead.
            }
         }

         MessageDigest digest = sha256();
         long size = 0;
         try (InputStream in = openUncached(resource))
         {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) >= 0)
            {
               digest.update(buffer, 0, read);
               size += read;
            }
         }
         return size + ":sha256-" + toHex(digest.digest());
      }

      @Override
      InputStream open(String name) throws IOException
      {
         URL resource = fastddsjavaNativeLibrary.class.getResource("/" + RESOURCE_ROOT + platform + "/" + name);
         if (resource == null)
            throw new IOException("Missing native library resource " + describe(name));
         return openUncached(resource);
      }

      @Override
      long size(String name)
      {
         return sizes[names.indexOf(name)];
      }

      @Override
      String fingerprint()
      {
         return fingerprint;
      }

      private static InputStream openUncached(URL resource) throws IOException
      {
         URLConnection connection = resource.openConnection();
         connection.setUseCaches(false);
         return connection.getInputStream();
      }
   }

   static MessageDigest sha256() throws IOException
   {
      try
      {
         return MessageDigest.getInstance("SHA-256");
      }
      catch (NoSuchAlgorithmException e)
      {
         throw new IOException("SHA-256 is not available", e);
      }
   }

   static String toHex(byte[] bytes)
   {
      char[] digits = "0123456789abcdef".toCharArray();
      char[] hex = new char[bytes.length * 2];
      for (int i = 0; i < bytes.length; i++)
      {
         hex[2 * i] = digits[(bytes[i] >> 4) & 0xF];
         hex[2 * i + 1] = digits[bytes[i] & 0xF];
      }
      return new String(hex);
   }
}
