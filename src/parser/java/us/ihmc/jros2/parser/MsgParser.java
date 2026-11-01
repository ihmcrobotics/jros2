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
package us.ihmc.jros2.parser;

import us.ihmc.jros2.parser.field.InterfaceField;
import us.ihmc.jros2.parser.field.InterfaceFieldParser;
import us.ihmc.jros2.parser.field.InterfaceFieldParsingException;

import java.util.StringJoiner;

public final class MsgParser
{
   public static MsgContext parseMsg(String schema, String packageResourceName) throws InterfaceFieldParsingException
   {
      MsgContext msgContext = new MsgContext(schema);
      msgContext.setPackageResourceName(packageResourceName);

      parseFields(msgContext, schema);

      return msgContext;
   }

   /**
    * Parse the header comment and fields of a single interface section into the given context.
    *
    * @param context the context to populate
    * @param section the section text (a whole .msg, or one side of a .srv)
    */
   static void parseFields(InterfaceContext context, String section) throws InterfaceFieldParsingException
   {
      StringJoiner commentLines = new StringJoiner("\n");
      String[] lines = section.split("\\R");
      for (String line : lines)
      {
         line = line.trim();

         if (line.startsWith("#"))
         {
            commentLines.add(line.substring(1).trim()); // Remove leading # and trim
            continue;
         }

         if (line.isEmpty() && context.getFields().isEmpty())
         {
            context.setHeaderComment(commentLines.toString());
            commentLines = new StringJoiner("\n");
            continue;
         }

         if (line.equals("---"))
         {
            // Section separators are handled by SrvParser; ignored in .msg files
            continue;
         }

         InterfaceField field = InterfaceFieldParser.parseField(context, line, commentLines.toString());
         commentLines = new StringJoiner("\n");

         if (field != null)
         {
            context.getFields().put(field.getName(), field);
         }
      }
   }
}
