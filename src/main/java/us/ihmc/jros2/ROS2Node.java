package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import us.ihmc.fastddsjava.fastddsjavaException;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapperType;
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps.UserTransports;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.TopicProfileType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorListType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static us.ihmc.fastddsjava.pointers.fastddsjava.*;

public class ROS2Node implements Closeable
{
   private final Pointer fastddsParticipant;
   private final Map<ROS2Topic<?>, ROS2TopicData> topicData = new HashMap<>();
   private final List<ROS2Publisher> publishers = new ArrayList<>();
   private final List<ROS2Subscription> subscriptions = new ArrayList<>();

   public ROS2Node()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      // Add transports
      TransportDescriptorListType transportDescriptorListType = new TransportDescriptorListType();
      TransportDescriptorType udp4Transport = new TransportDescriptorType();
      udp4Transport.setTransportId(UUID.randomUUID().toString());
      udp4Transport.setType("UDPv4");
      transportDescriptorListType.getTransportDescriptor().add(udp4Transport);
      TransportDescriptorType shmTransport = new TransportDescriptorType();
      shmTransport.setTransportId(UUID.randomUUID().toString());
      shmTransport.setType("SHM");
      transportDescriptorListType.getTransportDescriptor().add(shmTransport);
      profilesXML.addTransportDescriptorsProfile(transportDescriptorListType);

      // Add profile
      ParticipantProfileType participantProfile = new ParticipantProfileType();
      String participantProfileName = UUID.randomUUID().toString();
      participantProfile.setDomainId(113); // TODO:
      participantProfile.setProfileName(participantProfileName);
      Rtps rtps = new Rtps();
      rtps.setName("test_node");
      rtps.setUseBuiltinTransports(false);
      ParticipantProfileType.Rtps.UserTransports userTransports = new UserTransports();
      userTransports.getTransportId().add(udp4Transport.getTransportId());
      userTransports.getTransportId().add(shmTransport.getTransportId());
      rtps.setUserTransports(userTransports);
      participantProfile.setRtps(rtps);
      profilesXML.addParticipantProfile(participantProfile);

      // TODO: keep try-catch?
      try
      {
         profilesXML.load();
      }
      catch (fastddsjavaException e)
      {
         throw new RuntimeException(e);
      }

      fastddsParticipant = fastddsjava_create_participant(participantProfileName);
   }

   protected <T extends ROS2Message<T>> ROS2TopicData getOrCreateTopicData(ROS2Topic<T> topic)
   {
      synchronized (this.topicData)
      {
         if (this.topicData.containsKey(topic))
            return this.topicData.get(topic);
      }

      ProfilesXML profilesXML = new ProfilesXML();
      TopicProfileType topicProfile = new TopicProfileType();
      String topicProfileName = UUID.randomUUID().toString();
      topicProfile.setProfileName(topicProfileName);
      profilesXML.addTopicProfile(topicProfile);

      // TODO: keep try-catch?
      try
      {
         profilesXML.load();
      }
      catch (fastddsjavaException e)
      {
         throw new RuntimeException(e);
      }

      String topicTypeName = ROS2Message.getNameFromMessageClass(topic.topicType());
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType(topicTypeName, CDR_LE);
      Pointer fastddsTypeSupport = fastddsjava_create_typesupport(topicDataWrapperType);
      fastddsjava_register_type(fastddsParticipant, fastddsTypeSupport);
      Pointer fastddsTopic = fastddsjava_create_topic(fastddsParticipant, topicDataWrapperType, topic.topicName(), topicProfileName);
      ROS2TopicData topicData = new ROS2TopicData(topicDataWrapperType, fastddsTypeSupport, fastddsTopic);
      synchronized (this.topicData)
      {
         this.topicData.put(topic, topicData);
      }

      return topicData;
   }

   public <T extends ROS2Message<T>> ROS2Publisher createPublisher(ROS2Topic<T> topic, ROS2QoSProfile qosProfile)
   {
      ProfilesXML profilesXML = new ProfilesXML();
      PublisherProfileType publisherProfile = new PublisherProfileType();
      String publisherProfileName = UUID.randomUUID().toString();
      publisherProfile.setProfileName(publisherProfileName);
      profilesXML.addPublisherProfile(publisherProfile);

      // TODO: translate qosProfile into profilesXML

      // TODO: keep try-catch?
      try
      {
         profilesXML.load();
      }
      catch (fastddsjavaException e)
      {
         throw new RuntimeException(e);
      }

      ROS2TopicData topicData = getOrCreateTopicData(topic);
      Pointer fastddsPublisher = fastddsjava_create_publisher(fastddsParticipant, publisherProfileName);

      ROS2Publisher publisher = new ROS2Publisher(fastddsPublisher, publisherProfileName, topicData);
      synchronized (publishers)
      {
         publishers.add(publisher);
      }

      return publisher;
   }

   public ROS2Subscription createSubscription(Class<?> topicType, String topicName, ROS2SubscriptionCallback callback, ROS2QoSProfile qosProfile)
   {
      // TODO:
      return null;
   }

   public ROS2Service createService(Class<?> serviceType, String serviceName, Object callback)
   {
      // TODO:
      return null;
   }

   public Object declareParameter(String name, Object value)
   {
      // TODO:
      return null;
   }

   public Object getParameter(String name)
   {
      // TODO:
      return null;
   }

   public List<ROS2Publisher> getPublishers()
   {
      return Collections.unmodifiableList(publishers);
   }

   public List<ROS2Subscription> getSubscriptions()
   {
      return Collections.unmodifiableList(subscriptions);
   }

   @Override
   public void close()
   {
      for (ROS2Topic<?> topic : topicData.keySet())
      {
         ROS2TopicData topicData = this.topicData.get(topic);

         // TODO: Release topicData
      }

      fastddsjava_delete_participant(fastddsParticipant);
   }
}
