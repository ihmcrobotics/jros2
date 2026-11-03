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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.jros2.PubSubTestTools.*;

/**
 * Waiting for a matching endpoint ({@link ROS2Publisher#waitForSubscription}, {@link ROS2Subscription#waitForPublisher}):
 * timeouts, interruption (returns at once and keeps the interrupt status) and closing; the internal subscription-matched
 * callback used for services; and the argument checks of the raw payload write.
 */
public class ROS2EndpointWaitTest
{
   @Test
   @Timeout(60)
   public void testWaitingTimesOutWithoutAMatch()
   {
      ROS2Node node = new ROS2Node("endpoint_wait_timeout");
      try
      {
         ROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(new ROS2Topic<>(uniqueTopicName("wait_pub_only"), example_interfaces.Int64.class));
         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(new ROS2Topic<>(uniqueTopicName("wait_sub_only"),
                                                                                                           example_interfaces.Int64.class));

         long start = System.nanoTime();
         assertFalse(publisher.waitForSubscription(300));
         assertFalse(subscription.waitForPublisher(300));
         long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
         assertTrue(elapsedMs >= 590, "both waits must last their timeout: " + elapsedMs + " ms");

         assertFalse(publisher.waitForSubscription(0), "a zero timeout does not wait");
         assertFalse(subscription.waitForPublisher(-5), "a negative timeout does not wait");
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(60)
   public void testInterruptedWaitReturnsAtOnceAndKeepsTheInterruptStatus()
   {
      ROS2Node node = new ROS2Node("endpoint_wait_interrupted");
      try
      {
         ROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(new ROS2Topic<>(uniqueTopicName("wait_interrupted_pub"),
                                                                                                 example_interfaces.Int64.class));
         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(new ROS2Topic<>(uniqueTopicName("wait_interrupted_sub"),
                                                                                                           example_interfaces.Int64.class));

         long start = System.nanoTime();
         Thread.currentThread().interrupt();
         assertFalse(publisher.waitForSubscription(20_000));
         assertTrue(Thread.interrupted(), "the interrupt status must be kept (and is cleared here)");

         Thread.currentThread().interrupt();
         assertFalse(subscription.waitForPublisher(20_000));
         assertTrue(Thread.interrupted(), "the interrupt status must be kept (and is cleared here)");
         assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 10_000, "an interrupted wait must not wait for its timeout");
      }
      finally
      {
         Thread.interrupted();
         node.close();
      }
   }

   @Test
   @Timeout(60)
   public void testClosingEndsAWait() throws InterruptedException
   {
      ROS2Node node = new ROS2Node("endpoint_wait_closed");
      ROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(new ROS2Topic<>(uniqueTopicName("wait_closed_pub"), example_interfaces.Int64.class));
      ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(new ROS2Topic<>(uniqueTopicName("wait_closed_sub"),
                                                                                                        example_interfaces.Int64.class));
      boolean[] results = {true, true};
      Thread publisherWaiter = new Thread(() -> results[0] = publisher.waitForSubscription(30_000), "publisher-waiter");
      Thread subscriptionWaiter = new Thread(() -> results[1] = subscription.waitForPublisher(30_000), "subscription-waiter");
      publisherWaiter.start();
      subscriptionWaiter.start();
      awaitCondition(() -> publisherWaiter.getState() == Thread.State.TIMED_WAITING && subscriptionWaiter.getState() == Thread.State.TIMED_WAITING,
                     10_000,
                     "waiters did not start waiting");

      long start = System.nanoTime();
      node.close();
      publisherWaiter.join(20_000);
      subscriptionWaiter.join(20_000);
      assertFalse(publisherWaiter.isAlive());
      assertFalse(subscriptionWaiter.isAlive());
      assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 20_000, "closing must wake the waiters");
      assertFalse(results[0]);
      assertFalse(results[1]);

      // Closed endpoints return at once
      assertFalse(publisher.waitForSubscription(10_000));
      assertFalse(subscription.waitForPublisher(10_000));
      assertEquals(0, subscription.getSubscriptionMatchedStatus());
   }

   @Test
   @Timeout(60)
   public void testSubscriptionMatchedCallback()
   {
      List<String> severe = new ArrayList<>();
      Handler handler = new Handler()
      {
         @Override
         public void publish(LogRecord record)
         {
            if (record.getLevel() == Level.SEVERE && record.getMessage() != null)
            {
               synchronized (severe)
               {
                  severe.add(record.getMessage());
               }
            }
         }

         @Override
         public void flush()
         {
         }

         @Override
         public void close()
         {
         }
      };
      jros2.getLogger().addHandler(handler);
      ROS2Node node = new ROS2Node("endpoint_matched_callback");
      try
      {
         ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("matched_callback"), example_interfaces.Int64.class);
         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(topic, reliableKeepAll());

         // Not matched yet: setting the callback does not run it
         AtomicInteger calls = new AtomicInteger();
         subscription.setOnSubscriptionMatchedCallback(calls::incrementAndGet);
         assertEquals(0, calls.get());

         ROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic, reliableKeepAll());
         awaitMatched(publisher, subscription);
         awaitCondition(() -> calls.get() >= 1, 30_000, "the matched callback did not run on the match");

         // Already matched: the callback runs at once, and its exception is logged instead of thrown
         AtomicInteger throwingCalls = new AtomicInteger();
         assertDoesNotThrow(() -> subscription.setOnSubscriptionMatchedCallback(() ->
         {
            throwingCalls.incrementAndGet();
            throw new IllegalStateException("boom from the internal matched callback");
         }));
         assertEquals(1, throwingCalls.get());
         synchronized (severe)
         {
            assertTrue(severe.contains("boom from the internal matched callback"), severe.toString());
         }

         // Later match changes run it again
         ROS2Publisher<example_interfaces.Int64> secondPublisher = node.createPublisher(topic, reliableKeepAll());
         awaitCondition(() -> subscription.getSubscriptionMatchedStatus() == 2, 30_000, "second publisher not matched");
         awaitCondition(() -> throwingCalls.get() >= 2, 30_000, "the matched callback did not run for the second publisher");
         assertTrue(secondPublisher.waitForSubscription(10_000));

         // Cleared: no more calls
         subscription.setOnSubscriptionMatchedCallback(null);
         int before = throwingCalls.get();
         assertTrue(node.destroyPublisher(secondPublisher));
         awaitCondition(() -> subscription.getSubscriptionMatchedStatus() == 1, 30_000, "second publisher not unmatched");
         assertEquals(before, throwingCalls.get());
      }
      finally
      {
         node.close();
         jros2.getLogger().removeHandler(handler);
      }
   }

   @Test
   @Timeout(60)
   public void testRawPayloadWriteChecks()
   {
      ROS2Node node = new ROS2Node("endpoint_raw_payload");
      try
      {
         ROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(new ROS2Topic<>(uniqueTopicName("raw_payload"), example_interfaces.Int64.class));
         ByteBuffer heap = ByteBuffer.allocate(12);
         assertThrows(IllegalArgumentException.class, () -> publisher.writeSerializedPayload(heap, 12), "only direct buffers can be written");

         ByteBuffer direct = ByteBuffer.allocateDirect(12);
         direct.put(new byte[] {0, 1, 0, 0, 42, 0, 0, 0, 0, 0, 0, 0}); // CDR_LE header and an int64
         assertEquals(0, publisher.writeSerializedPayload(direct, 12), "RETCODE_OK");

         assertTrue(node.destroyPublisher(publisher));
         assertEquals(-1, publisher.writeSerializedPayload(direct, 12), "a closed publisher writes nothing");
      }
      finally
      {
         node.close();
      }
   }
}
