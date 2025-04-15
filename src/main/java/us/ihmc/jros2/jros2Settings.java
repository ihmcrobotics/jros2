package us.ihmc.jros2;

import us.ihmc.log.LogTools;

interface jros2Settings
{
   /**
    * The default domain ID jros2 will use when constructing new {@link ROS2Node}.
    * See: <a href="https://docs.ros.org/en/humble/Concepts/Intermediate/About-Domain-ID.html#the-ros-domain-id">ROS 2 Domain ID</a>.
    */
   int defaultDomainId();

   /**
    * A list of addresses (IPv4 or IPv6) which correspond to network interfaces on the host system.
    * {@link ROS2Node} will only be able to communicate using whitelisted network interfaces.
    * Empty or null for no whitelist.
    * This is a Fast-DDS parameter. See: <a href="https://fast-dds.docs.eprosima.com/en/latest/fastdds/transport/whitelist.html">Interface Whitelist</a>.
    */
   String[] interfaceWhitelist();

   class jros2SettingsDefault implements jros2Settings
   {
      @Override
      public int defaultDomainId()
      {
         return 0;
      }

      @Override
      public String[] interfaceWhitelist()
      {
         return new String[0];
      }
   }

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
}
