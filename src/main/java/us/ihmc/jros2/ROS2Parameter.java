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
package us.ihmc.jros2;

import java.util.Objects;

/**
 * Represents a ROS 2 parameter with a name, value, and type.
 * ROS 2 parameters are configuration values that can be set and retrieved at runtime.
 * They support various types: bool, int, double, string, byte[], bool[], int[], double[], string[].
 */
public class ROS2Parameter
{
   /**
    * Enumeration of supported parameter types in ROS 2.
    */
   public enum ParameterType
   {
      PARAMETER_NOT_SET,
      PARAMETER_BOOL,
      PARAMETER_INTEGER,
      PARAMETER_DOUBLE,
      PARAMETER_STRING,
      PARAMETER_BYTE_ARRAY,
      PARAMETER_BOOL_ARRAY,
      PARAMETER_INTEGER_ARRAY,
      PARAMETER_DOUBLE_ARRAY,
      PARAMETER_STRING_ARRAY
   }

   private final String name;
   private Object value;
   private ParameterType type;

   /**
    * Create a parameter with a name but no value (NOT_SET).
    *
    * @param name The parameter name
    */
   public ROS2Parameter(String name)
   {
      this.name = name;
      this.type = ParameterType.PARAMETER_NOT_SET;
      this.value = null;
   }

   /**
    * Create a boolean parameter.
    *
    * @param name  The parameter name
    * @param value The boolean value
    */
   public ROS2Parameter(String name, boolean value)
   {
      this.name = name;
      this.value = value;
      this.type = ParameterType.PARAMETER_BOOL;
   }

   /**
    * Create an integer parameter.
    *
    * @param name  The parameter name
    * @param value The integer value
    */
   public ROS2Parameter(String name, long value)
   {
      this.name = name;
      this.value = value;
      this.type = ParameterType.PARAMETER_INTEGER;
   }

   /**
    * Create a double parameter.
    *
    * @param name  The parameter name
    * @param value The double value
    */
   public ROS2Parameter(String name, double value)
   {
      this.name = name;
      this.value = value;
      this.type = ParameterType.PARAMETER_DOUBLE;
   }

   /**
    * Create a string parameter.
    *
    * @param name  The parameter name
    * @param value The string value
    */
   public ROS2Parameter(String name, String value)
   {
      this.name = name;
      this.value = value;
      this.type = ParameterType.PARAMETER_STRING;
   }

   /**
    * Create a byte array parameter.
    *
    * @param name  The parameter name
    * @param value The byte array value
    */
   public ROS2Parameter(String name, byte[] value)
   {
      this.name = name;
      this.value = value;
      this.type = ParameterType.PARAMETER_BYTE_ARRAY;
   }

   /**
    * Create a boolean array parameter.
    *
    * @param name  The parameter name
    * @param value The boolean array value
    */
   public ROS2Parameter(String name, boolean[] value)
   {
      this.name = name;
      this.value = value;
      this.type = ParameterType.PARAMETER_BOOL_ARRAY;
   }

   /**
    * Create an integer array parameter.
    *
    * @param name  The parameter name
    * @param value The integer array value
    */
   public ROS2Parameter(String name, long[] value)
   {
      this.name = name;
      this.value = value;
      this.type = ParameterType.PARAMETER_INTEGER_ARRAY;
   }

   /**
    * Create a double array parameter.
    *
    * @param name  The parameter name
    * @param value The double array value
    */
   public ROS2Parameter(String name, double[] value)
   {
      this.name = name;
      this.value = value;
      this.type = ParameterType.PARAMETER_DOUBLE_ARRAY;
   }

   /**
    * Create a string array parameter.
    *
    * @param name  The parameter name
    * @param value The string array value
    */
   public ROS2Parameter(String name, String[] value)
   {
      this.name = name;
      this.value = value;
      this.type = ParameterType.PARAMETER_STRING_ARRAY;
   }

   /**
    * Get the parameter name.
    *
    * @return The parameter name
    */
   public String getName()
   {
      return name;
   }

   /**
    * Get the parameter value.
    *
    * @return The parameter value (type depends on parameter type)
    */
   public Object getValue()
   {
      return value;
   }

   /**
    * Get the parameter type.
    *
    * @return The parameter type
    */
   public ParameterType getType()
   {
      return type;
   }

   /**
    * Get the value as a boolean.
    *
    * @return The boolean value
    * @throws ClassCastException if the parameter is not a boolean
    */
   public boolean asBool()
   {
      return (Boolean) value;
   }

   /**
    * Get the value as a long (integer).
    *
    * @return The integer value
    * @throws ClassCastException if the parameter is not an integer
    */
   public long asLong()
   {
      return (Long) value;
   }

   /**
    * Get the value as a double.
    *
    * @return The double value
    * @throws ClassCastException if the parameter is not a double
    */
   public double asDouble()
   {
      return (Double) value;
   }

   /**
    * Get the value as a string.
    *
    * @return The string value
    * @throws ClassCastException if the parameter is not a string
    */
   public String asString()
   {
      return (String) value;
   }

   /**
    * Get the value as a byte array.
    *
    * @return The byte array value
    * @throws ClassCastException if the parameter is not a byte array
    */
   public byte[] asByteArray()
   {
      return (byte[]) value;
   }

   /**
    * Get the value as a boolean array.
    *
    * @return The boolean array value
    * @throws ClassCastException if the parameter is not a boolean array
    */
   public boolean[] asBoolArray()
   {
      return (boolean[]) value;
   }

   /**
    * Get the value as a long array (integer array).
    *
    * @return The integer array value
    * @throws ClassCastException if the parameter is not an integer array
    */
   public long[] asLongArray()
   {
      return (long[]) value;
   }

   /**
    * Get the value as a double array.
    *
    * @return The double array value
    * @throws ClassCastException if the parameter is not a double array
    */
   public double[] asDoubleArray()
   {
      return (double[]) value;
   }

   /**
    * Get the value as a string array.
    *
    * @return The string array value
    * @throws ClassCastException if the parameter is not a string array
    */
   public String[] asStringArray()
   {
      return (String[]) value;
   }

   @Override
   public boolean equals(Object o)
   {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;
      ROS2Parameter that = (ROS2Parameter) o;
      return Objects.equals(name, that.name) && Objects.equals(value, that.value) && type == that.type;
   }

   @Override
   public int hashCode()
   {
      return Objects.hash(name, value, type);
   }

   @Override
   public String toString()
   {
      return "ROS2Parameter{" + "name='" + name + '\'' + ", value=" + value + ", type=" + type + '}';
   }
}
