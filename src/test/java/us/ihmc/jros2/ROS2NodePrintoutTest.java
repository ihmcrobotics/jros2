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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType.InterfaceWhiteList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ROS2NodePrintout}: the configuration summary logged when a participant is created must describe the transports
 * actually used (including interface whitelists), the static peers as given and as resolved, and the SROS2 state. XML-level
 * only (no DDS participant is created).
 */
public class ROS2NodePrintoutTest
{
   private static final String PRINTOUT_PROPERTY = "jros2.node.printout";

   @AfterEach
   public void clearPrintoutProperty()
   {
      System.clearProperty(PRINTOUT_PROPERTY);
   }

   private static String format(ParticipantProfiles.ParticipantConfiguration configuration)
   {
      return ROS2NodePrintout.format(ROS2Node.class, configuration).toString();
   }

   private static List<String> lines(String printout)
   {
      List<String> lines = new ArrayList<>();
      for (String line : printout.split("\n"))
      {
         lines.add(line.trim());
      }
      return lines;
   }

   @Test
   public void testInterfaceWhitelistAndSharedMemoryTransports()
   {
      String uniqueIp = "10.78." + (System.nanoTime() % 200) + ".1";
      ParticipantProfiles.Environment whitelisted = ParticipantProfilesTest.environment(ROS2DiscoveryConfigurationTest.settings(), new String[] {uniqueIp}, false);
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_print_whitelist", "node", 3, null, whitelisted);

      List<String> lines = lines(format(configuration));
      assertTrue(lines.contains("Using transports:"), lines.toString());
      assertTrue(lines.contains("UDPv4: on " + uniqueIp + ", 127.0.0.1"), lines.toString());
      assertTrue(lines.contains("SHM: local only, segment 4.0 MiB (Specified by: default)"), lines.toString());
      assertTrue(lines.stream().noneMatch(line -> line.startsWith("Using builtin transports")), lines.toString());
   }

   @Test
   public void testCustomTransportsWithAndWithoutWhitelist()
   {
      TransportDescriptorType allInterfaces = new TransportDescriptorType();
      allInterfaces.setTransportId("cov_print_udp_" + System.nanoTime());
      allInterfaces.setType("UDPv4");

      // A whitelist entry may also be a list of addresses
      TransportDescriptorType listed = new TransportDescriptorType();
      listed.setTransportId("cov_print_udp_listed_" + System.nanoTime());
      listed.setType("UDPv6");
      InterfaceWhiteList whiteList = new InterfaceWhiteList();
      whiteList.getAddressOrInterface().add("::1");
      whiteList.getAddressOrInterface().add(List.of("fe80::1", "fe80::2"));
      listed.setInterfaceWhiteList(whiteList);

      // An empty whitelist is the same as none
      TransportDescriptorType emptyWhitelist = new TransportDescriptorType();
      emptyWhitelist.setTransportId("cov_print_tcp_" + System.nanoTime());
      emptyWhitelist.setType("TCPv4");
      emptyWhitelist.setInterfaceWhiteList(new InterfaceWhiteList());

      ROS2NodeOptions options = ROS2NodeOptions.defaults().transports(allInterfaces, listed, emptyWhitelist);
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_print_custom", "node", 3, options, ParticipantProfilesTest.desktop());

      List<String> lines = lines(format(configuration));
      int header = lines.indexOf("Using custom transports:");
      assertTrue(header >= 0, lines.toString());
      assertEquals("UDPv4: on all interfaces", lines.get(header + 1));
      assertEquals("UDPv6: on ::1, fe80::1, fe80::2", lines.get(header + 2));
      assertEquals("TCPv4: on all interfaces", lines.get(header + 3));
   }

   @Test
   public void testBuiltinTransportsAndDomainIdSource()
   {
      // No settings source defines this domain (one that differs from ROS_DOMAIN_ID), so the printout attributes it to the
      // constructor
      jros2.load();
      int domainId = jros2.get().rosDomainId() == 231 ? 230 : 231;
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_print_builtin",
                                                                                             "builtin_node",
                                                                                             domainId,
                                                                                             null,
                                                                                             ParticipantProfilesTest.desktop(jros2Settings.Key.SHM_SEGMENT_SIZE, "0"));
      List<String> lines = lines(format(configuration));
      assertTrue(lines.contains("Created ROS2Node: builtin_node"), lines.toString());
      assertTrue(lines.contains("DomainID: " + domainId + " (Specified by: constructor)"), lines.toString());
      assertTrue(lines.contains("Using builtin transports: UDPv4, SHM (segment Fast-DDS default (512 KiB), Specified by: JROS2_SHM_SEGMENT_SIZE)"), lines.toString());
      assertTrue(lines.contains("Discovery range: SUBNET (Specified by: default)"), lines.toString());
      assertTrue(lines.stream().noneMatch(line -> line.startsWith("Static peers")), "no static peers: " + lines);
      assertTrue(lines.stream().noneMatch(line -> line.startsWith("Thread settings")), "no thread settings: " + lines);
      assertTrue(lines.contains("SROS2 security: disabled"), lines.toString());
   }

   @Test
   public void testDefaultTransports()
   {
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_print_default", "node", 3, null, ParticipantProfilesTest.desktop());
      List<String> lines = lines(format(configuration));
      int header = lines.indexOf("Using transports:");
      assertTrue(header >= 0, lines.toString());
      assertEquals("SHM: local only, segment 4.0 MiB (Specified by: default)", lines.get(header + 1));
      assertEquals("UDPv4: on all interfaces", lines.get(header + 2));
   }

   @Test
   public void testStaticPeersAsGivenAndAsResolved()
   {
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_print_peers",
                                                                                             "node",
                                                                                             3,
                                                                                             ROS2NodeOptions.defaults().staticPeers("10.1.2.3", "robot.local"),
                                                                                             ParticipantProfilesTest.desktop());
      List<String> lines = lines(format(configuration));
      // An IP literal resolves to itself and is printed once; a host name is followed by the address it resolved to
      assertTrue(lines.contains("Static peers: 10.1.2.3, robot.local (192.0.2.10) (Specified by: ROS2NodeOptions)"), lines.toString());
   }

   @Test
   public void testSecurityStates(@TempDir Path keystore) throws IOException
   {
      Path enclave = Files.createDirectories(keystore.resolve("enclaves").resolve("printout"));
      for (String file : new String[] {"identity_ca.cert.pem", "cert.pem", "key.pem", "permissions_ca.cert.pem", "governance.p7s", "permissions.p7s"})
      {
         Files.writeString(enclave.resolve(file), "x");
      }

      for (String strategy : new String[] {"Enforce", "Permissive"})
      {
         Function<jros2Settings.Key, String> settings = ROS2DiscoveryConfigurationTest.settings(jros2Settings.Key.SECURITY_ENABLE,
                                                                                               "true",
                                                                                               jros2Settings.Key.SECURITY_STRATEGY,
                                                                                               strategy,
                                                                                               jros2Settings.Key.SECURITY_KEYSTORE,
                                                                                               keystore.toString());
         ParticipantProfiles.Environment secure = new ParticipantProfiles.Environment(settings,
                                                                                     new String[0],
                                                                                     () -> true,
                                                                                     false,
                                                                                     ParticipantProfilesTest.FAKE_DNS);
         ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_print_secure_" + strategy,
                                                                                                "node",
                                                                                                3,
                                                                                                ROS2NodeOptions.defaults().enclave("/printout"),
                                                                                                secure);
         assertEquals(ROS2SecurityConfiguration.Status.ENABLED, configuration.security.getStatus());
         List<String> lines = lines(format(configuration));
         assertTrue(lines.contains("Enclave: /printout"), lines.toString());
         assertTrue(lines.contains("SROS2 security: enabled (" + strategy + ", " + enclave.toFile().getPath() + ")"), lines.toString());
      }

      // Permissive without a keystore runs unsecured, and the printout must say so
      Function<jros2Settings.Key, String> noKeystore = ROS2DiscoveryConfigurationTest.settings(jros2Settings.Key.SECURITY_ENABLE, "true");
      ParticipantProfiles.ParticipantConfiguration unsecured = ParticipantProfiles.build("p_print_unsecured",
                                                                                         "node",
                                                                                         3,
                                                                                         null,
                                                                                         new ParticipantProfiles.Environment(noKeystore,
                                                                                                                             new String[0],
                                                                                                                             () -> true,
                                                                                                                             false,
                                                                                                                             ParticipantProfilesTest.FAKE_DNS));
      assertEquals(ROS2SecurityConfiguration.Status.PERMISSIVE_UNSECURED, unsecured.security.getStatus());
      assertTrue(lines(format(unsecured)).contains("SROS2 security: requested but NOT active (Permissive strategy, running unsecured)"));
   }

   @Test
   public void testPrintCanBeDisabled()
   {
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_print_log", "logged_node", 3, null, ParticipantProfilesTest.desktop());
      List<String> logged = new ArrayList<>();
      Handler handler = new Handler()
      {
         @Override
         public void publish(LogRecord record)
         {
            if (record.getLevel() == Level.INFO && record.getMessage().contains("Created ROS2Node: logged_node"))
            {
               synchronized (logged)
               {
                  logged.add(record.getMessage());
               }
            }
         }

         @Override
         public void flush()
         {
         }

         @Override
         public void close()
         {
         }
      };

      jros2.getLogger().addHandler(handler);
      try
      {
         System.setProperty(PRINTOUT_PROPERTY, "FALSE");
         ROS2NodePrintout.print(ROS2Node.class, configuration);
         synchronized (logged)
         {
            assertTrue(logged.isEmpty(), "jros2.node.printout=false (case-insensitive) disables the printout");
         }

         System.setProperty(PRINTOUT_PROPERTY, "true");
         ROS2NodePrintout.print(ROS2Node.class, configuration);
         synchronized (logged)
         {
            assertEquals(1, logged.size());
            assertEquals(format(configuration), logged.get(0));
         }
      }
      finally
      {
         jros2.getLogger().removeHandler(handler);
      }
   }
}
