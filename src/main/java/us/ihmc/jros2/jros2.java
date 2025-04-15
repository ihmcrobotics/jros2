package us.ihmc.jros2;

import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;

final class jros2 implements jros2Settings
{
   private static jros2 instance;

   private final jros2SettingsEnv settingsEnv;
   private final jros2SettingsProp settingsProp;
   private final jros2SettingsFile settingsFile;
   private final boolean loaded;

   jros2()
   {
      loaded = fastddsjavaNativeLibrary.load();

      settingsEnv = new jros2SettingsEnv();
      settingsProp = new jros2SettingsProp();
      settingsFile = new jros2SettingsFile();

      settingsFile.load();

      instance = this;
   }

   static void load()
   {
      if (instance == null)
      {
         instance = new jros2();
      }
   }

   static jros2 get()
   {
      if (instance == null)
      {
         throw new RuntimeException("jros2 not initialized");
      }

      return instance;
   }

   public boolean isLoaded()
   {
      return loaded;
   }

   @Override
   public int defaultDomainId()
   {
      int defaultDomainId = 0;

      if (settingsEnv.defaultDomainId() != 0)
      {

      }

      if (settingsProp.defaultDomainId() != 0)
      {

      }

      if (settingsFile.defaultDomainId() != 0)
      {

      }

      return 0;
   }

   @Override
   public String[] interfaceWhitelist()
   {
      if (settingsEnv.interfaceWhitelist().length > 0)
      {

      }

      if (settingsProp.interfaceWhitelist().length > 0)
      {

      }

      if (settingsFile.interfaceWhitelist().length > 0)
      {

      }

      return new String[0];
   }
}
