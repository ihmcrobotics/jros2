package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import us.ihmc.fastddsjava.fastddsjavaException;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapperType;
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps.UserTransports;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;
import us.ihmc.fastddsjava.profiles.gen.TopicProfileType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorListType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;
import us.ihmc.log.LogTools;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static us.ihmc.fastddsjava.fastddsjavaTools.retcodePrintOnError;
import static us.ihmc.fastddsjava.pointers.fastddsjava.*;

public class ROS2Node implements Closeable
{
   static
   {
      fastddsjavaNativeLibrary.load();
   }

   private final Pointer fastddsParticipant;
   private final Map<ROS2Topic<?>, ROS2TopicData> topicData;
   private final List<ROS2Publisher<?>> publishers;
   private final List<ROS2Subscription<?>> subscriptions;

   protected ROS2Node(String name, int domainId, TransportDescriptorType... transports)
   {
      ProfilesXML profilesXML = new ProfilesXML();

      ParticipantProfileType participantProfile = new ParticipantProfileType();
      String participantProfileName = UUID.randomUUID().toString();
      participantProfile.setDomainId(domainId);
      participantProfile.setProfileName(participantProfileName);
      Rtps rtps = new Rtps();
      rtps.setName(name);

      boolean useCustomTransports = transports != null && transports.length != 0;
      rtps.setUseBuiltinTransports(!useCustomTransports);
      if (useCustomTransports)
      {
         rtps.setUseBuiltinTransports(false);
         TransportDescriptorListType transportDescriptorListType = new TransportDescriptorListType();
         for (TransportDescriptorType transport : transports)
            transportDescriptorListType.getTransportDescriptor().add(transport);
         profilesXML.addTransportDescriptorsProfile(transportDescriptorListType);

         ParticipantProfileType.Rtps.UserTransports userTransports = new UserTransports();
         for (TransportDescriptorType transport : transports)
            userTransports.getTransportId().add(transport.getTransportId());
         rtps.setUserTransports(userTransports);
      }

      participantProfile.setRtps(rtps);
      profilesXML.addParticipantProfile(participantProfile);

      try
      {
         profilesXML.load();
      }
      catch (fastddsjavaException e)
      {
         LogTools.error(e);
      }

      fastddsParticipant = fastddsjava_create_participant(participantProfileName);
      topicData = new HashMap<>();
      publishers = new ArrayList<>();
      subscriptions = new ArrayList<>();
   }

   public ROS2Node(String name)
   {
      this(name, 0);
   }

   public ROS2Node(String name, int domainId)
   {
      this(name, domainId, (TransportDescriptorType[]) null);
   }

   protected <T extends ROS2Message<T>> ROS2TopicData getOrCreateTopicData(ROS2Topic<T> topic)
   {
      synchronized (this.topicData)
      {
         if (this.topicData.containsKey(topic))
            return this.topicData.get(topic);

         ProfilesXML profilesXML = new ProfilesXML();
         TopicProfileType topicProfile = new TopicProfileType();
         String topicProfileName = UUID.randomUUID().toString();
         topicProfile.setProfileName(topicProfileName);
         profilesXML.addTopicProfile(topicProfile);

         try
         {
            profilesXML.load();
         }
         catch (fastddsjavaException e)
         {
            LogTools.error(e);
         }

         String topicTypeName = ROS2Message.getNameFromMessageClass(topic.topicType());
         fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType(topicTypeName, CDR_LE);
         Pointer fastddsTypeSupport = fastddsjava_create_typesupport(topicDataWrapperType);
         fastddsjava_register_type(fastddsParticipant, fastddsTypeSupport);
         Pointer fastddsTopic = fastddsjava_create_topic(fastddsParticipant, topicDataWrapperType, topic.topicName(), topicProfileName);
         ROS2TopicData topicData = new ROS2TopicData(topicDataWrapperType, fastddsTypeSupport, fastddsTopic);

         this.topicData.put(topic, topicData);

         return topicData;
      }
   }

   public <T extends ROS2Message<T>> ROS2Publisher<T> createPublisher(ROS2Topic<T> topic, ROS2QoSProfile qosProfile)
   {
      ProfilesXML profilesXML = new ProfilesXML();
      PublisherProfileType publisherProfile = new PublisherProfileType();
      String publisherProfileName = UUID.randomUUID().toString();
      publisherProfile.setProfileName(publisherProfileName);
      profilesXML.addPublisherProfile(publisherProfile);

      // Translate the ROS2QoSProfile into Fast-DDS publisher profile XML
      QoSTools.translateQoS(qosProfile, publisherProfile);

      try
      {
         profilesXML.load();
      }
      catch (fastddsjavaException e)
      {
         LogTools.error(e);
      }

      ROS2TopicData topicData = getOrCreateTopicData(topic);
      ROS2Publisher<T> publisher = new ROS2Publisher<>(fastddsParticipant, publisherProfileName, topicData);
      synchronized (publishers)
      {
         publishers.add(publisher);
      }

      return publisher;
   }

   public <T extends ROS2Message<T>> boolean destroyPublisher(ROS2Publisher<T> publisher)
   {
      final boolean removed;
      synchronized (publishers)
      {
         removed = publishers.remove(publisher);
      }
      publisher.close(fastddsParticipant);
      return removed;
   }

   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic, ROS2SubscriptionCallback<T> callback, ROS2QoSProfile qosProfile)
   {
      ProfilesXML profilesXML = new ProfilesXML();
      SubscriberProfileType subscriberProfile = new SubscriberProfileType();
      String subscriberProfileName = UUID.randomUUID().toString();
      subscriberProfile.setProfileName(subscriberProfileName);
      profilesXML.addSubscriberProfile(subscriberProfile);

      // Translate the ROS2QoSProfile into Fast-DDS subscriber profile XML
      QoSTools.translateQoS(qosProfile, subscriberProfile);

      try
      {
         profilesXML.load();
      }
      catch (fastddsjavaException e)
      {
         LogTools.error(e);
      }

      ROS2TopicData topicData = getOrCreateTopicData(topic);
      ROS2Subscription<T> subscription = new ROS2Subscription<>(fastddsParticipant, subscriberProfileName, callback, topicData);
      synchronized (subscriptions)
      {
         subscriptions.add(subscription);
      }

      return subscription;
   }

   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic, Consumer<T> callback, ROS2QoSProfile qosProfile)
   {
      return null;
   }

   public <T extends ROS2Message<T>> boolean destroySubscription(ROS2Subscription<T> subscription)
   {
      final boolean removed;
      synchronized (subscriptions)
      {
         removed = subscriptions.remove(subscription);
      }
      subscription.close(fastddsParticipant);
      return removed;
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

   public List<ROS2Publisher<?>> getPublishers()
   {
      return Collections.unmodifiableList(publishers);
   }

   public List<ROS2Subscription<?>> getSubscriptions()
   {
      return Collections.unmodifiableList(subscriptions);
   }

   @Override
   public synchronized void close()
   {
      if (!isClosed())
      {
         for (ROS2Topic<?> topic : topicData.keySet())
         {
            ROS2TopicData topicData = this.topicData.get(topic);

            retcodePrintOnError(fastddsjava_delete_topic(fastddsParticipant, topicData.fastddsTopic));
            retcodePrintOnError(fastddsjava_unregister_type(fastddsParticipant, topicData.topicDataWrapperType.get_name()));

            topicData.topicDataWrapperType.close();
            topicData.fastddsTypeSupport.close();
         }

         retcodePrintOnError(fastddsjava_delete_participant(fastddsParticipant));

         fastddsParticipant.setNull();
      }
   }

   public boolean isClosed()
   {
      return fastddsParticipant.isNull();
   }
}
