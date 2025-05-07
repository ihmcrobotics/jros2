package us.ihmc.jros2.generator;

import org.stringtemplate.v4.ST;
import us.ihmc.jros2.generator.context.MsgContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class ROS2MessageGenerator
{
   private final Map<String, MsgContext> msgs;

   public ROS2MessageGenerator(Path... ros2pkgPathsToInclude)
   {
      msgs = new LinkedHashMap<>();

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

            msgs.put(context.getName(), context);
         }
      }

      msgs.forEach((msgName, msgContext) -> msgContext.parse(msgs));
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

      MsgContext msgContext = new MsgContext("test_msgs", "Test.msg", TEST_MSG);


      messageGenerator.generate(msgContext);
   }

   public void generate(MsgContext context)
   {
      context.parse(msgs);

      ST st = new ST(template);
      st.add("context", context);
      System.out.println(st.render());
   }

   private static final String template = """
         package us.ihmc.jros2.msg;
                  
         import us.ihmc.fastddsjava.cdr.CDRBuffer;
         import us.ihmc.jros2.ROS2Message;
                  
         public class <context.name> implements ROS2Message
         {
            public static final String name = "<context.ROS2PackageName>::msg::dds_::<context.name>";
                  
            <context.fields:{ field |
         private <field.javaType><if(field.array&&field.fixedSize)>[]<endif> <field.name>_;
            }>
            public <context.name>
            {
               <context.fields:{ field |
               <if(field.array)>
         <field.name>_ = new <field.javaType><if(field.fixedSize)>[<field.length>]<else>()<endif>;
               <endif>
               }>
            }
            
            @Override
            public int calculateSizeBytes(int currentAlignment)
            {
               int initialAlignment = currentAlignment;
               
               <context.fields:{ field |
               <if(field.builtinType)>
               <if(field.array&&field.fixedSize)>
         currentAlignment += (<field.length> * <field.builtinTypeSize>) + CDRBuffer.alignment(currentAlignment, (<field.length> * <field.builtinTypeSize>)); // <field.name>_
               <elseif(field.array&&!field.fixedSize)>
         currentAlignment += <field.name>_.calculateSizeBytes(currentAlignment);
               <elseif(!field.array)>
         currentAlignment += <field.builtinTypeSize> + CDRBuffer.alignment(currentAlignment, <field.builtinTypeSize>); // <field.name>_
               <endif>
               <endif>
               }>
               return currentAlignment - initialAlignment;
            }
                  
            @Override
            public void serialize(CDRBuffer buffer)
            {
               <context.fields:{ field |
               <if(field.builtinType)>
               <if(field.array&&field.fixedSize)>
         <! TODO: direct array to buffer copy instead of iterating !>
         for (int i = 0; i \\< <field.name>_.length; ++i)
         {
            buffer.<field.builtinCDRBufferWriteMethod>(<field.name>_[i]);
         \\}
               <elseif(field.array&&!field.fixedSize)>
         <field.name>_.serialize(buffer);
               <elseif(!field.array)>
         buffer.<field.builtinCDRBufferWriteMethod>(<field.name>_);
               <endif>
               <endif>
               }>
            }
                  
            @Override
            public void deserialize(CDRBuffer buffer)
            {
               <context.fields:{ field |
               <if(field.builtinType)>
               <if(field.array&&field.fixedSize)>
         <! TODO: direct buffer to array copy instead of iterating !>
         for (int i = 0; i \\< <field.name>_.length; ++i)
         {
            <field.name>_[i] = buffer.<field.builtinCDRBufferReadMethod>;
         \\}
               <elseif(field.array&&!field.fixedSize)>
         <field.name>_.deserialize(buffer);
               <elseif(!field.array)>
         <field.name>_ = buffer.<field.builtinCDRBufferReadMethod>;
               <endif>
               <endif>
               }>
            }
                  
            @Override
            public String getName()
            {
               return name;
            }
                  
            @Override
            public void set(<context.name> from)
            {
               <context.fields:{ field |
               <if(field.builtinType)>
               <if(field.array&&field.fixedSize)>
         <! TODO: Use more efficient array copy !>
         for (int i = 0; i \\< <field.name>_.length; ++i)
         {
            <field.name>_[i] = from.<field.name>_[i];
         \\}
               <elseif(field.array&&!field.fixedSize)>
         <field.name>_.set(from.<field.name>_);
               <elseif(!field.array)>
         <field.name> = from.<field.name>_;
               <endif>
               <endif>
               }>
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
         uint8[<=234] field3
         float32[] field4 # Some comment
                  
           uint8 field5
                  
         # Some comment
                  
                  
         """;
}
