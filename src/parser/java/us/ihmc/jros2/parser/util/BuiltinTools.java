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
package us.ihmc.jros2.parser.util;

import java.util.Objects;

public final class BuiltinTools
{
   /**
    * If the type is a ROS 2 built-in type
    * See: <a href="https://docs.ros.org/en/foxy/Concepts/About-ROS-Interfaces.html#field-types">Field types</a>
    *
    * @param type the type name
    * @return true if built-in type, false if not a built-in type or the type was not set
    */
   public static boolean isBuiltinType(String type)
   {
      Objects.requireNonNull(type);

      switch (type)
      {
         case "bool":
         case "byte":
         case "char":
         case "float32":
         case "float64":
         case "int8":
         case "uint8":
         case "int16":
         case "uint16":
         case "int32":
         case "uint32":
         case "int64":
         case "uint64":
         case "string":
         case "wstring":
            return true;
      }

      return false;
   }

   /**
    * The size in bytes of the built-in type
    *
    * @param builtinType the built-in type name
    * @return the size in bytes, -1 if not a built-in type or the type was not set
    */
   public static int getBuiltinTypeSize(String builtinType)
   {
      if (!isBuiltinType(builtinType))
      {
         throw new IllegalArgumentException("builtinType was not a ROS 2 built-in type");
      }

      switch (builtinType)
      {
         case "bool":
         case "byte":
         case "char":
         case "int8":
         case "uint8":
            return 1;
         case "int16":
         case "uint16":
            return 2;
         case "float32":
         case "int32":
         case "uint32":
            return 4;
         case "float64":
         case "int64":
         case "uint64":
            return 8;
         // For strings, return the character size. See https://design.ros2.org/articles/wide_strings.html
         case "string":
            return 1;
         case "wstring":
            return 2;
         default:
            return -1;
      }
   }

   /**
    * The corresponding Java type for a ROS 2 built-in type
    *
    * @param builtinType the built-in type name
    * @return the corresponding Java type or null if not a built-in type or the type was not set
    */
   public static String getBuiltinTypeJavaType(String builtinType)
   {
      if (!isBuiltinType(builtinType))
      {
         throw new IllegalArgumentException("builtinType was not a ROS 2 built-in type");
      }

      switch (builtinType)
      {
         case "bool":
            return "boolean";
         case "byte":
         case "int8":
            return "byte";
         case "char":
            return "char";
         case "uint8":
         case "int16":
            return "short";
         case "float32":
            return "float";
         case "uint16":
         case "int32":
            return "int";
         case "float64":
            return "double";
         case "uint32":
         case "uint64":
         case "int64":
            return "long";
         case "string":
         case "wstring":
            return "StringBuilder";
         default:
            return null;
      }
   }

   /**
    * The {@literal us.ihmc.fastddsjava.cdr.idl.IDLSequence} type for a built-in type
    *
    * @param builtinType the built-in type name
    * @return the {@literal us.ihmc.fastddsjava.cdr.idl.IDLSequence} class name (no package prepended) or null if not a default type or the type was not set
    */
   public static String getBuiltinTypeIDLSequenceType(String builtinType)
   {
      if (!isBuiltinType(builtinType))
      {
         throw new IllegalArgumentException("builtinType was not a ROS 2 built-in type");
      }

      switch (builtinType)
      {
         case "bool":
            return "IDLBoolSequence";
         case "byte":
         case "int8":
            return "IDLByteSequence";
         case "char":
            return "IDLCharSequence";
         case "uint8":
         case "int16":
            return "IDLShortSequence";
         case "float32":
            return "IDLFloatSequence";
         case "uint16":
         case "int32":
            return "IDLIntSequence";
         case "float64":
            return "IDLDoubleSequence";
         case "uint32":
         case "uint64":
         case "int64":
            return "IDLLongSequence";
         case "string":
            return "IDLStringSequence";
         case "wstring":
            return "IDLWStringSequence";
         default:
            return null;
      }
   }

   /**
    * The method name within {@literal us.ihmc.fastddsjava.cdr.CDRBuffer} used to write the built-in type
    *
    * @param builtinType the built-in type name
    * @return the method name
    */
   public static String getBuiltinCDRBufferWriteMethod(String builtinType)
   {
      if (!isBuiltinType(builtinType))
      {
         throw new IllegalArgumentException("builtinType was not a ROS 2 built-in type");
      }

      switch (builtinType)
      {
         case "bool":
            return "writeBoolean";
         case "byte":
         case "int8":
            return "writeByte";
         case "char":
            return "writeChar";
         case "uint8":
         case "int16":
            return "writeShort";
         case "float32":
            return "writeFloat";
         case "int32":
         case "uint16":
            return "writeInt";
         case "float64":
            return "writeDouble";
         case "uint32":
         case "uint64":
         case "int64":
            return "writeLong";
         case "string":
            return "writeString";
         case "wstring":
            return "writeWString";
         default:
            return null;
      }
   }

   /**
    * The method name within {@literal us.ihmc.fastddsjava.cdr.CDRBuffer} used to read the built-in type
    *
    * @param builtinType the built-in type name
    * @return the method name
    */
   public static String getBuiltinCDRBufferReadMethod(String builtinType)
   {
      if (!isBuiltinType(builtinType))
      {
         throw new IllegalArgumentException("builtinType was not a ROS 2 built-in type");
      }

      switch (builtinType)
      {
         case "bool":
            return "readBoolean";
         case "byte":
         case "int8":
            return "readByte";
         case "char":
            return "readChar";
         case "uint8":
         case "int16":
            return "readShort";
         case "float32":
            return "readFloat";
         case "uint16":
         case "int32":
            return "readInt";
         case "float64":
            return "readDouble";
         case "uint32":
         case "uint64":
         case "int64":
            return "readLong";
         case "string":
            return "readString";
         case "wstring":
            return "readWString";
         default:
            return null;
      }
   }

   public static String sanitizeStringAsJavaFieldValue(String value)
   {
      if (value == null || value.isEmpty())
         return "\"\"";

      // Trim for good measure
      String sanitized = value.trim();

      // Check if string is quoted; if so, remove quotes
      char firstChar = sanitized.charAt(0);
      char lastChar = sanitized.charAt(sanitized.length() - 1);

      if ((firstChar == '\"' || firstChar == '\'') && firstChar == lastChar)
      {
         sanitized = sanitized.substring(1, sanitized.length() - 1);
         if (sanitized.indexOf(firstChar) != -1)
         {
            throw new IllegalArgumentException("Illegal character contained in string: " + firstChar);
         }
      }

      // Escape any double quotes
      sanitized = sanitized.replaceAll("(?<!\\\\)\"", "\\\\\"");

      // Return the sanitized value wrapped in double quotes
      return String.format("\"%s\"", sanitized);
   }

   public static String sanitizeBoolAsJavaFieldValue(String value)
   {
      if (value == null || value.isEmpty())
         return "false";

      String valueCopy = value.trim();

      return switch (valueCopy)
      {
         case "1", "true" -> "true";
         case "0", "false" -> "false";
         default -> throw new IllegalArgumentException(String.format("Unexpected bool value {%s}.", value));
      };
   }
}
