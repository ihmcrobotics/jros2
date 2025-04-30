package us.ihmc.jros2.interfaces.context;

import java.util.HashMap;
import java.util.Map;

public class MsgContext extends InterfaceContext
{
   private final Map<String, Field> fields;

   public MsgContext(String packageName, String name)
   {
      super(packageName, name);

      fields = new HashMap<>();
   }

   public Map<String, Field> getFields()
   {
      return fields;
   }
}
