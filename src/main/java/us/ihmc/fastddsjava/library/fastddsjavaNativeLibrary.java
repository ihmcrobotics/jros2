/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
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

import us.ihmc.fastddsjava.pointers.PublicationMatchedStatus;
import us.ihmc.tools.nativelibraries.NativeLibraryDescription;
import us.ihmc.tools.nativelibraries.NativeLibraryLoader;
import us.ihmc.tools.nativelibraries.NativeLibraryWithDependencies;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.stream.Stream;

public class fastddsjavaNativeLibrary implements NativeLibraryDescription
{
   /** SHA-1 prefix of linux-arm64 libjnifastddsjava.so built after commit 7be31aa (JavaCPP bindings). */
   private static final String EXPECTED_LINUX_ARM64_JNI_SHA1_PREFIX = "00197D91";
   private static final long EXPECTED_LINUX_ARM64_JNI_SIZE = 249120L;
   private static final String REFRESH_NATIVE_LIBRARIES_PROPERTY = "jros2.refreshNativeLibraries";

   @Override
   public String getPackage(OperatingSystem os, Architecture arch)
   {
      String archPackage = "";

      switch (os)
      {
         case LINUX64 ->
         {
            // Manually parse the architecture, IHMC Native Library Loader doesn't handle all cases for Linux
            String archProp = System.getProperty("os.arch");

            switch (archProp)
            {
               case "arm", "armhf", "armv7l" -> archPackage = "linux-armhf";
               case "arm64", "aarch64" -> archPackage = "linux-arm64";
               case "amd64", "x86_64" -> archPackage = "linux-x86_64";
            }
         }
         case WIN64 ->
         {
            if (arch == Architecture.x64)
            {
               archPackage = "windows-x86_64";
            }
         }
         case MACOSX64 ->
         {
            if (arch == Architecture.arm64)
            {
               archPackage = "macos-arm64";
            }
            else if (arch == Architecture.x64)
            {
               archPackage = "macos-x86_64";
            }
         }
      }

      if (archPackage.isEmpty())
      {
         throw new RuntimeException("Unsupported architecture or operating system.");
      }

      return "fastddsjava.native." + archPackage;
   }

   @Override
   public NativeLibraryWithDependencies getLibraryWithDependencies(OperatingSystem os, Architecture arch)
   {
      switch (os)
      {
         case LINUX64 ->
         {
            return NativeLibraryWithDependencies.fromFilename("libjnifastddsjava.so", "libfastcdr.so.2.3.0", "libfastdds.so.3.2.2");
         }
         case WIN64 ->
         {
            return NativeLibraryWithDependencies.fromFilename("jnifastddsjava.dll", "fastcdr-2.3.dll", "fastdds-3.2.dll");
         }
         case MACOSX64 ->
         {
            return NativeLibraryWithDependencies.fromFilename("libjnifastddsjava.dylib", "libfastcdr.2.3.0.dylib", "libfastdds.3.2.2.dylib");
         }
      }
      return null;
   }

   private static boolean loaded = false;

   public static synchronized boolean load()
   {
      if (!loaded)
      {
         if (Boolean.getBoolean(REFRESH_NATIVE_LIBRARIES_PROPERTY))
         {
            deleteNativeLibraryCache();
         }

         fastddsjavaNativeLibrary lib = new fastddsjavaNativeLibrary();
         String jniClasspathResource = getJniClasspathResourcePath(lib);
         ClasspathNativeInfo classpathNativeInfo = readClasspathNativeInfo(jniClasspathResource);
         logInfo("Classpath libjnifastddsjava.so SHA-1=" + classpathNativeInfo.sha1Hex()
               + " size=" + classpathNativeInfo.sizeBytes() + " bytes"
               + " resource=" + jniClasspathResource);

         if (!NativeLibraryLoader.loadLibrary(lib))
         {
            return false;
         }

         try
         {
            verifyNativeBindings(classpathNativeInfo);
            loaded = true;
         }
         catch (UnsatisfiedLinkError unsatisfiedLinkError)
         {
            if (Boolean.getBoolean(REFRESH_NATIVE_LIBRARIES_PROPERTY))
            {
               deleteNativeLibraryCache();
            }
            throw unsatisfiedLinkError;
         }
      }
      return loaded;
   }

   private static String getJniClasspathResourcePath(fastddsjavaNativeLibrary lib)
   {
      OperatingSystem os = getOperatingSystem();
      Architecture arch = getArchitecture();
      String packageName = lib.getPackage(os, arch);
      String packagePrefix = packageName.trim().replace('.', '/');
      if (!packagePrefix.isEmpty())
      {
         packagePrefix = packagePrefix + '/';
      }
      NativeLibraryWithDependencies libraryWithDependencies = lib.getLibraryWithDependencies(os, arch);
      if (libraryWithDependencies == null)
      {
         throw new RuntimeException("Unsupported architecture or operating system.");
      }
      return packagePrefix + libraryWithDependencies.getLibraryFilename();
   }

   private static OperatingSystem getOperatingSystem()
   {
      String osName = System.getProperty("os.name");
      if (osName.contains("Windows"))
      {
         return OperatingSystem.WIN64;
      }
      if (osName.contains("Mac"))
      {
         return OperatingSystem.MACOSX64;
      }
      if (osName.contains("Linux"))
      {
         return OperatingSystem.LINUX64;
      }
      throw new RuntimeException("Unsupported operating system: " + osName);
   }

   private static Architecture getArchitecture()
   {
      String arch = System.getProperty("os.arch");
      if ("aarch64".equals(arch) || "arm64".equals(arch))
      {
         return Architecture.arm64;
      }
      if ("arm".equals(arch) || "armhf".equals(arch) || "armv7l".equals(arch))
      {
         return Architecture.arm;
      }
      if ("amd64".equals(arch) || "x86_64".equals(arch) || "x64".equals(arch))
      {
         return Architecture.x64;
      }
      throw new RuntimeException("Unsupported architecture: " + arch);
   }

   private record ClasspathNativeInfo(String sha1Hex, long sizeBytes)
   {
   }

   private static ClasspathNativeInfo readClasspathNativeInfo(String resourcePath)
   {
      try (InputStream inputStream = fastddsjavaNativeLibrary.class.getClassLoader().getResourceAsStream(resourcePath))
      {
         if (inputStream == null)
         {
            return new ClasspathNativeInfo("MISSING", -1L);
         }

         MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
         byte[] buffer = new byte[8192];
         long sizeBytes = 0L;
         int read;
         while ((read = inputStream.read(buffer)) >= 0)
         {
            if (read > 0)
            {
               messageDigest.update(buffer, 0, read);
               sizeBytes += read;
            }
         }

         StringBuilder hexString = new StringBuilder();
         for (byte digestByte : messageDigest.digest())
         {
            hexString.append("%02X".formatted(digestByte));
         }
         return new ClasspathNativeInfo(hexString.toString(), sizeBytes);
      }
      catch (IOException | NoSuchAlgorithmException e)
      {
         return new ClasspathNativeInfo("UNREADABLE", -1L);
      }
   }

   /**
    * Delete {@code ~/.ihmc/lib/fastddsjava}. NativeLibraryLoader skips extraction when a file already
    * exists in the hash directory, so stale copies can survive cache deletes that leave files behind.
    */
   private static void deleteNativeLibraryCache()
   {
      File cacheDirectory = new File(NativeLibraryLoader.LIBRARY_LOCATION, "fastddsjava");
      if (!cacheDirectory.exists())
      {
         return;
      }

      try (Stream<File> paths = Files.walk(cacheDirectory.toPath()).map(path -> path.toFile()))
      {
         paths.sorted(Comparator.reverseOrder()).forEach(path ->
         {
            if (!path.delete())
            {
               logInfo("Could not delete native library cache entry: " + path.getAbsolutePath());
            }
         });
      }
      catch (IOException e)
      {
         logInfo("Could not delete native library cache at " + cacheDirectory.getAbsolutePath() + ": " + e.getMessage());
      }
   }

   private static void logInfo(String message)
   {
      try
      {
         Class<?> logToolsClass = Class.forName("us.ihmc.log.LogTools");
         logToolsClass.getMethod("info", String.class).invoke(null, message);
      }
      catch (ReflectiveOperationException ignored)
      {
         System.err.println("[jros2] INFO: " + message);
      }
   }

   /**
    * Fail fast when an outdated libjnifastddsjava.so was extracted to {@code ~/.ihmc/lib/fastddsjava}.
    * Pre-rebuild arm64 libraries (~187 KiB) are missing JavaCPP symbols such as PublicationMatchedStatus.allocate().
    */
   private static void verifyNativeBindings(ClasspathNativeInfo classpathNativeInfo)
   {
      try
      {
         PublicationMatchedStatus status = new PublicationMatchedStatus();
         status.close();
      }
      catch (UnsatisfiedLinkError unsatisfiedLinkError)
      {
         String classpathSha1 = classpathNativeInfo.sha1Hex();
         UnsatisfiedLinkError error = new UnsatisfiedLinkError(
               "Loaded libjnifastddsjava.so is missing JavaCPP bindings (PublicationMatchedStatus). "
                     + "Classpath libjnifastddsjava.so SHA-1=" + classpathSha1
                     + " (expected prefix " + EXPECTED_LINUX_ARM64_JNI_SHA1_PREFIX
                     + ", size ~" + EXPECTED_LINUX_ARM64_JNI_SIZE + " bytes). "
                     + "Rebuild jros2 so the JAR on the classpath contains the updated linux-arm64 natives; "
                     + "copying files into ~/.ihmc alone is not sufficient when the classpath JAR is stale. "
                     + "Delete ~/.ihmc/lib/fastddsjava (or run with -Djros2.refreshNativeLibraries=true) and restart in a new JVM. "
                     + "A retry in the same JVM cannot reload native libraries after a failed binding check.");
         error.initCause(unsatisfiedLinkError);
         throw error;
      }
   }
}
