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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every bundled message type (enumerated from src/main/java-interfaces): steady-state serialize, deserialize (little and
 * big endian, reused instance, sizes equal to or below the high-water mark, alternating), set, calculateSizeBytes allocate
 * nothing. See {@link MessageAllocationHarness}.
 */
@Tag("allocation")
public class BundledMessagesAllocationTest
{
   private static CallingThreadAllocationGate gate;
   private static final AtomicInteger tested = new AtomicInteger();
   private static final AtomicInteger testedAfterAscii = new AtomicInteger();

   /** The sampler is registered only while a test runs (never between tests). */
   @BeforeEach
   public void installGate()
   {
      gate = CallingThreadAllocationGate.create();
   }

   @AfterEach
   public void removeGate()
   {
      gate.close();
      gate = null;
   }

   @SuppressWarnings("rawtypes")
   @TestFactory
   public List<DynamicTest> testEveryBundledMessageType()
   {
      List<Class<? extends ROS2Message>> types = BundledMessageCatalog.bundledMessageClasses();
      List<DynamicTest> tests = new ArrayList<>();
      for (Class<? extends ROS2Message> type : types)
      {
         tests.add(DynamicTest.dynamicTest(type.getName(), () ->
         {
            long seed = type.getName().hashCode();
            MessageFiller filler = MessageFiller.standard(seed);
            MessageAllocationHarness.assertSteadyStateAllocationFree(gate, type, filler::fillHighWaterMark, filler::fillRandom);
            tested.incrementAndGet();
         }));
      }
      // The count check runs last: every enumerated type must have been tested (none skipped)
      int expected = types.size();
      tests.add(DynamicTest.dynamicTest("all " + expected + " bundled types tested", () -> assertEquals(expected, tested.get())));
      return tests;
   }

   /**
    * Every bundled type: strings with non-Latin-1 characters received (and copied) after an ASCII-only warm-up allocate
    * nothing. See {@link MessageAllocationHarness#assertNonLatin1AfterAsciiAllocationFree}.
    */
   @SuppressWarnings("rawtypes")
   @TestFactory
   public List<DynamicTest> testEveryBundledMessageTypeNonLatin1AfterAscii()
   {
      List<Class<? extends ROS2Message>> types = BundledMessageCatalog.bundledMessageClasses();
      List<DynamicTest> tests = new ArrayList<>();
      for (Class<? extends ROS2Message> type : types)
      {
         tests.add(DynamicTest.dynamicTest(type.getName(), () ->
         {
            long seed = 7L * type.getName().hashCode();
            MessageAllocationHarness.assertNonLatin1AfterAsciiAllocationFree(gate,
                                                                           type,
                                                                           MessageFiller.standard(seed)::fillHighWaterMarkAscii,
                                                                           MessageFiller.standard(seed)::fillHighWaterMark);
            testedAfterAscii.incrementAndGet();
         }));
      }
      int expected = types.size();
      tests.add(DynamicTest.dynamicTest("all " + expected + " bundled types tested", () -> assertEquals(expected, testedAfterAscii.get())));
      return tests;
   }

   @Test
   public void testLargeSensorMessages()
   {
      // Payloads that Fast-DDS fragments: 1-4 MB images and point clouds; smaller frames after the largest one.
      int[] imageSizes = {4 << 20, 1 << 20, 3 << 20, 2 << 20, 640 * 480 * 3};
      MessageAllocationHarness.assertSteadyStateAllocationFree(gate, sensor_msgs.Image.class, message -> fillImage((sensor_msgs.Image) message, imageSizes[0], 0),
                                                               new java.util.function.Consumer<>()
                                                               {
                                                                  int next = 1;

                                                                  @Override
                                                                  public void accept(ROS2Message message)
                                                                  {
                                                                     int i = next++;
                                                                     fillImage((sensor_msgs.Image) message, imageSizes[i], i);
                                                                  }
                                                               });

      MessageAllocationHarness.assertSteadyStateAllocationFree(gate, sensor_msgs.PointCloud2.class, message -> fillCloud((sensor_msgs.PointCloud2) message, 1 << 20, 0),
                                                               new java.util.function.Consumer<>()
                                                               {
                                                                  int next = 1;

                                                                  @Override
                                                                  public void accept(ROS2Message message)
                                                                  {
                                                                     int i = next++;
                                                                     fillCloud((sensor_msgs.PointCloud2) message, (1 << 20) >> i, i);
                                                                  }
                                                               });
   }

   static void fillImage(sensor_msgs.Image image, int sizeBytes, int seed)
   {
      image.getHeader().setFrameId("camera_€_" + seed);
      image.getHeader().getStamp().setSec(seed);
      image.setHeight(480);
      image.setWidth(sizeBytes / 480 / 3);
      image.setEncoding("rgb8");
      image.setStep(image.getWidth() * 3);
      image.getData().clear();
      image.getData().ensureMinCapacity(sizeBytes);
      for (int i = 0; i < sizeBytes; i++)
         image.getData().add((byte) (i * 31 + seed));
   }

   static void fillCloud(sensor_msgs.PointCloud2 cloud, int sizeBytes, int seed)
   {
      cloud.getHeader().setFrameId("lidar_" + seed);
      cloud.getFields().clear();
      String[] names = {"x", "y", "z", "intensity"};
      for (int i = 0; i < names.length - (seed % 2); i++)
      {
         sensor_msgs.PointField field = cloud.getFields().add();
         field.setName(names[i]);
         field.setOffset(4 * i);
         field.setDatatype(sensor_msgs.PointField.FLOAT32);
         field.setCount(1);
      }
      cloud.setPointStep(16);
      cloud.setWidth(sizeBytes / 16);
      cloud.setHeight(1);
      cloud.setRowStep(sizeBytes);
      cloud.setIsDense(seed % 2 == 0);
      cloud.getData().clear();
      for (int i = 0; i < sizeBytes; i++)
         cloud.getData().add((byte) (i * 17 + seed));
   }
}
