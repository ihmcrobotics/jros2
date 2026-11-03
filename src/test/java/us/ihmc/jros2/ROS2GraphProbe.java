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

import us.ihmc.fastddsjava.natives.fastddsjava;
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;
import us.ihmc.jros2.RmwDdsCommonMessages.ParticipantEntitiesInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static us.ihmc.fastddsjava.natives.fastddsjava.RETCODE_OK;

/**
 * A raw DDS reader on {@code ros_discovery_info}, like the reader in rmw's graph listener thread
 * (RELIABLE, TRANSIENT_LOCAL, KEEP_ALL), used to observe what jros2 announces.
 */
final class ROS2GraphProbe implements AutoCloseable
{
   private static final AtomicLong probeIdCounter = new AtomicLong();

   private final ROS2Participant participant;
   private final TopicData topicData;
   private final long subscriber;
   private final long reader;
   private final long data;
   private final long sampleInfo;
   private final List<byte[]> samples = new ArrayList<>();

   ROS2GraphProbe(int domainId)
   {
      participant = ROS2Participant.create("graph_probe", domainId, ROS2NodeOptions.defaults().domainId(domainId), ROS2Node.class);
      topicData = participant.acquireTopic(ROS2Graph.DDS_TOPIC_NAME, ROS2Graph.TYPE_NAME);

      ROS2QoSProfile qos = new ROS2QoSProfile();
      qos.history(ROS2QoSProfile.History.KEEP_ALL);
      qos.reliability(ROS2QoSProfile.Reliability.RELIABLE);
      qos.durability(ROS2QoSProfile.Durability.TRANSIENT_LOCAL);
      SubscriberProfileType profile = new SubscriberProfileType();
      String profileName = "graph_probe_" + probeIdCounter.getAndIncrement();
      profile.setProfileName(profileName);
      QoSTools.translateQoS(qos, profile, ROS2Graph.TYPE_HASH);
      ProfilesXML profilesXML = new ProfilesXML();
      profilesXML.addSubscriberProfile(profile);
      synchronized (ProfilesXML.getLoadLock())
      {
         try
         {
            profilesXML.load();
         }
         catch (Exception e)
         {
            throw new IllegalStateException(e);
         }
         subscriber = fastddsjava.createSubscriber(participant.getFastddsParticipant(), profileName);
         reader = fastddsjava.createDataReader(subscriber, topicData.fastddsTopic, 0, profileName);
      }
      if (subscriber == 0 || reader == 0)
      {
         throw new IllegalStateException("Could not create the graph probe reader");
      }
      data = fastddsjava.createData(topicData.fastddsTopicDataWrapperType);
      sampleInfo = fastddsjava.createSampleInfo();
   }

   /** Takes all available samples. */
   synchronized void poll()
   {
      while (fastddsjava.dataReaderTakeNextCustom(reader, data, sampleInfo) == RETCODE_OK)
      {
         if (!fastddsjava.sampleInfoValidData(sampleInfo))
         {
            continue;
         }
         int size = fastddsjava.topicDataSize(data);
         byte[] bytes = new byte[size];
         fastddsjava.topicDataRead(data, bytes, 0, size);
         samples.add(bytes);
      }
   }

   /** @return all samples received so far from the participant with the given GUID, in order */
   synchronized List<byte[]> samplesFrom(byte[] participantGuid)
   {
      poll();
      List<byte[]> result = new ArrayList<>();
      for (byte[] sample : samples)
      {
         if (sample.length >= 20 && Arrays.equals(Arrays.copyOfRange(sample, 4, 20), participantGuid))
         {
            result.add(sample);
         }
      }
      return result;
   }

   /**
    * Waits until the latest sample of a participant, decoded with {@code gidSize}, satisfies {@code condition}.
    *
    * @return the matching message, or null on timeout
    */
   ParticipantEntitiesInfo awaitLatest(byte[] participantGuid, int gidSize, Predicate<ParticipantEntitiesInfo> condition, long timeoutMs)
         throws InterruptedException
   {
      long deadline = System.currentTimeMillis() + timeoutMs;
      while (System.currentTimeMillis() < deadline)
      {
         List<byte[]> fromParticipant = samplesFrom(participantGuid);
         for (int i = fromParticipant.size() - 1; i >= 0; --i)
         {
            byte[] sample = fromParticipant.get(i);
            if (sampleGidSize(sample) != gidSize)
            {
               continue;
            }
            ParticipantEntitiesInfo message = ParticipantEntitiesInfo.fromBytes(sample, gidSize);
            if (condition.test(message))
            {
               return message;
            }
            break; // only the latest sample of that size counts
         }
         Thread.sleep(50);
      }
      return null;
   }

   /**
    * Guesses the GID size of a sample: bytes 20..27 of a 24-byte sample are the GID's zero padding.
    */
   static int sampleGidSize(byte[] sample)
   {
      if (sample.length >= 32)
      {
         boolean zeroPadding = true;
         for (int i = 20; i < 28; ++i)
         {
            zeroPadding &= sample[i] == 0;
         }
         if (zeroPadding)
         {
            try
            {
               ParticipantEntitiesInfo.fromBytes(sample, 24);
               return 24;
            }
            catch (IllegalArgumentException ignored)
            {
               // Not a 24-byte sample
            }
         }
      }
      return 16;
   }

   @Override
   public synchronized void close()
   {
      fastddsjava.deleteDataReader(subscriber, reader);
      fastddsjava.deleteSubscriber(participant.getFastddsParticipant(), subscriber);
      fastddsjava.deleteData(topicData.fastddsTopicDataWrapperType, data);
      fastddsjava.deleteSampleInfo(sampleInfo);
      participant.releaseTopic(ROS2Graph.DDS_TOPIC_NAME);
      participant.close();
   }
}
