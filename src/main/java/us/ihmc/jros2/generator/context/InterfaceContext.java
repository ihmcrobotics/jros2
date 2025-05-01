package us.ihmc.jros2.generator.context;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class InterfaceContext
{
   // https://docs.ros.org/en/humble/Concepts/Basic/About-Interfaces.html#field-types
   private static final String[] BUILT_IN_TYPES = new String[] {"bool",
                                                                "byte",
                                                                "char",
                                                                "float32",
                                                                "float64",
                                                                "int8",
                                                                "uint8",
                                                                "int16",
                                                                "uint16",
                                                                "int32",
                                                                "uint32",
                                                                "int64",
                                                                "uint64",
                                                                "string",
                                                                "wstring"};
   private static final String SECTION_SEPARATOR = "---";

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
               lineTokens = Arrays.copyOfRange(lineTokens, 0, i - 1);
               break;
            }
         }

         switch (lineTokens.length)
         {
            case 0:
               break;
            case 1:
               if (lineTokens[0].equals(SECTION_SEPARATOR))
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

   private static final Pattern STRING_WSTRING_PATTERN = Pattern.compile("^(?<strtype>string|wstring)(?<strlen><=\\d+)?(?<arr>\\[(?<seqbounds><=)?(?<len>\\d+)?])?$");
   private static final Pattern TYPE_PATTERN = Pattern.compile("^(?<type>[a-zA-Z0-9]+)(?<arr>\\[(?<seqbounds><=)?(?<length>\\d+)?])?$");

   private static InterfaceField parseField(String[] lineTokens, Map<String, MsgContext> discoveredTypes)
   {
      Matcher string_wstring_matcher = STRING_WSTRING_PATTERN.matcher(lineTokens[0]);

      // Handle string or wstring field
      if (string_wstring_matcher.matches())
      {
         // Example: wstring<=10[<=4]
         String stringTypeStr = string_wstring_matcher.group("strtype"); // e.g. wstring
         String stringLengthStr = string_wstring_matcher.group("strlen"); // e.g. <=10
         String arrayStr = string_wstring_matcher.group("arr"); // e.g. [<=4]
         String sequenceBoundsStr = string_wstring_matcher.group("seqbounds"); // e.g. <=
         String lengthStr = string_wstring_matcher.group("len"); // e.g. 4




      }

      Matcher typeMatcher = TYPE_PATTERN.matcher(lineTokens[0]);

      if (typeMatcher.matches())
      {
         // Example: MyCustomType[<=4]
         String typeStr = typeMatcher.group("type"); // e.g. MyCustomType
         String arrayStr = typeMatcher.group("arr"); // e.g. [<=4]
         String sequenceBoundsStr = typeMatcher.group("seqbounds"); // e.g. <=
         String lengthStr = typeMatcher.group("length"); // e.g. 4
      }

      return null;
   }
}
