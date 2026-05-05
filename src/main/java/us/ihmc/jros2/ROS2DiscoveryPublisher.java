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
import rmw_dds_common.Gid;
import rmw_dds_common.NodeEntitiesInfo;
import rmw_dds_common.ParticipantEntitiesInfo;
import us.ihmc.fastddsjava.pointers.fastddsjava;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages ROS2 discovery protocol publishing for a node.
 * Publishes ParticipantEntitiesInfo messages to ros_discovery_info topic.
 * Publishes periodically to ensure Python nodes receive discovery info.
 */
class ROS2DiscoveryPublisher
{
   private final ROS2Publisher<ParticipantEntitiesInfo> publisher;
   private final Pointer participantPointer;
   private final String nodeName;
   private final String nodeNamespace;
   private final List<Pointer> readerPointers = new ArrayList<>();
   private final List<Pointer> writerPointers = new ArrayList<>();
   private final ScheduledExecutorService periodicPublisher;
   private volatile boolean closed = false;

   ROS2DiscoveryPublisher(ROS2Node node, Pointer participantPointer, String nodeName, String nodeNamespace)
   {
      this.participantPointer = participantPointer;
      this.nodeName = nodeName;
      this.nodeNamespace = nodeNamespace;

      // Create discovery topic with proper QoS
      ROS2Topic<ParticipantEntitiesInfo> discoveryTopic = new ROS2Topic<>("/ros_discovery_info", ParticipantEntitiesInfo.class);
      ROS2QoSProfile qos = new ROS2QoSProfile();
      qos.reliability(ROS2QoSProfile.Reliability.RELIABLE);
      qos.durability(ROS2QoSProfile.Durability.TRANSIENT_LOCAL);
      qos.history(ROS2QoSProfile.History.KEEP_LAST);
      qos.depth(1);

      // Create discovery publisher with standard method
      // Python interop works with periodic publishing (every 2 seconds)
      this.publisher = node.createPublisher(discoveryTopic, qos);

      // Start periodic publishing to ensure discovery messages are received
      // Publish every 2 seconds to handle late-joining nodes
      this.periodicPublisher = Executors.newSingleThreadScheduledExecutor(r -> {
         Thread t = new Thread(r, "ROS2-Discovery-Publisher");
         t.setDaemon(true);
         return t;
      });

      // Publish immediately on startup
      publishDiscoveryInfo();

      // Then publish periodically every 2 seconds
      periodicPublisher.scheduleAtFixedRate(() -> {
         if (!closed) {
            publishDiscoveryInfo();
         }
      }, 2, 2, TimeUnit.SECONDS);
   }

   /**
    * Register a reader (subscriber) with the discovery publisher
    */
   void addReader(Pointer readerPointer)
   {
      synchronized (readerPointers)
      {
         readerPointers.add(readerPointer);
      }
   }

   /**
    * Register a writer (publisher) with the discovery publisher
    */
   void addWriter(Pointer writerPointer)
   {
      synchronized (writerPointers)
      {
         writerPointers.add(writerPointer);
      }
   }

   /**
    * Unregister a reader
    */
   void removeReader(Pointer readerPointer)
   {
      synchronized (readerPointers)
      {
         readerPointers.remove(readerPointer);
      }
   }

   /**
    * Unregister a writer
    */
   void removeWriter(Pointer writerPointer)
   {
      synchronized (writerPointers)
      {
         writerPointers.remove(writerPointer);
      }
   }

   /**
    * Publish current discovery information
    */
   void publishDiscoveryInfo()
   {
      ParticipantEntitiesInfo msg = new ParticipantEntitiesInfo();

      // Set participant GUID (ROS2 uses 16-byte GUIDs: 12-byte prefix + 4-byte entityId)
      byte[] participantGuid = new byte[16];
      fastddsjava.fastddsjava_get_participant_guid(participantPointer, participantGuid);
      Gid participantGid = msg.getGid();
      for (int i = 0; i < 16; i++)
      {
         participantGid.getData()[i] = (char) (participantGuid[i] & 0xFF);
      }

      // Add node entities info
      NodeEntitiesInfo nodeInfo = new NodeEntitiesInfo();
      nodeInfo.setNodeNamespace(nodeNamespace);
      nodeInfo.setNodeName(nodeName);

      // Add reader GUIDs
      synchronized (readerPointers)
      {
         for (Pointer readerPointer : readerPointers)
         {
            byte[] readerGuid = new byte[16];
            fastddsjava.fastddsjava_get_reader_guid(readerPointer, readerGuid);
            Gid gid = new Gid();
            for (int i = 0; i < 16; i++)
            {
               gid.getData()[i] = (char) (readerGuid[i] & 0xFF);
            }
            nodeInfo.getReaderGidSeq().add(gid);
         }
      }

      // Add writer GUIDs
      synchronized (writerPointers)
      {
         for (Pointer writerPointer : writerPointers)
         {
            byte[] writerGuid = new byte[16];
            fastddsjava.fastddsjava_get_writer_guid(writerPointer, writerGuid);
            Gid gid = new Gid();
            for (int i = 0; i < 16; i++)
            {
               gid.getData()[i] = (char) (writerGuid[i] & 0xFF);
            }
            nodeInfo.getWriterGidSeq().add(gid);
         }
      }

      msg.getNodeEntitiesInfoSeq().add(nodeInfo);

      publisher.publish(msg);
   }

   void close()
   {
      closed = true;
      if (periodicPublisher != null) {
         periodicPublisher.shutdown();
         try {
            periodicPublisher.awaitTermination(1, TimeUnit.SECONDS);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      }
      publisher.close(participantPointer);
   }
}
