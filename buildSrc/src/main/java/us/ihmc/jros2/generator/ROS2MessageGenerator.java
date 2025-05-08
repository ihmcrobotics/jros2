package us.ihmc.jros2.generator;

import org.stringtemplate.v4.ST;
import us.ihmc.jros2.generator.context.InterfaceField;
import us.ihmc.jros2.generator.context.MsgContext;

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
   private final Path packagePath;
   private final Path outputPath;
   private final List<MsgContext> msgs;
   private final Map<String, Class<?>> fieldTypeJavaClass;

   public ROS2MessageGenerator(Path packagePath, Path outputPath, Path... ros2pkgPathsToInclude)
   {
      this.packagePath = packagePath;
      this.outputPath = outputPath;
      msgs = new LinkedList<>();
      fieldTypeJavaClass = new HashMap<>();

      for (Path ros2pkgPath : ros2pkgPathsToInclude)
      {
         msgs.addAll(findMsgsInPkg(ros2pkgPath));
      }

      for (MsgContext context : msgs)
      {
         context.parse(msgs);
      }
   }

   private List<MsgContext> findMsgsInPkg(Path ros2pkgPath)
   {
      List<MsgContext> msgs = new LinkedList<>();

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

      return msgs;
   }

   /**
    * When generating Java code, the generator will use the fully qualified path of the class
    * provided instead of inferring the class.
    * // TODO: Add a wiki page on github for how to use this
    *
    * @param fieldType the field type to associate
    * @param clazz     the class to be used in the generated Java for the field type
    */
   public void registerJavaClass(String fieldType, Class<?> clazz)
   {
      fieldTypeJavaClass.put(fieldType, clazz);
   }

   public void generate()
   {
      List<MsgContext> msgs = findMsgsInPkg(packagePath);

      for (MsgContext context : msgs)
      {
         generate(context);
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
         e.printStackTrace();
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

      String fileContent = st.render();
      File msgFile = new File(outputPath.toFile(), context.getFileName());

      if (msgFile.exists())
      {
         msgFile.delete();
      }

      try
      {
         msgFile.getParentFile().mkdirs();
         msgFile.createNewFile();
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }

      Path outputFile = outputPath.resolve(context.getName() + ".java");

      try
      {
         Files.writeString(outputFile, fileContent, StandardCharsets.UTF_8);
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }

      System.out.println("Generated " + outputFile.toFile().getAbsolutePath());
   }
}
