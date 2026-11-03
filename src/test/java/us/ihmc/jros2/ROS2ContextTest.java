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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import us.ihmc.jros2.RmwDdsCommonMessages.NodeEntitiesInfo;
import us.ihmc.jros2.RmwDdsCommonMessages.ParticipantEntitiesInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ROS2Context}: several nodes on one shared DDS participant.
 */
public class ROS2ContextTest
{
   private static final String COMPATIBILITY_PROPERTY = "jros2.graph.compatibility";

   @AfterEach
   public void clearCompatibility()
   {
      System.clearProperty(COMPATIBILITY_PROPERTY);
   }

   private static ROS2NodeOptions in(ROS2Context context)
   {
      return ROS2NodeOptions.defaults().context(context);
   }

   @Test
   @Timeout(60)
   public void testNodesShareOneParticipantAndGraph() throws InterruptedException
   {
      System.setProperty(COMPATIBILITY_PROPERTY, "IRON_PLUS");
      try (ROS2Context context = ROS2Context.create())
      {
         ROS2Node talker = new ROS2Node("talker", in(context));
         ROS2Node listener = new ROS2Node("listener", in(context).namespace("/robot"));

         assertSame(context, talker.getContext());
         assertSame(talker.getParticipant(), listener.getParticipant());
         assertEquals(talker.fastddsParticipant, listener.fastddsParticipant);
         assertEquals(context.getDomainId(), talker.getDomainId());
         assertEquals(2, context.getNodes().size());

         ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>("/context_chatter", std_msgs.String_.class, ROS2QoSProfile.RELIABLE);
         ROS2Publisher<std_msgs.String_> publisher = talker.createPublisher(topic);
         CountDownLatch received = new CountDownLatch(1);
         ROS2Subscription<std_msgs.String_> subscription = listener.createSubscription(topic, reader ->
         {
            std_msgs.String_ message = reader.read();
            if (message != null && message.getData().toString().equals("hello"))
            {
               received.countDown();
            }
         });

         // One topic registration shared by both nodes
         assertEquals(2, talker.getParticipant().getTopicReferenceCount("rt/context_chatter"));

         // Nodes of the same participant communicate
         std_msgs.String_ message = new std_msgs.String_();
         message.getData().append("hello");
         assertTrue(publisher.waitForSubscription(5000));
         for (int i = 0; i < 50 && received.getCount() > 0; ++i)
         {
            publisher.publish(message);
            received.await(100, TimeUnit.MILLISECONDS);
         }
         assertEquals(0, received.getCount());

         // One graph sample lists both nodes with their endpoints
         byte[] writerGuid = publisher.getGuid().getValue().clone();
         byte[] readerGuid = subscription.getGuid().getValue().clone();
         try (ROS2GraphProbe probe = new ROS2GraphProbe(context.getDomainId()))
         {
            ParticipantEntitiesInfo info = probe.awaitLatest(context.getParticipant().getGuid(), 16, i -> i.getNodeEntitiesInfoSeq().size() == 2, 10000);
            assertNotNull(info, "both nodes must be announced in one sample");
            NodeEntitiesInfo first = info.getNodeEntitiesInfoSeq().get(0);
            NodeEntitiesInfo second = info.getNodeEntitiesInfoSeq().get(1);
            assertEquals("/", first.getNodeNamespace());
            assertEquals("talker", first.getNodeName());
            assertEquals(1, first.getWriterGids().size());
            assertArrayEquals(writerGuid, first.getWriterGids().get(0).getGuid());
            assertEquals(0, first.getReaderGids().size());
            assertEquals("/robot", second.getNodeNamespace());
            assertEquals("listener", second.getNodeName());
            assertEquals(1, second.getReaderGids().size());
            assertArrayEquals(readerGuid, second.getReaderGids().get(0).getGuid());

            // Closing one node keeps the participant and the other node
            talker.close();
            assertTrue(publisher.isClosed());
            assertFalse(listener.isClosed());
            assertFalse(context.getParticipant().isClosed());
            assertEquals(1, context.getNodes().size());
            assertEquals(1, listener.getParticipant().getTopicReferenceCount("rt/context_chatter"));
            ParticipantEntitiesInfo afterClose = probe.awaitLatest(context.getParticipant().getGuid(),
                                                                   16,
                                                                   i -> i.getNodeEntitiesInfoSeq().size() == 1
                                                                        && i.getNodeEntitiesInfoSeq().get(0).getNodeName().equals("listener"),
                                                                   10000);
            assertNotNull(afterClose);
         }

         // Closing the context closes the remaining nodes and the participant
         context.close();
         assertTrue(context.isClosed());
         assertTrue(listener.isClosed());
         assertTrue(subscription.isClosed());
         assertTrue(context.getParticipant().isClosed());
         assertTrue(context.getNodes().isEmpty());
      }
   }

   @Test
   @Timeout(30)
   public void testClosedContextRejectsNodes()
   {
      ROS2Context context = ROS2Context.create();
      context.close();
      assertTrue(context.isClosed());
      assertThrows(IllegalStateException.class, () -> new ROS2Node("late", in(context)));
      assertDoesNotThrow(context::close);
   }

   @Test
   @Timeout(30)
   public void testParticipantLevelOptionsComeFromTheContext()
   {
      ROS2NodeOptions contextOptions = ROS2NodeOptions.defaults().domainId(jros2.get().rosDomainId()).enclave("/");
      try (ROS2Context context = ROS2Context.create(contextOptions))
      {
         // Repeating the context's values is fine
         ROS2Node same = new ROS2Node("same_options", new ROS2NodeOptions(contextOptions).context(context));
         assertEquals(context.getDomainId(), same.getDomainId());

         int otherDomain = (context.getDomainId() + 1) % 233;
         IllegalArgumentException domain = assertThrows(IllegalArgumentException.class, () -> new ROS2Node("other_domain", in(context).domainId(otherDomain)));
         assertTrue(domain.getMessage().contains("domainId"), domain.getMessage());
         assertThrows(IllegalArgumentException.class, () -> new ROS2Node("other_enclave", in(context).enclave("/other")));
         assertThrows(IllegalArgumentException.class, () -> new ROS2Node("other_range", in(context).discoveryRange(ROS2DiscoveryRange.LOCALHOST)));
         assertThrows(IllegalArgumentException.class, () -> new ROS2Node("other_peers", in(context).staticPeers("192.168.0.1")));
         assertThrows(IllegalArgumentException.class, () -> new ROS2Node("other_threads", in(context).threadSettings(new ROS2ThreadSettings().priority(5))));
         assertEquals(1, context.getNodes().size(), "rejected nodes are not attached");

         // Node-level options stay per node
         ROS2Node namespaced = new ROS2Node("namespaced", in(context).namespace("/ns"));
         assertEquals("/ns/namespaced", namespaced.getFullyQualifiedName());

         // A context cannot reference another context
         assertThrows(IllegalArgumentException.class, () -> ROS2Context.create(in(context)));
      }
      assertThrows(IllegalArgumentException.class, () -> ROS2Context.create(ROS2NodeOptions.defaults().domainId(500)));
   }

   @Test
   @Timeout(30)
   public void testTransportsAreComparedByTransportId()
   {
      // Participant-level transports are compared without creating participants (the transport XML is G2's)
      us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType udp = new us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType();
      udp.setTransportId("context_test_udp");
      udp.setType("UDPv4");
      // A different descriptor object with the same transport ID is the same transport
      us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType copy = new us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType();
      copy.setTransportId("context_test_udp");
      copy.setType("UDPv4");
      us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType other = new us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType();
      other.setTransportId("context_test_udp_other");
      other.setType("UDPv4");
      us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType noId = new us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType();

      assertTrue(ROS2Context.sameTransports(new us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType[] {udp},
                                            new us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType[] {copy}));
      assertTrue(ROS2Context.sameTransports(new us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType[] {noId},
                                            new us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType[] {noId}));
      assertTrue(ROS2Context.sameTransports(null, null));
      assertFalse(ROS2Context.sameTransports(new us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType[] {udp},
                                             new us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType[] {other}));
      assertFalse(ROS2Context.sameTransports(new us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType[] {udp},
                                             new us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType[] {udp, copy}));
      assertFalse(ROS2Context.sameTransports(new us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType[] {udp}, null));
      assertFalse(ROS2Context.sameTransports(new us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType[] {noId},
                                             new us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType[] {new us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType()}));

      // A context with the default transports rejects nodes that ask for explicit ones
      try (ROS2Context context = ROS2Context.create())
      {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new ROS2Node("explicit_transports", in(context).transports(udp)));
         assertTrue(e.getMessage().contains("transports"), e.getMessage());
         assertTrue(context.getNodes().isEmpty());
      }
   }

   @Test
   @Timeout(30)
   public void testThreadSettingsAreCompared()
   {
      ROS2NodeOptions contextOptions = ROS2NodeOptions.defaults().threadSettings(new ROS2ThreadSettings().stackSizeBytes(1 << 20));
      try (ROS2Context context = ROS2Context.create(contextOptions))
      {
         // Equal values in a different object are accepted
         ROS2Node same = new ROS2Node("same_threads", in(context).threadSettings(new ROS2ThreadSettings().stackSizeBytes(1 << 20)));
         assertSame(context, same.getContext());
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                                   () -> new ROS2Node("other_threads", in(context).threadSettings(new ROS2ThreadSettings().stackSizeBytes(2 << 20))));
         assertTrue(e.getMessage().contains("threadSettings"), e.getMessage());
         assertEquals(1, context.getNodes().size());
      }
   }

   @Test
   @Timeout(30)
   public void testOneTypePerTopicWithinAContext()
   {
      try (ROS2Context context = ROS2Context.create())
      {
         ROS2Node a = new ROS2Node("type_a", in(context));
         ROS2Node b = new ROS2Node("type_b", in(context));
         assertNotNull(a.createPublisher(new ROS2Topic<>("/context_conflict", example_interfaces.Bool.class)));
         assertThrows(IllegalArgumentException.class, () -> b.createSubscription(new ROS2Topic<>("/context_conflict", std_msgs.String_.class)));

         // Topic references follow the endpoints: closing node a releases the topic, so b may now use another type
         a.close();
         assertEquals(0, a.getParticipant().getTopicReferenceCount("rt/context_conflict"));
         assertNotNull(b.createSubscription(new ROS2Topic<>("/context_conflict", std_msgs.String_.class)));
      }
   }

   @Test
   @Timeout(60)
   public void testConcurrentNodesInOneContext() throws InterruptedException
   {
      try (ROS2Context context = ROS2Context.create())
      {
         List<Thread> threads = new ArrayList<>();
         AtomicReference<Throwable> failure = new AtomicReference<>();
         for (int t = 0; t < 4; ++t)
         {
            int index = t;
            Thread thread = new Thread(() ->
            {
               try
               {
                  for (int i = 0; i < 10; ++i)
                  {
                     ROS2Node node = new ROS2Node("worker_" + index, in(context));
                     ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/context_shared", example_interfaces.Bool.class);
                     node.createPublisher(topic).publish(new example_interfaces.Bool());
                     node.createSubscription(topic, reader -> reader.read());
                     node.close();
                  }
               }
               catch (Throwable e)
               {
                  failure.compareAndSet(null, e);
               }
            });
            threads.add(thread);
            thread.start();
         }
         for (Thread thread : threads)
         {
            thread.join();
         }
         if (failure.get() != null)
         {
            fail(failure.get());
         }
         assertTrue(context.getNodes().isEmpty());
         assertEquals(0, context.getParticipant().getTopicReferenceCount("rt/context_shared"));
         assertEquals(1, context.getParticipant().getTopicCount(), "only the graph topic is left");
      }
   }

   @Test
   @Timeout(30)
   public void testContextCloseFromCallback() throws InterruptedException
   {
      ROS2Node publisherNode = new ROS2Node("context_cb_publisher");
      ROS2Context context = ROS2Context.create();
      try
      {
         ROS2Node subscriberNode = new ROS2Node("context_cb_subscriber", in(context));
         ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/context_close_from_callback", example_interfaces.Bool.class, ROS2QoSProfile.RELIABLE);
         CountDownLatch closedInCallback = new CountDownLatch(1);
         subscriberNode.createSubscription(topic, reader ->
         {
            reader.read();
            if (closedInCallback.getCount() > 0)
            {
               ROS2CallbackTestTools.asCallback(() ->
               {
                  context.close();
                  if (context.isClosed() && subscriberNode.isClosed())
                  {
                     closedInCallback.countDown();
                  }
               });
            }
         });

         ROS2Publisher<example_interfaces.Bool> publisher = publisherNode.createPublisher(topic);
         assertTrue(publisher.waitForSubscription(5000));
         for (int i = 0; i < 50 && closedInCallback.getCount() > 0; ++i)
         {
            publisher.publish(new example_interfaces.Bool());
            closedInCallback.await(100, TimeUnit.MILLISECONDS);
         }
         assertEquals(0, closedInCallback.getCount(), "close() from a callback must return and mark the context closed");
         assertTrue(context.awaitClosed(10, TimeUnit.SECONDS), "the deferred teardown must complete");
         assertTrue(context.getParticipant().isClosed());
         assertTrue(subscriberNode.getSubscriptions().isEmpty());
      }
      finally
      {
         context.close();
         publisherNode.close();
      }
   }

   @Test
   @Timeout(30)
   public void testCreatingInACallbackOfTheSameContextThrows() throws InterruptedException
   {
      ROS2Node publisherNode = new ROS2Node("context_create_publisher");
      try (ROS2Context context = ROS2Context.create())
      {
         ROS2Node a = new ROS2Node("context_create_a", in(context));
         ROS2Node b = new ROS2Node("context_create_b", in(context));
         ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/context_create_in_callback", example_interfaces.Bool.class, ROS2QoSProfile.RELIABLE);
         AtomicReference<Throwable> thrown = new AtomicReference<>();
         CountDownLatch done = new CountDownLatch(1);
         a.createSubscription(topic, reader ->
         {
            reader.read();
            if (done.getCount() > 0)
            {
               ROS2CallbackTestTools.asCallback(() ->
               {
                  try
                  {
                     // b shares a's participant
                     b.createPublisher(new ROS2Topic<>("/context_created_in_callback", example_interfaces.Bool.class));
                  }
                  catch (Throwable e)
                  {
                     thrown.set(e);
                  }
               });
               done.countDown();
            }
         });
         ROS2Publisher<example_interfaces.Bool> publisher = publisherNode.createPublisher(topic);
         assertTrue(publisher.waitForSubscription(5000));
         for (int i = 0; i < 50 && done.getCount() > 0; ++i)
         {
            publisher.publish(new example_interfaces.Bool());
            done.await(100, TimeUnit.MILLISECONDS);
         }
         assertEquals(0, done.getCount());
         assertInstanceOf(IllegalStateException.class, thrown.get());
         assertTrue(thrown.get().getMessage().contains("callbackExecutor"), thrown.get().getMessage());
         assertTrue(b.getPublishers().isEmpty());
      }
      finally
      {
         publisherNode.close();
      }
   }
}
