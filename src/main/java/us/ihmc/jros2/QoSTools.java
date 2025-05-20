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

import jakarta.xml.bind.JAXBElement;
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.gen.DataReaderQosPoliciesType;
import us.ihmc.fastddsjava.profiles.gen.DataWriterQosPoliciesType;
import us.ihmc.fastddsjava.profiles.gen.DeadlineQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.DurabilityQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.DurationType;
import us.ihmc.fastddsjava.profiles.gen.LifespanQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.LivelinessQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.ReliabilityQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;

import javax.xml.namespace.QName;
import java.time.Duration;

final class QoSTools
{
   /**
    * Translate {@link ROS2QoSProfile} into a Fast-DDS {@link PublisherProfileType}.
    * Use when building a {@link ROS2Publisher}.
    */
   static void translateQoS(ROS2QoSProfile qosProfile, PublisherProfileType publisherProfile)
   {
      ReliabilityQosPolicyType reliabilityQosPolicyType = new ReliabilityQosPolicyType();
      DurabilityQosPolicyType durabilityQosPolicyType = new DurabilityQosPolicyType();
      DeadlineQosPolicyType deadlineQosPolicyType = new DeadlineQosPolicyType();
      LifespanQosPolicyType lifespanQosPolicyType = new LifespanQosPolicyType();
      LivelinessQosPolicyType livelinessQosPolicyType = new LivelinessQosPolicyType();

      // History type
      switch (qosProfile.getHistory())
      {
         case SYSTEM_DEFAULT, KEEP_LAST -> durabilityQosPolicyType.setKind("KEEP_LAST");
         case KEEP_ALL -> durabilityQosPolicyType.setKind("KEEP_ALL");
      }

      // TODO:
      // History depth
      //      durabilityServiceQosPolicyType.setHistoryDepth(qosProfile.getDepth());

      // Reliability
      switch (qosProfile.getReliability())
      {
         case SYSTEM_DEFAULT, RELIABLE -> reliabilityQosPolicyType.setKind("RELIABLE");
         case BEST_EFFORT -> reliabilityQosPolicyType.setKind("BEST_EFFORT");
      }

      // Durability
      switch (qosProfile.getDurability())
      {
         case SYSTEM_DEFAULT, VOLATILE -> durabilityQosPolicyType.setKind("VOLATILE");
         case TRANSIENT_LOCAL -> durabilityQosPolicyType.setKind("TRANSIENT_LOCAL");
      }

      // Deadline
      Duration deadline = qosProfile.getDeadline();
      DurationType deadlineDurationType = new DurationType();
      deadlineDurationType.getSecOrNanosec()
                          .add(new JAXBElement<>(new QName(ProfilesXML.FAST_DDS_NAMESPACE_URI, "nanosec"), String.class, Integer.toString(deadline.getNano())));
      deadlineDurationType.getSecOrNanosec()
                          .add(new JAXBElement<>(new QName(ProfilesXML.FAST_DDS_NAMESPACE_URI, "sec"), String.class, Long.toString(deadline.getSeconds())));
      deadlineQosPolicyType.setPeriod(deadlineDurationType);

      // Lifespan
      Duration lifespan = qosProfile.getLifespan();
      DurationType lifespanDurationType = new DurationType();
      lifespanDurationType.getSecOrNanosec()
                          .add(new JAXBElement<>(new QName(ProfilesXML.FAST_DDS_NAMESPACE_URI, "nanosec"), String.class, Integer.toString(lifespan.getNano())));
      lifespanDurationType.getSecOrNanosec()
                          .add(new JAXBElement<>(new QName(ProfilesXML.FAST_DDS_NAMESPACE_URI, "sec"), String.class, Long.toString(lifespan.getSeconds())));
      lifespanQosPolicyType.setDuration(lifespanDurationType);

      // Liveliness
      switch (qosProfile.getLiveliness())
      {
         case SYSTEM_DEFAULT, AUTOMATIC -> livelinessQosPolicyType.setKind("AUTOMATIC");
         case MANUAL_BY_TOPIC -> livelinessQosPolicyType.setKind("MANUAL_BY_TOPIC");
      }

      // Lease duration
      Duration leaseDuration = qosProfile.getLeaseDuration();
      DurationType leaseDurationType = new DurationType();
      leaseDurationType.getSecOrNanosec()
                       .add(new JAXBElement<>(new QName(ProfilesXML.FAST_DDS_NAMESPACE_URI, "nanosec"),
                                              String.class,
                                              Integer.toString(leaseDuration.getNano())));
      leaseDurationType.getSecOrNanosec()
                       .add(new JAXBElement<>(new QName(ProfilesXML.FAST_DDS_NAMESPACE_URI, "sec"), String.class, Long.toString(leaseDuration.getSeconds())));
      livelinessQosPolicyType.setLeaseDuration(leaseDurationType);

      DataWriterQosPoliciesType dataWriterQosPoliciesType = new DataWriterQosPoliciesType();
      dataWriterQosPoliciesType.setDurability(durabilityQosPolicyType);
      if (!deadline.isZero())
         dataWriterQosPoliciesType.setDeadline(deadlineQosPolicyType);
      if (!lifespan.isZero())
         dataWriterQosPoliciesType.setLifespan(lifespanQosPolicyType);
      dataWriterQosPoliciesType.setReliability(reliabilityQosPolicyType);
      if (!leaseDuration.isZero())
         dataWriterQosPoliciesType.setLiveliness(livelinessQosPolicyType);

      publisherProfile.setQos(dataWriterQosPoliciesType);
   }

   /**
    * Translate {@link ROS2QoSProfile} into a Fast-DDS {@link SubscriberProfileType}.
    * Use when building a {@link ROS2Subscription}.
    */
   static void translateQoS(ROS2QoSProfile qosProfile, SubscriberProfileType subscriberProfile)
   {
      ReliabilityQosPolicyType reliabilityQosPolicyType = new ReliabilityQosPolicyType();
      DurabilityQosPolicyType durabilityQosPolicyType = new DurabilityQosPolicyType();
      DeadlineQosPolicyType deadlineQosPolicyType = new DeadlineQosPolicyType();
      LifespanQosPolicyType lifespanQosPolicyType = new LifespanQosPolicyType();
      LivelinessQosPolicyType livelinessQosPolicyType = new LivelinessQosPolicyType();

      // History type
      switch (qosProfile.getHistory())
      {
         case SYSTEM_DEFAULT, KEEP_LAST -> durabilityQosPolicyType.setKind("KEEP_LAST");
         case KEEP_ALL -> durabilityQosPolicyType.setKind("KEEP_ALL");
      }

      // TODO:
      // History depth
      //      durabilityServiceQosPolicyType.setHistoryDepth(qosProfile.getDepth());

      // Reliability
      switch (qosProfile.getReliability())
      {
         case SYSTEM_DEFAULT, BEST_EFFORT -> reliabilityQosPolicyType.setKind("BEST_EFFORT");
         case RELIABLE -> reliabilityQosPolicyType.setKind("RELIABLE");
      }

      // Durability
      switch (qosProfile.getDurability())
      {
         case SYSTEM_DEFAULT, VOLATILE -> durabilityQosPolicyType.setKind("VOLATILE");
         case TRANSIENT_LOCAL -> durabilityQosPolicyType.setKind("TRANSIENT_LOCAL");
      }

      // Deadline
      Duration deadline = qosProfile.getDeadline();
      DurationType deadlineDurationType = new DurationType();
      deadlineDurationType.getSecOrNanosec()
                          .add(new JAXBElement<>(new QName(ProfilesXML.FAST_DDS_NAMESPACE_URI, "nanosec"), String.class, Integer.toString(deadline.getNano())));
      deadlineDurationType.getSecOrNanosec()
                          .add(new JAXBElement<>(new QName(ProfilesXML.FAST_DDS_NAMESPACE_URI, "sec"), String.class, Long.toString(deadline.getSeconds())));
      deadlineQosPolicyType.setPeriod(deadlineDurationType);

      // Lifespan
      Duration lifespan = qosProfile.getLifespan();
      DurationType lifespanDurationType = new DurationType();
      lifespanDurationType.getSecOrNanosec()
                          .add(new JAXBElement<>(new QName(ProfilesXML.FAST_DDS_NAMESPACE_URI, "nanosec"), String.class, Integer.toString(lifespan.getNano())));
      lifespanDurationType.getSecOrNanosec()
                          .add(new JAXBElement<>(new QName(ProfilesXML.FAST_DDS_NAMESPACE_URI, "sec"), String.class, Long.toString(lifespan.getSeconds())));
      lifespanQosPolicyType.setDuration(lifespanDurationType);

      // Liveliness
      switch (qosProfile.getLiveliness())
      {
         case SYSTEM_DEFAULT, AUTOMATIC -> livelinessQosPolicyType.setKind("AUTOMATIC");
         case MANUAL_BY_TOPIC -> livelinessQosPolicyType.setKind("MANUAL_BY_TOPIC");
      }

      // Lease duration
      Duration leaseDuration = qosProfile.getLeaseDuration();
      DurationType leaseDurationType = new DurationType();
      leaseDurationType.getSecOrNanosec()
                       .add(new JAXBElement<>(new QName(ProfilesXML.FAST_DDS_NAMESPACE_URI, "nanosec"),
                                              String.class,
                                              Integer.toString(leaseDuration.getNano())));
      leaseDurationType.getSecOrNanosec()
                       .add(new JAXBElement<>(new QName(ProfilesXML.FAST_DDS_NAMESPACE_URI, "sec"), String.class, Long.toString(leaseDuration.getSeconds())));
      livelinessQosPolicyType.setLeaseDuration(leaseDurationType);

      DataReaderQosPoliciesType dataReaderQosPoliciesType = new DataReaderQosPoliciesType();
      dataReaderQosPoliciesType.setDurability(durabilityQosPolicyType);
      if (!deadline.isZero())
         dataReaderQosPoliciesType.setDeadline(deadlineQosPolicyType);
      if (!lifespan.isZero())
         dataReaderQosPoliciesType.setLifespan(lifespanQosPolicyType);
      dataReaderQosPoliciesType.setReliability(reliabilityQosPolicyType);
      if (!leaseDuration.isZero())
         dataReaderQosPoliciesType.setLiveliness(livelinessQosPolicyType);

      subscriberProfile.setQos(dataReaderQosPoliciesType);
   }
}
