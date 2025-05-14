package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import us.ihmc.fastddsjava.fastddsjavaException;
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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static us.ihmc.fastddsjava.fastddsjavaTools.retcodePrintOnError;
import static us.ihmc.fastddsjava.pointers.fastddsjava.*;

/**
 * A ROS 2-compatible node which provides functionality for managing ROS 2-compatible publishers, subscriptions.
 * Uses Fast-DDS middleware via the {@link us.ihmc.fastddsjava} package. Fully thread-safe.
 */
public class ROS2Node implements Closeable
{
   static
   {
      jros2.load();
   }

   /**
    * A colloquial name for the node, not used internally.
    */
   private final String name;

   /**
    * The domain ID the node will use when writing and reading to the network transport. A valid domain ID must
    * be within the range [0, 232].
    * <p>
    * See: <a href="https://fast-dds.docs.eprosima.com/en/v3.2.2/fastdds/dds_layer/domain/domain.html">Domain</a>
    * See: <a href="https://docs.ros.org/en/humble/Concepts/Intermediate/About-Domain-ID.html">ROS 2 Domain ID</a>
    */
   private final int domainId;

   /**
    * Pointer to a Fast-DDS participant used by this node in native memory. For internal use only.
    */
   protected final Pointer fastddsParticipant;

   /**
    * A helper map linking {@link ROS2Topic}\s to {@link TopicData}, where TopicData is a set of Fast-DDS pointers required
    * for creating and using a topic. For internal use only.
    */
   private final Map<ROS2Topic<?>, TopicData> topicData;

   /**
    * A list of {@link ROS2Publisher}\s managed by this node.
    */
   protected final List<ROS2Publisher<?>> publishers;

   /**
    * A list of {@link ROS2Subscription}\s managed by this node.
    */
   private final List<ROS2Subscription<?>> subscriptions;

   protected final ReadWriteLock closeLock;
   protected boolean closed;

   protected ROS2Node(String name, int domainId, TransportDescriptorType... transports)
   {
      if (name == null)
      {
         throw new NullPointerException("name cannot be null when constructing a ROS2Node");
      }
      this.name = name;

      if (domainId < 0 || domainId > 232)
      {
         throw new RuntimeException("Invalid domain ID used when constructing a ROS2Node (" + domainId + ")");
      }
      this.domainId = domainId;

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
         for (int i = 0; i < transports.length; ++i)
         {
            transportDescriptorListType.getTransportDescriptor().add(transports[i]);
         }
         profilesXML.addTransportDescriptorsProfile(transportDescriptorListType);

         ParticipantProfileType.Rtps.UserTransports userTransports = new UserTransports();
         for (int i = 0; i < transports.length; ++i)
         {
            userTransports.getTransportId().add(transports[i].getTransportId());
         }
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

      closeLock = new ReentrantReadWriteLock(true);
      closed = false;
   }

   public ROS2Node(String name)
   {
      this(name, jros2.get().rosDomainId());
   }

   public ROS2Node(String name, int domainId)
   {
      this(name, domainId, (TransportDescriptorType[]) null);
   }

   protected <T extends ROS2Message<T>> TopicData getOrCreateTopicData(ROS2Topic<T> topic)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            synchronized (this.topicData)
            {
               if (this.topicData.containsKey(topic))
               {
                  return this.topicData.get(topic);
               }
               else
               {
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

                  /*
                   * All ROS topics are prefixed with {@code rX} to create the DDS topic name,
                   * where {@code X} is determined by the subtype of the topic.
                   * See "Mapping of ROS 2 Topic and Service Names to DDS Concepts" section of
                   * https://design.ros2.org/articles/topic_and_service_names.html
                   */
                  // TODO: Support other prefixes depending on ROS subsystem
                  String prefixedTopicName = "rt" + topic.getName();
                  String topicTypeName = ROS2Message.getNameFromMessageClass(topic.getType());
                  fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType(topicTypeName, CDR_LE);
                  Pointer fastddsTypeSupport = fastddsjava_create_typesupport(topicDataWrapperType);
                  fastddsjava_register_type(fastddsParticipant, fastddsTypeSupport);
                  Pointer fastddsTopic = fastddsjava_create_topic(fastddsParticipant, topicDataWrapperType, prefixedTopicName, topicProfileName);
                  TopicData topicData = new TopicData(topicDataWrapperType, fastddsTypeSupport, fastddsTopic);

                  this.topicData.put(topic, topicData);

                  return topicData;
               }
            }
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return null;
   }

   public <T extends ROS2Message<T>> ROS2Publisher<T> createPublisher(ROS2Topic<T> topic, ROS2QoSProfile qosProfile)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
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

            TopicData topicData = getOrCreateTopicData(topic);
            ROS2Publisher<T> publisher = new ROS2Publisher<>(fastddsParticipant, publisherProfileName, topicData);

            synchronized (publishers)
            {
               publishers.add(publisher);
            }

            return publisher;
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return null;
   }

   public <T extends ROS2Message<T>> ROS2Publisher<T> createPublisher(ROS2Topic<T> topic)
   {
      return createPublisher(topic, ROS2QoSProfile.DEFAULT);
   }

   public boolean destroyPublisher(ROS2Publisher<?> publisher)
   {
      boolean removed = false;

      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            synchronized (publishers)
            {
               removed = publishers.remove(publisher);
            }

            if (removed)
            {
               publisher.close(fastddsParticipant);
            }
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return removed;
   }

   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic, ROS2SubscriptionCallback<T> callback, ROS2QoSProfile qosProfile)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
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

            TopicData topicData = getOrCreateTopicData(topic);
            ROS2Subscription<T> subscription = new ROS2Subscription<>(fastddsParticipant, subscriberProfileName, callback, topic, topicData);

            synchronized (subscriptions)
            {
               subscriptions.add(subscription);
            }

            return subscription;
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return null;
   }

   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic, ROS2SubscriptionCallback<T> callback)
   {
      return createSubscription(topic, callback, ROS2QoSProfile.DEFAULT);
   }

   public boolean destroySubscription(ROS2Subscription<?> subscription)
   {
      boolean removed = false;

      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            synchronized (subscriptions)
            {
               removed = subscriptions.remove(subscription);
            }

            if (removed)
            {
               subscription.close(fastddsParticipant);
            }
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return removed;
   }

   public Object createService(Class<?> serviceType, String serviceName, Object callback)
   {
      throw new RuntimeException("Not yet implemented");
   }

   public Object declareParameter(String name, Object value)
   {
      throw new RuntimeException("Not yet implemented");
   }

   public Object getParameter(String name)
   {
      throw new RuntimeException("Not yet implemented");
   }

   public String getName()
   {
      return name;
   }

   public int getDomainId()
   {
      return domainId;
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
   public void close()
   {
      // Wait until all readers are finished, then start closing
      closeLock.writeLock().lock();
      boolean wasClosed = closed;
      closed = true;
      closeLock.writeLock().unlock();

      if (!wasClosed)
      {
         synchronized (publishers)
         {
            // Delete publishers
            for (ROS2Publisher<?> publisher : publishers)
            {
               publisher.close(fastddsParticipant);
            }
            publishers.clear();
         }

         synchronized (subscriptions)
         {
            // Delete subscriptions
            for (ROS2Subscription<?> subscription : subscriptions)
            {
               subscription.close(fastddsParticipant);
            }
            subscriptions.clear();
         }

         synchronized (topicData)
         {
            // Delete topics
            for (ROS2Topic<?> topic : topicData.keySet())
            {
               TopicData topicData = this.topicData.get(topic);

               retcodePrintOnError(fastddsjava_delete_topic(fastddsParticipant, topicData.fastddsTopic));
               retcodePrintOnError(fastddsjava_unregister_type(fastddsParticipant, topicData.topicDataWrapperType.get_name()));

               topicData.topicDataWrapperType.close();
               topicData.fastddsTypeSupport.close();
            }
            topicData.clear();
         }

         // Delete participant
         retcodePrintOnError(fastddsjava_delete_participant(fastddsParticipant));
      }
   }
}
