package us.ihmc.jros2.generator.context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MsgContext extends InterfaceContext
{
   private final Map<String, InterfaceField> fields;

   public MsgContext(String packageName, String fileName, String fileContent)
   {
      super(packageName, fileName, fileContent);

      assert fileName.endsWith(".msg");

      fields = new LinkedHashMap<>(); // LinkedHashMap to keep insertion order
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

   public InterfaceField getField(String name)
   {
      return fields.get(name);
   }

   public List<InterfaceField> getFields()
   {
      List<InterfaceField> fields = new ArrayList<>();
      this.fields.forEach((fieldName, field) -> fields.add(field));
      return fields;
   }
}
