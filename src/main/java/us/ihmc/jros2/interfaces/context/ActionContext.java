package us.ihmc.jros2.interfaces.context;

import java.util.HashMap;
import java.util.Map;

public class ActionContext extends InterfaceContext
{
   private final Map<String, Field> goalFields;
   private final Map<String, Field> resultFields;
   private final Map<String, Field> feedbackFields;

   public ActionContext(String packageName, String name)
   {
      super(packageName, name);

      goalFields = new HashMap<>();
      resultFields = new HashMap<>();
      feedbackFields = new HashMap<>();
   }

   public Map<String, Field> getGoalFields()
   {
      return goalFields;
   }

   public Map<String, Field> getResultFields()
   {
      return resultFields;
   }

   public Map<String, Field> getFeedbackFields()
   {
      return feedbackFields;
   }

   // TODO: Finish
}
