package us.ihmc.jros2.interfaces.context;

import java.util.Map;

public class MsgContext extends InterfaceContext
{
   private Map<String, Field> fields;

   public MsgContext(String packageName, String name)
   {
      super(packageName, name);
   }
}
