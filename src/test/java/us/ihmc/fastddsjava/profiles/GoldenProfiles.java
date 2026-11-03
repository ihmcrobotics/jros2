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

import us.ihmc.fastddsjava.profiles.gen.BuiltinAttributesType;
import us.ihmc.fastddsjava.profiles.gen.DataReaderQosPoliciesType;
import us.ihmc.fastddsjava.profiles.gen.DataWriterQosPoliciesType;
import us.ihmc.fastddsjava.profiles.gen.DeadlineQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.DurabilityQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.HistoryQosKindPolicyType;
import us.ihmc.fastddsjava.profiles.gen.HistoryQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.LifespanQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.LivelinessQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.LocatorListType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.PropertyPolicyType;
import us.ihmc.fastddsjava.profiles.gen.PropertyType;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.ReceptionThreadsListType;
import us.ihmc.fastddsjava.profiles.gen.ReliabilityQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;
import us.ihmc.fastddsjava.profiles.gen.ThreadSettingsType;
import us.ihmc.fastddsjava.profiles.gen.ThreadSettingsWithPortType;
import us.ihmc.fastddsjava.profiles.gen.TopicElementType;
import us.ihmc.fastddsjava.profiles.gen.TopicProfileType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorListType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;
import us.ihmc.fastddsjava.profiles.gen.Udpv4LocatorType;

import java.math.BigInteger;

/**
 * The research reference profile {@code /mnt/jros2-build/research/fastdds-xml/cases/Z_golden.xml} (validated against Fast-DDS
 * 3.6.2: parser rc=0, XSD-valid, every entity created, wire values observed by a Jazzy peer), and a builder that reproduces it
 * with the generated profile classes.
 */
final class GoldenProfiles
{
   /** Verbatim copy of {@code cases/Z_golden.xml}. */
   static final String Z_GOLDEN_XML = """
         <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
         <dds xmlns="http://www.eprosima.com">
             <profiles>
                 <transport_descriptors>
                     <transport_descriptor>
                         <transport_id>gold_udp</transport_id>
                         <type>UDPv4</type>
                         <maxInitialPeersRange>32</maxInitialPeersRange>
                         <default_reception_threads><scheduling_policy>0</scheduling_policy><priority>0</priority><affinity>0</affinity><stack_size>1048576</stack_size></default_reception_threads>
                         <reception_threads><reception_thread port="45150"><priority>0</priority></reception_thread></reception_threads>
                     </transport_descriptor>
                     <transport_descriptor>
                         <transport_id>gold_shm</transport_id>
                         <type>SHM</type>
                         <default_reception_threads><stack_size>1048576</stack_size></default_reception_threads>
                     </transport_descriptor>
                 </transport_descriptors>
                 <participant profile_name="p_gold">
                     <domainId>151</domainId>
                     <rtps>
                         <name>probe_gold</name>
                         <builtin>
                             <metatrafficUnicastLocatorList><locator><udpv4/></locator></metatrafficUnicastLocatorList>
                             <initialPeersList>
                                 <locator><udpv4><address>127.0.0.1</address></udpv4></locator>
                                 <locator><udpv4><address>localhost</address></udpv4></locator>
                             </initialPeersList>
                         </builtin>
                         <userTransports><transport_id>gold_udp</transport_id><transport_id>gold_shm</transport_id></userTransports>
                         <useBuiltinTransports>false</useBuiltinTransports>
                         <propertiesPolicy>
                             <properties>
                                 <property><name>fastdds.ignore_local_endpoints</name><value>false</value></property>
                             </properties>
                         </propertiesPolicy>
                         <userData><value>65.6e.63.6c.61.76.65.3d.2f.3b.00</value></userData>
                         <builtin_controllers_sender_thread><stack_size>1048576</stack_size></builtin_controllers_sender_thread>
                         <timed_events_thread><stack_size>1048576</stack_size></timed_events_thread>
                         <discovery_server_thread><stack_size>1048576</stack_size></discovery_server_thread>
                         <typelookup_service_thread><stack_size>1048576</stack_size></typelookup_service_thread>
                     </rtps>
                 </participant>
                 <topic profile_name="t_gold"/>
                 <data_writer profile_name="pub_gold">
                     <topic>
                         <historyQos><kind>KEEP_LAST</kind><depth>10</depth></historyQos>
                         <resourceLimitsQos><max_samples>5000</max_samples><max_instances>10</max_instances><max_samples_per_instance>500</max_samples_per_instance></resourceLimitsQos>
                     </topic>
                     <qos>
                         <durability><kind>TRANSIENT_LOCAL</kind></durability>
                         <deadline><period><sec>1</sec><nanosec>0</nanosec></period></deadline>
                         <lifespan><duration><sec>DURATION_INFINITY</sec></duration></lifespan>
                         <liveliness><kind>AUTOMATIC</kind><lease_duration><sec>DURATION_INFINITY</sec></lease_duration><announcement_period><sec>DURATION_INFINITY</sec></announcement_period></liveliness>
                         <reliability><kind>RELIABLE</kind><max_blocking_time><sec>0</sec><nanosec>100000000</nanosec></max_blocking_time></reliability>
                         <userData><value>74.79.70.65.68.61.73.68.3d.52.49.48.53.30.31.5f.64.66.36.36.38.63.37.34.30.34.38.32.62.62.64.34.38.66.62.33.39.64.37.36.61.37.30.64.66.64.34.62.64.35.39.64.62.31.32.38.38.30.32.31.37.34.33.35.30.33.32.35.39.65.39.34.38.66.36.62.31.61.31.38.3b</value></userData>
                     </qos>
                 </data_writer>
                 <data_reader profile_name="sub_gold">
                     <topic>
                         <historyQos><kind>KEEP_LAST</kind><depth>10</depth></historyQos>
                     </topic>
                     <qos>
                         <durability><kind>VOLATILE</kind></durability>
                         <deadline><period><sec>DURATION_INFINITY</sec></period></deadline>
                         <lifespan><duration><sec>DURATION_INFINITY</sec></duration></lifespan>
                         <liveliness><kind>AUTOMATIC</kind><lease_duration><sec>DURATION_INFINITY</sec></lease_duration></liveliness>
                         <reliability><kind>BEST_EFFORT</kind></reliability>
                         <userData><value>74.79.70.65.68.61.73.68.3d.52.49.48.53.30.31.5f.64.66.36.36.38.63.37.34.30.34.38.32.62.62.64.34.38.66.62.33.39.64.37.36.61.37.30.64.66.64.34.62.64.35.39.64.62.31.32.38.38.30.32.31.37.34.33.35.30.33.32.35.39.65.39.34.38.66.36.62.31.61.31.38.3b</value></userData>
                     </qos>
                 </data_reader>
             </profiles>
             <log/>
             <library_settings><intraprocess_delivery>OFF</intraprocess_delivery></library_settings>
         </dds>
         """;

   static final String TYPE_HASH_USER_DATA = "typehash=RIHS01_df668c740482bbd48fb39d76a70dfd4bd59db1288021743503259e948f6b1a18;";

   private GoldenProfiles()
   {
   }

   /**
    * Builds the golden profiles with the generated classes.
    *
    * @param suffix   appended to every profile name and transport ID (Fast-DDS keeps the first registration of a name)
    * @param domainId the participant domain
    */
   static ProfilesXML build(String suffix, int domainId)
   {
      ProfilesXML profilesXML = new ProfilesXML();

      TransportDescriptorType udp = new TransportDescriptorType();
      udp.setTransportId("gold_udp" + suffix);
      udp.setType("UDPv4");
      udp.setMaxInitialPeersRange(32L);
      ThreadSettingsType udpThreads = new ThreadSettingsType();
      udpThreads.setSchedulingPolicy(0);
      udpThreads.setPriority(0);
      udpThreads.setAffinity(BigInteger.ZERO);
      udpThreads.setStackSize(1048576);
      udp.setDefaultReceptionThreads(udpThreads);
      ReceptionThreadsListType receptionThreads = new ReceptionThreadsListType();
      ThreadSettingsWithPortType portThread = new ThreadSettingsWithPortType();
      portThread.setPort(45150);
      portThread.setPriority(0);
      receptionThreads.getReceptionThread().add(portThread);
      udp.setReceptionThreads(receptionThreads);

      TransportDescriptorType shm = new TransportDescriptorType();
      shm.setTransportId("gold_shm" + suffix);
      shm.setType("SHM");
      shm.setDefaultReceptionThreads(stackOnly());

      TransportDescriptorListType transports = new TransportDescriptorListType();
      transports.getTransportDescriptor().add(udp);
      transports.getTransportDescriptor().add(shm);
      profilesXML.addTransportDescriptorsProfile(transports);

      ParticipantProfileType participant = new ParticipantProfileType();
      participant.setProfileName("p_gold" + suffix);
      participant.setDomainId(domainId);
      ParticipantProfileType.Rtps rtps = new ParticipantProfileType.Rtps();
      rtps.setName("probe_gold");
      BuiltinAttributesType builtin = new BuiltinAttributesType();
      builtin.setMetatrafficUnicastLocatorList(locators((String) null));
      builtin.setInitialPeersList(locators("127.0.0.1", "localhost"));
      rtps.setBuiltin(builtin);
      ParticipantProfileType.Rtps.UserTransports userTransports = new ParticipantProfileType.Rtps.UserTransports();
      userTransports.getTransportId().add(udp.getTransportId());
      userTransports.getTransportId().add(shm.getTransportId());
      rtps.setUserTransports(userTransports);
      rtps.setUseBuiltinTransports(false);
      PropertyPolicyType propertyPolicy = new PropertyPolicyType();
      PropertyPolicyType.Properties properties = new PropertyPolicyType.Properties();
      PropertyType property = new PropertyType();
      property.setName("fastdds.ignore_local_endpoints");
      property.setValue("false");
      properties.getProperty().add(property);
      propertyPolicy.getPropertiesOrBinaryProperties().add(properties);
      rtps.setPropertiesPolicy(propertyPolicy);
      rtps.setUserData(OctetVectorTools.fromAscii("enclave=/;", true));
      rtps.setBuiltinControllersSenderThread(stackOnly());
      rtps.setTimedEventsThread(stackOnly());
      rtps.setDiscoveryServerThread(stackOnly());
      rtps.setTypelookupServiceThread(stackOnly());
      participant.setRtps(rtps);
      profilesXML.addParticipantProfile(participant);

      TopicProfileType topic = new TopicProfileType();
      topic.setProfileName("t_gold" + suffix);
      profilesXML.addTopicProfile(topic);

      PublisherProfileType writer = new PublisherProfileType();
      writer.setProfileName("pub_gold" + suffix);
      TopicElementType writerTopic = new TopicElementType();
      writerTopic.setHistoryQos(history(10));
      TopicElementType.ResourceLimitsQos resourceLimits = new TopicElementType.ResourceLimitsQos();
      resourceLimits.setMaxSamples(5000L);
      resourceLimits.setMaxInstances(10L);
      resourceLimits.setMaxSamplesPerInstance(500L);
      writerTopic.setResourceLimitsQos(resourceLimits);
      writer.setTopic(writerTopic);
      DataWriterQosPoliciesType writerQos = new DataWriterQosPoliciesType();
      writerQos.setDurability(durability("TRANSIENT_LOCAL"));
      DeadlineQosPolicyType deadline = new DeadlineQosPolicyType();
      deadline.setPeriod(DurationTypeTools.of(1, 0));
      writerQos.setDeadline(deadline);
      writerQos.setLifespan(infiniteLifespan());
      LivelinessQosPolicyType writerLiveliness = new LivelinessQosPolicyType();
      writerLiveliness.setKind("AUTOMATIC");
      writerLiveliness.setLeaseDuration(DurationTypeTools.infinite());
      writerLiveliness.setAnnouncementPeriod(DurationTypeTools.infinite());
      writerQos.setLiveliness(writerLiveliness);
      ReliabilityQosPolicyType writerReliability = new ReliabilityQosPolicyType();
      writerReliability.setKind("RELIABLE");
      writerReliability.setMaxBlockingTime(DurationTypeTools.of(0, 100_000_000));
      writerQos.setReliability(writerReliability);
      writerQos.setUserData(OctetVectorTools.fromAscii(TYPE_HASH_USER_DATA, false));
      writer.setQos(writerQos);
      profilesXML.addPublisherProfile(writer);

      SubscriberProfileType reader = new SubscriberProfileType();
      reader.setProfileName("sub_gold" + suffix);
      TopicElementType readerTopic = new TopicElementType();
      readerTopic.setHistoryQos(history(10));
      reader.setTopic(readerTopic);
      DataReaderQosPoliciesType readerQos = new DataReaderQosPoliciesType();
      readerQos.setDurability(durability("VOLATILE"));
      DeadlineQosPolicyType readerDeadline = new DeadlineQosPolicyType();
      readerDeadline.setPeriod(DurationTypeTools.infinite());
      readerQos.setDeadline(readerDeadline);
      readerQos.setLifespan(infiniteLifespan());
      LivelinessQosPolicyType readerLiveliness = new LivelinessQosPolicyType();
      readerLiveliness.setKind("AUTOMATIC");
      readerLiveliness.setLeaseDuration(DurationTypeTools.infinite());
      readerQos.setLiveliness(readerLiveliness);
      ReliabilityQosPolicyType readerReliability = new ReliabilityQosPolicyType();
      readerReliability.setKind("BEST_EFFORT");
      readerQos.setReliability(readerReliability);
      readerQos.setUserData(OctetVectorTools.fromAscii(TYPE_HASH_USER_DATA, false));
      reader.setQos(readerQos);
      profilesXML.addSubscriberProfile(reader);

      return profilesXML;
   }

   private static ThreadSettingsType stackOnly()
   {
      ThreadSettingsType threadSettings = new ThreadSettingsType();
      threadSettings.setStackSize(1048576);
      return threadSettings;
   }

   private static HistoryQosPolicyType history(long depth)
   {
      HistoryQosPolicyType history = new HistoryQosPolicyType();
      history.setKind(HistoryQosKindPolicyType.KEEP_LAST);
      history.setDepth(depth);
      return history;
   }

   private static DurabilityQosPolicyType durability(String kind)
   {
      DurabilityQosPolicyType durability = new DurabilityQosPolicyType();
      durability.setKind(kind);
      return durability;
   }

   private static LifespanQosPolicyType infiniteLifespan()
   {
      LifespanQosPolicyType lifespan = new LifespanQosPolicyType();
      lifespan.setDuration(DurationTypeTools.infinite());
      return lifespan;
   }

   static LocatorListType locators(String... addresses)
   {
      LocatorListType locatorList = new LocatorListType();
      for (String address : addresses)
      {
         LocatorListType.Locator locator = new LocatorListType.Locator();
         Udpv4LocatorType udpv4 = new Udpv4LocatorType();
         udpv4.setAddress(address);
         locator.getUdpv4().add(udpv4);
         locatorList.getLocator().add(locator);
      }
      return locatorList;
   }

   /**
    * @return the content between {@code <profiles>} and {@code </profiles>} with whitespace between tags removed and empty
    *       elements written as start and end tag
    */
   static String normalizedProfiles(String xml)
   {
      String profiles = xml.substring(xml.indexOf("<profiles>") + "<profiles>".length(), xml.indexOf("</profiles>"));
      String compact = profiles.replaceAll(">\\s+<", "><").trim();
      // <tag attr="x"/> -> <tag attr="x"></tag>
      return compact.replaceAll("<([A-Za-z_0-9]+)([^<>]*?)/>", "<$1$2></$1>");
   }
}
