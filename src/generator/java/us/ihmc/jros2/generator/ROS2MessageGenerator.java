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

import org.stringtemplate.v4.AttributeRenderer;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import us.ihmc.jros2.parser.MsgContext;
import us.ihmc.jros2.parser.field.InterfaceField;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Java code generator from a ROS2 Message schema.
 */
public class ROS2MessageGenerator
{
   /**
    * Template format for text copied from the .msg file into Java comments (the schema and the comments), see
    * {@link #escapeForJavaComment(String)}.
    */
   static final String JAVA_COMMENT_FORMAT = "javaComment";

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
         throw new RuntimeException(e);
      }

      if (template == null)
      {
         return "";
      }

      for (InterfaceField field : context.getFields().values())
      {
         // Match "pkg/Type" keys against both "pkg/Type" and same-package "Type" references
         String type = field.getType();
         String qualifiedType = type.contains("/") ? type : context.getPackageName() + "/" + type;
         String mappedClass = typeToClass.containsKey(type) ? typeToClass.get(type) : typeToClass.get(qualifiedType);
         if (mappedClass != null)
         {
            field.javaType(mappedClass);
         }
      }

      STGroup group = new STGroup();
      group.registerRenderer(String.class, new CommentRenderer());
      ST st = new ST(group, template);
      st.add("context", context);
      return st.render();
   }

   /**
    * Makes text safe to place in a Java comment. javac translates unicode escapes everywhere in a source file, comments
    * included, so a backslash followed by {@code u} (for example a .msg comment mentioning the Windows path
    * {@code C:\\users}) would be an illegal unicode escape, or could even end a line comment (backslash, {@code u000a}).
    * A backslash only starts a unicode escape when it is preceded by an even number of backslashes, so one backslash is
    * added to every odd-length run of backslashes that is followed by {@code u}.
    *
    * @param text the comment text
    * @return text in which no unicode escape is recognized by javac
    */
   static String escapeForJavaComment(String text)
   {
      if (text.indexOf('\\') < 0)
      {
         return text;
      }

      StringBuilder escaped = new StringBuilder(text.length() + 8);
      int i = 0;
      while (i < text.length())
      {
         char c = text.charAt(i);
         if (c != '\\')
         {
            escaped.append(c);
            ++i;
            continue;
         }

         int runStart = i;
         while (i < text.length() && text.charAt(i) == '\\')
         {
            ++i;
         }
         int runLength = i - runStart;
         escaped.append(text, runStart, i);
         if (runLength % 2 == 1 && i < text.length() && text.charAt(i) == 'u')
         {
            escaped.append('\\');
         }
      }
      return escaped.toString();
   }

   /**
    * Renders strings unchanged, except with {@code format="javaComment"} (see {@link #escapeForJavaComment(String)}).
    */
   private static final class CommentRenderer implements AttributeRenderer<Object>
   {
      @Override
      public String toString(Object value, String formatString, Locale locale)
      {
         String text = String.valueOf(value);
         return JAVA_COMMENT_FORMAT.equals(formatString) ? escapeForJavaComment(text) : text;
      }
   }
}
