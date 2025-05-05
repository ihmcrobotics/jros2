package us.ihmc.jros2;

/**
 * Settings for jros2, parsed from system properties
 */
class jros2SettingsProp implements jros2Settings
{
   static final String DOMAIN_ID_KEY = "ros.domain.id";
   static final String INTERFACE_WHITELIST_KEY = "fastdds.interface.whitelist";

   @Override
   public int rosDomainId()
   {
      try
      {
         return Integer.parseInt(System.getProperty(DOMAIN_ID_KEY));
      }
      catch (NumberFormatException ignored)
      {
         return new jros2SettingsDefault().rosDomainId();
      }
   }

   @Override
   public boolean hasROSDomainId()
   {
      return System.getProperties().containsKey(DOMAIN_ID_KEY);
   }

   @Override
   public String[] interfaceWhitelist()
   {
      String property = System.getProperty(INTERFACE_WHITELIST_KEY);
      if (property != null)
      {
         return jros2Settings.splitInterfaceWhitelistFromCSV(property);
      }
      else
      {
         return new jros2SettingsDefault().interfaceWhitelist();
      }
   }

   @Override
   public boolean hasInterfaceWhitelist()
   {
      return System.getProperties().containsKey(INTERFACE_WHITELIST_KEY);
   }
}
