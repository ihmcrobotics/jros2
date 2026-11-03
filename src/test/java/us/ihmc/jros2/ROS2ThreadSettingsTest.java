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
import us.ihmc.fastddsjava.profiles.gen.ThreadSettingsType;
import us.ihmc.jros2.ROS2ThreadSettings.Kind;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validation, overrides and Fast-DDS mapping of {@link ROS2ThreadSettings}.
 */
public class ROS2ThreadSettingsTest
{
   @Test
   public void testDefaultsAreUnset()
   {
      ROS2ThreadSettings settings = new ROS2ThreadSettings();
      assertTrue(settings.isUnset());
      assertEquals(ROS2ThreadSettings.UNSET, settings.getSchedulingPolicy());
      assertEquals(ROS2ThreadSettings.UNSET, settings.getPriority());
      assertEquals(0L, settings.getAffinityMask());
      assertEquals(ROS2ThreadSettings.UNSET, settings.getStackSizeBytes());
      for (Kind kind : Kind.values())
      {
         assertNull(settings.toThreadSettingsType(kind));
         assertNull(settings.getOverride(kind));
      }
      assertEquals("ROS2ThreadSettings{default}", settings.toString());
   }

   @Test
   public void testStackSizeValidation()
   {
      ROS2ThreadSettings settings = new ROS2ThreadSettings();
      // Below PTHREAD_STACK_MIN of glibc aarch64 (131072) Fast-DDS aborts the JVM, so these are rejected
      for (int invalid : new int[] {0, 1000, 16383, 16384, 65536, ROS2ThreadSettings.MIN_STACK_SIZE_BYTES - 1, -2, Integer.MIN_VALUE + 1})
      {
         assertThrows(IllegalArgumentException.class, () -> settings.stackSizeBytes(invalid), Integer.toString(invalid));
      }
      // macOS refuses stacks that are not page aligned (16 KiB pages on Apple silicon); Fast-DDS would abort the JVM there
      for (int unaligned : new int[] {ROS2ThreadSettings.MIN_STACK_SIZE_BYTES + 1, 200_000, 1_000_000, 1048576 + 4096, Integer.MAX_VALUE})
      {
         assertThrows(IllegalArgumentException.class, () -> settings.stackSizeBytes(unaligned), Integer.toString(unaligned));
      }
      assertEquals(ROS2ThreadSettings.UNSET, settings.getStackSizeBytes(), "a rejected value must not be stored");

      settings.stackSizeBytes(ROS2ThreadSettings.MIN_STACK_SIZE_BYTES);
      assertEquals(131072, settings.getStackSizeBytes());
      settings.stackSizeBytes(-1);
      assertEquals(-1, settings.getStackSizeBytes());
      settings.stackSizeBytes(ROS2ThreadSettings.MIN_STACK_SIZE_BYTES + ROS2ThreadSettings.STACK_SIZE_ALIGNMENT_BYTES);
      settings.stackSizeBytes(Integer.MAX_VALUE - (Integer.MAX_VALUE % ROS2ThreadSettings.STACK_SIZE_ALIGNMENT_BYTES));
      assertEquals(2147467264, settings.getStackSizeBytes());
      settings.stackSizeBytes(ROS2ThreadSettings.UNSET);
      assertTrue(settings.isUnset());
   }

   @Test
   public void testSchedulingPolicyValidation()
   {
      ROS2ThreadSettings settings = new ROS2ThreadSettings();
      assertThrows(IllegalArgumentException.class, () -> settings.schedulingPolicy(-2));
      assertThrows(IllegalArgumentException.class, () -> settings.schedulingPolicy(-100));
      settings.schedulingPolicy(-1);
      settings.schedulingPolicy(0);
      settings.schedulingPolicy(1);
      settings.schedulingPolicy(5);
      settings.schedulingPolicy(ROS2ThreadSettings.UNSET);
      // Priority and affinity accept any value (failures to apply them are only logged by Fast-DDS)
      settings.priority(-20).priority(99).affinityMask(-1L);
      assertEquals(-1L, settings.getAffinityMask());
   }

   @Test
   public void testMappingToFastDDS()
   {
      ROS2ThreadSettings settings = new ROS2ThreadSettings().schedulingPolicy(1).priority(30).affinityMask(0xCL).stackSizeBytes(1048576);
      ThreadSettingsType threadSettings = settings.toThreadSettingsType(Kind.TIMED_EVENTS);
      assertEquals(1, threadSettings.getSchedulingPolicy());
      assertEquals(30, threadSettings.getPriority());
      assertEquals(BigInteger.valueOf(12), threadSettings.getAffinity());
      assertEquals(1048576, threadSettings.getStackSize());

      // All 64 bits of the affinity mask are an unsigned uint64
      ThreadSettingsType allCores = new ROS2ThreadSettings().affinityMask(-1L).toThreadSettingsType(Kind.RECEPTION);
      assertEquals(new BigInteger("18446744073709551615"), allCores.getAffinity());
      assertNull(allCores.getSchedulingPolicy());
      assertNull(allCores.getPriority());
      assertNull(allCores.getStackSize());
   }

   @Test
   public void testOverridesFallBackFieldByField()
   {
      ROS2ThreadSettings settings = new ROS2ThreadSettings().priority(10).affinityMask(0b11).stackSizeBytes(262144);
      ROS2ThreadSettings reception = new ROS2ThreadSettings().schedulingPolicy(1).priority(50);
      settings.forThreads(Kind.RECEPTION, reception);

      ROS2ThreadSettings effectiveReception = settings.getEffective(Kind.RECEPTION);
      assertEquals(1, effectiveReception.getSchedulingPolicy());
      assertEquals(50, effectiveReception.getPriority());
      assertEquals(0b11, effectiveReception.getAffinityMask());
      assertEquals(262144, effectiveReception.getStackSizeBytes());

      ROS2ThreadSettings effectiveEvents = settings.getEffective(Kind.TIMED_EVENTS);
      assertEquals(ROS2ThreadSettings.UNSET, effectiveEvents.getSchedulingPolicy());
      assertEquals(10, effectiveEvents.getPriority());

      // The override was copied
      reception.priority(1);
      assertEquals(50, settings.getEffective(Kind.RECEPTION).getPriority());

      // Only an override: other kinds stay unset
      ROS2ThreadSettings onlyReception = new ROS2ThreadSettings().forThreads(Kind.RECEPTION, new ROS2ThreadSettings().affinityMask(4));
      assertNotNull(onlyReception.toThreadSettingsType(Kind.RECEPTION));
      assertNull(onlyReception.toThreadSettingsType(Kind.TIMED_EVENTS));
      assertTrue(onlyReception.isUnset());

      // Removing an override
      settings.forThreads(Kind.RECEPTION, null);
      assertNull(settings.getOverride(Kind.RECEPTION));
      assertThrows(IllegalArgumentException.class, () -> settings.forThreads(null, reception));
   }

   @Test
   public void testCopyConstructorAndToString()
   {
      ROS2ThreadSettings settings = new ROS2ThreadSettings().priority(3).forThreads(Kind.RECEPTION, new ROS2ThreadSettings().stackSizeBytes(-1));
      ROS2ThreadSettings copy = new ROS2ThreadSettings(settings);
      settings.priority(4);
      settings.forThreads(Kind.RECEPTION, new ROS2ThreadSettings().stackSizeBytes(131072));
      assertEquals(3, copy.getPriority());
      assertEquals(-1, copy.getOverride(Kind.RECEPTION).getStackSizeBytes());
      assertEquals("ROS2ThreadSettings{priority=3, RECEPTION: stack=-1}", copy.toString());
      assertEquals("ROS2ThreadSettings{policy=1 priority=2 affinity=0xf0 stack=131072}",
                   new ROS2ThreadSettings().schedulingPolicy(1).priority(2).affinityMask(0xf0).stackSizeBytes(131072).toString());
   }
}
