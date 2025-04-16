package us.ihmc.jros2;

import java.io.IOException;
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

   private record Field(String type, String name, boolean array, boolean upperBounded, boolean unbounded, int length)
   {
      @Override
      public String toString()
      {
         return "Field{" + "type='" + type + '\'' + ", name='" + name + '\'' + ", array=" + array + ", upperBounded=" + upperBounded + ", unbounded="
                + unbounded + ", length=" + length + '}';
      }
   }

   private static final String TEST_MSG = """
         # Some comment
         # Some comment
         # Some comment
         # Some comment
         # Some comment
         # Some comment
                  
                  
         bool[<=3] some_bool
                  
         """;

   private static void parse()
   {
      String messageContent = TEST_MSG;

      String[] tokens = messageContent.replace("\n", " <NEWLINE> ").trim().split("\\s+");

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

               String fieldName = tokens[i + 1];

               Field field = new Field(type, fieldName, array, upperBounded, unbounded, length);

               System.out.println(field);
            }
         }
      }
   }

   public static void main(String[] args) throws IOException
   {
      parse();
   }
}
