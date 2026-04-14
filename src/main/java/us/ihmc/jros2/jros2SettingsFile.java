/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.jros2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

/**
 * Settings for jros2, parsed from jros2.properties file.
 * Searches for jros2.properties in the following order:
 * <ol>
 *    <li>Current working directory (./jros2.properties)</li>
 *    <li>JAR resources (/jros2.properties)</li>
 *    <li>User home directory ($HOME/.ihmc/jros2.properties)</li>
 * </ol>
 */
class jros2SettingsFile implements jros2Settings
{
   private static final String SOURCE_NAME = "jros2.properties";

   static final String DOMAIN_ID_KEY = "jros2.ros.domain.id";
   static final String INTRAPROCESS_DELIVERY_KEY = "jros2.fastdds.intraprocess.delivery";
   static final String INTERFACE_WHITELIST_KEY = "jros2.fastdds.interface.whitelist";

   private static final Path DEFAULT_FILE_PATH = Path.of(System.getProperty("user.home"), ".ihmc", "jros2.properties");
   private static final Path COMPATIBILITY_FILE_PATH = Path.of(System.getProperty("user.home"), ".ihmc", "IHMCNetworkParameters.ini");
   private static final jros2SettingsDefault DEFAULTS = new jros2SettingsDefault();

   private final Path filePath;
   private final Path compatibilityFilePath;
   private int rosDomainId;
   private boolean intraprocessDelivery;
   private String[] interfaceWhitelist;

   private boolean fileExists;
   private String loadedFrom;

   jros2SettingsFile()
   {
      this(DEFAULT_FILE_PATH, COMPATIBILITY_FILE_PATH);
   }

   jros2SettingsFile(Path filePath, Path compatibilityFilePath)
   {
      this.filePath = filePath;
      this.compatibilityFilePath = compatibilityFilePath;

      rosDomainId = DEFAULTS.rosDomainId();
      intraprocessDelivery = DEFAULTS.intraprocessDelivery();
      interfaceWhitelist = DEFAULTS.interfaceWhitelist();
      fileExists = false;
      loadedFrom = null;

      try
      {
         load();
      }
      catch (IOException ignored)
      {
      }

      if (!fileExists)
      {
         jros2.getLogger().fine("No jros2.properties file found in current directory, JAR resources, or " + filePath.toFile().getAbsolutePath());
      }
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

      Properties properties = new Properties();
      properties.setProperty(DOMAIN_ID_KEY, String.valueOf(rosDomainId));
      properties.setProperty(INTRAPROCESS_DELIVERY_KEY, String.valueOf(intraprocessDelivery));
      properties.setProperty(INTERFACE_WHITELIST_KEY, String.join(", ", interfaceWhitelist));

      try (FileOutputStream output = new FileOutputStream(file))
      {
         properties.store(output, null);
      }
   }

   private static int deleteRetries = 0;

   private void load() throws IOException
   {
      Properties properties = new Properties();
      boolean loaded = false;

      // 1. Try loading from current working directory
      File cwdFile = new File("jros2.properties");
      if (cwdFile.exists())
      {
         try (FileInputStream input = new FileInputStream(cwdFile))
         {
            properties.load(input);
            loaded = true;
            fileExists = true;
            loadedFrom = cwdFile.getAbsolutePath();
            jros2.getLogger().fine("Loaded jros2.properties from current working directory: " + loadedFrom);
         }
         catch (IOException e)
         {
            jros2.getLogger().warning("Found jros2.properties in current directory but failed to load: " + e.getMessage());
         }
      }

      // 2. Try loading from JAR resources
      if (!loaded)
      {
         try (InputStream resourceStream = jros2SettingsFile.class.getResourceAsStream("/jros2.properties"))
         {
            if (resourceStream != null)
            {
               properties.load(resourceStream);
               loaded = true;
               fileExists = true;
               loadedFrom = "JAR resources (/jros2.properties)";
               jros2.getLogger().fine("Loaded jros2.properties from JAR resources");
            }
         }
         catch (IOException e)
         {
            jros2.getLogger().warning("Found jros2.properties in JAR resources but failed to load: " + e.getMessage());
         }
      }

      // 3. Try loading from user home directory (~/.ihmc/jros2.properties)
      if (!loaded)
      {
         File file = filePath.toFile();

         if (!file.exists())
         {
            createNewSettingsFile();
         }

         try (FileInputStream input = new FileInputStream(file))
         {
            properties.load(input);
            loaded = true;
            fileExists = true;
            loadedFrom = file.getAbsolutePath();
            jros2.getLogger().fine("Loaded jros2.properties from: " + loadedFrom);
         }
      }

      // Parse properties if any file was loaded
      if (loaded)
      {
         try
         {
            rosDomainId = Integer.parseInt(properties.getProperty(DOMAIN_ID_KEY));
            intraprocessDelivery = Boolean.parseBoolean(properties.getProperty(INTRAPROCESS_DELIVERY_KEY));
            interfaceWhitelist = jros2Settings.splitInterfaceWhitelistFromCSV(properties.getProperty(INTERFACE_WHITELIST_KEY));
         }
         catch (Exception e)
         {
            // Possibly malformed keys or values
            // Only attempt to recreate if it was the user home directory file
            if (loadedFrom != null && loadedFrom.equals(filePath.toFile().getAbsolutePath()))
            {
               File file = filePath.toFile();
               if (deleteRetries++ < 10 && file.delete())
               {
                  createNewSettingsFile();
               }
            }
            else
            {
               jros2.getLogger().warning("Failed to parse jros2.properties from " + loadedFrom + ": " + e.getMessage());
            }
         }
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
            rosDomainId = Integer.parseInt(rtpsDomainId);
         }
         catch (NumberFormatException numberFormatException)
         {
            jros2.getLogger().warning("Found RTPSDomainID in " + compatibilityFilePath.getFileName() + ", but failed to parse the value (" + rtpsDomainId + ").");
         }
      }
   }

   @Override
   public String getSourceName()
   {
      return loadedFrom != null ? loadedFrom : SOURCE_NAME;
   }

   @Override
   public int rosDomainId()
   {
      return rosDomainId;
   }

   @Override
   public boolean hasROSDomainId()
   {
      return fileExists;
   }

   @Override
   public boolean intraprocessDelivery()
   {
      return intraprocessDelivery;
   }

   @Override
   public boolean hasIntraprocessDelivery()
   {
      return fileExists;
   }

   @Override
   public String[] interfaceWhitelist()
   {
      return interfaceWhitelist;
   }

   @Override
   public boolean hasInterfaceWhitelist()
   {
      return fileExists && !Arrays.equals(DEFAULTS.interfaceWhitelist(), interfaceWhitelist);
   }
}
