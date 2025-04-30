package us.ihmc.jros2.interfaces.context;

import java.util.Map;

public class SrvContext extends InterfaceContext
{
   private Map<String, Field> requestFields;
   private Map<String, Field> replyFields;

   public SrvContext(String packageName, String name)
   {
      super(packageName, name);
   }

   // TODO: Finish

}
