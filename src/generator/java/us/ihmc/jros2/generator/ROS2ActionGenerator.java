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
import us.ihmc.jros2.parser.ActionContext;
import us.ihmc.jros2.parser.field.InterfaceField;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Java code generator from a ROS2 Action schema.
 */
public class ROS2ActionGenerator
{
   /**
    * Generate Java class strings (Goal, Result, and Feedback) from an {@link ActionContext}
    *
    * @param context     the ROS Action in {@link ActionContext} form
    * @param typeToClass a mapping of ROS2 Message type to Java class name for custom written ROS2 Java Message classes.
    *                    Pass an empty Map if not required.
    * @return an array containing [goalClassContent, resultClassContent, feedbackClassContent]
    */
   public static String[] generateJavaClassContents(ActionContext context, Map<String, String> typeToClass)
   {
      String template = loadTemplate("ROS2ServiceRequest.st"); // Reuse the same template

      if (template == null)
      {
         return new String[]{"", "", ""};
      }

      // Apply custom type mappings to goal fields
      for (String type : typeToClass.keySet())
      {
         for (InterfaceField field : context.getGoalContext().getFields().values())
         {
            if (field.getType().equals(type))
            {
               field.javaType(typeToClass.get(type));
            }
         }
      }

      // Apply custom type mappings to result fields
      for (String type : typeToClass.keySet())
      {
         for (InterfaceField field : context.getResultContext().getFields().values())
         {
            if (field.getType().equals(type))
            {
               field.javaType(typeToClass.get(type));
            }
         }
      }

      // Apply custom type mappings to feedback fields
      for (String type : typeToClass.keySet())
      {
         for (InterfaceField field : context.getFeedbackContext().getFields().values())
         {
            if (field.getType().equals(type))
            {
               field.javaType(typeToClass.get(type));
            }
         }
      }

      ST goalST = new ST(template);
      goalST.add("context", context.getGoalContext());
      String goalClassContent = goalST.render();

      ST resultST = new ST(template);
      resultST.add("context", context.getResultContext());
      String resultClassContent = resultST.render();

      ST feedbackST = new ST(template);
      feedbackST.add("context", context.getFeedbackContext());
      String feedbackClassContent = feedbackST.render();

      return new String[]{goalClassContent, resultClassContent, feedbackClassContent};
   }

   private static String loadTemplate(String resourceName)
   {
      try (InputStream stream = ROS2ActionGenerator.class.getClassLoader().getResourceAsStream(resourceName))
      {
         if (stream != null)
         {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
         }
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }
      return null;
   }
}
