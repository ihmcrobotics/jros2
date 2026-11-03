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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.jros2.PubSubTestTools.*;

/**
 * Teardown of publishers and subscriptions: native listeners are released, nothing pins destroyed endpoints, and closing
 * from inside callbacks neither deadlocks nor crashes.
 */
public class ROS2EndpointLifecycleTest
{
   /** A callback that holds a large payload, so leaking it would be obvious. */
   private static final class PayloadCallback implements ROS2SubscriptionCallback<example_interfaces.Bool>
   {
      private final byte[] payload = new byte[256 * 1024];
      private final AtomicInteger received;

      PayloadCallback(AtomicInteger received)
      {
         this.received = received;
      }

      @Override
      public void onMessage(ROS2MessageReader<example_interfaces.Bool> reader)
      {
         if (reader.read() != null && payload.length > 0)
         {
            received.incrementAndGet();
         }
      }
   }

   /**
    * Before the fix every destroyed endpoint stayed reachable from a JNI global reference held by its native listener,
    * together with the user's callback.
    */
   @Test
   @Timeout(120)
   public void testDestroyedEndpointsAndCallbacksBecomeUnreachableInline()
   {
      testDestroyedEndpointsAndCallbacksBecomeUnreachable(false);
   }

   @Test
   @Timeout(120)
   public void testDestroyedEndpointsAndCallbacksBecomeUnreachableOnExecutor()
   {
      testDestroyedEndpointsAndCallbacksBecomeUnreachable(true);
   }

   private void testDestroyedEndpointsAndCallbacksBecomeUnreachable(boolean useExecutor)
   {
      final int rounds = 20;
      ExecutorService executor = useExecutor ? Executors.newSingleThreadExecutor() : null;
      ROS2Node node = new ROS2Node("unreachable_endpoints", ROS2NodeOptions.defaults().callbackExecutor(executor));
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(uniqueTopicName("unreachable_endpoints"), example_interfaces.Bool.class);
      List<WeakReference<Object>> references = new ArrayList<>();
      AtomicInteger received = new AtomicInteger();
      try
      {
         for (int i = 0; i < rounds; ++i)
         {
            PayloadCallback callback = new PayloadCallback(received);
            ROS2Subscription<example_interfaces.Bool> subscription = node.createSubscription(topic, callback, reliableKeepLast(5));
            ROS2Subscription<example_interfaces.Bool> pollingSubscription = node.createSubscription(topic, reliableKeepLast(5));
            ROS2Publisher<example_interfaces.Bool> publisher = node.createPublisher(topic, reliableKeepLast(5));
            awaitMatched(publisher, subscription);
            int before = received.get();
            publisher.publish(new example_interfaces.Bool());
            awaitCondition(() -> received.get() > before, 30_000, "sample not delivered in round " + i);

            references.add(new WeakReference<>(subscription));
            references.add(new WeakReference<>(pollingSubscription));
            references.add(new WeakReference<>(callback));
            references.add(new WeakReference<>(callback.payload));
            references.add(new WeakReference<>(publisher));

            assertTrue(node.destroySubscription(subscription));
            assertTrue(node.destroySubscription(pollingSubscription));
            assertTrue(node.destroyPublisher(publisher));
            assertTrue(subscription.isNativeReleased());
            assertTrue(subscription.isListenerTrampolineCleared());
            assertTrue(publisher.isNativeReleased());
            assertTrue(publisher.isListenerTrampolineCleared());
         }

         boolean collected = gcUntil(() ->
                                     {
                                        for (WeakReference<Object> reference : references)
                                        {
                                           if (reference.get() != null)
                                           {
                                              return false;
                                           }
                                        }
                                        return true;
                                     }, 30_000);
         if (!collected)
         {
            int retained = 0;
            for (WeakReference<Object> reference : references)
            {
               if (reference.get() != null)
               {
                  retained++;
               }
            }
            fail(retained + " of " + references.size() + " objects of destroyed endpoints are still reachable");
         }
      }
      finally
      {
         node.close();
         if (executor != null)
         {
            executor.shutdownNow();
         }
      }
   }

   /**
    * Destroying a subscription from its own callback used to self-deadlock (and deleting a DataReader from a Fast-DDS
    * callback deadlocks Fast-DDS). The subscription is closed immediately and released on the deferred thread.
    */
   @Test
   @Timeout(90)
   public void testDestroySubscriptionFromItsOwnCallbackInline() throws Exception
   {
      testDestroySubscriptionFromItsOwnCallback(false);
   }

   @Test
   @Timeout(90)
   public void testDestroySubscriptionFromItsOwnCallbackOnExecutor() throws Exception
   {
      testDestroySubscriptionFromItsOwnCallback(true);
   }

   private void testDestroySubscriptionFromItsOwnCallback(boolean useExecutor) throws Exception
   {
      ExecutorService executor = useExecutor ? Executors.newSingleThreadExecutor() : null;
      ROS2Node node = new ROS2Node("destroy_own_subscription", ROS2NodeOptions.defaults().callbackExecutor(executor));
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(uniqueTopicName("destroy_own_subscription"), example_interfaces.Bool.class);
      try
      {
         AtomicReference<ROS2Subscription<example_interfaces.Bool>> self = new AtomicReference<>();
         AtomicInteger callbacks = new AtomicInteger();
         CountDownLatch destroyed = new CountDownLatch(1);
         AtomicBoolean destroyResult = new AtomicBoolean();
         AtomicBoolean closedInsideCallback = new AtomicBoolean();
         AtomicBoolean readAfterClose = new AtomicBoolean(true);

         ROS2Subscription<example_interfaces.Bool> subscription = node.createSubscription(topic, reader ->
         {
            if (callbacks.incrementAndGet() == 1)
            {
               while (self.get() == null)
               {
                  Thread.yield();
               }
               destroyResult.set(node.destroySubscription(self.get()));
               closedInsideCallback.set(self.get().isClosed());
               reader.read(); // the current sample is still readable
               readAfterClose.set(reader.read() != null); // nothing new can be taken once closed
               destroyed.countDown();
            }
         }, reliableKeepAll());
         self.set(subscription);
         ROS2Publisher<example_interfaces.Bool> publisher = node.createPublisher(topic, reliableKeepAll());
         awaitMatched(publisher, subscription);

         publisher.publish(new example_interfaces.Bool());
         publisher.publish(new example_interfaces.Bool());
         assertTrue(destroyed.await(30, TimeUnit.SECONDS), "destroying from the callback deadlocked");
         assertTrue(destroyResult.get());
         assertTrue(closedInsideCallback.get(), "closed immediately");
         assertFalse(readAfterClose.get());

         awaitCondition(subscription::isNativeReleased, 30_000, "deferred teardown did not run");
         assertTrue(CallbackContext.awaitDeferred(10, TimeUnit.SECONDS));
         for (int i = 0; i < 5; ++i)
         {
            publisher.publish(new example_interfaces.Bool());
         }
         waitFor(() -> false, 200);
         assertEquals(1, callbacks.get(), "no callback after close");
      }
      finally
      {
         node.close();
         if (executor != null)
         {
            executor.shutdownNow();
         }
      }
   }

   /**
    * Destroying the publisher whose sample is being delivered (with intraprocess delivery the callback runs inside that
    * publisher's write) and another subscription of the same participant from a callback must neither crash nor deadlock.
    */
   @Test
   @Timeout(90)
   public void testDestroyPublisherAndOtherSubscriptionFromCallback() throws Exception
   {
      ROS2Node node = new ROS2Node("destroy_from_callback");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(uniqueTopicName("destroy_from_callback"), example_interfaces.Bool.class);
      try
      {
         AtomicReference<ROS2Publisher<example_interfaces.Bool>> publisherReference = new AtomicReference<>();
         AtomicReference<ROS2Subscription<example_interfaces.Bool>> otherReference = new AtomicReference<>();
         CountDownLatch done = new CountDownLatch(1);
         AtomicBoolean destroyedPublisher = new AtomicBoolean();
         AtomicBoolean destroyedOther = new AtomicBoolean();

         ROS2Subscription<example_interfaces.Bool> other = node.createSubscription(topic, reader -> reader.read(), reliableKeepAll());
         otherReference.set(other);
         ROS2Subscription<example_interfaces.Bool> subscription = node.createSubscription(topic, reader ->
         {
            reader.read();
            if (done.getCount() > 0)
            {
               destroyedPublisher.set(node.destroyPublisher(publisherReference.get()));
               destroyedOther.set(node.destroySubscription(otherReference.get()));
               done.countDown();
            }
         }, reliableKeepAll());
         ROS2Publisher<example_interfaces.Bool> publisher = node.createPublisher(topic, reliableKeepAll());
         publisherReference.set(publisher);
         awaitMatched(publisher, subscription);
         awaitMatched(publisher, other);

         Thread publishThread = new Thread(() -> publisher.publish(new example_interfaces.Bool()), "destroy-from-callback-publisher");
         publishThread.start();
         assertTrue(done.await(30, TimeUnit.SECONDS), "destroying from the callback deadlocked");
         publishThread.join(30_000);
         assertFalse(publishThread.isAlive(), "publish() did not return");
         assertTrue(destroyedPublisher.get());
         assertTrue(destroyedOther.get());
         assertTrue(publisher.isClosed());
         assertTrue(other.isClosed());

         awaitCondition(() -> publisher.isNativeReleased() && other.isNativeReleased(), 30_000, "deferred teardown did not run");
         publisher.publish(new example_interfaces.Bool()); // ignored
         assertEquals(0, publisher.getPublicationMatchedStatus());
      }
      finally
      {
         node.close();
      }
   }

   /**
    * Destroying a subscription from another thread while its callback runs waits for the callback (Fast-DDS fences the
    * listener) and then releases everything; reads inside the callback stop returning new samples.
    */
   @Test
   @Timeout(90)
   public void testDestroyWhileCallbackRunsWaitsForTheCallback() throws Exception
   {
      ROS2Node node = new ROS2Node("destroy_during_callback");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(uniqueTopicName("destroy_during_callback"), example_interfaces.Bool.class);
      try
      {
         CountDownLatch entered = new CountDownLatch(1);
         CountDownLatch release = new CountDownLatch(1);
         AtomicBoolean secondReadReturnedData = new AtomicBoolean(true);
         AtomicInteger callbacks = new AtomicInteger();
         AtomicReference<ROS2Subscription<example_interfaces.Bool>> self = new AtomicReference<>();

         ROS2Subscription<example_interfaces.Bool> subscription = node.createSubscription(topic, reader ->
         {
            callbacks.incrementAndGet();
            reader.read();
            entered.countDown();
            try
            {
               release.await(30, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
            secondReadReturnedData.set(reader.read() != null);
         }, reliableKeepAll());
         self.set(subscription);
         ROS2Publisher<example_interfaces.Bool> publisher = node.createPublisher(topic, reliableKeepAll());
         awaitMatched(publisher, subscription);

         Thread publishThread = new Thread(() ->
         {
            publisher.publish(new example_interfaces.Bool());
         }, "destroy-during-callback-publisher");
         publishThread.start();
         assertTrue(entered.await(30, TimeUnit.SECONDS));

         AtomicBoolean destroyReturned = new AtomicBoolean();
         Thread destroyer = new Thread(() ->
         {
            node.destroySubscription(subscription);
            destroyReturned.set(true);
         }, "destroyer");
         destroyer.start();
         awaitCondition(subscription::isClosed, 10_000, "not marked closed");
         Thread.sleep(300);
         assertFalse(destroyReturned.get(), "the DataReader must not be deleted while its callback runs");

         release.countDown();
         destroyer.join(30_000);
         assertTrue(destroyReturned.get());
         assertTrue(subscription.isNativeReleased());
         assertFalse(secondReadReturnedData.get(), "no new sample is taken after close");
         publishThread.join(30_000);
         assertEquals(1, callbacks.get());
      }
      finally
      {
         node.close();
      }
   }

   /**
    * With a callback executor, Fast-DDS does not fence the callback (it runs on the executor, not inside the listener), so
    * destroySubscription() itself must wait for a callback that is running: after it returns no callback of the
    * subscription may run any more, as with inline dispatch and as {@link ROS2Node#close()} documents.
    */
   @Test
   @Timeout(90)
   public void testDestroyWhileExecutorCallbackRunsWaitsForTheCallback() throws Exception
   {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      ROS2Node node = new ROS2Node("destroy_during_executor_callback", ROS2NodeOptions.defaults().callbackExecutor(executor));
      ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("destroy_during_executor_callback"), example_interfaces.Int64.class);
      try
      {
         CountDownLatch entered = new CountDownLatch(1);
         CountDownLatch release = new CountDownLatch(1);
         AtomicBoolean callbackRunning = new AtomicBoolean();
         AtomicInteger callbacks = new AtomicInteger();
         AtomicBoolean secondReadReturnedData = new AtomicBoolean(true);

         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(topic, reader ->
         {
            callbackRunning.set(true);
            try
            {
               callbacks.incrementAndGet();
               reader.read();
               entered.countDown();
               try
               {
                  release.await(30, TimeUnit.SECONDS);
               }
               catch (InterruptedException e)
               {
                  Thread.currentThread().interrupt();
               }
               secondReadReturnedData.set(reader.read() != null);
            }
            finally
            {
               callbackRunning.set(false);
            }
         }, reliableKeepAll());
         ROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic, reliableKeepAll());
         awaitMatched(publisher, subscription);

         example_interfaces.Int64 message = new example_interfaces.Int64();
         publisher.publish(message);
         assertTrue(entered.await(30, TimeUnit.SECONDS));
         publisher.publish(message); // waits behind the running callback

         AtomicBoolean destroyReturned = new AtomicBoolean();
         AtomicBoolean callbackRunningWhenDestroyReturned = new AtomicBoolean();
         Thread destroyer = new Thread(() ->
         {
            node.destroySubscription(subscription);
            callbackRunningWhenDestroyReturned.set(callbackRunning.get());
            destroyReturned.set(true);
         }, "executor-destroyer");
         destroyer.start();
         awaitCondition(subscription::isClosed, 10_000, "not marked closed");
         Thread.sleep(300);
         assertFalse(destroyReturned.get(), "destroySubscription() returned while the executor callback was still running");

         release.countDown();
         destroyer.join(30_000);
         assertTrue(destroyReturned.get());
         assertFalse(callbackRunningWhenDestroyReturned.get(), "the callback was still running when destroySubscription() returned");
         assertTrue(subscription.isNativeReleased());
         assertFalse(secondReadReturnedData.get(), "no new sample is taken after close");

         // Let the executor run whatever is still queued: no further callback may start.
         executor.submit(() -> { }).get(30, TimeUnit.SECONDS);
         assertEquals(1, callbacks.get());
      }
      finally
      {
         node.close();
         executor.shutdownNow();
      }
   }

   /**
    * Churn under load: subscriptions (inline and on a callback executor, polling and callback, some destroying themselves
    * from their own callback) and publishers are created and destroyed over and over while other threads keep publishing
    * messages of very different sizes on the same topic, so teardown races with takes, writes, matched callbacks and the
    * writer-gone notifications that deleting a writer delivers. Nothing may crash, deadlock or leave natives unreleased.
    */
   @Test
   @Timeout(300)
   public void testEndpointChurnUnderLoad() throws Exception
   {
      final int rounds = 30;
      ExecutorService executor = Executors.newFixedThreadPool(2);
      ROS2Node inlineNode = new ROS2Node("endpoint_churn_inline");
      ROS2Node executorNode = new ROS2Node("endpoint_churn_executor", ROS2NodeOptions.defaults().callbackExecutor(executor));
      ROS2Node publisherNode = new ROS2Node("endpoint_churn_publisher");
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(uniqueTopicName("endpoint_churn"), std_msgs.String_.class);
      AtomicBoolean running = new AtomicBoolean(true);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      AtomicInteger received = new AtomicInteger();
      AtomicInteger flappingPublishers = new AtomicInteger();
      List<Thread> threads = new ArrayList<>();
      try
      {
         ROS2Publisher<std_msgs.String_> stablePublisher = publisherNode.createPublisher(topic, reliableKeepLast(10));
         final int[] sizes = {8, 300, 5000, 40};
         for (int t = 0; t < 2; ++t)
         {
            final int offset = t;
            threads.add(new Thread(() ->
            {
               std_msgs.String_ message = new std_msgs.String_();
               StringBuilder builder = new StringBuilder();
               int sequence = 0;
               try
               {
                  while (running.get())
                  {
                     builder.setLength(0);
                     int size = sizes[(sequence++ + offset) % sizes.length];
                     for (int i = 0; i < size; ++i)
                     {
                        builder.append((char) ('a' + i % 26));
                     }
                     message.setData(builder.toString());
                     stablePublisher.publish(message);
                     LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(100));
                  }
               }
               catch (Throwable e)
               {
                  failure.compareAndSet(null, e);
               }
            }, "churn-publisher-" + t));
         }

         // Publishers of the subscribers' own participant come and go: deleting them delivers writer-gone notifications to
         // the inline subscriptions (with intraprocess delivery on this thread, inside deleteDataWriter).
         threads.add(new Thread(() ->
         {
            std_msgs.String_ message = new std_msgs.String_();
            message.setData("flapping");
            try
            {
               while (running.get())
               {
                  ROS2Publisher<std_msgs.String_> publisher = inlineNode.createPublisher(topic, reliableKeepLast(10));
                  for (int i = 0; i < 20 && running.get(); ++i)
                  {
                     publisher.publish(message);
                     LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                  }
                  assertTrue(inlineNode.destroyPublisher(publisher));
                  assertTrue(publisher.isNativeReleased(), "a publisher destroyed outside of callbacks is released synchronously");
                  flappingPublishers.incrementAndGet();
               }
            }
            catch (Throwable e)
            {
               failure.compareAndSet(null, e);
            }
         }, "churn-flapping-publisher"));
         for (Thread thread : threads)
         {
            thread.start();
         }

         List<ROS2Subscription<std_msgs.String_>> destroyed = new ArrayList<>();
         for (int round = 0; round < rounds && failure.get() == null; ++round)
         {
            ROS2Node node = round % 2 == 0 ? inlineNode : executorNode;
            boolean destroyFromCallback = round % 3 == 0;
            AtomicReference<ROS2Subscription<std_msgs.String_>> self = new AtomicReference<>();
            AtomicInteger count = new AtomicInteger();
            ROS2Subscription<std_msgs.String_> subscription = node.createSubscription(topic, reader ->
            {
               std_msgs.String_ data = reader.read();
               if (data != null)
               {
                  received.incrementAndGet();
                  if (count.incrementAndGet() >= 3 && destroyFromCallback)
                  {
                     ROS2Subscription<std_msgs.String_> me = self.get();
                     if (me != null && !me.isClosed())
                     {
                        node.destroySubscription(me);
                     }
                  }
               }
            }, reliableKeepLast(10));
            self.set(subscription);
            ROS2Subscription<std_msgs.String_> polling = node.createSubscription(topic, reliableKeepLast(10));

            awaitCondition(() -> count.get() >= 3 || subscription.isClosed(), 30_000, "round " + round + ": no samples");
            std_msgs.String_ data = new std_msgs.String_();
            for (int i = 0; i < 5; ++i)
            {
               polling.read(data);
               polling.readLatest(data);
            }

            if (!destroyFromCallback || !subscription.isClosed())
            {
               node.destroySubscription(subscription);
            }
            assertTrue(node.destroySubscription(polling));
            assertTrue(polling.isNativeReleased());
            destroyed.add(subscription);
         }

         running.set(false);
         for (Thread thread : threads)
         {
            thread.join(60_000);
            assertFalse(thread.isAlive(), thread.getName() + " did not stop");
         }
         assertNull(failure.get(), () -> "churn failed: " + failure.get());
         assertTrue(CallbackContext.awaitDeferred(30, TimeUnit.SECONDS), "deferred teardowns did not finish");
         for (ROS2Subscription<std_msgs.String_> subscription : destroyed)
         {
            assertTrue(subscription.isClosed());
            assertTrue(subscription.isNativeReleased());
            assertTrue(subscription.isListenerTrampolineCleared());
         }
         assertTrue(received.get() > 0);
         assertTrue(flappingPublishers.get() > 0, "no publisher was destroyed under load");
      }
      finally
      {
         running.set(false);
         for (Thread thread : threads)
         {
            thread.join(10_000);
         }
         publisherNode.close();
         executorNode.close();
         inlineNode.close();
         executor.shutdownNow();
      }
   }

   @Test
   @Timeout(60)
   public void testSubscriptionCreationFailureThrowsAndReleasesPartialResources()
   {
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(uniqueTopicName("subscription_creation_failure"), example_interfaces.Bool.class);
      ROS2Node node = new ROS2Node("subscription_creation_failure");
      try
      {
         ROS2Subscription<example_interfaces.Bool> valid = node.createSubscription(topic, reliableKeepLast(1));
         TopicData topicData = valid.getTopicData();

         AtomicInteger callbacks = new AtomicInteger();
         IllegalStateException unknownProfile = assertThrows(IllegalStateException.class,
                                                             () -> new ROS2Subscription<>(node.fastddsParticipant,
                                                                                          "jros2_no_such_profile",
                                                                                          reader -> callbacks.incrementAndGet(),
                                                                                          null,
                                                                                          topic,
                                                                                          topicData,
                                                                                          null));
         assertTrue(unknownProfile.getMessage().contains(topic.getName()), unknownProfile.getMessage());

         TopicData missingTopic = new TopicData(topicData.fastddsTopicDataWrapperType, topicData.fastddsTypeSupport, 0);
         assertThrows(IllegalStateException.class,
                      () -> new ROS2Subscription<>(node.fastddsParticipant, "jros2_no_such_profile", null, null, topic, missingTopic, null));

         // The node keeps working
         ROS2Publisher<example_interfaces.Bool> publisher = node.createPublisher(topic, reliableKeepLast(1));
         awaitMatched(publisher, valid);
         publisher.publish(new example_interfaces.Bool());
         awaitCondition(() -> valid.getUnreadMessageCount() == 1, 30_000, "sample after the failed creation");
         assertEquals(0, callbacks.get());
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(60)
   public void testGuidsAndStateAfterClose()
   {
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>(uniqueTopicName("guid_after_close"), example_interfaces.Bool.class);
      ROS2Node node = new ROS2Node("guid_after_close");
      try
      {
         ROS2Subscription<example_interfaces.Bool> subscription = node.createSubscription(topic, reliableKeepLast(1));
         ROS2Publisher<example_interfaces.Bool> publisher = node.createPublisher(topic, reliableKeepLast(1));
         Guid subscriptionGuid = new Guid();
         subscriptionGuid.set(subscription.getGuid());
         Guid publisherGuid = new Guid();
         publisherGuid.set(publisher.getGuid());
         assertNotEquals(subscriptionGuid, publisherGuid);
         assertNotEquals(new Guid(), subscriptionGuid);

         subscription.getGuid().getValue()[0] ^= 0x7f; // callers may scribble on the returned instance
         assertEquals(subscriptionGuid, subscription.getGuid(), "refreshed on each call");

         node.destroySubscription(subscription);
         node.destroyPublisher(publisher);
         assertEquals(subscriptionGuid, subscription.getGuid());
         assertEquals(publisherGuid, publisher.getGuid());
         assertFalse(subscription.read(new example_interfaces.Bool()));
         assertNull(subscription.readLatest());
         assertEquals(0, subscription.getUnreadMessageCount());
         assertEquals(0, subscription.getSubscriptionMatchedStatus());
         assertEquals(0, publisher.getPublicationMatchedStatus());
         long start = System.nanoTime();
         subscription.waitForPublisher(10_000);
         publisher.waitForSubscription(10_000);
         assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 5_000, "waits return immediately once closed");
      }
      finally
      {
         node.close();
      }
   }
}
