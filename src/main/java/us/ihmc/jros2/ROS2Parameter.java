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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * An immutable ROS 2 parameter: a name and a value of one of the {@link ROS2ParameterType}s.
 * <p>
 * Values are stored as {@code Boolean}, {@code Long}, {@code Double}, {@code String}, {@code byte[]}, {@code boolean[]},
 * {@code long[]}, {@code double[]} or {@code String[]}. {@link #of(String, Object)} also accepts other boxed numbers
 * ({@code Integer}, {@code Float}, ...), {@code int[]}, {@code float[]} and lists, and converts them. Arrays are copied.
 */
public final class ROS2Parameter
{
   private final String name;
   private final ROS2ParameterType type;
   private final Object value;

   private ROS2Parameter(String name, ROS2ParameterType type, Object value)
   {
      this.name = Objects.requireNonNull(name, "name");
      this.type = type;
      this.value = value;
   }

   /**
    * @param name  the parameter name
    * @param value the value, or null for {@link ROS2ParameterType#NOT_SET}
    * @return the parameter
    * @throws IllegalArgumentException if the value type is not supported
    */
   public static ROS2Parameter of(String name, Object value)
   {
      if (value == null)
         return new ROS2Parameter(name, ROS2ParameterType.NOT_SET, null);
      if (value instanceof Boolean)
         return new ROS2Parameter(name, ROS2ParameterType.BOOL, value);
      if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte)
         return new ROS2Parameter(name, ROS2ParameterType.INTEGER, ((Number) value).longValue());
      if (value instanceof Double || value instanceof Float)
         return new ROS2Parameter(name, ROS2ParameterType.DOUBLE, ((Number) value).doubleValue());
      if (value instanceof CharSequence)
         return new ROS2Parameter(name, ROS2ParameterType.STRING, value.toString());
      if (value instanceof byte[] bytes)
         return new ROS2Parameter(name, ROS2ParameterType.BYTE_ARRAY, bytes.clone());
      if (value instanceof boolean[] bools)
         return new ROS2Parameter(name, ROS2ParameterType.BOOL_ARRAY, bools.clone());
      if (value instanceof long[] longs)
         return new ROS2Parameter(name, ROS2ParameterType.INTEGER_ARRAY, longs.clone());
      if (value instanceof int[] ints)
         return new ROS2Parameter(name, ROS2ParameterType.INTEGER_ARRAY, Arrays.stream(ints).asLongStream().toArray());
      if (value instanceof double[] doubles)
         return new ROS2Parameter(name, ROS2ParameterType.DOUBLE_ARRAY, doubles.clone());
      if (value instanceof float[] floats)
      {
         double[] doubles = new double[floats.length];
         for (int i = 0; i < floats.length; i++)
            doubles[i] = floats[i];
         return new ROS2Parameter(name, ROS2ParameterType.DOUBLE_ARRAY, doubles);
      }
      if (value instanceof String[] strings)
         return new ROS2Parameter(name, ROS2ParameterType.STRING_ARRAY, strings.clone());
      if (value instanceof List<?> list)
         return ofList(name, list);

      throw new IllegalArgumentException("Unsupported parameter value type for '" + name + "': " + value.getClass().getName());
   }

   private static ROS2Parameter ofList(String name, List<?> list)
   {
      if (list.isEmpty())
         throw new IllegalArgumentException("Cannot infer the type of parameter '" + name + "' from an empty list; use an array");

      Object first = list.get(0);
      try
      {
         if (first instanceof Boolean)
         {
            boolean[] values = new boolean[list.size()];
            for (int i = 0; i < values.length; i++)
               values[i] = (Boolean) list.get(i);
            return new ROS2Parameter(name, ROS2ParameterType.BOOL_ARRAY, values);
         }
         if (first instanceof Long || first instanceof Integer || first instanceof Short || first instanceof Byte)
            return new ROS2Parameter(name, ROS2ParameterType.INTEGER_ARRAY, list.stream().mapToLong(v -> ((Number) v).longValue()).toArray());
         if (first instanceof Double || first instanceof Float)
            return new ROS2Parameter(name, ROS2ParameterType.DOUBLE_ARRAY, list.stream().mapToDouble(v -> ((Number) v).doubleValue()).toArray());
         if (first instanceof CharSequence)
            return new ROS2Parameter(name, ROS2ParameterType.STRING_ARRAY, list.stream().map(v -> ((CharSequence) v).toString()).toArray(String[]::new));
      }
      catch (ClassCastException e)
      {
         throw new IllegalArgumentException("List parameter '" + name + "' mixes element types", e);
      }
      throw new IllegalArgumentException("Unsupported list element type for '" + name + "': " + first.getClass().getName());
   }

   /**
    * Create a parameter from its {@code rcl_interfaces} representation.
    */
   public static ROS2Parameter fromMessage(String name, rcl_interfaces.ParameterValue message)
   {
      ROS2ParameterType type = ROS2ParameterType.fromId(message.getType());
      return switch (type)
      {
         case NOT_SET -> new ROS2Parameter(name, type, null);
         case BOOL -> new ROS2Parameter(name, type, message.getBoolValue());
         case INTEGER -> new ROS2Parameter(name, type, message.getIntegerValue());
         case DOUBLE -> new ROS2Parameter(name, type, message.getDoubleValue());
         case STRING -> new ROS2Parameter(name, type, message.getStringValueAsString());
         case BYTE_ARRAY ->
         {
            byte[] values = new byte[message.getByteArrayValue().size()];
            for (int i = 0; i < values.length; i++)
               values[i] = message.getByteArrayValue().get(i);
            yield new ROS2Parameter(name, type, values);
         }
         case BOOL_ARRAY ->
         {
            boolean[] values = new boolean[message.getBoolArrayValue().size()];
            for (int i = 0; i < values.length; i++)
               values[i] = message.getBoolArrayValue().get(i);
            yield new ROS2Parameter(name, type, values);
         }
         case INTEGER_ARRAY ->
         {
            long[] values = new long[message.getIntegerArrayValue().size()];
            for (int i = 0; i < values.length; i++)
               values[i] = message.getIntegerArrayValue().get(i);
            yield new ROS2Parameter(name, type, values);
         }
         case DOUBLE_ARRAY ->
         {
            double[] values = new double[message.getDoubleArrayValue().size()];
            for (int i = 0; i < values.length; i++)
               values[i] = message.getDoubleArrayValue().get(i);
            yield new ROS2Parameter(name, type, values);
         }
         case STRING_ARRAY ->
         {
            String[] values = new String[message.getStringArrayValue().size()];
            for (int i = 0; i < values.length; i++)
               values[i] = message.getStringArrayValue().getAsString(i);
            yield new ROS2Parameter(name, type, values);
         }
      };
   }

   /**
    * Write this parameter's value into its {@code rcl_interfaces} representation.
    */
   public void toMessage(rcl_interfaces.ParameterValue message)
   {
      message.set(new rcl_interfaces.ParameterValue());
      message.setType(type.getId());
      switch (type)
      {
         case NOT_SET ->
         {
         }
         case BOOL -> message.setBoolValue((Boolean) value);
         case INTEGER -> message.setIntegerValue((Long) value);
         case DOUBLE -> message.setDoubleValue((Double) value);
         case STRING -> message.setStringValue((String) value);
         case BYTE_ARRAY -> message.getByteArrayValue().addAll((byte[]) value);
         case BOOL_ARRAY -> message.getBoolArrayValue().addAll((boolean[]) value);
         case INTEGER_ARRAY -> message.getIntegerArrayValue().addAll((long[]) value);
         case DOUBLE_ARRAY -> message.getDoubleArrayValue().addAll((double[]) value);
         case STRING_ARRAY -> message.getStringArrayValue().addAll((String[]) value);
      }
   }

   /**
    * Write this parameter (name and value) into its {@code rcl_interfaces} representation.
    */
   public void toMessage(rcl_interfaces.Parameter message)
   {
      message.setName(name);
      toMessage(message.getValue());
   }

   public String getName()
   {
      return name;
   }

   public ROS2ParameterType getType()
   {
      return type;
   }

   /**
    * @return the value as stored (see the class documentation), or null if not set. Arrays are returned as copies.
    */
   public Object getValue()
   {
      return switch (type)
      {
         case BYTE_ARRAY -> ((byte[]) value).clone();
         case BOOL_ARRAY -> ((boolean[]) value).clone();
         case INTEGER_ARRAY -> ((long[]) value).clone();
         case DOUBLE_ARRAY -> ((double[]) value).clone();
         case STRING_ARRAY -> ((String[]) value).clone();
         default -> value;
      };
   }

   public boolean asBool()
   {
      return (Boolean) checkType(ROS2ParameterType.BOOL);
   }

   public long asLong()
   {
      return (Long) checkType(ROS2ParameterType.INTEGER);
   }

   public int asInt()
   {
      return Math.toIntExact(asLong());
   }

   public double asDouble()
   {
      return (Double) checkType(ROS2ParameterType.DOUBLE);
   }

   public String asString()
   {
      return (String) checkType(ROS2ParameterType.STRING);
   }

   public byte[] asByteArray()
   {
      return ((byte[]) checkType(ROS2ParameterType.BYTE_ARRAY)).clone();
   }

   public boolean[] asBoolArray()
   {
      return ((boolean[]) checkType(ROS2ParameterType.BOOL_ARRAY)).clone();
   }

   public long[] asLongArray()
   {
      return ((long[]) checkType(ROS2ParameterType.INTEGER_ARRAY)).clone();
   }

   public double[] asDoubleArray()
   {
      return ((double[]) checkType(ROS2ParameterType.DOUBLE_ARRAY)).clone();
   }

   public String[] asStringArray()
   {
      return ((String[]) checkType(ROS2ParameterType.STRING_ARRAY)).clone();
   }

   private Object checkType(ROS2ParameterType expected)
   {
      if (type != expected)
      {
         throw new IllegalStateException("Parameter '" + name + "' is of type " + type.getRosName() + ", not " + expected.getRosName());
      }
      return value;
   }

   @Override
   public boolean equals(Object obj)
   {
      if (this == obj)
         return true;
      if (!(obj instanceof ROS2Parameter other))
         return false;
      return name.equals(other.name) && type == other.type && Objects.deepEquals(value, other.value);
   }

   @Override
   public int hashCode()
   {
      return Objects.hash(name, type, Arrays.deepHashCode(new Object[] {value}));
   }

   @Override
   public String toString()
   {
      String valueString = switch (type)
      {
         case BYTE_ARRAY -> Arrays.toString((byte[]) value);
         case BOOL_ARRAY -> Arrays.toString((boolean[]) value);
         case INTEGER_ARRAY -> Arrays.toString((long[]) value);
         case DOUBLE_ARRAY -> Arrays.toString((double[]) value);
         case STRING_ARRAY -> Arrays.toString((String[]) value);
         default -> String.valueOf(value);
      };
      return name + "=" + valueString + " (" + type.getRosName() + ")";
   }
}
