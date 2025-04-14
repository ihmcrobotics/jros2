package us.ihmc.jros2;

import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;

class jros2
{
   private static boolean loaded;
   private static jros2Settings settings;

   protected static void loadLibrary()
   {
      if (!loaded)
      {
         fastddsjavaNativeLibrary.load();

         settings = new jros2Settings();
         settings.load();
      }
   }

   protected static jros2Settings getSettings()
   {
      return settings;
   }
}
