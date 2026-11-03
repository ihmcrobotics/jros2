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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import us.ihmc.jros2.ROS2Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every bundled message type in a long steady state: after ONE warm-up round, a single measured window of
 * {@link #rounds()} rounds of the runtime's per-message calls (see {@link MessageAllocationHarness}). The methods of the
 * type (serialize, deserialize, set, calculateSizeBytes) are called more often inside the window than the JIT
 * compilation thresholds (C1, C2 with and without tiered compilation, on-stack replacement), so the window contains the
 * JIT transitions of the type, with whatever the JVM does on the calling thread when it requests them (resolving string
 * constants, loading signature classes, deoptimizing), and any allocation that happens only every so many calls, which
 * the two-round windows of {@link BundledMessagesAllocationTest} would not reach.
 * <p>
 * {@link HotSpotAllocationTest} runs this class first in a JVM without the agent and without the JUnit engine
 * ({@link MinimalJvmAllocationProbe}), so that the shared CDR code and the JDK methods under it are compiled inside the
 * windows too and HotSpot's per-thread counter checks every byte.
 * </p>
 */
@Tag("allocation")
public class LongSteadyStateAllocationTest
{
   /** Rounds per measured window: each round calls serialize 36 times and set/deserialize 24 times per instance type. */
   private static final int ROUNDS = 700;
   /** In the interpreter nothing is ever compiled: a short window suffices there. */
   private static final int INTERPRETER_ROUNDS = 3;

   private static final AtomicInteger tested = new AtomicInteger();
   private CallingThreadAllocationGate gate;

   @BeforeEach
   public void installGate()
   {
      gate = CallingThreadAllocationGate.create();
   }

   @AfterEach
   public void removeGate()
   {
      gate.close();
   }

   static int rounds()
   {
      String vmInfo = System.getProperty("java.vm.info", "");
      return vmInfo.contains("interpreted mode") ? INTERPRETER_ROUNDS : ROUNDS;
   }

   @SuppressWarnings("rawtypes")
   @TestFactory
   public List<DynamicTest> testEveryBundledMessageTypeInALongSteadyState()
   {
      List<Class<? extends ROS2Message>> types = BundledMessageCatalog.bundledMessageClasses();
      List<DynamicTest> tests = new ArrayList<>();
      for (Class<? extends ROS2Message> type : types)
      {
         tests.add(DynamicTest.dynamicTest(type.getName(), () ->
         {
            MessageFiller filler = MessageFiller.standard(13L * type.getName().hashCode());
            MessageAllocationHarness.assertLongSteadyStateAllocationFree(gate, type, filler::fillHighWaterMark, filler::fillRandom, rounds());
            tested.incrementAndGet();
         }));
      }
      int expected = types.size();
      tests.add(DynamicTest.dynamicTest("all " + expected + " bundled types tested", () -> assertEquals(expected, tested.get())));
      return tests;
   }

   /** Images and point clouds of different sizes (the bulk byte sequence path), smaller frames after the largest one. */
   @Test
   public void testSensorMessagesInALongSteadyState()
   {
      int[] imageSizes = {96 * 1024, 48 * 1024, 72 * 1024, 64 * 1024, 30 * 1024};
      MessageAllocationHarness.assertLongSteadyStateAllocationFree(gate,
                                                                   sensor_msgs.Image.class,
                                                                   message -> BundledMessagesAllocationTest.fillImage((sensor_msgs.Image) message,
                                                                                                                      imageSizes[0],
                                                                                                                      0),
                                                                   sequence(imageSizes, (message, size, i) -> BundledMessagesAllocationTest.fillImage(
                                                                         (sensor_msgs.Image) message,
                                                                         size,
                                                                         i)),
                                                                   rounds() / 7 + 1);
      int[] cloudSizes = {64 * 1024, 32 * 1024, 16 * 1024, 48 * 1024, 8 * 1024};
      MessageAllocationHarness.assertLongSteadyStateAllocationFree(gate,
                                                                   sensor_msgs.PointCloud2.class,
                                                                   message -> BundledMessagesAllocationTest.fillCloud((sensor_msgs.PointCloud2) message,
                                                                                                                      cloudSizes[0],
                                                                                                                      0),
                                                                   sequence(cloudSizes, (message, size, i) -> BundledMessagesAllocationTest.fillCloud(
                                                                         (sensor_msgs.PointCloud2) message,
                                                                         size,
                                                                         i)),
                                                                   rounds() / 7 + 1);
   }

   private interface SizedFiller
   {
      void fill(ROS2Message<?> message, int sizeBytes, int index);
   }

   /** A filler for the instances 1, 2, ... of the harness, with the sizes 1, 2, ... of {@code sizes}. */
   @SuppressWarnings("rawtypes")
   private static Consumer<ROS2Message> sequence(int[] sizes, SizedFiller filler)
   {
      return new Consumer<>()
      {
         private int next = 1;

         @Override
         public void accept(ROS2Message message)
         {
            int i = next++;
            filler.fill(message, sizes[i], i);
         }
      };
   }
}
