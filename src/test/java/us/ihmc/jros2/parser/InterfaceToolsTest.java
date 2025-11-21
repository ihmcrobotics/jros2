/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
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
package us.ihmc.jros2.parser;

import org.junit.jupiter.api.Test;
import us.ihmc.jros2.parser.util.InterfaceTools;

import static org.junit.jupiter.api.Assertions.*;

public class InterfaceToolsTest
{
   @Test
   public void testCheckAndParsePackageResourceName()
   {
      String[] parsed = InterfaceTools.checkAndParsePackageResourceName("std_msgs/Empty");
      assertEquals("std_msgs", parsed[0]);
      assertEquals("Empty", parsed[1]);

      assertThrows(NullPointerException.class, () -> InterfaceTools.checkAndParsePackageResourceName(null));
      assertThrows(IllegalArgumentException.class, () -> InterfaceTools.checkAndParsePackageResourceName("std_msgs"));
      assertThrows(IllegalArgumentException.class, () -> InterfaceTools.checkAndParsePackageResourceName("Empty"));
      assertThrows(IllegalArgumentException.class, () -> InterfaceTools.checkAndParsePackageResourceName("std_msgs.Empty"));
      assertThrows(IllegalArgumentException.class, () -> InterfaceTools.checkAndParsePackageResourceName("std_msgs/msg/Empty"));
   }

   @Test
   public void testCheckSchema()
   {
      assertDoesNotThrow(() -> InterfaceTools.checkSchema("Hello"));
      assertThrows(NullPointerException.class, () -> InterfaceTools.checkSchema(null));
      assertThrows(IllegalArgumentException.class, () -> InterfaceTools.checkSchema(""));
   }
}
