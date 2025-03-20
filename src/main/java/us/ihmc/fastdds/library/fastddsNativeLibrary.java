package us.ihmc.fastdds.library;

import us.ihmc.tools.nativelibraries.NativeLibraryDescription;
import us.ihmc.tools.nativelibraries.NativeLibraryLoader;
import us.ihmc.tools.nativelibraries.NativeLibraryWithDependencies;

public class fastddsNativeLibrary implements NativeLibraryDescription {
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

      return "fastdds.native." + archPackage;
   }

   @Override
   public NativeLibraryWithDependencies getLibraryWithDependencies(OperatingSystem os, Architecture arch) {
      switch (os) {
         case LINUX64 -> {
            return NativeLibraryWithDependencies.fromFilename("libjnifastdds.so", "libfastcdr.so.2.2.6", "libfastdds.so.3.1.2");
         }
         case WIN64, MACOSX64 -> throw new RuntimeException("Unsupported platform");
      }
      return null;
   }

   private static boolean loaded = false;

   public static boolean load() {
      if (!loaded) {
         fastddsNativeLibrary lib = new fastddsNativeLibrary();
         loaded = NativeLibraryLoader.loadLibrary(lib);
      }
      return loaded;
   }
}