package us.ihmc.jros2.interfaces.context;

import java.util.Map;

public class ActionContext extends InterfaceContext
{
   private Map<String, Field> goalFields;
   private Map<String, Field> resultFields;
   private Map<String, Field> feedbackFields;

   public ActionContext(String packageName, String name)
   {
      super(packageName, name);
   }

   // TODO: Finish
}
