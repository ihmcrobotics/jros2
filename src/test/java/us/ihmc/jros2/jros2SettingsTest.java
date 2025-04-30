package us.ihmc.jros2;

import org.junit.jupiter.api.Test;
import us.ihmc.jros2.jros2Settings.jros2SettingsDefault;
import us.ihmc.jros2.jros2Settings.jros2SettingsEnv;
import us.ihmc.jros2.jros2Settings.jros2SettingsProp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class jros2SettingsTest
{
   static
   {
      jros2.load();
   }

   @Test
   public void testDefaultSettings()
   {
      jros2Settings defaultSettings = new jros2SettingsDefault();
      assertEquals(0, defaultSettings.defaultDomainId());
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
         assertEquals(defaultSettings.defaultDomainId(), fileSettings.defaultDomainId());
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
   public void testFileSettingsNoValues() throws IOException
   {
      Path fakeIHMCDirectoryPath = Path.of(System.getProperty("user.home"), ".fake_ihmc");
      Path fakeFilePath = fakeIHMCDirectoryPath.resolve("fake_jros2Settings.properties");
      Path fakeCompatibilityFilePath = fakeIHMCDirectoryPath.resolve("fake_IHMCNetworkParameters.ini");

      File fakeIHMCDirectory = fakeIHMCDirectoryPath.toFile();
      File fakeFile = fakeFilePath.toFile();

      try
      {
         // Create the directory and file
         assumeTrue(fakeIHMCDirectory.mkdirs());
         assumeTrue(fakeFile.createNewFile());

         // Put empty property values
         Properties properties = new Properties();
         properties.setProperty("jros2DefaultDomainID", "");
         properties.setProperty("jros2AddressWhitelist", "");
         try (FileOutputStream output = new FileOutputStream(fakeFile))
         {
            properties.store(output, null);
         }

         jros2Settings fileSettings = new jros2SettingsFile(fakeFilePath, fakeCompatibilityFilePath);

         // fileSettings should match defaultSettings
         jros2Settings defaultSettings = new jros2SettingsDefault();
         assertEquals(defaultSettings.defaultDomainId(), fileSettings.defaultDomainId());
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
      Path fakeFilePath = fakeIHMCDirectoryPath.resolve("fake_jros2Settings.properties");
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
         String[] interfaceWhitelist = {"127.0.0.1/8", "192.0.2.1/24"};

         StringJoiner csvWhitelist = new StringJoiner(", ");
         for (String intrface : interfaceWhitelist)
            csvWhitelist.add(intrface);

         Properties properties = new Properties();
         properties.setProperty("jros2DefaultDomainID", String.valueOf(domainId));
         properties.setProperty("jros2AddressWhitelist", csvWhitelist.toString());
         try (FileOutputStream output = new FileOutputStream(fakeFile))
         {
            properties.store(output, null);
         }

         jros2Settings fileSettings = new jros2SettingsFile(fakeFilePath, fakeCompatibilityFilePath);

         // Ensure values are correct
         assertEquals(domainId, fileSettings.defaultDomainId());
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
      Path fakeFilePath = fakeIHMCDirectoryPath.resolve("fake_jros2Settings.properties");
      Path fakeCompatibilityFilePath = fakeIHMCDirectoryPath.resolve("fake_IHMCNetworkParameters.ini");

      File fakeIHMCDirectory = fakeIHMCDirectoryPath.toFile();
      File fakeCompatibilityFile = fakeCompatibilityFilePath.toFile();

      try
      {
         assumeTrue(fakeIHMCDirectory.mkdirs());
         assumeTrue(fakeCompatibilityFile.createNewFile());

         // Write the properties to the compatibility file
         int domainId = 113;
         String[] interfaceWhitelist = {"127.0.0.1/8", "192.0.2.1/24"};

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

         assertEquals(domainId, fileSettings.defaultDomainId());
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
   public void testFileSettingsBothFiles() throws IOException
   {
      Path fakeIHMCDirectoryPath = Path.of(System.getProperty("user.home"), ".fake_ihmc");
      Path fakeFilePath = fakeIHMCDirectoryPath.resolve("fake_jros2Settings.properties");
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
         String[] interfaceWhitelist = {"127.0.0.1/8", "192.0.2.1/24"};

         StringJoiner csvWhitelist = new StringJoiner(", ");
         for (String intrface : interfaceWhitelist)
            csvWhitelist.add(intrface);

         Properties properties = new Properties();
         properties.setProperty("jros2DefaultDomainID", String.valueOf(domainId));
         properties.setProperty("jros2AddressWhitelist", csvWhitelist.toString());
         try (FileOutputStream output = new FileOutputStream(fakeFile))
         {
            properties.store(output, null);
         }

         // Write a compatibility properties file with different values
         Properties compatibilityProperties = new Properties();
         compatibilityProperties.setProperty("RTPSDomainID", String.valueOf(domainId - 1));
         compatibilityProperties.setProperty("RTPSSubnet", "0.0.0.0/32");
         try (FileOutputStream output = new FileOutputStream(fakeCompatibilityFile))
         {
            compatibilityProperties.store(output, null);
         }

         // Create jros2SettingsFile and ensure values are taken from the properties file, NOT compatibility file
         jros2Settings fileSettings = new jros2SettingsFile(fakeFilePath, fakeCompatibilityFilePath);

         assertEquals(domainId, fileSettings.defaultDomainId());
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

      // No environment variables defined. Should return default values
      assertEquals(defaultSettings.defaultDomainId(), environmentSettings.defaultDomainId());
      assertEquals(defaultSettings.interfaceWhitelist().length, environmentSettings.interfaceWhitelist().length);

      //// ENVIRONMENT WITH VALUES DEFINED ////
      int domainId = 113;
      String[] interfaceWhitelist = {"127.0.0.1/8", "192.0.2.1/24"};

      StringJoiner csvWhitelist = new StringJoiner(", ");
      for (String intrface : interfaceWhitelist)
         csvWhitelist.add(intrface);

      environment = Map.of("ROS_DOMAIN_ID", String.valueOf(domainId),
                           "ROS_ADDRESS_RESTRICTION", csvWhitelist.toString());
      environmentSettings = new jros2SettingsEnv(environment);

      // Now the values should reflect the newly defined system properties
      assertEquals(domainId, environmentSettings.defaultDomainId());
      for (int i = 0; i < interfaceWhitelist.length; ++i)
      {
         assertEquals(interfaceWhitelist[i], environmentSettings.interfaceWhitelist()[i]);
      }
   }

   @Test
   public void testSystemPropertySettings()
   {
      // Ensure no properties have been set
      System.clearProperty("ros.domain.id");
      System.clearProperty("ros.address.restriction");

      jros2Settings defaultSettings = new jros2SettingsDefault();
      jros2Settings systemPropertySettings = new jros2SettingsProp();

      // No system properties defined. Should return default values
      assertEquals(defaultSettings.defaultDomainId(), systemPropertySettings.defaultDomainId());
      assertEquals(defaultSettings.interfaceWhitelist().length, systemPropertySettings.interfaceWhitelist().length);

      int domainId = 113;
      String[] interfaceWhitelist = {"127.0.0.1/8", "192.0.2.1/24"};

      StringJoiner csvWhitelist = new StringJoiner(", ");
      for (String intrface : interfaceWhitelist)
         csvWhitelist.add(intrface);

      // Define system properties
      System.setProperty("ros.domain.id", Integer.toString(domainId));
      System.setProperty("ros.address.restriction", csvWhitelist.toString());

      // Now the values should reflect the newly defined system properties
      assertEquals(domainId, systemPropertySettings.defaultDomainId());
      for (int i = 0; i < interfaceWhitelist.length; ++i)
      {
         assertEquals(interfaceWhitelist[i], systemPropertySettings.interfaceWhitelist()[i]);
      }

      // Change up the system properties
      int newDomainId = 219;
      String[] newInterfaceWhitelist = {"198.51.100.5/32"};

      StringJoiner newCSVWhitelist = new StringJoiner(", ");
      for (String intrface : newInterfaceWhitelist)
         newCSVWhitelist.add(intrface);

      // Define system properties
      System.setProperty("ros.domain.id", Integer.toString(newDomainId));
      System.setProperty("ros.address.restriction",newCSVWhitelist.toString());

      // Now the values should reflect the newly defined system properties
      assertEquals(newDomainId, systemPropertySettings.defaultDomainId());
      for (int i = 0; i < newInterfaceWhitelist.length; ++i)
      {
         assertEquals(newInterfaceWhitelist[i], systemPropertySettings.interfaceWhitelist()[i]);
      }
   }
}
