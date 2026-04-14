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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class jros2SettingsTest
{
   // ROS 2 domain ID valid range is 0-232 (DDS domain ID limit)
   private static final int TEST_DOMAIN_ID = 42;
   private static final int MAX_DOMAIN_ID = 232;
   private static final boolean TEST_INTRAPROCESS = true;
   private static final String[] TEST_INTERFACE_WHITELIST = {"127.0.0.1", "192.168.1.1"};

   @AfterEach
   public void cleanup()
   {
      // Clear system properties after each test
      System.clearProperty(jros2SettingsProp.DOMAIN_ID_KEY);
      System.clearProperty(jros2SettingsProp.INTRAPROCESS_DELIVERY_KEY);
      System.clearProperty(jros2SettingsProp.INTERFACE_WHITELIST_KEY);
   }

   @Test
   public void testDefaultSettings()
   {
      jros2SettingsDefault defaults = new jros2SettingsDefault();

      assertTrue(defaults.hasROSDomainId());
      assertTrue(defaults.hasIntraprocessDelivery());
      assertTrue(defaults.hasInterfaceWhitelist());

      assertEquals(0, defaults.rosDomainId());
      // Intraprocess delivery is enabled in GitHub CI, disabled otherwise
      boolean isCI = System.getenv().containsKey("GITHUB_ACTIONS");
      assertEquals(isCI, defaults.intraprocessDelivery());
      assertEquals(0, defaults.interfaceWhitelist().length);
   }

   @Test
   public void testSystemPropertySettings()
   {
      jros2SettingsProp propSettings = new jros2SettingsProp();

      // Initially no properties set
      assertFalse(propSettings.hasROSDomainId());
      assertFalse(propSettings.hasIntraprocessDelivery());
      assertFalse(propSettings.hasInterfaceWhitelist());

      // Set properties
      System.setProperty(jros2SettingsProp.DOMAIN_ID_KEY, String.valueOf(TEST_DOMAIN_ID));
      System.setProperty(jros2SettingsProp.INTRAPROCESS_DELIVERY_KEY, String.valueOf(TEST_INTRAPROCESS));
      System.setProperty(jros2SettingsProp.INTERFACE_WHITELIST_KEY, String.join(", ", TEST_INTERFACE_WHITELIST));

      // Create new instance to read updated properties
      propSettings = new jros2SettingsProp();

      assertTrue(propSettings.hasROSDomainId());
      assertTrue(propSettings.hasIntraprocessDelivery());
      assertTrue(propSettings.hasInterfaceWhitelist());

      assertEquals(TEST_DOMAIN_ID, propSettings.rosDomainId());
      assertEquals(TEST_INTRAPROCESS, propSettings.intraprocessDelivery());
      assertArrayEquals(TEST_INTERFACE_WHITELIST, propSettings.interfaceWhitelist());
   }

   @Test
   public void testEnvironmentSettings()
   {
      // Empty environment
      Map<String, String> emptyEnv = Map.of();
      jros2SettingsEnv envSettings = new jros2SettingsEnv(emptyEnv);

      assertFalse(envSettings.hasROSDomainId());
      assertFalse(envSettings.hasIntraprocessDelivery());
      assertFalse(envSettings.hasInterfaceWhitelist());

      // Environment with values
      Map<String, String> env = Map.of(jros2SettingsEnv.DOMAIN_ID_KEY,
                                       String.valueOf(TEST_DOMAIN_ID),
                                       jros2SettingsEnv.INTRAPROCESS_DELIVERY_KEY,
                                       String.valueOf(TEST_INTRAPROCESS),
                                       jros2SettingsEnv.INTERFACE_WHITELIST_KEY,
                                       String.join(", ", TEST_INTERFACE_WHITELIST));
      envSettings = new jros2SettingsEnv(env);

      assertTrue(envSettings.hasROSDomainId());
      assertTrue(envSettings.hasIntraprocessDelivery());
      assertTrue(envSettings.hasInterfaceWhitelist());

      assertEquals(TEST_DOMAIN_ID, envSettings.rosDomainId());
      assertEquals(TEST_INTRAPROCESS, envSettings.intraprocessDelivery());
      assertArrayEquals(TEST_INTERFACE_WHITELIST, envSettings.interfaceWhitelist());
   }

   @Test
   public void testFileSettingsCreatesDefaultFile(@TempDir Path tempDir) throws IOException
   {
      Path propertiesPath = tempDir.resolve("jros2.properties");
      Path compatibilityPath = tempDir.resolve("IHMCNetworkParameters.ini");

      assertFalse(Files.exists(propertiesPath));

      jros2SettingsFile fileSettings = new jros2SettingsFile(propertiesPath, compatibilityPath);

      // Should create the file with default values
      assertTrue(Files.exists(propertiesPath));
      assertTrue(fileSettings.hasROSDomainId());
      assertTrue(fileSettings.hasIntraprocessDelivery());

      // Values should match defaults
      jros2SettingsDefault defaults = new jros2SettingsDefault();
      assertEquals(defaults.rosDomainId(), fileSettings.rosDomainId());
      assertEquals(defaults.intraprocessDelivery(), fileSettings.intraprocessDelivery());
   }

   @Test
   public void testFileSettingsWithExistingFile(@TempDir Path tempDir) throws IOException
   {
      Path propertiesPath = tempDir.resolve("jros2.properties");
      Path compatibilityPath = tempDir.resolve("IHMCNetworkParameters.ini");

      // Create file with test values
      writePropertiesFile(propertiesPath, TEST_DOMAIN_ID, TEST_INTRAPROCESS, TEST_INTERFACE_WHITELIST);

      jros2SettingsFile fileSettings = new jros2SettingsFile(propertiesPath, compatibilityPath);

      assertTrue(fileSettings.hasROSDomainId());
      assertTrue(fileSettings.hasIntraprocessDelivery());
      assertTrue(fileSettings.hasInterfaceWhitelist());

      assertEquals(TEST_DOMAIN_ID, fileSettings.rosDomainId());
      assertEquals(TEST_INTRAPROCESS, fileSettings.intraprocessDelivery());
      assertArrayEquals(TEST_INTERFACE_WHITELIST, fileSettings.interfaceWhitelist());
   }

   @Test
   public void testFileSettingsCompatibilityFile(@TempDir Path tempDir) throws IOException
   {
      Path propertiesPath = tempDir.resolve("jros2.properties");
      Path compatibilityPath = tempDir.resolve("IHMCNetworkParameters.ini");

      // Create only compatibility file (legacy)
      Properties compatibilityProps = new Properties();
      compatibilityProps.setProperty("RTPSDomainID", String.valueOf(TEST_DOMAIN_ID));
      try (FileOutputStream out = new FileOutputStream(compatibilityPath.toFile()))
      {
         compatibilityProps.store(out, null);
      }

      jros2SettingsFile fileSettings = new jros2SettingsFile(propertiesPath, compatibilityPath);

      // Should create jros2.properties and migrate domain ID from compatibility file
      assertTrue(Files.exists(propertiesPath));
      assertTrue(fileSettings.hasROSDomainId());
      assertEquals(TEST_DOMAIN_ID, fileSettings.rosDomainId());
   }

   @Test
   public void testFileSettingsPriorityOverCompatibility(@TempDir Path tempDir) throws IOException
   {
      Path propertiesPath = tempDir.resolve("jros2.properties");
      Path compatibilityPath = tempDir.resolve("IHMCNetworkParameters.ini");

      // Create both files with different values
      writePropertiesFile(propertiesPath, TEST_DOMAIN_ID, TEST_INTRAPROCESS, TEST_INTERFACE_WHITELIST);

      Properties compatibilityProps = new Properties();
      compatibilityProps.setProperty("RTPSDomainID", String.valueOf(100)); // Different from TEST_DOMAIN_ID
      try (FileOutputStream out = new FileOutputStream(compatibilityPath.toFile()))
      {
         compatibilityProps.store(out, null);
      }

      jros2SettingsFile fileSettings = new jros2SettingsFile(propertiesPath, compatibilityPath);

      // jros2.properties should take precedence
      assertEquals(TEST_DOMAIN_ID, fileSettings.rosDomainId());
   }

   @Test
   public void testFileSettingsLoadOrderCurrentWorkingDirectory(@TempDir Path tempDir) throws IOException
   {
      // Test that CWD file takes priority over home directory file
      // Note: We test by creating jros2.properties in a known location that simulates CWD behavior

      Path cwdPropertiesPath = Path.of("jros2.properties");
      Path homePropertiesPath = tempDir.resolve("jros2.properties");
      Path compatibilityPath = tempDir.resolve("IHMCNetworkParameters.ini");

      File cwdFile = cwdPropertiesPath.toFile();
      boolean cwdFileExisted = cwdFile.exists();

      try
      {
         // Create file in CWD with specific domain ID
         int cwdDomainId = 10;
         writePropertiesFile(cwdPropertiesPath, cwdDomainId, false, new String[] {});

         // Create file in home directory with different domain ID
         writePropertiesFile(homePropertiesPath, 20, false, new String[] {});

         jros2SettingsFile fileSettings = new jros2SettingsFile(homePropertiesPath, compatibilityPath);

         // Should load from current working directory (priority 1)
         assertEquals(cwdDomainId, fileSettings.rosDomainId());
         assertTrue(fileSettings.getSourceName().contains("jros2.properties"));
      }
      finally
      {
         // Cleanup: only delete if we created it
         if (!cwdFileExisted && cwdFile.exists())
         {
            cwdFile.delete();
         }
      }
   }

   @Test
   public void testFileSettingsLoadOrderHomeDirectory(@TempDir Path tempDir) throws IOException
   {
      Path homePropertiesPath = tempDir.resolve("jros2.properties");
      Path compatibilityPath = tempDir.resolve("IHMCNetworkParameters.ini");

      // Only create file in home directory (no CWD file, no JAR resource)
      int homeDomainId = 30;
      writePropertiesFile(homePropertiesPath, homeDomainId, false, new String[] {});

      jros2SettingsFile fileSettings = new jros2SettingsFile(homePropertiesPath, compatibilityPath);

      // Should load from home directory (priority 3)
      assertEquals(homeDomainId, fileSettings.rosDomainId());
      assertTrue(fileSettings.getSourceName().contains(homePropertiesPath.toString()));
   }

   @Test
   public void testSettingsPriorityOrder(@TempDir Path tempDir) throws IOException
   {
      // Setup: Create file settings with domain ID 50
      Path propertiesPath = tempDir.resolve("jros2.properties");
      Path compatibilityPath = tempDir.resolve("IHMCNetworkParameters.ini");
      writePropertiesFile(propertiesPath, 50, false, new String[] {});

      // Setup: Create environment settings with domain ID 100
      Map<String, String> env = Map.of(jros2SettingsEnv.DOMAIN_ID_KEY, "100");

      // Setup: Create system property settings with domain ID 150
      System.setProperty(jros2SettingsProp.DOMAIN_ID_KEY, "150");

      // Create jros2 instance with all settings sources
      jros2Settings[] sources = new jros2Settings[] {new jros2SettingsProp(),        // Priority 1: System properties (150)
                                                     new jros2SettingsEnv(env),      // Priority 2: Environment (100)
                                                     new jros2SettingsFile(propertiesPath, compatibilityPath), // Priority 3: File (50)
                                                     new jros2SettingsDefault()      // Priority 4: Defaults (0)
      };

      jros2 instance = new jros2(sources);

      // Should use system properties (highest priority)
      assertEquals(150, instance.rosDomainId());

      // Clear system properties and recreate
      System.clearProperty(jros2SettingsProp.DOMAIN_ID_KEY);
      sources = new jros2Settings[] {new jros2SettingsProp(),
                                     new jros2SettingsEnv(env),
                                     new jros2SettingsFile(propertiesPath, compatibilityPath),
                                     new jros2SettingsDefault()};
      instance = new jros2(sources);

      // Should now use environment variables
      assertEquals(100, instance.rosDomainId());
   }

   @Test
   public void testDomainIdBoundaries(@TempDir Path tempDir) throws IOException
   {
      Path propertiesPath = tempDir.resolve("jros2.properties");
      Path compatibilityPath = tempDir.resolve("IHMCNetworkParameters.ini");

      // Test minimum valid domain ID (0)
      writePropertiesFile(propertiesPath, 0, false, new String[]{});
      jros2SettingsFile fileSettings = new jros2SettingsFile(propertiesPath, compatibilityPath);
      assertEquals(0, fileSettings.rosDomainId());

      // Test maximum valid domain ID (232)
      Files.delete(propertiesPath);
      writePropertiesFile(propertiesPath, MAX_DOMAIN_ID, false, new String[]{});
      fileSettings = new jros2SettingsFile(propertiesPath, compatibilityPath);
      assertEquals(MAX_DOMAIN_ID, fileSettings.rosDomainId());

      // Test a typical mid-range domain ID
      Files.delete(propertiesPath);
      writePropertiesFile(propertiesPath, TEST_DOMAIN_ID, false, new String[]{});
      fileSettings = new jros2SettingsFile(propertiesPath, compatibilityPath);
      assertEquals(TEST_DOMAIN_ID, fileSettings.rosDomainId());
   }

   @Test
   public void testInterfaceWhitelistParsing()
   {
      String[] interfaces = {"eth0", "wlan0", "lo"};
      String csv = String.join(", ", interfaces);

      System.setProperty(jros2SettingsProp.INTERFACE_WHITELIST_KEY, csv);

      jros2SettingsProp propSettings = new jros2SettingsProp();

      assertTrue(propSettings.hasInterfaceWhitelist());
      assertArrayEquals(interfaces, propSettings.interfaceWhitelist());
   }

   @Test
   public void testInterfaceWhitelistWithSpaces()
   {
      // The regex \s*,\s* splits on comma and trims spaces around commas,
      // but doesn't trim leading/trailing spaces from the entire string
      String csv = "eth0  ,  wlan0  ,  lo";
      String[] expected = {"eth0", "wlan0", "lo"};

      System.setProperty(jros2SettingsProp.INTERFACE_WHITELIST_KEY, csv);

      jros2SettingsProp propSettings = new jros2SettingsProp();

      assertArrayEquals(expected, propSettings.interfaceWhitelist());
   }

   @Test
   public void testMalformedPropertiesFileRecovery(@TempDir Path tempDir) throws IOException
   {
      Path propertiesPath = tempDir.resolve("jros2.properties");
      Path compatibilityPath = tempDir.resolve("IHMCNetworkParameters.ini");

      // Create malformed properties file
      Files.writeString(propertiesPath, "jros2.ros.domain.id=not_a_number\n");

      jros2SettingsFile fileSettings = new jros2SettingsFile(propertiesPath, compatibilityPath);

      // Should recover by recreating the file with defaults
      assertTrue(Files.exists(propertiesPath));
      assertTrue(fileSettings.hasROSDomainId());

      // Should have default values after recovery
      jros2SettingsDefault defaults = new jros2SettingsDefault();
      assertEquals(defaults.rosDomainId(), fileSettings.rosDomainId());
   }

   @Test
   public void testAllSettingsExist(@TempDir Path tempDir) throws IOException
   {
      // Create comprehensive settings across all sources (all domain IDs <= 232)
      Path propertiesPath = tempDir.resolve("jros2.properties");
      Path compatibilityPath = tempDir.resolve("IHMCNetworkParameters.ini");
      writePropertiesFile(propertiesPath, 50, true, new String[] {"192.168.1.1"});

      Map<String, String> env = Map.of(jros2SettingsEnv.DOMAIN_ID_KEY,
                                       "100",
                                       jros2SettingsEnv.INTRAPROCESS_DELIVERY_KEY,
                                       "false",
                                       jros2SettingsEnv.INTERFACE_WHITELIST_KEY,
                                       "10.0.0.1");

      System.setProperty(jros2SettingsProp.DOMAIN_ID_KEY, "150");
      System.setProperty(jros2SettingsProp.INTRAPROCESS_DELIVERY_KEY, "true");
      System.setProperty(jros2SettingsProp.INTERFACE_WHITELIST_KEY, "172.16.0.1");

      jros2Settings[] sources = new jros2Settings[] {new jros2SettingsProp(),
                                                     new jros2SettingsEnv(env),
                                                     new jros2SettingsFile(propertiesPath, compatibilityPath),
                                                     new jros2SettingsDefault()};

      jros2 instance = new jros2(sources);

      // Verify highest priority wins for each setting
      assertEquals(150, instance.rosDomainId());
      assertTrue(instance.intraprocessDelivery());
      assertEquals(1, instance.interfaceWhitelist().length);
      assertEquals("172.16.0.1", instance.interfaceWhitelist()[0]);

      assertTrue(instance.hasROSDomainId());
      assertTrue(instance.hasIntraprocessDelivery());
      assertTrue(instance.hasInterfaceWhitelist());
   }

   // Helper method to write properties file
   private void writePropertiesFile(Path path, int domainId, boolean intraprocess, String[] interfaceWhitelist) throws IOException
   {
      Properties props = new Properties();
      props.setProperty(jros2SettingsFile.DOMAIN_ID_KEY, String.valueOf(domainId));
      props.setProperty(jros2SettingsFile.INTRAPROCESS_DELIVERY_KEY, String.valueOf(intraprocess));
      props.setProperty(jros2SettingsFile.INTERFACE_WHITELIST_KEY, String.join(", ", interfaceWhitelist));

      // Create parent directories if path has a parent
      if (path.getParent() != null)
      {
         Files.createDirectories(path.getParent());
      }

      try (FileOutputStream out = new FileOutputStream(path.toFile()))
      {
         props.store(out, null);
      }
   }
}
