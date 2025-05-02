package us.ihmc.jros2.generator;

import org.stringtemplate.v4.ST;
import us.ihmc.jros2.generator.context.InterfaceField;
import us.ihmc.jros2.generator.context.MsgContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ROS2MessageGenerator
{
   /*
      Iterate through all messages in every package and subpackage recursively in ros2_interfaces dir

      Add all messages to some data structure before parsing, so they can reference each other. We can
      error if a declared message type in a .msg field doesn't exist.



    */

   private final Map<String, MsgContext> msgs;

   public ROS2MessageGenerator(Path... ros2pkgPathsToInclude)
   {
      msgs = new HashMap<>();

      for (Path ros2pkgPath : ros2pkgPathsToInclude)
      {
         findMsgsInPkg(ros2pkgPath);
      }
   }

   private void findMsgsInPkg(Path ros2pkgPath)
   {
      if (!ros2pkgPath.resolve("package.xml").toFile().exists())
      {
         throw new RuntimeException(ros2pkgPath + " is not a ROS 2 package path");
      }

      File msgDir = ros2pkgPath.resolve("msg").toFile();

      if (!msgDir.exists() || !msgDir.isDirectory())
      {
         throw new RuntimeException(ros2pkgPath + " does not contain a msg directory");
      }

      for (File file : Objects.requireNonNull(msgDir.listFiles()))
      {
         if (file.isFile() && file.getName().endsWith(".msg"))
         {
            String fileContent;

            try
            {
               fileContent = Files.readString(file.toPath());
            }
            catch (IOException e)
            {
               throw new RuntimeException("Could not read .msg file:  " + file.getName());
            }

            MsgContext context = new MsgContext(ros2pkgPath.toFile().getName(), file.getName(), fileContent);

            msgs.put(file.getName(), context);
         }
      }
   }

   public static void main(String[] args)
   {
      ROS2MessageGenerator messageGenerator = new ROS2MessageGenerator(Path.of(new File("ros2_interfaces/common_interfaces/actionlib_msgs").toURI()),
                                                                       Path.of(new File("ros2_interfaces/common_interfaces/diagnostic_msgs").toURI()),
                                                                       Path.of(new File("ros2_interfaces/common_interfaces/geometry_msgs").toURI()),
                                                                       Path.of(new File("ros2_interfaces/common_interfaces/nav_msgs").toURI()),
                                                                       Path.of(new File("ros2_interfaces/common_interfaces/sensor_msgs").toURI()),
                                                                       Path.of(new File("ros2_interfaces/common_interfaces/shape_msgs").toURI()),
                                                                       Path.of(new File("ros2_interfaces/common_interfaces/std_msgs").toURI()),
                                                                       Path.of(new File("ros2_interfaces/common_interfaces/stereo_msgs").toURI()),
                                                                       Path.of(new File("ros2_interfaces/common_interfaces/trajectory_msgs").toURI()),
                                                                       Path.of(new File("ros2_interfaces/common_interfaces/visualization_msgs").toURI()));


      messageGenerator.msgs.forEach((s, msgContext) -> {
         msgContext.parse(messageGenerator.msgs);
         msgContext.getFields().forEach((s1, interfaceField) -> {
            System.out.println(interfaceField.getHeaderComment());
            System.out.println(interfaceField.getType() + " " + interfaceField.getName());
         });
      });

   }

   public static void generate(MsgContext context)
   {
      List<InterfaceField> fields = new ArrayList<>(context.getFields().values());

      ST st = new ST(template);
      st.add("fields", fields);
      st.add("name", context.getName());
      st.add("className", context.getJavaClassName());
      System.out.println(st.render());
   }

   private static final String template = """
         package us.ihmc.jros2.msg;
                  
         import us.ihmc.fastddsjava.cdr.CDRBuffer;
         import us.ihmc.jros2.ROS2Message;
                  
         public class <className> implements ROS2Message<<className>>
         {
            public static final String name = "std_msgs::msg::dds_::Bool_";
                  
            <<fields:{ field | private <field.type> <field.name>_;\n }>>
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
}
