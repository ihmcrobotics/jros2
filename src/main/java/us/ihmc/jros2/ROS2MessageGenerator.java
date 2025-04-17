package us.ihmc.jros2;

import org.stringtemplate.v4.ST;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

   public static class Field
   {
      public String type;
      public String name;
      public boolean array;
      public boolean upperBounded;
      public boolean unbounded;
      public int length;

      public Field(String type, String name, boolean array, boolean upperBounded, boolean unbounded, int length)
      {
         this.type = type;
         this.name = name;
         this.array = array;
         this.upperBounded = upperBounded;
         this.unbounded = unbounded;
         this.length = length;
      }

      @Override
      public String toString()
      {
         return "Field{" + "type='" + type + '\'' + ", name='" + name + '\'' + ", array=" + array + ", upperBounded=" + upperBounded + ", unbounded="
                + unbounded + ", length=" + length + '}';
      }

      public String getName()
      {
         return name;
      }
   }

   private static class MessageContext
   {
      public String name;
      public String className;
      // TODO constants
      public Map<String, Field> fields = new HashMap<>();

      @Override
      public String toString()
      {
         return "MessageContext{" + "name='" + name + '\'' + ", fields=" + fields + '}';
      }
   }

   private static final String TEST_MSG = """
         # Some comment
         # Some comment
         # Some comment
         # Some comment
         # Some comment
         # Some comment
                  
                  
         bool[1] field1
         bool[<=3] field2
         nontype[<=234] field3
         float32[] field4 # Some comment
                  
           uint8 field5
                  
         # Some comment
                  
                  
         """;

   private static void parse(String content, MessageContext context)
   {
      String[] tokens = content.replace("\n", " <NEWLINE> ").trim().split("\\s+");

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

               context.fields.put(fieldName, field);
            }
         }
      }
   }

   private static final String template = """
         package us.ihmc.jros2.msg;
                  
         import us.ihmc.fastddsjava.cdr.CDRBuffer;
         import us.ihmc.jros2.ROS2Message;
                  
         public class <className> implements ROS2Message<<className>>
         {
            public static final String name = "std_msgs::msg::dds_::Bool_";
                  
            <fields:{ field | private <field.type> <field.name>_;\n }>
            @Override
            public int calculateSizeBytes(int currentAlignment)
            {
               int initialAlignment = currentAlignment;
                  
               currentAlignment += 1 + CDRBuffer.alignment(currentAlignment, 1); // 1 byte for data
                  
               return currentAlignment - initialAlignment;
            }
                  
            @Override
            public void serialize(CDRBuffer buffer)
            {
               buffer.writeBoolean(data_);
            }
                  
            @Override
            public void deserialize(CDRBuffer buffer)
            {
               data_ = buffer.readBoolean();
            }
                  
            @Override
            public String getName()
            {
               return name;
            }
                  
            @Override
            public void set(<className> from)
            {
               data_ = from.data_;
            }
         }
         """;

   public static void generate(MessageContext context)
   {
      List<Field> fields = new ArrayList<>(context.fields.values());

      ST st = new ST(template);
      st.add("fields", fields);
      st.add("name", context.name);
      st.add("className", context.className);
      System.out.println(st.render());
   }

   public static void main(String[] args)
   {
      MessageContext context = new MessageContext();
      context.name = "TestMsg.msg";
      context.className = "TestMsg";

      parse(TEST_MSG, context);

      generate(context);

      //      System.out.println(context);
   }
}
