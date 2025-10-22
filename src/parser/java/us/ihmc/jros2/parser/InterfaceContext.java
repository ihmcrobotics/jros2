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

import us.ihmc.jros2.parser.field.InterfaceField;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Representation of a ROS 2 interface. Possible implementations:
 * <ul>
 *   <li>ROS 2 msg (.msg)</li>
 *   <li>ROS 2 service (.srv)</li>
 *   <li>ROS 2 action (.action)</li>
 * </ul>
 */
public abstract class InterfaceContext
{
   private final String schema;
   private final InterfaceMeta meta;
   private final Map<String, InterfaceField> fields;

   public InterfaceContext(String schema)
   {
      this.schema = schema;
      meta = new InterfaceMeta();
      fields = new LinkedHashMap<>();
   }

   public String getSchema()
   {
      return schema;
   }

   public InterfaceMeta getMeta()
   {
      return meta;
   }

   public Map<String, InterfaceField> getFields()
   {
      return fields;
   }
}
