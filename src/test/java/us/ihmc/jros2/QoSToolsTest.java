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

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;
import us.ihmc.jros2.ROS2QoSProfile.Durability;
import us.ihmc.jros2.ROS2QoSProfile.History;
import us.ihmc.jros2.ROS2QoSProfile.Liveliness;
import us.ihmc.jros2.ROS2QoSProfile.Reliability;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mapping table tests: every {@link ROS2QoSProfile} policy and edge case translated by {@link QoSTools} must give the Fast-DDS
 * XML that reproduces rmw_fastrtps (Jazzy) {@code fill_entity_qos_from_profile} plus its endpoint settings.
 */
public class QoSToolsTest
{
   /** RIHS01 hash of std_msgs/msg/String (Jazzy). */
   static final String STRING_TYPE_HASH = "RIHS01_df668c740482bbd48fb39d76a70dfd4bd59db1288021743503259e948f6b1a18";
   /** "typehash=RIHS01_df66...1a18;" as octets, as validated against Jazzy in the research (81 bytes, no NUL). */
   static final String STRING_TYPE_HASH_OCTETS = "74.79.70.65.68.61.73.68.3d.52.49.48.53.30.31.5f.64.66.36.36.38.63.37.34.30.34.38.32.62.62.64.34.38.66.62.33.39.64.37.36.61.37.30.64.66.64.34.62.64.35.39.64.62.31.32.38.38.30.32.31.37.34.33.35.30.33.32.35.39.65.39.34.38.66.36.62.31.61.31.38.3b";

   static String writerXml(ROS2QoSProfile qos, String typeHash)
   {
      PublisherProfileType profile = new PublisherProfileType();
      profile.setProfileName("pub_test");
      QoSTools.translateQoS(qos, profile, typeHash);
      ProfilesXML profilesXML = new ProfilesXML();
      profilesXML.addPublisherProfile(profile);
      String xml = profilesXML.marshall();
      return xml.substring(xml.indexOf("<data_writer"), xml.indexOf("</data_writer>") + "</data_writer>".length());
   }

   static String readerXml(ROS2QoSProfile qos, String typeHash)
   {
      SubscriberProfileType profile = new SubscriberProfileType();
      profile.setProfileName("sub_test");
      QoSTools.translateQoS(qos, profile, typeHash);
      ProfilesXML profilesXML = new ProfilesXML();
      profilesXML.addSubscriberProfile(profile);
      String xml = profilesXML.marshall();
      return xml.substring(xml.indexOf("<data_reader"), xml.indexOf("</data_reader>") + "</data_reader>".length());
   }

   private static void assertContains(String xml, String expected)
   {
      assertTrue(xml.contains(expected), () -> "Expected '" + expected + "' in\n" + xml);
   }

   private static void assertNotContains(String xml, String unexpected)
   {
      assertFalse(xml.contains(unexpected), () -> "Did not expect '" + unexpected + "' in\n" + xml);
   }

   @Test
   public void testDefaultProfileWriterAndReader()
   {
      String writer = writerXml(ROS2QoSProfile.DEFAULT, null);
      assertEquals("<data_writer profile_name=\"pub_test\">" + "<topic><historyQos><kind>KEEP_LAST</kind><depth>10</depth></historyQos></topic>" + "<qos>"
                   + "<durability><kind>VOLATILE</kind></durability>" + "<reliability><kind>RELIABLE</kind></reliability>"
                   + "<publishMode><kind>SYNCHRONOUS</kind></publishMode>" + "<data_sharing><kind>OFF</kind></data_sharing>" + "</qos>"
                   + "<historyMemoryPolicy>PREALLOCATED_WITH_REALLOC</historyMemoryPolicy>" + "</data_writer>", writer);

      String reader = readerXml(ROS2QoSProfile.DEFAULT, null);
      assertEquals("<data_reader profile_name=\"sub_test\">" + "<topic><historyQos><kind>KEEP_LAST</kind><depth>10</depth></historyQos></topic>" + "<qos>"
                   + "<durability><kind>VOLATILE</kind></durability>" + "<reliability><kind>RELIABLE</kind></reliability>"
                   + "<data_sharing><kind>OFF</kind></data_sharing>" + "</qos>" + "<historyMemoryPolicy>PREALLOCATED_WITH_REALLOC</historyMemoryPolicy>"
                   + "</data_reader>", reader);
      assertNotContains(reader, "publishMode");
   }

   @Test
   public void testSystemDefaultLeavesFastDDSDefaults()
   {
      for (String xml : new String[] {writerXml(ROS2QoSProfile.SYSTEM_DEFAULT, null), readerXml(ROS2QoSProfile.SYSTEM_DEFAULT, null)})
      {
         assertNotContains(xml, "<topic>");
         assertNotContains(xml, "historyQos");
         assertNotContains(xml, "<durability>");
         assertNotContains(xml, "<reliability>");
         assertNotContains(xml, "<liveliness>");
         assertNotContains(xml, "<deadline>");
         assertNotContains(xml, "<lifespan>");
         // The rmw_fastrtps endpoint settings are always applied
         assertContains(xml, "<data_sharing><kind>OFF</kind></data_sharing>");
         assertContains(xml, "<historyMemoryPolicy>PREALLOCATED_WITH_REALLOC</historyMemoryPolicy>");
      }
      assertContains(writerXml(ROS2QoSProfile.SYSTEM_DEFAULT, null), "<publishMode><kind>SYNCHRONOUS</kind></publishMode>");
   }

   @Test
   public void testHistoryAndDepth()
   {
      ROS2QoSProfile qos = new ROS2QoSProfile();
      qos.history(History.KEEP_ALL);
      qos.depth(7);
      // rmw applies the depth to KEEP_ALL too
      assertContains(writerXml(qos, null), "<topic><historyQos><kind>KEEP_ALL</kind><depth>7</depth></historyQos></topic>");

      // depth 0 = system default: never written (Fast-DDS rejects KEEP_LAST depth 0)
      qos.history(History.KEEP_LAST);
      qos.depth(0);
      assertContains(readerXml(qos, null), "<topic><historyQos><kind>KEEP_LAST</kind></historyQos></topic>");
      assertNotContains(readerXml(qos, null), "<depth>");

      // SYSTEM_DEFAULT history with a depth: only the depth (rmw raises the default depth)
      qos.history(History.SYSTEM_DEFAULT);
      qos.depth(42);
      assertContains(writerXml(qos, null), "<topic><historyQos><depth>42</depth></historyQos></topic>");

      // Neither: no topic element at all
      qos.depth(0);
      assertNotContains(writerXml(qos, null), "<topic>");

      // Large depth
      qos.history(History.KEEP_LAST);
      qos.depth(Integer.MAX_VALUE);
      assertContains(writerXml(qos, null), "<depth>2147483647</depth>");
   }

   @Test
   public void testReliabilityAndDurabilityKinds()
   {
      ROS2QoSProfile qos = new ROS2QoSProfile();
      qos.reliability(Reliability.BEST_EFFORT);
      qos.durability(Durability.TRANSIENT_LOCAL);
      String writer = writerXml(qos, null);
      assertContains(writer, "<reliability><kind>BEST_EFFORT</kind></reliability>");
      assertContains(writer, "<durability><kind>TRANSIENT_LOCAL</kind></durability>");

      qos.reliability(Reliability.SYSTEM_DEFAULT);
      qos.durability(Durability.SYSTEM_DEFAULT);
      String reader = readerXml(qos, null);
      assertNotContains(reader, "<reliability>");
      assertNotContains(reader, "<durability>");
   }

   @Test
   public void testDeadlineAndLifespan()
   {
      ROS2QoSProfile qos = new ROS2QoSProfile();
      qos.deadline(Duration.ofMillis(1500));
      qos.lifespan(Duration.ofSeconds(2));
      String writer = writerXml(qos, null);
      assertContains(writer, "<deadline><period><sec>1</sec><nanosec>500000000</nanosec></period></deadline>");
      assertContains(writer, "<lifespan><duration><sec>2</sec><nanosec>0</nanosec></duration></lifespan>");

      // Unspecified (0) is omitted
      qos.deadline(Duration.ZERO);
      qos.lifespan(ROS2QoSProfile.DURATION_UNSPECIFIED);
      String reader = readerXml(qos, null);
      assertNotContains(reader, "<deadline>");
      assertNotContains(reader, "<lifespan>");

      // Infinite
      qos.deadline(ROS2QoSProfile.DURATION_INFINITE);
      qos.lifespan(ROS2QoSProfile.DURATION_INFINITE);
      reader = readerXml(qos, null);
      assertContains(reader, "<deadline><period><sec>DURATION_INFINITY</sec></period></deadline>");
      assertContains(reader, "<lifespan><duration><sec>DURATION_INFINITY</sec></duration></lifespan>");

      // Huge values are infinite for DDS (int32 seconds; Fast-DDS would wrap them into negative values)
      qos.deadline(Duration.ofSeconds(5_000_000_000L));
      qos.lifespan(Duration.ofSeconds(Integer.MAX_VALUE));
      writer = writerXml(qos, null);
      assertContains(writer, "<deadline><period><sec>DURATION_INFINITY</sec></period></deadline>");
      assertContains(writer, "<lifespan><duration><sec>DURATION_INFINITY</sec></duration></lifespan>");

      // The largest finite value stays finite
      qos.deadline(Duration.ofSeconds(Integer.MAX_VALUE - 1L, 999_999_999));
      assertContains(writerXml(qos, null), "<deadline><period><sec>2147483646</sec><nanosec>999999999</nanosec></period></deadline>");

      // Nanosecond-only values
      qos.deadline(Duration.ofNanos(1));
      assertContains(writerXml(qos, null), "<deadline><period><sec>0</sec><nanosec>1</nanosec></period></deadline>");
   }

   @Test
   public void testLiveliness()
   {
      ROS2QoSProfile qos = new ROS2QoSProfile();

      // SYSTEM_DEFAULT kind and no lease: nothing
      assertNotContains(writerXml(qos, null), "<liveliness>");

      // The kind is written even without a lease (rmw sets it regardless)
      qos.liveliness(Liveliness.MANUAL_BY_TOPIC);
      assertContains(writerXml(qos, null), "<liveliness><kind>MANUAL_BY_TOPIC</kind></liveliness>");
      qos.liveliness(Liveliness.AUTOMATIC);
      assertContains(readerXml(qos, null), "<liveliness><kind>AUTOMATIC</kind></liveliness>");

      // A lease adds announcement_period = 2/3 lease
      qos.liveliness(Liveliness.MANUAL_BY_TOPIC);
      qos.leaseDuration(Duration.ofSeconds(3));
      assertContains(writerXml(qos, null),
                     "<liveliness><kind>MANUAL_BY_TOPIC</kind><lease_duration><sec>3</sec><nanosec>0</nanosec></lease_duration>"
                     + "<announcement_period><sec>2</sec><nanosec>0</nanosec></announcement_period></liveliness>");

      qos.leaseDuration(Duration.ofSeconds(1));
      assertContains(readerXml(qos, null), "<announcement_period><sec>0</sec><nanosec>666666666</nanosec></announcement_period>");

      // Lease without kind
      qos.liveliness(Liveliness.SYSTEM_DEFAULT);
      qos.leaseDuration(Duration.ofMillis(300));
      assertContains(writerXml(qos, null),
                     "<liveliness><lease_duration><sec>0</sec><nanosec>300000000</nanosec></lease_duration>"
                     + "<announcement_period><sec>0</sec><nanosec>200000000</nanosec></announcement_period></liveliness>");

      // Infinite lease: infinite announcement period
      qos.leaseDuration(ROS2QoSProfile.DURATION_INFINITE);
      assertContains(writerXml(qos, null),
                     "<liveliness><lease_duration><sec>DURATION_INFINITY</sec></lease_duration><announcement_period><sec>DURATION_INFINITY</sec></announcement_period></liveliness>");

      // Largest finite lease: the 2/3 computation must not overflow
      qos.leaseDuration(Duration.ofSeconds(Integer.MAX_VALUE - 1L, 999_999_999));
      assertContains(writerXml(qos, null), "<announcement_period><sec>1431655764</sec><nanosec>666666666</nanosec></announcement_period>");
   }

   @Test
   public void testMaxBlockingTime()
   {
      ROS2QoSProfile qos = new ROS2QoSProfile();
      assertNotContains(writerXml(qos, null), "max_blocking_time");

      qos.maxBlockingTime(Duration.ofMillis(5));
      assertContains(writerXml(qos, null), "<reliability><kind>RELIABLE</kind><max_blocking_time><sec>0</sec><nanosec>5000000</nanosec></max_blocking_time></reliability>");

      // Infinite is never written: Fast-DDS refuses RELIABLE endpoints with an infinite blocking time
      qos.maxBlockingTime(ROS2QoSProfile.DURATION_INFINITE);
      String writer = writerXml(qos, null);
      assertContains(writer, "<max_blocking_time><sec>2147483646</sec><nanosec>999999999</nanosec></max_blocking_time>");
      assertNotContains(writer, "DURATION_INFINITY");

      // SYSTEM_DEFAULT reliability with a blocking time: only the blocking time
      qos.reliability(Reliability.SYSTEM_DEFAULT);
      qos.maxBlockingTime(Duration.ofSeconds(1));
      assertContains(readerXml(qos, null), "<reliability><max_blocking_time><sec>1</sec><nanosec>0</nanosec></max_blocking_time></reliability>");
   }

   @Test
   public void testTypeHashUserData()
   {
      String writer = writerXml(ROS2QoSProfile.DEFAULT, STRING_TYPE_HASH);
      assertContains(writer, "<userData><value>" + STRING_TYPE_HASH_OCTETS + "</value></userData>");
      String reader = readerXml(ROS2QoSProfile.SENSOR_DATA, STRING_TYPE_HASH);
      assertContains(reader, "<userData><value>" + STRING_TYPE_HASH_OCTETS + "</value></userData>");

      // Exactly the ASCII bytes of "typehash=<hash>;": 81 octets, no NUL
      String[] octets = STRING_TYPE_HASH_OCTETS.split("\\.");
      assertEquals(81, octets.length);
      byte[] bytes = new byte[octets.length];
      for (int i = 0; i < octets.length; ++i)
      {
         bytes[i] = (byte) Integer.parseInt(octets[i], 16);
      }
      assertEquals("typehash=" + STRING_TYPE_HASH + ";", new String(bytes, StandardCharsets.US_ASCII));

      // No hash: no user data
      assertNotContains(writerXml(ROS2QoSProfile.DEFAULT, null), "userData");
      assertNotContains(readerXml(ROS2QoSProfile.DEFAULT, null), "userData");

      assertThrows(IllegalArgumentException.class, () -> writerXml(ROS2QoSProfile.DEFAULT, "RIHS01_bad hash"));
      assertThrows(IllegalArgumentException.class, () -> writerXml(ROS2QoSProfile.DEFAULT, "RIHS01_x;y"));
      assertThrows(IllegalArgumentException.class, () -> writerXml(ROS2QoSProfile.DEFAULT, "RIHS01_é"));
   }

   @Test
   public void testRosoutPreset()
   {
      String writer = writerXml(ROS2QoSProfile.ROSOUT, null);
      assertContains(writer, "<historyQos><kind>KEEP_LAST</kind><depth>1000</depth></historyQos>");
      assertContains(writer, "<durability><kind>TRANSIENT_LOCAL</kind></durability>");
      assertContains(writer, "<lifespan><duration><sec>10</sec><nanosec>0</nanosec></duration></lifespan>");
      assertContains(writer, "<reliability><kind>RELIABLE</kind></reliability>");
   }

   @Test
   public void testTwoArgumentOverloadsAdvertiseNoTypeHash()
   {
      PublisherProfileType publisherProfile = new PublisherProfileType();
      QoSTools.translateQoS(ROS2QoSProfile.DEFAULT, publisherProfile);
      assertNull(publisherProfile.getQos().getUserData());
      assertEquals("SYNCHRONOUS", publisherProfile.getQos().getPublishMode().getKind());

      SubscriberProfileType subscriberProfile = new SubscriberProfileType();
      QoSTools.translateQoS(ROS2QoSProfile.DEFAULT, subscriberProfile);
      assertNull(subscriberProfile.getQos().getUserData());
      assertEquals("OFF", subscriberProfile.getQos().getDataSharing().getKind());
   }
}
