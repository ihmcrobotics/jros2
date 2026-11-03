/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.fastddsjava.profiles;

import us.ihmc.fastddsjava.profiles.gen.AllocationConfigType;
import us.ihmc.fastddsjava.profiles.gen.BuiltinAttributesType;
import us.ihmc.fastddsjava.profiles.gen.BuiltinTransportsType;
import us.ihmc.fastddsjava.profiles.gen.DataReaderQosPoliciesType;
import us.ihmc.fastddsjava.profiles.gen.DataSharingQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.DataWriterQosPoliciesType;
import us.ihmc.fastddsjava.profiles.gen.DeadlineQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.DurabilityQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.DurationType;
import us.ihmc.fastddsjava.profiles.gen.HistoryMemoryPolicyType;
import us.ihmc.fastddsjava.profiles.gen.HistoryQosKindPolicyType;
import us.ihmc.fastddsjava.profiles.gen.HistoryQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.LifespanQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.LivelinessQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.LocatorListType;
import us.ihmc.fastddsjava.profiles.gen.OctectVectorQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.PartitionQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.PropertyPolicyType;
import us.ihmc.fastddsjava.profiles.gen.PropertyType;
import us.ihmc.fastddsjava.profiles.gen.PublishModeQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.ReceptionThreadsListType;
import us.ihmc.fastddsjava.profiles.gen.ReliabilityQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.RtpsParticipantAllocationAttributesType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;
import us.ihmc.fastddsjava.profiles.gen.ThreadSettingsType;
import us.ihmc.fastddsjava.profiles.gen.ThreadSettingsWithPortType;
import us.ihmc.fastddsjava.profiles.gen.TopicElementType;
import us.ihmc.fastddsjava.profiles.gen.TopicProfileType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorListType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;
import us.ihmc.fastddsjava.profiles.gen.Udpv4LocatorType;
import us.ihmc.fastddsjava.profiles.gen.Udpv6LocatorType;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Writes Fast-DDS XML profile fragments without an external XML library.
 * <p>
 * The output follows the rules of the Fast-DDS 3.6.2 XML parser (which is stricter than its XSD in places):
 * <ul>
 *    <li>Text content is written without surrounding whitespace (the parser does not trim it).</li>
 *    <li>An octet vector ({@code <userData>}) is written as exactly one {@code <value>} element.</li>
 *    <li>Durations are written as {@code <sec>}/{@code <nanosec>}, normalized ({@code nanosec < 10^9}); durations of
 *    {@code 2^31 - 1} seconds or more are written as {@code <sec>DURATION_INFINITY</sec>}. A {@code max_blocking_time} is never
 *    written as infinite.</li>
 *    <li>Inside {@code <rtps>}, socket buffer sizes, {@code <builtin>} and {@code <builtin_transports_reception_threads>} are
 *    written before {@code <builtinTransports>}, which the parser applies immediately; {@code <useBuiltinTransports>} is
 *    omitted when {@code <builtinTransports>} is used.</li>
 *    <li>Text matching {@code ${NAME}} is rejected because Fast-DDS would replace it with an environment variable.</li>
 *    <li>A KEEP_LAST depth larger than {@code max_samples_per_instance} is rejected (Fast-DDS would refuse the endpoint).</li>
 *    <li>Thread stack sizes other than -1 must be a multiple of 16384 and at least 131072 bytes; other values abort the process
 *    inside Fast-DDS on some platforms.</li>
 * </ul>
 */
final class ProfilesXMLWriter
{
   private static final Pattern ENVIRONMENT_VARIABLE = Pattern.compile("\\$\\{[A-Z_][A-Z0-9_]*\\}");
   /**
    * Smallest thread stack size written: glibc on aarch64 refuses stacks below 128 KiB ({@code PTHREAD_STACK_MIN}), and Fast-DDS
    * then throws from a native thread constructor, which aborts the JVM. Same value as {@code ROS2ThreadSettings.MIN_STACK_SIZE_BYTES}.
    */
   static final int MIN_STACK_SIZE_BYTES = 131072;
   /**
    * Thread stack sizes must be a multiple of this: macOS refuses stacks that are not page aligned (16 KiB pages on Apple
    * silicon). Same value as {@code ROS2ThreadSettings.STACK_SIZE_ALIGNMENT_BYTES}.
    */
   static final int STACK_SIZE_ALIGNMENT_BYTES = 16384;

   private ProfilesXMLWriter()
   {
   }

   static void writeProfile(StringBuilder xml, String elementName, Object profile)
   {
      if (profile instanceof TransportDescriptorListType)
      {
         writeTransportDescriptors(xml, elementName, (TransportDescriptorListType) profile);
      }
      else if (profile instanceof ParticipantProfileType)
      {
         writeParticipant(xml, elementName, (ParticipantProfileType) profile);
      }
      else if (profile instanceof PublisherProfileType)
      {
         writePublisher(xml, elementName, (PublisherProfileType) profile);
      }
      else if (profile instanceof SubscriberProfileType)
      {
         writeSubscriber(xml, elementName, (SubscriberProfileType) profile);
      }
      else if (profile instanceof TopicProfileType)
      {
         writeTopicProfile(xml, elementName, (TopicProfileType) profile);
      }
      else
      {
         throw new IllegalArgumentException("Unsupported profile type: " + profile.getClass().getName());
      }
   }

   private static void writeTransportDescriptors(StringBuilder xml, String elementName, TransportDescriptorListType transportList)
   {
      open(xml, elementName);
      for (TransportDescriptorType descriptor : transportList.getTransportDescriptor())
      {
         writeTransportDescriptor(xml, descriptor);
      }
      close(xml, elementName);
   }

   static void writeTransportDescriptor(StringBuilder xml, TransportDescriptorType descriptor)
   {
      open(xml, "transport_descriptor");
      writeText(xml, "transport_id", descriptor.getTransportId());
      writeText(xml, "type", descriptor.getType());
      writeText(xml, "sendBufferSize", descriptor.getSendBufferSize());
      writeText(xml, "receiveBufferSize", descriptor.getReceiveBufferSize());
      writeText(xml, "maxMessageSize", descriptor.getMaxMessageSize());
      writeText(xml, "maxInitialPeersRange", descriptor.getMaxInitialPeersRange());
      if (descriptor.getInterfaceWhiteList() != null)
      {
         open(xml, "interfaceWhiteList");
         for (Object entry : descriptor.getInterfaceWhiteList().getAddressOrInterface())
         {
            // Fast-DDS accepts either <address> or <interface>; use <address> for both string forms. An entry can also be a
            // list of addresses (the schema's address list type, see ROS2NodePrintout): one <address> per element.
            if (entry instanceof List<?> addresses)
            {
               for (Object address : addresses)
               {
                  writeText(xml, "address", address == null ? null : address.toString());
               }
            }
            else
            {
               writeText(xml, "address", entry == null ? null : entry.toString());
            }
         }
         close(xml, "interfaceWhiteList");
      }
      writeText(xml, "netmask_filter", descriptor.getNetmaskFilter());
      writeText(xml, "TTL", descriptor.getTTL());
      writeText(xml, "non_blocking_send", descriptor.isNonBlockingSend());
      writeText(xml, "output_port", descriptor.getOutputPort());
      writeText(xml, "wan_addr", descriptor.getWanAddr());
      writeText(xml, "keep_alive_frequency_ms", descriptor.getKeepAliveFrequencyMs());
      writeText(xml, "keep_alive_timeout_ms", descriptor.getKeepAliveTimeoutMs());
      writeText(xml, "max_logical_port", descriptor.getMaxLogicalPort());
      writeText(xml, "logical_port_range", descriptor.getLogicalPortRange());
      writeText(xml, "logical_port_increment", descriptor.getLogicalPortIncrement());
      if (descriptor.getListeningPorts() != null && !descriptor.getListeningPorts().getPort().isEmpty())
      {
         open(xml, "listening_ports");
         for (Integer port : descriptor.getListeningPorts().getPort())
         {
            writeText(xml, "port", port);
         }
         close(xml, "listening_ports");
      }
      writeText(xml, "calculate_crc", descriptor.isCalculateCrc());
      writeText(xml, "check_crc", descriptor.isCheckCrc());
      writeText(xml, "enable_tcp_nodelay", descriptor.isEnableTcpNodelay());
      writeThreadSettings(xml, "keep_alive_thread", descriptor.getKeepAliveThread());
      writeThreadSettings(xml, "accept_thread", descriptor.getAcceptThread());
      writeText(xml, "tcp_negotiation_timeout", descriptor.getTcpNegotiationTimeout());
      writeText(xml, "segment_size", descriptor.getSegmentSize());
      writeText(xml, "port_queue_capacity", descriptor.getPortQueueCapacity());
      writeText(xml, "healthy_check_timeout_ms", descriptor.getHealthyCheckTimeoutMs());
      writeText(xml, "rtps_dump_file", descriptor.getRtpsDumpFile());
      writeThreadSettings(xml, "default_reception_threads", descriptor.getDefaultReceptionThreads());
      writeReceptionThreads(xml, descriptor.getReceptionThreads());
      writeThreadSettings(xml, "dump_thread", descriptor.getDumpThread());
      close(xml, "transport_descriptor");
   }

   private static void writeReceptionThreads(StringBuilder xml, ReceptionThreadsListType receptionThreads)
   {
      if (receptionThreads != null && !receptionThreads.getReceptionThread().isEmpty())
      {
         open(xml, "reception_threads");
         for (ThreadSettingsWithPortType thread : receptionThreads.getReceptionThread())
         {
            // The port attribute is required by the parser for <reception_thread>
            xml.append("<reception_thread port=\"").append(thread.getPort()).append("\">");
            writeThreadSettingsChildren(xml, thread);
            close(xml, "reception_thread");
         }
         close(xml, "reception_threads");
      }
   }

   static void writeParticipant(StringBuilder xml, String elementName, ParticipantProfileType participant)
   {
      openWithAttrs(xml, elementName, attr("profile_name", participant.getProfileName()), attr("is_default_profile", participant.isIsDefaultProfile()));
      writeText(xml, "domainId", participant.getDomainId());
      if (participant.getRtps() != null)
      {
         ParticipantProfileType.Rtps rtps = participant.getRtps();
         open(xml, "rtps");
         writeText(xml, "name", rtps.getName());
         writeLocatorList(xml, "defaultUnicastLocatorList", rtps.getDefaultUnicastLocatorList());
         writeLocatorList(xml, "defaultMulticastLocatorList", rtps.getDefaultMulticastLocatorList());
         writeText(xml, "netmask_filter", rtps.getNetmaskFilter());
         // Everything that <builtinTransports> consumes while it is parsed must come before it.
         writeText(xml, "sendSocketBufferSize", rtps.getSendSocketBufferSize());
         writeText(xml, "listenSocketBufferSize", rtps.getListenSocketBufferSize());
         writeBuiltin(xml, rtps.getBuiltin());
         writeText(xml, "participantID", rtps.getParticipantID());
         writeThreadSettings(xml, "builtin_transports_reception_threads", rtps.getBuiltinTransportsReceptionThreads());
         writeBuiltinTransports(xml, rtps.getBuiltinTransports());
         if (rtps.getUserTransports() != null)
         {
            open(xml, "userTransports");
            for (String transportId : rtps.getUserTransports().getTransportId())
            {
               writeText(xml, "transport_id", transportId);
            }
            close(xml, "userTransports");
         }
         if (rtps.getBuiltinTransports() == null)
         {
            // Setting this after <builtinTransports> would add the implicit default transports on top of the explicit ones.
            writeText(xml, "useBuiltinTransports", rtps.isUseBuiltinTransports());
         }
         writePropertiesPolicy(xml, rtps.getPropertiesPolicy());
         writeAllocation(xml, rtps.getAllocation());
         writeOctetVector(xml, "userData", rtps.getUserData());
         writeThreadSettings(xml, "builtin_controllers_sender_thread", rtps.getBuiltinControllersSenderThread());
         writeThreadSettings(xml, "timed_events_thread", rtps.getTimedEventsThread());
         writeThreadSettings(xml, "discovery_server_thread", rtps.getDiscoveryServerThread());
         writeThreadSettings(xml, "typelookup_service_thread", rtps.getTypelookupServiceThread());
         writeThreadSettings(xml, "security_log_thread", rtps.getSecurityLogThread());
         close(xml, "rtps");
      }
      close(xml, elementName);
   }

   private static void writeBuiltin(StringBuilder xml, BuiltinAttributesType builtin)
   {
      if (builtin == null)
      {
         return;
      }
      // The generated classes lost the element names of these lists, so they cannot be written faithfully; refuse them
      // instead of silently dropping the configuration.
      if (builtin.getDiscoveryConfig() != null)
      {
         throw new IllegalArgumentException("<builtin><discovery_config> is not supported by the jros2 profile writer");
      }
      if (builtin.getMetatrafficExternalUnicastLocators() != null)
      {
         throw new IllegalArgumentException("<builtin><metatraffic_external_unicast_locators> is not supported by the jros2 profile writer");
      }
      open(xml, "builtin");
      writeText(xml, "avoid_builtin_multicast", builtin.isAvoidBuiltinMulticast());
      writeText(xml, "use_WriterLivelinessProtocol", builtin.isUseWriterLivelinessProtocol());
      writeLocatorList(xml, "metatrafficUnicastLocatorList", builtin.getMetatrafficUnicastLocatorList());
      writeLocatorList(xml, "metatrafficMulticastLocatorList", builtin.getMetatrafficMulticastLocatorList());
      writeLocatorList(xml, "initialPeersList", builtin.getInitialPeersList());
      writeText(xml, "readerHistoryMemoryPolicy", builtin.getReaderHistoryMemoryPolicy() == null ? null : builtin.getReaderHistoryMemoryPolicy().value());
      writeText(xml, "writerHistoryMemoryPolicy", builtin.getWriterHistoryMemoryPolicy() == null ? null : builtin.getWriterHistoryMemoryPolicy().value());
      writeText(xml, "readerPayloadSize", builtin.getReaderPayloadSize());
      writeText(xml, "writerPayloadSize", builtin.getWriterPayloadSize());
      writeText(xml, "mutation_tries", builtin.getMutationTries());
      close(xml, "builtin");
   }

   /**
    * Writes a locator list. Each {@code <locator>} holds exactly one transport child; an empty UDPv4 locator is written as
    * {@code <udpv4/>} (the XML spelling of a default {@code Locator_t}). An empty list is not written (Fast-DDS rejects it).
    */
   private static void writeLocatorList(StringBuilder xml, String elementName, LocatorListType locatorList)
   {
      if (locatorList == null || locatorList.getLocator().isEmpty())
      {
         return;
      }
      open(xml, elementName);
      for (LocatorListType.Locator locator : locatorList.getLocator())
      {
         open(xml, "locator");
         int children = 0;
         for (Udpv4LocatorType udpv4 : locator.getUdpv4())
         {
            writeIpLocator(xml, "udpv4", udpv4.getAddress(), udpv4.getPort());
            ++children;
         }
         for (Udpv6LocatorType udpv6 : locator.getUdpv6())
         {
            writeIpLocator(xml, "udpv6", udpv6.getAddress(), udpv6.getPort());
            ++children;
         }
         if (children != 1)
         {
            throw new IllegalArgumentException("Each <locator> in <" + elementName + "> must hold exactly one UDPv4 or UDPv6 locator, found " + children);
         }
         close(xml, "locator");
      }
      close(xml, elementName);
   }

   private static void writeIpLocator(StringBuilder xml, String kind, String address, Integer port)
   {
      if (address == null && port == null)
      {
         xml.append('<').append(kind).append("/>");
         return;
      }
      open(xml, kind);
      writeText(xml, "address", address);
      writeText(xml, "port", port);
      close(xml, kind);
   }

   private static void writeBuiltinTransports(StringBuilder xml, BuiltinTransportsType builtinTransports)
   {
      if (builtinTransports == null)
      {
         return;
      }
      if (builtinTransports.getValue() == null)
      {
         throw new IllegalArgumentException("<builtinTransports> requires a kind");
      }
      openWithAttrs(xml,
                    "builtinTransports",
                    attr("max_msg_size", builtinTransports.getMaxMsgSize()),
                    attr("sockets_size", builtinTransports.getSocketsSize()),
                    attr("non_blocking", builtinTransports.isNonBlocking()),
                    attr("tcp_negotiation_timeout", builtinTransports.getTcpNegotiationTimeout()));
      // value(), never name(): the enum constant names are mangled (e.g. UD_PV_4 for "UDPv4")
      xml.append(escape(builtinTransports.getValue().value()));
      close(xml, "builtinTransports");
   }

   private static void writePropertiesPolicy(StringBuilder xml, PropertyPolicyType propertiesPolicy)
   {
      if (propertiesPolicy == null)
      {
         return;
      }
      boolean opened = false;
      for (Object entry : propertiesPolicy.getPropertiesOrBinaryProperties())
      {
         if (entry instanceof PropertyPolicyType.Properties)
         {
            List<PropertyType> properties = ((PropertyPolicyType.Properties) entry).getProperty();
            if (properties.isEmpty())
            {
               continue; // <properties> must contain at least one <property>
            }
            if (!opened)
            {
               open(xml, "propertiesPolicy");
               opened = true;
            }
            open(xml, "properties");
            for (PropertyType property : properties)
            {
               if (property.getName() == null || property.getName().isEmpty() || property.getValue() == null || property.getValue().isEmpty())
               {
                  throw new IllegalArgumentException("Fast-DDS properties need a non-empty name and value: " + property.getName() + "=" + property.getValue());
               }
               open(xml, "property");
               writeText(xml, "name", property.getName());
               writeText(xml, "value", property.getValue());
               writeText(xml, "propagate", property.isPropagate());
               close(xml, "property");
            }
            close(xml, "properties");
         }
         else if (entry != null)
         {
            throw new IllegalArgumentException("Unsupported propertiesPolicy entry: " + entry.getClass().getName());
         }
      }
      if (opened)
      {
         close(xml, "propertiesPolicy");
      }
   }

   private static void writeAllocation(StringBuilder xml, RtpsParticipantAllocationAttributesType allocation)
   {
      if (allocation == null)
      {
         return;
      }
      open(xml, "allocation");
      if (allocation.getRemoteLocators() != null)
      {
         open(xml, "remote_locators");
         writeText(xml, "max_unicast_locators", allocation.getRemoteLocators().getMaxUnicastLocators());
         writeText(xml, "max_multicast_locators", allocation.getRemoteLocators().getMaxMulticastLocators());
         close(xml, "remote_locators");
      }
      writeAllocationConfig(xml, "total_participants", allocation.getTotalParticipants());
      writeAllocationConfig(xml, "total_readers", allocation.getTotalReaders());
      writeAllocationConfig(xml, "total_writers", allocation.getTotalWriters());
      if (allocation.getSendBuffers() != null)
      {
         open(xml, "send_buffers");
         writeText(xml, "preallocated_number", allocation.getSendBuffers().getPreallocatedNumber());
         writeText(xml, "dynamic", allocation.getSendBuffers().isDynamic());
         writeAllocationConfig(xml, "network_buffers_config", allocation.getSendBuffers().getNetworkBuffersConfig());
         close(xml, "send_buffers");
      }
      writeText(xml, "max_properties", allocation.getMaxProperties());
      writeText(xml, "max_user_data", allocation.getMaxUserData());
      writeText(xml, "max_partitions", allocation.getMaxPartitions());
      close(xml, "allocation");
   }

   private static void writeAllocationConfig(StringBuilder xml, String elementName, AllocationConfigType allocationConfig)
   {
      if (allocationConfig == null)
      {
         return;
      }
      if (allocationConfig.getIncrement() != null && allocationConfig.getIncrement() == 0L && allocationConfig.getInitial() != null
          && allocationConfig.getMaximum() != null && !allocationConfig.getInitial().equals(allocationConfig.getMaximum()))
      {
         throw new IllegalArgumentException("<" + elementName + "> with increment 0 requires initial == maximum");
      }
      open(xml, elementName);
      writeText(xml, "initial", allocationConfig.getInitial());
      writeText(xml, "maximum", allocationConfig.getMaximum());
      writeText(xml, "increment", allocationConfig.getIncrement());
      close(xml, elementName);
   }

   /**
    * Writes an octet vector as exactly one {@code <value>}: Fast-DDS rejects a second {@code <value>} element.
    */
   private static void writeOctetVector(StringBuilder xml, String elementName, OctectVectorQosPolicyType octetVector)
   {
      if (octetVector == null || octetVector.getValue().isEmpty())
      {
         return;
      }
      StringBuilder value = new StringBuilder();
      for (String part : octetVector.getValue())
      {
         if (part == null || part.isEmpty())
         {
            continue;
         }
         // Validates the format (and rejects whitespace, which the parser does not accept after an octet)
         OctetVectorTools.parseOctetString(part);
         if (value.length() > 0)
         {
            value.append('.');
         }
         value.append(part);
      }
      if (value.length() == 0)
      {
         throw new IllegalArgumentException("<" + elementName + "> cannot be empty");
      }
      open(xml, elementName);
      writeText(xml, "value", value.toString());
      close(xml, elementName);
   }

   /**
    * Writes thread settings. Nothing is written when no field is set.
    */
   private static void writeThreadSettings(StringBuilder xml, String elementName, ThreadSettingsType threadSettings)
   {
      if (threadSettings == null || (threadSettings.getSchedulingPolicy() == null && threadSettings.getPriority() == null && threadSettings.getAffinity() == null
                                     && threadSettings.getStackSize() == null))
      {
         return;
      }
      open(xml, elementName);
      writeThreadSettingsChildren(xml, threadSettings);
      close(xml, elementName);
   }

   private static void writeThreadSettingsChildren(StringBuilder xml, ThreadSettingsType threadSettings)
   {
      if (threadSettings.getSchedulingPolicy() != null && threadSettings.getSchedulingPolicy() < -1)
      {
         throw new IllegalArgumentException("Thread scheduling_policy must be >= -1: " + threadSettings.getSchedulingPolicy());
      }
      Integer stackSize = threadSettings.getStackSize();
      if (stackSize != null && stackSize != -1 && (stackSize < MIN_STACK_SIZE_BYTES || stackSize % STACK_SIZE_ALIGNMENT_BYTES != 0))
      {
         // Fast-DDS throws from the native thread constructor when pthread refuses the size, which aborts the JVM
         throw new IllegalArgumentException("Thread stack_size must be -1 or a multiple of " + STACK_SIZE_ALIGNMENT_BYTES + " that is at least " + MIN_STACK_SIZE_BYTES
                                            + " (other values abort the process on some platforms): " + stackSize);
      }
      if (threadSettings.getAffinity() != null && threadSettings.getAffinity().signum() < 0)
      {
         throw new IllegalArgumentException("Thread affinity must not be negative: " + threadSettings.getAffinity());
      }
      writeText(xml, "scheduling_policy", threadSettings.getSchedulingPolicy());
      writeText(xml, "priority", threadSettings.getPriority());
      writeText(xml, "affinity", threadSettings.getAffinity());
      writeText(xml, "stack_size", threadSettings.getStackSize());
   }

   private static void writePublisher(StringBuilder xml, String elementName, PublisherProfileType publisher)
   {
      openWithAttrs(xml, elementName, attr("profile_name", publisher.getProfileName()), attr("is_default_profile", publisher.isIsDefaultProfile()));
      writeTopicElement(xml, publisher.getTopic());
      writeDataWriterQos(xml, publisher.getQos());
      writeHistoryMemoryPolicy(xml, publisher.getHistoryMemoryPolicy());
      close(xml, elementName);
   }

   private static void writeSubscriber(StringBuilder xml, String elementName, SubscriberProfileType subscriber)
   {
      openWithAttrs(xml, elementName, attr("profile_name", subscriber.getProfileName()), attr("is_default_profile", subscriber.isIsDefaultProfile()));
      writeTopicElement(xml, subscriber.getTopic());
      writeDataReaderQos(xml, subscriber.getQos());
      writeHistoryMemoryPolicy(xml, subscriber.getHistoryMemoryPolicy());
      close(xml, elementName);
   }

   private static void writeHistoryMemoryPolicy(StringBuilder xml, HistoryMemoryPolicyType historyMemoryPolicy)
   {
      if (historyMemoryPolicy != null)
      {
         writeText(xml, "historyMemoryPolicy", historyMemoryPolicy.value());
      }
   }

   private static void writeTopicProfile(StringBuilder xml, String elementName, TopicProfileType topic)
   {
      openWithAttrs(xml, elementName, attr("profile_name", topic.getProfileName()), attr("is_default_profile", topic.isIsDefaultProfile()));
      writeHistoryQos(xml, topic.getHistoryQos());
      close(xml, elementName);
   }

   private static void writeTopicElement(StringBuilder xml, TopicElementType topic)
   {
      if (topic == null || (topic.getHistoryQos() == null && topic.getResourceLimitsQos() == null))
      {
         return;
      }
      checkHistoryAgainstResourceLimits(topic);
      open(xml, "topic");
      writeHistoryQos(xml, topic.getHistoryQos());
      TopicElementType.ResourceLimitsQos resourceLimits = topic.getResourceLimitsQos();
      if (resourceLimits != null)
      {
         open(xml, "resourceLimitsQos");
         writeText(xml, "max_samples", resourceLimits.getMaxSamples());
         writeText(xml, "max_instances", resourceLimits.getMaxInstances());
         writeText(xml, "max_samples_per_instance", resourceLimits.getMaxSamplesPerInstance());
         writeText(xml, "allocated_samples", resourceLimits.getAllocatedSamples());
         writeText(xml, "extra_samples", resourceLimits.getExtraSamples());
         close(xml, "resourceLimitsQos");
      }
      close(xml, "topic");
   }

   /**
    * Fast-DDS refuses to create a KEEP_LAST endpoint whose depth exceeds {@code max_samples_per_instance}.
    */
   private static void checkHistoryAgainstResourceLimits(TopicElementType topic)
   {
      HistoryQosPolicyType history = topic.getHistoryQos();
      TopicElementType.ResourceLimitsQos resourceLimits = topic.getResourceLimitsQos();
      if (history == null || resourceLimits == null || history.getDepth() == null || resourceLimits.getMaxSamplesPerInstance() == null)
      {
         return;
      }
      boolean keepLast = history.getKind() == null || history.getKind() == HistoryQosKindPolicyType.KEEP_LAST;
      long maxSamplesPerInstance = resourceLimits.getMaxSamplesPerInstance();
      if (keepLast && maxSamplesPerInstance > 0 && history.getDepth() > maxSamplesPerInstance)
      {
         throw new IllegalArgumentException("KEEP_LAST history depth " + history.getDepth() + " is higher than max_samples_per_instance " + maxSamplesPerInstance);
      }
   }

   private static void writeHistoryQos(StringBuilder xml, HistoryQosPolicyType historyQos)
   {
      if (historyQos != null)
      {
         boolean keepLast = historyQos.getKind() == null || historyQos.getKind() == HistoryQosKindPolicyType.KEEP_LAST;
         if (keepLast && historyQos.getDepth() != null && historyQos.getDepth() <= 0)
         {
            throw new IllegalArgumentException("A KEEP_LAST history depth must be greater than 0: " + historyQos.getDepth());
         }
         open(xml, "historyQos");
         if (historyQos.getKind() != null)
         {
            writeText(xml, "kind", historyQos.getKind().value());
         }
         writeText(xml, "depth", historyQos.getDepth());
         close(xml, "historyQos");
      }
   }

   private static void writeDataWriterQos(StringBuilder xml, DataWriterQosPoliciesType qos)
   {
      if (qos != null)
      {
         open(xml, "qos");
         writeDurability(xml, qos.getDurability());
         writeDeadline(xml, qos.getDeadline());
         writeLifespan(xml, qos.getLifespan());
         writeLiveliness(xml, qos.getLiveliness());
         writeReliability(xml, qos.getReliability());
         writePartition(xml, qos.getPartition());
         writePublishMode(xml, qos.getPublishMode());
         writeDataSharing(xml, qos.getDataSharing());
         writeOctetVector(xml, "userData", qos.getUserData());
         writeOctetVector(xml, "topicData", qos.getTopicData());
         writeOctetVector(xml, "groupData", qos.getGroupData());
         close(xml, "qos");
      }
   }

   private static void writeDataReaderQos(StringBuilder xml, DataReaderQosPoliciesType qos)
   {
      if (qos != null)
      {
         open(xml, "qos");
         writeDurability(xml, qos.getDurability());
         writeDeadline(xml, qos.getDeadline());
         writeLifespan(xml, qos.getLifespan());
         writeLiveliness(xml, qos.getLiveliness());
         writeReliability(xml, qos.getReliability());
         writePartition(xml, qos.getPartition());
         writeDataSharing(xml, qos.getDataSharing());
         writeOctetVector(xml, "userData", qos.getUserData());
         writeOctetVector(xml, "topicData", qos.getTopicData());
         writeOctetVector(xml, "groupData", qos.getGroupData());
         close(xml, "qos");
      }
   }

   private static void writeDurability(StringBuilder xml, DurabilityQosPolicyType durability)
   {
      if (durability != null)
      {
         // The parser rejects <durability/> without a kind
         if (durability.getKind() == null)
         {
            return;
         }
         open(xml, "durability");
         writeText(xml, "kind", durability.getKind());
         close(xml, "durability");
      }
   }

   private static void writeDeadline(StringBuilder xml, DeadlineQosPolicyType deadline)
   {
      // The parser rejects <deadline/> without a period
      if (deadline != null && deadline.getPeriod() != null)
      {
         open(xml, "deadline");
         writeDuration(xml, "period", deadline.getPeriod(), false);
         close(xml, "deadline");
      }
   }

   private static void writeLifespan(StringBuilder xml, LifespanQosPolicyType lifespan)
   {
      // The parser rejects <lifespan/> without a duration
      if (lifespan != null && lifespan.getDuration() != null)
      {
         open(xml, "lifespan");
         writeDuration(xml, "duration", lifespan.getDuration(), false);
         close(xml, "lifespan");
      }
   }

   private static void writeReliability(StringBuilder xml, ReliabilityQosPolicyType reliability)
   {
      if (reliability != null)
      {
         open(xml, "reliability");
         writeText(xml, "kind", reliability.getKind());
         // Fast-DDS refuses RELIABLE endpoints with an infinite max_blocking_time
         writeDuration(xml, "max_blocking_time", reliability.getMaxBlockingTime(), true);
         close(xml, "reliability");
      }
   }

   private static void writeLiveliness(StringBuilder xml, LivelinessQosPolicyType liveliness)
   {
      if (liveliness != null)
      {
         open(xml, "liveliness");
         writeText(xml, "kind", liveliness.getKind());
         writeDuration(xml, "lease_duration", liveliness.getLeaseDuration(), false);
         writeDuration(xml, "announcement_period", liveliness.getAnnouncementPeriod(), false);
         close(xml, "liveliness");
      }
   }

   private static void writePartition(StringBuilder xml, PartitionQosPolicyType partition)
   {
      if (partition != null && partition.getNames() != null && !partition.getNames().getName().isEmpty())
      {
         open(xml, "partition");
         open(xml, "names");
         for (String name : partition.getNames().getName())
         {
            writeText(xml, "name", name);
         }
         close(xml, "names");
         close(xml, "partition");
      }
   }

   private static void writePublishMode(StringBuilder xml, PublishModeQosPolicyType publishMode)
   {
      if (publishMode != null && (publishMode.getKind() != null || publishMode.getFlowControllerName() != null))
      {
         open(xml, "publishMode");
         writeText(xml, "kind", publishMode.getKind());
         writeText(xml, "flow_controller_name", publishMode.getFlowControllerName());
         close(xml, "publishMode");
      }
   }

   private static void writeDataSharing(StringBuilder xml, DataSharingQosPolicyType dataSharing)
   {
      if (dataSharing != null)
      {
         // <kind> is required by the parser
         if (dataSharing.getKind() == null)
         {
            throw new IllegalArgumentException("<data_sharing> requires a kind (AUTOMATIC, ON or OFF)");
         }
         open(xml, "data_sharing");
         writeText(xml, "kind", dataSharing.getKind());
         writeText(xml, "shared_dir", dataSharing.getSharedDir());
         writeText(xml, "max_domains", dataSharing.getMaxDomains());
         if (dataSharing.getDomainIds() != null && !dataSharing.getDomainIds().getDomainId().isEmpty())
         {
            open(xml, "domain_ids");
            for (Long domainId : dataSharing.getDomainIds().getDomainId())
            {
               writeText(xml, "domainId", domainId);
            }
            close(xml, "domain_ids");
         }
         writeThreadSettings(xml, "data_sharing_listener_thread", dataSharing.getDataSharingListenerThread());
         close(xml, "data_sharing");
      }
   }

   /**
    * Writes a duration by explicit element name ({@code <sec>}, {@code <nanosec>}), normalized. {@link DurationType} uses
    * the positional list convention documented in {@link DurationTypeTools}.
    *
    * @param neverInfinite write the largest finite duration instead of infinity (for {@code max_blocking_time})
    */
   private static void writeDuration(StringBuilder xml, String elementName, DurationType duration, boolean neverInfinite)
   {
      if (duration == null || duration.getSecOrNanosec() == null || duration.getSecOrNanosec().isEmpty())
      {
         return;
      }
      long[] normalized = DurationTypeTools.normalize(duration);
      if (normalized == null && neverInfinite)
      {
         normalized = new long[] {DurationTypeTools.MAX_FINITE_SECONDS, DurationTypeTools.MAX_NANOSECONDS};
      }
      open(xml, elementName);
      if (normalized == null)
      {
         writeText(xml, "sec", DurationTypeTools.DURATION_INFINITY);
      }
      else
      {
         writeText(xml, "sec", normalized[0]);
         writeText(xml, "nanosec", normalized[1]);
      }
      close(xml, elementName);
   }

   private static void open(StringBuilder xml, String name)
   {
      xml.append('<').append(name).append('>');
   }

   private static void close(StringBuilder xml, String name)
   {
      xml.append("</").append(name).append('>');
   }

   private static void openWithAttrs(StringBuilder xml, String name, String... attrs)
   {
      xml.append('<').append(name);
      for (String attr : attrs)
      {
         if (attr != null)
         {
            xml.append(' ').append(attr);
         }
      }
      xml.append('>');
   }

   private static String attr(String name, Object value)
   {
      String result = null;
      if (value != null)
      {
         result = name + "=\"" + escape(value.toString()) + "\"";
      }
      return result;
   }

   private static void writeText(StringBuilder xml, String name, Object value)
   {
      if (value != null)
      {
         xml.append('<').append(name).append('>');
         xml.append(escape(value.toString()));
         xml.append("</").append(name).append('>');
      }
   }

   /**
    * Escapes XML special characters.
    *
    * @throws IllegalArgumentException if the text contains {@code ${NAME}}: Fast-DDS replaces such sequences with environment
    *                                  variables and offers no way to escape them
    */
   static String escape(String value)
   {
      if (value.contains("${") && ENVIRONMENT_VARIABLE.matcher(value).find())
      {
         throw new IllegalArgumentException("'" + value + "' cannot be written to a Fast-DDS XML profile: Fast-DDS would replace '${...}' with an environment variable");
      }
      return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
   }
}
