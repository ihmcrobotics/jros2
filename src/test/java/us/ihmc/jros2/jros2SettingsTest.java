package us.ihmc.jros2;

import org.junit.jupiter.api.Test;
import us.ihmc.log.LogTools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class jros2SettingsTest
{
   @Test
   public void testjros2() throws IOException
   {
      final int maskUpperBound = 0b1000;
      final int systemPropertiesSettingsMask = maskUpperBound >> 1;
      final int environmentSettingsMask = maskUpperBound >> 2;
      final int fileSettingsMask = maskUpperBound >> 3;

      final int systemPropertyDomainId = 111;
      final int environmentDomainId = 112;
      final int fileDomainId = 113;

      final String[] systemPropertyInterfaceWhitelist = {"192.0.2.1"};
      final String[] environmentInterfaceWhitelist = {"192.0.2.2", "127.0.0.1"};
      final String[] fileInterfaceWhitelist = {"192.0.2.3", "127.0.0.1", "0.0.0.0"};

      // Temporary paths
      final Path fakeIHMCDirectoryPath = Path.of(System.getProperty("user.home"), ".fake_ihmc");
      final Path fakeFilePath = fakeIHMCDirectoryPath.resolve("fake_jros2Settings.properties");
      final Path fakeCompatibilityFilePath = fakeIHMCDirectoryPath.resolve("fake_IHMCNetworkParameters.ini");

      final File fakeIHMCDirectory = fakeIHMCDirectoryPath.toFile();
      final File fakeFile = fakeFilePath.toFile();
      final File fakeCompatibilityFile = fakeCompatibilityFilePath.toFile();

      final String permutationTableRow = "| %-21s | %-6s | %-10s |%n";

      // Loop over existence permutations (e.g. whether environment variables settings are considered, whether jros2.properties file settings are considered)
      for (int existencePermutation = 0; existencePermutation < maskUpperBound; ++existencePermutation)
      {
         // Loop over hasValues permutations (e.g. if environment variables are considered, do they have values defined)
         for (int hasValuesPermutation = 0; hasValuesPermutation < existencePermutation + 1; hasValuesPermutation++)
         {
            try
            {
               StringBuilder permutationDescription = new StringBuilder(permutationTableRow.formatted("Settings Source", "Exists", "Has Values"));
               permutationDescription.append("-----------------------------------------------\n");
               List<jros2Settings> settingsList = new ArrayList<>();

               ///////////////////////////
               //// SYSTEM PROPERTIES ////
               ///////////////////////////
               boolean systemPropertiesExist = (existencePermutation & systemPropertiesSettingsMask) != 0;
               boolean systemPropertiesHaveValues = (hasValuesPermutation & systemPropertiesSettingsMask) != 0;

               permutationDescription.append(permutationTableRow.formatted("System Properties", systemPropertiesExist, systemPropertiesExist ? systemPropertiesHaveValues : "N/A"));

               // Add system property settings if it should exist
               if (systemPropertiesExist)
               {
                  // If it should have values, set system properties
                  if (systemPropertiesHaveValues)
                  {
                     System.setProperty(jros2SettingsProp.DOMAIN_ID_KEY, Integer.toString(systemPropertyDomainId));
                     System.setProperty(jros2SettingsProp.INTERFACE_WHITELIST_KEY, systemPropertyInterfaceWhitelist[0]);
                  }
                  else // Otherwise ensure system does not have jros2 related properties
                  {
                     System.clearProperty(jros2SettingsProp.DOMAIN_ID_KEY);
                     System.clearProperty(jros2SettingsProp.INTERFACE_WHITELIST_KEY);
                  }

                  settingsList.add(new jros2SettingsProp());
               }

               ///////////////////////////////
               //// ENVIRONMENT VARIABLES ////
               ///////////////////////////////
               boolean environmentVariablesExist = (existencePermutation & environmentSettingsMask) != 0;
               boolean environmentVariablesHaveValues = (hasValuesPermutation & environmentSettingsMask) != 0;

               permutationDescription.append(permutationTableRow.formatted("Environment Variables", environmentVariablesExist, environmentVariablesExist ? environmentVariablesHaveValues : "N/A"));

               // Add environment variable settings if it should exist
               if ((existencePermutation & environmentSettingsMask) != 0)
               {
                  Map<String, String> environment = new HashMap<>();

                  // If it should have values, put values into the environment map
                  if ((hasValuesPermutation & environmentSettingsMask) != 0)
                  {
                     environment.put(jros2SettingsEnv.DOMAIN_ID_KEY, String.valueOf(environmentDomainId));
                     environment.put(jros2SettingsEnv.INTERFACE_WHITELIST_KEY, environmentInterfaceWhitelist[0]);
                  }

                  settingsList.add(new jros2SettingsEnv(environment));
               }

               ///////////////////////////////
               //// JROS2.PROPERTIES FILE ////
               ///////////////////////////////
               boolean jros2FileExist = (existencePermutation & fileSettingsMask) != 0;
               boolean jros2FileHaveValues = (hasValuesPermutation & fileSettingsMask) != 0;

               permutationDescription.append(permutationTableRow.formatted("jros2 file", jros2FileExist, jros2FileExist ? jros2FileHaveValues : "N/A"));

               // Add file settings if it should exist
               if ((existencePermutation & fileSettingsMask) != 0)
               {

                  if ((hasValuesPermutation & fileSettingsMask) != 0)
                  {
                     assumeTrue(fakeIHMCDirectory.mkdirs());
                     assumeTrue(fakeFile.createNewFile());
                     assumeTrue(fakeCompatibilityFile.createNewFile());

                     // Write the properties to the properties file
                     Properties properties = new Properties();
                     properties.setProperty(jros2SettingsFile.DOMAIN_ID_KEY, String.valueOf(fileDomainId));
                     properties.setProperty(jros2SettingsFile.INTERFACE_WHITELIST_KEY, fileInterfaceWhitelist[0]);
                     try (FileOutputStream output = new FileOutputStream(fakeFile))
                     {
                        properties.store(output, null);
                     }
                  }

                  settingsList.add(new jros2SettingsFile(fakeFilePath, fakeCompatibilityFilePath));
               }

               // Always add default settings
               permutationDescription.append(permutationTableRow.formatted("Default Settings", true, true));
               settingsList.add(new jros2SettingsDefault());

               permutationDescription.append("-----------------------------------------------\n");
               LogTools.info("Testing permutation:\n{}", permutationDescription);

               ////////////////////
               //// START TEST ////
               ////////////////////
               jros2 instance = new jros2(settingsList.toArray(jros2Settings[]::new));

               // The instance should always have values, since the default settings are always added
               assertTrue(instance.hasROSDomainId());
               assertTrue(instance.hasInterfaceWhitelist());

               // Ensure the jros2 instance settings values match the highest priority settings that has values.
               for (jros2Settings settings : settingsList)
               {
                  boolean hasValues = settings.hasROSDomainId() && settings.hasInterfaceWhitelist();
                  if (hasValues)
                  {
                     assertEquals(settings.rosDomainId(), instance.rosDomainId());
                     assertEquals(settings.interfaceWhitelist().length, instance.interfaceWhitelist().length);
                     for (int i = 0; i < settings.interfaceWhitelist().length; ++i)
                     {
                        assertEquals(settings.interfaceWhitelist()[i], instance.interfaceWhitelist()[i]);
                     }
                     break;
                  }
               }
            }
            finally
            {
               // Cleanup temporary files if any were created
               if (fakeIHMCDirectory.exists())
               {
                  try (Stream<Path> paths = Files.walk(fakeIHMCDirectoryPath))
                  {
                     paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                  }
               }
            }
         }
      }
   }

   @Test
   public void testDefaultSettings()
   {
      jros2Settings defaultSettings = new jros2SettingsDefault();
      assertTrue(defaultSettings.hasROSDomainId());
      assertTrue(defaultSettings.hasInterfaceWhitelist());
      assertEquals(0, defaultSettings.rosDomainId());
      assertEquals(0, defaultSettings.interfaceWhitelist().length);
   }

   @Test
   public void testFileSettingsNoFiles() throws IOException
   {
      // Non-existent paths
      Path fakeIHMCDirectoryPath = Path.of(System.getProperty("user.home"), ".fake_ihmc");
      Path fakeFilePath = fakeIHMCDirectoryPath.resolve("fake_jros2Settings.properties");
      Path fakeCompatibilityFilePath = fakeIHMCDirectoryPath.resolve("fake_IHMCNetworkParameters.ini");

      File fakeIHMCDirectory = fakeIHMCDirectoryPath.toFile();
      File fakeFile = fakeFilePath.toFile();
      File fakeCompatibilityFile = fakeCompatibilityFilePath.toFile();

      try
      {
         // Create the settings
         jros2SettingsFile fileSettings = new jros2SettingsFile(fakeFilePath, fakeCompatibilityFilePath);

         // Creating the settings should create the jros2Settings.properties file & any required subdirectories
         assertTrue(fakeIHMCDirectory.exists());
         assertTrue(fakeFile.exists());

         // Shouldn't create the compatibility file though
         assertFalse(fakeCompatibilityFile.exists());

         // Values should match default values
         jros2Settings defaultSettings = new jros2SettingsDefault();
         assertEquals(defaultSettings.rosDomainId(), fileSettings.rosDomainId());
         assertEquals(defaultSettings.interfaceWhitelist().length, fileSettings.interfaceWhitelist().length);
      }
      finally
      {
         // Cleanup after the test
         try (Stream<Path> paths = Files.walk(fakeIHMCDirectoryPath))
         {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
         }
      }
   }

   @Test
   public void testFileSettingsWithValues() throws IOException
   {
      Path fakeIHMCDirectoryPath = Path.of(System.getProperty("user.home"), ".fake_ihmc");
      Path fakeFilePath = fakeIHMCDirectoryPath.resolve("fake_jros2.properties");
      Path fakeCompatibilityFilePath = fakeIHMCDirectoryPath.resolve("fake_IHMCNetworkParameters.ini");

      File fakeIHMCDirectory = fakeIHMCDirectoryPath.toFile();
      File fakeFile = fakeFilePath.toFile();

      try
      {
         // Create the directory and file
         assumeTrue(fakeIHMCDirectory.mkdirs());
         assumeTrue(fakeFile.createNewFile());

         // Put property values
         int domainId = 113;
         String[] interfaceWhitelist = {"127.0.0.1", "192.0.2.1"};

         StringJoiner csvWhitelist = new StringJoiner(", ");
         for (String intrface : interfaceWhitelist)
            csvWhitelist.add(intrface);

         Properties properties = new Properties();
         properties.setProperty(jros2SettingsFile.DOMAIN_ID_KEY, String.valueOf(domainId));
         properties.setProperty(jros2SettingsFile.INTERFACE_WHITELIST_KEY, csvWhitelist.toString());
         try (FileOutputStream output = new FileOutputStream(fakeFile))
         {
            properties.store(output, null);
         }

         jros2Settings fileSettings = new jros2SettingsFile(fakeFilePath, fakeCompatibilityFilePath);

         assertTrue(fileSettings.hasROSDomainId());
         assertTrue(fileSettings.hasInterfaceWhitelist());

         // Ensure values are correct
         assertEquals(domainId, fileSettings.rosDomainId());
         for (int i = 0; i < interfaceWhitelist.length; ++i)
         {
            assertEquals(interfaceWhitelist[i], fileSettings.interfaceWhitelist()[i]);
         }
      }
      finally
      {
         // Cleanup after the test
         try (Stream<Path> paths = Files.walk(fakeIHMCDirectoryPath))
         {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
         }
      }
   }

   @Test
   public void testFileSettingsCompatibilityFile() throws IOException
   {
      Path fakeIHMCDirectoryPath = Path.of(System.getProperty("user.home"), ".fake_ihmc");
      Path fakeFilePath = fakeIHMCDirectoryPath.resolve("fake_jros2.properties");
      Path fakeCompatibilityFilePath = fakeIHMCDirectoryPath.resolve("fake_IHMCNetworkParameters.ini");

      File fakeIHMCDirectory = fakeIHMCDirectoryPath.toFile();
      File fakeCompatibilityFile = fakeCompatibilityFilePath.toFile();

      try
      {
         assumeTrue(fakeIHMCDirectory.mkdirs());
         assumeTrue(fakeCompatibilityFile.createNewFile());

         // Write the properties to the compatibility file
         int domainId = 113;
         String[] interfaceWhitelist = {"127.0.0.1", "192.0.2.1"};

         StringJoiner csvWhitelist = new StringJoiner(", ");
         for (String intrface : interfaceWhitelist)
            csvWhitelist.add(intrface);

         Properties compatibilityProperties = new Properties();
         compatibilityProperties.setProperty("RTPSDomainID", String.valueOf(domainId));
         compatibilityProperties.setProperty("RTPSSubnet", csvWhitelist.toString());

         try (FileOutputStream output = new FileOutputStream(fakeCompatibilityFile))
         {
            compatibilityProperties.store(output, null);
         }

         // Create jros2SettingsFile and ensure values are taken from compatibility file
         jros2Settings fileSettings = new jros2SettingsFile(fakeFilePath, fakeCompatibilityFilePath);

         // ROS domain id should be taken from the compatibility file
         assertTrue(fileSettings.hasROSDomainId());

         // Interface whitelist cannot be taken from the compatibility file
         assertFalse(fileSettings.hasInterfaceWhitelist());

         // Ensure the ROS domain id matches
         assertEquals(domainId, fileSettings.rosDomainId());
      }
      finally
      {
         // Cleanup after the test
         try (Stream<Path> paths = Files.walk(fakeIHMCDirectoryPath))
         {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
         }
      }
   }

   @Test
   public void testFileSettingsBothFiles() throws IOException
   {
      Path fakeIHMCDirectoryPath = Path.of(System.getProperty("user.home"), ".fake_ihmc");
      Path fakeFilePath = fakeIHMCDirectoryPath.resolve("fake_jros2.properties");
      Path fakeCompatibilityFilePath = fakeIHMCDirectoryPath.resolve("fake_IHMCNetworkParameters.ini");

      File fakeIHMCDirectory = fakeIHMCDirectoryPath.toFile();
      File fakeFile = fakeFilePath.toFile();
      File fakeCompatibilityFile = fakeCompatibilityFilePath.toFile();

      try
      {
         assumeTrue(fakeIHMCDirectory.mkdirs());
         assumeTrue(fakeFile.createNewFile());
         assumeTrue(fakeCompatibilityFile.createNewFile());

         // Write the properties to the properties file
         int domainId = 113;
         String[] interfaceWhitelist = {"127.0.0.1", "192.0.2.1"};

         StringJoiner csvWhitelist = new StringJoiner(", ");
         for (String intrface : interfaceWhitelist)
            csvWhitelist.add(intrface);

         Properties properties = new Properties();
         properties.setProperty(jros2SettingsFile.DOMAIN_ID_KEY, String.valueOf(domainId));
         properties.setProperty(jros2SettingsFile.INTERFACE_WHITELIST_KEY, csvWhitelist.toString());
         try (FileOutputStream output = new FileOutputStream(fakeFile))
         {
            properties.store(output, null);
         }

         // Write a compatibility properties file with different values
         Properties compatibilityProperties = new Properties();
         compatibilityProperties.setProperty("RTPSDomainID", String.valueOf(domainId - 1));
         compatibilityProperties.setProperty("RTPSSubnet", "0.0.0.0");
         try (FileOutputStream output = new FileOutputStream(fakeCompatibilityFile))
         {
            compatibilityProperties.store(output, null);
         }

         // Create jros2SettingsFile and ensure values are taken from the properties file, NOT compatibility file
         jros2Settings fileSettings = new jros2SettingsFile(fakeFilePath, fakeCompatibilityFilePath);

         assertTrue(fileSettings.hasROSDomainId());
         assertTrue(fileSettings.hasInterfaceWhitelist());

         assertEquals(domainId, fileSettings.rosDomainId());
         for (int i = 0; i < interfaceWhitelist.length; ++i)
         {
            assertEquals(interfaceWhitelist[i], fileSettings.interfaceWhitelist()[i]);
         }
      }
      finally
      {
         // Cleanup after the test
         try (Stream<Path> paths = Files.walk(fakeIHMCDirectoryPath))
         {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
         }
      }
   }

   @Test
   public void testEnvironmentSettings()
   {
      //// EMPTY ENVIRONMENT ////
      Map<String, String> environment = Map.of();

      jros2Settings defaultSettings = new jros2SettingsDefault();
      jros2Settings environmentSettings = new jros2SettingsEnv(environment);

      // No environment variables defined. Should say it doesn't have values
      assertFalse(environmentSettings.hasROSDomainId());
      assertFalse(environmentSettings.hasInterfaceWhitelist());

      // No environment variables defined. Should return default values
      assertEquals(defaultSettings.rosDomainId(), environmentSettings.rosDomainId());
      assertEquals(defaultSettings.interfaceWhitelist().length, environmentSettings.interfaceWhitelist().length);

      //// ENVIRONMENT WITH VALUES DEFINED ////
      int domainId = 113;
      String[] interfaceWhitelist = {"127.0.0.1", "192.0.2.1"};

      StringJoiner csvWhitelist = new StringJoiner(", ");
      for (String intrface : interfaceWhitelist)
         csvWhitelist.add(intrface);

      environment = Map.of(jros2SettingsEnv.DOMAIN_ID_KEY, String.valueOf(domainId), jros2SettingsEnv.INTERFACE_WHITELIST_KEY, csvWhitelist.toString());
      environmentSettings = new jros2SettingsEnv(environment);

      // Should report that it has values
      assertTrue(environmentSettings.hasROSDomainId());
      assertTrue(environmentSettings.hasInterfaceWhitelist());

      // Now the values should reflect the newly defined system properties
      assertEquals(domainId, environmentSettings.rosDomainId());
      for (int i = 0; i < interfaceWhitelist.length; ++i)
      {
         assertEquals(interfaceWhitelist[i], environmentSettings.interfaceWhitelist()[i]);
      }
   }

   @Test
   public void testSystemPropertySettings()
   {
      // Ensure no properties have been set
      System.clearProperty(jros2SettingsProp.DOMAIN_ID_KEY);
      System.clearProperty(jros2SettingsProp.INTERFACE_WHITELIST_KEY);

      jros2Settings defaultSettings = new jros2SettingsDefault();
      jros2Settings systemPropertySettings = new jros2SettingsProp();

      // Should say that it doesn't have values
      assertFalse(systemPropertySettings.hasROSDomainId());
      assertFalse(systemPropertySettings.hasInterfaceWhitelist());

      // No system properties defined. Should return default values
      assertEquals(defaultSettings.rosDomainId(), systemPropertySettings.rosDomainId());
      assertEquals(defaultSettings.interfaceWhitelist().length, systemPropertySettings.interfaceWhitelist().length);

      int domainId = 113;
      String[] interfaceWhitelist = {"127.0.0.1", "192.0.2.1"};

      StringJoiner csvWhitelist = new StringJoiner(", ");
      for (String intrface : interfaceWhitelist)
         csvWhitelist.add(intrface);

      // Define system properties
      System.setProperty(jros2SettingsProp.DOMAIN_ID_KEY, Integer.toString(domainId));
      System.setProperty(jros2SettingsProp.INTERFACE_WHITELIST_KEY, csvWhitelist.toString());

      // Now it should have values
      assertTrue(systemPropertySettings.hasROSDomainId());
      assertTrue(systemPropertySettings.hasInterfaceWhitelist());

      // The values should reflect the newly defined system properties
      assertEquals(domainId, systemPropertySettings.rosDomainId());
      for (int i = 0; i < interfaceWhitelist.length; ++i)
      {
         assertEquals(interfaceWhitelist[i], systemPropertySettings.interfaceWhitelist()[i]);
      }

      // Change up the system properties
      int newDomainId = 219;
      String[] newInterfaceWhitelist = {"198.51.100.5"};

      StringJoiner newCSVWhitelist = new StringJoiner(", ");
      for (String intrface : newInterfaceWhitelist)
         newCSVWhitelist.add(intrface);

      // Define system properties
      System.setProperty(jros2SettingsProp.DOMAIN_ID_KEY, Integer.toString(newDomainId));
      System.setProperty(jros2SettingsProp.INTERFACE_WHITELIST_KEY, newCSVWhitelist.toString());

      // Should still have values
      assertTrue(systemPropertySettings.hasROSDomainId());
      assertTrue(systemPropertySettings.hasInterfaceWhitelist());

      // Now the values should reflect the newly defined system properties
      assertEquals(newDomainId, systemPropertySettings.rosDomainId());
      for (int i = 0; i < newInterfaceWhitelist.length; ++i)
      {
         assertEquals(newInterfaceWhitelist[i], systemPropertySettings.interfaceWhitelist()[i]);
      }
   }
}
