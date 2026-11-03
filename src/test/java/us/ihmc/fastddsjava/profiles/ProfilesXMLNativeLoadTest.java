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
import org.junit.jupiter.api.Timeout;
import us.ihmc.fastddsjava.fastddsjavaException;
import us.ihmc.fastddsjava.natives.fastddsjava;
import us.ihmc.fastddsjava.profiles.gen.AllocationConfigType;
import us.ihmc.fastddsjava.profiles.gen.BuiltinAttributesType;
import us.ihmc.fastddsjava.profiles.gen.BuiltinTransportKind;
import us.ihmc.fastddsjava.profiles.gen.BuiltinTransportsType;
import us.ihmc.fastddsjava.profiles.gen.DataReaderQosPoliciesType;
import us.ihmc.fastddsjava.profiles.gen.DataSharingQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.DataWriterQosPoliciesType;
import us.ihmc.fastddsjava.profiles.gen.DeadlineQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.DurabilityQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.HistoryMemoryPolicyType;
import us.ihmc.fastddsjava.profiles.gen.HistoryQosKindPolicyType;
import us.ihmc.fastddsjava.profiles.gen.HistoryQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.LifespanQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.LivelinessQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.PartitionQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.PropertyPolicyType;
import us.ihmc.fastddsjava.profiles.gen.PropertyType;
import us.ihmc.fastddsjava.profiles.gen.PublishModeQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.ReliabilityQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.RtpsParticipantAllocationAttributesType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;
import us.ihmc.fastddsjava.profiles.gen.ThreadSettingsType;
import us.ihmc.fastddsjava.profiles.gen.TopicElementType;
import us.ihmc.fastddsjava.profiles.gen.TopicProfileType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every XML feature {@link ProfilesXMLWriter} emits, loaded through the bundled Fast-DDS natives
 * ({@code load_XML_profiles_string}) and used to create a participant, topic, data writer and data reader.
 * Creates DDS participants: run under the shared DDS lock in this workstream's domain.
 */
public class ProfilesXMLNativeLoadTest
{
   private static final int DOMAIN_ID = NativeProfileProbe.testDomainId();

   private static ParticipantProfileType participant(String name)
   {
      ParticipantProfileType participant = new ParticipantProfileType();
      participant.setProfileName(name);
      participant.setDomainId(DOMAIN_ID);
      ParticipantProfileType.Rtps rtps = new ParticipantProfileType.Rtps();
      rtps.setName("g2_load_test");
      participant.setRtps(rtps);
      return participant;
   }

   private static TopicProfileType topic(String name)
   {
      TopicProfileType topic = new TopicProfileType();
      topic.setProfileName(name);
      return topic;
   }

   @Test
   @Timeout(60)
   public void testGoldenXmlVerbatimLoadsAndCreatesEntities()
   {
      String suffix = NativeProfileProbe.uniqueSuffix();
      String xml = GoldenProfiles.Z_GOLDEN_XML.replace("<domainId>151</domainId>", "<domainId>" + DOMAIN_ID + "</domainId>")
                                              .replace("gold_udp", "gold_udp" + suffix)
                                              .replace("gold_shm", "gold_shm" + suffix)
                                              .replace("\"p_gold\"", "\"p_gold" + suffix + "\"")
                                              .replace("\"t_gold\"", "\"t_gold" + suffix + "\"")
                                              .replace("\"pub_gold\"", "\"pub_gold" + suffix + "\"")
                                              .replace("\"sub_gold\"", "\"sub_gold" + suffix + "\"");
      NativeProfileProbe.loadAndCreate(xml, "p_gold" + suffix, "t_gold" + suffix, "pub_gold" + suffix, "sub_gold" + suffix);
   }

   @Test
   @Timeout(60)
   public void testGoldenProfileGeneratedByWriterLoadsAndCreatesEntities()
   {
      String suffix = NativeProfileProbe.uniqueSuffix();
      String xml = GoldenProfiles.build(suffix, DOMAIN_ID).marshall();
      NativeProfileProbe.loadAndCreate(xml, "p_gold" + suffix, "t_gold" + suffix, "pub_gold" + suffix, "sub_gold" + suffix);
   }

   @Test
   @Timeout(60)
   public void testAllQoSElementsLoadAndCreateEntities()
   {
      String suffix = NativeProfileProbe.uniqueSuffix();
      ProfilesXML profilesXML = new ProfilesXML();
      profilesXML.addParticipantProfile(participant("p" + suffix));
      profilesXML.addTopicProfile(topic("t" + suffix));

      PublisherProfileType writer = new PublisherProfileType();
      writer.setProfileName("w" + suffix);
      writer.setHistoryMemoryPolicy(HistoryMemoryPolicyType.PREALLOCATED_WITH_REALLOC);
      TopicElementType writerTopic = new TopicElementType();
      HistoryQosPolicyType history = new HistoryQosPolicyType();
      history.setKind(HistoryQosKindPolicyType.KEEP_LAST);
      history.setDepth(7L);
      writerTopic.setHistoryQos(history);
      TopicElementType.ResourceLimitsQos resourceLimits = new TopicElementType.ResourceLimitsQos();
      resourceLimits.setMaxSamples(100L);
      resourceLimits.setMaxInstances(1L);
      resourceLimits.setMaxSamplesPerInstance(100L);
      resourceLimits.setAllocatedSamples(10L);
      resourceLimits.setExtraSamples(1L);
      writerTopic.setResourceLimitsQos(resourceLimits);
      writer.setTopic(writerTopic);
      DataWriterQosPoliciesType writerQos = new DataWriterQosPoliciesType();
      DurabilityQosPolicyType durability = new DurabilityQosPolicyType();
      durability.setKind("TRANSIENT_LOCAL");
      writerQos.setDurability(durability);
      DeadlineQosPolicyType deadline = new DeadlineQosPolicyType();
      deadline.setPeriod(DurationTypeTools.of(2, 500_000_000));
      writerQos.setDeadline(deadline);
      LifespanQosPolicyType lifespan = new LifespanQosPolicyType();
      lifespan.setDuration(DurationTypeTools.of(10, 0));
      writerQos.setLifespan(lifespan);
      LivelinessQosPolicyType liveliness = new LivelinessQosPolicyType();
      liveliness.setKind("MANUAL_BY_TOPIC");
      liveliness.setLeaseDuration(DurationTypeTools.of(3, 0));
      liveliness.setAnnouncementPeriod(DurationTypeTools.of(2, 0));
      writerQos.setLiveliness(liveliness);
      ReliabilityQosPolicyType reliability = new ReliabilityQosPolicyType();
      reliability.setKind("RELIABLE");
      // Infinite is clamped by the writer; Fast-DDS would refuse an infinite blocking time for RELIABLE
      reliability.setMaxBlockingTime(DurationTypeTools.infinite());
      writerQos.setReliability(reliability);
      PublishModeQosPolicyType publishMode = new PublishModeQosPolicyType();
      publishMode.setKind("SYNCHRONOUS");
      writerQos.setPublishMode(publishMode);
      DataSharingQosPolicyType dataSharing = new DataSharingQosPolicyType();
      dataSharing.setKind("OFF");
      writerQos.setDataSharing(dataSharing);
      PartitionQosPolicyType partition = new PartitionQosPolicyType();
      partition.setNames(new PartitionQosPolicyType.Names());
      partition.getNames().getName().add("g2");
      writerQos.setPartition(partition);
      writerQos.setUserData(OctetVectorTools.fromAscii(GoldenProfiles.TYPE_HASH_USER_DATA, false));
      writer.setQos(writerQos);
      profilesXML.addPublisherProfile(writer);

      SubscriberProfileType reader = new SubscriberProfileType();
      reader.setProfileName("r" + suffix);
      reader.setHistoryMemoryPolicy(HistoryMemoryPolicyType.PREALLOCATED_WITH_REALLOC);
      TopicElementType readerTopic = new TopicElementType();
      HistoryQosPolicyType readerHistory = new HistoryQosPolicyType();
      readerHistory.setKind(HistoryQosKindPolicyType.KEEP_ALL);
      readerTopic.setHistoryQos(readerHistory);
      reader.setTopic(readerTopic);
      DataReaderQosPoliciesType readerQos = new DataReaderQosPoliciesType();
      DurabilityQosPolicyType readerDurability = new DurabilityQosPolicyType();
      readerDurability.setKind("VOLATILE");
      readerQos.setDurability(readerDurability);
      DeadlineQosPolicyType readerDeadline = new DeadlineQosPolicyType();
      readerDeadline.setPeriod(DurationTypeTools.infinite());
      readerQos.setDeadline(readerDeadline);
      LivelinessQosPolicyType readerLiveliness = new LivelinessQosPolicyType();
      readerLiveliness.setKind("AUTOMATIC");
      readerLiveliness.setLeaseDuration(DurationTypeTools.infinite());
      readerLiveliness.setAnnouncementPeriod(DurationTypeTools.infinite());
      readerQos.setLiveliness(readerLiveliness);
      ReliabilityQosPolicyType readerReliability = new ReliabilityQosPolicyType();
      readerReliability.setKind("RELIABLE");
      readerReliability.setMaxBlockingTime(DurationTypeTools.of(0, 1));
      readerQos.setReliability(readerReliability);
      readerQos.setDataSharing(dataSharing);
      readerQos.setUserData(OctetVectorTools.fromAscii(GoldenProfiles.TYPE_HASH_USER_DATA, false));
      reader.setQos(readerQos);
      profilesXML.addSubscriberProfile(reader);

      NativeProfileProbe.loadAndCreate(profilesXML.marshall(), "p" + suffix, "t" + suffix, "w" + suffix, "r" + suffix);
   }

   @Test
   @Timeout(60)
   public void testHistoryWithoutKindAndEmptyQosLoad()
   {
      String suffix = NativeProfileProbe.uniqueSuffix();
      ProfilesXML profilesXML = new ProfilesXML();
      profilesXML.addParticipantProfile(participant("p" + suffix));
      profilesXML.addTopicProfile(topic("t" + suffix));
      PublisherProfileType writer = new PublisherProfileType();
      writer.setProfileName("w" + suffix);
      TopicElementType writerTopic = new TopicElementType();
      HistoryQosPolicyType depthOnly = new HistoryQosPolicyType();
      depthOnly.setDepth(42L);
      writerTopic.setHistoryQos(depthOnly);
      writer.setTopic(writerTopic);
      writer.setQos(new DataWriterQosPoliciesType());
      profilesXML.addPublisherProfile(writer);
      SubscriberProfileType reader = new SubscriberProfileType();
      reader.setProfileName("r" + suffix);
      ReliabilityQosPolicyType blockingOnly = new ReliabilityQosPolicyType();
      blockingOnly.setMaxBlockingTime(DurationTypeTools.of(1, 0));
      DataReaderQosPoliciesType readerQos = new DataReaderQosPoliciesType();
      readerQos.setReliability(blockingOnly);
      reader.setQos(readerQos);
      profilesXML.addSubscriberProfile(reader);
      NativeProfileProbe.loadAndCreate(profilesXML.marshall(), "p" + suffix, "t" + suffix, "w" + suffix, "r" + suffix);
   }

   @Test
   @Timeout(60)
   public void testParticipantDiscoveryAllocationPropertiesAndThreadsLoad()
   {
      String suffix = NativeProfileProbe.uniqueSuffix();
      ProfilesXML profilesXML = new ProfilesXML();
      ParticipantProfileType participant = participant("p" + suffix);
      ParticipantProfileType.Rtps rtps = participant.getRtps();
      BuiltinAttributesType builtin = new BuiltinAttributesType();
      builtin.setMetatrafficUnicastLocatorList(GoldenProfiles.locators((String) null));
      builtin.setInitialPeersList(GoldenProfiles.locators("127.0.0.1", "239.255.0.1"));
      builtin.setReaderHistoryMemoryPolicy(HistoryMemoryPolicyType.PREALLOCATED_WITH_REALLOC);
      builtin.setWriterHistoryMemoryPolicy(HistoryMemoryPolicyType.PREALLOCATED_WITH_REALLOC);
      rtps.setBuiltin(builtin);
      RtpsParticipantAllocationAttributesType allocation = new RtpsParticipantAllocationAttributesType();
      AllocationConfigType participants = new AllocationConfigType();
      participants.setInitial(1L);
      participants.setMaximum(1L);
      participants.setIncrement(0L);
      allocation.setTotalParticipants(participants);
      rtps.setAllocation(allocation);
      PropertyPolicyType policy = new PropertyPolicyType();
      PropertyPolicyType.Properties properties = new PropertyPolicyType.Properties();
      PropertyType property = new PropertyType();
      property.setName("fastdds.ignore_local_endpoints");
      property.setValue("false");
      properties.getProperty().add(property);
      policy.getPropertiesOrBinaryProperties().add(properties);
      rtps.setPropertiesPolicy(policy);
      rtps.setUserData(OctetVectorTools.fromAscii("enclave=/talker_listener/talker;", true));
      ThreadSettingsType threads = new ThreadSettingsType();
      threads.setSchedulingPolicy(0);
      threads.setPriority(0);
      threads.setAffinity(BigInteger.ONE);
      threads.setStackSize(1048576);
      rtps.setTimedEventsThread(threads);
      rtps.setBuiltinControllersSenderThread(threads);
      rtps.setTypelookupServiceThread(threads);
      rtps.setDiscoveryServerThread(threads);
      rtps.setUseBuiltinTransports(true);
      profilesXML.addParticipantProfile(participant);
      profilesXML.addTopicProfile(topic("t" + suffix));
      NativeProfileProbe.loadAndCreate(profilesXML.marshall(), "p" + suffix, "t" + suffix, null, null);
   }

   @Test
   @Timeout(60)
   public void testBuiltinTransportsWithReceptionThreadsLoad()
   {
      String suffix = NativeProfileProbe.uniqueSuffix();
      ProfilesXML profilesXML = new ProfilesXML();
      ParticipantProfileType participant = participant("p" + suffix);
      ThreadSettingsType reception = new ThreadSettingsType();
      reception.setSchedulingPolicy(0);
      reception.setPriority(0);
      reception.setStackSize(262144);
      participant.getRtps().setBuiltinTransportsReceptionThreads(reception);
      BuiltinTransportsType builtinTransports = new BuiltinTransportsType();
      builtinTransports.setValue(BuiltinTransportKind.DEFAULT);
      participant.getRtps().setBuiltinTransports(builtinTransports);
      participant.getRtps().setUseBuiltinTransports(true); // dropped by the writer because <builtinTransports> is set
      profilesXML.addParticipantProfile(participant);
      profilesXML.addTopicProfile(topic("t" + suffix));
      NativeProfileProbe.loadAndCreate(profilesXML.marshall(), "p" + suffix, "t" + suffix, null, null);
   }

   @Test
   @Timeout(60)
   public void testTransportDescriptorThreadsAndRegistrationLoad() throws fastddsjavaException
   {
      String suffix = NativeProfileProbe.uniqueSuffix();
      TransportDescriptorType udp = new TransportDescriptorType();
      udp.setTransportId("udp" + suffix);
      udp.setType("UDPv4");
      udp.setMaxInitialPeersRange(32L);
      ThreadSettingsType threads = new ThreadSettingsType();
      threads.setPriority(0);
      threads.setStackSize(1048576);
      udp.setDefaultReceptionThreads(threads);
      TransportDescriptorType shm = TransportDescriptorTypeTools.deriveVariant(TransportDescriptorTypeTools.createSHMDescriptor(), null, threads);

      // Registered in their own document first, like ParticipantProfiles does
      TransportDescriptorTypeTools.ensureRegistered(udp, shm);
      assertTrue(TransportDescriptorTypeTools.isRegistered(udp.getTransportId()));
      assertTrue(TransportDescriptorTypeTools.isRegistered(shm.getTransportId()));
      // Registering again is a no-op (no duplicate transport IDs reach Fast-DDS)
      TransportDescriptorTypeTools.ensureRegistered(udp, shm);

      ProfilesXML profilesXML = new ProfilesXML();
      ParticipantProfileType participant = participant("p" + suffix);
      participant.getRtps().setUseBuiltinTransports(false);
      ParticipantProfileType.Rtps.UserTransports userTransports = new ParticipantProfileType.Rtps.UserTransports();
      userTransports.getTransportId().add(shm.getTransportId());
      userTransports.getTransportId().add(udp.getTransportId());
      participant.getRtps().setUserTransports(userTransports);
      profilesXML.addParticipantProfile(participant);
      profilesXML.addTopicProfile(topic("t" + suffix));
      NativeProfileProbe.loadAndCreate(profilesXML.marshall(), "p" + suffix, "t" + suffix, null, null);
   }

   @Test
   @Timeout(60)
   public void testParserRejectionsThatTheWriterPrevents()
   {
      // Each of these is what a naive writer would emit; the writer never does (see ProfilesXMLWriterTest)
      String suffix = NativeProfileProbe.uniqueSuffix();
      String twoValues = "<dds xmlns=\"http://www.eprosima.com\"><profiles><participant profile_name=\"a" + suffix
                         + "\"><rtps><userData><value>65</value><value>6e</value></userData></rtps></participant></profiles></dds>";
      assertNotEquals(fastddsjava.RETCODE_OK, NativeProfileProbe.load(twoValues));
      String emptyDurability = "<dds xmlns=\"http://www.eprosima.com\"><profiles><data_writer profile_name=\"b" + suffix
                               + "\"><qos><durability/></qos></data_writer></profiles></dds>";
      assertNotEquals(fastddsjava.RETCODE_OK, NativeProfileProbe.load(emptyDurability));
      String trailingWhitespace = "<dds xmlns=\"http://www.eprosima.com\"><profiles><participant profile_name=\"c" + suffix
                                  + "\"><rtps><userData><value>65.6e </value></userData></rtps></participant></profiles></dds>";
      assertNotEquals(fastddsjava.RETCODE_OK, NativeProfileProbe.load(trailingWhitespace));
   }
}
