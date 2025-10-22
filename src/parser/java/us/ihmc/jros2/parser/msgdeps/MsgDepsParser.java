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
package us.ihmc.jros2.parser.msgdeps;

import us.ihmc.jros2.parser.MsgContext;
import us.ihmc.jros2.parser.MsgParser;

import java.util.StringJoiner;

// https://wiki.ros.org/roslib/gentools
// https://mcap.dev/spec/registry#ros2msg
public final class MsgDepsParser
{
   private static final String DEPENDENCY_DELIMITER_PATTERN = "^={80}$";

   /*
    * Handle nested dependency interface
    *
    * There are 2 sequential delimiter lines:
    *    First line: 80 = characters
    *    Second line: "MSG: <package resource name>"
    *
    * Reference:
    * https://wiki.ros.org/roslib/gentools
    * https://mcap.dev/spec/registry#ros2msg
    */
   public static MsgDepsContext parseMsgDeps(String schema, String packageResourceName)
   {
      MsgDepsContext msgDepsContext = new MsgDepsContext(schema);
      msgDepsContext.getMeta().setPackageResourceName(packageResourceName);

      String[] lines = schema.split("\\R");
      int i = 0;

      while (i < lines.length)
      {
         String line = lines[i].trim();

         if (line.matches(DEPENDENCY_DELIMITER_PATTERN))
         {
            if (i + 1 >= lines.length)
            {
               break;
            }

            String msgLine = lines[i + 1];
            if (!msgLine.startsWith("MSG: "))
            {
               // We were expecting a second line of the delimiter, but it wasn't there
               i++;
               continue;
            }

            String dependencyPackageResourceName = msgLine.substring(5).trim();
            StringJoiner dependencySchema = new StringJoiner("\n");

            i += 2; // Skip 2-line delimiter

            // Collect until next delimiter or EOF
            while (i < lines.length && !lines[i].matches(DEPENDENCY_DELIMITER_PATTERN))
            {
               dependencySchema.add(lines[i]);
               i++;
            }

            MsgContext dependency = MsgParser.parseMsg(dependencySchema.toString(), dependencyPackageResourceName);
            msgDepsContext.getDependencies().put(dependencyPackageResourceName, dependency);
         }
         else
         {
            i++;
         }
      }

      return msgDepsContext;
   }
}
