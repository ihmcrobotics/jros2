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
import us.ihmc.jros2.parser.SrvContext;
import us.ihmc.jros2.parser.field.InterfaceField;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Java code generator from a ROS2 Service schema.
 */
public class ROS2ServiceGenerator
{
   /**
    * Generate Java class strings (Request and Response) from a {@link SrvContext}
    *
    * @param context     the ROS Service in {@link SrvContext} form
    * @param typeToClass a mapping of ROS2 Message type to Java class name for custom written ROS2 Java Message classes.
    *                    Pass an empty Map if not required.
    * @return an array containing [requestClassContent, responseClassContent]
    */
   public static String[] generateJavaClassContents(SrvContext context, Map<String, String> typeToClass)
   {
      String requestTemplate = loadTemplate("ROS2ServiceRequest.st");
      String responseTemplate = loadTemplate("ROS2ServiceRequest.st"); // Use same template for both

      if (requestTemplate == null || responseTemplate == null)
      {
         return new String[]{"", ""};
      }

      // Apply custom type mappings to request fields
      for (String type : typeToClass.keySet())
      {
         for (InterfaceField field : context.getRequestContext().getFields().values())
         {
            if (field.getType().equals(type))
            {
               field.javaType(typeToClass.get(type));
            }
         }
      }

      // Apply custom type mappings to response fields
      for (String type : typeToClass.keySet())
      {
         for (InterfaceField field : context.getResponseContext().getFields().values())
         {
            if (field.getType().equals(type))
            {
               field.javaType(typeToClass.get(type));
            }
         }
      }

      ST requestST = new ST(requestTemplate);
      requestST.add("context", context.getRequestContext());
      String requestClassContent = requestST.render();

      ST responseST = new ST(responseTemplate);
      responseST.add("context", context.getResponseContext());
      String responseClassContent = responseST.render();

      return new String[]{requestClassContent, responseClassContent};
   }

   private static String loadTemplate(String resourceName)
   {
      try (InputStream stream = ROS2ServiceGenerator.class.getClassLoader().getResourceAsStream(resourceName))
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
