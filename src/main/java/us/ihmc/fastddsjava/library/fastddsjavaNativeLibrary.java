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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Extracts bundled Fast-DDS / JNI shared libraries from classpath resources and loads them via {@link System#load}.
 */
public final class fastddsjavaNativeLibrary
{
   private static boolean loaded = false;

   private fastddsjavaNativeLibrary()
   {
   }

   public static synchronized boolean load()
   {
      boolean success = loaded;

      if (!loaded)
      {
         try
         {
            if (isAndroid())
            {
               // Packaged under jni/<abi>/ in the AAR (see android/src/main/jniLibs).
               System.loadLibrary("log");
               System.loadLibrary("c++_shared");
               System.loadLibrary("fastcdr");
               System.loadLibrary("fastdds");
               System.loadLibrary("jnifastddsjava");
               loaded = true;
               success = true;
            }
            else
            {
               PlatformLibs platform = detectPlatform();
               Path extractDir = Files.createTempDirectory("fastddsjava-native-");
               extractDir.toFile().deleteOnExit();

               for (String dependency : platform.dependencies)
               {
                  Path extracted = extract(platform.resourcePackage, dependency, extractDir);
                  System.load(extracted.toAbsolutePath().toString());
               }

               Path jniLib = extract(platform.resourcePackage, platform.jniLibrary, extractDir);
               System.load(jniLib.toAbsolutePath().toString());
               loaded = true;
               success = true;
            }
         }
         catch (Throwable t)
         {
            System.err.println("Failed to load fastddsjava native libraries: " + t.getMessage());
            t.printStackTrace(System.err);
            success = false;
         }
      }

      return success;
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

   private static Path extract(String resourcePackage, String filename, Path extractDir) throws IOException
   {
      String resourcePath = "/" + resourcePackage.replace('.', '/') + "/" + filename;
      Path target = extractDir.resolve(filename);
      try (InputStream in = fastddsjavaNativeLibrary.class.getResourceAsStream(resourcePath))
      {
         if (in == null)
         {
            throw new IOException("Missing native library resource: " + resourcePath);
         }
         try (OutputStream out = Files.newOutputStream(target))
         {
            in.transferTo(out);
         }
      }
      target.toFile().deleteOnExit();
      return target;
   }

   private static PlatformLibs detectPlatform()
   {
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
      PlatformLibs platformLibs;

      if (os.contains("linux"))
      {
         String archPackage;
         if (arch.equals("arm") || arch.equals("armhf") || arch.equals("armv7l"))
         {
            archPackage = "linux-armhf";
         }
         else if (arch.equals("arm64") || arch.equals("aarch64"))
         {
            archPackage = "linux-arm64";
         }
         else if (arch.equals("amd64") || arch.equals("x86_64"))
         {
            archPackage = "linux-x86_64";
         }
         else
         {
            throw new RuntimeException("Unsupported Linux architecture: " + arch);
         }

         platformLibs = new PlatformLibs("fastddsjava.native." + archPackage, "libjnifastddsjava.so",
                                         new String[] {"libfastcdr.so.2.3.5", "libfastdds.so.3.6.2"});
      }
      else if (os.contains("win"))
      {
         String archPackage;
         if (arch.equals("amd64") || arch.equals("x86_64"))
         {
            archPackage = "windows-x86_64";
         }
         else if (arch.equals("aarch64") || arch.equals("arm64"))
         {
            archPackage = "windows-arm64";
         }
         else
         {
            throw new RuntimeException("Unsupported Windows architecture: " + arch);
         }
         platformLibs = new PlatformLibs("fastddsjava.native." + archPackage, "jnifastddsjava.dll",
                                         new String[] {"fastcdr-2.3.dll", "fastdds-3.6.dll"});
      }
      else if (os.contains("mac"))
      {
         String archPackage;
         if (arch.equals("arm64") || arch.equals("aarch64"))
         {
            archPackage = "macos-arm64";
         }
         else
         {
            archPackage = "macos-x86_64";
         }
         platformLibs = new PlatformLibs("fastddsjava.native." + archPackage, "libjnifastddsjava.dylib",
                                         new String[] {"libfastcdr.2.3.5.dylib", "libfastdds.3.6.2.dylib"});
      }
      else
      {
         throw new RuntimeException("Unsupported operating system: " + os);
      }

      return platformLibs;
   }

   private static final class PlatformLibs
   {
      final String resourcePackage;
      final String jniLibrary;
      final String[] dependencies;

      PlatformLibs(String resourcePackage, String jniLibrary, String[] dependencies)
      {
         this.resourcePackage = resourcePackage;
         this.jniLibrary = jniLibrary;
         this.dependencies = dependencies;
      }
   }
}
