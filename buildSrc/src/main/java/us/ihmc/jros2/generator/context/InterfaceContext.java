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

import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class InterfaceContext
{
   private final String fileName; // Includes extension (i.e. <.msg, .srv, .action>)
   private final String fileContent;
   private final String name;
   private final String packageName;
   private String headerComment;
   private transient boolean parsed;

   public InterfaceContext(String packageName, String fileName, String fileContent)
   {
      this.fileName = fileName;
      this.fileContent = fileContent;
      this.name = fileName.substring(0, fileName.indexOf("."));
      this.packageName = packageName;
   }

   public String getFileName()
   {
      return fileName;
   }

   public String getFileContent()
   {
      return fileContent;
   }

   public String getName()
   {
      return name;
   }

   public String getFullName()
   {
      return String.format("%s/%s", packageName, name);
   }

   public boolean matches(String name)
   {
      // Check if the full name (package_name/name) equals the provided name first, then check the shorthand name
      return getFullName().equals(name) || this.name.equals(name);
   }

   public String getJavaClassname()
   {
      // TODO: Some sanitation required
      return getName();
   }

   public String getPackageName()
   {
      return packageName;
   }

   public String getJavaPackageName()
   {
      return packageName + ".msg.dds";
   }

   public String getHeaderComment()
   {
      return headerComment;
   }

   protected abstract void onSection();

   protected abstract void onField(InterfaceField field);

   public void parse(List<MsgContext> discoveredMsgs)
   {
      if (parsed)
      {
         throw new RuntimeException(fileName + " has already been parsed");
      }

      String[] lines = fileContent.split("\\R");

      boolean parsedFirstField = false;
      StringJoiner commentLines = new StringJoiner("\n");

      for (String line : lines)
      {
         // Remove leading or trailing whitespace
         line = line.trim();

         String trailingComment = null;

         // If the entire line is a comment
         if (line.startsWith("#"))
         {
            commentLines.add(line.substring(1).trim()); // Remove # from start of line
            continue;
         }

         // If the line contains a comment, but it isn't the entire line
         if (line.contains("#"))
         {
            String lineWithCommentRemoved = line.substring(0, line.indexOf("#")).trim();
            trailingComment = line.substring(line.indexOf("#") + 1).trim(); // Do not include # in trailingComment
            line = lineWithCommentRemoved;
         }

         // If the line is a section separator
         if (line.equals("---"))
         {
            onSection();
         }
         else if (line.isEmpty() && !parsedFirstField)
         {
            headerComment = commentLines.toString();
            commentLines = new StringJoiner("\n");
         }
         else
         {
            InterfaceField field = parseField(line, commentLines.toString(), trailingComment, discoveredMsgs);
            commentLines = new StringJoiner("\n");
            if (field != null)
            {
               onField(field);
               parsedFirstField = true;
            }
         }
      }

      parsed = true;
   }

   private static final Pattern STRING_WSTRING_TYPE_PATTERN = Pattern.compile(
         "^(?<strtype>string|wstring)(<=(?<strlen>\\d+))?(?<arr>\\[(?<seqbounds><=)?(?<len>\\d+)?])? (?<fname>[a-zA-Z](?!.*__)[a-zA-Z0-9_]*(?<!_))$");
   private static final Pattern TYPE_PATTERN = Pattern.compile(
         "^(?<type>[a-zA-Z0-9/_]+)(?<arr>\\[(?<seqbounds><=)?(?<len>\\d+)?])? (?<fname>[a-zA-Z](?!.*__)[a-zA-Z0-9_]*(?<!_))(\\s*=\\s*(?<constval>.+)|\\s(?<defval>.+))?$");

   private InterfaceField parseField(String line, String headerComment, String trailingComment, List<MsgContext> discoveredMsgs)
   {
      Matcher string_wstring_matcher = STRING_WSTRING_TYPE_PATTERN.matcher(line);

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

         // TODO
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

      Matcher typeMatcher = TYPE_PATTERN.matcher(line);

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
            field.javaType(getJavaPackageName() + "." + typeStr);
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

      // Check if the type is valid
      if (field != null)
      {
         boolean valid = false;

         if (field.isBuiltinType())
         {
            valid = true;
         }
         else
         {
            for (MsgContext otherMsg : discoveredMsgs)
            {
               if (otherMsg.matches(field.getType()))
               {
                  valid = true;
                  // Set the javaType to the discovered MsgContext
                  field.javaType(String.format("%s.%s", otherMsg.getJavaPackageName(), otherMsg.getName()));
                  break;
               }
            }
         }

         if (!valid)
         {
            throw new RuntimeException(String.format("Failed to parse: %s. Invalid field type %s.", fileName, field.getType()));
         }
      }

      return field;
   }
}
