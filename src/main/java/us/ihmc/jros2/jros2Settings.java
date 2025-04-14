package us.ihmc.jros2;

import us.ihmc.log.LogTools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Settings for jros2, parsed from $HOME/.ihmc/jros2.properties
 */
class jros2Settings
{
   private static final Path FILE_PATH = Path.of(System.getProperty("user.home"), ".ihmc", "jros2.properties");

   private int defaultDomainId;
   private String[] interfaceWhitelist;

   jros2Settings()
   {
      defaultDomainId = 0; // Default ROS 2 domain ID
      interfaceWhitelist = new String[] {};
   }

   /**
    * A list of addresses (IPv4 or IPv6) which correspond to network interfaces on the host system.
    * {@link ROS2Node} will only be able to communicate using whitelisted network interfaces.
    * Empty or null for no whitelist.
    * This is a Fast-DDS parameter. See: <a href="https://fast-dds.docs.eprosima.com/en/latest/fastdds/transport/whitelist.html">Interface Whitelist</a>.
    */
   protected int getDefaultDomainId()
   {
      return defaultDomainId;
   }

   /**
    * The default domain ID jros2 will use when constructing new {@link ROS2Node}.
    * See: <a href="https://docs.ros.org/en/humble/Concepts/Intermediate/About-Domain-ID.html#the-ros-domain-id">ROS 2 Domain ID</a>.
    */
   protected String[] getInterfaceWhitelist()
   {
      return interfaceWhitelist;
   }

   protected void save() throws IOException
   {
      File file = FILE_PATH.toFile();

      file.getParentFile().mkdirs();
      file.createNewFile();

      Properties properties = new Properties();
      properties.setProperty("jros2DefaultDomainID", String.valueOf(defaultDomainId));
      properties.setProperty("jros2AddressWhitelist", String.join(", ", interfaceWhitelist));

      try (FileOutputStream output = new FileOutputStream(file))
      {
         properties.store(output, null);
      }
   }

   protected void load()
   {
      File file = FILE_PATH.toFile();

      if (!file.exists())
      {
         try
         {
            save();
         }
         catch (IOException e)
         {
            LogTools.error(e);
            LogTools.error("Could not create {}", file.getAbsolutePath());
            return;
         }
      }

      Properties properties = new Properties();

      try (FileInputStream input = new FileInputStream(file))
      {
         properties.load(input);
      }
      catch (IOException e)
      {
         LogTools.error(e);
         LogTools.error("Could not parse {}", file.getAbsolutePath());
         return;
      }

      try
      {
         defaultDomainId = Integer.parseInt(properties.getProperty("jros2DefaultDomainID"));
      }
      catch (NumberFormatException e)
      {
         LogTools.error(e);
         LogTools.error("Could not parse jros2DefaultDomainID in {}", file.getAbsolutePath());
      }

      interfaceWhitelist = properties.getProperty("jros2AddressWhitelist").split("\\s*,\\s*");
   }
}
