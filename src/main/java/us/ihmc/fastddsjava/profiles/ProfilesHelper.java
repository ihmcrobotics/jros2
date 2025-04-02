package us.ihmc.fastddsjava.profiles;

import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps.UserTransports;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;
import us.ihmc.fastddsjava.profiles.gen.TopicProfileType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorListType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;

import java.util.UUID;

public final class ProfilesHelper
{
   public static ProfilesXML defaultProfile(int domainId)
   {
      return null;
   }

   public static ProfilesXML unitTestProfile()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Add transport
      TransportDescriptorListType transportDescriptorListType = new TransportDescriptorListType();
      TransportDescriptorType udp4Transport = new TransportDescriptorType();
      udp4Transport.setTransportId(UUID.randomUUID().toString());
      udp4Transport.setType("UDPv4");
      transportDescriptorListType.getTransportDescriptor().add(udp4Transport);
      profilesXML.addTransportDescriptorsProfile(transportDescriptorListType);

      // Add participant profile
      ParticipantProfileType participantProfileType = new ParticipantProfileType();

      Rtps rtps = new Rtps();
      rtps.setUseBuiltinTransports(true);
      ParticipantProfileType.Rtps.UserTransports userTransports = new UserTransports();
      userTransports.getTransportId().add(udp4Transport.getTransportId());
      rtps.setUserTransports(userTransports);
      participantProfileType.setRtps(rtps);

      participantProfileType.setProfileName("example_participant");
      participantProfileType.setDomainId(145);
      profilesXML.addParticipantProfile(participantProfileType);

      // Add topic profile
      TopicProfileType topicProfileType = new TopicProfileType();
      topicProfileType.setProfileName("example_topic");
      profilesXML.addTopicProfile(topicProfileType);

      // Add publisher profile / AKA data writer profile
      PublisherProfileType publisherProfileType = new PublisherProfileType();
      publisherProfileType.setProfileName("example_publisher");
      profilesXML.addPublisherProfile(publisherProfileType);

      // Add subscriber profile / AKA data reader profile
      SubscriberProfileType subscriberProfileType = new SubscriberProfileType();
      subscriberProfileType.setProfileName("example_subscriber");
      profilesXML.addSubscriberProfile(subscriberProfileType);

      return profilesXML;
   }
}
