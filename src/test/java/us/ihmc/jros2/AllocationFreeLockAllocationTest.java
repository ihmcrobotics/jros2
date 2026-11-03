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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import us.ihmc.jros2.allocation.AllocationGate;
import us.ihmc.jros2.allocation.SpinWait;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AllocationFreeLock} under heavy contention (more threads than waiter slots, waiters parking and being unparked,
 * reentry) allocates nothing on any thread and never inflates a monitor; the JDK alternatives it replaces do allocate,
 * which the same gate reports (mutation checks of the contention gates).
 */
@Tag("allocation")
public class AllocationFreeLockAllocationTest
{
   private static final int THREADS = AllocationFreeLock.WAITER_SLOTS + 4;

   interface CriticalSection
   {
      void run(long[] counter);
   }

   /**
    * Runs {@code THREADS} threads that enter {@code section} {@code perRound} times per round; the body starts a round
    * and waits for it.
    */
   private static AllocationGate.Result contend(String name, CriticalSection section) throws InterruptedException
   {
      AtomicInteger round = new AtomicInteger();
      AtomicLong done = new AtomicLong();
      AtomicInteger stop = new AtomicInteger();
      long[] counter = new long[1];
      Thread[] threads = new Thread[THREADS];
      for (int t = 0; t < THREADS; ++t)
      {
         threads[t] = new Thread(() ->
         {
            int handled = 0;
            BooleanSupplier next = () -> round.get() != 0 || stop.get() != 0;
            while (stop.get() == 0)
            {
               int current = round.get();
               if (current != handled)
               {
                  handled = current;
                  for (int k = 0; k < 5; ++k)
                     section.run(counter);
                  done.incrementAndGet();
               }
               else
               {
                  SpinWait.until(next, 1_000_000L);
               }
            }
         }, "lock-contender-" + t);
         threads[t].setDaemon(true);
         threads[t].start();
      }
      try
      {
         long[] expected = new long[1];
         BooleanSupplier roundDone = () -> done.get() >= expected[0];
         return AllocationGate.named(name).warmup(1_000).iterations(300).run(i ->
         {
            expected[0] += THREADS;
            round.set(i + 1);
            SpinWait.await(roundDone, 10_000, "round");
         });
      }
      finally
      {
         stop.set(1);
         for (Thread thread : threads)
            thread.join(5_000);
      }
   }

   @Test
   public void testContendedLockIsAllocationFree() throws InterruptedException
   {
      AllocationFreeLock lock = new AllocationFreeLock();
      AllocationGate.Result result = contend("contended AllocationFreeLock", counter ->
      {
         lock.lock();
         try
         {
            lock.lock(); // reentry
            try
            {
               counter[0]++;
               Thread.onSpinWait();
            }
            finally
            {
               lock.unlock();
            }
         }
         finally
         {
            lock.unlock();
         }
      });
      result.assertAllocationFree();
      assertTrue(result.hotPathThreadNames().contains("lock-contender-0"), result.report());
   }

   /** Mutation check: the same contention on a monitor inflates it (a native malloc), which the monitor pass reports. */
   @Test
   public void testContendedMonitorIsCaught() throws InterruptedException
   {
      Object monitor = new Object();
      AllocationGate.Result result = contend("contended synchronized", counter ->
      {
         synchronized (monitor)
         {
            counter[0]++;
            Thread.onSpinWait();
         }
      });
      assertFalse(result.isAllocationFree(), result.report());
      assertTrue(result.monitorPass.events > 0, result.report());
   }

   /** Mutation check: the same contention on a ReentrantLock allocates queue nodes on the Java heap. */
   @Test
   public void testContendedReentrantLockIsCaught() throws InterruptedException
   {
      ReentrantLock lock = new ReentrantLock();
      AllocationGate.Result result = contend("contended ReentrantLock", counter ->
      {
         lock.lock();
         try
         {
            counter[0]++;
            Thread.onSpinWait();
         }
         finally
         {
            lock.unlock();
         }
      });
      assertFalse(result.isAllocationFree(), result.report());
      assertTrue(result.threadBytesPass.hotPathBytes > 0, result.report());
   }
}
