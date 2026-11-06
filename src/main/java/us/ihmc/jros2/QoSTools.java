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
package us.ihmc.jros2;

import us.ihmc.fastddsjava.profiles.DurationTypeTools;
import us.ihmc.fastddsjava.profiles.OctetVectorTools;
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
import us.ihmc.fastddsjava.profiles.gen.OctectVectorQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.PublishModeQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.ReliabilityQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;
import us.ihmc.fastddsjava.profiles.gen.TopicElementType;

import java.time.Duration;

/**
 * Translates {@link ROS2QoSProfile} into Fast-DDS {@code <data_writer>} / {@code <data_reader>} profiles exactly like
 * rmw_fastrtps (Jazzy) does in {@code fill_entity_qos_from_profile} plus the endpoint settings it applies to every endpoint:
 * <ul>
 *    <li>{@code SYSTEM_DEFAULT} kinds leave the Fast-DDS default (the element is omitted). Note that the Fast-DDS DataWriter
 *    default durability is TRANSIENT_LOCAL and its default reliability RELIABLE; a DataReader defaults to BEST_EFFORT and
 *    VOLATILE.</li>
 *    <li>The depth is only written when it is greater than 0 (rmw only ever raises the Fast-DDS default depth of 1).</li>
 *    <li>Unspecified durations ({0, 0}) are omitted; infinite durations are written as {@code DURATION_INFINITY}; finite
 *    durations are normalized, and durations of {@code 2^31 - 1} seconds or more are infinite.</li>
 *    <li>The liveliness kind is always written when it is not SYSTEM_DEFAULT; when a lease is set, the announcement period is
 *    2/3 of the lease.</li>
 *    <li>{@code max_blocking_time} is only written when set, and never as infinite (Fast-DDS rejects RELIABLE endpoints with an
 *    infinite blocking time).</li>
 *    <li>Every endpoint uses {@code historyMemoryPolicy PREALLOCATED_WITH_REALLOC} and {@code data_sharing OFF}; writers use
 *    {@code publishMode SYNCHRONOUS}. Unlike rmw_fastrtps, a KEEP_LAST reader sets {@code allocated_samples} to its depth
 *    (see {@link #translateReaderHistory}).</li>
 *    <li>A non-null type hash is advertised as endpoint USER_DATA {@code typehash=<hash>;} (ASCII, no terminating NUL), which
 *    ROS 2 Iron and newer show in {@code ros2 topic info -v}.</li>
 * </ul>
 * The XCDR1 data representation that rmw_fastrtps also sets cannot be expressed in Fast-DDS XML; the native layer sets it.
 */
final class QoSTools
{
   /** Prefix of the endpoint USER_DATA key that carries the RIHS01 type hash. */
   static final String TYPE_HASH_USER_DATA_PREFIX = "typehash=";
   /** Fast-DDS's default {@code allocated_samples}. */
   private static final long FASTDDS_DEFAULT_ALLOCATED_SAMPLES = 100L;

   private QoSTools()
   {
   }

   /**
    * Translate {@link ROS2QoSProfile} into a Fast-DDS {@link PublisherProfileType}, without a type hash.
    * Use when building a {@link ROS2Publisher}.
    */
   static void translateQoS(ROS2QoSProfile qosProfile, PublisherProfileType publisherProfile)
   {
      translateQoS(qosProfile, publisherProfile, null);
   }

   /**
    * Translate {@link ROS2QoSProfile} into a Fast-DDS {@link SubscriberProfileType}, without a type hash.
    * Use when building a {@link ROS2Subscription}.
    */
   static void translateQoS(ROS2QoSProfile qosProfile, SubscriberProfileType subscriberProfile)
   {
      translateQoS(qosProfile, subscriberProfile, null);
   }

   /**
    * Translate {@link ROS2QoSProfile} into a Fast-DDS {@link PublisherProfileType} (DataWriter and Publisher QoS).
    *
    * @param qosProfile       the ROS 2 QoS
    * @param publisherProfile the profile to fill; its topic element and QoS are replaced
    * @param typeHash         the RIHS01 type hash of the message type (e.g. {@code RIHS01_df66...}), or null to advertise none
    */
   static void translateQoS(ROS2QoSProfile qosProfile, PublisherProfileType publisherProfile, String typeHash)
   {
      DataWriterQosPoliciesType qos = new DataWriterQosPoliciesType();
      qos.setDurability(translateDurability(qosProfile.getDurability()));
      qos.setDeadline(translateDeadline(qosProfile.getDeadline()));
      qos.setLifespan(translateLifespan(qosProfile.getLifespan()));
      qos.setLiveliness(translateLiveliness(qosProfile.getLiveliness(), qosProfile.getLeaseDuration()));
      qos.setReliability(translateReliability(qosProfile.getReliability(), qosProfile.getMaxBlockingTime()));

      PublishModeQosPolicyType publishMode = new PublishModeQosPolicyType();
      publishMode.setKind("SYNCHRONOUS");
      qos.setPublishMode(publishMode);
      qos.setDataSharing(dataSharingOff());
      qos.setUserData(typeHashUserData(typeHash));

      publisherProfile.setQos(qos);
      publisherProfile.setTopic(translateHistory(qosProfile.getHistory(), qosProfile.getDepth()));
      publisherProfile.setHistoryMemoryPolicy(HistoryMemoryPolicyType.PREALLOCATED_WITH_REALLOC);
   }

   /**
    * Translate {@link ROS2QoSProfile} into a Fast-DDS {@link SubscriberProfileType} (DataReader and Subscriber QoS).
    *
    * @param qosProfile        the ROS 2 QoS
    * @param subscriberProfile the profile to fill; its topic element and QoS are replaced
    * @param typeHash          the RIHS01 type hash of the message type, or null to advertise none
    */
   static void translateQoS(ROS2QoSProfile qosProfile, SubscriberProfileType subscriberProfile, String typeHash)
   {
      DataReaderQosPoliciesType qos = new DataReaderQosPoliciesType();
      qos.setDurability(translateDurability(qosProfile.getDurability()));
      qos.setDeadline(translateDeadline(qosProfile.getDeadline()));
      qos.setLifespan(translateLifespan(qosProfile.getLifespan()));
      qos.setLiveliness(translateLiveliness(qosProfile.getLiveliness(), qosProfile.getLeaseDuration()));
      qos.setReliability(translateReliability(qosProfile.getReliability(), qosProfile.getMaxBlockingTime()));
      qos.setDataSharing(dataSharingOff());
      qos.setUserData(typeHashUserData(typeHash));

      subscriberProfile.setQos(qos);
      subscriberProfile.setTopic(translateReaderHistory(qosProfile.getHistory(), qosProfile.getDepth()));
      subscriberProfile.setHistoryMemoryPolicy(HistoryMemoryPolicyType.PREALLOCATED_WITH_REALLOC);
   }

   /**
    * The {@code <topic>} element of a DataReader: its history, plus for a KEEP_LAST history as many preallocated payloads as
    * the history holds. Fast-DDS limits a DataWriter's preallocation to its depth itself, but a DataReader preallocates
    * {@code allocated_samples} payloads (default 100) whatever its depth. Every preallocated payload of a topic grows to the
    * largest message seen, so the default would cost 100 times that message for each subscription. Fast-DDS adds its
    * {@code extra_samples} (1) to this, and adds a payload if more are ever in use at once. This does not change anything on
    * the wire. KEEP_ALL keeps the default. Returns null when nothing is set.
    */
   static TopicElementType translateReaderHistory(ROS2QoSProfile.History history, int depth)
   {
      TopicElementType topicElement = translateHistory(history, depth);
      if (history == ROS2QoSProfile.History.KEEP_ALL)
      {
         return topicElement;
      }

      // Without a depth (or with SYSTEM_DEFAULT) the history is Fast-DDS's default KEEP_LAST with depth 1
      long historySamples = depth > 0 ? depth : 1L;
      if (historySamples >= FASTDDS_DEFAULT_ALLOCATED_SAMPLES)
      {
         return topicElement;
      }

      TopicElementType.ResourceLimitsQos resourceLimitsQos = new TopicElementType.ResourceLimitsQos();
      resourceLimitsQos.setAllocatedSamples(historySamples);
      if (topicElement == null)
      {
         topicElement = new TopicElementType();
      }
      topicElement.setResourceLimitsQos(resourceLimitsQos);
      return topicElement;
   }

   /**
    * History kind and depth live in the endpoint's {@code <topic>} element. Returns null when neither is set.
    */
   static TopicElementType translateHistory(ROS2QoSProfile.History history, int depth)
   {
      HistoryQosPolicyType historyQos = new HistoryQosPolicyType();
      boolean set = false;
      switch (history)
      {
         case KEEP_LAST ->
         {
            historyQos.setKind(HistoryQosKindPolicyType.KEEP_LAST);
            set = true;
         }
         case KEEP_ALL ->
         {
            historyQos.setKind(HistoryQosKindPolicyType.KEEP_ALL);
            set = true;
         }
         case SYSTEM_DEFAULT ->
         {
            // Leave the Fast-DDS default kind (KEEP_LAST)
         }
      }

      // rmw_fastrtps raises the Fast-DDS default depth (1) to the requested depth; depth 0 means "system default".
      // Fast-DDS rejects KEEP_LAST with depth 0, so it is never written.
      if (depth > 0)
      {
         historyQos.setDepth((long) depth);
         set = true;
      }

      if (!set)
      {
         return null;
      }

      TopicElementType topicElement = new TopicElementType();
      topicElement.setHistoryQos(historyQos);
      return topicElement;
   }

   static DurabilityQosPolicyType translateDurability(ROS2QoSProfile.Durability durability)
   {
      String kind = switch (durability)
      {
         case VOLATILE -> "VOLATILE";
         case TRANSIENT_LOCAL -> "TRANSIENT_LOCAL";
         case SYSTEM_DEFAULT -> null;
      };
      if (kind == null)
      {
         return null;
      }
      DurabilityQosPolicyType durabilityQos = new DurabilityQosPolicyType();
      durabilityQos.setKind(kind);
      return durabilityQos;
   }

   static DeadlineQosPolicyType translateDeadline(Duration deadline)
   {
      if (ROS2QoSProfile.isUnspecified(deadline))
      {
         return null;
      }
      DeadlineQosPolicyType deadlineQos = new DeadlineQosPolicyType();
      deadlineQos.setPeriod(toDurationType(deadline));
      return deadlineQos;
   }

   static LifespanQosPolicyType translateLifespan(Duration lifespan)
   {
      if (ROS2QoSProfile.isUnspecified(lifespan))
      {
         return null;
      }
      LifespanQosPolicyType lifespanQos = new LifespanQosPolicyType();
      lifespanQos.setDuration(toDurationType(lifespan));
      return lifespanQos;
   }

   static LivelinessQosPolicyType translateLiveliness(ROS2QoSProfile.Liveliness liveliness, Duration leaseDuration)
   {
      LivelinessQosPolicyType livelinessQos = new LivelinessQosPolicyType();
      boolean set = false;

      switch (liveliness)
      {
         case AUTOMATIC ->
         {
            livelinessQos.setKind("AUTOMATIC");
            set = true;
         }
         case MANUAL_BY_TOPIC ->
         {
            livelinessQos.setKind("MANUAL_BY_TOPIC");
            set = true;
         }
         case SYSTEM_DEFAULT ->
         {
            // Leave the Fast-DDS default kind (AUTOMATIC)
         }
      }

      if (!ROS2QoSProfile.isUnspecified(leaseDuration))
      {
         livelinessQos.setLeaseDuration(toDurationType(leaseDuration));
         livelinessQos.setAnnouncementPeriod(announcementPeriod(leaseDuration));
         set = true;
      }

      return set ? livelinessQos : null;
   }

   /**
    * rmw_fastrtps: {@code announcement_period = lease_duration * 2 / 3}, computed from the DDS (clamped) lease.
    */
   static DurationType announcementPeriod(Duration leaseDuration)
   {
      if (ROS2QoSProfile.isInfinite(leaseDuration))
      {
         return DurationTypeTools.infinite();
      }
      // Finite leases are below 2^31 - 1 seconds, so the nanosecond count (< 2.2e18) times 2 fits in a long.
      long leaseNanos = leaseDuration.getSeconds() * 1_000_000_000L + leaseDuration.getNano();
      long periodNanos = leaseNanos * 2L / 3L;
      return DurationTypeTools.of(periodNanos / 1_000_000_000L, periodNanos % 1_000_000_000L);
   }

   static ReliabilityQosPolicyType translateReliability(ROS2QoSProfile.Reliability reliability, Duration maxBlockingTime)
   {
      ReliabilityQosPolicyType reliabilityQos = new ReliabilityQosPolicyType();
      boolean set = false;

      String kind = switch (reliability)
      {
         case RELIABLE -> "RELIABLE";
         case BEST_EFFORT -> "BEST_EFFORT";
         case SYSTEM_DEFAULT -> null;
      };
      if (kind != null)
      {
         reliabilityQos.setKind(kind);
         set = true;
      }

      if (!ROS2QoSProfile.isUnspecified(maxBlockingTime))
      {
         // Fast-DDS refuses to create RELIABLE endpoints with an infinite max_blocking_time; use the largest finite duration.
         reliabilityQos.setMaxBlockingTime(ROS2QoSProfile.isInfinite(maxBlockingTime) ? DurationTypeTools.maxFinite() : toDurationType(maxBlockingTime));
         set = true;
      }

      return set ? reliabilityQos : null;
   }

   /**
    * Converts a non-negative {@link Duration} to a normalized DDS {@link DurationType}; durations of {@code 2^31 - 1} seconds or
    * more become {@code DURATION_INFINITY}.
    */
   static DurationType toDurationType(Duration duration)
   {
      if (ROS2QoSProfile.isInfinite(duration))
      {
         return DurationTypeTools.infinite();
      }
      return DurationTypeTools.of(duration.getSeconds(), duration.getNano());
   }

   private static DataSharingQosPolicyType dataSharingOff()
   {
      DataSharingQosPolicyType dataSharing = new DataSharingQosPolicyType();
      dataSharing.setKind("OFF");
      return dataSharing;
   }

   /**
    * @param typeHash a RIHS01 type hash, or null
    * @return the USER_DATA {@code typehash=<typeHash>;} as ASCII octets (no NUL), or null if {@code typeHash} is null
    * @throws IllegalArgumentException if the type hash contains characters that are not printable ASCII or ';'
    */
   static OctectVectorQosPolicyType typeHashUserData(String typeHash)
   {
      if (typeHash == null)
      {
         return null;
      }
      for (int i = 0; i < typeHash.length(); ++i)
      {
         char c = typeHash.charAt(i);
         if (c <= 0x20 || c >= 0x7f || c == ';' || c == '=')
         {
            throw new IllegalArgumentException("Invalid type hash for USER_DATA: '" + typeHash + "'");
         }
      }
      return OctetVectorTools.fromAscii(TYPE_HASH_USER_DATA_PREFIX + typeHash + ";", false);
   }
}
