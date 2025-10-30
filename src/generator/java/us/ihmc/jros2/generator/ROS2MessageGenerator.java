/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.jros2.generator;

import org.stringtemplate.v4.ST;
import us.ihmc.jros2.generator.context.InterfaceField;
import us.ihmc.jros2.generator.context.MsgContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static us.ihmc.jros2.generator.ROS2InterfaceUtil.findMsgsInPkg;
import static us.ihmc.jros2.generator.ROS2InterfaceUtil.findMsgsInResources;

public class ROS2MessageGenerator
{
   private final Path outputPath;
   private final Map<String, String> typeToClass;
   private final List<MsgContext> msgs;

   public ROS2MessageGenerator(Path packagePath, Path outputPath, Map<String, String> typeToClass, List<String> ros2pkgPathsToInclude)
   {
      this.outputPath = outputPath;
      this.typeToClass = typeToClass;

      msgs = new LinkedList<>();

      for (String ros2pkgPathStr : ros2pkgPathsToInclude)
      {
         Path ros2pkgPath = Path.of(ros2pkgPathStr);

         msgs.addAll(findMsgsInPkg(ros2pkgPath));
      }

      List<MsgContext> commonMsgs = new LinkedList<>(findMsgsInResources());

      List<MsgContext> allMsgs = new LinkedList<>();
      allMsgs.addAll(msgs);
      allMsgs.addAll(commonMsgs);

      for (MsgContext context : msgs)
      {
         context.parse(allMsgs);
      }
   }

   public void generate()
   {
      for (MsgContext context : msgs)
      {
         if (typeToClass.containsKey(context.getFullName()))
         {
            String className = typeToClass.get(context.getFullName());

            System.out.println("[Custom class]\n" + context.getFullName() + " is mapped to " + className + " (not generating for it)");
         }
         else
         {
            generate(context);
         }
      }
   }

   public void generate(MsgContext context)
   {
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

      for (String type : typeToClass.keySet())
      {
         for (InterfaceField field : context.getFields())
         {
            if (field.getType().equals(type))
            {
               field.javaType(typeToClass.get(type));
            }
         }
      }

      ST st = new ST(template);
      st.add("context", context);

      Path outputFilePath = outputPath.resolve(context.getJavaPackageName().replace(".", "/") + "/" + context.getName() + ".java");

      if (outputFilePath.toFile().exists())
      {
         outputFilePath.toFile().delete();
      }
      outputFilePath.toFile().getParentFile().mkdirs();

      try
      {
         Files.writeString(outputFilePath, st.render(), StandardCharsets.UTF_8);
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }

      System.out.println("Generated " + outputFilePath.toFile().getAbsolutePath());
   }
}
