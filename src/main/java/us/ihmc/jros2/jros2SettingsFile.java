package us.ihmc.jros2;

import us.ihmc.log.LogTools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

/**
 * Settings for jros2, parsed from $HOME/.ihmc/jros2.properties
 */
class jros2SettingsFile implements jros2Settings
{
   static final String DOMAIN_ID_KEY = "jros2DefaultDomainID";
   static final String INTERFACE_WHITELIST_KEY = "jros2AddressWhitelist";

   private static final Path DEFAULT_FILE_PATH = Path.of(System.getProperty("user.home"), ".ihmc", "jros2.properties");
   private static final Path COMPATIBILITY_FILE_PATH = Path.of(System.getProperty("user.home"), ".ihmc", "IHMCNetworkParameters.ini");

   private final Path filePath;
   private final Path compatibilityFilePath;

   private int defaultDomainId;
   private boolean hasDefaultDomainId;

   private String[] interfaceWhitelist;
   private boolean hasInterfaceWhitelist;

   jros2SettingsFile()
   {
      this(DEFAULT_FILE_PATH, COMPATIBILITY_FILE_PATH);
   }

   jros2SettingsFile(Path filePath, Path compatibilityFilePath)
   {
      this.filePath = filePath;
      this.compatibilityFilePath = compatibilityFilePath;

      jros2SettingsDefault defaults = new jros2SettingsDefault();
      defaultDomainId = defaults.defaultDomainId(); // Default ROS 2 domain ID
      interfaceWhitelist = defaults.interfaceWhitelist();

      hasDefaultDomainId = false;
      hasInterfaceWhitelist = false;

      load();
   }

   private void createNewSettingsFile() throws IOException
   {
      File file = filePath.toFile();

      file.getParentFile().mkdirs();
      file.createNewFile();

      // If the compatibility file exists, read values from it
      File compatibilityFile = compatibilityFilePath.toFile();
      if (compatibilityFile.exists())
      {
         setFromCompatibilityFile(compatibilityFile);
      }

      // Create the properties
      Properties properties = new Properties();
      properties.setProperty(DOMAIN_ID_KEY, String.valueOf(defaultDomainId));
      if (interfaceWhitelist.length > 0)
         properties.setProperty(INTERFACE_WHITELIST_KEY, String.join(", ", interfaceWhitelist));

      // Write them out
      try (FileOutputStream output = new FileOutputStream(file))
      {
         properties.store(output, null);
      }
   }

   private void load()
   {
      File file = filePath.toFile();

      if (!file.exists())
      {
         try
         {
            createNewSettingsFile();
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
         defaultDomainId = Integer.parseInt(properties.getProperty(DOMAIN_ID_KEY));
         hasDefaultDomainId = true;
      }
      catch (NumberFormatException e)
      {
         LogTools.error(e);
         LogTools.error("Could not parse jros2DefaultDomainID in {}", file.getAbsolutePath());
      }

      if (properties.containsKey(INTERFACE_WHITELIST_KEY))
      {
         interfaceWhitelist = properties.getProperty(INTERFACE_WHITELIST_KEY).split("\\s*,\\s*"); // Split CSV into multiple strings
         hasInterfaceWhitelist = true;
      }
      else
      {
         interfaceWhitelist = new String[0];
      }
   }

   private void setFromCompatibilityFile(File compatibilityFile) throws IOException
   {
      Properties compatibilityProperties = new Properties();

      try (FileInputStream input = new FileInputStream(compatibilityFile))
      {
         compatibilityProperties.load(input);
      }

      String rtpsDomainId = compatibilityProperties.getProperty("RTPSDomainID");
      if (rtpsDomainId != null)
      {
         try
         {
            defaultDomainId = Integer.parseInt(rtpsDomainId);
         }
         catch (NumberFormatException numberFormatException)
         {
            LogTools.warn("Found RTPSDomainID in {}, but failed to parse the value ({}).", compatibilityFilePath.getFileName(), rtpsDomainId);
         }
      }

      String interfaceWhitelistCSV = compatibilityProperties.getProperty("RTPSSubnet");
      if (interfaceWhitelistCSV != null)
      {
         interfaceWhitelist = Arrays.stream(interfaceWhitelistCSV.split("\\s*,\\s*")) // Split CSV into multiple strings
                                    .filter(s -> !s.isEmpty()) // Remove empty strings
                                    .toArray(String[]::new); // Create the array of strings
      }
   }

   @Override
   public int defaultDomainId()
   {
      return defaultDomainId;
   }

   @Override
   public boolean hasDefaultDomainId()
   {
      return hasDefaultDomainId;
   }

   @Override
   public String[] interfaceWhitelist()
   {
      return interfaceWhitelist;
   }

   @Override
   public boolean hasInterfaceWhitelist()
   {
      return hasInterfaceWhitelist;
   }
}
