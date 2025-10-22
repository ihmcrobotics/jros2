package us.ihmc.jros2.parser;

import us.ihmc.jros2.parser.field.InterfaceField;
import us.ihmc.jros2.parser.field.InterfaceFieldParser;

import java.util.StringJoiner;

public final class MsgParser
{
   public static MsgContext parseMsg(String schema, String packageResourceName)
   {
      MsgContext msgContext = new MsgContext(schema);
      msgContext.getMeta().setPackageResourceName(packageResourceName);

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
            msgContext.getMeta().setHeaderComment(commentLines.toString());
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
