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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.LockSupport;

/**
 * A reentrant mutual-exclusion lock that never allocates memory, on the Java heap or natively, also when threads contend
 * for it. It guards the hot paths that several threads may enter at once (publishing on one publisher, polling one
 * subscription, recording statistics).
 * <p>
 * The JDK locks do allocate under contention: {@code synchronized} makes HotSpot inflate the object's monitor, which
 * {@code malloc}s an {@code ObjectMonitor} on the contending thread (and the JVM deflates and frees idle monitors again,
 * so this repeats; at least once a minute in JDK 17), and the {@code java.util.concurrent} locks allocate a queue node on
 * the Java heap for every thread that has to wait.
 * <p>
 * A thread that finds the lock held spins briefly, then registers in one of a fixed number of waiter slots and parks;
 * {@link #unlock()} unparks a registered waiter. When all slots are taken, further waiters poll with short timed parks.
 * The lock is not fair (a running thread may take it ahead of a waiter that was just unparked) and not interruptible
 * (an interrupt does not abort {@link #lock()}; the interrupt status is kept).
 */
final class AllocationFreeLock
{
   /** Waiters that park until they are unparked; more waiters poll. */
   static final int WAITER_SLOTS = 16;
   /** Attempts before a contending thread parks. */
   private static final int SPINS = 128;
   /** Poll interval of waiters that found no free slot. */
   private static final long POLL_NANOS = 50_000L;

   static
   {
      // Link the code of the contended path now, because contention may first happen long after warm-up, on a hot thread:
      // the first resolution of a class name through the class loader allocates (LockSupport; unpark(null) does nothing
      // else, unlike park() it cannot consume a permit the initializing thread is owed), and so does the first use of each
      // access mode of AtomicReferenceArray's VarHandle (method type and member name lookups).
      LockSupport.unpark(null);
      AtomicReferenceArray<Thread> slots = new AtomicReferenceArray<>(1);
      if (slots.get(0) == null && slots.compareAndSet(0, null, Thread.currentThread()))
      {
         slots.set(0, null);
      }
   }

   /** 0: free, 1: held. */
   private final AtomicInteger state = new AtomicInteger();
   /** Written only by the holder (and read by it to detect reentry). */
   private Thread owner;
   private int holds;
   /** Number of threads in {@link #lockContended}, registered in a slot or polling. */
   private final AtomicInteger waiting = new AtomicInteger();
   private final AtomicReferenceArray<Thread> waiters = new AtomicReferenceArray<>(WAITER_SLOTS);

   /**
    * Acquires the lock, waiting as long as necessary. Reentrant: a thread that holds the lock acquires it again
    * immediately and must call {@link #unlock()} once per {@code lock()}.
    */
   void lock()
   {
      Thread current = Thread.currentThread();
      if (owner == current)
      {
         holds++;
         return;
      }
      if (!state.compareAndSet(0, 1))
      {
         lockContended(current);
      }
      owner = current;
      holds = 1;
   }

   /**
    * @return true if the lock was free (or already held by the calling thread) and is now held by the calling thread
    */
   boolean tryLock()
   {
      Thread current = Thread.currentThread();
      if (owner == current)
      {
         holds++;
         return true;
      }
      if (state.compareAndSet(0, 1))
      {
         owner = current;
         holds = 1;
         return true;
      }
      return false;
   }

   /**
    * Releases the lock once.
    *
    * @throws IllegalMonitorStateException if the calling thread does not hold the lock
    */
   void unlock()
   {
      if (owner != Thread.currentThread())
      {
         throw new IllegalMonitorStateException(HotPathText.lockNotHeld());
      }
      if (--holds > 0)
      {
         return;
      }
      owner = null;
      state.set(0);
      // A waiter registers (slot, then count) before it tries the lock again, so either it sees the lock free or we see it.
      if (waiting.get() != 0)
      {
         unparkWaiter();
      }
   }

   /**
    * @return the number of threads waiting for the lock (registered in a slot or polling)
    */
   int getWaitingThreadCount()
   {
      return waiting.get();
   }

   /**
    * @return true if the calling thread holds the lock
    */
   boolean isHeldByCurrentThread()
   {
      return owner == Thread.currentThread();
   }

   private void lockContended(Thread current)
   {
      // Busy-wait without Thread.onSpinWait(), which Android only has from API 33.
      for (int i = 0; i < SPINS; ++i)
      {
         if (state.get() == 0 && state.compareAndSet(0, 1))
         {
            return;
         }
      }

      int slot = -1;
      for (int i = 0; i < WAITER_SLOTS; ++i)
      {
         if (waiters.get(i) == null && waiters.compareAndSet(i, null, current))
         {
            slot = i;
            break;
         }
      }
      waiting.incrementAndGet();
      boolean interrupted = false;
      try
      {
         while (!state.compareAndSet(0, 1))
         {
            if (slot >= 0)
            {
               LockSupport.park(this);
            }
            else
            {
               LockSupport.parkNanos(this, POLL_NANOS);
            }
            // park() returns immediately while the interrupt status is set: clear it, and restore it once locked.
            interrupted |= Thread.interrupted();
         }
      }
      finally
      {
         waiting.decrementAndGet();
         if (slot >= 0)
         {
            waiters.set(slot, null);
         }
      }
      if (interrupted)
      {
         current.interrupt();
      }
   }

   private void unparkWaiter()
   {
      for (int i = 0; i < WAITER_SLOTS; ++i)
      {
         Thread waiter = waiters.get(i);
         if (waiter != null)
         {
            LockSupport.unpark(waiter);
            return;
         }
      }
   }
}
