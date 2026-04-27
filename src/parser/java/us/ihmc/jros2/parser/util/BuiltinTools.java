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
         case "int8":
         case "uint8":
         case "int16":
         case "char":
         case "float32":
         case "uint16":
         case "int32":
         case "uint32":
         case "float64":
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
         // 1 bit
         case "bool":
         // 1 byte
         case "byte":
         case "int8":
         // 2 bytes
         case "uint8":
         case "int16":
         // 2 bytes (unsigned)
         case "char":
            return 1;
         // 4 bytes
         case "float32":
         case "uint16":
         case "int32":
         case "uint32":
            return 4;
         // 8 bytes
         case "float64":
         case "int64":
         case "uint64":
            return 8;
         // Variable length strings
         // string uses 1-byte chars (UTF-8), wstring uses 4-byte chars (UTF-32)
         case "string":
            return 1;
         case "wstring":
            return 4;
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
         // 1 bit
         case "bool":
            return "boolean";
         // 1 byte
         case "byte":
         case "int8":
            return "byte";
         // 2 bytes
         case "uint8":
         case "int16":
            return "short";
         // 2 bytes (unsigned)
         case "char":
            return "char";
         // 4 bytes
         case "float32":
            return "float";
         // 4 bytes
         case "uint16":
         case "int32":
         case "uint32":
            return "int";
         // 8 bytes
         case "float64":
            return "double";
         // 8 bytes
         case "int64":
         case "uint64":
            return "long";
         // Variable length strings
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
         // 1 bit
         case "bool":
            return "IDLBoolSequence";
         // 1 byte
         case "byte":
         case "int8":
            return "IDLByteSequence";
         // 2 bytes
         case "uint8":
         case "int16":
            return "IDLShortSequence";
         // 2 bytes (unsigned)
         case "char":
            return "IDLCharSequence";
         // 4 bytes
         case "float32":
            return "IDLFloatSequence";
         // 4 bytes
         case "uint16":
         case "int32":
         case "uint32":
            return "IDLIntSequence";
         // 8 bytes
         case "float64":
            return "IDLDoubleSequence";
         // 8 bytes
         case "int64":
         case "uint64":
            return "IDLLongSequence";
         // Variable length strings
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
         // 1 bit
         case "bool":
            return "writeBoolean";
         // 1 byte
         case "byte":
         case "int8":
            return "writeByte";
         // 2 bytes
         case "uint8":
         case "int16":
            return "writeShort";
         // 2 bytes (unsigned)
         case "char":
            return "writeChar";
         // 4 bytes
         case "float32":
            return "writeFloat";
         // 4 bytes
         case "uint16":
         case "int32":
         case "uint32":
            return "writeInt";
         // 8 bytes
         case "float64":
            return "writeDouble";
         // 8 bytes
         case "int64":
         case "uint64":
            return "writeLong";
         // Variable length strings
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
         // 1 bit
         case "bool":
            return "readBoolean";
         // 1 byte
         case "byte":
         case "int8":
            return "readByte";
         // 2 bytes
         case "uint8":
         case "int16":
            return "readShort";
         // 2 bytes (unsigned)
         case "char":
            return "readChar";
         // 4 bytes
         case "float32":
            return "readFloat";
         // 4 bytes
         case "uint16":
         case "int32":
         case "uint32":
            return "readInt";
         // 8 bytes
         case "float64":
            return "readDouble";
         // 8 bytes
         case "int64":
         case "uint64":
            return "readLong";
         // Variable length strings
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
