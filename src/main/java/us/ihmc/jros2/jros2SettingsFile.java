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
class jros2SettingsFile implements jros2Settings
{
   private static final Path FILE_PATH = Path.of(System.getProperty("user.home"), ".ihmc", "jros2.properties");
   private static final Path COMPATIBILITY_FILE_PATH = Path.of(System.getProperty("user.home"), ".ihmc", "IHMCNetworkParameters.ini");

   private int defaultDomainId;
   private String[] interfaceWhitelist;

   jros2SettingsFile()
   {
      defaultDomainId = 0; // Default ROS 2 domain ID
      interfaceWhitelist = new String[] {};
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

   @Override
   public int defaultDomainId()
   {
      return defaultDomainId;
   }

   @Override
   public String[] interfaceWhitelist()
   {
      return interfaceWhitelist;
   }
}
