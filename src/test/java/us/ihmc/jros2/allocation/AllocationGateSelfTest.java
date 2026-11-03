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

import com.google.monitoring.runtime.instrumentation.AllocationRecorder;
import com.google.monitoring.runtime.instrumentation.Sampler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Proves the allocation gate itself works, so an allocation-free result from the other "allocation" tests means something:
 * the instrumenter agent is loaded, it reports an allocation that escape analysis removes once C2 compiles the loop, and
 * it reports allocations made on other threads.
 */
@Tag("allocation")
public class AllocationGateSelfTest
{
   private static final class Point
   {
      final double x, y;

      Point(double x, double y)
      {
         this.x = x;
         this.y = y;
      }
   }

   private static volatile double sink;

   /** These checks are about the agent: they run in allocationTest, not in the agent-less allocationTestNoEA JVM. */
   @BeforeEach
   public void requireAgentJvm()
   {
      assumeFalse("noEA".equals(System.getProperty(AllocationGate.MODE_PROPERTY)), "agent checks run in the allocationTest JVM");
   }

   /** Allocates a Point that never escapes: C2 scalar-replaces it, so without the agent no heap allocation remains. */
   private static double nonEscaping(int i)
   {
      Point p = new Point(i, i + 1);
      return p.x + p.y;
   }

   @Test
   public void testAgentIsLoaded()
   {
      boolean agentLoaded = ManagementFactory.getRuntimeMXBean()
                                             .getInputArguments()
                                             .stream()
                                             .anyMatch(argument -> argument.startsWith("-javaagent:") && argument.contains("java-allocation-instrumenter"));
      assertTrue(agentLoaded, "allocation tests must run in the allocationTest task (java-allocation-instrumenter agent)");
   }

   @Test
   public void testEscapeAnalyzedAllocationIsStillReported()
   {
      AtomicLong points = new AtomicLong();
      Sampler sampler = (count, desc, newObj, size) ->
      {
         if (newObj instanceof Point)
            points.incrementAndGet();
      };

      // Warm up far past the C2 compile threshold so escape analysis would have removed the allocation.
      for (int i = 0; i < 200_000; ++i)
         sink += nonEscaping(i);

      AllocationRecorder.addSampler(sampler);
      try
      {
         for (int i = 0; i < 100_000; ++i)
            sink += nonEscaping(i);
      }
      finally
      {
         AllocationRecorder.removeSampler(sampler);
      }

      assertEquals(100_000, points.get(), "every Point allocation must be reported, independent of escape analysis");
   }

   @Test
   public void testAllocationOnAnotherThreadIsReported() throws InterruptedException
   {
      AtomicLong points = new AtomicLong();
      Sampler sampler = (count, desc, newObj, size) ->
      {
         if (newObj instanceof Point)
            points.incrementAndGet();
      };

      AllocationRecorder.addSampler(sampler);
      try
      {
         Thread other = new Thread(() ->
         {
            for (int i = 0; i < 1_000; ++i)
               sink += nonEscaping(i);
         });
         other.start();
         other.join();
      }
      finally
      {
         AllocationRecorder.removeSampler(sampler);
      }

      assertEquals(1_000, points.get());
   }
}
