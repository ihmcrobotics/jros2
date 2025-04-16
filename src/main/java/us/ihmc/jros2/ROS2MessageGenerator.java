package us.ihmc.jros2;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ROS2MessageGenerator
{
   private static final String[] TYPES = new String[] {"bool",
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

   public static void main(String[] args) throws IOException
   {
      File testFile = new File("Bool.msg");

      if (!testFile.exists())
      {
         return;
      }

      String testFileContent = Files.readString(testFile.toPath());

      String[] tokens = testFileContent.replace("\n", " <NEWLINE> ").trim().split("\\s+");

      for (int i = 0; i < tokens.length; ++i)
      {
         String token = tokens[i];

         // Skip new lines
         if (token.equals("<NEWLINE>"))
         {
            continue;
         }

         // Skip comments
         if (token.startsWith("#"))
         {
            // Find the next <NEWLINE> token
            int j = 0;
            while ((i + j) < tokens.length && !tokens[i + j].equals("<NEWLINE>"))
            {
               ++j;
            }
            i = i + j;
            continue;
         }

         for (String type : TYPES)
         {
            Pattern pattern = Pattern.compile(type + "(\\[(<=)?\\d*])?$");
            Matcher matcher = pattern.matcher(token);

            if (matcher.matches())
            {
               boolean arr = token.contains("[");
               boolean seq = false;
               boolean unbounded = false;
               int length = 0;

               if (arr)
               {
                  String boundOp = matcher.group(2);
                  String lengthStr = matcher.group(3);
                  length = Integer.parseInt(lengthStr); // TODO: NumberFormatException check

                  if (boundOp != null)
                  {
                     // It's a bounded sequence, not a fixed array
                  }
               }

               String fieldName = tokens[i + 1];

               System.out.println("field: " + fieldName + " (" + type + ")");
            }
         }
      }
   }
}
