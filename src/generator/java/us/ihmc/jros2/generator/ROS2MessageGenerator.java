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
import us.ihmc.jros2.parser.MsgContext;
import us.ihmc.jros2.parser.field.InterfaceField;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Java code generator from a ROS2 Message schema.
 */
public class ROS2MessageGenerator
{
   /**
    * Generate a Java class string from a {@link MsgContext}
    *
    * @param context     the ROS Message in {@link MsgContext} form
    * @param typeToClass a mapping of ROS2 Message type to Java class name for custom written ROS2 Java Message classes.
    *                    Pass an empty Map if not required.
    * @return the generated Java code as a string
    */
   public static String generateJavaClassContents(MsgContext context, Map<String, String> typeToClass)
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
         return "";
      }

      for (String type : typeToClass.keySet())
      {
         for (InterfaceField field : context.getFields().values())
         {
            if (field.getType().equals(type))
            {
               field.javaType(typeToClass.get(type));
            }
         }
      }

      ST st = new ST(template);
      st.add("context", context);
      return st.render();
   }
}
