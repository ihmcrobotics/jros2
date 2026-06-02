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
import java.util.concurrent.atomic.AtomicLong;
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

   /*
    * Atomic counters for garbage-free ID generation
    */
   private static final AtomicLong participantIdCounter = new AtomicLong(0);
   private static final Object participantDestructionLock = new Object();
   private static final Object typeRegistrationLock = new Object();
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
   private final Map<String, TopicData> topicDataByKey;
   private final Map<String, TypeRegistration> typeRegistrationsByName;

   /**
    * A list of {@link ROS2Publisher}\s managed by this node.
    */
   protected final List<ROS2Publisher<?>> publishers;
   /**
    * A list of {@link ROS2Subscription}\s managed by this node.
    */
   private final List<ROS2Subscription<?>> subscriptions;

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
      topicDataByKey = new HashMap<>();
      typeRegistrationsByName = new HashMap<>();
      publishers = new ArrayList<>();
      subscriptions = new ArrayList<>();

      closeLock = new ReentrantReadWriteLock(true);
      closed = false;

      shutdownHook = new Thread(this::close, "ROS2NodeShutdownHook-" + name);
      Runtime.getRuntime().addShutdownHook(shutdownHook);
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
   private static String topicDataKey(ROS2Topic<?> topic)
   {
      return topic.getName() + '\0' + ROS2Message.getNameFromMessageClass(topic.getType());
   }

   <T extends ROS2Message<T>> TopicData getOrCreateTopicData(ROS2Topic<T> topic)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            String topicKey = topicDataKey(topic);

            synchronized (this.topicDataByKey)
            {
               if (this.topicDataByKey.containsKey(topicKey))
               {
                  return this.topicDataByKey.get(topicKey);
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
                   */
                  // TODO: Support other prefixes depending on ROS subsystem
                  // Use concat method to avoid string allocation on hot path (though this still allocates)
                  String prefixedTopicName = "rt".concat(topic.getName());
                  String topicTypeName = ROS2Message.getNameFromMessageClass(topic.getType());
                  TypeRegistration typeRegistration = typeRegistrationsByName.get(topicTypeName);
                  if (typeRegistration == null)
                  {
                     synchronized (typeRegistrationLock)
                     {
                        typeRegistration = typeRegistrationsByName.get(topicTypeName);
                        if (typeRegistration == null)
                        {
                           fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType(topicTypeName, CDR_LE);
                           Pointer fastddsTypeSupport = fastddsjava_create_typesupport(topicDataWrapperType);
                           fastddsjava_register_type(fastddsParticipant, fastddsTypeSupport);
                           typeRegistration = new TypeRegistration(topicDataWrapperType, fastddsTypeSupport);
                           typeRegistrationsByName.put(topicTypeName, typeRegistration);
                        }
                     }
                  }
                  Pointer fastddsTopic = fastddsjava_create_topic(fastddsParticipant,
                                                                  typeRegistration.topicDataWrapperType,
                                                                  prefixedTopicName,
                                                                  topicProfileName);
                  TopicData topicData = new TopicData(typeRegistration.topicDataWrapperType, typeRegistration.fastddsTypeSupport, fastddsTopic);

                  this.topicDataByKey.put(topicKey, topicData);

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

            TopicData topicData = getOrCreateTopicData(topic);

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
   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic,
                                                                            ROS2SubscriptionCallback<T> callback,
                                                                            ROS2SubscriptionMatchedCallback matchedCallback,
                                                                            ROS2QoSProfile qosProfile)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            ProfilesXML profilesXML = new ProfilesXML();
            SubscriberProfileType subscriberProfile = new SubscriberProfileType();
            long subscriberId = subscriberIdCounter.getAndIncrement();
            String subscriberProfileName = "sub_" + subscriberId;
            subscriberProfile.setProfileName(subscriberProfileName);
            profilesXML.addSubscriberProfile(subscriberProfile);

            QoSTools.translateQoS(qosProfile, subscriberProfile);

            TopicData topicData = getOrCreateTopicData(topic);

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

   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic, ROS2SubscriptionCallback<T> callback, ROS2QoSProfile qosProfile)
   {
      return createSubscription(topic, callback, null, qosProfile);
   }

   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic,
                                                                            ROS2SubscriptionCallback<T> callback,
                                                                            ROS2SubscriptionMatchedCallback matchedCallback)
   {
      return createSubscription(topic, callback, matchedCallback, ROS2QoSProfile.DEFAULT);
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
      closeLock.writeLock().lock();
      if (closed)
      {
         closeLock.writeLock().unlock();
         return;
      }
      closed = true;

      try
      {
         try
         {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
         }
         catch (IllegalStateException ignored)
         {
            // JVM shutdown already in progress
         }

         synchronized (publishers)
         {
            for (ROS2Publisher<?> publisher : publishers)
            {
               publisher.close(fastddsParticipant);
            }
            publishers.clear();
         }

         synchronized (subscriptions)
         {
            for (ROS2Subscription<?> subscription : subscriptions)
            {
               subscription.close(fastddsParticipant);
            }
            subscriptions.clear();
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

               synchronized (topicDataByKey)
               {
                  for (TopicData topicData : topicDataByKey.values())
                  {
                     retcodePrintOnError(fastddsjava_delete_topic(fastddsParticipant, topicData.fastddsTopic));
                  }
                  topicDataByKey.clear();

                  synchronized (typeRegistrationLock)
                  {
                     for (TypeRegistration typeRegistration : typeRegistrationsByName.values())
                     {
                        retcodePrintOnError(fastddsjava_unregister_type(fastddsParticipant, typeRegistration.topicDataWrapperType.get_name()));
                        typeRegistration.fastddsTypeSupport.close();
                        typeRegistration.topicDataWrapperType.setNull();
                     }
                     typeRegistrationsByName.clear();
                  }
               }

               retcodePrintOnError(fastddsjava_delete_participant(fastddsParticipant));
               fastddsParticipant.setNull();
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
      private final fastddsjava_TopicDataWrapperType topicDataWrapperType;
      private final Pointer fastddsTypeSupport;

      private TypeRegistration(fastddsjava_TopicDataWrapperType topicDataWrapperType, Pointer fastddsTypeSupport)
      {
         this.topicDataWrapperType = topicDataWrapperType;
         this.fastddsTypeSupport = fastddsTypeSupport;
      }
   }
}
