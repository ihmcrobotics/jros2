package us.ihmc.jros2.parser.msgdeps;

import us.ihmc.jros2.parser.MsgContext;

import java.util.LinkedHashMap;
import java.util.Map;

public class MsgDepsContext extends MsgContext
{
   private final Map<String, MsgContext> dependencies;

   public MsgDepsContext(String schema)
   {
      super(schema);

      dependencies = new LinkedHashMap<>();
   }

   public Map<String, MsgContext> getDependencies()
   {
      return dependencies;
   }
}
