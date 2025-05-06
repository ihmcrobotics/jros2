package us.ihmc.jros2.generator.context;

import java.util.LinkedHashMap;
import java.util.Map;

public class ActionContext extends InterfaceContext
{
   private int section; // 0 = goal, 1 = result, 2 = feedback

   private final Map<String, InterfaceField> goalFields;
   private final Map<String, InterfaceField> resultFields;
   private final Map<String, InterfaceField> feedbackFields;

   public ActionContext(String packageName, String fileName, String fileContent)
   {
      super(packageName, fileName, fileContent);

      assert fileName.endsWith(".action");

      goalFields = new LinkedHashMap<>(); // LinkedHashMap to keep insertion order
      resultFields = new LinkedHashMap<>(); // LinkedHashMap to keep insertion order
      feedbackFields = new LinkedHashMap<>(); // LinkedHashMap to keep insertion order
   }

   @Override
   protected void onSection()
   {
      if (section < 2)
      {
         section++;
      }
   }

   @Override
   protected void onField(InterfaceField field)
   {
      switch (section)
      {
         case 0 -> goalFields.put(field.getName(), field);
         case 1 -> resultFields.put(field.getName(), field);
         case 2 -> feedbackFields.put(field.getName(), field);
      }
   }

   public Map<String, InterfaceField> getGoalFields()
   {
      return goalFields;
   }

   public Map<String, InterfaceField> getResultFields()
   {
      return resultFields;
   }

   public Map<String, InterfaceField> getFeedbackFields()
   {
      return feedbackFields;
   }
}
