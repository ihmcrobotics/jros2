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

import us.ihmc.fastddsjava.profiles.gen.DataReaderQosPoliciesType;
import us.ihmc.fastddsjava.profiles.gen.DataWriterQosPoliciesType;
import us.ihmc.fastddsjava.profiles.gen.DeadlineQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.DurabilityQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.DurationType;
import us.ihmc.fastddsjava.profiles.gen.HistoryQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.LifespanQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.LivelinessQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.ReliabilityQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;
import us.ihmc.fastddsjava.profiles.gen.TopicElementType;
import us.ihmc.fastddsjava.profiles.gen.TopicProfileType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorListType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;

import java.util.List;

/**
 * Writes Fast-DDS XML profile fragments without an external XML library.
 */
final class ProfilesXMLWriter
{
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

   private static void writeTransportDescriptor(StringBuilder xml, TransportDescriptorType descriptor)
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
            // Fast-DDS accepts either <address> or <interface>; use <address> for both string forms.
            writeText(xml, "address", entry == null ? null : entry.toString());
         }
         close(xml, "interfaceWhiteList");
      }
      writeText(xml, "netmask_filter", descriptor.getNetmaskFilter());
      writeText(xml, "TTL", descriptor.getTTL());
      writeText(xml, "non_blocking_send", descriptor.isNonBlockingSend());
      writeText(xml, "output_port", descriptor.getOutputPort());
      writeText(xml, "wan_addr", descriptor.getWanAddr());
      writeText(xml, "segment_size", descriptor.getSegmentSize());
      writeText(xml, "port_queue_capacity", descriptor.getPortQueueCapacity());
      writeText(xml, "healthy_check_timeout_ms", descriptor.getHealthyCheckTimeoutMs());
      close(xml, "transport_descriptor");
   }

   private static void writeParticipant(StringBuilder xml, String elementName, ParticipantProfileType participant)
   {
      openWithAttrs(xml, elementName, attr("profile_name", participant.getProfileName()), attr("is_default_profile", participant.isIsDefaultProfile()));
      writeText(xml, "domainId", participant.getDomainId());
      if (participant.getRtps() != null)
      {
         ParticipantProfileType.Rtps rtps = participant.getRtps();
         open(xml, "rtps");
         writeText(xml, "name", rtps.getName());
         writeText(xml, "useBuiltinTransports", rtps.isUseBuiltinTransports());
         if (rtps.getUserTransports() != null)
         {
            open(xml, "userTransports");
            for (String transportId : rtps.getUserTransports().getTransportId())
            {
               writeText(xml, "transport_id", transportId);
            }
            close(xml, "userTransports");
         }
         close(xml, "rtps");
      }
      close(xml, elementName);
   }

   private static void writePublisher(StringBuilder xml, String elementName, PublisherProfileType publisher)
   {
      openWithAttrs(xml, elementName, attr("profile_name", publisher.getProfileName()), attr("is_default_profile", publisher.isIsDefaultProfile()));
      writeTopicElement(xml, publisher.getTopic());
      writeDataWriterQos(xml, publisher.getQos());
      close(xml, elementName);
   }

   private static void writeSubscriber(StringBuilder xml, String elementName, SubscriberProfileType subscriber)
   {
      openWithAttrs(xml, elementName, attr("profile_name", subscriber.getProfileName()), attr("is_default_profile", subscriber.isIsDefaultProfile()));
      writeTopicElement(xml, subscriber.getTopic());
      writeDataReaderQos(xml, subscriber.getQos());
      close(xml, elementName);
   }

   private static void writeTopicProfile(StringBuilder xml, String elementName, TopicProfileType topic)
   {
      openWithAttrs(xml, elementName, attr("profile_name", topic.getProfileName()), attr("is_default_profile", topic.isIsDefaultProfile()));
      writeHistoryQos(xml, topic.getHistoryQos());
      close(xml, elementName);
   }

   private static void writeTopicElement(StringBuilder xml, TopicElementType topic)
   {
      if (topic != null)
      {
         open(xml, "topic");
         writeHistoryQos(xml, topic.getHistoryQos());
         close(xml, "topic");
      }
   }

   private static void writeHistoryQos(StringBuilder xml, HistoryQosPolicyType historyQos)
   {
      if (historyQos != null)
      {
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
         writeReliability(xml, qos.getReliability());
         writeLiveliness(xml, qos.getLiveliness());
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
         writeReliability(xml, qos.getReliability());
         writeLiveliness(xml, qos.getLiveliness());
         close(xml, "qos");
      }
   }

   private static void writeDurability(StringBuilder xml, DurabilityQosPolicyType durability)
   {
      if (durability != null)
      {
         open(xml, "durability");
         writeText(xml, "kind", durability.getKind());
         close(xml, "durability");
      }
   }

   private static void writeDeadline(StringBuilder xml, DeadlineQosPolicyType deadline)
   {
      if (deadline != null)
      {
         open(xml, "deadline");
         writeDuration(xml, "period", deadline.getPeriod());
         close(xml, "deadline");
      }
   }

   private static void writeLifespan(StringBuilder xml, LifespanQosPolicyType lifespan)
   {
      if (lifespan != null)
      {
         open(xml, "lifespan");
         writeDuration(xml, "duration", lifespan.getDuration());
         close(xml, "lifespan");
      }
   }

   private static void writeReliability(StringBuilder xml, ReliabilityQosPolicyType reliability)
   {
      if (reliability != null)
      {
         open(xml, "reliability");
         writeText(xml, "kind", reliability.getKind());
         writeDuration(xml, "max_blocking_time", reliability.getMaxBlockingTime());
         close(xml, "reliability");
      }
   }

   private static void writeLiveliness(StringBuilder xml, LivelinessQosPolicyType liveliness)
   {
      if (liveliness != null)
      {
         open(xml, "liveliness");
         writeText(xml, "kind", liveliness.getKind());
         writeDuration(xml, "lease_duration", liveliness.getLeaseDuration());
         writeDuration(xml, "announcement_period", liveliness.getAnnouncementPeriod());
         close(xml, "liveliness");
      }
   }

   /**
    * QoSTools stores duration as [nanosec, sec] in {@link DurationType#getSecOrNanosec()}.
    */
   private static void writeDuration(StringBuilder xml, String elementName, DurationType duration)
   {
      if (duration != null)
      {
         List<String> values = duration.getSecOrNanosec();
         if (values != null && !values.isEmpty())
         {
            open(xml, elementName);
            if (values.size() >= 2)
            {
               writeText(xml, "nanosec", values.get(0));
               writeText(xml, "sec", values.get(1));
            }
            else
            {
               writeText(xml, "sec", values.get(0));
            }
            close(xml, elementName);
         }
      }
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

   private static String escape(String value)
   {
      return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
   }
}
