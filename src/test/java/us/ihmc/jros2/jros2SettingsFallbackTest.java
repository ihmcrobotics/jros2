/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Settings that are present but not usable fall back to the jros2 defaults instead of failing node creation: a domain ID
 * that is not a number (system property, environment, legacy IHMCNetworkParameters.ini), and unset values of a source.
 * The source still reports the key as present, so it keeps its priority.
 */
public class jros2SettingsFallbackTest
{
   private static final jros2SettingsDefault DEFAULTS = new jros2SettingsDefault();

   /** The system properties this test sets; values given to the test JVM are restored afterwards. */
   private static final String[] PROPERTIES = {jros2SettingsProp.DOMAIN_ID_KEY, jros2SettingsProp.INTERFACE_WHITELIST_KEY};
   private final Map<String, String> savedProperties = new HashMap<>();

   @BeforeEach
   public void clearProperties()
   {
      for (String key : PROPERTIES)
      {
         String value = System.getProperty(key);
         if (value != null)
            savedProperties.put(key, value);
         System.clearProperty(key);
      }
   }

   @AfterEach
   public void restoreProperties()
   {
      for (String key : PROPERTIES)
      {
         String value = savedProperties.get(key);
         if (value == null)
            System.clearProperty(key);
         else
            System.setProperty(key, value);
      }
   }

   @Test
   public void testSystemProperties()
   {
      jros2SettingsProp properties = new jros2SettingsProp();
      assertEquals("System Properties", properties.getSourceName());
      assertFalse(properties.hasROSDomainId());
      assertFalse(properties.hasInterfaceWhitelist());
      assertEquals(DEFAULTS.rosDomainId(), properties.rosDomainId(), "unset");
      assertArrayEquals(DEFAULTS.interfaceWhitelist(), properties.interfaceWhitelist(), "unset");

      System.setProperty(jros2SettingsProp.DOMAIN_ID_KEY, "forty-two");
      assertTrue(properties.hasROSDomainId());
      assertEquals(DEFAULTS.rosDomainId(), properties.rosDomainId(), "not a number");

      System.setProperty(jros2SettingsProp.INTERFACE_WHITELIST_KEY, "10.0.0.1, eth0");
      assertTrue(properties.hasInterfaceWhitelist());
      assertArrayEquals(new String[] {"10.0.0.1", "eth0"}, properties.interfaceWhitelist());
   }

   @Test
   public void testEnvironment()
   {
      jros2SettingsEnv environment = new jros2SettingsEnv(Map.of(jros2SettingsEnv.DOMAIN_ID_KEY, "not a number"));
      assertEquals("Environment Variables", environment.getSourceName());
      assertTrue(environment.hasROSDomainId());
      assertEquals(DEFAULTS.rosDomainId(), environment.rosDomainId());
      assertFalse(environment.hasInterfaceWhitelist());
      assertArrayEquals(DEFAULTS.interfaceWhitelist(), environment.interfaceWhitelist());
      assertFalse(environment.hasIntraprocessDelivery());
      assertEquals(DEFAULTS.intraprocessDelivery(), environment.intraprocessDelivery());

      jros2SettingsEnv valid = new jros2SettingsEnv(Map.of(jros2SettingsEnv.DOMAIN_ID_KEY, "17", jros2SettingsEnv.INTERFACE_WHITELIST_KEY, "lo"));
      assertEquals(17, valid.rosDomainId());
      assertArrayEquals(new String[] {"lo"}, valid.interfaceWhitelist());
   }

   @Test
   public void testCompatibilityFileWithAnInvalidDomainId(@TempDir Path directory) throws IOException
   {
      Path compatibility = directory.resolve("IHMCNetworkParameters.ini");
      Files.writeString(compatibility, "RTPSDomainID=abc\n");
      Path file = directory.resolve(".ihmc").resolve("jros2.properties");

      jros2SettingsFile settings = new jros2SettingsFile(file, compatibility);
      assertTrue(Files.isRegularFile(file), "the settings file is created");
      assertEquals(file.toFile().getAbsolutePath(), settings.getSourceName());
      assertTrue(settings.hasROSDomainId());
      assertEquals(DEFAULTS.rosDomainId(), settings.rosDomainId(), "an invalid RTPSDomainID is not taken over");
      assertTrue(Files.readString(file).contains(jros2SettingsFile.DOMAIN_ID_KEY + "=" + DEFAULTS.rosDomainId()), Files.readString(file));
   }
}
