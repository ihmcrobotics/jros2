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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Tracks whether the current thread is running a jros2 callback (subscription data, matched events) and runs deferred
 * teardown work.
 * <p>
 * Fast-DDS deadlocks when an endpoint of a participant is deleted from inside a callback of that participant, and with
 * intraprocess delivery those callbacks also run inside the user's own {@code write()}/{@code delete} calls. Closing a
 * subscription, publisher or node while {@link #isInCallback()} is true must therefore mark the object closed and hand the
 * native teardown to {@link #runDeferred(Runnable)}.
 * <p>
 * Deferred tasks run in submission order on a single daemon thread that is never a Fast-DDS callback thread. Code that
 * deletes a participant must call {@link #awaitDeferred(long, TimeUnit)} first (when it is not itself in a callback), so
 * that endpoint teardowns deferred from callbacks have finished before the participant is deleted.
 */
final class CallbackContext
{
   private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

   private static volatile Thread deferredThread;

   private static final ExecutorService DEFERRED = Executors.newSingleThreadExecutor(runnable ->
   {
      Thread thread = new Thread(runnable, HotPathText.deferredThreadName());
      thread.setDaemon(true);
      deferredThread = thread;
      return thread;
   });

   private CallbackContext()
   {
   }

   /**
    * Creates the per-thread state that callbacks use (the thread-local values of this class and of
    * {@link ROS2Participant}). Their first use on a thread allocates; threads that jros2 creates call this when they start,
    * so that a thread that runs its first callback late does not allocate then.
    */
   static void prepareThread()
   {
      DEPTH.get();
      ROS2Participant.CallbackStack.current();
   }

   /** Marks the current thread as running a jros2 callback. Calls must be balanced with {@link #exit()}. */
   static void enter()
   {
      DEPTH.get()[0]++;
   }

   /** Ends the region started by {@link #enter()}. */
   static void exit()
   {
      DEPTH.get()[0]--;
   }

   /** @return true if the current thread is inside a jros2 callback. */
   static boolean isInCallback()
   {
      return DEPTH.get()[0] > 0;
   }

   /** @return true if the current thread is the thread that runs {@link #runDeferred(Runnable) deferred} tasks. */
   static boolean isDeferredThread()
   {
      return Thread.currentThread() == deferredThread;
   }

   /**
    * Runs {@code task} later on a dedicated thread that is never a Fast-DDS callback thread. Tasks run one at a time in
    * submission order. Exceptions thrown by a task are logged.
    */
   static void runDeferred(Runnable task)
   {
      try
      {
         DEFERRED.execute(() ->
         {
            try
            {
               task.run();
            }
            catch (Throwable throwable)
            {
               jros2.getLogger().log(Level.SEVERE, HotPathText.deferredTaskFailed(), throwable);
            }
         });
      }
      catch (RejectedExecutionException rejected)
      {
         // Cannot happen with the unbounded single-thread executor unless it was shut down; never lose a teardown.
         task.run();
      }
   }

   /**
    * Waits until every task submitted to {@link #runDeferred(Runnable)} before this call has finished. Returns
    * immediately when called from the deferred thread itself (earlier tasks have already run) or from inside a callback
    * (waiting there could deadlock; the caller must defer its own work instead).
    *
    * @return true if all earlier tasks finished within the timeout
    */
   static boolean awaitDeferred(long timeout, TimeUnit unit)
   {
      if (isDeferredThread() || isInCallback())
      {
         return true;
      }

      CountDownLatch marker = new CountDownLatch(1);
      runDeferred(marker::countDown);
      try
      {
         return marker.await(timeout, unit);
      }
      catch (InterruptedException interruptedException)
      {
         Thread.currentThread().interrupt();
         return false;
      }
   }
}
