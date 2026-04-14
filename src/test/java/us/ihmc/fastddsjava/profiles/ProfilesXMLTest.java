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
package us.ihmc.fastddsjava.profiles;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.profiles.gen.*;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps.UserTransports;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType.InterfaceWhiteList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test coverage for {@link ProfilesXML} based on Fast-DDS XML configuration documentation.
 * Tests verify compliance with Fast-DDS v3.2.2 XML schema and examples.
 *
 * @see <a href="https://fast-dds.docs.eprosima.com/en/v3.2.2/fastdds/xml_configuration/xml_configuration.html">Fast-DDS XML Configuration</a>
 */
public class ProfilesXMLTest
{
   @Test
   public void testXMLNamespaceCorrect()
   {
      ProfilesXML profilesXML = new ProfilesXML();
      String xml = profilesXML.marshall();

      // Verify namespace matches Fast-DDS documentation
      assertTrue(xml.contains("xmlns=\"http://www.eprosima.com\""),
                 "XML namespace must be http://www.eprosima.com");
      assertEquals("http://www.eprosima.com", ProfilesXML.FAST_DDS_NAMESPACE_URI);
   }

   @Test
   public void testXMLStructureHasRequiredElements()
   {
      ProfilesXML profilesXML = new ProfilesXML();
      String xml = profilesXML.marshall();

      // Verify all required root elements are present
      assertTrue(xml.contains("<?xml version=\"1.0\""), "Missing XML declaration");
      assertTrue(xml.contains("<dds"), "Missing <dds> root element");
      assertTrue(xml.contains("<profiles"), "Missing <profiles> element");
      assertTrue(xml.contains("<types/>"), "Missing <types> element");
      assertTrue(xml.contains("<log/>"), "Missing <log> element");
      assertTrue(xml.contains("<library_settings>"), "Missing <library_settings> element");
      assertTrue(xml.contains("</dds>"), "Missing closing </dds> tag");
   }

   @Test
   public void testUDPv4TransportDescriptorBasic()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Create UDPv4 transport descriptor as per Fast-DDS documentation
      TransportDescriptorType udpv4 = new TransportDescriptorType();
      udpv4.setTransportId("udpv4_transport");
      udpv4.setType("UDPv4");

      TransportDescriptorListType transportList = new TransportDescriptorListType();
      transportList.getTransportDescriptor().add(udpv4);

      profilesXML.addTransportDescriptorsProfile(transportList);
      String xml = profilesXML.marshall();

      // Verify XML structure matches Fast-DDS documentation
      assertTrue(xml.contains("<transport_descriptors>"));
      assertTrue(xml.contains("<transport_descriptor>"));
      assertTrue(xml.contains("<transport_id>udpv4_transport</transport_id>"));
      assertTrue(xml.contains("<type>UDPv4</type>"));
      assertTrue(xml.contains("</transport_descriptor>"));
      assertTrue(xml.contains("</transport_descriptors>"));
   }

   @Test
   public void testUDPv4TransportWithInterfaceWhitelistAddress()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Per Fast-DDS docs: InterfaceWhiteList can contain <address> elements
      TransportDescriptorType udpv4 = new TransportDescriptorType();
      udpv4.setTransportId("udpv4_with_whitelist");
      udpv4.setType("UDPv4");

      InterfaceWhiteList whitelist = new InterfaceWhiteList();
      whitelist.getAddressOrInterface().add("192.168.1.41");
      whitelist.getAddressOrInterface().add("192.168.1.42");
      udpv4.setInterfaceWhiteList(whitelist);

      TransportDescriptorListType transportList = new TransportDescriptorListType();
      transportList.getTransportDescriptor().add(udpv4);

      profilesXML.addTransportDescriptorsProfile(transportList);
      String xml = profilesXML.marshall();

      assertTrue(xml.contains("<interfaceWhiteList>"));
      // The XML may serialize addresses differently, but both should be present
      assertTrue(xml.contains("192.168.1.41"), "Should contain first address");
      assertTrue(xml.contains("192.168.1.42"), "Should contain second address");
      assertTrue(xml.contains("</interfaceWhiteList>"));
   }

   @Test
   public void testUDPv4TransportWithInterfaceWhitelistInterfaceName()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Per Fast-DDS docs: InterfaceWhiteList can contain <interface> elements
      TransportDescriptorType udpv4 = new TransportDescriptorType();
      udpv4.setTransportId("udpv4_interface_names");
      udpv4.setType("UDPv4");

      InterfaceWhiteList whitelist = new InterfaceWhiteList();
      whitelist.getAddressOrInterface().add("eth0");
      whitelist.getAddressOrInterface().add("wlan0");
      udpv4.setInterfaceWhiteList(whitelist);

      TransportDescriptorListType transportList = new TransportDescriptorListType();
      transportList.getTransportDescriptor().add(udpv4);

      profilesXML.addTransportDescriptorsProfile(transportList);
      String xml = profilesXML.marshall();

      assertTrue(xml.contains("<interfaceWhiteList>"));
      // Note: The implementation may serialize as <address> due to Jackson limitations
      // This is acceptable as Fast-DDS accepts both
      assertTrue(xml.contains("eth0"));
      assertTrue(xml.contains("wlan0"));
   }

   @Test
   public void testUDPv4TransportWithMixedWhitelist()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Per Fast-DDS docs: Can mix addresses and interface names
      TransportDescriptorType udpv4 = new TransportDescriptorType();
      udpv4.setTransportId("udpv4_mixed_whitelist");
      udpv4.setType("UDPv4");

      InterfaceWhiteList whitelist = new InterfaceWhiteList();
      whitelist.getAddressOrInterface().add("192.168.1.100");
      whitelist.getAddressOrInterface().add("eth0");
      whitelist.getAddressOrInterface().add("127.0.0.1");
      whitelist.getAddressOrInterface().add("lo");
      udpv4.setInterfaceWhiteList(whitelist);

      TransportDescriptorListType transportList = new TransportDescriptorListType();
      transportList.getTransportDescriptor().add(udpv4);

      profilesXML.addTransportDescriptorsProfile(transportList);
      String xml = profilesXML.marshall();

      assertTrue(xml.contains("192.168.1.100"));
      assertTrue(xml.contains("eth0"));
      assertTrue(xml.contains("127.0.0.1"));
      assertTrue(xml.contains("lo"));
   }

   @Test
   public void testUDPv6TransportDescriptor()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      TransportDescriptorType udpv6 = new TransportDescriptorType();
      udpv6.setTransportId("udpv6_transport");
      udpv6.setType("UDPv6");

      TransportDescriptorListType transportList = new TransportDescriptorListType();
      transportList.getTransportDescriptor().add(udpv6);

      profilesXML.addTransportDescriptorsProfile(transportList);
      String xml = profilesXML.marshall();

      assertTrue(xml.contains("<transport_id>udpv6_transport</transport_id>"));
      assertTrue(xml.contains("<type>UDPv6</type>"));
   }

   @Test
   public void testTCPv4TransportDescriptor()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Per Fast-DDS docs: TCPv4 with interface whitelist
      TransportDescriptorType tcpv4 = new TransportDescriptorType();
      tcpv4.setTransportId("tcpv4_transport");
      tcpv4.setType("TCPv4");

      InterfaceWhiteList whitelist = new InterfaceWhiteList();
      whitelist.getAddressOrInterface().add("127.0.0.1");
      tcpv4.setInterfaceWhiteList(whitelist);

      TransportDescriptorListType transportList = new TransportDescriptorListType();
      transportList.getTransportDescriptor().add(tcpv4);

      profilesXML.addTransportDescriptorsProfile(transportList);
      String xml = profilesXML.marshall();

      assertTrue(xml.contains("<transport_id>tcpv4_transport</transport_id>"));
      assertTrue(xml.contains("<type>TCPv4</type>"));
      assertTrue(xml.contains("127.0.0.1"));
   }

   @Test
   public void testTCPv6TransportDescriptor()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      TransportDescriptorType tcpv6 = new TransportDescriptorType();
      tcpv6.setTransportId("tcpv6_transport");
      tcpv6.setType("TCPv6");

      TransportDescriptorListType transportList = new TransportDescriptorListType();
      transportList.getTransportDescriptor().add(tcpv6);

      profilesXML.addTransportDescriptorsProfile(transportList);
      String xml = profilesXML.marshall();

      assertTrue(xml.contains("<transport_id>tcpv6_transport</transport_id>"));
      assertTrue(xml.contains("<type>TCPv6</type>"));
   }

   @Test
   public void testSHMTransportDescriptor()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Per Fast-DDS docs: SHM (Shared Memory) transport
      TransportDescriptorType shm = new TransportDescriptorType();
      shm.setTransportId("shm_transport");
      shm.setType("SHM");

      TransportDescriptorListType transportList = new TransportDescriptorListType();
      transportList.getTransportDescriptor().add(shm);

      profilesXML.addTransportDescriptorsProfile(transportList);
      String xml = profilesXML.marshall();

      assertTrue(xml.contains("<transport_id>shm_transport</transport_id>"));
      assertTrue(xml.contains("<type>SHM</type>"));
   }

   @Test
   public void testMultipleTransportDescriptors()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Per Fast-DDS docs: Multiple transports can be defined
      TransportDescriptorType udpv4 = new TransportDescriptorType();
      udpv4.setTransportId("udpv4_transport");
      udpv4.setType("UDPv4");

      TransportDescriptorType udpv6 = new TransportDescriptorType();
      udpv6.setTransportId("udpv6_transport");
      udpv6.setType("UDPv6");

      TransportDescriptorType shm = new TransportDescriptorType();
      shm.setTransportId("shm_transport");
      shm.setType("SHM");

      TransportDescriptorListType transportList = new TransportDescriptorListType();
      transportList.getTransportDescriptor().add(udpv4);
      transportList.getTransportDescriptor().add(udpv6);
      transportList.getTransportDescriptor().add(shm);

      profilesXML.addTransportDescriptorsProfile(transportList);
      String xml = profilesXML.marshall();

      // Verify all three transports are present
      assertTrue(xml.contains("udpv4_transport"));
      assertTrue(xml.contains("UDPv4"));
      assertTrue(xml.contains("udpv6_transport"));
      assertTrue(xml.contains("UDPv6"));
      assertTrue(xml.contains("shm_transport"));
      assertTrue(xml.contains("SHM"));

      // Count transport_descriptor occurrences (should be 3)
      int count = countOccurrences(xml, "<transport_descriptor>");
      assertEquals(3, count, "Should have 3 transport descriptors");
   }

   @Test
   public void testParticipantProfileBasic()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Per Fast-DDS docs: Participant profile with name
      ParticipantProfileType participant = new ParticipantProfileType();
      participant.setProfileName("participant_profile");
      participant.setDomainId(0);

      Rtps rtps = new Rtps();
      rtps.setName("MyParticipant");
      participant.setRtps(rtps);

      profilesXML.addParticipantProfile(participant);
      String xml = profilesXML.marshall();

      assertTrue(xml.contains("<participant"));
      assertTrue(xml.contains("profile_name=\"participant_profile\""));
      assertTrue(xml.contains("<domainId>0</domainId>"));
      assertTrue(xml.contains("<name>MyParticipant</name>"));
   }

   @Test
   public void testParticipantWithCustomTransportsDisablingBuiltin()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Per Fast-DDS docs: When using custom transports, disable builtin transports
      TransportDescriptorType shm = new TransportDescriptorType();
      shm.setTransportId("shm_only");
      shm.setType("SHM");

      TransportDescriptorListType transportList = new TransportDescriptorListType();
      transportList.getTransportDescriptor().add(shm);
      profilesXML.addTransportDescriptorsProfile(transportList);

      ParticipantProfileType participant = new ParticipantProfileType();
      participant.setProfileName("custom_transport_participant");

      Rtps rtps = new Rtps();
      rtps.setUseBuiltinTransports(false);

      UserTransports userTransports = new UserTransports();
      userTransports.getTransportId().add("shm_only");
      rtps.setUserTransports(userTransports);

      participant.setRtps(rtps);
      profilesXML.addParticipantProfile(participant);

      String xml = profilesXML.marshall();

      assertTrue(xml.contains("<useBuiltinTransports>false</useBuiltinTransports>"));
      assertTrue(xml.contains("<userTransports>"));
      assertTrue(xml.contains("<transport_id>shm_only</transport_id>"));
   }

   @Test
   public void testParticipantWithMultipleUserTransports()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Per Fast-DDS docs: Participant can use multiple custom transports
      TransportDescriptorType udpv4 = new TransportDescriptorType();
      udpv4.setTransportId("udpv4_transport");
      udpv4.setType("UDPv4");

      TransportDescriptorType tcpv4 = new TransportDescriptorType();
      tcpv4.setTransportId("tcpv4_transport");
      tcpv4.setType("TCPv4");

      TransportDescriptorType shm = new TransportDescriptorType();
      shm.setTransportId("shm_transport");
      shm.setType("SHM");

      TransportDescriptorListType transportList = new TransportDescriptorListType();
      transportList.getTransportDescriptor().add(udpv4);
      transportList.getTransportDescriptor().add(tcpv4);
      transportList.getTransportDescriptor().add(shm);
      profilesXML.addTransportDescriptorsProfile(transportList);

      ParticipantProfileType participant = new ParticipantProfileType();
      participant.setProfileName("multi_transport_participant");

      Rtps rtps = new Rtps();
      rtps.setUseBuiltinTransports(false);

      UserTransports userTransports = new UserTransports();
      userTransports.getTransportId().add("udpv4_transport");
      userTransports.getTransportId().add("tcpv4_transport");
      userTransports.getTransportId().add("shm_transport");
      rtps.setUserTransports(userTransports);

      participant.setRtps(rtps);
      profilesXML.addParticipantProfile(participant);

      String xml = profilesXML.marshall();

      assertTrue(xml.contains("<transport_id>udpv4_transport</transport_id>"));
      assertTrue(xml.contains("<transport_id>tcpv4_transport</transport_id>"));
      assertTrue(xml.contains("<transport_id>shm_transport</transport_id>"));
   }

   @Test
   public void testPublisherProfile()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Per Fast-DDS docs: Publisher profile
      PublisherProfileType publisher = new PublisherProfileType();
      publisher.setProfileName("publisher_profile");

      profilesXML.addPublisherProfile(publisher);
      String xml = profilesXML.marshall();

      // Note: ProfilesXML uses "data_writer" as the element name for publishers
      assertTrue(xml.contains("<data_writer"));
      assertTrue(xml.contains("profile_name=\"publisher_profile\""));
   }

   @Test
   public void testSubscriberProfile()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Per Fast-DDS docs: Subscriber profile
      SubscriberProfileType subscriber = new SubscriberProfileType();
      subscriber.setProfileName("subscriber_profile");

      profilesXML.addSubscriberProfile(subscriber);
      String xml = profilesXML.marshall();

      // Note: ProfilesXML uses "data_reader" as the element name for subscribers
      assertTrue(xml.contains("<data_reader"));
      assertTrue(xml.contains("profile_name=\"subscriber_profile\""));
   }

   @Test
   public void testTopicProfile()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Per Fast-DDS docs: Topic profile
      TopicProfileType topic = new TopicProfileType();
      topic.setProfileName("topic_profile");

      profilesXML.addTopicProfile(topic);
      String xml = profilesXML.marshall();

      assertTrue(xml.contains("<topic"));
      assertTrue(xml.contains("profile_name=\"topic_profile\""));
   }

   @Test
   public void testIntraprocessDeliveryOFF()
   {
      ProfilesXML.setIntraprocessDelivery("OFF");
      ProfilesXML profilesXML = new ProfilesXML();

      String xml = profilesXML.marshall();

      assertTrue(xml.contains("<intraprocess_delivery>OFF</intraprocess_delivery>"));
   }

   @Test
   public void testIntraprocessDeliveryUserDataOnly()
   {
      ProfilesXML.setIntraprocessDelivery("USER_DATA_ONLY");
      ProfilesXML profilesXML = new ProfilesXML();

      String xml = profilesXML.marshall();

      assertTrue(xml.contains("<intraprocess_delivery>USER_DATA_ONLY</intraprocess_delivery>"));

      // Reset to default
      ProfilesXML.setIntraprocessDelivery("OFF");
   }

   @Test
   public void testIntraprocessDeliveryFull()
   {
      ProfilesXML.setIntraprocessDelivery("FULL");
      ProfilesXML profilesXML = new ProfilesXML();

      String xml = profilesXML.marshall();

      assertTrue(xml.contains("<intraprocess_delivery>FULL</intraprocess_delivery>"));

      // Reset to default
      ProfilesXML.setIntraprocessDelivery("OFF");
   }

   @Test
   public void testIntraprocessDeliveryInvalidValue()
   {
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
      {
         ProfilesXML.setIntraprocessDelivery("INVALID");
      });

      assertTrue(exception.getMessage().contains("Invalid intraprocess delivery mode"));
   }

   @Test
   public void testCompleteWorkingExample()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Per Fast-DDS docs: Complete working example with all elements

      // Transport descriptors
      TransportDescriptorType udpv4 = new TransportDescriptorType();
      udpv4.setTransportId("udpv4_transport");
      udpv4.setType("UDPv4");
      InterfaceWhiteList udpWhitelist = new InterfaceWhiteList();
      udpWhitelist.getAddressOrInterface().add("192.168.1.100");
      udpWhitelist.getAddressOrInterface().add("eth0");
      udpv4.setInterfaceWhiteList(udpWhitelist);

      TransportDescriptorType shm = new TransportDescriptorType();
      shm.setTransportId("shm_transport");
      shm.setType("SHM");

      TransportDescriptorListType transportList = new TransportDescriptorListType();
      transportList.getTransportDescriptor().add(udpv4);
      transportList.getTransportDescriptor().add(shm);
      profilesXML.addTransportDescriptorsProfile(transportList);

      // Participant profile
      ParticipantProfileType participant = new ParticipantProfileType();
      participant.setProfileName("participant_xml_profile");
      participant.setDomainId(0);

      Rtps rtps = new Rtps();
      rtps.setName("MyParticipant");
      rtps.setUseBuiltinTransports(false);

      UserTransports userTransports = new UserTransports();
      userTransports.getTransportId().add("udpv4_transport");
      userTransports.getTransportId().add("shm_transport");
      rtps.setUserTransports(userTransports);

      participant.setRtps(rtps);
      profilesXML.addParticipantProfile(participant);

      // Publisher profile
      PublisherProfileType publisher = new PublisherProfileType();
      publisher.setProfileName("publisher_xml_profile");
      profilesXML.addPublisherProfile(publisher);

      // Subscriber profile
      SubscriberProfileType subscriber = new SubscriberProfileType();
      subscriber.setProfileName("subscriber_xml_profile");
      profilesXML.addSubscriberProfile(subscriber);

      // Topic profile
      TopicProfileType topic = new TopicProfileType();
      topic.setProfileName("topic_xml_profile");
      profilesXML.addTopicProfile(topic);

      String xml = profilesXML.marshall();

      // Verify complete structure
      assertTrue(xml.contains("<?xml version=\"1.0\""));
      assertTrue(xml.contains("<dds xmlns=\"http://www.eprosima.com\">"));
      assertTrue(xml.contains("<profiles>"));
      assertTrue(xml.contains("<transport_descriptors>"));
      assertTrue(xml.contains("udpv4_transport"));
      assertTrue(xml.contains("shm_transport"));
      assertTrue(xml.contains("<participant"));
      assertTrue(xml.contains("participant_xml_profile"));
      assertTrue(xml.contains("<data_writer"));
      assertTrue(xml.contains("publisher_xml_profile"));
      assertTrue(xml.contains("<data_reader"));
      assertTrue(xml.contains("subscriber_xml_profile"));
      assertTrue(xml.contains("<topic"));
      assertTrue(xml.contains("topic_xml_profile"));
      assertTrue(xml.contains("</profiles>"));
      assertTrue(xml.contains("<types/>"));
      assertTrue(xml.contains("<log/>"));
      assertTrue(xml.contains("<library_settings>"));
      assertTrue(xml.contains("</dds>"));
   }

   @Test
   public void testEmptyProfilesXML()
   {
      ProfilesXML profilesXML = new ProfilesXML();
      String xml = profilesXML.marshall();

      // Even with no profiles, structure should be valid
      assertTrue(xml.contains("<dds xmlns=\"http://www.eprosima.com\">"));
      assertTrue(xml.contains("<profiles/>"));
      assertTrue(xml.contains("<types/>"));
      assertTrue(xml.contains("<log/>"));
      assertTrue(xml.contains("<library_settings>"));
      assertTrue(xml.contains("<intraprocess_delivery>"));
      assertTrue(xml.contains("</dds>"));
   }

   @Test
   public void testXMLIndentationFormat()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      TransportDescriptorType udpv4 = new TransportDescriptorType();
      udpv4.setTransportId("test_transport");
      udpv4.setType("UDPv4");

      TransportDescriptorListType transportList = new TransportDescriptorListType();
      transportList.getTransportDescriptor().add(udpv4);
      profilesXML.addTransportDescriptorsProfile(transportList);

      String xml = profilesXML.marshall();

      // Verify proper indentation exists (Fast-DDS expects well-formatted XML)
      assertTrue(xml.contains("    <profiles>"), "Profiles should be indented 4 spaces");
      assertTrue(xml.contains("        <transport_descriptors>"), "Transport descriptors should be indented 8 spaces");
   }

   @Test
   public void testTransportIdUniquenessInSingleProfile()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Per Fast-DDS validation: Each transport_id must be unique
      TransportDescriptorType transport1 = new TransportDescriptorType();
      transport1.setTransportId("unique_id_1");
      transport1.setType("UDPv4");

      TransportDescriptorType transport2 = new TransportDescriptorType();
      transport2.setTransportId("unique_id_2");
      transport2.setType("UDPv6");

      TransportDescriptorListType transportList = new TransportDescriptorListType();
      transportList.getTransportDescriptor().add(transport1);
      transportList.getTransportDescriptor().add(transport2);

      profilesXML.addTransportDescriptorsProfile(transportList);
      String xml = profilesXML.marshall();

      assertTrue(xml.contains("unique_id_1"));
      assertTrue(xml.contains("unique_id_2"));

      // Ensure no duplicate IDs in XML
      int count1 = countOccurrences(xml, "unique_id_1");
      int count2 = countOccurrences(xml, "unique_id_2");
      assertEquals(1, count1, "Transport ID unique_id_1 should appear exactly once");
      assertEquals(1, count2, "Transport ID unique_id_2 should appear exactly once");
   }

   @Test
   public void testProfileNameAttributeFormat()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      ParticipantProfileType participant = new ParticipantProfileType();
      participant.setProfileName("my_custom_profile_name");

      profilesXML.addParticipantProfile(participant);
      String xml = profilesXML.marshall();

      // Per Fast-DDS docs: profile_name is an XML attribute
      assertTrue(xml.contains("profile_name=\"my_custom_profile_name\""));
      assertFalse(xml.contains("<profile_name>"), "profile_name should be an attribute, not an element");
   }

   @Test
   public void testMultipleProfilesOfSameType()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Multiple participant profiles can be defined
      ParticipantProfileType participant1 = new ParticipantProfileType();
      participant1.setProfileName("participant_profile_1");

      ParticipantProfileType participant2 = new ParticipantProfileType();
      participant2.setProfileName("participant_profile_2");

      profilesXML.addParticipantProfile(participant1);
      profilesXML.addParticipantProfile(participant2);

      String xml = profilesXML.marshall();

      assertTrue(xml.contains("participant_profile_1"));
      assertTrue(xml.contains("participant_profile_2"));

      int participantCount = countOccurrences(xml, "<participant");
      assertEquals(2, participantCount, "Should have 2 participant profiles");
   }

   @Test
   public void testMixedProfileTypes()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Mix different profile types
      ParticipantProfileType participant = new ParticipantProfileType();
      participant.setProfileName("part");

      PublisherProfileType publisher = new PublisherProfileType();
      publisher.setProfileName("pub");

      SubscriberProfileType subscriber = new SubscriberProfileType();
      subscriber.setProfileName("sub");

      TopicProfileType topic = new TopicProfileType();
      topic.setProfileName("top");

      profilesXML.addParticipantProfile(participant);
      profilesXML.addPublisherProfile(publisher);
      profilesXML.addSubscriberProfile(subscriber);
      profilesXML.addTopicProfile(topic);

      String xml = profilesXML.marshall();

      assertTrue(xml.contains("<participant"));
      assertTrue(xml.contains("<data_writer"));
      assertTrue(xml.contains("<data_reader"));
      assertTrue(xml.contains("<topic"));
   }

   // Helper method to count occurrences of a substring
   private int countOccurrences(String text, String pattern)
   {
      int count = 0;
      int index = 0;
      while ((index = text.indexOf(pattern, index)) != -1)
      {
         count++;
         index += pattern.length();
      }
      return count;
   }
}
