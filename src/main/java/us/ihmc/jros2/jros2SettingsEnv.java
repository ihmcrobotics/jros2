package us.ihmc.jros2;

import java.util.Map;

/**
 * Settings for jros2, parsed from environment variables
 */
class jros2SettingsEnv implements jros2Settings
{
   static final String DOMAIN_ID_KEY = "ROS_DOMAIN_ID";
   static final String INTERFACE_WHITELIST_KEY = "ROS_ADDRESS_RESTRICTION";

   private final Map<String, String> env;

   jros2SettingsEnv()
   {
      this(System.getenv());
   }

   /**
    * Constructor mainly for unit tests, so a fake environment can be given.
    *
    * @param env The system environment.
    */
   jros2SettingsEnv(Map<String, String> env)
   {
      this.env = env;
   }

   @Override
   public int defaultDomainId()
   {
      try
      {
         return Integer.parseInt(env.get(DOMAIN_ID_KEY));
      }
      catch (NumberFormatException ignored)
      {
         return new jros2SettingsDefault().defaultDomainId();
      }
   }

   @Override
   public boolean hasDefaultDomainId()
   {
      return env.containsKey(DOMAIN_ID_KEY);
   }

   @Override
   public String[] interfaceWhitelist()
   {
      String property = env.get(INTERFACE_WHITELIST_KEY);
      if (property != null)
      {
         return property.split("\\s*,\\s*");
      }
      else
      {
         return new jros2SettingsDefault().interfaceWhitelist();
      }
   }

   @Override
   public boolean hasInterfaceWhitelist()
   {
      return env.containsKey(INTERFACE_WHITELIST_KEY);
   }
}
