package us.ihmc.jros2;

/**
 * Settings for jros2, parsed from environment variables
 */
class jros2SettingsEnv implements jros2Settings
{
   @Override
   public int defaultDomainId()
   {
      try
      {
         return Integer.parseInt(System.getenv("ROS_DOMAIN_ID"));
      }
      catch (NumberFormatException e)
      {
         return 0;
      }
   }

   @Override
   public String[] interfaceWhitelist()
   {
      return System.getProperty("ROS_ADDRESS_RESTRICTION").split("\\s*,\\s*");
   }
}
