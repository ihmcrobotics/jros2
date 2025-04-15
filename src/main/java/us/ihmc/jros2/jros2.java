package us.ihmc.jros2;

import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;

final class jros2 implements jros2Settings
{
   private static jros2 instance;

   private final jros2SettingsDefault settingsDefault;
   private final jros2SettingsEnv settingsEnv;
   private final jros2SettingsProp settingsProp;
   private final jros2SettingsFile settingsFile;

   private final boolean loaded;

   private jros2()
   {
      loaded = fastddsjavaNativeLibrary.load();

      settingsDefault = new jros2SettingsDefault();
      settingsEnv = new jros2SettingsEnv();
      settingsProp = new jros2SettingsProp();
      settingsFile = new jros2SettingsFile();

      settingsFile.load();

      instance = this;
   }

   static synchronized void load()
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

   boolean isLoaded()
   {
      return loaded;
   }

   @Override
   public int defaultDomainId()
   {
      int defaultDomainId = 0;

      if (settingsEnv.defaultDomainId() != settingsDefault.defaultDomainId())
      {

      }

      if (settingsProp.defaultDomainId() != settingsDefault.defaultDomainId())
      {

      }

      if (settingsFile.defaultDomainId() != settingsDefault.defaultDomainId())
      {

      }

      return 0;
   }

   @Override
   public String[] interfaceWhitelist()
   {
      if (settingsEnv.interfaceWhitelist() != settingsDefault.interfaceWhitelist())
      {

      }

      if (settingsProp.interfaceWhitelist() != settingsDefault.interfaceWhitelist())
      {

      }

      if (settingsFile.interfaceWhitelist() != settingsDefault.interfaceWhitelist())
      {

      }

      return new String[0];
   }
}
