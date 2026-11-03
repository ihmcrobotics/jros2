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

import us.ihmc.fastddsjava.natives.fastddsjava;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Helpers shared by the publisher/subscription tests.
 */
final class PubSubTestTools
{
   private static final AtomicLong TOPIC_COUNTER = new AtomicLong();
   private static final long RUN_ID = Math.abs(System.nanoTime() % 1_000_000L);

   private PubSubTestTools()
   {
   }

   /** @return a topic name that no other test in this JVM uses. */
   static String uniqueTopicName(String base)
   {
      return "/jros2_test/" + base + "_" + RUN_ID + "_" + TOPIC_COUNTER.incrementAndGet();
   }

   /** RELIABLE, VOLATILE, KEEP_ALL. */
   static ROS2QoSProfile reliableKeepAll()
   {
      ROS2QoSProfile qos = new ROS2QoSProfile();
      qos.reliability(ROS2QoSProfile.Reliability.RELIABLE);
      qos.durability(ROS2QoSProfile.Durability.VOLATILE);
      qos.history(ROS2QoSProfile.History.KEEP_ALL);
      return qos;
   }

   /** RELIABLE, VOLATILE, KEEP_LAST(depth). */
   static ROS2QoSProfile reliableKeepLast(int depth)
   {
      ROS2QoSProfile qos = new ROS2QoSProfile();
      qos.reliability(ROS2QoSProfile.Reliability.RELIABLE);
      qos.durability(ROS2QoSProfile.Durability.VOLATILE);
      qos.history(ROS2QoSProfile.History.KEEP_LAST);
      qos.depth(depth);
      return qos;
   }

   /** RELIABLE, TRANSIENT_LOCAL, KEEP_LAST(depth). */
   static ROS2QoSProfile reliableTransientLocal(int depth)
   {
      ROS2QoSProfile qos = reliableKeepLast(depth);
      qos.durability(ROS2QoSProfile.Durability.TRANSIENT_LOCAL);
      return qos;
   }

   /** @return true if the loaded natives provide the ABI v2 single-call write/take paths. */
   static boolean nativeAbiV2Available()
   {
      return fastddsjava.abiVersion() >= fastddsjava.ABI_V2;
   }

   /** @return true if this JVM uses Fast-DDS intraprocess delivery. */
   static boolean intraprocessDelivery()
   {
      return jros2.get().intraprocessDelivery();
   }

   /**
    * Forces endpoints created inside the returned scope to use the ABI v1 native paths when {@code forceV1} is true.
    * With ABI v1 natives both values exercise the same (v1) path.
    */
   static AbiScope abi(boolean forceV1)
   {
      return new AbiScope(forceV1);
   }

   static final class AbiScope implements AutoCloseable
   {
      private final boolean previous;
      private final boolean forceV1;

      private AbiScope(boolean forceV1)
      {
         this.forceV1 = forceV1;
         previous = EndpointSupport.forceNativeAbiV1;
         EndpointSupport.forceNativeAbiV1 = forceV1;
      }

      /** @return true if endpoints created in this scope use the ABI v2 paths. */
      boolean expectV2()
      {
         return !forceV1 && nativeAbiV2Available();
      }

      void assertPath(ROS2Publisher<?> publisher)
      {
         assertEquals(expectV2(), publisher.usesNativeAbiV2(), "publisher native ABI path");
      }

      void assertPath(ROS2Subscription<?> subscription)
      {
         assertEquals(expectV2(), subscription.usesNativeAbiV2(), "subscription native ABI path");
      }

      @Override
      public void close()
      {
         EndpointSupport.forceNativeAbiV1 = previous;
      }
   }

   /** Polls {@code condition} every millisecond until it holds or the timeout expires. */
   static boolean waitFor(BooleanSupplier condition, long timeoutMs)
   {
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
      while (!condition.getAsBoolean())
      {
         if (System.nanoTime() - deadline > 0)
         {
            return condition.getAsBoolean();
         }
         LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
      }
      return true;
   }

   /** Like {@link #waitFor} but fails the test on timeout. */
   static void awaitCondition(BooleanSupplier condition, long timeoutMs, String message)
   {
      if (!waitFor(condition, timeoutMs))
      {
         fail("Timed out after " + timeoutMs + " ms: " + message);
      }
   }

   /** Waits until both endpoints have discovered each other. */
   static void awaitMatched(ROS2Publisher<?> publisher, ROS2Subscription<?> subscription)
   {
      awaitCondition(() -> publisher.getPublicationMatchedStatus() > 0 && subscription.getSubscriptionMatchedStatus() > 0,
                     15_000,
                     "publisher and subscription did not match");
   }

   /**
    * Runs {@code ros2 topic echo --once topicName} until its output contains {@code expected}, at most 5 times.
    * <p>
    * The CLI exits right away ("topic ... does not appear to be published yet") when its graph has not discovered the
    * publisher yet, which races the first attempt on a loaded machine. Only use this for TRANSIENT_LOCAL samples (or
    * publishers that keep publishing), so that a later attempt still receives the message under test.
    *
    * @return the output of the last attempt
    */
   static String ros2EchoOnceUntil(int domainId, String topicName, String expected) throws java.io.IOException, InterruptedException
   {
      String result = "";
      for (int attempt = 0; attempt < 5; ++attempt)
      {
         if (attempt > 0)
         {
            Thread.sleep(500);
         }
         result = ROS2TestTools.ros2EchoOnce(domainId, topicName);
         if (result.contains(expected))
         {
            break;
         }
      }
      return result;
   }

   /** Runs the garbage collector until {@code condition} holds or the timeout expires. */
   static boolean gcUntil(BooleanSupplier condition, long timeoutMs)
   {
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
      while (!condition.getAsBoolean())
      {
         if (System.nanoTime() - deadline > 0)
         {
            return false;
         }
         System.gc();
         LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
      }
      return true;
   }
}
