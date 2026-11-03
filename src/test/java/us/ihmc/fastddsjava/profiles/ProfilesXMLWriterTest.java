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
package us.ihmc.fastddsjava.profiles;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.profiles.gen.AllocationConfigType;
import us.ihmc.fastddsjava.profiles.gen.BuiltinAttributesType;
import us.ihmc.fastddsjava.profiles.gen.BuiltinTransportKind;
import us.ihmc.fastddsjava.profiles.gen.BuiltinTransportsType;
import us.ihmc.fastddsjava.profiles.gen.DataSharingQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.DataWriterQosPoliciesType;
import us.ihmc.fastddsjava.profiles.gen.DeadlineQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.DurabilityQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.DurationType;
import us.ihmc.fastddsjava.profiles.gen.HistoryMemoryPolicyType;
import us.ihmc.fastddsjava.profiles.gen.HistoryQosKindPolicyType;
import us.ihmc.fastddsjava.profiles.gen.HistoryQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.LocatorListType;
import us.ihmc.fastddsjava.profiles.gen.OctectVectorQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.PartitionQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.PropertyPolicyType;
import us.ihmc.fastddsjava.profiles.gen.PropertyType;
import us.ihmc.fastddsjava.profiles.gen.PublishModeQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.ReliabilityQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.RtpsParticipantAllocationAttributesType;
import us.ihmc.fastddsjava.profiles.gen.ThreadSettingsType;
import us.ihmc.fastddsjava.profiles.gen.TopicElementType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;
import us.ihmc.fastddsjava.profiles.gen.Udpv6LocatorType;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XML-level tests of {@link ProfilesXMLWriter}: every element jros2 emits, the parser ordering rules, and the research golden
 * profile. The same XML is load-tested through the natives in {@link ProfilesXMLNativeLoadTest}.
 */
public class ProfilesXMLWriterTest
{
   private static String participantXml(ParticipantProfileType participant)
   {
      StringBuilder xml = new StringBuilder();
      ProfilesXMLWriter.writeParticipant(xml, "participant", participant);
      return xml.toString();
   }

   private static String writerXml(PublisherProfileType publisher)
   {
      StringBuilder xml = new StringBuilder();
      ProfilesXMLWriter.writeProfile(xml, "data_writer", publisher);
      return xml.toString();
   }

   private static void assertContains(String xml, String expected)
   {
      assertTrue(xml.contains(expected), () -> "Expected '" + expected + "' in\n" + xml);
   }

   private static void assertBefore(String xml, String first, String second)
   {
      int a = xml.indexOf(first);
      int b = xml.indexOf(second);
      assertTrue(a >= 0 && b >= 0 && a < b, () -> "Expected '" + first + "' before '" + second + "' in\n" + xml);
   }

   @Test
   public void testGoldenProfileIsReproducedExactly()
   {
      String generated = GoldenProfiles.build("", 151).marshall();
      assertEquals(GoldenProfiles.normalizedProfiles(GoldenProfiles.Z_GOLDEN_XML), GoldenProfiles.normalizedProfiles(generated));
   }

   @Test
   public void testParticipantUserDataEnclave()
   {
      ParticipantProfileType participant = new ParticipantProfileType();
      participant.setProfileName("p");
      ParticipantProfileType.Rtps rtps = new ParticipantProfileType.Rtps();
      rtps.setUserData(OctetVectorTools.fromAscii("enclave=/;", true));
      participant.setRtps(rtps);
      // Byte-identical to a Jazzy rmw_fastrtps participant (11 bytes including the NUL)
      assertContains(participantXml(participant), "<userData><value>65.6e.63.6c.61.76.65.3d.2f.3b.00</value></userData>");

      rtps.setUserData(OctetVectorTools.fromAscii("enclave=/talker_listener/talker;", true));
      String xml = participantXml(participant);
      String value = xml.substring(xml.indexOf("<value>") + 7, xml.indexOf("</value>"));
      byte[] bytes = OctetVectorTools.parseOctetString(value);
      assertEquals(0, bytes[bytes.length - 1]);
      assertEquals("enclave=/talker_listener/talker;", new String(bytes, 0, bytes.length - 1, StandardCharsets.US_ASCII));
   }

   @Test
   public void testOctetVectorIsSingleValueWithoutWhitespace()
   {
      OctectVectorQosPolicyType userData = new OctectVectorQosPolicyType();
      userData.getValue().add("61.62");
      userData.getValue().add("63");
      DataWriterQosPoliciesType qos = new DataWriterQosPoliciesType();
      qos.setUserData(userData);
      PublisherProfileType publisher = new PublisherProfileType();
      publisher.setProfileName("w");
      publisher.setQos(qos);
      String xml = writerXml(publisher);
      // Fast-DDS accepts exactly one <value>
      assertContains(xml, "<userData><value>61.62.63</value></userData>");
      assertEquals(1, xml.split("<value>", -1).length - 1);

      userData.getValue().clear();
      userData.getValue().add("61. 62");
      assertThrows(IllegalArgumentException.class, () -> writerXml(publisher));
      userData.getValue().clear();
      userData.getValue().add("zz");
      assertThrows(IllegalArgumentException.class, () -> writerXml(publisher));
      assertThrows(IllegalArgumentException.class, () -> OctetVectorTools.fromBytes(new byte[0]));
      assertThrows(IllegalArgumentException.class, () -> OctetVectorTools.fromAscii("café", false));
      assertEquals("00.7f.80.ff", OctetVectorTools.toOctetString(new byte[] {0, 0x7f, (byte) 0x80, (byte) 0xff}));
   }

   @Test
   public void testDurationsAreNormalizedAndNamed()
   {
      // Legacy [nanosec, sec] list convention with nanoseconds above one second: carried into the seconds
      DurationType raw = new DurationType();
      raw.getSecOrNanosec().add("1500000000");
      raw.getSecOrNanosec().add("2");
      assertArrayEquals(new long[] {3, 500_000_000}, DurationTypeTools.normalize(raw));

      DeadlineQosPolicyType deadline = new DeadlineQosPolicyType();
      deadline.setPeriod(raw);
      DataWriterQosPoliciesType qos = new DataWriterQosPoliciesType();
      qos.setDeadline(deadline);
      PublisherProfileType publisher = new PublisherProfileType();
      publisher.setProfileName("w");
      publisher.setQos(qos);
      assertContains(writerXml(publisher), "<deadline><period><sec>3</sec><nanosec>500000000</nanosec></period></deadline>");

      // A value that Fast-DDS would wrap into a negative int32 is infinite
      DurationType huge = new DurationType();
      huge.getSecOrNanosec().add("4294967295");
      deadline.setPeriod(huge);
      assertContains(writerXml(publisher), "<deadline><period><sec>DURATION_INFINITY</sec></period></deadline>");
      assertTrue(DurationTypeTools.isInfinite(huge));

      // Seconds-only list and every Fast-DDS infinity literal
      DurationType secondsOnly = new DurationType();
      secondsOnly.getSecOrNanosec().add("7");
      assertArrayEquals(new long[] {7, 0}, DurationTypeTools.normalize(secondsOnly));
      for (String literal : new String[] {"DURATION_INFINITY", "DURATION_INFINITE_SEC", "DURATION_INFINITE_NSEC"})
      {
         DurationType infinite = new DurationType();
         infinite.getSecOrNanosec().add(literal);
         assertNull(DurationTypeTools.normalize(infinite));
      }

      // Exactly 2^31-1 seconds is infinite for Fast-DDS (Time_t::is_infinite), one less is the largest finite value
      assertTrue(DurationTypeTools.isInfinite(DurationTypeTools.of(Integer.MAX_VALUE, 0)));
      assertTrue(DurationTypeTools.isInfinite(DurationTypeTools.of(Integer.MAX_VALUE - 1L, 1_000_000_000L)));
      assertFalse(DurationTypeTools.isInfinite(DurationTypeTools.maxFinite()));
      assertArrayEquals(new long[] {2147483646L, 999_999_999L}, DurationTypeTools.normalize(DurationTypeTools.maxFinite()));
      assertFalse(DurationTypeTools.isInfinite(null));

      // Malformed values
      assertThrows(IllegalArgumentException.class, () -> DurationTypeTools.of(-1, 0));
      assertThrows(IllegalArgumentException.class, () -> DurationTypeTools.of(0, -1));
      DurationType negative = new DurationType();
      negative.getSecOrNanosec().add("0");
      negative.getSecOrNanosec().add("-1");
      assertThrows(IllegalArgumentException.class, () -> DurationTypeTools.normalize(negative));
      DurationType text = new DurationType();
      text.getSecOrNanosec().add("forever");
      assertThrows(IllegalArgumentException.class, () -> DurationTypeTools.normalize(text));
      assertThrows(IllegalArgumentException.class, () -> DurationTypeTools.normalize(new DurationType()));
   }

   @Test
   public void testMaxBlockingTimeIsNeverInfinite()
   {
      ReliabilityQosPolicyType reliability = new ReliabilityQosPolicyType();
      reliability.setKind("RELIABLE");
      reliability.setMaxBlockingTime(DurationTypeTools.infinite());
      DataWriterQosPoliciesType qos = new DataWriterQosPoliciesType();
      qos.setReliability(reliability);
      PublisherProfileType publisher = new PublisherProfileType();
      publisher.setProfileName("w");
      publisher.setQos(qos);
      String xml = writerXml(publisher);
      assertContains(xml, "<max_blocking_time><sec>2147483646</sec><nanosec>999999999</nanosec></max_blocking_time>");
      assertFalse(xml.contains("DURATION_INFINITY"));
   }

   @Test
   public void testEndpointSettings()
   {
      PublisherProfileType publisher = new PublisherProfileType();
      publisher.setProfileName("w");
      publisher.setHistoryMemoryPolicy(HistoryMemoryPolicyType.PREALLOCATED_WITH_REALLOC);
      DataWriterQosPoliciesType qos = new DataWriterQosPoliciesType();
      PublishModeQosPolicyType publishMode = new PublishModeQosPolicyType();
      publishMode.setKind("SYNCHRONOUS");
      qos.setPublishMode(publishMode);
      DataSharingQosPolicyType dataSharing = new DataSharingQosPolicyType();
      dataSharing.setKind("OFF");
      qos.setDataSharing(dataSharing);
      PartitionQosPolicyType partition = new PartitionQosPolicyType();
      partition.setNames(new PartitionQosPolicyType.Names());
      partition.getNames().getName().add("part_a");
      qos.setPartition(partition);
      publisher.setQos(qos);
      String xml = writerXml(publisher);
      assertContains(xml, "<publishMode><kind>SYNCHRONOUS</kind></publishMode>");
      assertContains(xml, "<data_sharing><kind>OFF</kind></data_sharing>");
      assertContains(xml, "<partition><names><name>part_a</name></names></partition>");
      assertContains(xml, "<historyMemoryPolicy>PREALLOCATED_WITH_REALLOC</historyMemoryPolicy>");

      // <data_sharing> without <kind> is rejected by the parser
      dataSharing.setKind(null);
      assertThrows(IllegalArgumentException.class, () -> writerXml(publisher));
   }

   @Test
   public void testHistoryValidation()
   {
      PublisherProfileType publisher = new PublisherProfileType();
      publisher.setProfileName("w");
      TopicElementType topic = new TopicElementType();
      HistoryQosPolicyType history = new HistoryQosPolicyType();
      history.setKind(HistoryQosKindPolicyType.KEEP_LAST);
      history.setDepth(7L);
      topic.setHistoryQos(history);
      TopicElementType.ResourceLimitsQos resourceLimits = new TopicElementType.ResourceLimitsQos();
      resourceLimits.setMaxSamples(100L);
      resourceLimits.setMaxInstances(1L);
      resourceLimits.setMaxSamplesPerInstance(100L);
      resourceLimits.setAllocatedSamples(10L);
      resourceLimits.setExtraSamples(1L);
      topic.setResourceLimitsQos(resourceLimits);
      publisher.setTopic(topic);
      assertContains(writerXml(publisher),
                     "<topic><historyQos><kind>KEEP_LAST</kind><depth>7</depth></historyQos><resourceLimitsQos><max_samples>100</max_samples>"
                     + "<max_instances>1</max_instances><max_samples_per_instance>100</max_samples_per_instance><allocated_samples>10</allocated_samples>"
                     + "<extra_samples>1</extra_samples></resourceLimitsQos></topic>");

      // KEEP_LAST depth must not exceed max_samples_per_instance (Fast-DDS returns a NULL writer)
      resourceLimits.setMaxSamplesPerInstance(5L);
      assertThrows(IllegalArgumentException.class, () -> writerXml(publisher));
      // KEEP_ALL is not affected; unlimited (-1 or 0) is not checked
      history.setKind(HistoryQosKindPolicyType.KEEP_ALL);
      assertDoesNotThrow(() -> writerXml(publisher));
      history.setKind(HistoryQosKindPolicyType.KEEP_LAST);
      resourceLimits.setMaxSamplesPerInstance(-1L);
      assertDoesNotThrow(() -> writerXml(publisher));

      // KEEP_LAST depth 0 is rejected by Fast-DDS
      topic.setResourceLimitsQos(null);
      history.setDepth(0L);
      assertThrows(IllegalArgumentException.class, () -> writerXml(publisher));
   }

   @Test
   public void testElementsWithoutContentAreOmitted()
   {
      DataWriterQosPoliciesType qos = new DataWriterQosPoliciesType();
      qos.setDurability(new DurabilityQosPolicyType());
      qos.setDeadline(new DeadlineQosPolicyType());
      PublisherProfileType publisher = new PublisherProfileType();
      publisher.setProfileName("w");
      publisher.setQos(qos);
      publisher.setTopic(new TopicElementType());
      String xml = writerXml(publisher);
      // <durability/> and <deadline/> without content make the parser fail
      assertEquals("<data_writer profile_name=\"w\"><qos></qos></data_writer>", xml);
   }

   @Test
   public void testBuiltinDiscoveryAndAllocation()
   {
      ParticipantProfileType participant = new ParticipantProfileType();
      participant.setProfileName("p");
      participant.setDomainId(5);
      ParticipantProfileType.Rtps rtps = new ParticipantProfileType.Rtps();
      BuiltinAttributesType builtin = new BuiltinAttributesType();
      builtin.setMetatrafficUnicastLocatorList(GoldenProfiles.locators((String) null));
      builtin.setInitialPeersList(GoldenProfiles.locators("10.0.0.5", "127.0.0.1"));
      builtin.setReaderHistoryMemoryPolicy(HistoryMemoryPolicyType.PREALLOCATED_WITH_REALLOC);
      builtin.setWriterHistoryMemoryPolicy(HistoryMemoryPolicyType.PREALLOCATED_WITH_REALLOC);
      builtin.setAvoidBuiltinMulticast(true);
      rtps.setBuiltin(builtin);
      RtpsParticipantAllocationAttributesType allocation = new RtpsParticipantAllocationAttributesType();
      AllocationConfigType participants = new AllocationConfigType();
      participants.setInitial(1L);
      participants.setMaximum(1L);
      participants.setIncrement(0L);
      allocation.setTotalParticipants(participants);
      RtpsParticipantAllocationAttributesType.SendBuffers sendBuffers = new RtpsParticipantAllocationAttributesType.SendBuffers();
      sendBuffers.setPreallocatedNumber(4L);
      sendBuffers.setDynamic(false);
      AllocationConfigType networkBuffers = new AllocationConfigType();
      networkBuffers.setInitial(80L);
      networkBuffers.setIncrement(16L);
      sendBuffers.setNetworkBuffersConfig(networkBuffers);
      allocation.setSendBuffers(sendBuffers);
      allocation.setMaxUserData(256L);
      rtps.setAllocation(allocation);
      participant.setRtps(rtps);

      String xml = participantXml(participant);
      assertContains(xml,
                     "<builtin><avoid_builtin_multicast>true</avoid_builtin_multicast>"
                     + "<metatrafficUnicastLocatorList><locator><udpv4/></locator></metatrafficUnicastLocatorList>"
                     + "<initialPeersList><locator><udpv4><address>10.0.0.5</address></udpv4></locator><locator><udpv4><address>127.0.0.1</address></udpv4></locator></initialPeersList>"
                     + "<readerHistoryMemoryPolicy>PREALLOCATED_WITH_REALLOC</readerHistoryMemoryPolicy>"
                     + "<writerHistoryMemoryPolicy>PREALLOCATED_WITH_REALLOC</writerHistoryMemoryPolicy></builtin>");
      assertContains(xml,
                     "<allocation><total_participants><initial>1</initial><maximum>1</maximum><increment>0</increment></total_participants>"
                     + "<send_buffers><preallocated_number>4</preallocated_number><dynamic>false</dynamic>"
                     + "<network_buffers_config><initial>80</initial><increment>16</increment></network_buffers_config></send_buffers>"
                     + "<max_user_data>256</max_user_data></allocation>");

      // increment 0 requires initial == maximum
      participants.setMaximum(2L);
      assertThrows(IllegalArgumentException.class, () -> participantXml(participant));
      participants.setMaximum(1L);

      // An empty locator list must not be written (Fast-DDS: "without content")
      builtin.setInitialPeersList(new LocatorListType());
      assertFalse(participantXml(participant).contains("initialPeersList"));

      // UDPv6 locators with a port; a locator with two children is rejected
      LocatorListType v6 = new LocatorListType();
      LocatorListType.Locator locator = new LocatorListType.Locator();
      Udpv6LocatorType udpv6 = new Udpv6LocatorType();
      udpv6.setAddress("::1");
      udpv6.setPort(7412);
      locator.getUdpv6().add(udpv6);
      v6.getLocator().add(locator);
      builtin.setInitialPeersList(v6);
      assertContains(participantXml(participant), "<initialPeersList><locator><udpv6><address>::1</address><port>7412</port></udpv6></locator></initialPeersList>");
      locator.getUdpv4().add(new us.ihmc.fastddsjava.profiles.gen.Udpv4LocatorType());
      assertThrows(IllegalArgumentException.class, () -> participantXml(participant));
      locator.getUdpv4().clear();

      // Builtin elements whose generated classes lost their element names are refused rather than silently dropped
      builtin.setDiscoveryConfig(new us.ihmc.fastddsjava.profiles.gen.DiscoverySettingsType());
      IllegalArgumentException discoveryConfig = assertThrows(IllegalArgumentException.class, () -> participantXml(participant));
      assertTrue(discoveryConfig.getMessage().contains("discovery_config"), discoveryConfig.getMessage());
      builtin.setDiscoveryConfig(null);
      builtin.setMetatrafficExternalUnicastLocators(new us.ihmc.fastddsjava.profiles.gen.ExternalLocatorListType());
      assertThrows(IllegalArgumentException.class, () -> participantXml(participant));
      builtin.setMetatrafficExternalUnicastLocators(null);
      assertContains(participantXml(participant), "<builtin>");
   }

   @Test
   public void testPropertiesPolicy()
   {
      ParticipantProfileType participant = new ParticipantProfileType();
      participant.setProfileName("p");
      ParticipantProfileType.Rtps rtps = new ParticipantProfileType.Rtps();
      PropertyPolicyType policy = new PropertyPolicyType();
      PropertyPolicyType.Properties properties = new PropertyPolicyType.Properties();
      PropertyType property = new PropertyType();
      property.setName("dds.sec.auth.plugin");
      property.setValue("builtin.PKI-DH");
      properties.getProperty().add(property);
      PropertyType withPropagate = new PropertyType();
      withPropagate.setName("dds.sec.auth.builtin.PKI-DH.identity_ca");
      withPropagate.setValue("file:///a&b/<c>/identity_ca.cert.pem");
      withPropagate.setPropagate(false);
      properties.getProperty().add(withPropagate);
      policy.getPropertiesOrBinaryProperties().add(properties);
      rtps.setPropertiesPolicy(policy);
      participant.setRtps(rtps);

      String xml = participantXml(participant);
      assertContains(xml,
                     "<propertiesPolicy><properties><property><name>dds.sec.auth.plugin</name><value>builtin.PKI-DH</value></property>"
                     + "<property><name>dds.sec.auth.builtin.PKI-DH.identity_ca</name><value>file:///a&amp;b/&lt;c&gt;/identity_ca.cert.pem</value>"
                     + "<propagate>false</propagate></property></properties></propertiesPolicy>");

      // Empty names or values are rejected by the parser
      withPropagate.setValue("");
      assertThrows(IllegalArgumentException.class, () -> participantXml(participant));

      // An empty <properties> is skipped (the parser requires at least one <property>)
      properties.getProperty().clear();
      assertFalse(participantXml(participant).contains("propertiesPolicy"));
   }

   @Test
   public void testEnvironmentVariableSyntaxIsRejected()
   {
      ParticipantProfileType participant = new ParticipantProfileType();
      participant.setProfileName("p");
      ParticipantProfileType.Rtps rtps = new ParticipantProfileType.Rtps();
      rtps.setName("node_${HOME}");
      participant.setRtps(rtps);
      // Fast-DDS would substitute ${HOME}
      assertThrows(IllegalArgumentException.class, () -> participantXml(participant));

      // Text that is not a variable reference is fine
      rtps.setName("cost_$5_{x}_${lowercase}");
      assertContains(participantXml(participant), "<name>cost_$5_{x}_${lowercase}</name>");
   }

   @Test
   public void testThreadSettingsAndBuiltinTransportsOrdering()
   {
      ParticipantProfileType participant = new ParticipantProfileType();
      participant.setProfileName("p");
      ParticipantProfileType.Rtps rtps = new ParticipantProfileType.Rtps();
      rtps.setName("n");
      rtps.setSendSocketBufferSize(1048576L);
      rtps.setBuiltin(new BuiltinAttributesType());
      rtps.getBuiltin().setReaderHistoryMemoryPolicy(HistoryMemoryPolicyType.PREALLOCATED_WITH_REALLOC);
      ThreadSettingsType reception = new ThreadSettingsType();
      reception.setSchedulingPolicy(0);
      reception.setPriority(-2);
      reception.setAffinity(new BigInteger("18446744073709551615"));
      reception.setStackSize(262144);
      rtps.setBuiltinTransportsReceptionThreads(reception);
      BuiltinTransportsType builtinTransports = new BuiltinTransportsType();
      builtinTransports.setValue(BuiltinTransportKind.UD_PV_4);
      builtinTransports.setMaxMsgSize("1MB");
      builtinTransports.setNonBlocking(true);
      rtps.setBuiltinTransports(builtinTransports);
      rtps.setUseBuiltinTransports(true); // must not be written together with <builtinTransports>
      ThreadSettingsType events = new ThreadSettingsType();
      events.setPriority(5);
      rtps.setTimedEventsThread(events);
      rtps.setSecurityLogThread(new ThreadSettingsType()); // nothing set: not written
      participant.setRtps(rtps);

      String xml = participantXml(participant);
      assertContains(xml,
                     "<builtin_transports_reception_threads><scheduling_policy>0</scheduling_policy><priority>-2</priority>"
                     + "<affinity>18446744073709551615</affinity><stack_size>262144</stack_size></builtin_transports_reception_threads>");
      // value(), not the mangled enum constant name
      assertContains(xml, "<builtinTransports max_msg_size=\"1MB\" non_blocking=\"true\">UDPv4</builtinTransports>");
      assertBefore(xml, "<sendSocketBufferSize>", "<builtinTransports");
      assertBefore(xml, "<builtin>", "<builtinTransports");
      assertBefore(xml, "<builtin_transports_reception_threads>", "<builtinTransports");
      assertFalse(xml.contains("useBuiltinTransports"), xml);
      assertContains(xml, "<timed_events_thread><priority>5</priority></timed_events_thread>");
      assertFalse(xml.contains("security_log_thread"), xml);

      // Values the parser rejects
      ThreadSettingsType badPolicy = new ThreadSettingsType();
      badPolicy.setSchedulingPolicy(-2);
      rtps.setTimedEventsThread(badPolicy);
      assertThrows(IllegalArgumentException.class, () -> participantXml(participant));
      ThreadSettingsType badStack = new ThreadSettingsType();
      badStack.setStackSize(-2);
      rtps.setTimedEventsThread(badStack);
      assertThrows(IllegalArgumentException.class, () -> participantXml(participant));
      // Stack sizes pthread refuses make Fast-DDS abort the JVM: below glibc aarch64's PTHREAD_STACK_MIN, or not page aligned (macOS)
      for (int abortsTheJvm : new int[] {0, 1000, 16384, 131071, 131072 + 4096, 1000000})
      {
         ThreadSettingsType stack = new ThreadSettingsType();
         stack.setStackSize(abortsTheJvm);
         rtps.setTimedEventsThread(stack);
         assertThrows(IllegalArgumentException.class, () -> participantXml(participant), Integer.toString(abortsTheJvm));
      }
      // The writer enforces the same rule as the public API
      assertEquals(us.ihmc.jros2.ROS2ThreadSettings.MIN_STACK_SIZE_BYTES, ProfilesXMLWriter.MIN_STACK_SIZE_BYTES);
      assertEquals(us.ihmc.jros2.ROS2ThreadSettings.STACK_SIZE_ALIGNMENT_BYTES, ProfilesXMLWriter.STACK_SIZE_ALIGNMENT_BYTES);
      for (int accepted : new int[] {-1, ProfilesXMLWriter.MIN_STACK_SIZE_BYTES, 1048576})
      {
         ThreadSettingsType stack = new ThreadSettingsType();
         stack.setStackSize(accepted);
         rtps.setTimedEventsThread(stack);
         assertContains(participantXml(participant), "<timed_events_thread><stack_size>" + accepted + "</stack_size></timed_events_thread>");
      }

      // A kind is required
      rtps.setTimedEventsThread(null);
      builtinTransports.setValue(null);
      assertThrows(IllegalArgumentException.class, () -> participantXml(participant));
   }

   @Test
   public void testTransportDescriptorThreads()
   {
      TransportDescriptorType descriptor = new TransportDescriptorType();
      descriptor.setTransportId("t");
      descriptor.setType("UDPv4");
      descriptor.setMaxInitialPeersRange(32L);
      ThreadSettingsType threads = new ThreadSettingsType();
      threads.setAffinity(BigInteger.valueOf(256));
      descriptor.setDefaultReceptionThreads(threads);
      ThreadSettingsType dump = new ThreadSettingsType();
      dump.setStackSize(-1);
      descriptor.setDumpThread(dump);
      StringBuilder xml = new StringBuilder();
      ProfilesXMLWriter.writeTransportDescriptor(xml, descriptor);
      assertEquals("<transport_descriptor><transport_id>t</transport_id><type>UDPv4</type><maxInitialPeersRange>32</maxInitialPeersRange>"
                   + "<default_reception_threads><affinity>256</affinity></default_reception_threads>"
                   + "<dump_thread><stack_size>-1</stack_size></dump_thread></transport_descriptor>", xml.toString());
   }
}
