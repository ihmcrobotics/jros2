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

import us.ihmc.jros2.parser.InterfaceContext;
import us.ihmc.jros2.parser.util.BuiltinTools;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses one line of a .msg file into an {@link InterfaceField}, following the rules of the ROS 2 reference
 * implementation ({@code rosidl_adapter/parser.py}):
 * <ul>
 *    <li>Tabs are treated as spaces; everything from the first {@code #} is a comment (even inside quotes).</li>
 *    <li>{@code <type> <name>} is a field, {@code <type> <name> <default>} a field with a default value and
 *    {@code <type> <name>=<value>} (spaces around {@code =} allowed) a constant.</li>
 *    <li>{@code <type>} is a built-in type, {@code Type} (same package) or {@code pkg/Type}, optionally with a string bound
 *    {@code string<=N} / {@code wstring<=N} and an array suffix {@code [N]}, {@code [<=N]} or {@code []}.</li>
 *    <li>Field names start with a letter, contain only letters, digits and underscores, contain no {@code __} and do not
 *    end with {@code _}. (Upper case letters are accepted, although ROS 2 itself only allows lower case.)</li>
 * </ul>
 * Lines that do not follow these rules are rejected with an {@link InterfaceFieldParsingException} that explains why;
 * no line is silently ignored.
 */
public final class InterfaceFieldParser
{
   private static final Pattern TYPE_PATTERN = Pattern.compile(
         "^(?<type>[a-zA-Z][a-zA-Z0-9_]*(?:/[a-zA-Z][a-zA-Z0-9_]*)?)(?:<=(?<strlen>\\d+))?(?<arr>\\[(?<seqbounds><=)?(?<len>\\d+)?])?$");
   private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*");

   private InterfaceFieldParser()
   {
   }

   /**
    * @param context       the interface the line belongs to (used to resolve same-package type names)
    * @param fieldLine     the line, without leading or trailing whitespace
    * @param headerComment the comment lines preceding the field, or an empty string
    * @return the parsed field, or null if the line is empty or only a comment
    * @throws InterfaceFieldParsingException if the line is not a valid field or constant definition
    */
   public static InterfaceField parseField(InterfaceContext context, String fieldLine, String headerComment) throws InterfaceFieldParsingException
   {
      InterfaceField field = null;
      String fieldName = null;

      try
      {
         String line = fieldLine.replace('\t', ' ');

         String trailingComment = null;
         int commentIndex = line.indexOf('#');
         if (commentIndex >= 0)
         {
            trailingComment = line.substring(commentIndex + 1).trim(); // Do not include # in trailingComment
            line = line.substring(0, commentIndex);
         }
         line = line.trim();

         if (line.isEmpty())
         {
            return null;
         }

         int typeEnd = line.indexOf(' ');
         if (typeEnd < 0)
         {
            throw new IllegalArgumentException("Expected '<type> <name>' but the line has no field name");
         }

         String typeToken = line.substring(0, typeEnd);
         String rest = line.substring(typeEnd + 1).trim();

         Matcher nameMatcher = NAME_PATTERN.matcher(rest);
         if (!nameMatcher.find())
         {
            throw new IllegalArgumentException("Invalid field name '" + firstToken(rest) + "': it must start with a letter");
         }
         fieldName = nameMatcher.group();
         String afterName = rest.substring(fieldName.length());

         String constantValue = null;
         String defaultValue = null;
         String afterNameTrimmed = afterName.trim();
         if (afterNameTrimmed.startsWith("="))
         {
            constantValue = afterNameTrimmed.substring(1).trim();
            if (constantValue.isEmpty())
            {
               throw new IllegalArgumentException("Constant '" + fieldName + "' has no value");
            }
         }
         else if (!afterName.isEmpty())
         {
            if (afterName.charAt(0) != ' ')
            {
               throw new IllegalArgumentException("Invalid field name '" + firstToken(rest) + "': only letters, digits and underscores are allowed");
            }
            defaultValue = afterNameTrimmed;
         }

         if (fieldName.contains("__"))
         {
            throw new IllegalArgumentException("Invalid field name '" + fieldName + "': it must not contain '__'");
         }
         if (fieldName.endsWith("_"))
         {
            throw new IllegalArgumentException("Invalid field name '" + fieldName + "': it must not end with '_'");
         }

         Matcher typeMatcher = TYPE_PATTERN.matcher(typeToken);
         if (!typeMatcher.matches())
         {
            throw new IllegalArgumentException("Invalid type '" + typeToken + "'");
         }

         // Example: wstring<=10[<=4] or MyCustomType[<=4] or pkg/Type[]
         String typeStr = typeMatcher.group("type");
         String stringLengthStr = typeMatcher.group("strlen"); // e.g. 10
         String arrayStr = typeMatcher.group("arr"); // e.g. [<=4]
         String sequenceBoundsStr = typeMatcher.group("seqbounds"); // e.g. <=
         String lengthStr = typeMatcher.group("len"); // e.g. 4

         boolean builtin = BuiltinTools.isBuiltinType(typeStr);
         boolean stringType = typeStr.equals("string") || typeStr.equals("wstring");

         if (typeStr.equals("time") || typeStr.equals("duration"))
         {
            throw new IllegalArgumentException("'" + typeStr + "' is not a ROS 2 field type; use builtin_interfaces/" + (typeStr.equals("time") ? "Time" : "Duration"));
         }
         if (builtin && typeStr.contains("/"))
         {
            throw new IllegalArgumentException("Invalid type '" + typeToken + "'");
         }
         if (stringLengthStr != null && !stringType)
         {
            throw new IllegalArgumentException("Only string and wstring can have an upper bound ('<=N'), not '" + typeStr + "'");
         }
         int stringLength = stringLengthStr == null ? 0 : parsePositive(stringLengthStr, "string upper bound");
         if (sequenceBoundsStr != null && lengthStr == null)
         {
            throw new IllegalArgumentException("Bounded array '" + arrayStr + "' needs an upper bound");
         }
         int length = lengthStr == null ? -1 : parsePositive(lengthStr, "array size");

         field = new InterfaceField();
         field.type(typeStr);
         if (!builtin)
         {
            String typePackageStr = typeStr.contains("/") ? typeStr.substring(0, typeStr.lastIndexOf('/')) : "";
            String javaTypePackageStr = typePackageStr.isEmpty() ? context.getJavaPackageName() : typePackageStr;
            String javaTypeStr = typeStr.substring(typeStr.lastIndexOf('/') + 1);

            field.javaType(javaTypePackageStr + "." + javaTypeStr);
         }
         field.stringLength(stringLength);
         field.stringUpperBounded(stringLength > 0);
         field.array(arrayStr != null);
         field.upperBounded(sequenceBoundsStr != null);
         field.unbounded(arrayStr != null && sequenceBoundsStr == null && lengthStr == null);
         field.length(length);
         field.name(fieldName);

         if (constantValue != null)
         {
            if (!builtin)
            {
               throw new IllegalArgumentException("The constant type '" + typeStr + "' must be a primitive type");
            }
            if (arrayStr != null)
            {
               throw new IllegalArgumentException("Constants cannot be arrays");
            }
            if (stringLength > 0)
            {
               throw new IllegalArgumentException("Constants cannot be bounded strings");
            }
            field.constantValue(constantValue);
         }

         if (defaultValue != null)
         {
            if (!builtin)
            {
               throw new IllegalArgumentException("Default values are only supported for built-in types, not '" + typeStr + "'");
            }
            field.defaultValue(defaultValue);
         }

         field.headerComment(headerComment);
         field.trailingComment(trailingComment);
      }
      catch (Exception e)
      {
         String name = field != null ? field.getName() : fieldName != null ? fieldName : "<unknown>";

         throw new InterfaceFieldParsingException(name, fieldLine, e);
      }

      return field;
   }

   private static int parsePositive(String digits, String what)
   {
      int value;
      try
      {
         value = Integer.parseInt(digits);
      }
      catch (NumberFormatException e)
      {
         throw new IllegalArgumentException("The " + what + " " + digits + " is too large");
      }
      if (value <= 0)
      {
         throw new IllegalArgumentException("The " + what + " must be greater than zero");
      }
      return value;
   }

   private static String firstToken(String text)
   {
      int space = text.indexOf(' ');
      return space < 0 ? text : text.substring(0, space);
   }
}
