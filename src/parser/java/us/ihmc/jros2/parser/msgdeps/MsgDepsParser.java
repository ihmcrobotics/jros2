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
      for (int i = 0; i < lines.length; ++i)
      {
         String line = lines[i];

         if (line.matches(DEPENDENCY_DELIMITER_PATTERN))
         {
            String msgDelimiter = (i + 1 < lines.length) ? lines[i + 1] : null;

            if (msgDelimiter != null && msgDelimiter.startsWith("MSG: "))
            {
               i += 2; // Seek past the 2-line delimiter

               String dependencyPackageResourceName = msgDelimiter.substring(5);
               StringJoiner dependencySchema = new StringJoiner("\n");

               while ((i + 1) < lines.length && !lines[i + 1].matches(DEPENDENCY_DELIMITER_PATTERN))
               {
                  dependencySchema.add(lines[i++]);
               }

               MsgContext dependency = MsgParser.parseMsg(dependencySchema.toString(), dependencyPackageResourceName);

               msgDepsContext.getDependencies().put(dependencyPackageResourceName, dependency);
            }
         }
      }

      return msgDepsContext;
   }
}
