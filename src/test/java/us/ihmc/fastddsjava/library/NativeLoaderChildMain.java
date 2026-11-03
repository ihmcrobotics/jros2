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

import us.ihmc.fastddsjava.natives.fastddsjava;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Entry point of the child JVMs started by {@link fastddsjavaNativeLibraryProcessTest}. Creates no DDS entities.
 * <ul>
 *    <li>{@code load}: loads the natives like an application; prints {@code LOADED <dir> ABI <n>} or {@code FAILED <reason>}</li>
 *    <li>{@code early}: queries the ABI version and DDS-Security support before and after loading the natives; prints
 *    {@code EARLY <abi> <security>} and {@code AFTER <abi> <security>}</li>
 *    <li>{@code extract <base> <chunkDelayMillis>}: extracts a deterministic {@link InMemoryNativeBundle} under {@code base};
 *    prints {@code EXTRACTED <dir> <opens> <contentOk>}</li>
 *    <li>{@code twoLoaders}: loads the natives, then loads them again for a second copy of the jros2 classes in another class
 *    loader; prints {@code TWO <loaded> <first dir> <second dir> <abi> <abi of the copy>}</li>
 * </ul>
 */
public final class NativeLoaderChildMain
{
   static final String TEST_PLATFORM = "test-platform";
   static final int TEST_LIBRARY_SIZE = 768 * 1024;
   static final long TEST_SEED = 4242;

   private NativeLoaderChildMain()
   {
   }

   public static void main(String[] args) throws Exception
   {
      switch (args[0])
      {
         case "load":
            if (fastddsjavaNativeLibrary.load())
            {
               System.out.println("LOADED " + fastddsjavaNativeLibrary.getLoadedDirectory() + " ABI " + fastddsjava.abiVersion());
            }
            else
            {
               StringBuilder reason = new StringBuilder();
               for (Throwable t = fastddsjavaNativeLibrary.getLoadError(); t != null; t = t.getCause())
               {
                  reason.append(t).append(" | ");
                  for (Throwable suppressed : t.getSuppressed())
                     reason.append("suppressed ").append(suppressed).append(" | ");
               }
               System.out.println("FAILED " + reason);
            }
            break;
         case "early":
            // Code that asks for the ABI before anything loaded the natives must not pin the answer to ABI v1.
            System.out.println("EARLY " + fastddsjava.abiVersion() + " " + fastddsjava.hasSecurity());
            if (!fastddsjavaNativeLibrary.load())
               throw new IllegalStateException("natives did not load", fastddsjavaNativeLibrary.getLoadError());
            System.out.println("AFTER " + fastddsjava.abiVersion() + " " + fastddsjava.hasSecurity());
            break;
         case "extract":
            Path base = Paths.get(args[1]);
            long delay = Long.parseLong(args[2]);
            InMemoryNativeBundle bundle = InMemoryNativeBundle.deterministic(TEST_PLATFORM, TEST_LIBRARY_SIZE, TEST_SEED, delay);
            Path directory = fastddsjavaNativeLibrary.extract(bundle, base);
            System.out.println("EXTRACTED " + directory + " " + bundle.opens.get() + " " + bundle.matches(directory));
            break;
         case "twoLoaders":
            if (!fastddsjavaNativeLibrary.load())
               throw new IllegalStateException("natives did not load", fastddsjavaNativeLibrary.getLoadError());
            // A second copy of the classes, e.g. a reloaded plugin or another application in the same JVM
            URL location = fastddsjavaNativeLibrary.class.getProtectionDomain().getCodeSource().getLocation();
            try (URLClassLoader other = new URLClassLoader(new URL[] {location}, ClassLoader.getPlatformClassLoader()))
            {
               Class<?> library = Class.forName(fastddsjavaNativeLibrary.class.getName(), true, other);
               boolean loaded = (Boolean) library.getMethod("load").invoke(null);
               Object otherDirectory = library.getMethod("getLoadedDirectory").invoke(null);
               Object abi = Class.forName(fastddsjava.class.getName(), true, other).getMethod("abiVersion").invoke(null);
               System.out.println("TWO " + loaded + " " + fastddsjavaNativeLibrary.getLoadedDirectory() + " " + otherDirectory + " " + fastddsjava.abiVersion() + " " + abi);
            }
            break;
         default:
            throw new IllegalArgumentException("unknown mode " + args[0]);
      }
      System.out.flush();
   }
}
