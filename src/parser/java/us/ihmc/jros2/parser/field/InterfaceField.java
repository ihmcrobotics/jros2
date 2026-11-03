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
package us.ihmc.jros2.parser.field;

import us.ihmc.jros2.parser.util.BuiltinTools;
import us.ihmc.jros2.parser.util.DefaultValueTools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
   private String constantValueJava;
   private String defaultValue;
   private String defaultValueJava;
   private List<String> defaultValueJavaElements = Collections.emptyList();
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
      String elementType = getObjectSequenceElementType();
      return elementType == null ? null : elementType + ".class";
   }

   /**
    * @return the Java class name of the elements of a sequence of nested messages (e.g. {@code geometry_msgs.Pose} for
    *       {@code IDLObjectSequence<geometry_msgs.Pose>}), or null if this field is not such a sequence
    */
   public String getObjectSequenceElementType()
   {
      if (isObjectSequence())
      {
         return getJavaType().replace("<", "").replace(">", "").replace("IDLObjectSequence", "").trim();
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
            return name.toUpperCase(Locale.ROOT);
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
         // Handle edge case where we don't want to generate a method getClass()
         if (name.equals("class"))
         {
            return "getClass_";
         }
         else
         {
            return String.format("get%s", normalizeToPascal(name));
         }
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
         return String.format("set%s", normalizeToPascal(name));
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

   /**
    * @return the constant value: a quoted Java string literal for string constants, otherwise the literal as written in
    *       the .msg file; null if this field is not a constant
    */
   public String getConstantValue()
   {
      return constantValue;
   }

   /**
    * @return the constant value as a Java expression of the constant's Java type (e.g. {@code (byte) 200},
    *       {@code 5000000000L}, {@code "foo"}), or null if this field is not a constant
    */
   public String getConstantValueJava()
   {
      return constantValueJava;
   }

   /**
    * Sets the constant value. The type (and array/bound attributes) must be set first.
    *
    * @param constantValue the literal as written in the .msg file
    * @throws IllegalArgumentException if the value is not valid for the type
    */
   public void constantValue(String constantValue)
   {
      if (constantValue == null)
      {
         this.constantValue = null;
         this.constantValueJava = null;
         return;
      }

      constantValue = constantValue.trim();
      constantValueJava = DefaultValueTools.toJavaExpression(type, constantValue, 0, false);

      if (isBuiltinStringType())
      {
         constantValue = constantValueJava;
      }

      this.constantValue = constantValue;
   }

   /**
    * @return the default value: a quoted Java string literal for scalar string fields, {@code true}/{@code false} for
    *       scalar bool fields, otherwise the literal as written in the .msg file (e.g. {@code [1, 2, 3]} for arrays); null
    *       if the field has no default value
    */
   public String getDefaultValue()
   {
      return defaultValue;
   }

   /**
    * @return the default value of a non-array field as a Java expression of the field's Java type, or null
    */
   public String getDefaultValueJava()
   {
      return defaultValueJava;
   }

   /**
    * @return the default elements of an array or sequence field as Java expressions (for sequences, of the element type
    *       stored by the IDL sequence class), or an empty list
    */
   public List<String> getDefaultValueJavaElements()
   {
      return defaultValueJavaElements;
   }

   /**
    * @return true if this array or sequence field has at least one default element
    */
   public boolean hasDefaultValueJavaElements()
   {
      return !defaultValueJavaElements.isEmpty();
   }

   /**
    * @return the Java element type of the array used to add default values to an IDL sequence (e.g. {@code short} for
    *       {@code uint16[]}, {@code java.lang.String} for {@code string[]})
    */
   public String getSequenceDefaultValueJavaElementType()
   {
      if (isBuiltinStringType())
      {
         return "java.lang.String";
      }

      if (type.equals("int16") || type.equals("uint16"))
      {
         return "short";
      }

      return BuiltinTools.getBuiltinTypeJavaType(type);
   }

   /**
    * Sets the default value. The type (and array/bound attributes) must be set first.
    *
    * @param defaultValue the literal as written in the .msg file
    * @throws IllegalArgumentException if the value is not valid for the type
    */
   public void defaultValue(String defaultValue)
   {
      if (defaultValue == null)
      {
         this.defaultValue = null;
         this.defaultValueJava = null;
         this.defaultValueJavaElements = Collections.emptyList();
         return;
      }

      defaultValue = defaultValue.trim();

      if (array)
      {
         List<String> elements = DefaultValueTools.splitArrayLiteral(type, defaultValue, isFixedSize() ? length : -1, upperBounded ? length : -1);
         List<String> javaElements = new ArrayList<>(elements.size());
         for (String element : elements)
         {
            javaElements.add(DefaultValueTools.toJavaExpression(type, element, stringLength, isSequence()));
         }
         this.defaultValueJavaElements = Collections.unmodifiableList(javaElements);
         this.defaultValueJava = null;
      }
      else
      {
         this.defaultValueJava = DefaultValueTools.toJavaExpression(type, defaultValue, stringLength, false);
         this.defaultValueJavaElements = Collections.emptyList();

         if (isBuiltinStringType() || type.equals("bool"))
         {
            defaultValue = defaultValueJava;
         }
      }

      this.defaultValue = defaultValue;
   }

   public boolean isDefaultValueArray()
   {
      return array && defaultValue != null && (defaultValue.startsWith("[") && defaultValue.endsWith("]"));
   }

   public List<String> getDefaultValueArrayValues()
   {
      List<String> values = new ArrayList<>();

      if (isDefaultValueArray())
      {
         for (String value : defaultValue.substring(1, defaultValue.length() - 1).split(","))
         {
            values.add(value.trim());
         }
      }

      return values;
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
      return BuiltinTools.isBuiltinType(type);
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
      return BuiltinTools.getBuiltinTypeSize(type);
   }

   public String getBuiltinTypeJavaType()
   {
      return BuiltinTools.getBuiltinTypeJavaType(type);
   }

   public String getBuiltinTypeIDLSequenceType()
   {
      return BuiltinTools.getBuiltinTypeIDLSequenceType(type);
   }

   public String getBuiltinCDRBufferWriteMethod()
   {
      return BuiltinTools.getBuiltinCDRBufferWriteMethod(type);
   }

   public String getBuiltinCDRBufferReadMethod()
   {
      return BuiltinTools.getBuiltinCDRBufferReadMethod(type);
   }

   /**
    * @return the {@literal CDRBuffer} method that writes a fixed-size array of this built-in type in bulk, or null for
    *       strings
    */
   public String getBuiltinCDRBufferWriteArrayMethod()
   {
      return BuiltinTools.getBuiltinCDRBufferWriteArrayMethod(type);
   }

   /**
    * @return the {@literal CDRBuffer} method that reads a fixed-size array of this built-in type in bulk, or null for
    *       strings
    */
   public String getBuiltinCDRBufferReadArrayMethod()
   {
      return BuiltinTools.getBuiltinCDRBufferReadArrayMethod(type);
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

   private static String normalizeToPascal(String input)
   {
      String normalized = input.replace("_", " ");
      normalized = normalized.replaceAll("(?<!^)([A-Z])", " $1");
      String[] parts = normalized.split("\\s+");

      StringBuilder pascal = new StringBuilder();
      for (String part : parts)
      {
         if (!part.isEmpty())
         {
            pascal.append(Character.toUpperCase(part.charAt(0)));
            pascal.append(part.substring(1).toLowerCase(Locale.ROOT));
         }
      }

      return pascal.toString();
   }
}