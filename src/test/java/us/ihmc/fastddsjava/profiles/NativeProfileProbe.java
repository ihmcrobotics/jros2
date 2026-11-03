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
package us.ihmc.fastddsjava.profiles;

import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.natives.fastddsjava;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Loads Fast-DDS XML through the natives and creates the entities jros2 creates from it (participant, type, topic, publisher,
 * data writer, subscriber, data reader), mirroring the JNI sequence of {@code ROS2Node}/{@code ROS2Publisher}/{@code ROS2Subscription}.
 * Tests using it create DDS participants and must run under the shared DDS lock in their own domain.
 */
public final class NativeProfileProbe
{
   private static final AtomicLong UNIQUE = new AtomicLong();

   static
   {
      fastddsjavaNativeLibrary.load();
   }

   private NativeProfileProbe()
   {
   }

   /**
    * @return a suffix that makes profile names unique in this process (Fast-DDS keeps the first profile of a name)
    */
   public static String uniqueSuffix()
   {
      return "_g2_" + UNIQUE.incrementAndGet();
   }

   /**
    * @return the domain of this test run (ROS_DOMAIN_ID, as for every other jros2 test), default 0
    */
   public static int testDomainId()
   {
      String value = System.getenv("ROS_DOMAIN_ID");
      return value == null || value.isEmpty() ? 0 : Integer.parseInt(value.trim());
   }

   /**
    * @return the return code of {@code DomainParticipantFactory::load_XML_profiles_string}
    */
   public static int load(String xml)
   {
      synchronized (ProfilesXML.getLoadLock())
      {
         return fastddsjava.loadXmlProfilesString(xml);
      }
   }

   /**
    * Loads {@code xml} (if non-null), then creates and deletes every entity. Fails the test with the step that failed.
    *
    * @param xml                the XML to load first, or null if the profiles are already loaded
    * @param participantProfile participant profile name
    * @param topicProfile       topic profile name, or null to skip the topic and endpoints
    * @param writerProfile      data_writer profile name, or null to skip the writer
    * @param readerProfile      data_reader profile name, or null to skip the reader
    */
   public static void loadAndCreate(String xml, String participantProfile, String topicProfile, String writerProfile, String readerProfile)
   {
      if (xml != null)
      {
         assertEquals(fastddsjava.RETCODE_OK, load(xml), () -> "load_XML_profiles_string failed for\n" + xml);
      }

      long participant = fastddsjava.createParticipant(participantProfile);
      assertNotEquals(0L, participant, () -> "createParticipant(" + participantProfile + ") returned NULL for\n" + xml);
      long wrapperType = 0;
      long typeSupport = 0;
      long topic = 0;
      long publisher = 0;
      long writer = 0;
      long subscriber = 0;
      long reader = 0;
      try
      {
         if (topicProfile == null)
         {
            return;
         }
         wrapperType = fastddsjava.createTopicDataWrapperType("std_msgs::msg::dds_::String_", fastddsjava.CDR_LE);
         typeSupport = fastddsjava.createTypesupport(wrapperType);
         assertEquals(fastddsjava.RETCODE_OK, fastddsjava.registerType(participant, typeSupport));
         topic = fastddsjava.createTopic(participant, wrapperType, "rt/g2_profile_probe" + uniqueSuffix(), topicProfile);
         assertNotEquals(0L, topic, "createTopic returned NULL");

         if (writerProfile != null)
         {
            publisher = fastddsjava.createPublisher(participant, writerProfile);
            assertNotEquals(0L, publisher, () -> "createPublisher(" + writerProfile + ") returned NULL for\n" + xml);
            writer = fastddsjava.createDataWriter(publisher, topic, writerProfile);
            assertNotEquals(0L, writer, () -> "createDataWriter(" + writerProfile + ") returned NULL for\n" + xml);
         }
         if (readerProfile != null)
         {
            subscriber = fastddsjava.createSubscriber(participant, readerProfile);
            assertNotEquals(0L, subscriber, () -> "createSubscriber(" + readerProfile + ") returned NULL for\n" + xml);
            reader = fastddsjava.createDataReader(subscriber, topic, 0, readerProfile);
            assertNotEquals(0L, reader, () -> "createDataReader(" + readerProfile + ") returned NULL for\n" + xml);
         }
      }
      finally
      {
         if (reader != 0)
         {
            fastddsjava.deleteDataReader(subscriber, reader);
         }
         if (subscriber != 0)
         {
            fastddsjava.deleteSubscriber(participant, subscriber);
         }
         if (writer != 0)
         {
            fastddsjava.deleteDataWriter(publisher, writer);
         }
         if (publisher != 0)
         {
            fastddsjava.deletePublisher(participant, publisher);
         }
         if (topic != 0)
         {
            fastddsjava.deleteTopic(participant, topic);
         }
         if (typeSupport != 0)
         {
            fastddsjava.unregisterType(participant, fastddsjava.topicDataWrapperTypeGetName(wrapperType));
            fastddsjava.deleteTypesupport(typeSupport);
         }
         assertEquals(fastddsjava.RETCODE_OK, fastddsjava.deleteParticipant(participant));
      }
   }
}
