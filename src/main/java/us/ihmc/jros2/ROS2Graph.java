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

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.fastddsjavaException;
import us.ihmc.fastddsjava.natives.fastddsjava;
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.jros2.RmwDdsCommonMessages.NodeEntitiesInfo;
import us.ihmc.jros2.RmwDdsCommonMessages.ParticipantEntitiesInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import static us.ihmc.fastddsjava.fastddsjavaTools.retcodeName;
import static us.ihmc.fastddsjava.natives.fastddsjava.RETCODE_OK;

/**
 * Publishes the ROS 2 graph information of one DDS participant on the {@code ros_discovery_info} DDS topic, the way
 * {@code rmw_dds_common::GraphCache} users (rmw_fastrtps, rmw_cyclonedds, ...) do. This is what makes jros2 nodes and
 * their publishers and subscriptions visible to {@code ros2 node list}, {@code ros2 node info} and
 * {@code ros2 topic info -v}.
 * <p>
 * Every sample lists all nodes of the participant, because receivers replace their view of the participant with each
 * sample. The writer is RELIABLE + TRANSIENT_LOCAL with KEEP_LAST 1 (2 in {@link Compatibility#DUAL} mode), so late
 * joiners receive the latest state. The graph endpoint itself is never listed.
 */
final class ROS2Graph
{
   /** DDS topic name; rmw creates it with avoid_ros_namespace_conventions, so it has no "rt" prefix. */
   static final String DDS_TOPIC_NAME = "ros_discovery_info";
   static final String TYPE_NAME = ParticipantEntitiesInfo.name;
   static final String TYPE_HASH = ParticipantEntitiesInfo.typeHash;

   private static final AtomicLong graphWriterIdCounter = new AtomicLong(0);

   /**
    * Which {@code rmw_dds_common} wire format to publish. Humble and older use 24-byte GIDs, Iron and newer use 16-byte
    * GIDs, and the two are not compatible.
    */
   enum Compatibility
   {
      /** 16-byte GIDs: visible to Iron, Jazzy, Kilted, Lyrical and Rolling. */
      IRON_PLUS(RmwDdsCommonMessages.GID_SIZE_IRON_PLUS),
      /** 24-byte GIDs: visible to Foxy, Galactic and Humble. */
      HUMBLE(RmwDdsCommonMessages.GID_SIZE_HUMBLE),
      /**
       * Both formats: every update is published as a 24-byte sample followed by a 16-byte sample on a KEEP_LAST(2) writer.
       * Iron and newer apply the 16-byte sample last; Humble applies the 24-byte sample and rejects the 16-byte one. Foxy and
       * Galactic do not survive the 16-byte sample: with Fast-DDS their processes abort ({@code std::bad_alloc}), Galactic's
       * Cyclone DDS lists the node twice; native Iron and newer nodes have the same effect on them.
       */
      DUAL(RmwDdsCommonMessages.GID_SIZE_HUMBLE, RmwDdsCommonMessages.GID_SIZE_IRON_PLUS);

      private final int[] gidSizes;

      Compatibility(int... gidSizes)
      {
         this.gidSizes = gidSizes;
      }

      /** @return the GID sizes of the samples published on every update, in publication order */
      int[] gidSizes()
      {
         return gidSizes.clone();
      }

      /** @return the history depth the graph writer needs */
      int historyDepth()
      {
         return gidSizes.length;
      }
   }

   /**
    * Resolves the {@code GRAPH_COMPATIBILITY} setting.
    *
    * @param setting   AUTO (or null/empty), IRON_PLUS, HUMBLE or DUAL, case-insensitive
    * @param rosDistro the ROS_DISTRO setting, used for AUTO
    * @return HUMBLE for foxy/galactic/humble, IRON_PLUS for iron and newer, DUAL when the distro is unset or unknown
    */
   static Compatibility resolveCompatibility(String setting, String rosDistro)
   {
      String value = setting == null ? "" : setting.trim().toUpperCase(Locale.ROOT);
      switch (value)
      {
         case "IRON_PLUS":
         case "IRON+":
         case "IRON":
            return Compatibility.IRON_PLUS;
         case "HUMBLE":
            return Compatibility.HUMBLE;
         case "DUAL":
            return Compatibility.DUAL;
         case "":
         case "AUTO":
            break;
         default:
            jros2.getLogger().warning("Unknown graph compatibility '" + setting + "'; expected AUTO, IRON_PLUS, HUMBLE or DUAL. Using AUTO.");
            break;
      }

      String distro = rosDistro == null ? "" : rosDistro.trim().toLowerCase(Locale.ROOT);
      switch (distro)
      {
         case "foxy":
         case "galactic":
         case "humble":
            return Compatibility.HUMBLE;
         case "iron":
         case "jazzy":
         case "kilted":
         case "lyrical":
         case "rolling":
            return Compatibility.IRON_PLUS;
         default:
            return Compatibility.DUAL;
      }
   }

   /**
    * @return the compatibility selected by the jros2 settings (system property, environment variable, jros2.properties)
    */
   static Compatibility compatibilityFromSettings()
   {
      jros2 settings = jros2.get();
      return resolveCompatibility(settings.rawSetting(jros2Settings.Key.GRAPH_COMPATIBILITY), settings.rawSetting(jros2Settings.Key.ROS_DISTRO));
   }

   /**
    * A node of the participant: its namespace, name and the 16-byte GUIDs of its ROS readers and writers.
    */
   static final class NodeSnapshot
   {
      final String namespace;
      final String name;
      final List<byte[]> readerGuids;
      final List<byte[]> writerGuids;

      NodeSnapshot(String namespace, String name, List<byte[]> readerGuids, List<byte[]> writerGuids)
      {
         this.namespace = namespace;
         this.name = name;
         this.readerGuids = readerGuids;
         this.writerGuids = writerGuids;
      }
   }

   /**
    * Builds the ParticipantEntitiesInfo sample for one GID size.
    */
   static ParticipantEntitiesInfo createMessage(int gidSize, byte[] participantGuid, List<NodeSnapshot> nodes)
   {
      ParticipantEntitiesInfo message = new ParticipantEntitiesInfo(gidSize);
      message.getGid().setGuid(participantGuid);
      for (NodeSnapshot node : nodes)
      {
         message.getNodeEntitiesInfoSeq().add(new NodeEntitiesInfo(gidSize, node.namespace, node.name, node.readerGuids, node.writerGuids));
      }
      return message;
   }

   /**
    * @return the serialized samples (with encapsulation header) published for one update, in publication order
    */
   static List<byte[]> serializeSamples(Compatibility compatibility, byte[] participantGuid, List<NodeSnapshot> nodes)
   {
      List<byte[]> samples = new ArrayList<>();
      for (int gidSize : compatibility.gidSizes)
      {
         samples.add(createMessage(gidSize, participantGuid, nodes).toBytes());
      }
      return samples;
   }

   private final Compatibility compatibility;
   private final long fastddsParticipant;
   private final TopicData topicData;
   private final long fastddsPublisher;
   private final long fastddsDataWriter;
   private final long fastddsData;
   private final CDRBuffer buffer = new CDRBuffer();
   private List<byte[]> lastSamples = Collections.emptyList();
   private int publishCount;
   private boolean closed;

   /**
    * Creates the graph writer. The caller owns the topic registration in {@code topicData} and releases it after
    * {@link #close()}, and holds the creation lock ({@link ROS2Participant#lockCreation(String)}).
    *
    * @throws IllegalStateException if Fast-DDS cannot create the writer
    */
   ROS2Graph(Compatibility compatibility, long fastddsParticipant, TopicData topicData)
   {
      this.compatibility = compatibility;
      this.fastddsParticipant = fastddsParticipant;
      this.topicData = topicData;

      ROS2QoSProfile qos = new ROS2QoSProfile();
      qos.history(ROS2QoSProfile.History.KEEP_LAST);
      qos.depth(compatibility.historyDepth());
      qos.reliability(ROS2QoSProfile.Reliability.RELIABLE);
      qos.durability(ROS2QoSProfile.Durability.TRANSIENT_LOCAL);

      ProfilesXML profilesXML = ROS2Participant.newProfilesXML();
      PublisherProfileType publisherProfile = new PublisherProfileType();
      String profileName = "graph_pub_" + graphWriterIdCounter.getAndIncrement();
      publisherProfile.setProfileName(profileName);
      QoSTools.translateQoS(qos, publisherProfile, TYPE_HASH);
      profilesXML.addPublisherProfile(publisherProfile);

      long publisher;
      long writer = 0;
      synchronized (ProfilesXML.getLoadLock())
      {
         try
         {
            profilesXML.load();
         }
         catch (fastddsjavaException e)
         {
            throw new IllegalStateException("Failed to load the ROS graph writer profile '" + profileName + "'", e);
         }
         publisher = fastddsjava.createPublisher(fastddsParticipant, profileName);
         if (publisher != 0)
         {
            writer = fastddsjava.createDataWriter(publisher, topicData.fastddsTopic, profileName);
         }
      }

      if (publisher == 0)
      {
         throw new IllegalStateException("Fast-DDS could not create the publisher for the ROS graph topic '" + DDS_TOPIC_NAME + "'");
      }
      if (writer == 0)
      {
         int rc = fastddsjava.deletePublisher(fastddsParticipant, publisher);
         throw new IllegalStateException("Fast-DDS could not create the data writer for the ROS graph topic '" + DDS_TOPIC_NAME + "' (publisher cleanup: "
                                         + retcodeName(rc) + ")");
      }

      fastddsPublisher = publisher;
      fastddsDataWriter = writer;
      fastddsData = fastddsjava.createData(topicData.fastddsTopicDataWrapperType);
   }

   Compatibility getCompatibility()
   {
      return compatibility;
   }

   /**
    * Publishes the complete state of the participant: one sample per GID size of the compatibility mode.
    *
    * @param participantGuid the 16-byte GUID of the participant that owns the listed endpoints
    * @param nodes           every node of the participant (may be empty)
    * @return true if every sample was written
    */
   synchronized boolean publish(byte[] participantGuid, List<NodeSnapshot> nodes)
   {
      if (closed)
      {
         return false;
      }

      List<byte[]> samples = serializeSamples(compatibility, participantGuid, nodes);
      boolean ok = true;
      for (byte[] sample : samples)
      {
         buffer.rewind();
         buffer.ensureRemainingCapacity(sample.length);
         buffer.rewind();
         buffer.getBufferUnsafe().put(sample);
         fastddsjava.topicDataResize(fastddsData, sample.length);
         fastddsjava.topicDataWriteBuffer(fastddsData, buffer.getBufferUnsafe(), 0, sample.length);
         int rc = fastddsjava.dataWriterWrite(fastddsDataWriter, fastddsData);
         if (rc != RETCODE_OK)
         {
            ok = false;
            jros2.getLogger().warning("Failed to publish the ROS graph (" + DDS_TOPIC_NAME + "): " + retcodeName(rc));
         }
      }
      lastSamples = Collections.unmodifiableList(samples);
      publishCount++;
      return ok;
   }

   /** @return the samples of the last {@link #publish} call (for diagnostics and tests) */
   synchronized List<byte[]> getLastSamples()
   {
      return lastSamples;
   }

   /** @return how many updates were published */
   synchronized int getPublishCount()
   {
      return publishCount;
   }

   /** @return the 16-byte GUID of the graph writer */
   byte[] getWriterGuid()
   {
      byte[] guid = new byte[16];
      synchronized (this)
      {
         if (!closed)
         {
            fastddsjava.getWriterGuid(fastddsDataWriter, guid);
         }
      }
      return guid;
   }

   synchronized boolean isClosed()
   {
      return closed;
   }

   /**
    * Deletes the graph writer. Must not be called from a Fast-DDS callback of this participant.
    */
   synchronized void close()
   {
      if (closed)
      {
         return;
      }
      closed = true;

      int rc = fastddsjava.deleteDataWriter(fastddsPublisher, fastddsDataWriter);
      if (rc != RETCODE_OK)
      {
         jros2.getLogger().warning("Failed to delete the ROS graph writer: " + retcodeName(rc));
      }
      fastddsjava.deleteData(topicData.fastddsTopicDataWrapperType, fastddsData);
      rc = fastddsjava.deletePublisher(fastddsParticipant, fastddsPublisher);
      if (rc != RETCODE_OK)
      {
         jros2.getLogger().warning("Failed to delete the ROS graph publisher: " + retcodeName(rc));
      }
   }
}
