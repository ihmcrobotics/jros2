package us.ihmc.jros2.interfaces;

import org.stringtemplate.v4.ST;
import us.ihmc.jros2.interfaces.context.Field;
import us.ihmc.jros2.interfaces.context.MsgContext;

import java.util.ArrayList;
import java.util.List;

public class ROS2MessageGenerator
{
   /*
      Iterate through all messages in every package and subpackage recursively in ros2_interfaces dir

      Add all messages to some data structure before parsing, so they can reference each other. We can
      error if a declared message type in a .msg field doesn't exist.



    */

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

   public static void generate(MsgContext context)
   {
      List<Field> fields = new ArrayList<>(context.getFields().values());

      ST st = new ST(template);
      st.add("fields", fields);
      st.add("name", context.getName());
      st.add("className", context.getJavaClassName());
      System.out.println(st.render());
   }

   public static void main(String[] args)
   {
      MsgContext context = new MsgContext("test_pkg", "TestMsg.msg");

      context.parse(TEST_MSG);

      generate(context);

      //      System.out.println(context);
   }
}
