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
package us.ihmc.fastddsjava.natives;

import us.ihmc.fastddsjava.fastddsjavaException;
import us.ihmc.fastddsjava.fastddsjavaTools;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.profiles.ProfilesXML;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static us.ihmc.fastddsjava.fastddsjavaTestTools.testDomainId;
import static us.ihmc.fastddsjava.fastddsjavaTestTools.waitFor;
import static us.ihmc.fastddsjava.natives.fastddsjava.*;

/**
 * One DDS participant plus the entities a native test creates on it; {@link #close()} deletes whatever is left in a
 * valid order. Profiles are loaded once per JVM.
 */
final class NativeDdsFixture implements AutoCloseable
{
   static final String PARTICIPANT_PROFILE = "n1_native_participant";
   static final String TOPIC_PROFILE = "n1_native_topic";
   /** RELIABLE, VOLATILE, KEEP_ALL. */
   static final String RELIABLE = "n1_native_reliable";
   /** RELIABLE, VOLATILE, KEEP_ALL, PREALLOCATED history memory (fixed-size payloads). */
   static final String PREALLOCATED = "n1_native_preallocated";
   /** Writer: RELIABLE, TRANSIENT_LOCAL, KEEP_LAST 2. */
   static final String TRANSIENT_DEPTH_2 = "n1_native_transient_depth2";
   /** Reader: RELIABLE, TRANSIENT_LOCAL, KEEP_LAST 10. */
   static final String TRANSIENT_READER = "n1_native_transient_reader";

   private static final AtomicInteger UNIQUE = new AtomicInteger();
   private static boolean profilesLoaded = false;

   static
   {
      fastddsjavaNativeLibrary.load();
   }

   final long participant;
   private final List<long[]> readers = new ArrayList<>();      // {subscriber, reader}
   private final List<long[]> writers = new ArrayList<>();      // {publisher, writer}
   private final List<Long> subscribers = new ArrayList<>();
   private final List<Long> publishers = new ArrayList<>();
   private final List<Topic> topics = new ArrayList<>();

   static final class Topic
   {
      final long topic;
      final long type;
      final long typeSupport;
      final String typeName;
      final String name;

      Topic(long topic, long type, long typeSupport, String typeName, String name)
      {
         this.topic = topic;
         this.type = type;
         this.typeSupport = typeSupport;
         this.typeName = typeName;
         this.name = name;
      }
   }

   NativeDdsFixture() throws fastddsjavaException
   {
      loadProfiles();
      participant = createParticipant(PARTICIPANT_PROFILE);
      assertNotEquals(0L, participant, "createParticipant");
   }

   static String uniqueName(String prefix)
   {
      return prefix + "_" + ProcessHandle.current().pid() + "_" + UNIQUE.incrementAndGet();
   }

   Topic topic(String prefix)
   {
      return topic(uniqueName(prefix), uniqueName(prefix + "_type"));
   }

   /** A topic with its own registered type. Other participants may create the same topic name with {@link #topic(String, String)}. */
   Topic topic(String topicName, String typeName)
   {
      long type = createTopicDataWrapperType(typeName, CDR_LE);
      long typeSupport = createTypesupport(type);
      assertEquals(RETCODE_OK, registerType(participant, typeSupport), "registerType");
      long topic = createTopic(participant, type, topicName, TOPIC_PROFILE);
      assertNotEquals(0L, topic, "createTopic");
      Topic created = new Topic(topic, type, typeSupport, typeName, topicName);
      topics.add(created);
      return created;
   }

   long publisher(String profile)
   {
      long publisher = createPublisher(participant, profile);
      assertNotEquals(0L, publisher, "createPublisher");
      publishers.add(publisher);
      return publisher;
   }

   long subscriber(String profile)
   {
      long subscriber = createSubscriber(participant, profile);
      assertNotEquals(0L, subscriber, "createSubscriber");
      subscribers.add(subscriber);
      return subscriber;
   }

   /** Creates a publisher and a data writer with the given profile. */
   long writer(Topic topic, String profile)
   {
      long publisher = publisher(profile);
      long writer = createDataWriter(publisher, topic.topic, profile);
      assertNotEquals(0L, writer, "createDataWriter");
      writers.add(new long[] {publisher, writer});
      return writer;
   }

   /** Creates a subscriber and a data reader with the given profile and listener (may be 0). */
   long reader(Topic topic, String profile, long listener)
   {
      long subscriber = subscriber(profile);
      long reader = createDataReader(subscriber, topic.topic, listener, profile);
      assertNotEquals(0L, reader, "createDataReader");
      readers.add(new long[] {subscriber, reader});
      return reader;
   }

   int deleteWriter(long writer)
   {
      for (long[] entry : writers)
      {
         if (entry[1] == writer)
         {
            writers.remove(entry);
            return deleteDataWriter(entry[0], writer);
         }
      }
      throw new IllegalArgumentException("unknown writer");
   }

   int deleteReader(long reader)
   {
      for (long[] entry : readers)
      {
         if (entry[1] == reader)
         {
            readers.remove(entry);
            return deleteDataReader(entry[0], reader);
         }
      }
      throw new IllegalArgumentException("unknown reader");
   }

   static boolean waitForMatch(long writer, long reader) throws InterruptedException
   {
      return waitFor(() -> dataWriterGetPublicationMatchedCount(writer) > 0 && dataReaderGetSubscriptionMatchedCount(reader) > 0, 10000);
   }

   /** Writes with {@link fastddsjava#dataWriterWriteBuffer}, retrying while a full KEEP_ALL history times out. */
   static int writeBuffer(long writer, ByteBuffer buffer, int length)
   {
      int ret;
      int attempts = 0;
      while ((ret = dataWriterWriteBuffer(writer, buffer, length)) == RETCODE_TIMEOUT && ++attempts < 500)
         Thread.yield();
      return ret;
   }

   /** A payload of {@code size} bytes: a CDR_LE encapsulation header followed by a pattern derived from {@code seed}. */
   static byte[] payload(int size, int seed)
   {
      byte[] payload = new byte[size];
      for (int i = 0; i < size; i++)
         payload[i] = (byte) (seed * 31 + i * 7 + (i >>> 8));
      if (size >= 4)
      {
         payload[0] = 0x00;
         payload[1] = 0x01;
         payload[2] = 0x00;
         payload[3] = 0x00;
      }
      return payload;
   }

   static int align4(int size)
   {
      return (size + 3) & ~3;
   }

   /**
    * Fast-DDS pads payloads sent over a transport to a multiple of 4 bytes and the reader receives the padding (the
    * payload length comes from the RTPS submessage length); intraprocess delivery does not pad. CDR readers ignore it.
    */
   static boolean isPaddedSize(int receivedSize, int sentSize)
   {
      return receivedSize >= sentSize && receivedSize <= align4(sentSize);
   }

   static void assertPayload(byte[] expected, byte[] actual, String message)
   {
      assertNotNull(actual, message + ": no sample received");
      assertTrue(isPaddedSize(actual.length, expected.length), message + ": received " + actual.length + " bytes for a " + expected.length + "-byte payload");
      assertArrayEquals(expected, Arrays.copyOf(actual, expected.length), message);
   }

   static ByteBuffer direct(byte[] content, int capacity)
   {
      ByteBuffer buffer = ByteBuffer.allocateDirect(capacity);
      buffer.put(content);
      buffer.clear();
      return buffer;
   }

   static byte[] bytes(ByteBuffer buffer, int length)
   {
      byte[] out = new byte[length];
      ByteBuffer view = buffer.duplicate();
      view.clear();
      view.get(out, 0, length);
      return out;
   }

   @Override
   public void close()
   {
      for (long[] entry : new ArrayList<>(readers))
         assertEquals(RETCODE_OK, deleteDataReader(entry[0], entry[1]), "deleteDataReader");
      readers.clear();
      for (long[] entry : new ArrayList<>(writers))
         assertEquals(RETCODE_OK, deleteDataWriter(entry[0], entry[1]), "deleteDataWriter");
      writers.clear();
      for (long subscriber : subscribers)
         assertEquals(RETCODE_OK, deleteSubscriber(participant, subscriber), "deleteSubscriber");
      for (long publisher : publishers)
         assertEquals(RETCODE_OK, deletePublisher(participant, publisher), "deletePublisher");
      for (Topic topic : topics)
      {
         assertEquals(RETCODE_OK, deleteTopic(participant, topic.topic), "deleteTopic");
         assertEquals(RETCODE_OK, unregisterType(participant, topic.typeName), "unregisterType");
         deleteTypesupport(topic.typeSupport);
      }
      assertEquals(RETCODE_OK, deleteParticipant(participant), "deleteParticipant");
   }

   /**
    * Hand-written XML (not ProfilesXML) so that every element the native tests rely on, such as historyMemoryPolicy and
    * resourceLimitsQos, is certainly present, and so that the library-wide intraprocess setting is left untouched.
    */
   private static synchronized void loadProfiles() throws fastddsjavaException
   {
      if (profilesLoaded)
         return;

      StringBuilder xml = new StringBuilder();
      xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<dds xmlns=\"").append(ProfilesXML.FAST_DDS_NAMESPACE_URI).append("\">\n<profiles>\n");
      xml.append("<participant profile_name=\"").append(PARTICIPANT_PROFILE).append("\"><domainId>").append(testDomainId())
         .append("</domainId><rtps><useBuiltinTransports>true</useBuiltinTransports></rtps></participant>\n");
      xml.append("<topic profile_name=\"").append(TOPIC_PROFILE).append("\"/>\n");
      endpoint(xml, "data_writer", RELIABLE, "VOLATILE", "<kind>KEEP_ALL</kind>", null);
      endpoint(xml, "data_reader", RELIABLE, "VOLATILE", "<kind>KEEP_ALL</kind>", null);
      endpoint(xml, "data_writer", PREALLOCATED, "VOLATILE", "<kind>KEEP_ALL</kind>", "PREALLOCATED");
      endpoint(xml, "data_reader", PREALLOCATED, "VOLATILE", "<kind>KEEP_ALL</kind>", "PREALLOCATED");
      endpoint(xml, "data_writer", TRANSIENT_DEPTH_2, "TRANSIENT_LOCAL", "<kind>KEEP_LAST</kind><depth>2</depth>", null);
      endpoint(xml, "data_reader", TRANSIENT_READER, "TRANSIENT_LOCAL", "<kind>KEEP_LAST</kind><depth>10</depth>", null);
      xml.append("</profiles>\n</dds>\n");

      synchronized (ProfilesXML.getLoadLock())
      {
         fastddsjavaTools.retcodeThrowOnError(loadXmlProfilesString(xml.toString()));
      }
      profilesLoaded = true;
   }

   private static void endpoint(StringBuilder xml, String element, String name, String durability, String history, String memoryPolicy)
   {
      xml.append("<").append(element).append(" profile_name=\"").append(name).append("\">");
      xml.append("<topic><historyQos>").append(history).append("</historyQos>");
      xml.append("<resourceLimitsQos><max_samples>10000</max_samples><max_instances>1</max_instances>");
      xml.append("<max_samples_per_instance>10000</max_samples_per_instance><allocated_samples>64</allocated_samples></resourceLimitsQos></topic>");
      xml.append("<qos><durability><kind>").append(durability).append("</kind></durability><reliability><kind>RELIABLE</kind></reliability></qos>");
      if (memoryPolicy != null)
         xml.append("<historyMemoryPolicy>").append(memoryPolicy).append("</historyMemoryPolicy>");
      xml.append("</").append(element).append(">\n");
   }
}
