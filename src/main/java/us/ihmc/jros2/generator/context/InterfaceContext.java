package us.ihmc.jros2.generator.context;

import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class InterfaceContext
{
   private final String ros2packageName;
   private final String name; // Includes extension (i.e. <.msg, .srv, .action>)
   private final String fileContent;
   private String headerComment;

   public InterfaceContext(String packageName, String name, String fileContent)
   {
      this.ros2packageName = packageName;
      this.name = name;
      this.fileContent = fileContent;
   }

   public String getROS2PackageName()
   {
      return ros2packageName;
   }

   public String getName()
   {
      return name;
   }

   public String getFileContent()
   {
      return fileContent;
   }

   public String getHeaderComment()
   {
      return headerComment;
   }

   public String getNameWithoutExtension()
   {
      return getName().substring(0, getName().indexOf("."));
   }

   public String getJavaClassName()
   {
      return getNameWithoutExtension();
   }

   protected abstract void onSection();

   protected abstract void onField(InterfaceField field);

   public void parse(Map<String, MsgContext> discoveredTypes)
   {
      String[] lines = fileContent.split("\\R");

      StringJoiner commentLines = new StringJoiner("\n");

      for (String line : lines)
      {
         // Remove leading or trailing whitespace
         line = line.trim();

         String trailingComment = null;

         // If the entire line is a comment
         if (line.startsWith("#"))
         {
            commentLines.add(line);
            continue;
         }

         // If the line contains a comment, but it isn't the entire line
         if (line.contains("#"))
         {
            String lineWithCommentRemoved = line.substring(0, line.indexOf("#"));
            trailingComment = line.substring(line.indexOf("#"));
            line = lineWithCommentRemoved;
         }

         // If the line is a section separator
         if (line.equals("---"))
         {
            onSection();
         }
         else
         {
            InterfaceField field = parseField(line, commentLines.toString(), trailingComment, discoveredTypes);
            commentLines = new StringJoiner("\n");
            if (field != null)
            {
               onField(field);
            }
         }
      }
   }

   private static final Pattern STRING_WSTRING_TYPE_PATTERN = Pattern.compile(
         "^(?<strtype>string|wstring)(?<strlen><=\\d+)?(?<arr>\\[(?<seqbounds><=)?(?<len>\\d+)?])?$");
   private static final Pattern TYPE_PATTERN = Pattern.compile("^(?<type>[a-zA-Z0-9]+)(?<arr>\\[(?<seqbounds><=)?(?<len>\\d+)?])? (?<fname>[a-z](?!.*__)[a-z0-9_]*(?<!_))(\\s*=\\s*(?<constval>.+)|\\s(?<defval>.+))?$");

   private static InterfaceField parseField(String line, String headerComment, String trailingComment, Map<String, MsgContext> discoveredTypes)
   {
      Matcher string_wstring_matcher = STRING_WSTRING_TYPE_PATTERN.matcher(line);

      InterfaceField field = null;

      // Handle string or wstring field
      if (string_wstring_matcher.matches())
      {
         // Example: wstring<=10[<=4]
         String stringTypeStr = string_wstring_matcher.group("strtype"); // e.g. wstring
         String stringLengthStr = string_wstring_matcher.group("strlen"); // e.g. <=10
         String arrayStr = string_wstring_matcher.group("arr"); // e.g. [<=4]
         String sequenceBoundsStr = string_wstring_matcher.group("seqbounds"); // e.g. <=
         String lengthStr = string_wstring_matcher.group("len"); // e.g. 4

         // TODO
         field = new InterfaceField();
         field.type(stringTypeStr);
         field.stringLength(lengthStr == null ? -1 : Integer.parseInt(stringLengthStr));
         field.array(arrayStr != null);
         field.upperBounded(sequenceBoundsStr != null);
         field.length(lengthStr == null ? -1 : Integer.parseInt(lengthStr));
         field.headerComment(headerComment);
         field.trailingComment(trailingComment);
      }

      Matcher typeMatcher = TYPE_PATTERN.matcher(line);

      if (typeMatcher.matches())
      {
         // Example: MyCustomType[<=4] my_type = {data: 1}
         String typeStr = typeMatcher.group("type"); // MyCustomType
         String arrayStr = typeMatcher.group("arr"); // [<=4]
         String sequenceBoundsStr = typeMatcher.group("seqbounds"); // <=
         String lengthStr = typeMatcher.group("len"); // 4
         String fieldNameStr = typeMatcher.group("fname"); // my_type
         String constValStr = typeMatcher.group("constval"); // {data: 1}

         field = new InterfaceField();
         field.type(typeStr);
         field.array(arrayStr != null);
         field.upperBounded(sequenceBoundsStr != null);
         field.length(lengthStr == null ? -1 : Integer.parseInt(lengthStr));
         field.name(fieldNameStr);
         field.constantValue(constValStr);
         field.headerComment(headerComment);
         field.trailingComment(trailingComment);
      }

      // Check if the type is valid
      if (field != null)
      {
         boolean valid = false;

         switch (field.getType())
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
               valid = true;
               break;
            default:
               for (MsgContext otherMsg : discoveredTypes.values())
               {
                  if (field.getType().equals(otherMsg.getNameWithoutExtension()))
                  {
                     valid = true;
                     break;
                  }
               }
         }

         if (!valid)
         {
            // TODO: Replace with custom exception
            throw new RuntimeException("Invalid message field type: " + field.getType());
         }
      }

      return field;
   }
}
