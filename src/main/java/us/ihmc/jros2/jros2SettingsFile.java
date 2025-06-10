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
   private static final String SOURCE_NAME = "jros2.properties";

   static final String DOMAIN_ID_KEY = "jros2.ros.domain.id";
   static final String INTERFACE_WHITELIST_KEY = "jros2.fastdds.interface.whitelist";

   private static final Path DEFAULT_FILE_PATH = Path.of(System.getProperty("user.home"), ".ihmc", "jros2.properties");
   private static final Path COMPATIBILITY_FILE_PATH = Path.of(System.getProperty("user.home"), ".ihmc", "IHMCNetworkParameters.ini");
   private static final jros2SettingsDefault DEFAULTS = new jros2SettingsDefault();

   private final Path filePath;
   private final Path compatibilityFilePath;
   private int rosDomainId;
   private String[] interfaceWhitelist;

   jros2SettingsFile()
   {
      this(DEFAULT_FILE_PATH, COMPATIBILITY_FILE_PATH);
   }

   jros2SettingsFile(Path filePath, Path compatibilityFilePath)
   {
      this.filePath = filePath;
      this.compatibilityFilePath = compatibilityFilePath;

      rosDomainId = DEFAULTS.rosDomainId();
      interfaceWhitelist = DEFAULTS.interfaceWhitelist();

      try
      {
         load();
      }
      catch (IOException ignored)
      {
      }

      if (!filePath.toFile().exists())
      {
         LogTools.error("There was an issue creating the jros2 settings file: {}", filePath.toFile().getAbsolutePath());
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
      properties.setProperty(INTERFACE_WHITELIST_KEY, String.join(", ", interfaceWhitelist));

      try (FileOutputStream output = new FileOutputStream(file))
      {
         properties.store(output, null);
      }
   }

   private static int deleteRetries = 0;

   private void load() throws IOException
   {
      File file = filePath.toFile();

      if (!file.exists())
      {
         createNewSettingsFile();
      }

      Properties properties = new Properties();

      try (FileInputStream input = new FileInputStream(file))
      {
         properties.load(input);
      }

      try
      {
         rosDomainId = Integer.parseInt(properties.getProperty(DOMAIN_ID_KEY));
         interfaceWhitelist = jros2Settings.splitInterfaceWhitelistFromCSV(properties.getProperty(INTERFACE_WHITELIST_KEY));
      }
      catch (Exception e)
      {
         // Possibly malformed keys or values, reset the file content
         if (deleteRetries++ < 10 && file.delete())
         {
            createNewSettingsFile();
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
            LogTools.warn("Found RTPSDomainID in {}, but failed to parse the value ({}).", compatibilityFilePath.getFileName(), rtpsDomainId);
         }
      }
   }

   @Override
   public String getSourceName()
   {
      return SOURCE_NAME;
   }

   @Override
   public int rosDomainId()
   {
      return rosDomainId;
   }

   @Override
   public boolean hasROSDomainId()
   {
      return DEFAULTS.rosDomainId() != rosDomainId;
   }

   @Override
   public String[] interfaceWhitelist()
   {
      return interfaceWhitelist;
   }

   @Override
   public boolean hasInterfaceWhitelist()
   {
      return !Arrays.equals(DEFAULTS.interfaceWhitelist(), interfaceWhitelist);
   }
}
