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
package us.ihmc.jros2.allocation.messages;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Self-test of the allocation gates for what the java-allocation-instrumenter agent cannot see.
 * <p>
 * The agent rewrites the bytecode of the classes it can transform. It cannot transform hidden classes, so the instances
 * of capturing lambdas and method references (created by classes that LambdaMetafactory generates) are never reported to
 * {@link SingleThreadAllocationGate}; neither are arrays created by {@code Unsafe.allocateUninitializedArray} (the
 * {@code byte[]} of a string concatenation) or objects the JVM creates itself. Verifier mutations showed it: a capturing
 * lambda added to CDRBuffer.writeString, or to the object-sequence copy of the template's set(), passed every gate of the
 * agent JVM. The message and CDR gates catch such allocations because {@link HotSpotAllocationTest} runs them again in
 * JVMs without the agent, where {@link ThreadAllocatedBytesGate} checks HotSpot's per-thread allocated-bytes counter, also
 * without escape analysis and with C1 only (so that the result does not depend on escape analysis).
 * </p>
 * <p>
 * This test proves, in every JVM that runs the gates (the agent JVM and each probe JVM of {@link HotSpotAllocationTest}),
 * that the counter-based gate sees these allocations in steady state, after the code has been compiled.
 * </p>
 */
@Tag("allocation")
public class HotSpotCounterSelfTest
{
   private static final int CALLS = 100;
   private static final int WARM_UP_CALLS = 30_000;

   private static volatile Object escape;
   private static volatile long sink;

   private static final StringBuilder captured = new StringBuilder("captured");
   private static final int[] value = {12345};

   /** Creates a capturing lambda (a new object of a hidden class) per call; it escapes, so escape analysis cannot remove it. */
   private static void capturingLambdas()
   {
      for (int i = 0; i < CALLS; i++)
      {
         StringBuilder builder = captured;
         int offset = value[0] + i;
         IntSupplier supplier = () -> builder.length() + offset;
         escape = supplier;
      }
   }

   /** String concatenation: its byte[] is allocated with Unsafe.allocateUninitializedArray. */
   private static void concatenations()
   {
      for (int i = 0; i < CALLS; i++)
      {
         escape = captured + "_" + (value[0] + i);
      }
   }

   private static long measure(CallingThreadAllocationGate gate, Runnable body)
   {
      for (int i = 0; i < WARM_UP_CALLS / CALLS; i++)
         body.run();
      long allocated = gate.countAllocations(body);
      sink += allocated;
      return allocated;
   }

   @Test
   public void testCounterSeesWhatTheAgentCannotSee()
   {
      ThreadAllocatedBytesGate counter = new ThreadAllocatedBytesGate();
      Runnable lambdas = HotSpotCounterSelfTest::capturingLambdas;
      Runnable strings = HotSpotCounterSelfTest::concatenations;

      long lambdaBytes = measure(counter, lambdas);
      assertTrue(lambdaBytes >= CALLS * 16L, "HotSpot's counter must see " + CALLS + " capturing lambdas: " + lambdaBytes + " bytes");
      long stringBytes = measure(counter, strings);
      assertTrue(stringBytes >= CALLS * 40L, "HotSpot's counter must see " + CALLS + " string concatenations: " + stringBytes + " bytes");

      if (SingleThreadAllocationGate.Text.isAgentLoaded())
      {
         // Informational: what the agent reports for the same calls (the reason the agent-free probe JVMs exist)
         try (SingleThreadAllocationGate agent = new SingleThreadAllocationGate())
         {
            System.out.println("instrumenter: " + measure(agent, lambdas) + " allocations reported for " + CALLS + " capturing lambdas, " + measure(agent, strings)
                               + " for " + CALLS + " string concatenations; HotSpot counter: " + lambdaBytes + " and " + stringBytes + " bytes");
         }
      }
   }
}
