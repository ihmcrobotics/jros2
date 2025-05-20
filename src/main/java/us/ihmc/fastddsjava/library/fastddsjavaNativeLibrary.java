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

import us.ihmc.tools.nativelibraries.NativeLibraryDescription;
import us.ihmc.tools.nativelibraries.NativeLibraryLoader;
import us.ihmc.tools.nativelibraries.NativeLibraryWithDependencies;

public class fastddsjavaNativeLibrary implements NativeLibraryDescription {
   @Override
   public String getPackage(OperatingSystem os, Architecture arch) {
      String archPackage = "";
      if (arch == Architecture.x64) {
         archPackage = switch (os) {
            case LINUX64 -> "linux-x86_64";
            case WIN64, MACOSX64 -> throw new RuntimeException("Unsupported platform");
         };
      } else if (arch == Architecture.arm64) {
         throw new RuntimeException("Unsupported platform");
      }

      return "fastddsjava.native." + archPackage;
   }

   @Override
   public NativeLibraryWithDependencies getLibraryWithDependencies(OperatingSystem os, Architecture arch) {
      switch (os) {
         case LINUX64 -> {
            return NativeLibraryWithDependencies.fromFilename("libjnifastddsjava.so", "libfastcdr.so.2.3.0", "libfastdds.so.3.2.2");
         }
         case WIN64, MACOSX64 -> throw new RuntimeException("Unsupported platform");
      }
      return null;
   }

   private static boolean loaded = false;

   public static synchronized boolean load() {
      if (!loaded) {
         fastddsjavaNativeLibrary lib = new fastddsjavaNativeLibrary();
         loaded = NativeLibraryLoader.loadLibrary(lib);
      }
      return loaded;
   }
}