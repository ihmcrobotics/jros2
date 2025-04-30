package us.ihmc.jros2.generator.context;

import java.util.HashMap;
import java.util.Map;

public class ActionContext extends InterfaceContext
{
   private int section = 0; // 0 = goal, 1 = result, 2 = feedback

   private final Map<String, Field> goalFields;
   private final Map<String, Field> resultFields;
   private final Map<String, Field> feedbackFields;

   public ActionContext(String packageName, String name, String fileContent)
   {
      super(packageName, name, fileContent);

      goalFields = new HashMap<>();
      resultFields = new HashMap<>();
      feedbackFields = new HashMap<>();
   }

   @Override
   protected void onToken(String[] tokens, int position)
   {
      if (tokens[position].equals("---"))
      {
         if (section < 2)
            section++;
      }
   }

   @Override
   protected void onField(Field field)
   {
      switch (section)
      {
         case 0 -> goalFields.put(field.getName(), field);
         case 1 -> resultFields.put(field.getName(), field);
         case 2 -> feedbackFields.put(field.getName(), field);
      }
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
}
