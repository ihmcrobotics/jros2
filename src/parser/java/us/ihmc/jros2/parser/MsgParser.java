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

import java.util.StringJoiner;

public final class MsgParser
{
   public static MsgContext parseMsg(String schema, String packageResourceName)
   {
      MsgContext msgContext = new MsgContext(schema);
      msgContext.setPackageResourceName(packageResourceName);

      StringJoiner commentLines = new StringJoiner("\n");
      String[] lines = schema.split("\\R");
      for (String line : lines)
      {
         line = line.trim();

         if (line.startsWith("#"))
         {
            commentLines.add(line);
            continue;
         }

         if (line.isEmpty() && msgContext.getFields().isEmpty())
         {
            msgContext.setHeaderComment(commentLines.toString());
            commentLines = new StringJoiner("\n");
            continue;
         }

         if (line.equals("---"))
         {
            // TODO: Handle interface sections
            continue;
         }

         InterfaceField field = InterfaceFieldParser.parseField(msgContext, line, commentLines.toString());
         commentLines = new StringJoiner("\n");

         if (field != null)
         {
            msgContext.getFields().put(field.getName(), field);
         }
      }

      return msgContext;
   }
}
