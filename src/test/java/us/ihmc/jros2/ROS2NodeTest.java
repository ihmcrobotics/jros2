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
import us.ihmc.fastddsjava.natives.fastddsjava;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Node-level behavior: names, topic resolution, native handle checks, topic reference counting, snapshots,
 * deprecated placeholders and concurrency.
 */
public class ROS2NodeTest
{
   @Test
   @Timeout(30)
   public void testInvalidNodeNamesAreRejected()
   {
      int activeBefore = ROS2Node.getActiveNodes().size();
      for (String name : new String[] {"my-node", "1node", "", "node name", "né"})
      {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new ROS2Node(name), name);
         assertTrue(e.getMessage().contains("Invalid node name"), e.getMessage());
      }
      assertThrows(IllegalArgumentException.class, () -> new ROS2Node(null));
      assertEquals(activeBefore, ROS2Node.getActiveNodes().size());
   }

   @Test
   @Timeout(30)
   public void testNamespaceNormalization()
   {
      ROS2Node relative = new ROS2Node("ns_node", ROS2NodeOptions.defaults().namespace("robot"));
      ROS2Node root = new ROS2Node("ns_node", ROS2NodeOptions.defaults().namespace(""));
      ROS2Node unset = new ROS2Node("ns_node");
      ROS2Node nested = new ROS2Node("ns_node", ROS2NodeOptions.defaults().namespace("/robot/arm"));
      try
      {
         assertEquals("/robot", relative.getNamespace());
         assertEquals("/robot/ns_node", relative.getFullyQualifiedName());
         assertEquals("/", root.getNamespace());
         assertEquals("/ns_node", root.getFullyQualifiedName());
         assertEquals("/", unset.getNamespace());
         assertEquals("ns_node", unset.getName());
         assertEquals("/robot/arm/ns_node", nested.getFullyQualifiedName());
      }
      finally
      {
         relative.close();
         root.close();
         unset.close();
         nested.close();
      }
   }

   @Test
   @Timeout(30)
   public void testInvalidNamespacesAndDomainsAreRejected()
   {
      for (String namespace : new String[] {"/robot/", "/ro bot", "/1robot", "/a//b", "/{ns}"})
      {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                                   () -> new ROS2Node("node", ROS2NodeOptions.defaults().namespace(namespace)),
                                                   namespace);
         assertTrue(e.getMessage().contains("Invalid node namespace"), e.getMessage());
      }
      assertThrows(IllegalArgumentException.class, () -> new ROS2Node("node", 233));
      assertThrows(IllegalArgumentException.class, () -> new ROS2Node("node", -1));
   }

   @Test
   @Timeout(30)
   public void testRelativeAndPrivateTopicNames() throws InterruptedException
   {
      ROS2Node node = new ROS2Node("resolver", ROS2NodeOptions.defaults().namespace("/robot"));
      ROS2Node listener = new ROS2Node("absolute_listener");
      try
      {
         assertEquals("/robot/chatter", node.resolveTopicName("chatter"));
         assertEquals("/robot/resolver/status", node.resolveTopicName("~/status"));
         assertEquals("/robot/resolver/x", node.resolveTopicName("{node}/x"));
         assertEquals("/robot/x", node.resolveTopicName("{ns}/x"));
         assertEquals("/abs", node.resolveTopicName("/abs"));

         ROS2Topic<example_interfaces.Bool> relative = new ROS2Topic<>("chatter", example_interfaces.Bool.class, ROS2QoSProfile.RELIABLE);
         ROS2Publisher<example_interfaces.Bool> publisher = node.createPublisher(relative);
         assertEquals("/robot/chatter", publisher.getTopicName());
         assertEquals("chatter", relative.getName(), "the topic object is not modified");

         ROS2Publisher<example_interfaces.Bool> privatePublisher = node.createPublisher(new ROS2Topic<>("~/status", example_interfaces.Bool.class));
         assertEquals("/robot/resolver/status", privatePublisher.getTopicName());

         // A subscription using the absolute name receives what the relative publisher sends
         CountDownLatch received = new CountDownLatch(1);
         ROS2Subscription<example_interfaces.Bool> subscription = listener.createSubscription(new ROS2Topic<>("/robot/chatter",
                                                                                                             example_interfaces.Bool.class,
                                                                                                             ROS2QoSProfile.RELIABLE),
                                                                                             reader ->
                                                                                             {
                                                                                                reader.read();
                                                                                                received.countDown();
                                                                                             });
         assertTrue(subscription.waitForPublisher(5000));
         assertTrue(publisher.waitForSubscription(5000));
         for (int i = 0; i < 50 && received.getCount() > 0; ++i)
         {
            publisher.publish(new example_interfaces.Bool());
            received.await(100, TimeUnit.MILLISECONDS);
         }
         assertEquals(0, received.getCount(), "no message received on the resolved topic");
      }
      finally
      {
         node.close();
         listener.close();
      }
   }

   @Test
   @Timeout(30)
   public void testInvalidTopicsAreRejected()
   {
      ROS2Node node = new ROS2Node("invalid_topics");
      try
      {
         for (String name : new String[] {"/my-topic", "{unknown}", "", "/a//b", "/1abc", "chatter/", "~x/y", "/"})
         {
            ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(name, example_interfaces.Bool.class);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> node.createPublisher(topic), name);
            assertTrue(e.getMessage().contains("Invalid topic name"), e.getMessage());
            assertThrows(IllegalArgumentException.class, () -> node.createSubscription(topic), name);
         }
         ROS2Topic<example_interfaces.Bool> noType = new ROS2Topic<example_interfaces.Bool>("/no_type", null);
         assertThrows(IllegalArgumentException.class, () -> node.createPublisher(noType));
         assertThrows(IllegalArgumentException.class, () -> node.createSubscription(noType));
         assertThrows(IllegalArgumentException.class, () -> node.createSubscriptionSampler(null, sample ->
         {
         }));
         assertThrows(IllegalArgumentException.class, () -> node.createPublisher(null, ROS2QoSProfile.DEFAULT));

         // Nothing was registered and the node still works
         assertEquals(0, node.getPublishers().size());
         assertEquals(0, node.getSubscriptions().size());
         assertEquals(1, node.getParticipant().getTopicCount(), "only the graph topic");
         assertNotNull(node.createPublisher(new ROS2Topic<>("/valid_topic", example_interfaces.Bool.class)));
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(30)
   public void testSameTopicNameWithDifferentTypeThrows()
   {
      ROS2Node node = new ROS2Node("type_conflict");
      try
      {
         ROS2Publisher<example_interfaces.Bool> publisher = node.createPublisher(new ROS2Topic<>("/conflict", example_interfaces.Bool.class));
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                                   () -> node.createSubscription(new ROS2Topic<>("/conflict", std_msgs.String_.class)));
         assertTrue(e.getMessage().contains("example_interfaces::msg::dds_::Bool_"), e.getMessage());
         assertTrue(e.getMessage().contains("std_msgs::msg::dds_::String_"), e.getMessage());
         assertTrue(e.getMessage().contains("/conflict"), e.getMessage());
         assertThrows(IllegalArgumentException.class, () -> node.createPublisher(new ROS2Topic<>("/conflict", std_msgs.String_.class)));
         // Relative names that resolve to the same topic conflict too
         assertThrows(IllegalArgumentException.class, () -> node.createPublisher(new ROS2Topic<>("conflict", std_msgs.String_.class)));

         // The node and the existing publisher still work
         publisher.publish(new example_interfaces.Bool());
         ROS2Subscription<example_interfaces.Bool> subscription = node.createSubscription(new ROS2Topic<>("/conflict", example_interfaces.Bool.class));
         assertNotNull(subscription);
         assertEquals(2, node.getParticipant().getTopicReferenceCount("rt/conflict"));
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(30)
   public void testNativeTopicCreationFailureIsReported()
   {
      ROS2Node node = new ROS2Node("topic_failure");
      ROS2Participant participant = node.getParticipant();
      try
      {
         String typeName = "example_interfaces::msg::dds_::Bool_";
         TopicData other = participant.acquireTopic("rt/topic_failure_other", typeName);
         // Create the DDS topic behind the registry's back: Fast-DDS refuses a second topic with the same name
         long rogueTopic = fastddsjava.createTopic(participant.getFastddsParticipant(), other.fastddsTopicDataWrapperType, "rt/taken", "jros2_topic");
         assertNotEquals(0L, rogueTopic);

         ROS2Topic<example_interfaces.Bool> taken = new ROS2Topic<>("/taken", example_interfaces.Bool.class);
         IllegalStateException e = assertThrows(IllegalStateException.class, () -> node.createPublisher(taken));
         assertTrue(e.getMessage().contains("rt/taken"), e.getMessage());
         assertThrows(IllegalStateException.class, () -> node.createSubscription(taken));

         // Nothing leaked: no reference on the topic, the type keeps only the other topic's reference
         assertEquals(0, participant.getTopicReferenceCount("rt/taken"));
         assertTrue(participant.isTypeRegistered(typeName));
         assertTrue(node.getPublishers().isEmpty());
         assertTrue(node.getSubscriptions().isEmpty());

         assertEquals(fastddsjava.RETCODE_OK, fastddsjava.deleteTopic(participant.getFastddsParticipant(), rogueTopic));
         assertNotNull(node.createPublisher(taken));
         participant.releaseTopic("rt/topic_failure_other");
         assertEquals(1, participant.getTopicReferenceCount("rt/taken"));
         assertTrue(participant.isTypeRegistered(typeName));
      }
      finally
      {
         node.close();
      }
      assertTrue(participant.isClosed());
      assertThrows(IllegalStateException.class, () -> participant.acquireTopic("rt/after_close", "example_interfaces::msg::dds_::Bool_"));
   }

   @Test
   @Timeout(30)
   public void testSameTopicNameWithDifferentTypeInDifferentNodes()
   {
      ROS2Node a = new ROS2Node("type_a");
      ROS2Node b = new ROS2Node("type_b");
      try
      {
         // Separate participants: allowed by DDS, the endpoints just do not match
         assertNotNull(a.createPublisher(new ROS2Topic<>("/shared_name", example_interfaces.Bool.class)));
         assertNotNull(b.createSubscription(new ROS2Topic<>("/shared_name", std_msgs.String_.class)));
      }
      finally
      {
         a.close();
         b.close();
      }
   }

   @Test
   @Timeout(30)
   public void testTopicReferenceCounting()
   {
      ROS2Node node = new ROS2Node("refcount");
      ROS2Participant participant = node.getParticipant();
      try
      {
         ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/refcount", example_interfaces.Bool.class);
         ROS2Publisher<example_interfaces.Bool> publisher1 = node.createPublisher(topic);
         ROS2Publisher<example_interfaces.Bool> publisher2 = node.createPublisher(topic);
         ROS2Subscription<example_interfaces.Bool> subscription = node.createSubscription(topic);
         assertEquals(3, participant.getTopicReferenceCount("rt/refcount"));
         assertTrue(participant.isTypeRegistered("example_interfaces::msg::dds_::Bool_"));

         assertTrue(node.destroyPublisher(publisher1));
         assertFalse(node.destroyPublisher(publisher1), "destroying twice is a no-op");
         assertEquals(2, participant.getTopicReferenceCount("rt/refcount"));
         assertTrue(node.destroySubscription(subscription));
         assertFalse(node.destroySubscription(subscription));
         assertEquals(1, participant.getTopicReferenceCount("rt/refcount"));
         assertTrue(node.destroyPublisher(publisher2));
         assertEquals(0, participant.getTopicReferenceCount("rt/refcount"));
         assertFalse(participant.isTypeRegistered("example_interfaces::msg::dds_::Bool_"), "the type is unregistered with its last topic");

         // Once released, the topic name can be used with another type
         assertNotNull(node.createPublisher(new ROS2Topic<>("/refcount", std_msgs.String_.class)));
         assertEquals(1, participant.getTopicReferenceCount("rt/refcount"));
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(30)
   public void testEndpointListsAreSnapshots()
   {
      ROS2Node node = new ROS2Node("snapshots");
      try
      {
         node.createPublisher(new ROS2Topic<>("/snap_a", example_interfaces.Bool.class));
         node.createSubscription(new ROS2Topic<>("/snap_b", example_interfaces.Bool.class));
         List<ROS2Publisher<?>> publishers = node.getPublishers();
         List<ROS2Subscription<?>> subscriptions = node.getSubscriptions();
         assertEquals(1, publishers.size());
         assertEquals(1, subscriptions.size());

         node.createPublisher(new ROS2Topic<>("/snap_c", example_interfaces.Bool.class));
         node.destroySubscription(subscriptions.get(0));
         assertEquals(1, publishers.size(), "snapshot must not change");
         assertEquals(1, subscriptions.size(), "snapshot must not change");
         assertEquals(2, node.getPublishers().size());
         assertEquals(0, node.getSubscriptions().size());

         assertThrows(UnsupportedOperationException.class, () -> publishers.clear());
         assertThrows(UnsupportedOperationException.class, () -> subscriptions.remove(0));
         assertThrows(UnsupportedOperationException.class, () -> ROS2Node.getActiveNodes().clear());
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(30)
   @SuppressWarnings("deprecation")
   public void testServicesAndParametersAreUnsupported() throws NoSuchMethodException
   {
      ROS2Node node = new ROS2Node("unsupported");
      try
      {
         UnsupportedOperationException service = assertThrows(UnsupportedOperationException.class, () -> node.createService(Object.class, "/svc", null));
         assertTrue(service.getMessage().contains("services are not supported"), service.getMessage());
         UnsupportedOperationException declare = assertThrows(UnsupportedOperationException.class, () -> node.declareParameter("p", 1));
         assertTrue(declare.getMessage().contains("parameters are not supported"), declare.getMessage());
         UnsupportedOperationException get = assertThrows(UnsupportedOperationException.class, () -> node.getParameter("p"));
         assertTrue(get.getMessage().contains("parameters are not supported"), get.getMessage());

         Method createService = ROS2Node.class.getMethod("createService", Class.class, String.class, Object.class);
         Method declareParameter = ROS2Node.class.getMethod("declareParameter", String.class, Object.class);
         Method getParameter = ROS2Node.class.getMethod("getParameter", String.class);
         assertTrue(createService.isAnnotationPresent(Deprecated.class));
         assertTrue(declareParameter.isAnnotationPresent(Deprecated.class));
         assertTrue(getParameter.isAnnotationPresent(Deprecated.class));
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(30)
   public void testClosedNode()
   {
      ROS2Node node = new ROS2Node("closed_node");
      ROS2Publisher<example_interfaces.Bool> publisher = node.createPublisher(new ROS2Topic<>("/closed_topic", example_interfaces.Bool.class));
      assertTrue(ROS2Node.getActiveNodes().contains(node));

      node.close();
      assertTrue(node.isClosed());
      assertTrue(publisher.isClosed(), "close() closes the publishers");
      assertTrue(node.getPublishers().isEmpty());
      assertFalse(ROS2Node.getActiveNodes().contains(node));
      assertTrue(node.getParticipant().isClosed());

      assertNull(node.createPublisher(new ROS2Topic<>("/closed_topic", example_interfaces.Bool.class)));
      assertNull(node.createSubscription(new ROS2Topic<>("/closed_topic", example_interfaces.Bool.class)));
      assertFalse(node.destroyPublisher(publisher));
      // Name validation still applies first
      assertThrows(IllegalArgumentException.class, () -> node.createPublisher(new ROS2Topic<>("/bad-name", example_interfaces.Bool.class)));

      assertDoesNotThrow(node::close);
   }

   @Test
   @Timeout(60)
   public void testConcurrentCreateAndDestroy() throws InterruptedException
   {
      ROS2Node node = new ROS2Node("concurrent_endpoints");
      try
      {
         int threads = 6;
         int iterations = 15;
         AtomicReference<Throwable> failure = new AtomicReference<>();
         List<Thread> workers = new ArrayList<>();
         for (int t = 0; t < threads; ++t)
         {
            int index = t;
            Thread worker = new Thread(() ->
            {
               try
               {
                  for (int i = 0; i < iterations; ++i)
                  {
                     ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/concurrent_" + (i % 3), example_interfaces.Bool.class);
                     ROS2Publisher<example_interfaces.Bool> publisher = node.createPublisher(topic);
                     ROS2Subscription<example_interfaces.Bool> subscription = node.createSubscription(topic, reader -> reader.read());
                     publisher.publish(new example_interfaces.Bool());
                     assertTrue(node.destroySubscription(subscription));
                     if (index % 2 == 0 || i < iterations - 1)
                     {
                        assertTrue(node.destroyPublisher(publisher));
                     }
                  }
               }
               catch (Throwable e)
               {
                  failure.compareAndSet(null, e);
               }
            }, "concurrent-endpoints-" + t);
            workers.add(worker);
            worker.start();
         }
         for (Thread worker : workers)
         {
            worker.join();
         }
         if (failure.get() != null)
         {
            fail(failure.get());
         }

         // Odd workers kept their last publisher
         assertEquals(threads / 2, node.getPublishers().size());
         assertEquals(0, node.getSubscriptions().size());
         int references = 0;
         for (int i = 0; i < 3; ++i)
         {
            references += node.getParticipant().getTopicReferenceCount("rt/concurrent_" + i);
         }
         assertEquals(threads / 2, references);

         // The graph lists exactly the remaining writers
         ROS2Graph.NodeSnapshot snapshot = node.graphSnapshot();
         assertEquals(threads / 2, snapshot.writerGuids.size());
         assertEquals(0, snapshot.readerGuids.size());
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(60)
   public void testCloseWhileCreating() throws InterruptedException
   {
      for (int round = 0; round < 5; ++round)
      {
         ROS2Node node = new ROS2Node("close_while_creating");
         List<ROS2Publisher<?>> created = new ArrayList<>();
         AtomicReference<Throwable> failure = new AtomicReference<>();
         AtomicInteger nulls = new AtomicInteger();
         Thread creator = new Thread(() ->
         {
            try
            {
               for (int i = 0; i < 200; ++i)
               {
                  ROS2Publisher<example_interfaces.Bool> publisher = node.createPublisher(new ROS2Topic<>("/cwc_" + (i % 4), example_interfaces.Bool.class));
                  if (publisher == null)
                  {
                     nulls.incrementAndGet();
                     break;
                  }
                  synchronized (created)
                  {
                     created.add(publisher);
                  }
                  publisher.publish(new example_interfaces.Bool());
               }
            }
            catch (Throwable e)
            {
               failure.compareAndSet(null, e);
            }
         }, "creator");
         creator.start();
         Thread.sleep(20 + 20 * round);
         node.close();
         creator.join();
         if (failure.get() != null)
         {
            fail(failure.get());
         }
         assertTrue(node.isClosed());
         synchronized (created)
         {
            for (ROS2Publisher<?> publisher : created)
            {
               assertTrue(publisher.isClosed(), "every publisher created before close() is closed by it");
            }
         }
         assertTrue(node.getParticipant().isClosed());
      }
   }

   @Test
   @Timeout(30)
   public void testRepeatedCreateAndCloseOfNodes()
   {
      for (int i = 0; i < 20; ++i)
      {
         ROS2Node node = new ROS2Node("repeated_node");
         node.createPublisher(new ROS2Topic<>("/repeated", example_interfaces.Bool.class)).publish(new example_interfaces.Bool());
         node.createSubscription(new ROS2Topic<>("/repeated", example_interfaces.Bool.class), reader -> reader.read());
         node.close();
         assertTrue(node.getParticipant().isClosed());
      }
   }
}
