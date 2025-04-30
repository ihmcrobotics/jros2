package us.ihmc.jros2.generator.context;

import java.util.HashMap;
import java.util.Map;

public class MsgContext extends InterfaceContext
{
   private final Map<String, Field> fields;

   public MsgContext(String packageName, String name, String fileContent)
   {
      super(packageName, name, fileContent);

      fields = new HashMap<>();
   }

   @Override
   protected void onToken(String[] tokens, int position)
   {
      // Do nothing
   }

   @Override
   protected void onField(Field field)
   {
      fields.put(field.getName(), field);
   }

   public Map<String, Field> getFields()
   {
      return fields;
   }
}
