package us.ihmc.jros2.generator;

import org.stringtemplate.v4.ST;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.jros2.ROS2Message;
import us.ihmc.jros2.generator.context.InterfaceField;
import us.ihmc.jros2.generator.context.MsgContext;
import us.ihmc.log.LogTools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ROS2MessageGenerator
{
   private final List<MsgContext> msgs;
   private final Map<String, Class<? extends ROS2Message<?>>> fieldTypeJavaClass;

   public ROS2MessageGenerator(Path... ros2pkgPathsToInclude)
   {
      msgs = new LinkedList<>();
      fieldTypeJavaClass = new HashMap<>();

      for (Path ros2pkgPath : ros2pkgPathsToInclude)
      {
         findMsgsInPkg(ros2pkgPath);
      }

      for (MsgContext context : msgs)
      {
         context.parse(msgs);
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

      for (File file : Objects.requireNonNull(msgDir.listFiles((f, name) -> name.endsWith(".msg"))))
      {
         String fileContent;

         try
         {
            fileContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
         }
         catch (IOException e)
         {
            throw new RuntimeException("Could not read .msg file:  " + file.getName());
         }

         MsgContext context = new MsgContext(ros2pkgPath.toFile().getName(), file.getName(), fileContent);

         msgs.add(context);
      }
   }

   /**
    * When generating {@link ROS2Message} classes, the generator will use the fully qualified path of the class
    * provided instead of inferring the class.
    * // TODO: Add a wiki page on github for how to use this
    *
    * @param fieldType the field type to associate
    * @param clazz     the class to be used in the generated Java for the field type
    */
   public void registerJavaClass(String fieldType, Class<? extends ROS2Message<?>> clazz)
   {
      fieldTypeJavaClass.put(fieldType, clazz);
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

      messageGenerator.registerJavaClass("Bool", CustomBool.class);

      MsgContext msgContext = new MsgContext("test_msgs", "Test.msg", TEST_MSG);

      messageGenerator.generate(msgContext);
   }

   public static class CustomBool implements ROS2Message<CustomBool>
   {
      @Override
      public int calculateSizeBytes(int currentAlignment)
      {
         return 0;
      }

      @Override
      public void serialize(CDRBuffer buffer)
      {

      }

      @Override
      public void deserialize(CDRBuffer buffer)
      {

      }

      @Override
      public String getName()
      {
         return null;
      }

      @Override
      public void set(CustomBool from)
      {

      }
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

      // Handle custom Java classes for fields
      for (String fieldType : fieldTypeJavaClass.keySet())
      {
         for (InterfaceField field : context.getFields())
         {
            if (field.getType().equals(fieldType))
            {
               field.javaType(fieldTypeJavaClass.get(fieldType).getCanonicalName());
            }
         }
      }

      ST st = new ST(template);
      st.add("context", context);
      System.out.println(st.render());
   }

   private static final String TEST_MSG = """
         # msg header line 1
         # msg header line 2
         # msg header line 3
                  
         # field1 header comment
         # field1 header comment (line 2)
         bool[1] field1
         bool[<=3] field2
         uint8[<=234] field3
         float32[] field4 # Some comment
         Bool test_bool
                  
           uint8 field5
                  
         # Some comment
                  
                  
         """;
}
