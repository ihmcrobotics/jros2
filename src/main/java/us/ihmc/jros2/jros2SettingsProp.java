package us.ihmc.jros2;

import us.ihmc.log.LogTools;

/**
 * Settings for jros2, parsed from system properties
 */
class jros2SettingsProp implements jros2Settings
{
   @Override
   public int defaultDomainId()
   {
      try
      {
         return Integer.parseInt(System.getProperty("ros.domain.id"));
      }
      catch (NumberFormatException e)
      {
         LogTools.error(e);
      }

      return 0;
   }

   @Override
   public String[] interfaceWhitelist()
   {
      return System.getProperty("ros.address.restriction").split("\\s*,\\s*");
   }
}
