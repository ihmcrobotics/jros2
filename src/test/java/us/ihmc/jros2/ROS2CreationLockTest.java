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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The process-wide creation lock ({@link ROS2Participant#lockCreation(String)}) as seen from a thread that runs a
 * Fast-DDS callback. The creator side is simulated: a helper thread holds the lock (optionally marking a native create
 * on some participant) the way a thread blocked inside Fast-DDS would. The real deadlocks this prevents are reproduced
 * in {@link ROS2CreationDeadlockTest}.
 */
public class ROS2CreationLockTest
{
   private long savedConfirmationMs;
   private long savedTimeoutMs;

   @BeforeEach
   public void saveSettings()
   {
      savedConfirmationMs = ROS2Participant.callbackDeadlockConfirmationMs;
      savedTimeoutMs = ROS2Participant.callbackCreationTimeoutMs;
   }

   @AfterEach
   public void restoreSettings()
   {
      ROS2Participant.callbackDeadlockConfirmationMs = savedConfirmationMs;
      ROS2Participant.callbackCreationTimeoutMs = savedTimeoutMs;
   }

   /**
    * Holds the creation lock on a helper thread until {@code release} opens, inside a simulated native create on
    * {@code target} (or outside any native create when {@code target} is null).
    */
   private static Thread holdCreationLock(ROS2Participant target, CountDownLatch held, CountDownLatch release)
   {
      Thread holder = new Thread(() ->
      {
         ROS2Participant.lockCreation("test");
         try
         {
            ROS2Participant previous = target == null ? null : target.enterNativeCreate();
            try
            {
               held.countDown();
               release.await(30, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
            finally
            {
               if (target != null)
               {
                  ROS2Participant.exitNativeCreate(previous);
               }
            }
         }
         finally
         {
            ROS2Participant.unlockCreation();
         }
      }, "creation-lock-holder");
      holder.setDaemon(true);
      holder.start();
      return holder;
   }

   /** Runs {@code action} as if inside a Fast-DDS callback of {@code participant}. */
   private static void inCallbackOf(ROS2Participant participant, Runnable action)
   {
      participant.enterUserCallback();
      try
      {
         ROS2CallbackTestTools.asCallback(action);
      }
      finally
      {
         participant.exitUserCallback();
      }
   }

   @Test
   @Timeout(30)
   public void testCallbackThrowsWhenTheCreatorWaitsForItsParticipant() throws InterruptedException
   {
      ROS2Node subscriberNode = new ROS2Node("creation_lock_subscriber");
      ROS2Node otherNode = new ROS2Node("creation_lock_other");
      CountDownLatch held = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      Thread holder = holdCreationLock(subscriberNode.getParticipant(), held, release);
      try
      {
         assertTrue(held.await(10, TimeUnit.SECONDS));
         assertSame(subscriberNode.getParticipant(), ROS2Participant.getNativeCreateTarget());
         ROS2Participant.callbackDeadlockConfirmationMs = 200;
         ROS2Participant.callbackCreationTimeoutMs = 20000;

         AtomicReference<Throwable> error = new AtomicReference<>();
         long start = System.nanoTime();
         inCallbackOf(subscriberNode.getParticipant(), () -> error.set(assertThrows(IllegalStateException.class,
                                                                                     () -> otherNode.createPublisher(new ROS2Topic<>("/creation_lock",
                                                                                                                                     example_interfaces.Bool.class)))));
         long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

         assertTrue(error.get().getMessage().contains("ROS2NodeOptions.callbackExecutor"), error.get().getMessage());
         assertTrue(error.get().getMessage().contains("until this callback returns"), error.get().getMessage());
         // Detected by the confirmation delay, long before the overall timeout
         assertTrue(elapsedMs >= 150 && elapsedMs < 10000, "took " + elapsedMs + " ms");
         // Nothing was registered by the failed call
         assertTrue(otherNode.getPublishers().isEmpty());
         assertEquals(0, otherNode.getParticipant().getTopicReferenceCount("rt/creation_lock"));
      }
      finally
      {
         release.countDown();
         holder.join(10000);
         subscriberNode.close();
         otherNode.close();
      }
      assertNull(ROS2Participant.getNativeCreateTarget());

      // Once the lock is free, the same creation from the "callback" works again
      ROS2Node a = new ROS2Node("creation_lock_after_a");
      ROS2Node b = new ROS2Node("creation_lock_after_b");
      try
      {
         AtomicReference<Object> created = new AtomicReference<>();
         inCallbackOf(a.getParticipant(), () -> created.set(b.createPublisher(new ROS2Topic<>("/creation_lock", example_interfaces.Bool.class))));
         assertInstanceOf(ROS2Publisher.class, created.get());
      }
      finally
      {
         a.close();
         b.close();
      }
   }

   @Test
   @Timeout(30)
   public void testCallbackTimesOutWhenTheCreatorDoesNotFinish() throws InterruptedException
   {
      ROS2Node subscriberNode = new ROS2Node("creation_timeout_subscriber");
      ROS2Node otherNode = new ROS2Node("creation_timeout_other");
      ROS2Node creatorNode = new ROS2Node("creation_timeout_creator");
      CountDownLatch held = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      // A creator stuck in a native create on a third participant: with intraprocess delivery that happens when the new
      // endpoint matches the callback's subscription. It cannot be attributed, so only the timeout catches it.
      Thread holder = holdCreationLock(creatorNode.getParticipant(), held, release);
      try
      {
         assertTrue(held.await(10, TimeUnit.SECONDS));
         ROS2Participant.callbackDeadlockConfirmationMs = 100;
         ROS2Participant.callbackCreationTimeoutMs = 600;

         AtomicReference<Throwable> error = new AtomicReference<>();
         long start = System.nanoTime();
         inCallbackOf(subscriberNode.getParticipant(),
                      () -> error.set(assertThrows(IllegalStateException.class, () -> new ROS2Node("creation_timeout_new_node"))));
         long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

         assertTrue(error.get().getMessage().contains("600 ms"), error.get().getMessage());
         assertTrue(error.get().getMessage().contains("ROS2NodeOptions.callbackExecutor"), error.get().getMessage());
         assertTrue(elapsedMs >= 550 && elapsedMs < 10000, "took " + elapsedMs + " ms");
         for (ROS2Node node : ROS2Node.getActiveNodes())
         {
            assertNotEquals("creation_timeout_new_node", node.getName());
         }
      }
      finally
      {
         release.countDown();
         holder.join(10000);
         subscriberNode.close();
         otherNode.close();
         creatorNode.close();
      }
   }

   @Test
   @Timeout(30)
   public void testCallbackWaitsForACreatorThatFinishes() throws InterruptedException
   {
      ROS2Node subscriberNode = new ROS2Node("creation_wait_subscriber");
      ROS2Node otherNode = new ROS2Node("creation_wait_other");
      CountDownLatch held = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      // The holder is not inside a native create on the callback's participant and finishes after 300 ms
      Thread holder = holdCreationLock(null, held, release);
      try
      {
         assertTrue(held.await(10, TimeUnit.SECONDS));
         ROS2Participant.callbackDeadlockConfirmationMs = 50;
         ROS2Participant.callbackCreationTimeoutMs = 20000;
         Thread releaser = new Thread(() ->
         {
            try
            {
               Thread.sleep(300);
            }
            catch (InterruptedException e)
            {
               return;
            }
            release.countDown();
         });
         releaser.start();

         AtomicReference<Object> created = new AtomicReference<>();
         long start = System.nanoTime();
         inCallbackOf(subscriberNode.getParticipant(),
                      () -> created.set(otherNode.createSubscription(new ROS2Topic<>("/creation_wait", example_interfaces.Bool.class))));
         long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
         assertInstanceOf(ROS2Subscription.class, created.get());
         assertTrue(elapsedMs >= 250, "the callback must have waited for the holder, took " + elapsedMs + " ms");
         releaser.join();
      }
      finally
      {
         release.countDown();
         holder.join(10000);
         subscriberNode.close();
         otherNode.close();
      }
   }

   @Test
   @Timeout(30)
   public void testOutsideCallbacksCreationWaitsForTheLock() throws InterruptedException
   {
      ROS2Node node = new ROS2Node("creation_outside_callback");
      CountDownLatch held = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      Thread holder = holdCreationLock(node.getParticipant(), held, release);
      try
      {
         assertTrue(held.await(10, TimeUnit.SECONDS));
         ROS2Participant.callbackDeadlockConfirmationMs = 10;
         ROS2Participant.callbackCreationTimeoutMs = 10;

         AtomicReference<Object> created = new AtomicReference<>();
         Thread creator = new Thread(() -> created.set(node.createPublisher(new ROS2Topic<>("/creation_outside", example_interfaces.Bool.class))));
         creator.start();
         creator.join(1000);
         // Not in a callback: no deadlock detection, the creator simply waits for the lock
         assertTrue(creator.isAlive());
         assertNull(created.get());

         release.countDown();
         creator.join(10000);
         assertInstanceOf(ROS2Publisher.class, created.get());
      }
      finally
      {
         release.countDown();
         holder.join(10000);
         node.close();
      }
   }

   @Test
   public void testNativeCreateMarkingRequiresTheLockAndNests()
   {
      ROS2Node a = new ROS2Node("creation_marking_a");
      ROS2Node b = new ROS2Node("creation_marking_b");
      try
      {
         assertThrows(IllegalStateException.class, () -> a.getParticipant().enterNativeCreate());

         ROS2Participant.lockCreation("test");
         try
         {
            ROS2Participant outer = a.getParticipant().enterNativeCreate();
            assertNull(outer);
            // Re-entrant creation from a callback that runs inside the native create (e.g. an intraprocess matched callback)
            ROS2Participant.lockCreation("test");
            try
            {
               ROS2Participant inner = b.getParticipant().enterNativeCreate();
               assertSame(a.getParticipant(), inner);
               assertSame(b.getParticipant(), ROS2Participant.getNativeCreateTarget());
               ROS2Participant.exitNativeCreate(inner);
            }
            finally
            {
               ROS2Participant.unlockCreation();
            }
            assertSame(a.getParticipant(), ROS2Participant.getNativeCreateTarget());
            ROS2Participant.exitNativeCreate(outer);
            assertNull(ROS2Participant.getNativeCreateTarget());
         }
         finally
         {
            ROS2Participant.unlockCreation();
         }
      }
      finally
      {
         a.close();
         b.close();
      }
   }
}
