package us.ihmc.jros2;

import java.util.Map;

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
      private final Map<String, String> env;

      public jros2SettingsEnv()
      {
         this(System.getenv());
      }

      /**
       * Constructor mainly for unit tests, so a fake environment can be given.
       *
       * @param env The system environment.
       */
      public jros2SettingsEnv(Map<String, String> env)
      {
         this.env = env;
      }

      @Override
      public int defaultDomainId()
      {
         try
         {
            return Integer.parseInt(env.get("ROS_DOMAIN_ID"));
         }
         catch (NumberFormatException ignored)
         {
            return new jros2SettingsDefault().defaultDomainId();
         }
      }

      @Override
      public String[] interfaceWhitelist()
      {
         String property = env.get("ROS_ADDRESS_RESTRICTION");
         if (property != null)
         {
            return property.split("\\s*,\\s*");
         }
         else
         {
            return new jros2SettingsDefault().interfaceWhitelist();
         }
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
         catch (NumberFormatException ignored)
         {
            return new jros2SettingsDefault().defaultDomainId();
         }
      }

      @Override
      public String[] interfaceWhitelist()
      {
         String property = System.getProperty("ros.address.restriction");
         if (property != null)
         {
            return property.split("\\s*,\\s*");
         }
         else
         {
            return new jros2SettingsDefault().interfaceWhitelist();
         }
      }
   }
}
