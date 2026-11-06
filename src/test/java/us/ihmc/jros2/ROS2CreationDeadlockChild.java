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

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Child JVM of {@link ROS2CreationDeadlockTest}: while a subscription callback of node {@code sub} (participant P1) runs
 * on a Fast-DDS thread, the main thread creates a DDS entity that Fast-DDS makes wait for that callback, and the callback
 * creates something on another participant. Without the creation-lock detection both threads wait for each other
 * forever (measured with Fast-DDS 3.6.2). Prints one {@code RESULT} line, then {@code CLOSED} after a normal teardown; a
 * watchdog prints {@code RESULT DEADLOCK} with the stacks and kills the JVM.
 * <p>
 * Cases: {@code same_participant} (main creates a subscription on P1, the callback a publisher on P2),
 * {@code new_node} (main creates a subscription on P1, the callback a new node) and {@code matching_endpoint} (main
 * creates, on a third participant, a publisher that matches the callback's subscription; a Fast-DDS discovery thread
 * then waits for the callback's reader while it holds the PDP lock of the callback's participant).
 */
public class ROS2CreationDeadlockChild
{
   private static void killSelf()
   {
      System.out.flush();
      try
      {
         // Runtime.halt runs native static destructors, which can wait for the stuck Fast-DDS threads
         new ProcessBuilder("kill", "-9", Long.toString(ProcessHandle.current().pid())).start().waitFor();
      }
      catch (Exception e)
      {
         Runtime.getRuntime().halt(3);
      }
   }

   public static void main(String[] args) throws Exception
   {
      String testCase = args[0];
      // Keep the run short: the cases that can only be caught by the timeout take this long
      ROS2Participant.callbackCreationTimeoutMs = 2000;

      Thread watchdog = new Thread(() ->
      {
         try
         {
            Thread.sleep(25000);
         }
         catch (InterruptedException e)
         {
            return;
         }
         System.out.println("RESULT DEADLOCK");
         for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet())
         {
            System.out.println("THREAD " + entry.getKey().getName() + " " + entry.getKey().getState());
            StackTraceElement[] stack = entry.getValue();
            for (int i = 0; i < Math.min(stack.length, 12); ++i)
            {
               System.out.println("    at " + stack[i]);
            }
         }
         killSelf();
      }, "deadlock-watchdog");
      watchdog.setDaemon(true);
      watchdog.start();

      ROS2Node publisherNode = new ROS2Node("deadlock_child_pub");
      ROS2Node subscriberNode = new ROS2Node("deadlock_child_sub");
      ROS2Node otherNode = new ROS2Node("deadlock_child_other");
      ROS2Node creatorNode = new ROS2Node("deadlock_child_creator");
      ROS2Topic<example_interfaces.Bool> topic = new ROS2Topic<>("/deadlock_child", example_interfaces.Bool.class, ROS2QoSProfile.RELIABLE);
      ROS2Participant expectedTarget = testCase.equals("matching_endpoint") ? creatorNode.getParticipant() : subscriberNode.getParticipant();

      AtomicBoolean first = new AtomicBoolean();
      AtomicBoolean supported = new AtomicBoolean();
      AtomicBoolean creatorSeen = new AtomicBoolean();
      AtomicReference<String> callbackResult = new AtomicReference<>("none");
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(1);

      subscriberNode.createSubscription(topic, reader ->
      {
         reader.read();
         if (!first.compareAndSet(false, true))
         {
            return;
         }
         // Without a subscription that marks its callbacks, the node cannot detect anything and this would deadlock
         supported.set(CallbackContext.isInCallback());
         entered.countDown();
         if (!supported.get())
         {
            done.countDown();
            return;
         }
         try
         {
            // Wait until the main thread is inside the native create
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (System.nanoTime() < deadline && ROS2Participant.getNativeCreateTarget() != expectedTarget)
            {
               Thread.sleep(1);
            }
            creatorSeen.set(ROS2Participant.getNativeCreateTarget() == expectedTarget);
            Thread.sleep(50);

            if (testCase.equals("new_node"))
            {
               ROS2Node created = new ROS2Node("deadlock_child_created");
               created.close();
            }
            else
            {
               otherNode.createPublisher(new ROS2Topic<>("/deadlock_child_created", example_interfaces.Bool.class));
            }
            callbackResult.set("created");
         }
         catch (IllegalStateException e)
         {
            callbackResult.set(e.getMessage() != null && e.getMessage().contains("ROS2NodeOptions.callbackExecutor") ? "rejected" : "ise:" + e.getMessage());
         }
         catch (Throwable t)
         {
            callbackResult.set("error:" + t);
         }
         done.countDown();
      });

      ROS2Publisher<example_interfaces.Bool> publisher = publisherNode.createPublisher(topic);
      publisher.waitForSubscription(5000);
      Thread publishThread = new Thread(() ->
      {
         for (int i = 0; i < 200 && entered.getCount() > 0; ++i)
         {
            publisher.publish(new example_interfaces.Bool());
            try
            {
               Thread.sleep(50);
            }
            catch (InterruptedException e)
            {
               return;
            }
         }
      }, "deadlock-child-publish");
      publishThread.setDaemon(true);
      publishThread.start();

      if (!entered.await(15, TimeUnit.SECONDS))
      {
         System.out.println("RESULT NO_CALLBACK");
         killSelf();
      }
      if (!supported.get())
      {
         System.out.println("RESULT SKIP");
         killSelf();
      }

      long start = System.nanoTime();
      if (testCase.equals("matching_endpoint"))
      {
         creatorNode.createPublisher(topic);
      }
      else
      {
         subscriberNode.createSubscription(new ROS2Topic<>("/deadlock_child_main", example_interfaces.Bool.class));
      }
      long mainMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      boolean callbackDone = done.await(15, TimeUnit.SECONDS);
      System.out.println("RESULT OK case=" + testCase + " main=" + mainMs + "ms callbackDone=" + callbackDone + " callback=" + callbackResult.get()
                         + " creatorSeen=" + creatorSeen.get());
      System.out.flush();

      subscriberNode.close();
      publisherNode.close();
      otherNode.close();
      creatorNode.close();
      System.out.println("CLOSED");
      killSelf();
   }
}
