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
import us.ihmc.fastddsjava.profiles.OctetVectorTools;
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.TransportDescriptorTypeTools;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Participant profiles built by {@link ParticipantProfiles}: enclave user data, rmw_fastrtps discovery configurations, transport
 * interplay (builtin, interface whitelist, custom, Android), thread settings and the printout. XML-level only (no DDS); the
 * same configurations are load-tested through the natives in {@link ParticipantProfilesNativeTest}.
 */
public class ParticipantProfilesTest
{
   /**
    * Every participant preallocates the network buffers of its send buffers, so that their vectors do not grow at
    * unpredictable times in steady state (see ParticipantProfiles.NETWORK_BUFFERS_PER_SEND_BUFFER).
    */
   private static final String SEND_BUFFERS = "<send_buffers><network_buffers_config><initial>" + ParticipantProfiles.NETWORK_BUFFERS_PER_SEND_BUFFER
                                              + "</initial><increment>16</increment></network_buffers_config></send_buffers>";

   static final ROS2DiscoveryConfiguration.PeerResolver FAKE_DNS = host ->
   {
      switch (host)
      {
         case "robot.local":
            return new InetAddress[] {InetAddress.getByName("192.0.2.10")};
         case "10.1.2.3":
            return new InetAddress[] {InetAddress.getByName("10.1.2.3")};
         default:
            throw new UnknownHostException(host);
      }
   };

   static ParticipantProfiles.Environment environment(Function<jros2Settings.Key, String> settings, String[] whitelist, boolean android)
   {
      return new ParticipantProfiles.Environment(settings, whitelist, () -> false, android, FAKE_DNS);
   }

   static ParticipantProfiles.Environment desktop(Object... keyValues)
   {
      return environment(ROS2DiscoveryConfigurationTest.settings(keyValues), new String[0], false);
   }

   static String rtpsXml(ParticipantProfiles.ParticipantConfiguration configuration)
   {
      ProfilesXML profilesXML = new ProfilesXML();
      profilesXML.addParticipantProfile(configuration.profile);
      String xml = profilesXML.marshall();
      return xml.substring(xml.indexOf("<rtps>"), xml.indexOf("</rtps>") + "</rtps>".length());
   }

   private static void assertContains(String xml, String expected)
   {
      assertTrue(xml.contains(expected), () -> "Expected '" + expected + "' in\n" + xml);
   }

   private static void assertNotContains(String xml, String unexpected)
   {
      assertFalse(xml.contains(unexpected), () -> "Did not expect '" + unexpected + "' in\n" + xml);
   }

   private static final String BUILTIN_MEMORY = "<readerHistoryMemoryPolicy>PREALLOCATED_WITH_REALLOC</readerHistoryMemoryPolicy>"
                                                + "<writerHistoryMemoryPolicy>PREALLOCATED_WITH_REALLOC</writerHistoryMemoryPolicy>";
   private static final String ENCLAVE_ROOT = "<userData><value>65.6e.63.6c.61.76.65.3d.2f.3b.00</value></userData>";

   @Test
   public void testDefaultSubnetParticipant()
   {
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_t1", "node", 42, null, desktop());
      ParticipantProfileType profile = configuration.profile;
      assertEquals("p_t1", profile.getProfileName());
      assertEquals(42, profile.getDomainId());
      assertEquals("node", profile.getRtps().getName());
      assertTrue(configuration.usesBuiltinTransports());
      assertEquals(ROS2DiscoveryRange.SUBNET, configuration.discovery.getRange());
      assertEquals("/", configuration.enclave);

      // Fast-DDS defaults for SUBNET; rmw's builtin memory policy; ROS graph enclave user data
      assertEquals("<rtps><name>node</name><builtin>" + BUILTIN_MEMORY + "</builtin><useBuiltinTransports>true</useBuiltinTransports><allocation>" + SEND_BUFFERS + "</allocation>" + ENCLAVE_ROOT
                   + "</rtps>", rtpsXml(configuration));
      assertTrue(configuration.warnings.isEmpty());
   }

   @Test
   public void testEnclaveUserData()
   {
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_t2",
                                                                                             "node",
                                                                                             0,
                                                                                             ROS2NodeOptions.defaults().enclave("/talker_listener/talker"),
                                                                                             desktop());
      byte[] userData = OctetVectorTools.parseOctetString(configuration.profile.getRtps().getUserData().getValue().get(0));
      assertEquals(0, userData[userData.length - 1], "rmw_fastrtps writes a terminating NUL");
      assertEquals("enclave=/talker_listener/talker;", new String(userData, 0, userData.length - 1, StandardCharsets.US_ASCII));

      for (String invalid : new String[] {"", "relative", "/trailing/", "//double", "/a//b", "/1digit", "/a/2b", "/bad-char", "/sp ace"})
      {
         assertThrows(IllegalArgumentException.class,
                      () -> ParticipantProfiles.build("p_x", "node", 0, ROS2NodeOptions.defaults().enclave(invalid), desktop()),
                      invalid);
      }
      assertThrows(IllegalArgumentException.class,
                   () -> ParticipantProfiles.build("p_x", "node", 0, ROS2NodeOptions.defaults().enclave("/" + "a".repeat(255)), desktop()));
      assertDoesNotThrow(() -> ParticipantProfiles.build("p_x", "node", 0, ROS2NodeOptions.defaults().enclave("/a1/_b/C_2"), desktop()));
   }

   @Test
   public void testOffDiscovery()
   {
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_t3",
                                                                                             "node",
                                                                                             0,
                                                                                             null,
                                                                                             desktop(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE, "OFF"));
      String xml = rtpsXml(configuration);
      // rmw_fastrtps OFF: one participant, unicast metatraffic locator (no multicast), builtin transports kept, no peers
      assertContains(xml, "<builtin><metatrafficUnicastLocatorList><locator><udpv4/></locator></metatrafficUnicastLocatorList>" + BUILTIN_MEMORY + "</builtin>");
      assertContains(xml, "<allocation><total_participants><initial>1</initial><maximum>1</maximum><increment>0</increment></total_participants>"
                          + SEND_BUFFERS + "</allocation>");
      assertContains(xml, "<useBuiltinTransports>true</useBuiltinTransports>");
      assertNotContains(xml, "initialPeersList");
   }

   @Test
   public void testLocalhostDiscoveryDesktopDefault()
   {
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_t4",
                                                                                             "node",
                                                                                             0,
                                                                                             null,
                                                                                             desktop(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE, "LOCALHOST"));
      String xml = rtpsXml(configuration);
      assertContains(xml,
                     "<builtin><metatrafficUnicastLocatorList><locator><udpv4/></locator></metatrafficUnicastLocatorList>"
                     + "<initialPeersList><locator><udpv4><address>127.0.0.1</address></udpv4></locator></initialPeersList>" + BUILTIN_MEMORY + "</builtin>");
      assertContains(xml, "<useBuiltinTransports>false</useBuiltinTransports>");
      assertContains(xml, "<allocation>" + SEND_BUFFERS + "</allocation>");
      assertNotContains(xml, "total_participants");

      // rmw_fastrtps: SHM + UDPv4 with maxInitialPeersRange 32 (and no interface whitelist)
      TransportDescriptorType[] transports = configuration.transports;
      assertEquals(2, transports.length);
      assertEquals("SHM", transports[0].getType());
      assertEquals("UDPv4", transports[1].getType());
      assertEquals(32L, transports[1].getMaxInitialPeersRange());
      assertNull(transports[1].getInterfaceWhiteList());
      assertContains(xml, "<userTransports><transport_id>" + transports[0].getTransportId() + "</transport_id><transport_id>" + transports[1].getTransportId()
                          + "</transport_id></userTransports>");
      // Dedicated descriptors, independent of the shared cached ones (which callers may have modified, e.g. with a whitelist)
      assertSame(ParticipantProfiles.LocalhostTransports.SHM, transports[0]);
      assertSame(ParticipantProfiles.LocalhostTransports.UDPV4, transports[1]);
      TransportDescriptorTypeTools.setInterfacesWhitelist(TransportDescriptorTypeTools.createUDPv4Descriptor(), "10.99.0.1");
      try
      {
         TransportDescriptorType[] again = ParticipantProfiles.build("p_t4c", "node", 0, null, desktop(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE, "LOCALHOST")).transports;
         assertNull(again[1].getInterfaceWhiteList());
      }
      finally
      {
         TransportDescriptorTypeTools.setInterfacesWhitelist(TransportDescriptorTypeTools.createUDPv4Descriptor());
      }
      // Same configuration, same descriptors (stable transport IDs)
      ParticipantProfiles.ParticipantConfiguration again = ParticipantProfiles.build("p_t4b",
                                                                                     "node",
                                                                                     0,
                                                                                     null,
                                                                                     desktop(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE, "LOCALHOST"));
      assertSame(transports[1], again.transports[1]);
   }

   @Test
   public void testLocalhostWithStaticPeers()
   {
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_t5",
                                                                                             "node",
                                                                                             0,
                                                                                             null,
                                                                                             desktop(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE,
                                                                                                     "LOCALHOST",
                                                                                                     jros2Settings.Key.STATIC_PEERS,
                                                                                                     "robot.local;;10.1.2.3"));
      // Each peer resolved in Java (DNS), no port, then 127.0.0.1
      assertContains(rtpsXml(configuration),
                     "<initialPeersList><locator><udpv4><address>192.0.2.10</address></udpv4></locator>"
                     + "<locator><udpv4><address>10.1.2.3</address></udpv4></locator>"
                     + "<locator><udpv4><address>127.0.0.1</address></udpv4></locator></initialPeersList>");
      assertEquals(java.util.List.of("192.0.2.10", "10.1.2.3"), configuration.resolvedStaticPeers);
   }

   @Test
   public void testSubnetWithAndWithoutStaticPeers()
   {
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_t6",
                                                                                             "node",
                                                                                             0,
                                                                                             ROS2NodeOptions.defaults().staticPeers("robot.local"),
                                                                                             desktop());
      String xml = rtpsXml(configuration);
      // Peers followed by the default multicast address so multicast announcements continue
      assertContains(xml,
                     "<builtin><initialPeersList><locator><udpv4><address>192.0.2.10</address></udpv4></locator>"
                     + "<locator><udpv4><address>239.255.0.1</address></udpv4></locator></initialPeersList>" + BUILTIN_MEMORY + "</builtin>");
      assertNotContains(xml, "metatrafficUnicastLocatorList");
      assertContains(xml, "<useBuiltinTransports>true</useBuiltinTransports>");

      assertNotContains(rtpsXml(ParticipantProfiles.build("p_t6b", "node", 0, null, desktop(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE, "SUBNET"))),
                        "initialPeersList");
   }

   @Test
   public void testSystemDefaultIgnoresStaticPeers()
   {
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_t7",
                                                                                             "node",
                                                                                             0,
                                                                                             null,
                                                                                             desktop(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE,
                                                                                                     "SYSTEM_DEFAULT",
                                                                                                     jros2Settings.Key.STATIC_PEERS,
                                                                                                     "no-such-host.invalid"));
      String xml = rtpsXml(configuration);
      assertEquals("<rtps><name>node</name><builtin>" + BUILTIN_MEMORY + "</builtin><useBuiltinTransports>true</useBuiltinTransports><allocation>" + SEND_BUFFERS + "</allocation>" + ENCLAVE_ROOT + "</rtps>",
                   xml);
      assertEquals(1, configuration.warnings.size());
   }

   @Test
   public void testUnresolvablePeerFails()
   {
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                        () -> ParticipantProfiles.build("p_t8",
                                                                                        "node",
                                                                                        0,
                                                                                        null,
                                                                                        desktop(jros2Settings.Key.STATIC_PEERS, "no-such-host.invalid")));
      assertTrue(exception.getMessage().contains("no-such-host.invalid"));
   }

   @Test
   public void testLocalhostOnlyEnvironment()
   {
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_t9",
                                                                                             "node",
                                                                                             0,
                                                                                             ROS2NodeOptions.defaults()
                                                                                                            .discoveryRange(ROS2DiscoveryRange.SUBNET)
                                                                                                            .staticPeers("no-such-host.invalid"),
                                                                                             desktop(jros2Settings.Key.LOCALHOST_ONLY, "1"));
      assertEquals(ROS2DiscoveryRange.LOCALHOST, configuration.discovery.getRange());
      // Peers are dropped before resolution, so the bad peer does not matter
      assertContains(rtpsXml(configuration), "<initialPeersList><locator><udpv4><address>127.0.0.1</address></udpv4></locator></initialPeersList>");
      assertFalse(configuration.warnings.isEmpty());
   }

   @Test
   public void testInterfaceWhitelistInterplay()
   {
      String uniqueIp = "10.77." + (System.nanoTime() % 200) + ".1";
      ParticipantProfiles.Environment whitelisted = environment(ROS2DiscoveryConfigurationTest.settings(), new String[] {uniqueIp}, false);

      ParticipantProfiles.ParticipantConfiguration subnet = ParticipantProfiles.build("p_t10", "node", 0, null, whitelisted);
      assertFalse(subnet.usesBuiltinTransports());
      assertEquals(2, subnet.transports.length);
      assertEquals("UDPv4", subnet.transports[0].getType());
      assertTrue(subnet.transports[0].getInterfaceWhiteList().getAddressOrInterface().contains(uniqueIp));
      assertTrue(subnet.transports[0].getInterfaceWhiteList().getAddressOrInterface().contains("127.0.0.1"));
      assertNull(subnet.transports[0].getMaxInitialPeersRange());
      assertEquals("SHM", subnet.transports[1].getType());

      // LOCALHOST keeps the whitelist (it contains the loopback) and raises the peer range on a copy
      ParticipantProfiles.ParticipantConfiguration localhost = ParticipantProfiles.build("p_t10b",
                                                                                         "node",
                                                                                         0,
                                                                                         ROS2NodeOptions.defaults().discoveryRange(ROS2DiscoveryRange.LOCALHOST),
                                                                                         whitelisted);
      assertEquals(32L, localhost.transports[0].getMaxInitialPeersRange());
      assertSame(subnet.transports[0].getInterfaceWhiteList(), localhost.transports[0].getInterfaceWhiteList());
      assertNotEquals(subnet.transports[0].getTransportId(), localhost.transports[0].getTransportId());
      assertNull(subnet.transports[0].getMaxInitialPeersRange());
   }

   @Test
   public void testCustomTransportsInterplay()
   {
      TransportDescriptorType custom = new TransportDescriptorType();
      custom.setTransportId("g2_custom_udp_" + System.nanoTime());
      custom.setType("UDPv4");
      TransportDescriptorType customWithRange = new TransportDescriptorType();
      customWithRange.setTransportId("g2_custom_udp_range_" + System.nanoTime());
      customWithRange.setType("UDPv4");
      customWithRange.setMaxInitialPeersRange(8L);

      ROS2NodeOptions options = ROS2NodeOptions.defaults().transports(custom, customWithRange);
      ParticipantProfiles.ParticipantConfiguration subnet = ParticipantProfiles.build("p_t11", "node", 0, options, desktop());
      assertSame(custom, subnet.transports[0]);
      assertSame(customWithRange, subnet.transports[1]);

      ParticipantProfiles.ParticipantConfiguration localhost = ParticipantProfiles.build("p_t11b",
                                                                                         "node",
                                                                                         0,
                                                                                         new ROS2NodeOptions(options).discoveryRange(ROS2DiscoveryRange.LOCALHOST),
                                                                                         desktop());
      assertEquals(32L, localhost.transports[0].getMaxInitialPeersRange());
      assertNull(custom.getMaxInitialPeersRange(), "the user's descriptor must not be modified");
      // A peer range chosen by the user is respected
      assertSame(customWithRange, localhost.transports[1]);
   }

   @Test
   public void testAndroidTransports()
   {
      ParticipantProfiles.Environment android = environment(ROS2DiscoveryConfigurationTest.settings(), new String[0], true);
      ParticipantProfiles.ParticipantConfiguration subnet = ParticipantProfiles.build("p_t12", "node", 0, null, android);
      assertEquals(1, subnet.transports.length);
      assertEquals("UDPv4", subnet.transports[0].getType());
      assertContains(rtpsXml(subnet), "<useBuiltinTransports>false</useBuiltinTransports>");

      // LOCALHOST on Android: UDPv4 only (no SHM), peer range 32
      ParticipantProfiles.ParticipantConfiguration localhost = ParticipantProfiles.build("p_t12b",
                                                                                         "node",
                                                                                         0,
                                                                                         ROS2NodeOptions.defaults().discoveryRange(ROS2DiscoveryRange.LOCALHOST),
                                                                                         android);
      assertEquals(1, localhost.transports.length);
      assertEquals("UDPv4", localhost.transports[0].getType());
      assertEquals(32L, localhost.transports[0].getMaxInitialPeersRange());
   }

   @Test
   public void testThreadSettingsWithBuiltinTransports()
   {
      ROS2ThreadSettings threads = new ROS2ThreadSettings().priority(0)
                                                           .stackSizeBytes(1048576)
                                                           .forThreads(ROS2ThreadSettings.Kind.RECEPTION, new ROS2ThreadSettings().affinityMask(0b10));
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_t13",
                                                                                             "node",
                                                                                             0,
                                                                                             ROS2NodeOptions.defaults().threadSettings(threads),
                                                                                             desktop());
      String xml = rtpsXml(configuration);
      // Reception threads of builtin transports only apply when an explicit <builtinTransports> follows them
      assertContains(xml,
                     "<builtin_transports_reception_threads><priority>0</priority><affinity>2</affinity><stack_size>1048576</stack_size>"
                     + "</builtin_transports_reception_threads><builtinTransports>DEFAULT</builtinTransports>");
      assertNotContains(xml, "useBuiltinTransports");
      assertContains(xml, "<builtin_controllers_sender_thread><priority>0</priority><stack_size>1048576</stack_size></builtin_controllers_sender_thread>");
      assertContains(xml, "<timed_events_thread><priority>0</priority><stack_size>1048576</stack_size></timed_events_thread>");
      assertContains(xml, "<discovery_server_thread><priority>0</priority><stack_size>1048576</stack_size></discovery_server_thread>");
      assertContains(xml, "<typelookup_service_thread><priority>0</priority><stack_size>1048576</stack_size></typelookup_service_thread>");
      // No security: no security log thread
      assertNotContains(xml, "security_log_thread");
      assertTrue(configuration.usesBuiltinTransports());

      // The options' thread settings are copied
      threads.priority(9);
      assertEquals(0, configuration.threadSettings.getPriority());
   }

   @Test
   public void testThreadSettingsWithUserTransports()
   {
      ROS2ThreadSettings threads = new ROS2ThreadSettings().forThreads(ROS2ThreadSettings.Kind.RECEPTION,
                                                                       new ROS2ThreadSettings().schedulingPolicy(0).priority(1));
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_t14",
                                                                                             "node",
                                                                                             0,
                                                                                             ROS2NodeOptions.defaults()
                                                                                                            .threadSettings(threads)
                                                                                                            .discoveryRange(ROS2DiscoveryRange.LOCALHOST),
                                                                                             desktop());
      for (TransportDescriptorType transport : configuration.transports)
      {
         assertEquals(0, transport.getDefaultReceptionThreads().getSchedulingPolicy());
         assertEquals(1, transport.getDefaultReceptionThreads().getPriority());
      }
      assertEquals(32L, configuration.transports[1].getMaxInitialPeersRange());
      // The base descriptors are untouched
      assertNull(ParticipantProfiles.LocalhostTransports.SHM.getDefaultReceptionThreads());
      assertNull(ParticipantProfiles.LocalhostTransports.UDPV4.getDefaultReceptionThreads());
      String xml = rtpsXml(configuration);
      // Only reception threads were configured
      assertNotContains(xml, "timed_events_thread");
      assertNotContains(xml, "builtinTransports");
   }

   @Test
   public void testPrintoutDescribesEffectiveConfiguration()
   {
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_t15",
                                                                                             "node",
                                                                                             7,
                                                                                             ROS2NodeOptions.defaults()
                                                                                                            .enclave("/talker_listener/talker")
                                                                                                            .threadSettings(new ROS2ThreadSettings().priority(3)),
                                                                                             desktop(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE,
                                                                                                     "LOCALHOST",
                                                                                                     jros2Settings.Key.STATIC_PEERS,
                                                                                                     "robot.local"));
      String printout = ROS2NodePrintout.format(ROS2Node.class, configuration).toString();
      assertTrue(printout.contains("Created ROS2Node: node"), printout);
      assertTrue(printout.contains("DomainID: 7"), printout);
      assertTrue(printout.contains("Using custom transports:"), printout);
      assertTrue(printout.contains("Discovery range: LOCALHOST (Specified by: ROS_AUTOMATIC_DISCOVERY_RANGE)"), printout);
      assertTrue(printout.contains("Static peers: robot.local (192.0.2.10) (Specified by: ROS_STATIC_PEERS)"), printout);
      assertTrue(printout.contains("Enclave: /talker_listener/talker"), printout);
      assertTrue(printout.contains("SROS2 security: disabled"), printout);
      assertTrue(printout.contains("Thread settings: ROS2ThreadSettings{priority=3}"), printout);

      // The builder is reused (no new builder per printout)
      assertSame(ROS2NodePrintout.format(ROS2Node.class, configuration), ROS2NodePrintout.format(ROS2Node.class, configuration));
   }
}
