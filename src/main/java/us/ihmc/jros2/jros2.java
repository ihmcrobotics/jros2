package us.ihmc.jros2;

import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;

final class jros2 implements jros2Settings
{
   private static jros2 instance;

   /**
    * Array of settings sources to query for setting values, in order of priority:
    * <ol>
    *    <li>Java system properties</li>
    *    <li>Environment variables</li>
    *    <li><code>jros2.properties</code> file</li>
    *    <li>Default settings</li>
    * </ol>
    */
   private final jros2Settings[] settingsSources;
   private final boolean loaded;

   private jros2()
   {
      this(new jros2Settings[] {new jros2SettingsProp(), new jros2SettingsEnv(), new jros2SettingsFile(), new jros2SettingsDefault()});
   }

   /**
    * Constructor for unit tests. Do not use in source code.
    * <p>
    * Use {@link #load()} to create a singleton instance and {@link #get()} to access the instance.
    *
    * @param settingsSources Setting sources to use.
    */
   jros2(jros2Settings[] settingsSources)
   {
      this.settingsSources = settingsSources;
      loaded = fastddsjavaNativeLibrary.load();
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
   public int rosDomainId()
   {
      // Loop through setting sources in order of priority
      for (jros2Settings settings : settingsSources)
      {
         // If the source specifies a default domain id, return the value
         if (settings.hasROSDomainId())
         {
            return settings.rosDomainId();
         }
      }

      // Realistically should never reach here
      return settingsSources[settingsSources.length - 1].rosDomainId();
   }

   @Override
   public boolean hasROSDomainId()
   {
      for (jros2Settings settings : settingsSources)
      {
         if (settings.hasROSDomainId())
         {
            return true;
         }
      }

      return false;
   }

   @Override
   public String[] interfaceWhitelist()
   {
      // Loop through setting sources in order of priority
      for (jros2Settings settings : settingsSources)
      {
         // If the source specifies a default domain id, return the value
         if (settings.hasInterfaceWhitelist())
         {
            return settings.interfaceWhitelist();
         }
      }

      // Realistically should never reach here
      return settingsSources[settingsSources.length - 1].interfaceWhitelist();
   }

   @Override
   public boolean hasInterfaceWhitelist()
   {
      for (jros2Settings settings : settingsSources)
      {
         if (settings.hasInterfaceWhitelist())
         {
            return true;
         }
      }

      return false;
   }
}
