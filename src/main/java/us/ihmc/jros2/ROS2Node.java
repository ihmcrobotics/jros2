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
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.TransportDescriptorTypeTools;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A ROS 2-compatible node which provides functionality for managing ROS 2-compatible publishers, subscriptions.
 * Uses Fast-DDS middleware via the {@link us.ihmc.fastddsjava} package. Fully thread-safe.
 * <p>
 * <b>Names.</b> Node names, namespaces and topic names follow the ROS 2 rules exactly like rclcpp: the node name must
 * be valid for {@code rmw_validate_node_name}, the namespace is normalized ({@code ""} becomes {@code "/"},
 * {@code "foo"} becomes {@code "/foo"}) and validated, and topic names are expanded like {@code rcl_expand_topic_name}
 * (relative names are prefixed with the node namespace, {@code ~} is the private node namespace, and
 * {@code {node}}, {@code {ns}} and {@code {namespace}} are substituted) when a publisher or subscription is created.
 * Invalid names throw an {@link IllegalArgumentException} with the rmw reason and index. Names that rmw rejects (for
 * example {@code "/my-topic"} or {@code "my node"}) were accepted by jros2 1.5 and earlier but are now rejected.
 * <p>
 * <b>ROS graph.</b> Each node announces itself and its publishers and subscriptions on the {@code ros_discovery_info}
 * DDS topic, so it is listed by {@code ros2 node list}, {@code ros2 node info} and {@code ros2 topic info -v}. The wire
 * format differs between ROS 2 distributions (24-byte GIDs up to Humble, 16-byte GIDs from Iron on) and follows the
 * {@code JROS2_GRAPH_COMPATIBILITY} environment variable (system property {@code jros2.graph.compatibility},
 * jros2.properties {@code jros2.graph.compatibility}): {@code IRON_PLUS}, {@code HUMBLE}, {@code DUAL} (both formats,
 * for mixed systems) or {@code AUTO} (the default: {@code HUMBLE} when {@code ROS_DISTRO} is foxy, galactic or humble,
 * {@code IRON_PLUS} for iron and newer, {@code DUAL} when it is unset or unknown). With Foxy or Galactic nodes in the
 * domain, set {@code ROS_DISTRO} (or {@code HUMBLE}): their Fast-DDS based processes abort with {@code std::bad_alloc} on
 * a 16-byte GID sample ({@code DUAL} and {@code IRON_PLUS}), exactly as they do next to a native Iron or newer node.
 * <p>
 * <b>Participants.</b> By default every node has its own DDS participant. Nodes created with
 * {@link ROS2NodeOptions#context(ROS2Context)} share the participant of that {@link ROS2Context}.
 * <p>
 * <b>Intraprocess delivery.</b> Fast-DDS intraprocess delivery is OFF by default: samples between nodes of the same
 * process go through the shared-memory or UDP transports. Set {@code FASTDDS_INTRAPROCESS_DELIVERY=true} (system
 * property {@code fastdds.intraprocess.delivery}, jros2.properties {@code jros2.fastdds.intraprocess.delivery}) to
 * deliver them in-process; this is a library-wide setting read once when jros2 loads. With intraprocess delivery,
 * subscription callbacks of local subscribers run inside {@link ROS2Publisher#publish} on the publishing thread.
 * <p>
 * <b>Callbacks.</b> Subscription callbacks run on Fast-DDS threads unless {@link ROS2NodeOptions#callbackExecutor} is
 * set. From such a callback you may publish, destroy publishers and subscriptions and close nodes and contexts (the
 * native teardown is deferred to a jros2 thread), and create publishers and subscriptions on <i>other</i> participants.
 * Creating a publisher or subscription on the participant whose callback is running deadlocks inside Fast-DDS (measured
 * with Fast-DDS 3.6.2 for data callbacks delivered by the network transports and for matched callbacks, with and without
 * intraprocess delivery), so it throws an {@link IllegalStateException}. Creating publishers, subscriptions and nodes
 * on other participants from such a callback works, but jros2 serializes all DDS entity creation in the process, and a
 * creation running on another thread at the same time can itself be waiting inside Fast-DDS for the callback to return
 * (a creation on the callback's participant always is, and with intraprocess delivery so is the creation of an
 * endpoint that matches the callback's subscription). The creation from the callback then fails with an
 * {@link IllegalStateException} after a short wait instead of deadlocking. Data callbacks dispatched to a callback
 * executor do not block a Fast-DDS thread and may create endpoints of their own participant without these limits; the
 * executor must run tasks on its own threads for that.
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
   private static final AtomicLong publisherIdCounter = new AtomicLong(0);
   private static final AtomicLong subscriberIdCounter = new AtomicLong(0);
   private static final CopyOnWriteArrayList<ROS2Node> activeNodes = new CopyOnWriteArrayList<>();
   /** Number of node operations (create/destroy) in progress on the current thread. */
   private static final ThreadLocal<int[]> OPERATION_DEPTH = ThreadLocal.withInitial(() -> new int[1]);
   /** How long a second {@link #close()} or a teardown waits for work owned by other threads before giving up. */
   private static final long CLOSE_WAIT_TIMEOUT_MS = 30000;

   /*
    * Node identification
    */
   /**
    * The node name, e.g. "talker".
    */
   private final String name;
   /**
    * The normalized absolute namespace, e.g. "/" or "/robot".
    */
   private final String namespace;
   private final String fullyQualifiedName;
   /**
    * The domain ID the node will use when writing and reading to the network transport. A valid domain ID must
    * be within the range [0, 232].
    * <p>
    * See: <a href="https://fast-dds.docs.eprosima.com/en/v3.6.2/fastdds/dds_layer/domain/domain.html">Domain</a>
    * See: <a href="https://docs.ros.org/en/humble/Concepts/Intermediate/About-Domain-ID.html">ROS 2 Domain ID</a>
    */
   private final int domainId;
   /**
    * Defensive copy of the options this node was created with.
    */
   protected final ROS2NodeOptions options;

   /*
    * Fast-DDS participant
    */
   private final ROS2Participant participant;
   /** The context whose participant this node uses, or null if the node owns a private participant. */
   private final ROS2Context context;
   /**
    * Native handle to a Fast-DDS participant used by this node. For internal use only.
    */
   protected final long fastddsParticipant;

   /**
    * A list of {@link ROS2Publisher}\s managed by this node. Guarded by its own monitor.
    */
   protected final List<ROS2Publisher<?>> publishers;
   /**
    * A list of {@link ROS2Subscription}\s managed by this node. Guarded by its own monitor.
    */
   private final List<ROS2Subscription<?>> subscriptions;
   /** Topic reference and graph GID of every endpoint created through this node. Guarded by its own monitor. */
   private final List<EndpointRecord> endpointRecords;
   /** Topic references taken through {@link #getOrCreateTopicData(ROS2Topic)}, released when the node closes. */
   private final List<String> extraTopicReferences;

   /*
    * Locks
    */
   /**
    * Subclasses may hold the read lock while they use the node; {@link #close()} acquires the write lock before any
    * native resource is released.
    */
   protected final ReadWriteLock closeLock;
   protected volatile boolean closed;
   /** Guards {@link #closed} transitions, {@link #operationsInFlight} and {@link #pendingDeferredTasks}. */
   private final Object operationGate = new Object();
   private int operationsInFlight;
   private int pendingDeferredTasks;
   private final AtomicBoolean closeRequested = new AtomicBoolean(false);
   private final CountDownLatch teardownDone = new CountDownLatch(1);
   private final Thread shutdownHook;

   /**
    * Create a new ROS 2 Node for managing ROS 2-compatible publishers, subscriptions.
    *
    * @param name              The node name. Must be a valid ROS 2 node name.
    * @param domainId          The domain ID the node will use when writing and reading to the network transport.
    * @param fastddsTransports An optional list of Transports to enable. These define what network protocol and network parameters to use when communicating
    *                          over the network. See: {@link TransportDescriptorTypeTools}.
    */
   public ROS2Node(String name, int domainId, TransportDescriptorType... fastddsTransports)
   {
      this(name, ROS2NodeOptions.defaults().domainId(domainId).transports(fastddsTransports));
   }

   /**
    * Create a new ROS 2 Node with the given options.
    *
    * @param name    The node name. Must be a valid ROS 2 node name ({@code [A-Za-z_][A-Za-z0-9_]*}, at most 255 characters).
    * @param options Node options. Unset options fall back to the jros2 settings and ROS 2 defaults.
    * @throws IllegalArgumentException if the name, namespace or domain ID is invalid, or if participant-level options
    *                                  conflict with the {@link ROS2Context} given in the options
    * @throws IllegalStateException    if Fast-DDS cannot create the DDS participant, or the context is closed
    */
   public ROS2Node(String name, ROS2NodeOptions options)
   {
      ROS2Names.checkNodeName(name);
      this.name = name;
      this.options = options == null ? ROS2NodeOptions.defaults() : new ROS2NodeOptions(options);
      namespace = ROS2Names.checkNamespace(this.options.getNamespace());
      fullyQualifiedName = ROS2Names.fullyQualifiedNodeName(namespace, name);

      context = this.options.getContext();
      if (context != null)
      {
         context.checkNodeOptions(this.options);
         participant = context.getParticipant();
         domainId = context.getDomainId();
      }
      else
      {
         int domainId = this.options.getDomainId() != null ? this.options.getDomainId() : jros2.get().rosDomainId();
         if (domainId < 0 || domainId > 232)
         {
            throw new IllegalArgumentException("Invalid domain ID used when constructing a ROS2Node (" + domainId + "); it must be in [0, 232]");
         }
         this.domainId = domainId;
         participant = ROS2Participant.create(name, domainId, this.options, getClass());
      }
      fastddsParticipant = participant.getFastddsParticipant();

      publishers = new ArrayList<>();
      subscriptions = new ArrayList<>();
      endpointRecords = new ArrayList<>();
      extraTopicReferences = new ArrayList<>();

      closeLock = new ReentrantReadWriteLock(true);
      closed = false;

      // Nodes of a context are closed by the context's shutdown hook
      shutdownHook = context == null ? new Thread(this::close, "ROS2NodeShutdownHook-" + name) : null;
      // Registered before the node becomes reachable through its context, which may close it right away
      activeNodes.add(this);
      if (shutdownHook != null)
      {
         Runtime.getRuntime().addShutdownHook(shutdownHook);
      }

      try
      {
         if (context != null)
         {
            context.attach(this); // Announces the node in the context's graph
         }
         else
         {
            participant.addNode(this);
         }
      }
      catch (RuntimeException | Error e)
      {
         activeNodes.remove(this);
         if (shutdownHook != null)
         {
            try
            {
               Runtime.getRuntime().removeShutdownHook(shutdownHook);
            }
            catch (IllegalStateException ignored)
            {
               // JVM shutdown already in progress
            }
            participant.close();
         }
         throw e;
      }
   }

   /**
    * Returns all nodes that have been constructed and not yet closed.
    *
    * @return an unmodifiable snapshot
    */
   public static List<ROS2Node> getActiveNodes()
   {
      return Collections.unmodifiableList(new ArrayList<>(activeNodes));
   }

   /**
    * Create a new ROS 2 Node for managing ROS 2-compatible publishers, subscriptions.
    *
    * @param name The node name. Must be a valid ROS 2 node name.
    */
   public ROS2Node(String name)
   {
      this(name, ROS2NodeOptions.defaults());
   }

   /**
    * Create a new ROS 2 Node for managing ROS 2-compatible publishers, subscriptions.
    *
    * @param name     The node name. Must be a valid ROS 2 node name.
    * @param domainId The domain ID the node will use when writing and reading to the network transport.
    */
   public ROS2Node(String name, int domainId)
   {
      this(name, domainId, (TransportDescriptorType[]) null);
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Operation gate: create/destroy operations run "in flight"; the teardown waits for them without blocking new
    * callers, which simply see the node as closed.
    * ------------------------------------------------------------------------------------------------------------ */

   private boolean enterOperation()
   {
      synchronized (operationGate)
      {
         if (closed)
         {
            return false;
         }
         operationsInFlight++;
      }
      OPERATION_DEPTH.get()[0]++;
      return true;
   }

   private void exitOperation()
   {
      OPERATION_DEPTH.get()[0]--;
      synchronized (operationGate)
      {
         operationsInFlight--;
         if (operationsInFlight == 0)
         {
            operationGate.notifyAll();
         }
      }
   }

   private void awaitOperationsAndDeferredTasks(boolean includeDeferredTasks)
   {
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CLOSE_WAIT_TIMEOUT_MS);
      boolean interrupted = false;
      synchronized (operationGate)
      {
         while (operationsInFlight > 0 || (includeDeferredTasks && pendingDeferredTasks > 0))
         {
            long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remainingMs <= 0)
            {
               jros2.getLogger().warning("Closing node '" + fullyQualifiedName + "' while " + operationsInFlight + " operation(s) and " + pendingDeferredTasks
                                         + " deferred task(s) are still running");
               break;
            }
            try
            {
               operationGate.wait(remainingMs);
            }
            catch (InterruptedException e)
            {
               interrupted = true;
            }
         }
      }
      if (interrupted)
      {
         Thread.currentThread().interrupt();
      }
   }

   /**
    * @return true if native endpoints of this node's participant must not be created or deleted on the current thread
    */
   private boolean mustDeferNativeTeardown()
   {
      return CallbackContext.isInCallback() || OPERATION_DEPTH.get()[0] > 0;
   }

   private void checkNotInOwnParticipantCallback(String what)
   {
      if (participant.isInFastddsCallbackOfThisParticipant())
      {
         throw new IllegalStateException("Cannot create a " + what + " on node '" + fullyQualifiedName + "' from inside a callback of a subscription of the same "
                                         + "DDS participant: Fast-DDS deadlocks in that case. Set ROS2NodeOptions.callbackExecutor(...) on the subscribing node "
                                         + "so callbacks run on an executor thread, or create the " + what + " from another thread.");
      }
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Topics
    * ------------------------------------------------------------------------------------------------------------ */

   /**
    * A topic resolved for this node: fully qualified name, DDS names and the topic object handed to the endpoint.
    */
   private static final class ResolvedTopic<T extends ROS2Message<T>>
   {
      private final ROS2Topic<T> topic;
      private final String ddsTopicName;
      private final String typeName;
      private final String typeHash;

      private ResolvedTopic(ROS2Topic<T> topic, String ddsTopicName, String typeName, String typeHash)
      {
         this.topic = topic;
         this.ddsTopicName = ddsTopicName;
         this.typeName = typeName;
         this.typeHash = typeHash;
      }
   }

   private <T extends ROS2Message<T>> ResolvedTopic<T> resolveTopic(ROS2Topic<T> topic)
   {
      if (topic == null)
      {
         throw new IllegalArgumentException("topic must not be null");
      }
      if (topic.getType() == null)
      {
         throw new IllegalArgumentException("Topic '" + topic.getName() + "' has no message type; use ROS2Topic.withType(...)");
      }
      String fullyQualifiedTopicName = resolveTopicName(topic.getName());
      String typeName = ROS2Message.getNameFromMessageClass(topic.getType());
      if (typeName == null || typeName.isEmpty())
      {
         throw new IllegalArgumentException(topic.getType().getName() + " is not a jros2 message class: it has no 'public static final String name' DDS type name");
      }
      ROS2Topic<T> resolved = fullyQualifiedTopicName.equals(topic.getName()) ? topic : new ROS2Topic<>(fullyQualifiedTopicName, topic.getType(), topic.getQoS());
      return new ResolvedTopic<>(resolved, ROS2Names.ddsTopicName(fullyQualifiedTopicName), typeName, ROS2Message.getTypeHashFromMessageClass(topic.getType()));
   }

   /** @return the suggested QoS of a topic, or null for a null topic (reported by {@link #resolveTopic}) */
   private static ROS2QoSProfile qosOf(ROS2Topic<?> topic)
   {
      return topic == null ? null : topic.getQoS();
   }

   /**
    * Expands and validates a topic name for this node, like rclcpp does when a publisher or subscription is created:
    * relative names are prefixed with the node namespace, {@code ~} is replaced by the node's fully qualified name and
    * {@code {node}}, {@code {ns}} and {@code {namespace}} are substituted.
    *
    * @param topicName absolute, relative or private ({@code ~/...}) topic name
    * @return the fully qualified topic name, e.g. "/robot/chatter"
    * @throws IllegalArgumentException with the rmw/rcl reason if the name is invalid
    */
   public String resolveTopicName(String topicName)
   {
      return ROS2Names.resolveTopicName(topicName, name, namespace);
   }

   /**
    * Takes a reference on the native topic of {@code topic} in this node's participant. The reference is released when the
    * node closes. Kept for subclasses that create endpoints themselves; prefer {@link #createPublisherInternal}.
    *
    * @return the topic data, or null if the node is closed
    */
   <T extends ROS2Message<T>> TopicData getOrCreateTopicData(ROS2Topic<T> topic)
   {
      ResolvedTopic<T> resolved = resolveTopic(topic);
      if (!enterOperation())
      {
         return null;
      }
      try
      {
         TopicData topicData = participant.acquireTopic(resolved.ddsTopicName, resolved.typeName);
         synchronized (extraTopicReferences)
         {
            extraTopicReferences.add(resolved.ddsTopicName);
         }
         return topicData;
      }
      finally
      {
         exitOperation();
      }
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Publishers
    * ------------------------------------------------------------------------------------------------------------ */

   /**
    * Create a publisher to publish {@param T} message type for subscriptions to receive.
    *
    * @param topic      the ROS 2 topic, (see {@link ROS2Topic} for how to use. Relative and private names are expanded
    *                   against this node's namespace and name.
    * @param qosProfile specify what quality-of-service settings you want for this publisher. Note: publisher and subscription QoS must match if you want them
    *                   to communicate.
    * @return the publisher instance, or null if this node is closed. You do not have to store this as a field or manage it in any way if you don't need to.
    * @throws IllegalArgumentException if the topic name is invalid, or the topic is already used with another type in this node's participant
    * @throws IllegalStateException    if Fast-DDS fails to create the publisher, or this is called from a callback of this node's participant
    */
   public <T extends ROS2Message<T>> ROS2Publisher<T> createPublisher(ROS2Topic<T> topic, ROS2QoSProfile qosProfile)
   {
      return createPublisherInternal(topic, qosProfile, 0);
   }

   /**
    * Creates and validates all node-level state for a publisher (XML profile, topic, graph registration) and
    * instantiates it through {@link #newPublisher}. Subclasses such as {@link AsyncROS2Node} reuse this.
    *
    * @param asyncQueueCapacity passed through to {@link #newPublisher}; ignored by {@link ROS2Node}
    * @return the publisher, or null if this node is closed
    */
   protected <T extends ROS2Message<T>> ROS2Publisher<T> createPublisherInternal(ROS2Topic<T> topic, ROS2QoSProfile qosProfile, int asyncQueueCapacity)
   {
      ResolvedTopic<T> resolved = resolveTopic(topic);
      ROS2QoSProfile qos = qosProfile != null ? qosProfile : topic.getQoS();

      if (!enterOperation())
      {
         return null;
      }
      try
      {
         checkNotInOwnParticipantCallback("publisher");

         ProfilesXML profilesXML = ROS2Participant.newProfilesXML();
         PublisherProfileType publisherProfile = new PublisherProfileType();
         // Prefix with "pub_" to ensure valid XML identifier
         String publisherProfileName = "pub_" + publisherIdCounter.getAndIncrement();
         publisherProfile.setProfileName(publisherProfileName);
         profilesXML.addPublisherProfile(publisherProfile);

         // Translate the ROS2QoSProfile into Fast-DDS publisher profile XML
         QoSTools.translateQoS(qos, publisherProfile, resolved.typeHash);

         ROS2Publisher<T> publisher = null;
         byte[] gid;
         // The creation lock covers the topic, the profile load and the native create (see ROS2Participant.lockCreation)
         ROS2Participant.lockCreation("publisher");
         try
         {
            TopicData topicData = participant.acquireTopic(resolved.ddsTopicName, resolved.typeName);
            try
            {
               // Synchronize profile loading and publisher creation: Fast-DDS looks the profile up without synchronization
               // inside create_datawriter_with_profile.
               synchronized (ProfilesXML.getLoadLock())
               {
                  loadEndpointProfile(profilesXML, "publisher", publisherProfileName, resolved.topic.getName());
                  ROS2Participant previousTarget = participant.enterNativeCreate();
                  try
                  {
                     publisher = newPublisher(fastddsParticipant, publisherProfileName, resolved.topic, topicData, asyncQueueCapacity);
                  }
                  finally
                  {
                     ROS2Participant.exitNativeCreate(previousTarget);
                  }
               }
               if (publisher == null)
               {
                  throw new IllegalStateException("Failed to create a publisher for topic '" + resolved.topic.getName() + "'");
               }
               gid = publisher.getGuid().getValue().clone();
            }
            catch (RuntimeException | Error e)
            {
               if (publisher != null)
               {
                  closeEndpointQuietly(publisher);
               }
               participant.releaseTopic(resolved.ddsTopicName);
               throw e;
            }
         }
         finally
         {
            ROS2Participant.unlockCreation();
         }

         synchronized (endpointRecords)
         {
            endpointRecords.add(new EndpointRecord(publisher, resolved.ddsTopicName, gid, true));
         }
         synchronized (publishers)
         {
            publishers.add(publisher);
         }

         participant.publishGraph();
         return publisher;
      }
      finally
      {
         exitOperation();
      }
   }

   /**
    * Factory hook for the publisher instance. {@link AsyncROS2Node} overrides it to create {@link AsyncROS2Publisher}s.
    */
   protected <T extends ROS2Message<T>> ROS2Publisher<T> newPublisher(long fastddsParticipant,
                                                                      String publisherProfileName,
                                                                      ROS2Topic<T> topic,
                                                                      TopicData topicData,
                                                                      int asyncQueueCapacity)
   {
      return new ROS2Publisher<>(fastddsParticipant, publisherProfileName, topic, topicData);
   }

   public <T extends ROS2Message<T>> ROS2Publisher<T> createPublisher(ROS2Topic<T> topic)
   {
      return createPublisher(topic, qosOf(topic));
   }

   /**
    * Destroy a {@link ROS2Publisher}. Do not use on publishers which were created with another instance of {@link ROS2Node}.
    * This will remove the publisher from the list of publishers within this node and call {@link ROS2Publisher#close(long)} to release the native Fast-DDS
    * publisher.
    * You do not have to call this if you call {@link ROS2Node#close()}, it will destroy all publishers created by this node for you.
    * May be called from a subscription callback; the native teardown is then deferred.
    *
    * @param publisher the publisher to destroy.
    * @return true if the node contained the publisher, and it has been removed.
    */
   public boolean destroyPublisher(ROS2Publisher<?> publisher)
   {
      if (publisher == null || !enterOperation())
      {
         return false;
      }
      try
      {
         synchronized (publishers)
         {
            if (!removeByIdentity(publishers, publisher))
            {
               return false;
            }
         }
         EndpointRecord record = removeRecord(publisher);
         publisher.close(fastddsParticipant);
         releaseEndpointResources(record);
         return true;
      }
      finally
      {
         exitOperation();
      }
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Subscriptions
    * ------------------------------------------------------------------------------------------------------------ */

   /**
    * Create a subscription using a {@link ROS2SubscriptionCallback}.
    * In the callback, you are given a {@link ROS2MessageReader} to read a sample of the {@param T} message type.
    * If you want a callback which gives you a sample of the message type directly, use
    * {@link #createSubscriptionSampler(ROS2Topic, ROS2SubscriptionCallbackSampler, ROS2QoSProfile)}.
    *
    * @param topic           the ROS 2 topic, (see {@link ROS2Topic} for how to use. Relative and private names are expanded
    *                        against this node's namespace and name.
    * @param callback        a {@link ROS2SubscriptionCallback} callback which provides access to a {@link ROS2MessageReader} for deserializing the message
    *                        sample. May be null for a polling subscription.
    *                        Has an allocation-free method, letting you reuse an instance of a {@link ROS2Message}.
    * @param matchedCallback called when a publisher is matched or unmatched; may be null
    * @param qosProfile      specify what quality-of-service settings you want for this subscription. Note: subscription and publisher QoS must match if you want
    *                        them to communicate.
    * @return the subscription instance, or null if this node is closed. You do not have to store this as a field or manage it in any way if you don't need to.
    * @throws IllegalArgumentException if the topic name is invalid, or the topic is already used with another type in this node's participant
    * @throws IllegalStateException    if Fast-DDS fails to create the subscription, or this is called from a callback of this node's participant
    */
   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic,
                                                                            ROS2SubscriptionCallback<T> callback,
                                                                            ROS2SubscriptionMatchedCallback matchedCallback,
                                                                            ROS2QoSProfile qosProfile)
   {
      ResolvedTopic<T> resolved = resolveTopic(topic);
      ROS2QoSProfile qos = qosProfile != null ? qosProfile : topic.getQoS();

      if (!enterOperation())
      {
         return null;
      }
      try
      {
         checkNotInOwnParticipantCallback("subscription");

         ProfilesXML profilesXML = ROS2Participant.newProfilesXML();
         SubscriberProfileType subscriberProfile = new SubscriberProfileType();
         // Prefix with "sub_" to ensure valid XML identifier
         String subscriberProfileName = "sub_" + subscriberIdCounter.getAndIncrement();
         subscriberProfile.setProfileName(subscriberProfileName);
         profilesXML.addSubscriberProfile(subscriberProfile);

         // Translate the ROS2QoSProfile into Fast-DDS subscriber profile XML
         QoSTools.translateQoS(qos, subscriberProfile, resolved.typeHash);

         ROS2Subscription<T> subscription = null;
         byte[] gid;
         // The creation lock covers the topic, the profile load and the native create (see ROS2Participant.lockCreation)
         ROS2Participant.lockCreation("subscription");
         try
         {
            TopicData topicData = participant.acquireTopic(resolved.ddsTopicName, resolved.typeName);
            try
            {
               // Synchronize profile loading and subscription creation: Fast-DDS looks the profile up without
               // synchronization inside create_datareader_with_profile.
               synchronized (ProfilesXML.getLoadLock())
               {
                  loadEndpointProfile(profilesXML, "subscriber", subscriberProfileName, resolved.topic.getName());
                  ROS2Participant previousTarget = participant.enterNativeCreate();
                  try
                  {
                     subscription = new ROS2Subscription<>(fastddsParticipant,
                                                           subscriberProfileName,
                                                           trackCallback(callback),
                                                           trackMatchedCallback(matchedCallback),
                                                           resolved.topic,
                                                           topicData,
                                                           options.getCallbackExecutor());
                  }
                  finally
                  {
                     ROS2Participant.exitNativeCreate(previousTarget);
                  }
               }
               gid = subscription.getGuid().getValue().clone();
            }
            catch (RuntimeException | Error e)
            {
               if (subscription != null)
               {
                  closeEndpointQuietly(subscription);
               }
               participant.releaseTopic(resolved.ddsTopicName);
               throw e;
            }
         }
         finally
         {
            ROS2Participant.unlockCreation();
         }

         synchronized (endpointRecords)
         {
            endpointRecords.add(new EndpointRecord(subscription, resolved.ddsTopicName, gid, false));
         }
         synchronized (subscriptions)
         {
            subscriptions.add(subscription);
         }

         participant.publishGraph();
         return subscription;
      }
      finally
      {
         exitOperation();
      }
   }

   /**
    * Wraps a user data callback so the node knows which participant's callback runs on the current thread.
    * <p>
    * With a {@link ROS2NodeOptions#callbackExecutor callback executor} the callback runs on an executor thread while the
    * Fast-DDS thread has already returned, so creating endpoints of this participant there is safe and the callback is
    * not recorded, unless the executor ran it inline on a native Fast-DDS thread.
    */
   private <T extends ROS2Message<T>> ROS2SubscriptionCallback<T> trackCallback(ROS2SubscriptionCallback<T> callback)
   {
      if (callback == null)
      {
         return null;
      }
      return new TrackedCallback<>(callback, participant, options.getCallbackExecutor() != null);
   }

   /**
    * See {@link #trackCallback(ROS2SubscriptionCallback)}. A class rather than a lambda: the code of a lambda is a method
    * of this class, and HotSpot creates this class's string literals on the thread that first triggers the C2
    * compilation of one of its methods, here the callback thread (see {@link HotPathText}).
    */
   private static final class TrackedCallback<T extends ROS2Message<T>> implements ROS2SubscriptionCallback<T>
   {
      private final ROS2SubscriptionCallback<T> callback;
      private final ROS2Participant participant;
      private final boolean dispatchedToExecutor;

      TrackedCallback(ROS2SubscriptionCallback<T> callback, ROS2Participant participant, boolean dispatchedToExecutor)
      {
         this.callback = callback;
         this.participant = participant;
         this.dispatchedToExecutor = dispatchedToExecutor;
      }

      @Override
      public void onMessage(ROS2MessageReader<T> reader)
      {
         boolean record = !dispatchedToExecutor || ROS2Participant.CallbackStack.isNativeCallbackThread();
         if (record)
         {
            ROS2Participant.CallbackStack.enter(participant);
         }
         try
         {
            callback.onMessage(reader);
         }
         finally
         {
            if (record)
            {
               ROS2Participant.CallbackStack.exit();
            }
         }
      }
   }

   private ROS2SubscriptionMatchedCallback trackMatchedCallback(ROS2SubscriptionMatchedCallback matchedCallback)
   {
      if (matchedCallback == null)
      {
         return null;
      }
      ROS2Participant callbackParticipant = participant;
      return (subscription, publicationGuid, matched) ->
      {
         callbackParticipant.enterUserCallback();
         try
         {
            matchedCallback.onSubscriptionMatched(subscription, publicationGuid, matched);
         }
         finally
         {
            callbackParticipant.exitUserCallback();
         }
      };
   }

   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic, ROS2SubscriptionCallback<T> callback, ROS2QoSProfile qosProfile)
   {
      return createSubscription(topic, callback, null, qosProfile);
   }

   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic,
                                                                            ROS2SubscriptionCallback<T> callback,
                                                                            ROS2SubscriptionMatchedCallback matchedCallback)
   {
      return createSubscription(topic, callback, matchedCallback, qosOf(topic));
   }

   /**
    * Create a subscription using a {@link ROS2SubscriptionCallback}.
    * In the callback, you are given a {@link ROS2MessageReader} to read a sample of the {@param T} message type.
    * If you want a callback which gives you a sample of the message type directly, use
    * {@link #createSubscriptionSampler(ROS2Topic, ROS2SubscriptionCallbackSampler, ROS2QoSProfile)}.
    * This method will create a subscription using the QoS of the topic (by default {@link ROS2QoSProfile#DEFAULT}).
    *
    * @param topic    the ROS 2 topic, (see {@link ROS2Topic} for how to use.
    * @param callback a {@link ROS2SubscriptionCallback} callback which provides access to a {@link ROS2MessageReader} for deserializing the message
    *                 sample.
    *                 Has an allocation-free method, letting you reuse an instance of a {@link ROS2Message}.
    * @return the subscription instance, you do not have to store this as a field or manage it in any way if you don't need to.
    */
   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic, ROS2SubscriptionCallback<T> callback)
   {
      return createSubscription(topic, callback, qosOf(topic));
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
    * This method will create a subscription using the QoS of the topic (by default {@link ROS2QoSProfile#DEFAULT}).
    *
    * @param topic the ROS 2 topic, (see {@link ROS2Topic} for how to use.
    * @return the subscription instance, you do not have to store this as a field or manage it in any way if you don't need to.
    */
   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscription(ROS2Topic<T> topic)
   {
      return createSubscription(topic, null, qosOf(topic));
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
      if (topic == null || topic.getType() == null)
      {
         // Report the problem the same way createSubscription does
         resolveTopic(topic);
      }

      return createSubscription(topic, new SamplerCallback<>(ROS2Message.createInstance(topic.getType()), sampler), qosProfile);
   }

   /** Reads every sample into one reused message and hands it to a {@link ROS2SubscriptionCallbackSampler}. */
   private static final class SamplerCallback<T extends ROS2Message<T>> implements ROS2SubscriptionCallback<T>
   {
      private final T sample; // null if the message type cannot be instantiated
      private final ROS2SubscriptionCallbackSampler<T> sampler;

      SamplerCallback(T sample, ROS2SubscriptionCallbackSampler<T> sampler)
      {
         this.sample = sample;
         this.sampler = sampler;
      }

      @Override
      public void onMessage(ROS2MessageReader<T> reader)
      {
         if (sample != null && reader.read(sample))
         {
            sampler.consume(sample);
         }
      }
   }

   /**
    * Create a subscription using a {@link ROS2SubscriptionCallbackSampler}.
    * In the callback, you are given a sample of {@param T} message type.
    * This method will create a subscription using the QoS of the topic (by default {@link ROS2QoSProfile#DEFAULT}).
    *
    * @param topic   the ROS 2 topic, (see {@link ROS2Topic} for how to use.
    * @param sampler a {@link ROS2SubscriptionCallbackSampler} callback which gives you the sample of a message upon receiving it from a subscription.
    *                This does not allocate a new Java object for each sample, the same one is reused.
    * @return the subscription instance, you do not have to store this as a field or manage it in any way if you don't need to.
    */
   public <T extends ROS2Message<T>> ROS2Subscription<T> createSubscriptionSampler(ROS2Topic<T> topic, ROS2SubscriptionCallbackSampler<T> sampler)
   {
      return createSubscriptionSampler(topic, sampler, qosOf(topic));
   }

   /**
    * Destroy a {@link ROS2Subscription}. Do not use on subscriptions which were created with another instance of {@link ROS2Node}.
    * This will remove the subscription from the list of subscriptions within this node and call {@link ROS2Subscription#close(long)} to release the native
    * Fast-DDS subscriber.
    * You do not have to call this if you call {@link ROS2Node#close()}, it will destroy all subscriptions created by this node for you.
    * May be called from a subscription callback (including the subscription's own); the native teardown is then deferred.
    *
    * @param subscription the subscription to destroy.
    * @return true if the node contained the subscription, and it has been removed.
    */
   public boolean destroySubscription(ROS2Subscription<?> subscription)
   {
      if (subscription == null || !enterOperation())
      {
         return false;
      }
      try
      {
         synchronized (subscriptions)
         {
            if (!removeByIdentity(subscriptions, subscription))
            {
               return false;
            }
         }
         EndpointRecord record = removeRecord(subscription);
         subscription.close(fastddsParticipant);
         releaseEndpointResources(record);
         return true;
      }
      finally
      {
         exitOperation();
      }
   }

   /**
    * Releases the topic reference of a destroyed endpoint and announces the new graph. When the endpoint was destroyed
    * from a callback, its native teardown was deferred, so the topic release is deferred too: {@link CallbackContext}
    * runs deferred tasks in order, so it runs after the endpoint was deleted.
    */
   private void releaseEndpointResources(EndpointRecord record)
   {
      participant.publishGraph();
      if (record == null)
      {
         return;
      }
      if (CallbackContext.isInCallback())
      {
         synchronized (operationGate)
         {
            pendingDeferredTasks++;
         }
         CallbackContext.runDeferred(() ->
         {
            try
            {
               participant.releaseTopic(record.ddsTopicName);
            }
            finally
            {
               synchronized (operationGate)
               {
                  pendingDeferredTasks--;
                  operationGate.notifyAll();
               }
            }
         });
      }
      else
      {
         participant.releaseTopic(record.ddsTopicName);
      }
   }

   private static void loadEndpointProfile(ProfilesXML profilesXML, String kind, String profileName, String topicName)
   {
      try
      {
         profilesXML.load();
      }
      catch (fastddsjavaException e)
      {
         jros2.logError("Failed to load " + kind + " profile '" + profileName + "' for topic '" + topicName + "'", e);
         throw new IllegalStateException("Fast-DDS rejected the " + kind + " profile '" + profileName + "' for topic '" + topicName + "'", e);
      }
   }

   private void closeEndpointQuietly(Object endpoint)
   {
      try
      {
         if (endpoint instanceof ROS2Publisher)
         {
            ((ROS2Publisher<?>) endpoint).close(fastddsParticipant);
         }
         else if (endpoint instanceof ROS2Subscription)
         {
            ((ROS2Subscription<?>) endpoint).close(fastddsParticipant);
         }
      }
      catch (Throwable t)
      {
         jros2.logError("Failed to close an endpoint of node '" + fullyQualifiedName + "'", t);
      }
   }

   private static <E> boolean removeByIdentity(List<E> list, Object element)
   {
      for (int i = 0; i < list.size(); ++i)
      {
         if (list.get(i) == element)
         {
            list.remove(i);
            return true;
         }
      }
      return false;
   }

   private EndpointRecord removeRecord(Object endpoint)
   {
      synchronized (endpointRecords)
      {
         for (int i = 0; i < endpointRecords.size(); ++i)
         {
            if (endpointRecords.get(i).endpoint == endpoint)
            {
               return endpointRecords.remove(i);
            }
         }
      }
      return null;
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Services and parameters (not supported)
    * ------------------------------------------------------------------------------------------------------------ */

   /**
    * Not supported: jros2 does not implement ROS 2 services yet.
    *
    * @throws UnsupportedOperationException always
    * @deprecated jros2 does not implement ROS 2 services; this placeholder will be replaced by a typed API.
    */
   @Deprecated
   public Object createService(Class<?> serviceType, String serviceName, Object callback)
   {
      throw new UnsupportedOperationException("ROS 2 services are not supported by jros2 yet (ROS2Node.createService). "
                                              + "Use a pair of request/response topics, or a ROS 2 client library such as rclcpp, for services.");
   }

   /**
    * Not supported: jros2 does not implement ROS 2 parameters yet.
    *
    * @throws UnsupportedOperationException always
    * @deprecated jros2 does not implement ROS 2 parameters; this placeholder will be replaced by a typed API.
    */
   @Deprecated
   public Object declareParameter(String name, Object value)
   {
      throw new UnsupportedOperationException("ROS 2 parameters are not supported by jros2 yet (ROS2Node.declareParameter). "
                                              + "Pass configuration through ROS2NodeOptions, system properties or your own topics instead.");
   }

   /**
    * Not supported: jros2 does not implement ROS 2 parameters yet.
    *
    * @throws UnsupportedOperationException always
    * @deprecated jros2 does not implement ROS 2 parameters; this placeholder will be replaced by a typed API.
    */
   @Deprecated
   public Object getParameter(String name)
   {
      throw new UnsupportedOperationException("ROS 2 parameters are not supported by jros2 yet (ROS2Node.getParameter). "
                                              + "Pass configuration through ROS2NodeOptions, system properties or your own topics instead.");
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Accessors
    * ------------------------------------------------------------------------------------------------------------ */

   /**
    * Get the node name, e.g. "talker". This is the name shown by {@code ros2 node list} (prefixed with the namespace).
    *
    * @return the name of the node
    */
   public String getName()
   {
      return name;
   }

   /**
    * Get the node namespace, e.g. "/" or "/robot".
    *
    * @return the absolute node namespace
    */
   public String getNamespace()
   {
      return namespace;
   }

   /**
    * @return the fully qualified node name, e.g. "/robot/talker"
    */
   public String getFullyQualifiedName()
   {
      return fullyQualifiedName;
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
    * @return the context whose DDS participant this node shares, or null if the node has its own participant
    */
   public ROS2Context getContext()
   {
      return context;
   }

   /**
    * Get all active publishers created by this node.
    *
    * @return an unmodifiable snapshot of the list of publishers.
    */
   public List<ROS2Publisher<?>> getPublishers()
   {
      synchronized (publishers)
      {
         return Collections.unmodifiableList(new ArrayList<>(publishers));
      }
   }

   /**
    * Get all active subscriptions created by this node.
    *
    * @return an unmodifiable snapshot of the list of subscriptions.
    */
   public List<ROS2Subscription<?>> getSubscriptions()
   {
      synchronized (subscriptions)
      {
         return Collections.unmodifiableList(new ArrayList<>(subscriptions));
      }
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

   /** The participant this node lives on (for the graph, contexts and tests). */
   ROS2Participant getParticipant()
   {
      return participant;
   }

   /**
    * @return this node's entry in the ROS graph: namespace, name and the GUIDs of its readers and writers
    */
   ROS2Graph.NodeSnapshot graphSnapshot()
   {
      List<byte[]> readers = new ArrayList<>();
      List<byte[]> writers = new ArrayList<>();
      synchronized (endpointRecords)
      {
         for (EndpointRecord record : endpointRecords)
         {
            (record.writer ? writers : readers).add(record.gid);
         }
      }
      return new ROS2Graph.NodeSnapshot(namespace, name, readers, writers);
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Close
    * ------------------------------------------------------------------------------------------------------------ */

   /**
    * Release resources and mark this node as inoperable. After close() has been called, this node will be unable to create new publishers or subscriptions.
    * This method will block and wait for:
    * 1. any currently executing {@link ROS2Publisher#publish(ROS2Message)}
    * 2. any currently executing {@link ROS2Subscription} callback
    * 3. any currently executing {@link ROS2Subscription#read()} (or other read(T), readLatest(), readLatest(T))
    * This is to ensure memory safety and guaranteed order of close operations.
    * All publishers and subscriptions will be destroyed if close() has not been called already.
    * <p>
    * When called from a subscription callback, the node is marked closed immediately and the teardown runs on a jros2
    * thread after this method returns, because Fast-DDS cannot delete endpoints from inside its own callbacks.
    */
   @Override
   public void close()
   {
      boolean defer = mustDeferNativeTeardown();

      if (!closeRequested.compareAndSet(false, true))
      {
         if (!defer)
         {
            awaitTeardown();
         }
         return;
      }

      if (defer)
      {
         markClosed();
         runDeferredTeardown(this::teardown);
      }
      else
      {
         // Wait for subclasses that use the node under the read lock
         closeLock.writeLock().lock();
         try
         {
            markClosed();
         }
         finally
         {
            closeLock.writeLock().unlock();
         }
         teardown();
      }
   }

   private void markClosed()
   {
      synchronized (operationGate)
      {
         closed = true;
      }
      activeNodes.remove(this);
   }

   /**
    * Removes the shutdown hook once the teardown is complete. A teardown deferred from a callback runs on a daemon
    * thread, so until it finishes the hook stays registered: at JVM exit it waits for the teardown, so that the
    * participant is deleted before the process exits.
    */
   private void removeShutdownHook()
   {
      if (shutdownHook != null && Thread.currentThread() != shutdownHook)
      {
         try
         {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
         }
         catch (IllegalStateException ignored)
         {
            // JVM shutdown already in progress
         }
      }
   }

   private void awaitTeardown()
   {
      try
      {
         if (!teardownDone.await(CLOSE_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
         {
            jros2.getLogger().warning("Timed out waiting for node '" + fullyQualifiedName + "' to close");
         }
      }
      catch (InterruptedException e)
      {
         Thread.currentThread().interrupt();
      }
   }

   /**
    * Waits until this node's teardown finished (for tests and {@link ROS2Context}).
    */
   boolean awaitClosed(long timeout, TimeUnit unit) throws InterruptedException
   {
      return teardownDone.await(timeout, unit);
   }

   private void teardown()
   {
      try
      {
         // Waits for subclasses that still hold the read lock (the deferred path did not take the write lock)
         closeLock.writeLock().lock();
         closeLock.writeLock().unlock();

         // Deferred topic releases were queued before a deferred teardown and have already run, so only wait for them
         // when this teardown runs on the caller's thread.
         awaitOperationsAndDeferredTasks(!onDeferredThread());

         List<ROS2Publisher<?>> publishersToClose;
         synchronized (publishers)
         {
            publishersToClose = new ArrayList<>(publishers);
            publishers.clear();
         }
         List<ROS2Subscription<?>> subscriptionsToClose;
         synchronized (subscriptions)
         {
            subscriptionsToClose = new ArrayList<>(subscriptions);
            subscriptions.clear();
         }
         List<EndpointRecord> records;
         synchronized (endpointRecords)
         {
            records = new ArrayList<>(endpointRecords);
            endpointRecords.clear();
         }
         List<String> extraReferences;
         synchronized (extraTopicReferences)
         {
            extraReferences = new ArrayList<>(extraTopicReferences);
            extraTopicReferences.clear();
         }

         for (ROS2Publisher<?> publisher : publishersToClose)
         {
            closeEndpointQuietly(publisher);
         }
         for (ROS2Subscription<?> subscription : subscriptionsToClose)
         {
            closeEndpointQuietly(subscription);
         }

         for (EndpointRecord record : records)
         {
            participant.releaseTopic(record.ddsTopicName);
         }
         for (String ddsTopicName : extraReferences)
         {
            participant.releaseTopic(ddsTopicName);
         }
      }
      catch (Throwable t)
      {
         jros2.logError("Failed to close the endpoints of node '" + fullyQualifiedName + "'", t);
      }

      finishTeardown();
   }

   /**
    * Removes the node from the graph and, for a private participant, deletes the participant. On the deferred thread,
    * endpoint teardowns deferred from callbacks may still be queued behind this teardown (a destroy that was in flight
    * when the node was closed); the participant can only be deleted after them, so this step is queued behind them.
    */
   private void finishTeardown()
   {
      if (onDeferredThread() && hasPendingDeferredTasks())
      {
         runDeferredTeardown(this::finishTeardown);
         return;
      }

      try
      {
         if (context != null)
         {
            context.detach(this); // Announces the removal in the context's graph
         }
         else
         {
            // Announces an empty node list, then deletes every remaining topic and type and the participant
            participant.close();
         }
      }
      catch (Throwable t)
      {
         jros2.logError("Failed to close node '" + fullyQualifiedName + "'", t);
      }
      finally
      {
         teardownDone.countDown();
         removeShutdownHook();
      }
   }

   private boolean hasPendingDeferredTasks()
   {
      synchronized (operationGate)
      {
         return pendingDeferredTasks > 0;
      }
   }

   /**
    * @return true once this node's teardown has completed (the participant was deleted or the node left its context)
    */
   boolean isTeardownDone()
   {
      return teardownDone.getCount() == 0;
   }

   /** Set while a deferred teardown runs, so it does not wait for tasks queued behind it. */
   private static final ThreadLocal<boolean[]> ON_DEFERRED_THREAD = ThreadLocal.withInitial(() -> new boolean[1]);

   static boolean onDeferredThread()
   {
      return ON_DEFERRED_THREAD.get()[0];
   }

   /**
    * Runs {@code task} with {@link CallbackContext#runDeferred(Runnable)}, marking the deferred thread while it runs.
    */
   static void runDeferredTeardown(Runnable task)
   {
      CallbackContext.runDeferred(() ->
      {
         boolean[] flag = ON_DEFERRED_THREAD.get();
         boolean previous = flag[0];
         flag[0] = true;
         try
         {
            task.run();
         }
         finally
         {
            flag[0] = previous;
         }
      });
   }

   /**
    * Topic reference and graph GID of an endpoint created through this node.
    */
   private static final class EndpointRecord
   {
      private final Object endpoint;
      private final String ddsTopicName;
      private final byte[] gid;
      private final boolean writer;

      private EndpointRecord(Object endpoint, String ddsTopicName, byte[] gid, boolean writer)
      {
         this.endpoint = endpoint;
         this.ddsTopicName = ddsTopicName;
         this.gid = gid;
         this.writer = writer;
      }
   }
}
