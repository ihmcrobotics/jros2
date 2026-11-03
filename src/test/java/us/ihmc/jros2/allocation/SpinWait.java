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
package us.ihmc.jros2.allocation;

import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * Allocation-free waiting for use inside allocation gate windows.
 * <p>
 * This class must not contain string constants. When HotSpot first queues a method of a class for C2 compilation
 * (including an on-stack replacement of a long spin loop), it resolves every string constant of that class on the
 * requesting thread, which allocates the strings there. A waiting loop in a class with string constants (error
 * messages) therefore allocates once, whenever the JIT gets to it, possibly inside a window. Failures are reported by
 * {@link AllocationGate}.
 */
public final class SpinWait
{
   private static final int SPINS = 2_000;
   private static final long PARK_NANOS = 20_000L;

   private SpinWait()
   {
   }

   /**
    * Waits until {@code condition} holds: spins, then parks for 20 µs at a time. Allocation-free while waiting.
    * {@code condition} must be created outside the window (a capturing lambda allocates where it is created).
    *
    * @throws IllegalStateException on timeout (allocates, but then the test fails anyway)
    */
   public static void await(BooleanSupplier condition, long timeoutMillis, String what)
   {
      if (!until(condition, timeoutMillis * 1_000_000L))
      {
         AllocationGate.timedOut(timeoutMillis, what);
      }
   }

   /**
    * @return true if {@code condition} held before {@code timeoutNanos} elapsed
    */
   public static boolean until(BooleanSupplier condition, long timeoutNanos)
   {
      if (condition.getAsBoolean())
      {
         return true;
      }
      long deadline = System.nanoTime() + timeoutNanos;
      int spins = 0;
      while (!condition.getAsBoolean())
      {
         if (++spins < SPINS)
         {
            Thread.onSpinWait();
         }
         else
         {
            LockSupport.parkNanos(PARK_NANOS);
            if (System.nanoTime() - deadline > 0)
            {
               return condition.getAsBoolean();
            }
         }
      }
      return true;
   }
}
