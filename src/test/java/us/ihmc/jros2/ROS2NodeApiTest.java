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
import us.ihmc.fastddsjava.fastddsjavaTestTools;
import us.ihmc.fastddsjava.profiles.TransportDescriptorTypeTools;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.jros2.PubSubTestTools.*;

/**
 * Parts of the {@link ROS2Node} API that other tests do not use: the (domain ID, transports) constructor kept for
 * compatibility, and the topic references that subclasses take with {@code getOrCreateTopicData}, which the node must release
 * when it closes.
 */
public class ROS2NodeApiTest
{
   @Test
   @Timeout(60)
   public void testDomainAndTransportsConstructor()
   {
      int domainId = fastddsjavaTestTools.testDomainId();
      TransportDescriptorType udp = TransportDescriptorTypeTools.createUDPv4Descriptor();
      ROS2Node publisherNode = new ROS2Node("domain_transports_pub", domainId, udp);
      ROS2Node subscriberNode = new ROS2Node("domain_transports_sub", domainId);
      try
      {
         assertEquals(domainId, publisherNode.getDomainId());
         assertEquals(domainId, subscriberNode.getDomainId());
         assertNotSame(publisherNode.getParticipant(), subscriberNode.getParticipant());

         // The two nodes communicate: UDPv4 only on one side, the builtin transports on the other
         ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("domain_transports"), example_interfaces.Int64.class);
         ROS2Subscription<example_interfaces.Int64> subscription = subscriberNode.createSubscription(topic, reliableKeepAll());
         ROS2Publisher<example_interfaces.Int64> publisher = publisherNode.createPublisher(topic, reliableKeepAll());
         awaitMatched(publisher, subscription);
         example_interfaces.Int64 message = new example_interfaces.Int64();
         message.setData(17);
         publisher.publish(message);
         example_interfaces.Int64 received = new example_interfaces.Int64();
         awaitCondition(() -> subscription.read(received), 30_000, "sample not received");
         assertEquals(17, received.getData());
      }
      finally
      {
         publisherNode.close();
         subscriberNode.close();
      }

      assertThrows(IllegalArgumentException.class, () -> new ROS2Node("domain_transports_bad", -1));
   }

   @Test
   @Timeout(60)
   public void testTopicReferencesOfSubclassesAreReleasedOnClose()
   {
      ROS2Context context = ROS2Context.create();
      try
      {
         ROS2Node node = new ROS2Node("topic_references", ROS2NodeOptions.defaults().context(context));
         ROS2Participant participant = context.getParticipant();
         ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("topic_references"), example_interfaces.Int64.class);
         String ddsTopicName = "rt" + topic.getName();

         TopicData first = node.getOrCreateTopicData(topic);
         assertNotNull(first);
         assertEquals(1, participant.getTopicReferenceCount(ddsTopicName));
         TopicData second = node.getOrCreateTopicData(topic);
         assertSame(first, second, "one native topic per DDS topic name");
         assertEquals(2, participant.getTopicReferenceCount(ddsTopicName));

         // An endpoint shares the registration
         ROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic);
         assertSame(first, publisher.getTopicData());
         assertEquals(3, participant.getTopicReferenceCount(ddsTopicName));
         assertTrue(node.destroyPublisher(publisher));
         assertEquals(2, participant.getTopicReferenceCount(ddsTopicName));

         // Closing the node releases the references it took, the participant (owned by the context) stays
         node.close();
         assertEquals(0, participant.getTopicReferenceCount(ddsTopicName));
         assertFalse(participant.isClosed());
         assertNull(node.getOrCreateTopicData(topic), "a closed node hands out no topic data");
         assertEquals(0, participant.getTopicReferenceCount(ddsTopicName));
      }
      finally
      {
         context.close();
      }
   }
}
