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
import us.ihmc.jros2.parser.util.Builtin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InterfaceFieldParser
{
   private static final Pattern STRING_WSTRING_TYPE_PATTERN = Pattern.compile(
         "^(?<strtype>string|wstring)(<=(?<strlen>\\d+))?(?<arr>\\[(?<seqbounds><=)?(?<len>\\d+)?])? (?<fname>[a-zA-Z](?!.*__)[a-zA-Z0-9_]*(?<!_))$");
   private static final Pattern TYPE_PATTERN = Pattern.compile(
         "^(?<type>[a-zA-Z0-9/_]+)(?<arr>\\[(?<seqbounds><=)?(?<len>\\d+)?])? (?<fname>[a-zA-Z](?!.*__)[a-zA-Z0-9_]*(?<!_))(\\s*=\\s*(?<constval>.+)|\\s(?<defval>.+))?$");

   /**
    *
    * @param context
    * @param fieldLine
    * @param headerComment
    * @return
    */
   public static InterfaceField parseField(InterfaceContext context, String fieldLine, String headerComment)
   {
      String trailingComment = null;
      if (fieldLine.contains("#"))
      {
         String lineWithCommentRemoved = fieldLine.substring(0, fieldLine.indexOf("#")).trim();
         trailingComment = fieldLine.substring(fieldLine.indexOf("#") + 1).trim(); // Do not include # in trailingComment
         fieldLine = lineWithCommentRemoved;
      }

      Matcher string_wstring_matcher = STRING_WSTRING_TYPE_PATTERN.matcher(fieldLine);

      InterfaceField field = null;

      // Handle string or wstring field
      if (string_wstring_matcher.matches())
      {
         // Example: wstring<=10[<=4]
         String stringTypeStr = string_wstring_matcher.group("strtype"); // e.g. wstring
         String stringLengthStr = string_wstring_matcher.group("strlen"); // e.g. 10
         String arrayStr = string_wstring_matcher.group("arr"); // e.g. [<=4]
         String sequenceBoundsStr = string_wstring_matcher.group("seqbounds"); // e.g. <=
         String lengthStr = string_wstring_matcher.group("len"); // e.g. 4
         String fieldNameStr = string_wstring_matcher.group("fname"); // my_type

         field = new InterfaceField();
         field.type(stringTypeStr);
         field.stringLength(stringLengthStr == null ? -1 : Integer.parseInt(stringLengthStr));
         field.array(arrayStr != null);
         field.upperBounded(sequenceBoundsStr != null);
         field.unbounded(arrayStr != null && sequenceBoundsStr == null && lengthStr == null);
         field.length(lengthStr == null ? -1 : Integer.parseInt(lengthStr));
         field.name(fieldNameStr);
         field.headerComment(headerComment);
         field.trailingComment(trailingComment);
      }

      Matcher typeMatcher = TYPE_PATTERN.matcher(fieldLine);

      if (typeMatcher.matches())
      {
         // Example with const val: MyCustomType[<=4] my_type = {data: 1}
         // Example with default val: MyCustomType[<=4] my_type {data: 1}
         String typeStr = typeMatcher.group("type"); // MyCustomType
         String arrayStr = typeMatcher.group("arr"); // [<=4]
         String sequenceBoundsStr = typeMatcher.group("seqbounds"); // <=
         String lengthStr = typeMatcher.group("len"); // 4
         String fieldNameStr = typeMatcher.group("fname"); // my_type
         String constValStr = typeMatcher.group("constval"); // {data: 1}
         String defaultValStr = typeMatcher.group("defval"); // {data: 1}

         field = new InterfaceField();
         field.type(typeStr);
         if (!Builtin.isBuiltinType(typeStr))
         {
            field.javaType(context.getMeta().getJavaPackageName() + "." + typeStr);
         }
         field.array(arrayStr != null);
         field.upperBounded(sequenceBoundsStr != null);
         field.unbounded(arrayStr != null && sequenceBoundsStr == null && lengthStr == null);
         field.length(lengthStr == null ? -1 : Integer.parseInt(lengthStr));
         field.name(fieldNameStr);
         field.constantValue(constValStr);
         field.defaultValue(defaultValStr);
         field.headerComment(headerComment);
         field.trailingComment(trailingComment);
      }

      return field;
   }
}
