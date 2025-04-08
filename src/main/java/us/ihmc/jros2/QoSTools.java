package us.ihmc.jros2;

import us.ihmc.fastddsjava.profiles.gen.DurabilityServiceQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.HistoryQosKindPolicyType;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.ReliabilityQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;

final class QoSTools
{
   protected static void translateQoS(ROS2QoSProfile qosProfile, PublisherProfileType publisherProfile)
   {

   }

   protected static void translateQoS(ROS2QoSProfile qoSProfile, SubscriberProfileType subscriberProfile)
   {
      DurabilityServiceQosPolicyType durabilityServiceQosPolicyType = new DurabilityServiceQosPolicyType();
      ReliabilityQosPolicyType reliabilityQosPolicyType = new ReliabilityQosPolicyType();

      // History type
      switch (qoSProfile.getHistory())
      {
         case KEEP_LAST -> durabilityServiceQosPolicyType.setHistoryKind(HistoryQosKindPolicyType.KEEP_LAST);
         case KEEP_ALL -> durabilityServiceQosPolicyType.setHistoryKind(HistoryQosKindPolicyType.KEEP_ALL);
      }

      // History depth
      durabilityServiceQosPolicyType.setHistoryDepth(qoSProfile.getDepth());

      // Reliability
      switch (qoSProfile.getReliability())
      {
         case BEST_EFFORT -> reliabilityQosPolicyType.setKind("BEST_EFFORT");
      }



   }
}
