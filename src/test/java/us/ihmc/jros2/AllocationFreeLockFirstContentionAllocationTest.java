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
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The FIRST time in the JVM that a thread has to wait for an {@link AllocationFreeLock} (register in a waiter slot,
 * park, be unparked) allocates nothing, on the waiting thread and on the releasing one. Contention can first happen long
 * after warm-up, so the code of the contended path is linked when the class is initialized: without that, the first use
 * of each access mode of AtomicReferenceArray's VarHandle allocates (seen as MethodType, MemberName and
 * VarHandle$AccessDescriptor allocations on a thread reading statistics while the publish thread recorded them). This
 * class must run in a JVM of its own (the allocation test tasks fork one per class) and must not warm the contended path
 * up: the first gate window is the first contention.
 */
@Tag("allocation")
public class AllocationFreeLockFirstContentionAllocationTest
{
   @Test
   public void testFirstContentionIsAllocationFree() throws InterruptedException
   {
      AllocationFreeLock lock = new AllocationFreeLock();
      AtomicInteger round = new AtomicInteger();
      AtomicInteger acquired = new AtomicInteger();
      AtomicInteger stop = new AtomicInteger();
      Thread contender = new Thread(() ->
      {
         int handled = 0;
         BooleanSupplier next = () -> round.get() != acquired.get() || stop.get() != 0;
         while (stop.get() == 0)
         {
            if (round.get() != handled)
            {
               handled++;
               lock.lock();
               lock.unlock();
               acquired.incrementAndGet();
            }
            else
            {
               SpinWait.until(next, 1_000_000L);
            }
         }
      }, "first-contender");
      contender.setDaemon(true);
      contender.start();
      try
      {
         // Uncontended rounds before the windows: the contender's own code runs once, the contended path does not.
         int[] expected = {0};
         BooleanSupplier contenderDone = () -> acquired.get() == expected[0];
         for (int i = 0; i < 3; ++i)
         {
            expected[0]++;
            round.incrementAndGet();
            SpinWait.await(contenderDone, 5_000, null);
         }
         assertEquals(0, lock.getWaitingThreadCount());

         BooleanSupplier contenderWaits = () -> lock.getWaitingThreadCount() > 0;
         BooleanSupplier never = () -> false;
         AllocationGate.Result result = AllocationGate.named("first contention of an AllocationFreeLock").warmup(0).iterations(1).run(i ->
         {
            lock.lock();
            expected[0]++;
            round.incrementAndGet();
            SpinWait.await(contenderWaits, 5_000, null);
            // Let the contender spin out and park.
            SpinWait.until(never, 2_000_000L);
            lock.unlock();
            SpinWait.await(contenderDone, 5_000, null);
         });
         result.assertAllocationFree();
         assertTrue(result.hotPathThreadNames().contains("first-contender"), result.report());
      }
      finally
      {
         stop.set(1);
         contender.join(5_000);
      }
   }
}
