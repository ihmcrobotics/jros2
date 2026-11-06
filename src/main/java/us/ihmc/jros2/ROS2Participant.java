/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
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
import us.ihmc.fastddsjava.profiles.gen.TopicProfileType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import static us.ihmc.fastddsjava.fastddsjavaTools.retcodeName;
import static us.ihmc.fastddsjava.natives.fastddsjava.CDR_LE;
import static us.ihmc.fastddsjava.natives.fastddsjava.RETCODE_OK;

/**
 * One Fast-DDS DomainParticipant shared by one or more {@link ROS2Node}s: either the private participant of a node or
 * the participant of a {@link ROS2Context}.
 * <p>
 * It owns the reference-counted type and topic registrations (a DDS topic name can only exist once per participant,
 * with one type), the list of nodes that live on it and the ROS graph writer ({@link ROS2Graph}) that announces those
 * nodes. Every native handle returned by Fast-DDS is checked.
 */
final class ROS2Participant
{
   private static final AtomicLong participantIdCounter = new AtomicLong(0);
   private static final String TOPIC_PROFILE_NAME = "jros2_topic";
   /** Guarded by {@link ProfilesXML#getLoadLock()}. */
   private static boolean topicProfileLoaded = false;

   /**
    * Serializes the creation of native DDS entities (participants, topics, publishers, subscriptions) in this process,
    * always taken before {@link ProfilesXML#getLoadLock()}. Fast-DDS looks XML profiles up without synchronization inside
    * its {@code create_*_with_profile} calls, so the profile load and the native create must run under one lock, and a
    * native create can block until a Fast-DDS callback of the participant returns. A callback that then needs this lock to
    * create something elsewhere would wait for the blocked creator forever; {@link #lockCreation(String)} detects that
    * case on callback threads and throws instead.
    */
   private static final ReentrantLock CREATION_LOCK = new ReentrantLock();
   /** Participant on which the holder of {@link #CREATION_LOCK} is inside a native create call, or null. Written by the holder only. */
   private static volatile ROS2Participant nativeCreateTarget;
   /** Incremented by the holder of {@link #CREATION_LOCK} each time it enters a native create call. */
   private static volatile long nativeCreateEpoch;
   /**
    * How long a callback waits for a creator that is inside a native create on the callback's own participant before it
    * concludes that the creator waits for the callback and throws. Mutable for tests.
    */
   static volatile long callbackDeadlockConfirmationMs = 500;
   /** How long a callback waits for the creation lock at most before it throws. Mutable for tests. */
   static volatile long callbackCreationTimeoutMs = 5000;

   private final String name;
   private final int domainId;
   private final long fastddsParticipant;
   private final byte[] guid = new byte[16];

   private final Object registryLock = new Object();
   private final Map<String, TopicRegistration> topicsByDdsName = new HashMap<>();
   private final Map<String, TypeRegistration> typesByName = new HashMap<>();
   private boolean registryClosed = false;

   private final Object graphLock = new Object();
   private final List<ROS2Node> nodes = new ArrayList<>();
   private ROS2Graph graph;
   private TopicData graphTopicData;
   private boolean graphClosed = false;

   private ROS2Participant(String name, int domainId, long fastddsParticipant)
   {
      this.name = name;
      this.domainId = domainId;
      this.fastddsParticipant = fastddsParticipant;
      fastddsjava.getParticipantGuid(fastddsParticipant, guid);
   }

   /**
    * Creates a DDS participant and its ROS graph writer.
    *
    * @param participantName name of the DDS participant (diagnostics only)
    * @param domainId        validated ROS domain ID
    * @param options         participant-level options (transports, discovery, enclave, security, thread settings)
    * @param nodeClass       class used in the configuration printout
    * @throws IllegalStateException if Fast-DDS cannot create the participant or the graph writer
    */
   /**
    * @return a new {@link ProfilesXML} whose library settings carry jros2's intraprocess delivery setting
    *       ({@code FASTDDS_INTRAPROCESS_DELIVERY}). Fast-DDS applies the library settings of every profile XML it loads to the
    *       whole process, for the endpoints matched afterward, so every profile jros2 loads states jros2's mode: another
    *       component of the process that changes the process-wide default of {@link ProfilesXML} (which the profiles that
    *       fastddsjava loads itself, e.g. transport profiles, use) cannot switch jros2 nodes to another delivery mode. That
    *       default is set back to jros2's mode as well.
    */
   static ProfilesXML newProfilesXML()
   {
      String intraprocessDelivery = jros2.get().intraprocessDelivery() ? "USER_DATA_ONLY" : "OFF";
      ProfilesXML.setIntraprocessDelivery(intraprocessDelivery);
      ProfilesXML profilesXML = new ProfilesXML();
      profilesXML.getLibrarySettingsType().setIntraprocessDelivery(intraprocessDelivery);
      return profilesXML;
   }

   static ROS2Participant create(String participantName, int domainId, ROS2NodeOptions options, Class<? extends ROS2Node> nodeClass)
   {
      // Setup work that keeps the JIT from allocating on the hot threads later (once per process)
      JdkStringConstants.prepare();

      // ParticipantProfiles may load transport profiles, so it runs under the creation lock as well
      lockCreation("node or context");
      try
      {
         ProfilesXML profilesXML = newProfilesXML();
         // Prefix with "p_" to ensure valid XML identifier
         String profileName = "p_" + participantIdCounter.getAndIncrement();
         ParticipantProfiles.create(profileName, participantName, domainId, options, nodeClass, profilesXML);

         long handle;
         synchronized (ProfilesXML.getLoadLock())
         {
            try
            {
               profilesXML.load();
            }
            catch (fastddsjavaException e)
            {
               jros2.getLogger().severe("Rejected participant profile:\n" + profilesXML.marshall());
               throw new IllegalStateException("Fast-DDS rejected the participant profile '" + profileName + "' for '" + participantName + "'", e);
            }
            handle = fastddsjava.createParticipant(profileName);
         }

         if (handle == 0)
         {
            jros2.getLogger().severe("Fast-DDS could not create a participant from this profile:\n" + profilesXML.marshall());
            throw new IllegalStateException("Fast-DDS could not create the DDS participant for '" + participantName + "' in domain " + domainId + " (profile '"
                                            + profileName + "'). Check the transports, the network interface whitelist, the discovery and security settings, "
                                            + "and the Fast-DDS log output above.");
         }

         ROS2Participant participant = new ROS2Participant(participantName, domainId, handle);
         try
         {
            participant.createGraph(ROS2Graph.compatibilityFromSettings());
         }
         catch (RuntimeException | Error e)
         {
            participant.close();
            throw e;
         }
         return participant;
      }
      finally
      {
         unlockCreation();
      }
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Creation lock
    * ------------------------------------------------------------------------------------------------------------ */

   /**
    * Acquires the process-wide creation lock (reentrant). Must be released with {@link #unlockCreation()}.
    * <p>
    * On a thread that runs a subscription callback on a Fast-DDS thread, waiting for the lock can deadlock: the holder
    * may be inside a native create that Fast-DDS blocks until this callback returns (measured with Fast-DDS 3.6.2: a
    * create on the callback's participant waits for its network data and matched callbacks). There the lock is polled: if the holder
    * stays inside a native create on one of the callback's participants for {@link #callbackDeadlockConfirmationMs}, or
    * the lock is not available within {@link #callbackCreationTimeoutMs}, an {@link IllegalStateException} is thrown so
    * that the callback returns and the other thread can proceed.
    *
    * @param what what is being created, for the error message
    * @throws IllegalStateException if called from a Fast-DDS callback and the lock is held by a thread that is (or may
    *                               be) waiting for that callback
    */
   static void lockCreation(String what)
   {
      if (CREATION_LOCK.isHeldByCurrentThread() || !isInFastddsCallback())
      {
         CREATION_LOCK.lock();
         return;
      }
      if (CREATION_LOCK.tryLock())
      {
         return;
      }
      lockCreationFromCallback(what);
   }

   private static void lockCreationFromCallback(String what)
   {
      CallbackStack stack = CallbackStack.current();
      long start = System.nanoTime();
      long suspectEpoch = -1;
      long suspectSince = 0;
      boolean interrupted = false;
      try
      {
         while (true)
         {
            try
            {
               if (CREATION_LOCK.tryLock(2, TimeUnit.MILLISECONDS))
               {
                  return;
               }
            }
            catch (InterruptedException e)
            {
               interrupted = true;
            }

            long now = System.nanoTime();
            long epoch = nativeCreateEpoch;
            ROS2Participant target = nativeCreateTarget;
            if (target != null && stack.contains(target))
            {
               if (epoch != suspectEpoch)
               {
                  suspectEpoch = epoch;
                  suspectSince = now;
               }
               else if (now - suspectSince >= TimeUnit.MILLISECONDS.toNanos(callbackDeadlockConfirmationMs))
               {
                  throw new IllegalStateException("Cannot create a " + what + " from inside a subscription callback of the DDS participant '" + target.name
                                                  + "': another thread is creating a publisher or subscription on that participant, and Fast-DDS makes it "
                                                  + "wait until this callback returns, while it holds the lock this call needs. Set "
                                                  + "ROS2NodeOptions.callbackExecutor(...) on the subscribing node so callbacks run on an executor thread, or "
                                                  + "create the " + what + " from another thread.");
               }
            }
            else
            {
               suspectEpoch = -1;
            }

            if (now - start >= TimeUnit.MILLISECONDS.toNanos(callbackCreationTimeoutMs))
            {
               throw new IllegalStateException("Cannot create a " + what + " from inside a subscription callback: another thread has been creating a DDS "
                                               + "entity for more than " + callbackCreationTimeoutMs + " ms, most likely because Fast-DDS makes it wait "
                                               + "for this callback to return (with intraprocess delivery, creating an endpoint that matches the "
                                               + "callback's subscription does). Set ROS2NodeOptions.callbackExecutor(...) on the subscribing node so "
                                               + "callbacks run on an executor thread, or create the " + what + " from another thread.");
            }
         }
      }
      finally
      {
         if (interrupted)
         {
            Thread.currentThread().interrupt();
         }
      }
   }

   /** Releases {@link #lockCreation(String)}. */
   static void unlockCreation()
   {
      CREATION_LOCK.unlock();
   }

   /**
    * Marks that the holder of the creation lock (the current thread) enters a native call that creates an entity of
    * this participant and may block on its callbacks.
    *
    * @return the previous target, to pass to {@link #exitNativeCreate(ROS2Participant)}
    */
   ROS2Participant enterNativeCreate()
   {
      if (!CREATION_LOCK.isHeldByCurrentThread())
      {
         throw new IllegalStateException("enterNativeCreate() requires the creation lock");
      }
      ROS2Participant previous = nativeCreateTarget;
      nativeCreateEpoch++;
      nativeCreateTarget = this;
      return previous;
   }

   /** Ends {@link #enterNativeCreate()}. */
   static void exitNativeCreate(ROS2Participant previous)
   {
      nativeCreateTarget = previous;
   }

   /** @return the participant on which the holder of the creation lock is inside a native create, or null (for tests) */
   static ROS2Participant getNativeCreateTarget()
   {
      return nativeCreateTarget;
   }

   private void createGraph(ROS2Graph.Compatibility compatibility)
   {
      TopicData topicData = acquireTopic(ROS2Graph.DDS_TOPIC_NAME, ROS2Graph.TYPE_NAME);
      try
      {
         ROS2Graph graph = new ROS2Graph(compatibility, fastddsParticipant, topicData);
         synchronized (graphLock)
         {
            this.graph = graph;
            this.graphTopicData = topicData;
         }
      }
      catch (RuntimeException | Error e)
      {
         releaseTopic(ROS2Graph.DDS_TOPIC_NAME);
         throw e;
      }
   }

   long getFastddsParticipant()
   {
      return fastddsParticipant;
   }

   int getDomainId()
   {
      return domainId;
   }

   String getName()
   {
      return name;
   }

   /** @return a copy of the 16-byte participant GUID */
   byte[] getGuid()
   {
      return guid.clone();
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Type and topic registrations
    * ------------------------------------------------------------------------------------------------------------ */

   /**
    * Gets or creates the DDS topic {@code ddsTopicName} with the given type and takes a reference on it. Each call must
    * be matched by one {@link #releaseTopic(String)} after the endpoint that uses the topic was deleted.
    *
    * @param ddsTopicName DDS topic name, e.g. "rt/chatter" or "ros_discovery_info"
    * @param typeName     DDS type name, e.g. "std_msgs::msg::dds_::String_"
    * @throws IllegalArgumentException if the topic already exists in this participant with a different type
    * @throws IllegalStateException    if the participant is closed or Fast-DDS fails to register the type or create the topic
    */
   TopicData acquireTopic(String ddsTopicName, String typeName)
   {
      if (typeName == null || typeName.isEmpty())
      {
         throw new IllegalArgumentException("Missing DDS type name for topic '" + ddsTopicName + "'");
      }

      // Lock order: creation lock, load lock, then registry lock. Endpoint creation holds the creation lock and may run
      // user callbacks (matched, intraprocess data) that create endpoints on other participants and so come back here.
      lockCreation("topic");
      try
      {
         synchronized (ProfilesXML.getLoadLock())
         {
            synchronized (registryLock)
            {
               return acquireTopicLocked(ddsTopicName, typeName);
            }
         }
      }
      finally
      {
         unlockCreation();
      }
   }

   private TopicData acquireTopicLocked(String ddsTopicName, String typeName)
   {
      {
         if (registryClosed)
         {
            throw new IllegalStateException("The DDS participant '" + name + "' is closed");
         }

         TopicRegistration topic = topicsByDdsName.get(ddsTopicName);
         if (topic != null)
         {
            if (!topic.type.typeName.equals(typeName))
            {
               throw new IllegalArgumentException("Topic '" + rosTopicName(ddsTopicName) + "' already has type '" + topic.type.typeName
                                                  + "' in this DDS participant and cannot also be used with type '" + typeName
                                                  + "'. Use another topic name, or another ROS2Node/ROS2Context.");
            }
            topic.referenceCount++;
            return topic.topicData;
         }

         TypeRegistration type = acquireType(typeName);
         long fastddsTopic;
         try
         {
            loadTopicProfile();
            fastddsTopic = fastddsjava.createTopic(fastddsParticipant, type.fastddsTopicDataWrapperType, ddsTopicName, TOPIC_PROFILE_NAME);
         }
         catch (RuntimeException | Error e)
         {
            releaseType(type);
            throw e;
         }

         if (fastddsTopic == 0)
         {
            releaseType(type);
            throw new IllegalStateException("Fast-DDS could not create the topic '" + ddsTopicName + "' with type '" + typeName + "' in the participant '" + name
                                            + "'. See the Fast-DDS log output above.");
         }

         topic = new TopicRegistration(ddsTopicName, type, new TopicData(type.fastddsTopicDataWrapperType, type.fastddsTypeSupport, fastddsTopic));
         topic.referenceCount = 1;
         topicsByDdsName.put(ddsTopicName, topic);
         return topic.topicData;
      }
   }

   /**
    * Drops one reference on a topic. The DDS topic (and its type, when no other topic uses it) is deleted when the last
    * reference is dropped. Must only be called after the endpoint that used the topic was deleted.
    */
   void releaseTopic(String ddsTopicName)
   {
      synchronized (registryLock)
      {
         if (registryClosed)
         {
            return;
         }

         TopicRegistration topic = topicsByDdsName.get(ddsTopicName);
         if (topic == null || topic.referenceCount <= 0)
         {
            jros2.getLogger().warning("Unbalanced release of topic '" + ddsTopicName + "' in participant '" + name + "'");
            return;
         }

         topic.referenceCount--;
         if (topic.referenceCount == 0)
         {
            int rc = fastddsjava.deleteTopic(fastddsParticipant, topic.topicData.fastddsTopic);
            if (rc == RETCODE_OK)
            {
               topicsByDdsName.remove(ddsTopicName);
               releaseType(topic.type);
            }
            else
            {
               // An endpoint still uses the topic; keep the registration so it can be reused, and retry when closing
               jros2.getLogger().warning("Could not delete topic '" + ddsTopicName + "' (" + retcodeName(rc) + "); it is kept until the participant is closed");
            }
         }
      }
   }

   /** @return the reference count of a topic, or 0 if it is not registered (for tests) */
   int getTopicReferenceCount(String ddsTopicName)
   {
      synchronized (registryLock)
      {
         TopicRegistration topic = topicsByDdsName.get(ddsTopicName);
         return topic == null ? 0 : topic.referenceCount;
      }
   }

   /** @return whether a type is registered (for tests) */
   boolean isTypeRegistered(String typeName)
   {
      synchronized (registryLock)
      {
         return typesByName.containsKey(typeName);
      }
   }

   /** @return the number of registered topics, including the graph topic (for tests) */
   int getTopicCount()
   {
      synchronized (registryLock)
      {
         return topicsByDdsName.size();
      }
   }

   private TypeRegistration acquireType(String typeName)
   {
      TypeRegistration type = typesByName.get(typeName);
      if (type != null)
      {
         type.referenceCount++;
         return type;
      }

      long wrapperType = fastddsjava.createTopicDataWrapperType(typeName, CDR_LE);
      if (wrapperType == 0)
      {
         throw new IllegalStateException("Could not create the native type for '" + typeName + "'");
      }
      long typeSupport = fastddsjava.createTypesupport(wrapperType);
      if (typeSupport == 0)
      {
         throw new IllegalStateException("Could not create the native type support for '" + typeName + "'");
      }
      int rc = fastddsjava.registerType(fastddsParticipant, typeSupport);
      if (rc != RETCODE_OK)
      {
         // The TypeSupport owns the wrapper type, so this frees both
         fastddsjava.deleteTypesupport(typeSupport);
         throw new IllegalStateException("Fast-DDS could not register the type '" + typeName + "' in the participant '" + name + "': " + retcodeName(rc));
      }

      type = new TypeRegistration(typeName, wrapperType, typeSupport);
      type.referenceCount = 1;
      typesByName.put(typeName, type);
      return type;
   }

   private void releaseType(TypeRegistration type)
   {
      type.referenceCount--;
      if (type.referenceCount == 0)
      {
         int rc = fastddsjava.unregisterType(fastddsParticipant, type.typeName);
         if (rc == RETCODE_OK)
         {
            typesByName.remove(type.typeName);
            fastddsjava.deleteTypesupport(type.fastddsTypeSupport);
         }
         else
         {
            jros2.getLogger().warning("Could not unregister type '" + type.typeName + "' (" + retcodeName(rc) + "); it is kept until the participant is closed");
         }
      }
   }

   private static void loadTopicProfile()
   {
      // Called with the load lock held
      if (!topicProfileLoaded)
      {
         ProfilesXML profilesXML = newProfilesXML();
         TopicProfileType topicProfile = new TopicProfileType();
         topicProfile.setProfileName(TOPIC_PROFILE_NAME);
         profilesXML.addTopicProfile(topicProfile);
         try
         {
            profilesXML.load();
         }
         catch (fastddsjavaException e)
         {
            // Most likely already loaded by another copy of jros2 in this process; createTopic reports real problems
            jros2.getLogger().log(Level.FINE, "Could not load the topic profile '" + TOPIC_PROFILE_NAME + "'", e);
         }
         topicProfileLoaded = true;
      }
   }

   private static String rosTopicName(String ddsTopicName)
   {
      return ddsTopicName.startsWith(ROS2Names.TOPIC_PREFIX + "/") ? ddsTopicName.substring(ROS2Names.TOPIC_PREFIX.length()) : ddsTopicName;
   }

   /* ------------------------------------------------------------------------------------------------------------
    * ROS graph
    * ------------------------------------------------------------------------------------------------------------ */

   /** Adds a node to the graph of this participant and publishes the new state. */
   void addNode(ROS2Node node)
   {
      synchronized (graphLock)
      {
         if (graphClosed)
         {
            throw new IllegalStateException("The DDS participant '" + name + "' is closed");
         }
         nodes.add(node);
         publishGraphLocked();
      }
   }

   /** Removes a node from the graph of this participant and publishes the new state. */
   void removeNode(ROS2Node node)
   {
      synchronized (graphLock)
      {
         if (nodes.remove(node))
         {
            publishGraphLocked();
         }
      }
   }

   /** @return a snapshot of the nodes on this participant */
   List<ROS2Node> getNodes()
   {
      synchronized (graphLock)
      {
         return new ArrayList<>(nodes);
      }
   }

   /**
    * Publishes the full state of this participant (all nodes with all their readers and writers). Called after every
    * node and endpoint creation or destruction.
    */
   void publishGraph()
   {
      synchronized (graphLock)
      {
         publishGraphLocked();
      }
   }

   private void publishGraphLocked()
   {
      if (graphClosed || graph == null)
      {
         return;
      }
      List<ROS2Graph.NodeSnapshot> snapshots = new ArrayList<>(nodes.size());
      for (ROS2Node node : nodes)
      {
         snapshots.add(node.graphSnapshot());
      }
      graph.publish(guid, snapshots);
   }

   /** @return the graph writer, or null before it was created (for tests) */
   ROS2Graph getGraph()
   {
      synchronized (graphLock)
      {
         return graph;
      }
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Callback tracking
    * ------------------------------------------------------------------------------------------------------------ */

   /**
    * Java name of the native Fast-DDS threads (receive, event and discovery threads) when they call into Java; see
    * {@code getEnv()} in {@code jnifastddsjava.cpp}.
    */
   static final String NATIVE_CALLBACK_THREAD_NAME = "fastdds-callback";

   /**
    * @return true if the current thread is a native Fast-DDS thread attached to the JVM
    */
   static boolean isNativeCallbackThread()
   {
      return CallbackStack.isNativeCallbackThread();
   }

   /**
    * Marks the current thread as running a user callback of this participant until {@link #exitUserCallback()}.
    * Allocation-free after the first call on a thread.
    */
   void enterUserCallback()
   {
      CallbackStack.enter(this);
   }

   /** Ends {@link #enterUserCallback()}. */
   void exitUserCallback()
   {
      CallbackStack.exit();
   }

   /**
    * @return true if the current thread is inside a Fast-DDS callback ({@link CallbackContext#isInCallback()}) that
    *       belongs to an endpoint of this participant. Creating or deleting endpoints of this participant there deadlocks
    *       inside Fast-DDS.
    */
   boolean isInFastddsCallbackOfThisParticipant()
   {
      return CallbackContext.isInCallback() && CallbackStack.current().contains(this);
   }

   /**
    * @return true if the current thread is inside a Fast-DDS callback ({@link CallbackContext#isInCallback()}) of any
    *       participant, i.e. it may hold Fast-DDS locks that other threads' native calls wait for
    */
   static boolean isInFastddsCallback()
   {
      return CallbackContext.isInCallback() && CallbackStack.current().depth > 0;
   }

   /**
    * The participants whose user callbacks are running on the current thread, innermost last. The static methods are the
    * entry points of the callback path (see {@link ROS2Node}'s tracked callbacks); this class holds no string literal
    * (see {@link HotPathText}), unlike {@link ROS2Participant}.
    */
   static final class CallbackStack
   {
      private static final ThreadLocal<CallbackStack> STACK = ThreadLocal.withInitial(CallbackStack::new);
      /** Not the compile-time constant itself: javac would copy it into this class as a string literal. */
      private static final String NATIVE_THREAD_NAME = nativeCallbackThreadName();

      private ROS2Participant[] participants = new ROS2Participant[4];
      private int depth;

      /** @return the current thread's stack; its first use on a thread allocates it. */
      static CallbackStack current()
      {
         return STACK.get();
      }

      /** Marks the current thread as running a user callback of {@code participant} until {@link #exit()}. */
      static void enter(ROS2Participant participant)
      {
         STACK.get().push(participant);
      }

      /** Ends {@link #enter(ROS2Participant)}. */
      static void exit()
      {
         STACK.get().pop();
      }

      /** @return true if the current thread is a native Fast-DDS thread attached to the JVM */
      static boolean isNativeCallbackThread()
      {
         return NATIVE_THREAD_NAME.equals(Thread.currentThread().getName());
      }

      void push(ROS2Participant participant)
      {
         if (depth == participants.length)
         {
            participants = Arrays.copyOf(participants, depth * 2);
         }
         participants[depth++] = participant;
      }

      void pop()
      {
         if (depth > 0)
         {
            participants[--depth] = null;
         }
      }

      boolean contains(ROS2Participant participant)
      {
         for (int i = 0; i < depth; ++i)
         {
            if (participants[i] == participant)
            {
               return true;
            }
         }
         return false;
      }
   }

   private static String nativeCallbackThreadName()
   {
      return NATIVE_CALLBACK_THREAD_NAME;
   }

   /* ------------------------------------------------------------------------------------------------------------
    * Teardown
    * ------------------------------------------------------------------------------------------------------------ */

   /**
    * Announces an empty node list, deletes the graph writer, every remaining topic and type and the participant. All
    * endpoints of the participant must already be deleted. Must not be called from a Fast-DDS callback of this
    * participant.
    */
   void close()
   {
      synchronized (graphLock)
      {
         if (graphClosed)
         {
            return;
         }
         nodes.clear();
         publishGraphLocked();
         graphClosed = true;
         if (graph != null)
         {
            graph.close();
         }
      }
      if (graphTopicData != null)
      {
         releaseTopic(ROS2Graph.DDS_TOPIC_NAME);
      }

      synchronized (registryLock)
      {
         registryClosed = true;

         for (TopicRegistration topic : topicsByDdsName.values())
         {
            if (topic.referenceCount > 0)
            {
               jros2.getLogger().warning("Topic '" + topic.ddsTopicName + "' is still referenced " + topic.referenceCount + " time(s) while closing participant '" + name + "'");
            }
            int rc = fastddsjava.deleteTopic(fastddsParticipant, topic.topicData.fastddsTopic);
            if (rc != RETCODE_OK)
            {
               jros2.getLogger().warning("Could not delete topic '" + topic.ddsTopicName + "': " + retcodeName(rc));
            }
         }
         topicsByDdsName.clear();

         for (TypeRegistration type : typesByName.values())
         {
            int rc = fastddsjava.unregisterType(fastddsParticipant, type.typeName);
            if (rc == RETCODE_OK)
            {
               fastddsjava.deleteTypesupport(type.fastddsTypeSupport);
            }
            else
            {
               jros2.getLogger().warning("Could not unregister type '" + type.typeName + "': " + retcodeName(rc));
            }
         }
         typesByName.clear();

         int rc = fastddsjava.deleteParticipant(fastddsParticipant);
         if (rc != RETCODE_OK)
         {
            jros2.getLogger().severe("Could not delete the DDS participant '" + name + "' (domain " + domainId + "): " + retcodeName(rc)
                                     + ". Some of its publishers or subscriptions were probably not destroyed.");
         }
      }
   }

   boolean isClosed()
   {
      synchronized (registryLock)
      {
         return registryClosed;
      }
   }

   private static final class TypeRegistration
   {
      private final String typeName;
      private final long fastddsTopicDataWrapperType;
      private final long fastddsTypeSupport;
      private int referenceCount;

      private TypeRegistration(String typeName, long fastddsTopicDataWrapperType, long fastddsTypeSupport)
      {
         this.typeName = typeName;
         this.fastddsTopicDataWrapperType = fastddsTopicDataWrapperType;
         this.fastddsTypeSupport = fastddsTypeSupport;
      }
   }

   private static final class TopicRegistration
   {
      private final String ddsTopicName;
      private final TypeRegistration type;
      private final TopicData topicData;
      private int referenceCount;

      private TopicRegistration(String ddsTopicName, TypeRegistration type, TopicData topicData)
      {
         this.ddsTopicName = ddsTopicName;
         this.type = type;
         this.topicData = topicData;
      }
   }
}
