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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Node operations from inside subscription callbacks, which run on Fast-DDS threads (or, with intraprocess delivery,
 * inside the publisher's write).
 * <p>
 * Measured with Fast-DDS 3.6.2 (each case in its own JVM with an 8 s watchdog, without the jros2 guard): creating a
 * publisher or subscription on the participant whose callback is running deadlocks in {@code create_datawriter} /
 * {@code create_datareader} for data callbacks with intraprocess delivery OFF and for matched callbacks with
 * intraprocess delivery OFF and FULL; with FULL it returned for data callbacks, but a callback cannot tell whether its
 * sample came through intraprocess delivery or a network transport, so jros2 rejects every such call. Creating it on
 * another participant worked in every case. Deleting endpoints of the same participant from a callback deadlocks as
 * well, so closing is deferred.
 */
public class ROS2NodeCallbackTest
{
   private static final ROS2QoSProfile QOS = ROS2QoSProfile.RELIABLE;

   /** Publishes until {@code latch} opens or about 5 seconds passed. */
   private static void publishUntil(ROS2Publisher<example_interfaces.Bool> publisher, CountDownLatch latch) throws InterruptedException
   {
      publisher.waitForSubscription(5000);
      for (int i = 0; i < 50 && latch.getCount() > 0; ++i)
      {
         publisher.publish(new example_interfaces.Bool());
         latch.await(100, TimeUnit.MILLISECONDS);
      }
   }

   @Test
   @Timeout(30)
   public void testCloseFromOwnCallback() throws InterruptedException
   {
      ROS2Node publisherNode = new ROS2Node("close_cb_publisher");
      ROS2Node subscriberNode = new ROS2Node("close_cb_subscriber");
      try
      {
         ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/close_from_callback", example_interfaces.Bool.class, QOS);
         CountDownLatch closed = new CountDownLatch(1);
         AtomicBoolean markedClosed = new AtomicBoolean();
         ROS2Subscription<example_interfaces.Bool> subscription = subscriberNode.createSubscription(topic, reader ->
         {
            reader.read();
            if (closed.getCount() > 0)
            {
               ROS2CallbackTestTools.asCallback(() ->
               {
                  subscriberNode.close();
                  // Marked closed before close() returns; the teardown runs later on another thread
                  markedClosed.set(subscriberNode.isClosed());
               });
               closed.countDown();
            }
         });
         ROS2Publisher<example_interfaces.Bool> publisher = publisherNode.createPublisher(topic);
         publishUntil(publisher, closed);

         assertEquals(0, closed.getCount(), "the callback did not run or close() did not return");
         assertTrue(markedClosed.get());
         assertTrue(subscriberNode.awaitClosed(10, TimeUnit.SECONDS), "the deferred teardown must complete");
         assertTrue(subscription.isClosed());
         assertTrue(subscriberNode.getParticipant().isClosed());
         assertFalse(ROS2Node.getActiveNodes().contains(subscriberNode));
         assertNull(subscriberNode.createPublisher(topic));

         // The other node is unaffected
         publisher.publish(new example_interfaces.Bool());
         assertFalse(publisherNode.isClosed());
      }
      finally
      {
         subscriberNode.close();
         publisherNode.close();
      }
   }

   @Test
   @Timeout(30)
   public void testClosePublishingNodeFromCallback() throws InterruptedException
   {
      ROS2Node publisherNode = new ROS2Node("close_other_publisher");
      ROS2Node subscriberNode = new ROS2Node("close_other_subscriber");
      try
      {
         ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/close_other_from_callback", example_interfaces.Bool.class, QOS);
         CountDownLatch closed = new CountDownLatch(1);
         subscriberNode.createSubscription(topic, reader ->
         {
            reader.read();
            if (closed.getCount() > 0)
            {
               ROS2CallbackTestTools.asCallback(publisherNode::close);
               closed.countDown();
            }
         });
         ROS2Publisher<example_interfaces.Bool> publisher = publisherNode.createPublisher(topic);
         publishUntil(publisher, closed);

         assertEquals(0, closed.getCount());
         assertTrue(publisherNode.isClosed());
         assertTrue(publisherNode.awaitClosed(10, TimeUnit.SECONDS));
         assertTrue(publisher.isClosed());
         assertFalse(subscriberNode.isClosed());
      }
      finally
      {
         subscriberNode.close();
         publisherNode.close();
      }
   }

   @Test
   @Timeout(30)
   public void testCreatingOnTheCallbacksParticipantThrows() throws InterruptedException
   {
      ROS2Node publisherNode = new ROS2Node("create_cb_publisher");
      ROS2Node subscriberNode = new ROS2Node("create_cb_subscriber");
      try
      {
         ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/create_in_callback", example_interfaces.Bool.class, QOS);
         AtomicReference<Throwable> publisherError = new AtomicReference<>();
         AtomicReference<Throwable> subscriptionError = new AtomicReference<>();
         CountDownLatch done = new CountDownLatch(1);
         subscriberNode.createSubscription(topic, reader ->
         {
            reader.read();
            if (done.getCount() > 0)
            {
               ROS2CallbackTestTools.asCallback(() ->
               {
                  try
                  {
                     subscriberNode.createPublisher(new ROS2Topic<>("/created_in_callback", example_interfaces.Bool.class));
                  }
                  catch (Throwable e)
                  {
                     publisherError.set(e);
                  }
                  try
                  {
                     subscriberNode.createSubscription(new ROS2Topic<>("/created_in_callback", example_interfaces.Bool.class));
                  }
                  catch (Throwable e)
                  {
                     subscriptionError.set(e);
                  }
               });
               done.countDown();
            }
         });
         ROS2Publisher<example_interfaces.Bool> publisher = publisherNode.createPublisher(topic);
         publishUntil(publisher, done);

         assertEquals(0, done.getCount());
         assertInstanceOf(IllegalStateException.class, publisherError.get());
         assertTrue(publisherError.get().getMessage().contains("ROS2NodeOptions.callbackExecutor"), publisherError.get().getMessage());
         assertInstanceOf(IllegalStateException.class, subscriptionError.get());
         assertEquals(1, subscriberNode.getSubscriptions().size());
         assertTrue(subscriberNode.getPublishers().isEmpty());
         assertEquals(0, subscriberNode.getParticipant().getTopicReferenceCount("rt/created_in_callback"));

         // Outside of the callback the same call works
         assertNotNull(subscriberNode.createPublisher(new ROS2Topic<>("/created_in_callback", example_interfaces.Bool.class)));
      }
      finally
      {
         subscriberNode.close();
         publisherNode.close();
      }
   }

   @Test
   @Timeout(30)
   public void testCreatingFromAMatchedCallbackOfTheSameParticipantThrows() throws InterruptedException
   {
      ROS2Node publisherNode = new ROS2Node("match_cb_publisher");
      ROS2Node subscriberNode = new ROS2Node("match_cb_subscriber");
      try
      {
         ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/create_in_matched_callback", example_interfaces.Bool.class, QOS);
         AtomicReference<Throwable> error = new AtomicReference<>();
         CountDownLatch done = new CountDownLatch(1);
         subscriberNode.createSubscription(topic, null, (subscription, guid, matched) ->
         {
            if (matched && done.getCount() > 0)
            {
               ROS2CallbackTestTools.asCallback(() ->
               {
                  try
                  {
                     subscriberNode.createPublisher(new ROS2Topic<>("/created_in_matched_callback", example_interfaces.Bool.class));
                  }
                  catch (Throwable e)
                  {
                     error.set(e);
                  }
               });
               done.countDown();
            }
         }, QOS);
         publisherNode.createPublisher(topic);
         assertTrue(done.await(10, TimeUnit.SECONDS));
         assertInstanceOf(IllegalStateException.class, error.get());
      }
      finally
      {
         subscriberNode.close();
         publisherNode.close();
      }
   }

   @Test
   @Timeout(30)
   public void testCreatingOnAnotherParticipantFromACallbackWorks() throws InterruptedException
   {
      ROS2Node publisherNode = new ROS2Node("other_cb_publisher");
      ROS2Node subscriberNode = new ROS2Node("other_cb_subscriber");
      ROS2Node otherNode = new ROS2Node("other_cb_target");
      try
      {
         ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/create_elsewhere", example_interfaces.Bool.class, QOS);
         AtomicReference<Object> created = new AtomicReference<>();
         AtomicReference<Throwable> error = new AtomicReference<>();
         CountDownLatch done = new CountDownLatch(1);
         subscriberNode.createSubscription(topic, reader ->
         {
            reader.read();
            if (done.getCount() > 0)
            {
               ROS2CallbackTestTools.asCallback(() ->
               {
                  try
                  {
                     ROS2Publisher<example_interfaces.Bool> fromCallback = otherNode.createPublisher(new ROS2Topic<>("/created_elsewhere",
                                                                                                                     example_interfaces.Bool.class));
                     fromCallback.publish(new example_interfaces.Bool());
                     created.set(fromCallback);
                     // The publishing participant works as well
                     created.set(publisherNode.createSubscription(new ROS2Topic<>("/created_elsewhere", example_interfaces.Bool.class)));
                  }
                  catch (Throwable e)
                  {
                     error.set(e);
                  }
               });
               done.countDown();
            }
         });
         ROS2Publisher<example_interfaces.Bool> publisher = publisherNode.createPublisher(topic);
         publishUntil(publisher, done);

         assertEquals(0, done.getCount());
         assertNull(error.get(), () -> "unexpected " + error.get());
         assertNotNull(created.get());
         assertEquals(1, otherNode.getPublishers().size());
         assertEquals(1, publisherNode.getSubscriptions().size());
      }
      finally
      {
         subscriberNode.close();
         publisherNode.close();
         otherNode.close();
      }
   }

   @Test
   @Timeout(30)
   public void testDestroyFromOwnCallback() throws InterruptedException
   {
      ROS2Node publisherNode = new ROS2Node("destroy_cb_publisher");
      ROS2Node subscriberNode = new ROS2Node("destroy_cb_subscriber");
      try
      {
         ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/destroy_from_callback", example_interfaces.Bool.class, QOS);
         ROS2Publisher<example_interfaces.Bool> sameParticipantPublisher = subscriberNode.createPublisher(new ROS2Topic<>("/destroy_other",
                                                                                                                          example_interfaces.Bool.class));
         AtomicReference<ROS2Subscription<example_interfaces.Bool>> self = new AtomicReference<>();
         AtomicReference<Boolean> supported = new AtomicReference<>();
         AtomicBoolean destroyed = new AtomicBoolean();
         CountDownLatch done = new CountDownLatch(1);
         self.set(subscriberNode.createSubscription(topic, reader ->
         {
            reader.read();
            if (done.getCount() > 0)
            {
               // Destroying endpoints from a callback needs the subscription to mark its callbacks and defer its own
               // native teardown; skip with implementations that do not do that yet.
               supported.compareAndSet(null, CallbackContext.isInCallback());
               if (supported.get())
               {
                  destroyed.set(subscriberNode.destroySubscription(self.get()) && subscriberNode.destroyPublisher(sameParticipantPublisher));
               }
               done.countDown();
            }
         }));
         ROS2Publisher<example_interfaces.Bool> publisher = publisherNode.createPublisher(topic);
         publishUntil(publisher, done);
         assertEquals(0, done.getCount());
         Assumptions.assumeTrue(Boolean.TRUE.equals(supported.get()), "ROS2Subscription does not mark its callbacks with CallbackContext");

         assertTrue(destroyed.get());
         assertTrue(self.get().isClosed());
         assertTrue(sameParticipantPublisher.isClosed());
         assertTrue(subscriberNode.getSubscriptions().isEmpty());
         assertTrue(subscriberNode.getPublishers().isEmpty());
         // The topic references are released after the deferred native teardown
         long deadline = System.currentTimeMillis() + 10000;
         while (System.currentTimeMillis() < deadline && (subscriberNode.getParticipant().getTopicReferenceCount("rt/destroy_from_callback") > 0
                                                          || subscriberNode.getParticipant().getTopicReferenceCount("rt/destroy_other") > 0))
         {
            Thread.sleep(20);
         }
         assertEquals(0, subscriberNode.getParticipant().getTopicReferenceCount("rt/destroy_from_callback"));
         assertEquals(0, subscriberNode.getParticipant().getTopicReferenceCount("rt/destroy_other"));
      }
      finally
      {
         subscriberNode.close();
         publisherNode.close();
      }
   }

   @Test
   @Timeout(30)
   public void testCreatingFromAnExecutorCallbackWorks() throws InterruptedException
   {
      ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "callback-executor-test"));
      ROS2Node publisherNode = new ROS2Node("executor_cb_publisher");
      ROS2Node subscriberNode = new ROS2Node("executor_cb_subscriber", ROS2NodeOptions.defaults().callbackExecutor(executor));
      try
      {
         ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/create_in_executor_callback", example_interfaces.Bool.class, QOS);
         AtomicReference<Object> created = new AtomicReference<>();
         AtomicBoolean onExecutor = new AtomicBoolean();
         CountDownLatch done = new CountDownLatch(1);
         subscriberNode.createSubscription(topic, reader ->
         {
            reader.read();
            if (done.getCount() > 0)
            {
               onExecutor.set(Thread.currentThread().getName().equals("callback-executor-test"));
               if (onExecutor.get())
               {
                  ROS2CallbackTestTools.asCallback(() ->
                  {
                     try
                     {
                        // The Fast-DDS thread is not blocked by this callback, so the subscriber's own participant may
                        // create endpoints here
                        created.set(subscriberNode.createPublisher(new ROS2Topic<>("/created_in_executor_callback", example_interfaces.Bool.class)));
                     }
                     catch (Throwable e)
                     {
                        created.set(e);
                     }
                  });
               }
               done.countDown();
            }
         });
         ROS2Publisher<example_interfaces.Bool> publisher = publisherNode.createPublisher(topic);
         publishUntil(publisher, done);
         assertEquals(0, done.getCount());
         Assumptions.assumeTrue(onExecutor.get(), "ROS2Subscription does not dispatch callbacks to ROS2NodeOptions.callbackExecutor");

         assertInstanceOf(ROS2Publisher.class, created.get(), () -> "unexpected " + created.get());
         assertEquals(1, subscriberNode.getPublishers().size());
      }
      finally
      {
         subscriberNode.close();
         publisherNode.close();
         executor.shutdownNow();
      }
   }

   @Test
   @Timeout(30)
   public void testCallbackTrackingIsPerParticipantAndBalanced()
   {
      ROS2Node a = new ROS2Node("tracking_a");
      ROS2Node b = new ROS2Node("tracking_b");
      try
      {
         ROS2Participant participantA = a.getParticipant();
         ROS2Participant participantB = b.getParticipant();
         assertFalse(participantA.isInFastddsCallbackOfThisParticipant());

         // Recorded participants only count inside a jros2 callback
         participantA.enterUserCallback();
         try
         {
            assertFalse(participantA.isInFastddsCallbackOfThisParticipant());
            ROS2CallbackTestTools.asCallback(() ->
            {
               assertTrue(participantA.isInFastddsCallbackOfThisParticipant());
               assertFalse(participantB.isInFastddsCallbackOfThisParticipant());
               // Nested callback of another participant (e.g. intraprocess delivery from inside a callback)
               participantB.enterUserCallback();
               assertTrue(participantA.isInFastddsCallbackOfThisParticipant());
               assertTrue(participantB.isInFastddsCallbackOfThisParticipant());
               participantB.exitUserCallback();
               assertFalse(participantB.isInFastddsCallbackOfThisParticipant());

               IllegalStateException e = assertThrows(IllegalStateException.class,
                                                      () -> a.createSubscription(new ROS2Topic<>("/tracking", example_interfaces.Bool.class)));
               assertTrue(e.getMessage().contains("callbackExecutor"), e.getMessage());
               assertNotNull(b.createSubscription(new ROS2Topic<>("/tracking", example_interfaces.Bool.class)));
            });
         }
         finally
         {
            participantA.exitUserCallback();
         }
         ROS2CallbackTestTools.asCallback(() -> assertFalse(participantA.isInFastddsCallbackOfThisParticipant()));
         assertNotNull(a.createSubscription(new ROS2Topic<>("/tracking", example_interfaces.Bool.class)));
         assertFalse(ROS2Participant.isNativeCallbackThread());
      }
      finally
      {
         a.close();
         b.close();
      }
   }
}
