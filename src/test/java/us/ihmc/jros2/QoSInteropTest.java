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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import us.ihmc.fastddsjava.natives.fastddsjava;
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.TransportDescriptorTypeTools;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;
import us.ihmc.fastddsjava.profiles.gen.TopicProfileType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Interop: the QoS jros2 advertises, as reported by {@code ros2 topic info -v} of a real ROS 2 installation (Galactic or newer, for
 * {@code --spin-time}; the type hashes Iron or newer). Creates DDS participants: run under the shared DDS lock in this workstream's
 * domain.
 */
public class QoSInteropTest
{
   static
   {
      jros2.load();
   }

   /**
    * {@code ros2 topic info -v --no-daemon --spin-time} printing the QoS policies by name ("Reliability: RELIABLE"): Galactic and
    * newer. Foxy has neither option and prints "RMW_QOS_POLICY_RELIABILITY_RELIABLE".
    */
   static boolean isTopicInfoAvailable()
   {
      return ROS2TestTools.isROS2CLIAvailable() && ROS2TestTools.supportsTopicInfoSpinTime();
   }

   /** {@code ros2 topic info -v} prints the endpoint type hash ("Topic type hash: RIHS01_...") since Iron. */
   static boolean isTypeHashAvailable()
   {
      return isTopicInfoAvailable() && !ROS2TestTools.ROS_DISTRO.equals("galactic") && !ROS2TestTools.ROS_DISTRO.equals("humble");
   }

   private static String topicInfo(int domainId, String topicName) throws IOException, InterruptedException
   {
      ProcessBuilder processBuilder = new ProcessBuilder("bash",
                                                         "-c",
                                                         "source /opt/ros/" + ROS2TestTools.ROS_DISTRO + "/setup.bash && ros2 topic info -v " + topicName
                                                         + " --no-daemon --spin-time 5");
      processBuilder.environment().put("ROS_DOMAIN_ID", String.valueOf(domainId));
      processBuilder.environment().remove("ROS_AUTOMATIC_DISCOVERY_RANGE");
      processBuilder.environment().remove("ROS_LOCALHOST_ONLY");
      processBuilder.environment().remove("ROS_STATIC_PEERS");
      processBuilder.redirectErrorStream(true);
      Process process = processBuilder.start();
      StringBuilder output = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
         String line;
         while ((line = reader.readLine()) != null)
         {
            output.append(line).append('\n');
         }
      }
      assertTrue(process.waitFor(60, TimeUnit.SECONDS));
      return output.toString();
   }

   private static String endpointSection(String info, String kind)
   {
      // Each endpoint block starts with "Node name:"; the type hash is printed before "Endpoint type:"
      int endpointType = info.indexOf("Endpoint type: " + kind);
      assertTrue(endpointType >= 0, () -> "No " + kind + " endpoint in\n" + info);
      int start = Math.max(0, info.lastIndexOf("Node name:", endpointType));
      int end = info.indexOf("Node name:", endpointType);
      return end < 0 ? info.substring(start) : info.substring(start, end);
   }

   private static void assertQoS(String section, String reliability, String durability)
   {
      assertTrue(section.contains("Reliability: " + reliability), () -> "Expected reliability " + reliability + " in\n" + section);
      assertTrue(section.contains("Durability: " + durability), () -> "Expected durability " + durability + " in\n" + section);
   }

   @Test
   @Timeout(180)
   @EnabledIf("isTopicInfoAvailable")
   public void testAdvertisedQoSAsSeenByROS2() throws IOException, InterruptedException
   {
      ROS2Node node = new ROS2Node("g2_qos_interop");
      try
      {
         String suffix = Long.toString(System.nanoTime());
         String defaultTopic = "/g2_qos_default_" + suffix;
         String sensorTopic = "/g2_qos_sensor_" + suffix;
         String latchedTopic = "/g2_qos_latched_" + suffix;

         node.createPublisher(new ROS2Topic<>(defaultTopic, std_msgs.String_.class), ROS2QoSProfile.DEFAULT);
         node.createSubscription(new ROS2Topic<>(defaultTopic, std_msgs.String_.class), ROS2QoSProfile.DEFAULT);
         node.createPublisher(new ROS2Topic<>(sensorTopic, std_msgs.String_.class), ROS2QoSProfile.SENSOR_DATA);
         node.createSubscription(new ROS2Topic<>(sensorTopic, std_msgs.String_.class), ROS2QoSProfile.SENSOR_DATA);
         ROS2QoSProfile latched = new ROS2QoSProfile();
         latched.durability(ROS2QoSProfile.Durability.TRANSIENT_LOCAL);
         latched.deadline(Duration.ofMillis(1500));
         latched.liveliness(ROS2QoSProfile.Liveliness.MANUAL_BY_TOPIC);
         latched.leaseDuration(Duration.ofSeconds(3));
         node.createPublisher(new ROS2Topic<>(latchedTopic, std_msgs.String_.class), latched);

         String defaultInfo = topicInfo(node.getDomainId(), defaultTopic);
         // DEFAULT is RELIABLE / VOLATILE for publishers and subscriptions (behaviour change: subscriptions were BEST_EFFORT)
         assertQoS(endpointSection(defaultInfo, "PUBLISHER"), "RELIABLE", "VOLATILE");
         assertQoS(endpointSection(defaultInfo, "SUBSCRIPTION"), "RELIABLE", "VOLATILE");

         // Generated message classes that carry a RIHS01 type hash advertise it in the endpoint USER_DATA
         String typeHash = ROS2Message.getTypeHashFromMessageClass(std_msgs.String_.class);
         if (typeHash != null && isTypeHashAvailable())
         {
            assertTrue(endpointSection(defaultInfo, "PUBLISHER").contains("Topic type hash: " + typeHash), defaultInfo);
            assertTrue(endpointSection(defaultInfo, "SUBSCRIPTION").contains("Topic type hash: " + typeHash), defaultInfo);
         }

         String sensorInfo = topicInfo(node.getDomainId(), sensorTopic);
         assertQoS(endpointSection(sensorInfo, "PUBLISHER"), "BEST_EFFORT", "VOLATILE");
         assertQoS(endpointSection(sensorInfo, "SUBSCRIPTION"), "BEST_EFFORT", "VOLATILE");

         String latchedInfo = topicInfo(node.getDomainId(), latchedTopic);
         String publisher = endpointSection(latchedInfo, "PUBLISHER");
         assertQoS(publisher, "RELIABLE", "TRANSIENT_LOCAL");
         assertTrue(publisher.contains("Deadline: 1500000000 nanoseconds"), publisher);
         assertTrue(publisher.contains("Liveliness: MANUAL_BY_TOPIC"), publisher);
         assertTrue(publisher.contains("Liveliness lease duration: 3000000000 nanoseconds"), publisher);
      }
      finally
      {
         node.close();
      }
   }

   /**
    * The endpoint USER_DATA "typehash=RIHS01_...;" written by {@link QoSTools} is understood by ROS 2 (independent of whether the
    * generated message classes carry a hash yet): a writer and reader created from jros2's XML show the hash in
    * {@code ros2 topic info -v}.
    */
   @Test
   @Timeout(180)
   @EnabledIf("isTypeHashAvailable")
   public void testTypeHashUserDataAsSeenByROS2() throws Exception
   {
      int domainId = jros2.get().rosDomainId();
      String suffix = Long.toString(System.nanoTime());
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_hash_" + suffix,
                                                                                             "g2_type_hash",
                                                                                             domainId,
                                                                                             null,
                                                                                             ParticipantProfiles.Environment.current());
      TransportDescriptorTypeTools.ensureRegistered(configuration.transports);
      ProfilesXML profilesXML = new ProfilesXML();
      profilesXML.addParticipantProfile(configuration.profile);
      TopicProfileType topicProfile = new TopicProfileType();
      topicProfile.setProfileName("t_hash_" + suffix);
      profilesXML.addTopicProfile(topicProfile);
      PublisherProfileType writerProfile = new PublisherProfileType();
      writerProfile.setProfileName("w_hash_" + suffix);
      QoSTools.translateQoS(ROS2QoSProfile.DEFAULT, writerProfile, QoSToolsTest.STRING_TYPE_HASH);
      profilesXML.addPublisherProfile(writerProfile);
      SubscriberProfileType readerProfile = new SubscriberProfileType();
      readerProfile.setProfileName("r_hash_" + suffix);
      QoSTools.translateQoS(ROS2QoSProfile.SENSOR_DATA, readerProfile, QoSToolsTest.STRING_TYPE_HASH);
      profilesXML.addSubscriberProfile(readerProfile);
      profilesXML.load();

      long participant = fastddsjava.createParticipant(configuration.profile.getProfileName());
      assertNotEquals(0L, participant);
      long wrapperType = fastddsjava.createTopicDataWrapperType("std_msgs::msg::dds_::String_", fastddsjava.CDR_LE);
      long typeSupport = fastddsjava.createTypesupport(wrapperType);
      long topic = 0;
      long publisher = 0;
      long writer = 0;
      long subscriber = 0;
      long reader = 0;
      try
      {
         assertEquals(0, fastddsjava.registerType(participant, typeSupport));
         String topicName = "/g2_type_hash_" + suffix;
         topic = fastddsjava.createTopic(participant, wrapperType, "rt" + topicName, topicProfile.getProfileName());
         publisher = fastddsjava.createPublisher(participant, writerProfile.getProfileName());
         writer = fastddsjava.createDataWriter(publisher, topic, writerProfile.getProfileName());
         subscriber = fastddsjava.createSubscriber(participant, readerProfile.getProfileName());
         reader = fastddsjava.createDataReader(subscriber, topic, 0, readerProfile.getProfileName());
         assertNotEquals(0L, writer);
         assertNotEquals(0L, reader);

         String info = topicInfo(domainId, topicName);
         String publisherSection = endpointSection(info, "PUBLISHER");
         String subscriptionSection = endpointSection(info, "SUBSCRIPTION");
         assertTrue(publisherSection.contains("Topic type hash: " + QoSToolsTest.STRING_TYPE_HASH), info);
         assertTrue(subscriptionSection.contains("Topic type hash: " + QoSToolsTest.STRING_TYPE_HASH), info);
         assertQoS(publisherSection, "RELIABLE", "VOLATILE");
         assertQoS(subscriptionSection, "BEST_EFFORT", "VOLATILE");
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
         fastddsjava.unregisterType(participant, "std_msgs::msg::dds_::String_");
         fastddsjava.deleteTypesupport(typeSupport);
         fastddsjava.deleteParticipant(participant);
      }
   }
}
