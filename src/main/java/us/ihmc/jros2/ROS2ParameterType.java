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

/**
 * Types of ROS 2 parameter values, with the ids of {@code rcl_interfaces/msg/ParameterType}.
 */
public enum ROS2ParameterType
{
   NOT_SET(0, "not set"),
   BOOL(1, "bool"),
   INTEGER(2, "integer"),
   DOUBLE(3, "double"),
   STRING(4, "string"),
   BYTE_ARRAY(5, "byte_array"),
   BOOL_ARRAY(6, "bool_array"),
   INTEGER_ARRAY(7, "integer_array"),
   DOUBLE_ARRAY(8, "double_array"),
   STRING_ARRAY(9, "string_array");

   private static final ROS2ParameterType[] BY_ID = values();

   private final byte id;
   private final String rosName;

   ROS2ParameterType(int id, String rosName)
   {
      this.id = (byte) id;
      this.rosName = rosName;
   }

   /**
    * @return the id used in {@code rcl_interfaces/msg/ParameterType}
    */
   public byte getId()
   {
      return id;
   }

   /**
    * @return the name rclcpp uses for this type in messages, e.g. {@code integer}
    */
   public String getRosName()
   {
      return rosName;
   }

   public static ROS2ParameterType fromId(byte id)
   {
      if (id < 0 || id >= BY_ID.length)
      {
         throw new IllegalArgumentException("Unknown parameter type id: " + id);
      }
      return BY_ID[id];
   }
}
