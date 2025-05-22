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
package us.ihmc.jros2.generator.context;

import java.util.Locale;

public class InterfaceField
{
   private String type;
   private String javaType;
   private String name;
   private boolean stringUpperBounded;
   private int stringLength;
   private boolean array;
   private boolean upperBounded;
   private boolean unbounded;
   private int length;
   private String constantValue;
   private String defaultValue;
   private String headerComment;
   private String trailingComment;

   /**
    * Get the type name as defined in the ROS 2 .msg file
    *
    * @return the type name
    */
   public String getType()
   {
      return type;
   }

   public void type(String type)
   {
      this.type = type;
   }

   public String getJavaType()
   {
      if (javaType != null)
      {
         if (array && !isFixedSize())
         {
            return "IDLObjectSequence<" + javaType + ">";
         }
         else
         {
            return javaType;
         }
      }
      else
      {
         return inferJavaType();
      }
   }

   public void javaType(String javaType)
   {
      this.javaType = javaType;
   }

   public boolean isObjectSequence()
   {
      String javaType = getJavaType();

      if (javaType != null)
      {
         return javaType.startsWith("IDLObjectSequence");
      }
      else
      {
         return false;
      }
   }

   public String getObjectSequenceTypeClass()
   {
      if (isObjectSequence())
      {
         // TODO: Make this cleaner
         return getJavaType().replace("<", "").replace(">", "").replace("IDLObjectSequence", "").trim() + ".class";
      }
      else
      {
         return null;
      }
   }

   public String getName()
   {
      return name;
   }

   public void name(String name)
   {
      this.name = name;
   }

   public String getJavaName()
   {
      if (name != null)
      {
         if (constantValue != null)
         {
            // upper snake case constants
            return name.toUpperCase(Locale.getDefault());
         }
         else
         {
            // postfix with _ to avoid conflict with Java reserved words (e.g. "default")
            return name + "_";
         }
      }
      else
      {
         return null;
      }
   }

   public String getJavaGetterMethodName()
   {
      if (name != null)
      {
         return String.format("get%s", snakeToPascal(name));
      }
      else
      {
         return null;
      }
   }

   public String getJavaSetterMethodName()
   {
      if (name != null)
      {
         return String.format("set%s", snakeToPascal(name));
      }
      else
      {
         return null;
      }
   }

   public boolean isStringUpperBounded()
   {
      return stringUpperBounded;
   }

   public void stringUpperBounded(boolean stringUpperBounded)
   {
      this.stringUpperBounded = stringUpperBounded;
   }

   public int getStringLength()
   {
      return stringLength;
   }

   public boolean hasStringMaxLength()
   {
      return stringLength > 0;
   }

   public void stringLength(int stringLength)
   {
      this.stringLength = stringLength;
   }

   public boolean isArray()
   {
      return array;
   }

   public void array(boolean array)
   {
      this.array = array;
   }

   public boolean isUpperBounded()
   {
      return upperBounded;
   }

   public void upperBounded(boolean upperBounded)
   {
      this.upperBounded = upperBounded;
   }

   public boolean isUnbounded()
   {
      return unbounded;
   }

   public boolean isSequence()
   {
      return array && (upperBounded || unbounded);
   }

   public void unbounded(boolean unbounded)
   {
      this.unbounded = unbounded;
   }

   public boolean isFixedSize()
   {
      return array && !upperBounded && !unbounded;
   }

   public int getLength()
   {
      return length;
   }

   public void length(int length)
   {
      this.length = length;
   }

   public String getConstantValue()
   {
      return constantValue;
   }

   public void constantValue(String constantValue)
   {
      if (constantValue != null && isBuiltinStringType())
      {
         constantValue = Builtin.sanitizeStringValue(constantValue);
      }

      this.constantValue = constantValue;
   }

   public String getDefaultValue()
   {
      return defaultValue;
   }

   public void defaultValue(String defaultValue)
   {
      // String sanitization
      if (defaultValue != null && isBuiltinStringType())
      {
         defaultValue = Builtin.sanitizeStringValue(defaultValue);
      }

      // Boolean sanitization
      if (defaultValue != null && type.equals("bool"))
      {
         // Ensure it's either "true" or "false"
         // interface definitions allow 1 or 0 as well
         defaultValue = Boolean.toString(Builtin.sanitizeBoolValue(defaultValue));
      }

      this.defaultValue = defaultValue;
   }

   public String getHeaderComment()
   {
      return headerComment;
   }

   public void headerComment(String headerComment)
   {
      if (headerComment != null && headerComment.isEmpty())
      {
         headerComment = null;
      }

      this.headerComment = headerComment;
   }

   public String getTrailingComment()
   {
      return trailingComment;
   }

   public void trailingComment(String trailingComment)
   {
      if (trailingComment != null && trailingComment.isEmpty())
      {
         trailingComment = null;
      }

      this.trailingComment = trailingComment;
   }

   public boolean isBuiltinType()
   {
      return Builtin.isBuiltinType(type);
   }

   public boolean isBuiltinStringType()
   {
      return type != null && (type.equals("string") || type.equals("wstring"));
   }

   public boolean isBuiltinWStringType()
   {
      return type != null && type.equals("wstring");
   }

   public int getBuiltinTypeSize()
   {
      return Builtin.getBuiltinTypeSize(type);
   }

   public String getBuiltinTypeJavaType()
   {
      return Builtin.getBuiltinTypeJavaType(type);
   }

   public String getBuiltinTypeIDLSequenceType()
   {
      return Builtin.getBuiltinTypeIDLSequenceType(type);
   }

   public String getBuiltinCDRBufferWriteMethod()
   {
      return Builtin.getBuiltinCDRBufferWriteMethod(type);
   }

   public String getBuiltinCDRBufferReadMethod()
   {
      return Builtin.getBuiltinCDRBufferReadMethod(type);
   }

   private String inferJavaType()
   {
      if (type == null)
      {
         throw new RuntimeException("Cannot infer Java type, InterfaceField type has not been set");
      }

      if (isArray() && !isFixedSize())
      {
         if (isBuiltinType())
         {
            return getBuiltinTypeIDLSequenceType();
         }
         else
         {
            return "IDLObjectSequence<" + getType() + ">";
         }
      }
      else
      {
         if (isBuiltinType())
         {
            if (getType().equals("string") || getType().equals("wstring"))
            {
               if (getConstantValue() != null)
               {
                  return "String";
               }
               else
               {
                  return "StringBuilder";
               }
            }
            else
            {
               return getBuiltinTypeJavaType();
            }
         }
         else
         {
            return getType();
         }
      }
   }

   private static String snakeToPascal(String snake)
   {
      String[] parts = snake.split("_");
      StringBuilder pascal = new StringBuilder();
      for (String part : parts)
      {
         if (!part.isEmpty())
         {
            pascal.append(Character.toUpperCase(part.charAt(0)));
            pascal.append(part.substring(1).toLowerCase());
         }
      }
      return pascal.toString();
   }
}
