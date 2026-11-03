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

import org.junit.jupiter.api.Test;
import us.ihmc.jros2.ROS2DiscoveryConfiguration.LocalhostOnly;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * rcl (Jazzy) parsing and precedence of ROS_LOCALHOST_ONLY, ROS_AUTOMATIC_DISCOVERY_RANGE and ROS_STATIC_PEERS
 * ({@code localhost.c}, {@code discovery_options.c}, {@code init.c}).
 */
public class ROS2DiscoveryConfigurationTest
{
   static Function<jros2Settings.Key, String> settings(Object... keyValues)
   {
      Map<jros2Settings.Key, String> map = new EnumMap<>(jros2Settings.Key.class);
      for (int i = 0; i < keyValues.length; i += 2)
      {
         map.put((jros2Settings.Key) keyValues[i], (String) keyValues[i + 1]);
      }
      return map::get;
   }

   @Test
   public void testLocalhostOnlyParsing()
   {
      assertEquals(LocalhostOnly.DEFAULT, ROS2DiscoveryConfiguration.parseLocalhostOnly(null));
      assertEquals(LocalhostOnly.DEFAULT, ROS2DiscoveryConfiguration.parseLocalhostOnly(""));
      assertEquals(LocalhostOnly.ENABLED, ROS2DiscoveryConfiguration.parseLocalhostOnly("1"));
      // Jazzy uses strncmp(value, "1", 1): a prefix test
      assertEquals(LocalhostOnly.ENABLED, ROS2DiscoveryConfiguration.parseLocalhostOnly("1abc"));
      assertEquals(LocalhostOnly.ENABLED, ROS2DiscoveryConfiguration.parseLocalhostOnly("10"));
      assertEquals(LocalhostOnly.DISABLED, ROS2DiscoveryConfiguration.parseLocalhostOnly("0"));
      assertEquals(LocalhostOnly.DISABLED, ROS2DiscoveryConfiguration.parseLocalhostOnly("true"));
      assertEquals(LocalhostOnly.DISABLED, ROS2DiscoveryConfiguration.parseLocalhostOnly(" 1"));
   }

   @Test
   public void testDiscoveryRangeParsing()
   {
      List<String> warnings = new ArrayList<>();
      assertEquals(ROS2DiscoveryRange.SUBNET, ROS2DiscoveryConfiguration.parseDiscoveryRange(null, warnings));
      assertEquals(ROS2DiscoveryRange.SUBNET, ROS2DiscoveryConfiguration.parseDiscoveryRange("", warnings));
      assertEquals(ROS2DiscoveryRange.OFF, ROS2DiscoveryConfiguration.parseDiscoveryRange("OFF", warnings));
      assertEquals(ROS2DiscoveryRange.LOCALHOST, ROS2DiscoveryConfiguration.parseDiscoveryRange("LOCALHOST", warnings));
      assertEquals(ROS2DiscoveryRange.SUBNET, ROS2DiscoveryConfiguration.parseDiscoveryRange("SUBNET", warnings));
      assertEquals(ROS2DiscoveryRange.SYSTEM_DEFAULT, ROS2DiscoveryConfiguration.parseDiscoveryRange("SYSTEM_DEFAULT", warnings));
      assertTrue(warnings.isEmpty());

      // Exact, case-sensitive match; anything else is LOCALHOST with a warning
      for (String invalid : new String[] {"localhost", "subnet", "Off", " SUBNET", "SUBNET ", "1", "ANY"})
      {
         warnings.clear();
         assertEquals(ROS2DiscoveryRange.LOCALHOST, ROS2DiscoveryConfiguration.parseDiscoveryRange(invalid, warnings), invalid);
         assertEquals(1, warnings.size());
         assertTrue(warnings.get(0).contains("'" + invalid + "'"), warnings.get(0));
         assertTrue(warnings.get(0).contains("assuming localhost only"), warnings.get(0));
      }
   }

   @Test
   public void testStaticPeersParsing()
   {
      List<String> warnings = new ArrayList<>();
      assertEquals(List.of(), ROS2DiscoveryConfiguration.parseStaticPeers(null, warnings));
      assertEquals(List.of(), ROS2DiscoveryConfiguration.parseStaticPeers("", warnings));
      assertEquals(List.of(), ROS2DiscoveryConfiguration.parseStaticPeers(";;;", warnings));
      assertEquals(List.of("10.0.0.1"), ROS2DiscoveryConfiguration.parseStaticPeers("10.0.0.1", warnings));
      // The research's verified rcutils_split case: empty tokens dropped, whitespace kept
      assertEquals(List.of("192.168.77.77", "localhost", " ", "127.0.0.1"),
                   ROS2DiscoveryConfiguration.parseStaticPeers("192.168.77.77;;localhost; ;127.0.0.1;", warnings));
      assertEquals(List.of("a", "b"), ROS2DiscoveryConfiguration.parseStaticPeers(";a;b", warnings));
      assertEquals(List.of(" host "), ROS2DiscoveryConfiguration.parseStaticPeers(" host ", warnings));
      assertTrue(warnings.isEmpty());

      // Entries longer than 255 characters are skipped with a warning; exactly 255 is accepted
      String max = "a".repeat(255);
      String tooLong = "b".repeat(256);
      assertEquals(List.of(max, "c"), ROS2DiscoveryConfiguration.parseStaticPeers(max + ";" + tooLong + ";c", warnings));
      assertEquals(1, warnings.size());
      assertTrue(warnings.get(0).contains("too long"), warnings.get(0));
   }

   @Test
   public void testDefaultsWithNothingSet()
   {
      ROS2DiscoveryConfiguration configuration = ROS2DiscoveryConfiguration.resolve(null, null, settings());
      assertEquals(ROS2DiscoveryRange.SUBNET, configuration.getRange());
      assertEquals("default", configuration.getRangeSource());
      assertTrue(configuration.getStaticPeers().isEmpty());
      assertNull(configuration.getStaticPeersSource());
      assertTrue(configuration.getWarnings().isEmpty());
   }

   @Test
   public void testEnvironmentRangeAndPeers()
   {
      ROS2DiscoveryConfiguration configuration = ROS2DiscoveryConfiguration.resolve(null,
                                                                                    null,
                                                                                    settings(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE,
                                                                                             "LOCALHOST",
                                                                                             jros2Settings.Key.STATIC_PEERS,
                                                                                             "10.1.2.3;robot.local"));
      assertEquals(ROS2DiscoveryRange.LOCALHOST, configuration.getRange());
      assertEquals("ROS_AUTOMATIC_DISCOVERY_RANGE", configuration.getRangeSource());
      assertEquals(List.of("10.1.2.3", "robot.local"), configuration.getStaticPeers());
      assertEquals("ROS_STATIC_PEERS", configuration.getStaticPeersSource());
   }

   @Test
   public void testLocalhostOnlyWinsOverEverything()
   {
      ROS2DiscoveryConfiguration configuration = ROS2DiscoveryConfiguration.resolve(ROS2DiscoveryRange.OFF,
                                                                                    new String[] {"10.0.0.9"},
                                                                                    settings(jros2Settings.Key.LOCALHOST_ONLY,
                                                                                             "1",
                                                                                             jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE,
                                                                                             "SUBNET",
                                                                                             jros2Settings.Key.STATIC_PEERS,
                                                                                             "10.0.0.1"));
      assertEquals(ROS2DiscoveryRange.LOCALHOST, configuration.getRange());
      assertEquals("ROS_LOCALHOST_ONLY", configuration.getRangeSource());
      assertTrue(configuration.getStaticPeers().isEmpty());
      assertTrue(configuration.getWarnings().stream().anyMatch(w -> w.contains("deprecated")));
      assertTrue(configuration.getWarnings().stream().anyMatch(w -> w.contains("will be ignored")));

      // Disabled localhost-only does not affect the range
      configuration = ROS2DiscoveryConfiguration.resolve(null, null, settings(jros2Settings.Key.LOCALHOST_ONLY, "0", jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE, "OFF"));
      assertEquals(ROS2DiscoveryRange.OFF, configuration.getRange());
   }

   @Test
   public void testOptionsOverrideEnvironment()
   {
      ROS2DiscoveryConfiguration configuration = ROS2DiscoveryConfiguration.resolve(ROS2DiscoveryRange.SUBNET,
                                                                                    new String[] {"192.168.1.20"},
                                                                                    settings(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE,
                                                                                             "OFF",
                                                                                             jros2Settings.Key.STATIC_PEERS,
                                                                                             "10.0.0.1"));
      assertEquals(ROS2DiscoveryRange.SUBNET, configuration.getRange());
      assertEquals("ROS2NodeOptions", configuration.getRangeSource());
      assertEquals(List.of("192.168.1.20"), configuration.getStaticPeers());
      assertEquals("ROS2NodeOptions", configuration.getStaticPeersSource());

      // An empty programmatic list means "not given": the environment applies
      configuration = ROS2DiscoveryConfiguration.resolve(ROS2DiscoveryRange.LOCALHOST, new String[0], settings(jros2Settings.Key.STATIC_PEERS, "10.0.0.1"));
      assertEquals(List.of("10.0.0.1"), configuration.getStaticPeers());
      assertEquals("ROS_STATIC_PEERS", configuration.getStaticPeersSource());

      // Invalid programmatic peers are rejected
      assertThrows(IllegalArgumentException.class, () -> ROS2DiscoveryConfiguration.resolve(null, new String[] {""}, settings()));
      assertThrows(IllegalArgumentException.class, () -> ROS2DiscoveryConfiguration.resolve(null, new String[] {null}, settings()));
      assertThrows(IllegalArgumentException.class, () -> ROS2DiscoveryConfiguration.resolve(null, new String[] {"x".repeat(256)}, settings()));
   }

   @Test
   public void testStaticPeersIgnoredForOffAndSystemDefault()
   {
      // OFF: rcl does not even read ROS_STATIC_PEERS
      ROS2DiscoveryConfiguration off = ROS2DiscoveryConfiguration.resolve(null,
                                                                          null,
                                                                          settings(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE,
                                                                                   "OFF",
                                                                                   jros2Settings.Key.STATIC_PEERS,
                                                                                   "10.0.0.1"));
      assertEquals(ROS2DiscoveryRange.OFF, off.getRange());
      assertTrue(off.getStaticPeers().isEmpty());
      assertTrue(off.getWarnings().isEmpty());

      // OFF with programmatic peers: warned and ignored
      off = ROS2DiscoveryConfiguration.resolve(ROS2DiscoveryRange.OFF, new String[] {"10.0.0.1"}, settings());
      assertTrue(off.getStaticPeers().isEmpty());
      assertEquals(1, off.getWarnings().size());
      assertTrue(off.getWarnings().get(0).contains("ROS_STATIC_PEERS will be ignored"));

      // SYSTEM_DEFAULT: rmw_fastrtps only applies peers for LOCALHOST and SUBNET
      ROS2DiscoveryConfiguration systemDefault = ROS2DiscoveryConfiguration.resolve(null,
                                                                                    null,
                                                                                    settings(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE,
                                                                                             "SYSTEM_DEFAULT",
                                                                                             jros2Settings.Key.STATIC_PEERS,
                                                                                             "10.0.0.1"));
      assertEquals(ROS2DiscoveryRange.SYSTEM_DEFAULT, systemDefault.getRange());
      assertTrue(systemDefault.getStaticPeers().isEmpty());
      assertEquals(1, systemDefault.getWarnings().size());
   }

   @Test
   public void testInvalidRangeFromEnvironmentWarnsAndUsesLocalhost()
   {
      ROS2DiscoveryConfiguration configuration = ROS2DiscoveryConfiguration.resolve(null, null, settings(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE, "localhost"));
      assertEquals(ROS2DiscoveryRange.LOCALHOST, configuration.getRange());
      assertEquals(1, configuration.getWarnings().size());
   }

   @Test
   public void testStaticPeerResolution()
   {
      ROS2DiscoveryConfiguration configuration = ROS2DiscoveryConfiguration.resolve(null,
                                                                                    new String[] {"10.20.30.40", "dual.example", "localhost"},
                                                                                    settings());
      ROS2DiscoveryConfiguration.PeerResolver resolver = host ->
      {
         switch (host)
         {
            case "dual.example":
               // IPv6 first: the first IPv4 address is used
               return new InetAddress[] {InetAddress.getByName("::1"), InetAddress.getByName("192.0.2.7"), InetAddress.getByName("192.0.2.8")};
            default:
               return InetAddress.getAllByName(host);
         }
      };
      List<String> resolved = configuration.resolveStaticPeers(resolver);
      assertEquals("10.20.30.40", resolved.get(0));
      assertEquals("192.0.2.7", resolved.get(1));
      assertTrue(resolved.get(2).startsWith("127."), resolved.get(2));
   }

   @Test
   public void testUnresolvableStaticPeersFail()
   {
      ROS2DiscoveryConfiguration.PeerResolver failing = host ->
      {
         throw new UnknownHostException(host);
      };
      ROS2DiscoveryConfiguration configuration = ROS2DiscoveryConfiguration.resolve(null, null, settings(jros2Settings.Key.STATIC_PEERS, "no-such-host.invalid"));
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> configuration.resolveStaticPeers(failing));
      assertTrue(exception.getMessage().contains("no-such-host.invalid"), exception.getMessage());
      assertTrue(exception.getMessage().contains("ROS_STATIC_PEERS"), exception.getMessage());

      // A name with only IPv6 addresses is unusable (rmw takes the first IPv4 result)
      ROS2DiscoveryConfiguration ipv6Only = ROS2DiscoveryConfiguration.resolve(null, new String[] {"v6.example"}, settings());
      assertThrows(IllegalArgumentException.class, () -> ipv6Only.resolveStaticPeers(host -> new InetAddress[] {InetAddress.getByName("::1")}));

      // A whitespace token from ROS_STATIC_PEERS fails resolution like in rmw (real DNS)
      ROS2DiscoveryConfiguration whitespace = ROS2DiscoveryConfiguration.resolve(null, null, settings(jros2Settings.Key.STATIC_PEERS, " "));
      assertEquals(List.of(" "), whitespace.getStaticPeers());
      assertThrows(IllegalArgumentException.class, () -> whitespace.resolveStaticPeers(InetAddress::getAllByName));
   }
}
