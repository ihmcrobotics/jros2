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

import org.bytedeco.javacpp.Pointer;
import us.ihmc.fastddsjava.fastddsjavaException;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapperType;
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.TransportDescriptorTypeTools;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps.UserTransports;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;
import us.ihmc.fastddsjava.profiles.gen.TopicProfileType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorListType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static us.ihmc.fastddsjava.fastddsjavaTools.retcodePrintOnError;
import static us.ihmc.fastddsjava.pointers.fastddsjava.*;

/**
 * A ROS 2-compatible node which provides functionality for managing ROS 2-compatible publishers, subscriptions,
 * services, actions, and parameters.
 * Uses Fast-DDS middleware via the {@link us.ihmc.fastddsjava} package. Fully thread-safe.
 */
public class ROS2Node implements Closeable
{
   static
   {
      jros2.load();
   }

   /*
    * Atomic counters for garbage-free ID generation
    */
   private static final AtomicLong participantIdCounter = new AtomicLong(0);
   private static final AtomicLong topicIdCounter = new AtomicLong(0);
   private static final AtomicLong publisherIdCounter = new AtomicLong(0);
   private static final AtomicLong subscriberIdCounter = new AtomicLong(0);

   /*
    * Node identification
    */
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

   /*
    * Fast-DDS pointers
    */
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
   /**
    * A list of {@link ROS2ServiceClient}\s managed by this node.
    */
   private final List<ROS2ServiceClient<?, ?>> serviceClients;
   /**
    * A list of {@link ROS2ServiceServer}\s managed by this node.
    */
   private final List<ROS2ServiceServer<?, ?>> serviceServers;
   /**
    * A list of {@link ROS2ActionClient}\s managed by this node.
    */
   private final List<ROS2ActionClient<?, ?, ?>> actionClients;
   /**
    * A list of {@link ROS2ActionServer}\s managed by this node.
    */
   private final List<ROS2ActionServer<?, ?, ?>> actionServers;
   /**
    * A list of {@link ROS2ParameterClient}\s managed by this node.
    */
   private final List<ROS2ParameterClient> parameterClients;
   /**
    * A map of parameters managed by this node (name -> parameter).
    */
   private final Map<String, ROS2Parameter> parameters;
   /**
    * Discovery publisher for ROS2 discovery protocol.
    */
   private final ROS2DiscoveryPublisher discoveryPublisher;
   /**
    * Publisher for parameter events (publishes to /parameter_events topic).
    */
   private ROS2Publisher<rcl_interfaces.ParameterEvent> parameterEventPublisher;
   /**
    * Parameter service manager (creates 6 parameter services for remote access).
    */
   private ROS2ParameterService parameterService;

   /*
    * Locks
    */
   protected final ReadWriteLock closeLock;
   protected boolean closed;

   /**
    * Create a new ROS 2 Node for managing ROS 2-compatible publishers, subscriptions.
    *
    * @param name              The colloquial name for the node, not used internally.
    * @param domainId          The domain ID the node will use when writing and reading to the network transport.
    * @param fastddsTransports An optional list of Transports to enable. These define what network protocol and network parameters to use when communicating
    *                          over the network. See: {@link TransportDescriptorTypeTools}.
    */
   public ROS2Node(String name, int domainId, TransportDescriptorType... fastddsTransports)
   {
      if (name == null || name.isEmpty())
      {
         throw new IllegalArgumentException("name cannot be null or empty when constructing a ROS2Node");
      }
      this.name = name;

      if (domainId < 0 || domainId > 232)
      {
         throw new IllegalArgumentException("Invalid domain ID used when constructing a ROS2Node (" + domainId + ")");
      }
      this.domainId = domainId;

      // Configure transports based on custom transports and interface whitelist
      TransportDescriptorTypeTools.TransportConfiguration transportConfig = TransportDescriptorTypeTools.configureTransports(fastddsTransports,
                                                                                                                             jros2.get().interfaceWhitelist());

      ProfilesXML profilesXML = new ProfilesXML();

      ParticipantProfileType participantProfile = new ParticipantProfileType();
      // Prefix with "p_" to ensure valid XML identifier
      long participantId = participantIdCounter.getAndIncrement();
      String participantProfileName = "p_" + participantId;
      participantProfile.setDomainId(domainId);
      participantProfile.setProfileName(participantProfileName);
      Rtps rtps = new Rtps();
      rtps.setName(name);

      // Configure RTPS transports
      rtps.setUseBuiltinTransports(transportConfig.shouldUseBuiltinTransports());

      if (!transportConfig.shouldUseBuiltinTransports())
      {
         TransportDescriptorType[] transports = transportConfig.getTransports();

         // Only add transports to XML profile if they haven't been added before (to avoid duplicate transport IDs)
         if (transportConfig.shouldAddToXml())
         {
            TransportDescriptorListType transportDescriptorListType = new TransportDescriptorListType();
            for (TransportDescriptorType transport : transports)
            {
               transportDescriptorListType.getTransportDescriptor().add(transport);
            }
            profilesXML.addTransportDescriptorsProfile(transportDescriptorListType);
         }

         ParticipantProfileType.Rtps.UserTransports userTransports = new UserTransports();
         for (TransportDescriptorType transport : transports)
         {
            userTransports.getTransportId().add(transport.getTransportId());
         }
         rtps.setUserTransports(userTransports);
      }

      checkSHMAvailabilityWindows(rtps, transportConfig.getTransports());

      participantProfile.setRtps(rtps);
      profilesXML.addParticipantProfile(participantProfile);

      ROS2NodePrintout.print(getClass(), participantProfile, transportConfig.getTransports());

      try
      {
         profilesXML.load();
      }
      catch (fastddsjavaException e)
      {
         jros2.logError("Failed to load participant profile '" + participantProfileName + "' for node '" + name + "'", e);
         throw new RuntimeException("Failed to load participant profile: " + participantProfileName, e);
      }

      fastddsParticipant = fastddsjava_create_participant(participantProfileName);
      topicData = new HashMap<>();
      publishers = new ArrayList<>();
      subscriptions = new ArrayList<>();
      serviceClients = new ArrayList<>();
      serviceServers = new ArrayList<>();
      actionClients = new ArrayList<>();
      actionServers = new ArrayList<>();
      parameterClients = new ArrayList<>();
      parameters = new ConcurrentHashMap<>();

      closeLock = new ReentrantReadWriteLock(true);
      closed = false;

      // Parameter event publisher is created lazily on first parameter operation.
      // Tracked separately and cleaned up in close().
      parameterEventPublisher = null;

      // Parameter services are created lazily when first parameter is declared.
      parameterService = null;

      // Discovery publisher for ROS2 discovery protocol.
      // Periodic publishing starts automatically after 2 seconds.
      discoveryPublisher = new ROS2DiscoveryPublisher(this, fastddsParticipant, name, "/");
   }

   /**
    * Ensures parameter services are initialized for remote parameter access.
    * Called lazily when the first parameter is declared.
    */
   private void ensureParameterServicesInitialized()
   {
      if (parameterService == null && !closed)
      {
         synchronized (this)
         {
            if (parameterService == null && !closed)
            {
               parameterService = new ROS2ParameterService(this);
            }
         }
      }
   }

   /**
    * Create a new ROS 2 Node for managing ROS 2-compatible publishers, subscriptions.
    *
    * @param name The colloquial name for the node, not used internally.
    */
   public ROS2Node(String name)
   {
      this(name, jros2.get().rosDomainId());
   }

   /**
    * Create a new ROS 2 Node for managing ROS 2-compatible publishers, subscriptions.
    *
    * @param name     The colloquial name for the node, not used internally.
    * @param domainId The domain ID the node will use when writing and reading to the network transport.
    */
   public ROS2Node(String name, int domainId)
   {
      this(name, domainId, (TransportDescriptorType[]) null);
   }

   /*
    * For managing native Fast-DDS topic memory. For internal-use only.
    */
   <T extends ROS2Message<T>> TopicData getOrCreateTopicData(ROS2Topic<T> topic)
   {
      // Detect service topics by checking if message type ends with "_Request" or "_Response"
      // and use appropriate DDS topic prefix: rq for requests, rr for replies, rt for regular topics
      String messageTypeName = topic.getType().getSimpleName();
      String prefix;
      if (messageTypeName.endsWith("_Request"))
      {
         prefix = "rq";
      }
      else if (messageTypeName.endsWith("_Response"))
      {
         prefix = "rr";
      }
      else if (messageTypeName.equals("ParticipantEntitiesInfo"))
      {
         // Discovery topic uses no prefix (avoid_ros_namespace_conventions = true)
         prefix = "";
      }
      else
      {
         prefix = "rt";
      }
      return getOrCreateTopicData(topic, prefix);
   }

   <T extends ROS2Message<T>> TopicData getOrCreateTopicData(ROS2Topic<T> topic, String prefix)
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
                  // Prefix with "t_" to ensure valid XML identifier
                  long topicId = topicIdCounter.getAndIncrement();
                  String topicProfileName = "t_" + topicId;
                  topicProfile.setProfileName(topicProfileName);
                  profilesXML.addTopicProfile(topicProfile);

                  try
                  {
                     profilesXML.load();
                  }
                  catch (fastddsjavaException e)
                  {
                     jros2.logError("Failed to load topic profile '" + topicProfileName + "' for topic '" + topic.getName() + "'", e);
                     throw new RuntimeException("Failed to load topic profile: " + topicProfileName, e);
                  }

                  /*
                   * All ROS topics are prefixed with {@code rX} to create the DDS topic name,
                   * where {@code X} is determined by the subtype of the topic.
                   * See "Mapping of ROS 2 Topic and Service Names to DDS Concepts" section of
                   * https://design.ros2.org/articles/topic_and_service_names.html
                   * rt = ROS topic, rq = service request, rr = service reply
                   *
                   * Service topics must also have the appropriate suffix:
                   * - Request topics: rq/<service_name>Request
                   * - Response topics: rr/<service_name>Reply
                   */
                  String messageTypeName = topic.getType().getSimpleName();
                  String suffix = "";
                  if (messageTypeName.endsWith("_Request"))
                  {
                     suffix = "Request";
                  }
                  else if (messageTypeName.endsWith("_Response"))
                  {
                     suffix = "Reply";
                  }

                  // Use concat method to avoid string allocation on hot path (though this still allocates)
                  String prefixedTopicName = prefix.concat(topic.getName()).concat(suffix);
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

   /**
    * Create a publisher to publish {@param T} message type for subscriptions to receive.
    *
    * @param topic      the ROS 2 topic, (see {@link ROS2Topic} for how to use.
    * @param qosProfile specify what quality-of-service settings you want for this publisher. Note: publisher and subscription QoS must match if you want them
    *                   to communicate.
    * @return the publisher instance, you do not have to store this as a field or manage it in any way if you don't need to.
    */
   public <T extends ROS2Message<T>> ROS2Publisher<T> createPublisher(ROS2Topic<T> topic, ROS2QoSProfile qosProfile)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            ProfilesXML profilesXML = new ProfilesXML();
            PublisherProfileType publisherProfile = new PublisherProfileType();
            // Prefix with "pub_" to ensure valid XML identifier
            long publisherId = publisherIdCounter.getAndIncrement();
            String publisherProfileName = "pub_" + publisherId;
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
               jros2.logError("Failed to load publisher profile '" + publisherProfileName + "' for topic '" + topic.getName() + "'", e);
               throw new RuntimeException("Failed to load publisher profile: " + publisherProfileName, e);
            }

            TopicData topicData = getOrCreateTopicData(topic);
            ROS2Publisher<T> publisher = new ROS2Publisher<>(fastddsParticipant, publisherProfileName, topic, topicData);

            synchronized (publishers)
            {
               publishers.add(publisher);
            }

            // Notify discovery publisher
            if (!topic.getName().equals("ros_discovery_info"))
            {
               discoveryPublisher.addWriter(publisher.fastddsDataWriter);
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

   /**
    * Package-private method to create a publisher with RMW-compatible options.
    * Used by ROS2DiscoveryPublisher to enable Python interop.
    */
   <T extends ROS2Message<T>> ROS2Publisher<T> createPublisherWithRmwOptions(ROS2Topic<T> topic, ROS2QoSProfile qosProfile)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            ProfilesXML profilesXML = new ProfilesXML();
            PublisherProfileType publisherProfile = new PublisherProfileType();
            // Prefix with "pub_" to ensure valid XML identifier
            long publisherId = publisherIdCounter.getAndIncrement();
            String publisherProfileName = "pub_" + publisherId;
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
               jros2.logError("Failed to load publisher profile '" + publisherProfileName + "' for topic '" + topic.getName() + "'", e);
               throw new RuntimeException("Failed to load publisher profile: " + publisherProfileName, e);
            }

            TopicData topicData = getOrCreateTopicData(topic);
            ROS2Publisher<T> publisher = new ROS2Publisher<>(fastddsParticipant, publisherProfileName, topic, topicData);

            synchronized (publishers)
            {
               publishers.add(publisher);
            }

            // Don't notify discovery publisher for discovery topic itself
            if (!topic.getName().equals("ros_discovery_info"))
            {
               discoveryPublisher.addWriter(publisher.fastddsDataWriter);
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

   /**
    * Destroy a {@link ROS2Publisher}. Do not use on publishers which were created with another instance of {@link ROS2Node}.
    * This will remove the publisher from the list of publishers within this node and call {@link ROS2Publisher#close(Pointer)} to release the native Fast-DDS
    * publisher.
    * You do not have to call this if you call {@link ROS2Node#close()}, it will destroy all publishers created by this node for you.
    *
    * @param publisher the publisher to destroy.
    * @return true if the node contained the publisher, and it has been removed.
    */
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

   /**
    * Create a subscription using a {@link ROS2SubscriptionCallback}.
    * In the callback, you are given a {@link ROS2MessageReader} to read a sample of the {@param T} message type.
    * If you want a callback which gives you a sample of the message type directly, use
    * {@link #createSubscriptionSampler(ROS2Topic, ROS2SubscriptionCallbackSampler, ROS2QoSProfile)}.
    *
    * @param topic      the ROS 2 topic, (see {@link ROS2Topic} for how to use.
    * @param callback   a {@link ROS2SubscriptionCallback} callback which provides access to a {@link ROS2MessageReader} for deserializing the message
    *                   sample.
    *                   Has an allocation-free method, letting you reuse an instance of a {@link ROS2Message}.
    * @param qosProfile specify what quality-of-service settings you want for this subscription. Note: subscription and publisher QoS must match if you want
    *                   them to communicate.
    * @return the subscription instance, you do not have to store this as a field or manage it in any way if you don't need to.
    */
   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic, ROS2SubscriptionCallback<T> callback, ROS2QoSProfile qosProfile)
   {
      // Warn if subscribing to /parameter_events without matching QoS profile
      if (topic.getName().equals("/parameter_events") && qosProfile != ROS2QoSProfile.PARAMETER_EVENTS)
      {
         jros2.getLogger().warning("Creating subscription to /parameter_events with non-standard QoS profile. " +
                                   "Consider using ROS2QoSProfile.PARAMETER_EVENTS to ensure compatibility with parameter event publishers.");
      }

      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            ProfilesXML profilesXML = new ProfilesXML();
            SubscriberProfileType subscriberProfile = new SubscriberProfileType();
            // Prefix with "sub_" to ensure valid XML identifier
            long subscriberId = subscriberIdCounter.getAndIncrement();
            String subscriberProfileName = "sub_" + subscriberId;
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
               jros2.logError("Failed to load subscriber profile '" + subscriberProfileName + "' for topic '" + topic.getName() + "'", e);
               throw new RuntimeException("Failed to load subscriber profile: " + subscriberProfileName, e);
            }

            TopicData topicData = getOrCreateTopicData(topic);
            ROS2Subscription<T> subscription = new ROS2Subscription<>(fastddsParticipant, subscriberProfileName, callback, topic, topicData);

            synchronized (subscriptions)
            {
               subscriptions.add(subscription);
            }

            // Notify discovery publisher
            if (!topic.getName().equals("ros_discovery_info"))
            {
               discoveryPublisher.addReader(subscription.getReaderPointer());
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

   /**
    * Create a subscription using a {@link ROS2SubscriptionCallback}.
    * In the callback, you are given a {@link ROS2MessageReader} to read a sample of the {@param T} message type.
    * If you want a callback which gives you a sample of the message type directly, use
    * {@link #createSubscriptionSampler(ROS2Topic, ROS2SubscriptionCallbackSampler, ROS2QoSProfile)}.
    * This method will create a subscription using the default quality-of-service settings. See {@link ROS2QoSProfile#DEFAULT}.
    *
    * @param topic    the ROS 2 topic, (see {@link ROS2Topic} for how to use.
    * @param callback a {@link ROS2SubscriptionCallback} callback which provides access to a {@link ROS2MessageReader} for deserializing the message
    *                 sample.
    *                 Has an allocation-free method, letting you reuse an instance of a {@link ROS2Message}.
    * @return the subscription instance, you do not have to store this as a field or manage it in any way if you don't need to.
    */
   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic, ROS2SubscriptionCallback<T> callback)
   {
      return createSubscription(topic, callback, ROS2QoSProfile.DEFAULT);
   }

   /**
    * Create a subscription without any callback for when new data is received. You can use {@link ROS2Subscription#read()} and
    * {@link ROS2Subscription#readLatest()} ()} to manually read messages.
    *
    * @param topic      the ROS 2 topic, (see {@link ROS2Topic} for how to use.
    * @param qosProfile specify what quality-of-service settings you want for this subscription. Note: subscription and publisher QoS must match if you want
    *                   them to communicate.
    * @return the subscription instance, you do not have to store this as a field or manage it in any way if you don't need to.
    */
   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic, ROS2QoSProfile qosProfile)
   {
      return createSubscription(topic, null, qosProfile);
   }

   /**
    * Create a subscription without any callback for when new data is received. You can use {@link ROS2Subscription#read()} and
    * {@link ROS2Subscription#readLatest()} to manually read messages.
    * This method will create a subscription using the default quality-of-service settings. See {@link ROS2QoSProfile#DEFAULT}.
    *
    * @param topic the ROS 2 topic, (see {@link ROS2Topic} for how to use.
    * @return the subscription instance, you do not have to store this as a field or manage it in any way if you don't need to.
    */
   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic)
   {
      return createSubscription(topic, null, ROS2QoSProfile.DEFAULT);
   }

   /**
    * Create a subscription using a {@link ROS2SubscriptionCallbackSampler}.
    * In the callback, you are given a sample of {@param T} message type.
    *
    * @param topic      the ROS 2 topic, (see {@link ROS2Topic} for how to use.
    * @param sampler    a {@link ROS2SubscriptionCallbackSampler} callback which gives you the sample of a message upon receiving it from a subscription.
    *                   This does not allocate a new Java object for each sample, the same one is reused.
    * @param qosProfile specify what quality-of-service settings you want for this subscription. Note: subscription and publisher QoS must match if you want
    *                   them to communicate.
    * @return the subscription instance, you do not have to store this as a field or manage it in any way if you don't need to.
    */
   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscriptionSampler(ROS2Topic<T> topic,
                                                                                   ROS2SubscriptionCallbackSampler<T> sampler,
                                                                                   ROS2QoSProfile qosProfile)
   {
      ROS2SubscriptionCallback<T> callback = new ROS2SubscriptionCallback<>()
      {
         final T sample = T.createInstance(topic.getType());

         @Override
         public void onMessage(ROS2MessageReader<T> reader)
         {
            if (sample != null && reader.read(sample))
            {
               sampler.consume(sample);
            }
         }
      };

      return createSubscription(topic, callback, qosProfile);
   }

   /**
    * Create a subscription using a {@link ROS2SubscriptionCallbackSampler}.
    * In the callback, you are given a sample of {@param T} message type.
    * This method will create a subscription using the default quality-of-service settings. See {@link ROS2QoSProfile#DEFAULT}.
    *
    * @param topic   the ROS 2 topic, (see {@link ROS2Topic} for how to use.
    * @param sampler a {@link ROS2SubscriptionCallbackSampler} callback which gives you the sample of a message upon receiving it from a subscription.
    *                This does not allocate a new Java object for each sample, the same one is reused.
    * @return the subscription instance, you do not have to store this as a field or manage it in any way if you don't need to.
    */
   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscriptionSampler(ROS2Topic<T> topic, ROS2SubscriptionCallbackSampler<T> sampler)
   {
      return createSubscriptionSampler(topic, sampler, ROS2QoSProfile.DEFAULT);
   }

   /**
    * Destroy a {@link ROS2Subscription}. Do not use on subscriptions which were created with another instance of {@link ROS2Node}.
    * This will remove the subscription from the list of subscriptions within this node and call {@link ROS2Subscription#close(Pointer)} to release the native
    * Fast-DDS subscriber.
    * You do not have to call this if you call {@link ROS2Node#close()}, it will destroy all subscriptions created by this node for you.
    *
    * @param subscription the publisher to destroy.
    * @return true if the node contained the subscription, and it has been removed.
    */
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

   /**
    * Create a service client for calling a ROS 2 service.
    *
    * @param serviceName  The name of the service
    * @param requestType  The service request message class
    * @param responseType The service response message class
    * @param qosProfile   The QoS profile for the service client
    * @return The service client instance
    */
   public <Request extends ROS2Message<Request>, Response extends ROS2Message<Response>> ROS2ServiceClient<Request, Response> createServiceClient(String serviceName,
                                                                                                                                                  Class<Request> requestType,
                                                                                                                                                  Class<Response> responseType,
                                                                                                                                                  ROS2QoSProfile qosProfile)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            // Create topics for request and response
            // Use service name directly - prefix will be added based on topic type
            ROS2Topic<Request> requestTopic = new ROS2Topic<>(serviceName, requestType);
            ROS2Topic<Response> responseTopic = new ROS2Topic<>(serviceName, responseType);

            ROS2ServiceClient<Request, Response> client = new ROS2ServiceClient<>(this, serviceName, requestTopic, responseTopic, qosProfile);

            synchronized (serviceClients)
            {
               serviceClients.add(client);
            }

            return client;
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return null;
   }

   /**
    * Create a service client with default QoS profile.
    */
   public <Request extends ROS2Message<Request>, Response extends ROS2Message<Response>> ROS2ServiceClient<Request, Response> createServiceClient(String serviceName,
                                                                                                                                                  Class<Request> requestType,
                                                                                                                                                  Class<Response> responseType)
   {
      return createServiceClient(serviceName, requestType, responseType, ROS2QoSProfile.SERVICES_DEFAULT);
   }

   /**
    * Create a service server for handling service requests.
    *
    * @param serviceName  The name of the service
    * @param requestType  The service request message class
    * @param responseType The service response message class
    * @param callback     The callback to handle incoming requests
    * @param qosProfile   The QoS profile for the service server
    * @return The service server instance
    */
   public <Request extends ROS2Message<Request>, Response extends ROS2Message<Response>> ROS2ServiceServer<Request, Response> createServiceServer(String serviceName,
                                                                                                                                                  Class<Request> requestType,
                                                                                                                                                  Class<Response> responseType,
                                                                                                                                                  ROS2ServiceCallback<Request, Response> callback,
                                                                                                                                                  ROS2QoSProfile qosProfile)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            // Create topics for request and response
            // Use service name directly - prefix will be added based on message type
            // See getOrCreateTopicData() for prefix logic
            ROS2Topic<Request> requestTopic = new ROS2Topic<>(serviceName, requestType);
            ROS2Topic<Response> responseTopic = new ROS2Topic<>(serviceName, responseType);

            ROS2ServiceServer<Request, Response> server = new ROS2ServiceServer<>(this,
                                                                                  serviceName,
                                                                                  requestTopic,
                                                                                  responseTopic,
                                                                                  callback,
                                                                                  responseType,
                                                                                  qosProfile);

            synchronized (serviceServers)
            {
               serviceServers.add(server);
            }

            return server;
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return null;
   }

   /**
    * Create a service server with default QoS profile.
    */
   public <Request extends ROS2Message<Request>, Response extends ROS2Message<Response>> ROS2ServiceServer<Request, Response> createServiceServer(String serviceName,
                                                                                                                                                  Class<Request> requestType,
                                                                                                                                                  Class<Response> responseType,
                                                                                                                                                  ROS2ServiceCallback<Request, Response> callback)
   {
      return createServiceServer(serviceName, requestType, responseType, callback, ROS2QoSProfile.SERVICES_DEFAULT);
   }

   /**
    * Destroy a service client and release its resources.
    *
    * @param client The service client to destroy
    * @return true if the client was successfully removed
    */
   public boolean destroyServiceClient(ROS2ServiceClient<?, ?> client)
   {
      boolean removed = false;

      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            synchronized (serviceClients)
            {
               removed = serviceClients.remove(client);
            }

            if (removed)
            {
               client.close(fastddsParticipant);
            }
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return removed;
   }

   /**
    * Destroy a service server and release its resources.
    *
    * @param server The service server to destroy
    * @return true if the server was successfully removed
    */
   public boolean destroyServiceServer(ROS2ServiceServer<?, ?> server)
   {
      boolean removed = false;

      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            synchronized (serviceServers)
            {
               removed = serviceServers.remove(server);
            }

            if (removed)
            {
               server.close(fastddsParticipant);
            }
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return removed;
   }

   /**
    * Destroy an action client and release its resources.
    *
    * @param client The action client to destroy
    * @return true if the client was successfully removed
    */
   public boolean destroyActionClient(ROS2ActionClient<?, ?, ?> client)
   {
      boolean removed = false;

      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            synchronized (actionClients)
            {
               removed = actionClients.remove(client);
            }

            if (removed)
            {
               client.close(fastddsParticipant);
            }
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return removed;
   }

   /**
    * Destroy an action server and release its resources.
    *
    * @param server The action server to destroy
    * @return true if the server was successfully removed
    */
   public boolean destroyActionServer(ROS2ActionServer<?, ?, ?> server)
   {
      boolean removed = false;

      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            synchronized (actionServers)
            {
               removed = actionServers.remove(server);
            }

            if (removed)
            {
               server.close(fastddsParticipant);
            }
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return removed;
   }

   /**
    * Create a parameter client for interacting with parameters on a remote node.
    *
    * @param nodeName The name of the remote node
    * @return A new parameter client
    */
   public ROS2ParameterClient createParameterClient(String nodeName)
   {
      return createParameterClient(nodeName, ROS2QoSProfile.SERVICES_DEFAULT);
   }

   /**
    * Create a parameter client for interacting with parameters on a remote node.
    *
    * @param nodeName   The name of the remote node
    * @param qosProfile The QoS profile for the parameter services
    * @return A new parameter client
    */
   public ROS2ParameterClient createParameterClient(String nodeName, ROS2QoSProfile qosProfile)
   {
      ROS2ParameterClient client = null;

      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            client = new ROS2ParameterClient(this, nodeName, qosProfile);

            synchronized (parameterClients)
            {
               parameterClients.add(client);
            }
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return client;
   }

   /**
    * Destroy a parameter client and release its resources.
    *
    * @param client The parameter client to destroy
    * @return true if the client was successfully removed
    */
   public boolean destroyParameterClient(ROS2ParameterClient client)
   {
      boolean removed = false;

      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            synchronized (parameterClients)
            {
               removed = parameterClients.remove(client);
            }

            if (removed)
            {
               client.close();
            }
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return removed;
   }

   /**
    * Create an action client for sending action goals.
    *
    * @param actionName   The name of the action
    * @param goalType     The action goal message class
    * @param resultType   The action result message class
    * @param feedbackType The action feedback message class
    * @param qosProfile   The QoS profile for the action client
    * @return The action client instance
    */
   public <Goal extends ROS2Message<Goal>, Result extends ROS2Message<Result>, Feedback extends ROS2Message<Feedback>> ROS2ActionClient<Goal, Result, Feedback> createActionClient(
         String actionName,
         Class<Goal> goalType,
         Class<Result> resultType,
         Class<Feedback> feedbackType,
         ROS2QoSProfile qosProfile)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            // Create topics for goal, result, and feedback
            // Note: These will get 'rt' prefix added by getOrCreateTopicData()
            String actionPrefix = actionName.startsWith("/") ? actionName : "/" + actionName;
            ROS2Topic<Goal> goalTopic = new ROS2Topic<>(actionPrefix + "/_action/send_goal", goalType);
            ROS2Topic<Result> resultTopic = new ROS2Topic<>(actionPrefix + "/_action/get_result", resultType);
            ROS2Topic<Feedback> feedbackTopic = new ROS2Topic<>(actionPrefix + "/_action/feedback", feedbackType);

            ROS2ActionClient<Goal, Result, Feedback> client = new ROS2ActionClient<>(this, actionName, goalTopic, resultTopic, feedbackTopic, qosProfile);

            synchronized (actionClients)
            {
               actionClients.add(client);
            }

            return client;
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return null;
   }

   /**
    * Create an action client with default QoS profile.
    */
   public <Goal extends ROS2Message<Goal>, Result extends ROS2Message<Result>, Feedback extends ROS2Message<Feedback>> ROS2ActionClient<Goal, Result, Feedback> createActionClient(
         String actionName,
         Class<Goal> goalType,
         Class<Result> resultType,
         Class<Feedback> feedbackType)
   {
      return createActionClient(actionName, goalType, resultType, feedbackType, ROS2QoSProfile.DEFAULT);
   }

   /**
    * Create an action server for executing action goals.
    *
    * @param actionName   The name of the action
    * @param goalType     The action goal message class
    * @param resultType   The action result message class
    * @param feedbackType The action feedback message class
    * @param callback     The callback to handle incoming goals
    * @param qosProfile   The QoS profile for the action server
    * @return The action server instance
    */
   public <Goal extends ROS2Message<Goal>, Result extends ROS2Message<Result>, Feedback extends ROS2Message<Feedback>> ROS2ActionServer<Goal, Result, Feedback> createActionServer(
         String actionName,
         Class<Goal> goalType,
         Class<Result> resultType,
         Class<Feedback> feedbackType,
         ROS2ActionGoalCallback<Goal, Result, Feedback> callback,
         ROS2QoSProfile qosProfile)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            // Create topics for goal, result, and feedback
            // Note: These will get 'rt' prefix added by getOrCreateTopicData()
            String actionPrefix = actionName.startsWith("/") ? actionName : "/" + actionName;
            ROS2Topic<Goal> goalTopic = new ROS2Topic<>(actionPrefix + "/_action/send_goal", goalType);
            ROS2Topic<Result> resultTopic = new ROS2Topic<>(actionPrefix + "/_action/get_result", resultType);
            ROS2Topic<Feedback> feedbackTopic = new ROS2Topic<>(actionPrefix + "/_action/feedback", feedbackType);

            ROS2ActionServer<Goal, Result, Feedback> server = new ROS2ActionServer<>(this,
                                                                                     actionName,
                                                                                     goalTopic,
                                                                                     resultTopic,
                                                                                     feedbackTopic,
                                                                                     callback,
                                                                                     resultType,
                                                                                     feedbackType,
                                                                                     qosProfile);

            synchronized (actionServers)
            {
               actionServers.add(server);
            }

            return server;
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return null;
   }

   /**
    * Create an action server with default QoS profile.
    */
   public <Goal extends ROS2Message<Goal>, Result extends ROS2Message<Result>, Feedback extends ROS2Message<Feedback>> ROS2ActionServer<Goal, Result, Feedback> createActionServer(
         String actionName,
         Class<Goal> goalType,
         Class<Result> resultType,
         Class<Feedback> feedbackType,
         ROS2ActionGoalCallback<Goal, Result, Feedback> callback)
   {
      return createActionServer(actionName, goalType, resultType, feedbackType, callback, ROS2QoSProfile.DEFAULT);
   }

   /**
    * Declare a parameter with a default value.
    * If the parameter already exists, it will be updated with the new value.
    * Publishes a parameter event.
    *
    * @param parameter The parameter to declare
    * @return The declared parameter
    * @throws IllegalArgumentException if parameter or parameter name is null or empty
    */
   public ROS2Parameter declareParameter(ROS2Parameter parameter)
   {
      if (parameter == null)
      {
         throw new IllegalArgumentException("parameter cannot be null when declaring a parameter");
      }
      if (parameter.getName() == null || parameter.getName().isEmpty())
      {
         throw new IllegalArgumentException("parameter name cannot be null or empty when declaring a parameter");
      }

      // Initialize parameter services on first parameter declaration
      // This allows the Fast-DDS participant to be fully initialized before creating services
      ensureParameterServicesInitialized();

      boolean isNew = !parameters.containsKey(parameter.getName());
      parameters.put(parameter.getName(), parameter);
      publishParameterEvent(parameter, isNew);
      return parameter;
   }

   /**
    * Declare a boolean parameter.
    *
    * @param name  The parameter name
    * @param value The boolean value
    * @return The declared parameter
    * @throws IllegalArgumentException if name is null or empty
    */
   public ROS2Parameter declareParameter(String name, boolean value)
   {
      if (name == null || name.isEmpty())
      {
         throw new IllegalArgumentException("parameter name cannot be null or empty when declaring a parameter");
      }
      return declareParameter(new ROS2Parameter(name, value));
   }

   /**
    * Declare an integer parameter.
    *
    * @param name  The parameter name
    * @param value The integer value
    * @return The declared parameter
    * @throws IllegalArgumentException if name is null or empty
    */
   public ROS2Parameter declareParameter(String name, long value)
   {
      if (name == null || name.isEmpty())
      {
         throw new IllegalArgumentException("parameter name cannot be null or empty when declaring a parameter");
      }
      return declareParameter(new ROS2Parameter(name, value));
   }

   /**
    * Declare a double parameter.
    *
    * @param name  The parameter name
    * @param value The double value
    * @return The declared parameter
    * @throws IllegalArgumentException if name is null or empty
    */
   public ROS2Parameter declareParameter(String name, double value)
   {
      if (name == null || name.isEmpty())
      {
         throw new IllegalArgumentException("parameter name cannot be null or empty when declaring a parameter");
      }
      return declareParameter(new ROS2Parameter(name, value));
   }

   /**
    * Declare a string parameter.
    *
    * @param name  The parameter name
    * @param value The string value
    * @return The declared parameter
    * @throws IllegalArgumentException if name is null or empty
    */
   public ROS2Parameter declareParameter(String name, String value)
   {
      if (name == null || name.isEmpty())
      {
         throw new IllegalArgumentException("parameter name cannot be null or empty when declaring a parameter");
      }
      return declareParameter(new ROS2Parameter(name, value));
   }

   /**
    * Get a parameter by name.
    *
    * @param name The parameter name
    * @return The parameter, or null if not found
    */
   public ROS2Parameter getParameter(String name)
   {
      return parameters.get(name);
   }

   /**
    * Check if a parameter exists.
    *
    * @param name The parameter name
    * @return true if the parameter exists
    */
   public boolean hasParameter(String name)
   {
      return parameters.containsKey(name);
   }

   /**
    * Set a parameter value. The parameter must be declared first.
    * Publishes a parameter event if successful.
    *
    * @param parameter The parameter to set
    * @return true if the parameter was set successfully
    */
   public boolean setParameter(ROS2Parameter parameter)
   {
      if (parameters.containsKey(parameter.getName()))
      {
         parameters.put(parameter.getName(), parameter);
         publishParameterEvent(parameter, false);
         return true;
      }
      return false;
   }

   /**
    * Get all parameters managed by this node.
    *
    * @return An unmodifiable map of parameters
    */
   public Map<String, ROS2Parameter> getParameters()
   {
      return Collections.unmodifiableMap(parameters);
   }

   /**
    * Publish a parameter event to the /parameter_events topic.
    *
    * @param parameter The parameter that changed
    * @param isNew     True if this is a new parameter, false if it's a change
    */
   private void publishParameterEvent(ROS2Parameter parameter, boolean isNew)
   {
      // Lazily create parameter event publisher
      if (parameterEventPublisher == null && !closed)
      {
         parameterEventPublisher = createPublisher(new ROS2Topic<>("/parameter_events", rcl_interfaces.ParameterEvent.class), ROS2QoSProfile.PARAMETER_EVENTS);
         // Note: The parameter event publisher is tracked in the publishers list via createPublisher(),
         // so it will be properly cleaned up in close()
      }

      if (parameterEventPublisher != null && !closed)
      {
         rcl_interfaces.ParameterEvent event = new rcl_interfaces.ParameterEvent();

         // Set timestamp (using current time in milliseconds)
         long currentTimeMillis = System.currentTimeMillis();
         event.getStamp().setSec((int) (currentTimeMillis / 1000L));
         event.getStamp().setNanosec((int) ((currentTimeMillis % 1000L) * 1_000_000L));

         // Set node name
         event.setNode(name);

         // Convert ROS2Parameter to rcl_interfaces.Parameter
         rcl_interfaces.Parameter rosParam = convertToRclParameter(parameter);

         // Add to appropriate list
         if (isNew)
         {
            event.getNewParameters().add(rosParam);
         }
         else
         {
            event.getChangedParameters().add(rosParam);
         }

         parameterEventPublisher.publish(event);
      }
   }

   /**
    * Convert ROS2Parameter to rcl_interfaces.Parameter.
    */
   private rcl_interfaces.Parameter convertToRclParameter(ROS2Parameter param)
   {
      rcl_interfaces.Parameter rosParam = new rcl_interfaces.Parameter();
      rosParam.setName(param.getName());

      rcl_interfaces.ParameterValue value = rosParam.getValue();
      switch (param.getType())
      {
         case PARAMETER_NOT_SET:
            value.setType(rcl_interfaces.ParameterType.PARAMETER_NOT_SET);
            break;
         case PARAMETER_BOOL:
            value.setType(rcl_interfaces.ParameterType.PARAMETER_BOOL);
            value.setBoolValue(param.asBool());
            break;
         case PARAMETER_INTEGER:
            value.setType(rcl_interfaces.ParameterType.PARAMETER_INTEGER);
            value.setIntegerValue(param.asLong());
            break;
         case PARAMETER_DOUBLE:
            value.setType(rcl_interfaces.ParameterType.PARAMETER_DOUBLE);
            value.setDoubleValue(param.asDouble());
            break;
         case PARAMETER_STRING:
            value.setType(rcl_interfaces.ParameterType.PARAMETER_STRING);
            value.setStringValue(param.asString());
            break;
         case PARAMETER_BYTE_ARRAY:
            value.setType(rcl_interfaces.ParameterType.PARAMETER_BYTE_ARRAY);
            for (byte b : param.asByteArray())
            {
               value.getByteArrayValue().add(b);
            }
            break;
         case PARAMETER_BOOL_ARRAY:
            value.setType(rcl_interfaces.ParameterType.PARAMETER_BOOL_ARRAY);
            for (boolean b : param.asBoolArray())
            {
               value.getBoolArrayValue().add(b);
            }
            break;
         case PARAMETER_INTEGER_ARRAY:
            value.setType(rcl_interfaces.ParameterType.PARAMETER_INTEGER_ARRAY);
            for (long l : param.asLongArray())
            {
               value.getIntegerArrayValue().add(l);
            }
            break;
         case PARAMETER_DOUBLE_ARRAY:
            value.setType(rcl_interfaces.ParameterType.PARAMETER_DOUBLE_ARRAY);
            for (double d : param.asDoubleArray())
            {
               value.getDoubleArrayValue().add(d);
            }
            break;
         case PARAMETER_STRING_ARRAY:
            value.setType(rcl_interfaces.ParameterType.PARAMETER_STRING_ARRAY);
            for (String s : param.asStringArray())
            {
               value.getStringArrayValue().add(s);
            }
            break;
      }
      return rosParam;
   }

   /**
    * Get the colloquial (user-friendly) name of the node.
    * This name is intended for display or user reference only and is not used internally.
    *
    * @return the colloquial name of the node
    */
   public String getName()
   {
      return name;
   }

   /**
    * Get the domain ID the node uses for communication. Valid domain ID range: [0, 232]. The default domain ID is 0.
    *
    * @return the domain ID
    */
   public int getDomainId()
   {
      return domainId;
   }

   /**
    * Get all active publishers created by this node.
    *
    * @return an unmodifiable copy of the list of publishers.
    */
   public List<ROS2Publisher<?>> getPublishers()
   {
      return Collections.unmodifiableList(publishers);
   }

   /**
    * Get all active subscriptions created by this node.
    *
    * @return an unmodifiable copy of the list of subscriptions.
    */
   public List<ROS2Subscription<?>> getSubscriptions()
   {
      return Collections.unmodifiableList(subscriptions);
   }

   /**
    * Check if this node has been closed and is now inoperable.
    *
    * @return true if {@link #close()} has been called.
    */
   public boolean isClosed()
   {
      return closed;
   }

   /**
    * Release resources and mark this node as inoperable. After close() has been called, this node will be unable to create new publishers, subscriptions,
    * services, actions, or parameters.
    * This method will block and wait for:
    * 1. any currently executing {@link ROS2Publisher#publish(ROS2Message)}
    * 2. any currently executing {@link ROS2Subscription} callback
    * 3. any currently executing {@link ROS2Subscription#read()} (or other read(T), readLatest(), readLatest(T))
    * 4. any currently executing service or action callbacks
    * This is to ensure memory safety and guaranteed order of close operations.
    * All publishers, subscriptions, services, actions, and parameters will be destroyed if close() has not been called already.
    */
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

         synchronized (serviceClients)
         {
            // Delete service clients
            for (ROS2ServiceClient<?, ?> client : serviceClients)
            {
               client.close(fastddsParticipant);
            }
            serviceClients.clear();
         }

         synchronized (serviceServers)
         {
            // Delete service servers
            for (ROS2ServiceServer<?, ?> server : serviceServers)
            {
               server.close(fastddsParticipant);
            }
            serviceServers.clear();
         }

         synchronized (actionClients)
         {
            // Delete action clients
            for (ROS2ActionClient<?, ?, ?> client : actionClients)
            {
               client.close(fastddsParticipant);
            }
            actionClients.clear();
         }

         synchronized (actionServers)
         {
            // Delete action servers
            for (ROS2ActionServer<?, ?, ?> server : actionServers)
            {
               server.close(fastddsParticipant);
            }
            actionServers.clear();
         }

         synchronized (parameterClients)
         {
            // Delete parameter clients
            for (ROS2ParameterClient client : parameterClients)
            {
               client.close();
            }
            parameterClients.clear();
         }

         // Delete parameter services
         if (parameterService != null)
         {
            parameterService.close(fastddsParticipant);
         }

         // Clear parameters
         parameters.clear();

         // Close discovery publisher
         if (discoveryPublisher != null)
         {
            discoveryPublisher.close();
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

   /**
    * Check if SHM is usable on Windows and log a warning if it's enabled but unavailable.
    * <p>
    * On Windows, the SHM directory (C:\ProgramData\eprosima\fastdds_interprocess) can sometimes
    * lose write permissions, making SHM transport unavailable. This method detects that condition
    * and logs a severe warning with recovery instructions.
    *
    * @param rtps RTPS configuration containing transport settings
    * @param fastddsTransports optional array of custom transport descriptors
    */
   private static void checkSHMAvailabilityWindows(Rtps rtps, TransportDescriptorType... fastddsTransports)
   {
      if (System.getProperty("os.name").startsWith("Windows") && !TransportDescriptorTypeTools.SHM_TRANSPORT_AVAILABLE_ON_WINDOWS)
      {
         boolean shmEnabled = false;

         if (rtps.isUseBuiltinTransports())
         {
            shmEnabled = true;
         }
         else if (fastddsTransports != null)
         {
            for (int i = 0; i < fastddsTransports.length; ++i)
            {
               TransportDescriptorType transportDescriptorType = fastddsTransports[i];

               if (transportDescriptorType.getType().equals("SHM"))
               {
                  shmEnabled = true;
                  break;
               }
            }
         }

         if (shmEnabled)
         {
            jros2.getLogger().severe("Shared Memory Transport (SHM) is not available. Could not write to: C:\\ProgramData\\eprosima\\fastdds_interprocess");
            jros2.getLogger().severe("Try restarting the process after deleting the directory.");
         }
      }
   }
}
