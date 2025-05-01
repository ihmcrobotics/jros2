package us.ihmc.jros2.generator.context;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class InterfaceContext
{
   private final String ros2packageName;
   private final String name; // Includes extension (e.g. <.msg, .srv, .action>)
   private final String fileContent;

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

      for (String line : lines)
      {
         String[] lineTokens = line.split("\\s+(?![^\\[]*\\])"); // TODO:

         // Remove all tokens that come after a comment token
         for (int i = 0; i < lineTokens.length; i++)
         {
            if (lineTokens[i].startsWith("#"))
            {
               lineTokens = Arrays.copyOfRange(lineTokens, 0, Math.max(i - 1, 0));
               break;
            }
         }

         switch (lineTokens.length)
         {
            case 0:
               break;
            case 1:
               if (lineTokens[0].equals("---")) // Section separator (valid for services and actions)
               {
                  onSection();
               }
               break;
            case 2:
            case 3:
               // Field, field with default value, or constant field
               InterfaceField field = parseField(lineTokens, discoveredTypes);

               if (field != null)
               {
                  onField(field);
               }

               break;
         }
      }
   }

   private static final Pattern STRING_WSTRING_PATTERN = Pattern.compile(
         "^(?<strtype>string|wstring)(?<strlen><=\\d+)?(?<arr>\\[(?<seqbounds><=)?(?<len>\\d+)?])?$");
   private static final Pattern TYPE_PATTERN = Pattern.compile("^(?<type>[a-zA-Z0-9]+)(?<arr>\\[(?<seqbounds><=)?(?<length>\\d+)?])?$");

   private static InterfaceField parseField(String[] lineTokens, Map<String, MsgContext> discoveredTypes)
   {
      Matcher string_wstring_matcher = STRING_WSTRING_PATTERN.matcher(lineTokens[0]);

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

         field = new InterfaceField();
         field.type(stringTypeStr);
         field.stringLength(lengthStr == null ? -1 : Integer.parseInt(stringLengthStr));
         field.array(arrayStr != null);
         field.upperBounded(sequenceBoundsStr != null);
         field.length(lengthStr == null ? -1 : Integer.parseInt(lengthStr));
      }

      Matcher typeMatcher = TYPE_PATTERN.matcher(lineTokens[0]);

      if (typeMatcher.matches())
      {
         // Example: MyCustomType[<=4]
         String typeStr = typeMatcher.group("type"); // e.g. MyCustomType
         String arrayStr = typeMatcher.group("arr"); // e.g. [<=4]
         String sequenceBoundsStr = typeMatcher.group("seqbounds"); // e.g. <=
         String lengthStr = typeMatcher.group("length"); // e.g. 4

         field = new InterfaceField();
         field.type(typeStr);
         field.array(arrayStr != null);
         field.upperBounded(sequenceBoundsStr != null);
         field.length(lengthStr == null ? -1 : Integer.parseInt(lengthStr));
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
