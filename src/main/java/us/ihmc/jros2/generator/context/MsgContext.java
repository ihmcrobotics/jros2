package us.ihmc.jros2.generator.context;

import java.util.HashMap;
import java.util.Map;

public class MsgContext extends InterfaceContext
{
   private final Map<String, InterfaceField> fields;

   public MsgContext(String packageName, String name, String fileContent)
   {
      super(packageName, name, fileContent);

      fields = new HashMap<>();
   }

   @Override
   protected void onSection()
   {
      // Do nothing, msg definitions do not have sections
   }

   @Override
   protected void onField(InterfaceField field)
   {
      fields.put(field.getName(), field);
   }

   public Map<String, InterfaceField> getFields()
   {
      return fields;
   }
}
