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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Functional tests of {@link AllocationFreeLock}: mutual exclusion under contention (more waiters than slots too),
 * reentrancy, tryLock, ownership checks and interrupts. Its allocation behavior is checked by
 * {@code AllocationFreeLockAllocationTest}.
 */
public class AllocationFreeLockTest
{
   @Test
   @Timeout(60)
   public void testMutualExclusionWithMoreThreadsThanSlots() throws InterruptedException
   {
      AllocationFreeLock lock = new AllocationFreeLock();
      int threads = AllocationFreeLock.WAITER_SLOTS + 8;
      int increments = 20_000;
      long[] counter = new long[1];
      AtomicInteger inside = new AtomicInteger();
      AtomicBoolean overlap = new AtomicBoolean();
      Thread[] workers = new Thread[threads];
      CountDownLatch start = new CountDownLatch(1);
      for (int t = 0; t < threads; ++t)
      {
         workers[t] = new Thread(() ->
         {
            try
            {
               start.await();
            }
            catch (InterruptedException e)
            {
               return;
            }
            for (int i = 0; i < increments; ++i)
            {
               lock.lock();
               try
               {
                  if (inside.incrementAndGet() != 1)
                     overlap.set(true);
                  counter[0]++;
                  if ((i & 255) == 0)
                     Thread.yield(); // hold the lock a while now and then so that waiters park
                  inside.decrementAndGet();
               }
               finally
               {
                  lock.unlock();
               }
            }
         }, "lock-test-" + t);
         workers[t].start();
      }
      start.countDown();
      for (Thread worker : workers)
         worker.join();
      assertFalse(overlap.get(), "two threads held the lock at once");
      assertEquals((long) threads * increments, counter[0]);
      assertFalse(lock.isHeldByCurrentThread());
      assertTrue(lock.tryLock(), "the lock is free again");
      lock.unlock();
   }

   @Test
   public void testReentrancyAndTryLock() throws InterruptedException
   {
      AllocationFreeLock lock = new AllocationFreeLock();
      lock.lock();
      lock.lock();
      assertTrue(lock.tryLock());
      assertTrue(lock.isHeldByCurrentThread());

      AtomicBoolean otherAcquired = new AtomicBoolean(true);
      Thread other = new Thread(() -> otherAcquired.set(lock.tryLock()));
      other.start();
      other.join();
      assertFalse(otherAcquired.get(), "held by this thread");

      lock.unlock();
      lock.unlock();
      assertTrue(lock.isHeldByCurrentThread(), "still held once");
      lock.unlock();
      assertFalse(lock.isHeldByCurrentThread());

      Thread again = new Thread(() ->
      {
         otherAcquired.set(lock.tryLock());
         if (otherAcquired.get())
            lock.unlock();
      });
      again.start();
      again.join();
      assertTrue(otherAcquired.get(), "free after the last unlock");
   }

   @Test
   public void testUnlockByNonOwnerThrows() throws InterruptedException
   {
      AllocationFreeLock lock = new AllocationFreeLock();
      assertThrows(IllegalMonitorStateException.class, lock::unlock);
      lock.lock();
      AtomicBoolean threw = new AtomicBoolean();
      Thread other = new Thread(() ->
      {
         try
         {
            lock.unlock();
         }
         catch (IllegalMonitorStateException expected)
         {
            threw.set(true);
         }
      });
      other.start();
      other.join();
      assertTrue(threw.get());
      assertTrue(lock.isHeldByCurrentThread());
      lock.unlock();
   }

   /** An interrupt neither aborts lock() nor makes it spin; the interrupt status is kept. */
   @Test
   @Timeout(30)
   public void testInterruptedWaiterKeepsWaitingAndKeepsTheInterruptStatus() throws InterruptedException
   {
      AllocationFreeLock lock = new AllocationFreeLock();
      lock.lock();
      AtomicBoolean acquired = new AtomicBoolean();
      AtomicBoolean interruptedAfterward = new AtomicBoolean();
      Thread waiter = new Thread(() ->
      {
         lock.lock();
         acquired.set(true);
         interruptedAfterward.set(Thread.currentThread().isInterrupted());
         lock.unlock();
      });
      waiter.start();
      PubSubTestTools.awaitCondition(() -> waiter.getState() == Thread.State.WAITING || waiter.getState() == Thread.State.TIMED_WAITING, 10_000, "waiter parked");
      waiter.interrupt();
      Thread.sleep(50);
      assertFalse(acquired.get(), "an interrupt does not abort lock()");
      assertTrue(waiter.getState() == Thread.State.WAITING || waiter.getState() == Thread.State.TIMED_WAITING, "parked again, not spinning: " + waiter.getState());
      lock.unlock();
      waiter.join(10_000);
      assertTrue(acquired.get());
      assertTrue(interruptedAfterward.get(), "the interrupt status is restored");
   }

   /** A waiter that finds no free slot polls and still gets the lock. */
   @Test
   @Timeout(30)
   public void testWaitersBeyondTheSlotsGetTheLock() throws InterruptedException
   {
      AllocationFreeLock lock = new AllocationFreeLock();
      lock.lock();
      int waiters = AllocationFreeLock.WAITER_SLOTS + 4;
      CountDownLatch done = new CountDownLatch(waiters);
      for (int w = 0; w < waiters; ++w)
      {
         Thread thread = new Thread(() ->
         {
            lock.lock();
            lock.unlock();
            done.countDown();
         });
         thread.setDaemon(true);
         thread.start();
      }
      Thread.sleep(100);
      assertEquals(waiters, done.getCount(), "all wait while the lock is held");
      lock.unlock();
      assertTrue(done.await(20, TimeUnit.SECONDS), "every waiter got the lock");
   }
}
