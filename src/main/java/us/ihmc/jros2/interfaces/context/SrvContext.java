package us.ihmc.jros2.interfaces.context;

import java.util.HashMap;
import java.util.Map;

public class SrvContext extends InterfaceContext
{
   private final Map<String, Field> requestFields;
   private final Map<String, Field> replyFields;

   public SrvContext(String packageName, String name)
   {
      super(packageName, name);

      requestFields = new HashMap<>();
      replyFields = new HashMap<>();
   }

   public Map<String, Field> getRequestFields()
   {
      return requestFields;
   }

   public Map<String, Field> getReplyFields()
   {
      return replyFields;
   }

   // TODO: Finish

}
