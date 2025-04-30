package us.ihmc.jros2.generator.context;

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

   protected abstract void onToken(String[] tokens, int position);

   protected abstract void onField(Field field);

   public void parse(String content)
   {
      String[] tokens = content.replace("\n", " <NEWLINE> ").trim().split("\\s+");

      for (int position = 0; position < tokens.length; ++position)
      {
         // Skip new lines
         if (tokens[position].equals("<NEWLINE>"))
         {
            continue;
         }

         // Skip comments
         if (tokens[position].startsWith("#"))
         {
            // Find the next <NEWLINE> token
            int j = 0;
            while ((position + j) < tokens.length && !tokens[position + j].equals("<NEWLINE>"))
            {
               ++j;
            }
            position = position + j;
            continue;
         }

         onToken(tokens, position);

         Field field = parseField(tokens, position);

         if (field != null)
         {
            onField(field);
         }
      }
   }

   private static Field parseField(String[] tokens, int position)
   {
      String token = tokens[position];

      for (String type : BUILT_IN_TYPES)
      {
         Pattern pattern = Pattern.compile(type + "(\\[(<=)?(\\d*)?])?$");
         Matcher matcher = pattern.matcher(token);

         if (matcher.matches())
         {
            boolean array = token.contains("[");
            boolean upperBounded = false;
            int length = 0;
            boolean unbounded = false;

            if (array)
            {
               String boundOp = matcher.group(2);
               String lengthStr = matcher.group(3);

               if (lengthStr != null && !lengthStr.isEmpty())
               {
                  try
                  {
                     length = Integer.parseInt(lengthStr);
                  }
                  catch (NumberFormatException ignored)
                  {
                  }
               }
               else
               {
                  unbounded = true;
               }

               if (boundOp != null)
               {
                  upperBounded = true;
               }
            }

            String fieldName = tokens[position + 1];

            return new Field(type, fieldName, array, upperBounded, unbounded, length);
         }
      }

      return null;
   }
}
