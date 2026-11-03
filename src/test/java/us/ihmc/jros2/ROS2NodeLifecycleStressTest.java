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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Creates and closes nodes in parallel while data flows. jros2 1.5 slept 50 ms under global locks when closing a node;
 * these tests check that creation and teardown are safe without it (participant deletion only happens after every
 * endpoint was deleted, and endpoint deletion is a fence for its callbacks).
 */
public class ROS2NodeLifecycleStressTest
{
   /** Multiplies the iteration counts; set the JROS2_STRESS_SCALE environment variable to run longer locally. */
   private static final int SCALE = Math.max(1, Integer.parseInt(System.getenv().getOrDefault("JROS2_STRESS_SCALE", "1")));
   private static final ROS2Topic<example_interfaces.Bool> TOPIC = new ROS2Topic<>("/lifecycle_stress", example_interfaces.Bool.class, ROS2QoSProfile.RELIABLE);

   @Test
   @Timeout(600)
   public void testParallelNodeLifecycles() throws InterruptedException
   {
      ROS2Node listener = new ROS2Node("lifecycle_listener");
      AtomicInteger received = new AtomicInteger();
      listener.createSubscription(TOPIC, reader ->
      {
         if (reader.read() != null)
         {
            received.incrementAndGet();
         }
      });

      try
      {
         int threads = 6;
         int iterations = 8 * SCALE;
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
                     ROS2Node node = (i % 2 == 0) ? new ROS2Node("lifecycle_" + index) : new AsyncROS2Node("lifecycle_async_" + index);
                     ROS2Publisher<example_interfaces.Bool> publisher = node.createPublisher(TOPIC);
                     ROS2Subscription<example_interfaces.Bool> subscription = node.createSubscription(TOPIC, reader -> reader.read());
                     for (int m = 0; m < 20; ++m)
                     {
                        publisher.publish(new example_interfaces.Bool());
                     }
                     node.close();
                     assertTrue(node.isClosed());
                     assertTrue(publisher.isClosed());
                     assertTrue(subscription.isClosed());
                  }
               }
               catch (Throwable e)
               {
                  failure.compareAndSet(null, e);
               }
            }, "lifecycle-" + t);
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
         assertFalse(listener.isClosed());
      }
      finally
      {
         listener.close();
      }
   }

   @Test
   @Timeout(600)
   public void testCloseWhileDataFlows() throws InterruptedException
   {
      for (int round = 0; round < 5 * SCALE; ++round)
      {
         ROS2Node publisherNode = new ROS2Node("flow_publisher");
         ROS2Node subscriberNode = new ROS2Node("flow_subscriber");
         AtomicInteger callbacks = new AtomicInteger();
         subscriberNode.createSubscription(TOPIC, reader ->
         {
            reader.read();
            callbacks.incrementAndGet();
         });
         ROS2Publisher<example_interfaces.Bool> publisher = publisherNode.createPublisher(TOPIC);
         publisher.waitForSubscription(5000);

         AtomicBoolean running = new AtomicBoolean(true);
         AtomicReference<Throwable> failure = new AtomicReference<>();
         Thread publishThread = new Thread(() ->
         {
            try
            {
               while (running.get())
               {
                  publisher.publish(new example_interfaces.Bool());
               }
            }
            catch (Throwable e)
            {
               failure.compareAndSet(null, e);
            }
         }, "flow-publisher");
         publishThread.start();
         Thread.sleep(100);

         // Alternate which side goes away first
         if (round % 2 == 0)
         {
            subscriberNode.close();
            publisherNode.close();
         }
         else
         {
            publisherNode.close();
            subscriberNode.close();
         }
         running.set(false);
         publishThread.join(5000);
         assertFalse(publishThread.isAlive());
         if (failure.get() != null)
         {
            fail(failure.get());
         }
         assertTrue(publisherNode.getParticipant().isClosed());
         assertTrue(subscriberNode.getParticipant().isClosed());
      }
   }

   @Test
   @Timeout(600)
   public void testContextNodesChurnWhileDataFlows() throws InterruptedException
   {
      try (ROS2Context context = ROS2Context.create())
      {
         ROS2Node stable = new ROS2Node("stable", ROS2NodeOptions.defaults().context(context));
         ROS2Publisher<example_interfaces.Bool> publisher = stable.createPublisher(TOPIC);
         AtomicBoolean running = new AtomicBoolean(true);
         Thread publishThread = new Thread(() ->
         {
            while (running.get())
            {
               publisher.publish(new example_interfaces.Bool());
               Thread.yield();
            }
         }, "context-publisher");
         publishThread.start();
         try
         {
            for (int i = 0; i < 20 * SCALE; ++i)
            {
               ROS2Node churn = new ROS2Node("churn", ROS2NodeOptions.defaults().context(context));
               churn.createSubscription(TOPIC, reader -> reader.read());
               churn.createPublisher(new ROS2Topic<>("/churn_" + (i % 3), example_interfaces.Bool.class)).publish(new example_interfaces.Bool());
               Thread.sleep(5);
               churn.close();
            }
         }
         finally
         {
            running.set(false);
            publishThread.join(5000);
         }
         assertEquals(1, context.getNodes().size());
         assertEquals(1, context.getParticipant().getTopicReferenceCount("rt/lifecycle_stress"));
         for (int i = 0; i < 3; ++i)
         {
            assertEquals(0, context.getParticipant().getTopicReferenceCount("rt/churn_" + i));
         }
      }
   }
}
