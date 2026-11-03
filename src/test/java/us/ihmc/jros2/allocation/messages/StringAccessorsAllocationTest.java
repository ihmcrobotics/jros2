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
import org.junit.jupiter.api.io.TempDir;
import us.ihmc.jros2.ROS2Message;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The string convenience accessors of every bundled message type (enumerated from src/main/java-interfaces):
 * {@code setX(String)} and {@code setX(CharSequence)} with ASCII, non-Latin-1 and surrogate-pair content, self-assignment,
 * and {@code getX()} (the StringBuilder) allocate nothing in steady state. {@code getXAsString()} returns a new
 * {@link String} and necessarily allocates; {@code getX()} is its allocation-free alternative.
 */
@Tag("allocation")
public class StringAccessorsAllocationTest
{
   /** Bundled types with at least one non-array string field when this gate was written; may grow, never shrink. */
   private static final int MINIMUM_TYPES_WITH_STRING_FIELDS = 42;
   /** Their non-array string fields when this gate was written; may grow, never shrink. */
   private static final int MINIMUM_STRING_FIELDS = 85;

   @TempDir
   Path tmp;

   private static volatile long sink;

   @Test
   public void testEveryBundledStringAccessor() throws Exception
   {
      List<Class<?>> types = new ArrayList<>();
      int fields = 0;
      for (Class<? extends ROS2Message> type : BundledMessageCatalog.bundledMessageClasses())
      {
         types.add(type);
         fields += StringAccessorDrivers.stringAccessors(type).size();
      }

      StringAccessorDrivers drivers = new StringAccessorDrivers(types, tmp, getClass().getClassLoader());
      try (CallingThreadAllocationGate gate = CallingThreadAllocationGate.create())
      {
         assertEquals(fields, drivers.getFieldCount(), "every string field of every bundled type is driven");
         assertTrue(drivers.getTypeCount() >= MINIMUM_TYPES_WITH_STRING_FIELDS, "only " + drivers.getTypeCount() + " bundled types with string fields");
         assertTrue(drivers.getFieldCount() >= MINIMUM_STRING_FIELDS, "only " + drivers.getFieldCount() + " string fields in the bundled types");
         assertAccessors(gate, drivers, "bundled message types");
      }
      finally
      {
         drivers.close();
      }
   }

   /**
    * Warms up the setters driver, then requires zero allocations for two more runs; checks the content and that the
    * as-string getters allocate (at least one object per call).
    */
   public static void assertAccessors(CallingThreadAllocationGate gate, StringAccessorDrivers drivers, String what) throws Exception
   {
      sink += drivers.runSetters();
      gate.assertNoAllocation("set(String), set(CharSequence), get() of the string fields of " + drivers.getTypeCount() + " " + what, () ->
      {
         sink += drivers.runSetters();
         sink += drivers.runSetters();
      });
      drivers.assertSetterResults();

      sink += drivers.runAsStringGetters();
      long allocations = gate.countAllocations(() -> sink += drivers.runAsStringGetters());
      assertTrue(allocations >= drivers.getFieldCount(), "getXAsString() returns a new String: " + allocations + " allocations for " + drivers.getFieldCount() + " calls");
   }
}
