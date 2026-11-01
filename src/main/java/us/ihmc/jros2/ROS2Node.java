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

import us.ihmc.fastddsjava.fastddsjavaException;
import us.ihmc.fastddsjava.natives.fastddsjava;
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.TransportDescriptorTypeTools;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps.UserTransports;
import us.ihmc.fastddsjava.profiles.gen.PropertyPolicyType;
import us.ihmc.fastddsjava.profiles.gen.PropertyType;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static us.ihmc.fastddsjava.fastddsjavaTools.retcodePrintOnError;
import static us.ihmc.fastddsjava.natives.fastddsjava.*;

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

   /*
    * Atomic counters for garbage-free ID generation
    */
   private static final AtomicLong participantIdCounter = new AtomicLong(0);
   private static final Object participantDestructionLock = new Object();
   private static final Object typeRegistrationLock = new Object();
   private static final AtomicLong topicIdCounter = new AtomicLong(0);
   private static final AtomicLong publisherIdCounter = new AtomicLong(0);
   private static final AtomicLong subscriberIdCounter = new AtomicLong(0);
   private static final CopyOnWriteArrayList<ROS2Node> activeNodes = new CopyOnWriteArrayList<>();

   /*
    * DDS topic name prefixes and suffixes, see https://design.ros2.org/articles/topic_and_service_names.html
    */
   static final String ROS_TOPIC_PREFIX = "rt";
   static final String ROS_SERVICE_REQUEST_PREFIX = "rq";
   static final String ROS_SERVICE_RESPONSE_PREFIX = "rr";
   static final String ROS_SERVICE_REQUEST_SUFFIX = "Request";
   static final String ROS_SERVICE_RESPONSE_SUFFIX = "Reply";

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
    * See: <a href="https://fast-dds.docs.eprosima.com/en/v3.6.2/fastdds/dds_layer/domain/domain.html">Domain</a>
    * See: <a href="https://docs.ros.org/en/humble/Concepts/Intermediate/About-Domain-ID.html">ROS 2 Domain ID</a>
    */
   private final int domainId;

   /*
    * Fast-DDS pointers
    */
   /**
    * Native handle to a Fast-DDS participant used by this node. For internal use only.
    */
   protected final long fastddsParticipant;
   /**
    * A helper map linking {@link ROS2Topic}\s to {@link TopicData}, where TopicData is a set of Fast-DDS pointers required
    * for creating and using a topic. For internal use only.
    */
   private final Map<String, Map<String, TopicData>> topicDataByNameAndType;
   private final Map<String, TypeRegistration> typeRegistrationsByName;

   /**
    * A list of {@link ROS2Publisher}\s managed by this node.
    */
   protected final List<ROS2Publisher<?>> publishers;
   /**
    * A list of {@link ROS2Subscription}\s managed by this node.
    */
   private final List<ROS2Subscription<?>> subscriptions;
   /**
    * A list of {@link ROS2Service}\s managed by this node.
    */
   private final List<ROS2Service<?, ?>> services;
   /**
    * A list of {@link ROS2Client}\s managed by this node.
    */
   private final List<ROS2Client<?, ?>> clients;
   private final List<ROS2ActionServer<?, ?, ?>> actionServers;
   private final List<ROS2ActionClient<?, ?, ?>> actionClients;
   /**
    * Parameters of this node and their services.
    */
   private final ROS2NodeParameters parameters;

   /*
    * Locks
    */
   protected final ReadWriteLock closeLock;
   protected boolean closed;
   private final Thread shutdownHook;

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

      /*
       * jros2 registers its types by name only, without XTypes type information, so it has no use for the TypeLookup service.
       * Disable type propagation: otherwise, when a remote endpoint announces type information (e.g. rclcpp nodes using
       * Fast-DDS 3, as in ROS 2 Lyrical), this participant first queries the remote type before matching the endpoint. The
       * remote side matches at once, so a request sent right after a client sees a service as available can arrive before
       * our reader has matched its writer, and is lost.
       */
      PropertyPolicyType propertiesPolicy = new PropertyPolicyType();
      PropertyPolicyType.Properties properties = new PropertyPolicyType.Properties();
      PropertyType typePropagation = new PropertyType();
      typePropagation.setName("fastdds.type_propagation");
      typePropagation.setValue("disabled");
      properties.getProperty().add(typePropagation);
      propertiesPolicy.getPropertiesOrBinaryProperties().add(properties);
      rtps.setPropertiesPolicy(propertiesPolicy);

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

      fastddsParticipant = fastddsjava.createParticipant(participantProfileName);
      topicDataByNameAndType = new HashMap<>();
      typeRegistrationsByName = new HashMap<>();
      publishers = new ArrayList<>();
      subscriptions = new ArrayList<>();
      services = new ArrayList<>();
      clients = new ArrayList<>();
      actionServers = new ArrayList<>();
      actionClients = new ArrayList<>();
      parameters = new ROS2NodeParameters(this);

      closeLock = new ReentrantReadWriteLock(true);
      closed = false;

      shutdownHook = new Thread(this::close, "ROS2NodeShutdownHook-" + name);
      Runtime.getRuntime().addShutdownHook(shutdownHook);

      activeNodes.add(this);
   }

   /**
    * Returns all nodes that have been constructed and not yet closed.
    */
   public static List<ROS2Node> getActiveNodes()
   {
      return activeNodes;
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

   <T extends ROS2Message<T>> TopicData getOrCreateTopicData(ROS2Topic<T> topic)
   {
      return getOrCreateTopicData(topic, ROS_TOPIC_PREFIX, "");
   }

   /**
    * @param prefix prepended to the ROS name to create the DDS topic name ({@code rt} for topics, {@code rq} / {@code rr} for services)
    * @param suffix appended to the ROS name ({@code Request} / {@code Reply} for services, empty for topics)
    */
   <T extends ROS2Message<T>> TopicData getOrCreateTopicData(ROS2Topic<T> topic, String prefix, String suffix)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            /*
             * All ROS names are prefixed with {@code rX} to create the DDS topic name, where {@code X} is determined by the
             * subsystem: {@code rt} for topics, {@code rq} for service requests and {@code rr} for service replies.
             * See "Mapping of ROS 2 Topic and Service Names to DDS Concepts" section of
             * https://design.ros2.org/articles/topic_and_service_names.html
             */
            String topicName = prefix + topic.getName() + suffix;
            String topicTypeName = ROS2Message.getNameFromMessageClass(topic.getType());

            synchronized (this.topicDataByNameAndType)
            {
               Map<String, TopicData> topicDataByType = this.topicDataByNameAndType.get(topicName);
               if (topicDataByType != null)
               {
                  TopicData existingTopicData = topicDataByType.get(topicTypeName);
                  if (existingTopicData != null)
                  {
                     return existingTopicData;
                  }
               }
               else
               {
                  topicDataByType = new HashMap<>();
                  this.topicDataByNameAndType.put(topicName, topicDataByType);
               }

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
                  jros2.logError("Failed to load topic profile '" + topicProfileName + "' for topic '" + topicName + "'", e);
                  throw new RuntimeException("Failed to load topic profile: " + topicProfileName, e);
               }

               TypeRegistration typeRegistration = typeRegistrationsByName.get(topicTypeName);
               if (typeRegistration == null)
               {
                  synchronized (typeRegistrationLock)
                  {
                     typeRegistration = typeRegistrationsByName.get(topicTypeName);
                     if (typeRegistration == null)
                     {
                        long fastddsTopicDataWrapperType = fastddsjava.createTopicDataWrapperType(topicTypeName, CDR_LE);
                        long fastddsTypeSupport = fastddsjava.createTypesupport(fastddsTopicDataWrapperType);
                        fastddsjava.registerType(fastddsParticipant, fastddsTypeSupport);
                        typeRegistration = new TypeRegistration(fastddsTopicDataWrapperType, fastddsTypeSupport);
                        typeRegistrationsByName.put(topicTypeName, typeRegistration);
                     }
                  }
               }
               long fastddsTopic = fastddsjava.createTopic(fastddsParticipant,
                                                               typeRegistration.fastddsTopicDataWrapperType,
                                                               topicName,
                                                               topicProfileName);
               TopicData topicData = new TopicData(typeRegistration.fastddsTopicDataWrapperType, typeRegistration.fastddsTypeSupport, fastddsTopic);

               topicDataByType.put(topicTypeName, topicData);

               return topicData;
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
      return createPublisher(topic, qosProfile, ROS_TOPIC_PREFIX, "", true);
   }

   private <T extends ROS2Message<T>> ROS2Publisher<T> createPublisher(ROS2Topic<T> topic,
                                                                     ROS2QoSProfile qosProfile,
                                                                     String prefix,
                                                                     String suffix,
                                                                     boolean register)
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

            TopicData topicData = getOrCreateTopicData(topic, prefix, suffix);

            // Synchronize profile loading and publisher creation to prevent race condition where
            // Fast-DDS hasn't finished loading the profile when create_datawriter_with_profile is called.
            ROS2Publisher<T> publisher;
            synchronized (ProfilesXML.getLoadLock())
            {
               try
               {
                  profilesXML.load();
               }
               catch (fastddsjavaException e)
               {
                  jros2.logError("Failed to load publisher profile '" + publisherProfileName + "' for topic '" + topic.getName() + "'", e);
                  throw new RuntimeException("Failed to load publisher profile: " + publisherProfileName, e);
               }

               publisher = new ROS2Publisher<>(fastddsParticipant, publisherProfileName, topic, topicData);
            }

            if (register)
            {
               synchronized (publishers)
               {
                  publishers.add(publisher);
               }
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
      return createPublisher(topic, topic.getQoS());
   }

   /**
    * Destroy a {@link ROS2Publisher}. Do not use on publishers which were created with another instance of {@link ROS2Node}.
    * This will remove the publisher from the list of publishers within this node and call {@link ROS2Publisher#close(long)} to release the native Fast-DDS
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
   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic,
                                                                            ROS2SubscriptionCallback<T> callback,
                                                                            ROS2SubscriptionMatchedCallback matchedCallback,
                                                                            ROS2QoSProfile qosProfile)
   {
      return createSubscription(topic, callback, matchedCallback, qosProfile, ROS_TOPIC_PREFIX, "", true);
   }

   private <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic,
                                                                             ROS2SubscriptionCallback<T> callback,
                                                                             ROS2SubscriptionMatchedCallback matchedCallback,
                                                                             ROS2QoSProfile qosProfile,
                                                                             String prefix,
                                                                             String suffix,
                                                                             boolean register)
   {
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

            TopicData topicData = getOrCreateTopicData(topic, prefix, suffix);

            // Synchronize profile loading and subscription creation to prevent race condition where
            // Fast-DDS hasn't finished loading the profile when create_datareader_with_profile is called.
            ROS2Subscription<T> subscription;
            synchronized (ProfilesXML.getLoadLock())
            {
               try
               {
                  profilesXML.load();
               }
               catch (fastddsjavaException e)
               {
                  jros2.logError("Failed to load subscriber profile '" + subscriberProfileName + "' for topic '" + topic.getName() + "'", e);
                  throw new RuntimeException("Failed to load subscriber profile: " + subscriberProfileName, e);
               }

               subscription = new ROS2Subscription<>(fastddsParticipant, subscriberProfileName, callback, matchedCallback, topic, topicData);
            }

            if (register)
            {
               synchronized (subscriptions)
               {
                  subscriptions.add(subscription);
               }
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

   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic, ROS2SubscriptionCallback<T> callback, ROS2QoSProfile qosProfile)
   {
      return createSubscription(topic, callback, null, qosProfile);
   }

   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic,
                                                                            ROS2SubscriptionCallback<T> callback,
                                                                            ROS2SubscriptionMatchedCallback matchedCallback)
   {
      return createSubscription(topic, callback, matchedCallback, topic.getQoS());
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
      return createSubscription(topic, callback, topic.getQoS());
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
      return createSubscription(topic, null, topic.getQoS());
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
      return createSubscriptionSampler(topic, sampler, topic.getQoS());
   }

   /**
    * Destroy a {@link ROS2Subscription}. Do not use on subscriptions which were created with another instance of {@link ROS2Node}.
    * This will remove the subscription from the list of subscriptions within this node and call {@link ROS2Subscription#close(long)} to release the native
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
    * Create a service server. Requests are read from {@code rq<serviceName>Request} and responses written to
    * {@code rr<serviceName>Reply}, compatible with ROS 2 nodes using rmw_fastrtps.
    *
    * @param serviceName  the ROS 2 service name, e.g. {@code /add_two_ints}
    * @param requestType  the generated request class, e.g. {@code example_interfaces.AddTwoInts_Request.class}
    * @param responseType the generated response class, e.g. {@code example_interfaces.AddTwoInts_Response.class}
    * @param callback     called for each request to fill the response
    * @param qosProfile   QoS of the request and response topics. Clients and services must use compatible QoS.
    * @return the service
    */
   public <Req extends ROS2Message<Req>, Res extends ROS2Message<Res>> ROS2Service<Req, Res> createService(String serviceName,
                                                                                                         Class<Req> requestType,
                                                                                                         Class<Res> responseType,
                                                                                                         ROS2ServiceCallback<Req, Res> callback,
                                                                                                         ROS2QoSProfile qosProfile)
   {
      return createService(serviceName, requestType, responseType, callback, null, qosProfile);
   }

   /**
    * Create a service whose responses are sent later with {@link ROS2Service#sendResponse(ROS2Message, SampleIdentity)}.
    */
   <Req extends ROS2Message<Req>, Res extends ROS2Message<Res>> ROS2Service<Req, Res> createDeferredService(String serviceName,
                                                                                                          Class<Req> requestType,
                                                                                                          Class<Res> responseType,
                                                                                                          ROS2Service.DeferredCallback<Req> callback,
                                                                                                          ROS2QoSProfile qosProfile)
   {
      return createService(serviceName, requestType, responseType, null, callback, qosProfile);
   }

   private <Req extends ROS2Message<Req>, Res extends ROS2Message<Res>> ROS2Service<Req, Res> createService(String serviceName,
                                                                                                          Class<Req> requestType,
                                                                                                          Class<Res> responseType,
                                                                                                          ROS2ServiceCallback<Req, Res> callback,
                                                                                                          ROS2Service.DeferredCallback<Req> deferredCallback,
                                                                                                          ROS2QoSProfile qosProfile)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            ROS2Publisher<Res> responsePublisher = createPublisher(new ROS2Topic<>(serviceName, responseType, qosProfile),
                                                                   qosProfile,
                                                                   ROS_SERVICE_RESPONSE_PREFIX,
                                                                   ROS_SERVICE_RESPONSE_SUFFIX,
                                                                   false);
            ROS2Topic<Req> requestTopic = new ROS2Topic<>(serviceName, requestType, qosProfile);
            ROS2Service<Req, Res> service = new ROS2Service<>(serviceName,
                                                              requestType,
                                                              responseType,
                                                              callback,
                                                              deferredCallback,
                                                              responsePublisher,
                                                              requestCallback -> createSubscription(requestTopic,
                                                                                                    requestCallback,
                                                                                                    null,
                                                                                                    qosProfile,
                                                                                                    ROS_SERVICE_REQUEST_PREFIX,
                                                                                                    ROS_SERVICE_REQUEST_SUFFIX,
                                                                                                    false));

            synchronized (services)
            {
               services.add(service);
            }

            return service;
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      return null;
   }

   /**
    * Create a service server using the default ROS 2 service QoS (reliable, volatile, keep last 10).
    *
    * @param serviceName  the ROS 2 service name, e.g. {@code /add_two_ints}
    * @param requestType  the generated request class, e.g. {@code example_interfaces.AddTwoInts_Request.class}
    * @param responseType the generated response class, e.g. {@code example_interfaces.AddTwoInts_Response.class}
    * @param callback     called for each request to fill the response
    * @return the service
    */
   public <Req extends ROS2Message<Req>, Res extends ROS2Message<Res>> ROS2Service<Req, Res> createService(String serviceName,
                                                                                                         Class<Req> requestType,
                                                                                                         Class<Res> responseType,
                                                                                                         ROS2ServiceCallback<Req, Res> callback)
   {
      return createService(serviceName, requestType, responseType, callback, ROS2QoSProfile.DEFAULT);
   }

   public boolean destroyService(ROS2Service<?, ?> service)
   {
      boolean removed = false;

      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            synchronized (services)
            {
               removed = services.remove(service);
            }

            if (removed)
            {
               closeService(service);
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
    * Create a service client. Requests are written to {@code rq<serviceName>Request} and responses read from
    * {@code rr<serviceName>Reply}, compatible with ROS 2 nodes using rmw_fastrtps.
    *
    * @param serviceName  the ROS 2 service name, e.g. {@code /add_two_ints}
    * @param requestType  the generated request class, e.g. {@code example_interfaces.AddTwoInts_Request.class}
    * @param responseType the generated response class, e.g. {@code example_interfaces.AddTwoInts_Response.class}
    * @param qosProfile   QoS of the request and response topics. Clients and services must use compatible QoS.
    * @return the client
    */
   public <Req extends ROS2Message<Req>, Res extends ROS2Message<Res>> ROS2Client<Req, Res> createClient(String serviceName,
                                                                                                       Class<Req> requestType,
                                                                                                       Class<Res> responseType,
                                                                                                       ROS2QoSProfile qosProfile)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            ROS2Publisher<Req> requestPublisher = createPublisher(new ROS2Topic<>(serviceName, requestType, qosProfile),
                                                                  qosProfile,
                                                                  ROS_SERVICE_REQUEST_PREFIX,
                                                                  ROS_SERVICE_REQUEST_SUFFIX,
                                                                  false);
            ROS2Topic<Res> responseTopic = new ROS2Topic<>(serviceName, responseType, qosProfile);
            ROS2Client<Req, Res> client = new ROS2Client<>(serviceName,
                                                           requestType,
                                                           responseType,
                                                           requestPublisher,
                                                           responseCallback -> createSubscription(responseTopic,
                                                                                                  responseCallback,
                                                                                                  null,
                                                                                                  qosProfile,
                                                                                                  ROS_SERVICE_RESPONSE_PREFIX,
                                                                                                  ROS_SERVICE_RESPONSE_SUFFIX,
                                                                                                  false));

            synchronized (clients)
            {
               clients.add(client);
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
    * Create a service client using the default ROS 2 service QoS (reliable, volatile, keep last 10).
    *
    * @param serviceName  the ROS 2 service name, e.g. {@code /add_two_ints}
    * @param requestType  the generated request class, e.g. {@code example_interfaces.AddTwoInts_Request.class}
    * @param responseType the generated response class, e.g. {@code example_interfaces.AddTwoInts_Response.class}
    * @return the client
    */
   public <Req extends ROS2Message<Req>, Res extends ROS2Message<Res>> ROS2Client<Req, Res> createClient(String serviceName,
                                                                                                       Class<Req> requestType,
                                                                                                       Class<Res> responseType)
   {
      return createClient(serviceName, requestType, responseType, ROS2QoSProfile.DEFAULT);
   }

   public boolean destroyClient(ROS2Client<?, ?> client)
   {
      boolean removed = false;

      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            synchronized (clients)
            {
               removed = clients.remove(client);
            }

            if (removed)
            {
               closeClient(client);
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
    * Create an action server. It serves {@code <actionName>/_action/send_goal}, {@code cancel_goal} and {@code get_result} and
    * publishes {@code <actionName>/_action/feedback} and {@code status}, compatible with rclcpp_action clients.
    *
    * @param actionName   the ROS 2 action name, e.g. {@code /fibonacci}
    * @param goalType     the generated goal class, e.g. {@code example_interfaces.Fibonacci_Goal.class}; the other generated
    *                     messages of the action are found from it
    * @param resultType   the generated result class, e.g. {@code example_interfaces.Fibonacci_Result.class}
    * @param feedbackType the generated feedback class, e.g. {@code example_interfaces.Fibonacci_Feedback.class}
    * @param callbacks    decide on goals and cancel requests, and execute accepted goals
    * @return the action server
    */
   public <G extends ROS2Message<G>, R extends ROS2Message<R>, F extends ROS2Message<F>> ROS2ActionServer<G, R, F> createActionServer(String actionName,
                                                                                                                                    Class<G> goalType,
                                                                                                                                    Class<R> resultType,
                                                                                                                                    Class<F> feedbackType,
                                                                                                                                    ROS2ActionServerCallbacks<G, R, F> callbacks)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            ROS2ActionServer<G, R, F> actionServer = new ROS2ActionServer<>(this, actionName, goalType, resultType, feedbackType, callbacks);
            synchronized (actionServers)
            {
               actionServers.add(actionServer);
            }
            return actionServer;
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }
      return null;
   }

   /**
    * Create an action client, compatible with rclcpp_action servers.
    *
    * @param actionName   the ROS 2 action name, e.g. {@code /fibonacci}
    * @param goalType     the generated goal class, e.g. {@code example_interfaces.Fibonacci_Goal.class}
    * @param resultType   the generated result class, e.g. {@code example_interfaces.Fibonacci_Result.class}
    * @param feedbackType the generated feedback class, e.g. {@code example_interfaces.Fibonacci_Feedback.class}
    * @return the action client
    */
   public <G extends ROS2Message<G>, R extends ROS2Message<R>, F extends ROS2Message<F>> ROS2ActionClient<G, R, F> createActionClient(String actionName,
                                                                                                                                    Class<G> goalType,
                                                                                                                                    Class<R> resultType,
                                                                                                                                    Class<F> feedbackType)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            ROS2ActionClient<G, R, F> actionClient = new ROS2ActionClient<>(this, actionName, goalType, resultType, feedbackType);
            synchronized (actionClients)
            {
               actionClients.add(actionClient);
            }
            return actionClient;
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }
      return null;
   }

   public boolean destroyActionServer(ROS2ActionServer<?, ?, ?> actionServer)
   {
      boolean removed;
      synchronized (actionServers)
      {
         removed = actionServers.remove(actionServer);
      }
      if (removed)
      {
         actionServer.closeResources();
         actionServer.getServices().forEach(this::destroyService);
         actionServer.getPublishers().forEach(this::destroyPublisher);
      }
      return removed;
   }

   public boolean destroyActionClient(ROS2ActionClient<?, ?, ?> actionClient)
   {
      boolean removed;
      synchronized (actionClients)
      {
         removed = actionClients.remove(actionClient);
      }
      if (removed)
      {
         actionClient.closeResources();
         actionClient.getClients().forEach(this::destroyClient);
         actionClient.getSubscriptions().forEach(this::destroySubscription);
      }
      return removed;
   }

   public List<ROS2ActionServer<?, ?, ?>> getActionServers()
   {
      return Collections.unmodifiableList(actionServers);
   }

   public List<ROS2ActionClient<?, ?, ?>> getActionClients()
   {
      return Collections.unmodifiableList(actionClients);
   }

   private void closeService(ROS2Service<?, ?> service)
   {
      service.getRequestSubscription().close(fastddsParticipant);
      service.getResponsePublisher().close(fastddsParticipant);
      service.closeResources();
   }

   private void closeClient(ROS2Client<?, ?> client)
   {
      client.getResponseSubscription().close(fastddsParticipant);
      client.getRequestPublisher().close(fastddsParticipant);
      client.closeResources();
   }
   /**
    * Declare a parameter. The first declaration also starts this node's parameter services
    * ({@code /<node name>/get_parameters}, {@code set_parameters}, ...) and its events publisher on {@code /parameter_events}.
    *
    * @param name         the parameter name, use '.' to group parameters, e.g. {@code control.gain}
    * @param defaultValue the initial value, see {@link ROS2Parameter#of(String, Object)} for the supported types
    * @param descriptor   description, read-only and dynamic typing flags
    * @return the declared parameter
    * @throws IllegalStateException    if the parameter is already declared
    * @throws IllegalArgumentException if the value type is not supported or a {@link ROS2ParameterCallback} rejects it
    */
   public ROS2Parameter declareParameter(String name, Object defaultValue, ROS2ParameterDescriptor descriptor)
   {
      return parameters.declare(name, defaultValue, descriptor);
   }

   /**
    * Declare a parameter with the default descriptor (not read-only, statically typed).
    *
    * @see #declareParameter(String, Object, ROS2ParameterDescriptor)
    */
   public ROS2Parameter declareParameter(String name, Object defaultValue)
   {
      return parameters.declare(name, defaultValue, ROS2ParameterDescriptor.DEFAULT);
   }

   /**
    * @return the current value of a declared parameter
    * @throws IllegalStateException if the parameter is not declared
    */
   public ROS2Parameter getParameter(String name)
   {
      return parameters.get(name);
   }

   /**
    * @return all declared parameters, in declaration order
    */
   public List<ROS2Parameter> getParameters()
   {
      return parameters.getAll();
   }

   public boolean hasParameter(String name)
   {
      return parameters.has(name);
   }

   public ROS2ParameterDescriptor describeParameter(String name)
   {
      return parameters.describe(name);
   }

   /**
    * Set a declared parameter, with the same checks as a remote {@code set_parameters} call: the parameter must be declared, not
    * read-only, keep its type (unless dynamically typed) and be accepted by every {@link ROS2ParameterCallback}.
    *
    * @param name  the parameter name
    * @param value the new value
    * @return whether it was set and, if not, why
    */
   public ROS2SetParametersResult setParameter(String name, Object value)
   {
      return parameters.set(List.of(ROS2Parameter.of(name, value)));
   }

   /**
    * Set several parameters atomically: either all are set or none.
    */
   public ROS2SetParametersResult setParameters(List<ROS2Parameter> parameters)
   {
      return this.parameters.set(parameters);
   }

   /**
    * Undeclare a dynamically typed, writable parameter.
    *
    * @throws IllegalStateException if the parameter is not declared, is read-only or statically typed
    */
   public void undeclareParameter(String name)
   {
      parameters.undeclare(name);
   }

   /**
    * Add a callback that can reject parameter changes before they are applied.
    */
   public void addOnSetParametersCallback(ROS2ParameterCallback callback)
   {
      parameters.addCallback(callback);
   }

   public boolean removeOnSetParametersCallback(ROS2ParameterCallback callback)
   {
      return parameters.removeCallback(callback);
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

   public List<ROS2Service<?, ?>> getServices()
   {
      return Collections.unmodifiableList(services);
   }

   public List<ROS2Client<?, ?>> getClients()
   {
      return Collections.unmodifiableList(clients);
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
    * @return true if this node publishes asynchronously on a background thread.
    */
   public boolean isAsync()
   {
      return false;
   }

   /**
    * Release resources and mark this node as inoperable. After close() has been called, this node will be unable to create new publishers or subscriptions.
    * This method will block and wait for:
    * 1. any currently executing {@link ROS2Publisher#publish(ROS2Message)}
    * 2. any currently executing {@link ROS2Subscription} callback
    * 3. any currently executing {@link ROS2Subscription#read()} (or other read(T), readLatest(), readLatest(T))
    * This is to ensure memory safety and guaranteed order of close operations.
    * All publishers and subscriptions will be destroyed if close() has not been called already.
    */
   @Override
   public void close()
   {
      // Wait until all readers are finished, then start closing
      closeLock.writeLock().lock();
      try
      {
         if (!closed)
         {
            closed = true;
            activeNodes.remove(this);

            try
            {
               Runtime.getRuntime().removeShutdownHook(shutdownHook);
            }
            catch (IllegalStateException ignored)
            {
               // JVM shutdown already in progress
            }

            // Stop action servers and clients first; their endpoints are closed with the others below
            synchronized (actionServers)
            {
               actionServers.forEach(ROS2ActionServer::closeResources);
               actionServers.clear();
            }
            synchronized (actionClients)
            {
               actionClients.forEach(ROS2ActionClient::closeResources);
               actionClients.clear();
            }

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

            synchronized (services)
            {
               for (ROS2Service<?, ?> service : services)
               {
                  closeService(service);
               }
               services.clear();
            }

            synchronized (clients)
            {
               for (ROS2Client<?, ?> client : clients)
               {
                  closeClient(client);
               }
               clients.clear();
            }

            synchronized (participantDestructionLock)
            {
               synchronized (ProfilesXML.getLoadLock())
               {
                  try
                  {
                     Thread.sleep(50);
                  }
                  catch (InterruptedException e)
                  {
                     Thread.currentThread().interrupt();
                  }

                  synchronized (topicDataByNameAndType)
                  {
                     // Delete topics
                     for (Map<String, TopicData> topicDataByType : topicDataByNameAndType.values())
                     {
                        for (TopicData topicData : topicDataByType.values())
                        {
                           retcodePrintOnError(fastddsjava.deleteTopic(fastddsParticipant, topicData.fastddsTopic));
                        }
                     }
                     topicDataByNameAndType.clear();

                     synchronized (typeRegistrationLock)
                     {
                        // Unregister types
                        for (TypeRegistration typeRegistration : typeRegistrationsByName.values())
                        {
                           retcodePrintOnError(fastddsjava.unregisterType(fastddsParticipant,
                                                                            fastddsjava.topicDataWrapperTypeGetName(typeRegistration.fastddsTopicDataWrapperType)));
                           fastddsjava.deleteTypesupport(typeRegistration.fastddsTypeSupport);
                        }
                        typeRegistrationsByName.clear();
                     }
                  }

                  // Delete participant
                  retcodePrintOnError(fastddsjava.deleteParticipant(fastddsParticipant));
               }
            }
         }
      }
      finally
      {
         closeLock.writeLock().unlock();
      }
   }

   /**
    * Check if SHM is usable on Windows (sometimes the SHM directory can lose write permissions)
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

   private static final class TypeRegistration
   {
      private final long fastddsTopicDataWrapperType;
      private final long fastddsTypeSupport;

      private TypeRegistration(long fastddsTopicDataWrapperType, long fastddsTypeSupport)
      {
         this.fastddsTopicDataWrapperType = fastddsTopicDataWrapperType;
         this.fastddsTypeSupport = fastddsTypeSupport;
      }
   }
}
