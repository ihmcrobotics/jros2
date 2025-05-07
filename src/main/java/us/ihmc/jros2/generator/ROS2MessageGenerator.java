package us.ihmc.jros2.generator;

import org.stringtemplate.v4.ST;
import us.ihmc.jros2.generator.context.MsgContext;
import us.ihmc.log.LogTools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

      String template = null;

      try (InputStream stream = ROS2MessageGenerator.class.getClassLoader().getResourceAsStream("ROS2Message.st"))
      {
         if (stream != null)
         {
            template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
         }
      }
      catch (IOException e)
      {
         LogTools.error(e);
      }

      if (template == null)
      {
         return;
      }

      ST st = new ST(template);
      st.add("context", context);
      System.out.println(st.render());
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
         uint8[<=234] field3
         float32[] field4 # Some comment
         Bool test_bool
                  
           uint8 field5
                  
         # Some comment
                  
                  
         """;
}
