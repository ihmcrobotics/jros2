package us.ihmc.jros2.generator.context;

import java.util.LinkedHashMap;
import java.util.Map;

public class SrvContext extends InterfaceContext
{
   private int section; // 0 = request, 1 = reply

   private final Map<String, InterfaceField> requestFields;
   private final Map<String, InterfaceField> replyFields;

   public SrvContext(String packageName, String fileName, String fileContent)
   {
      super(packageName, fileName, fileContent);

      assert fileName.endsWith(".srv");

      requestFields = new LinkedHashMap<>(); // LinkedHashMap to keep insertion order
      replyFields = new LinkedHashMap<>(); // LinkedHashMap to keep insertion order
   }

   @Override
   protected void onSection()
   {
      if (section < 1)
      {
         section++;
      }
   }

   @Override
   protected void onField(InterfaceField field)
   {
      switch (section)
      {
         case 0 -> requestFields.put(field.getName(), field);
         case 1 -> replyFields.put(field.getName(), field);
      }
   }

   public Map<String, InterfaceField> getRequestFields()
   {
      return requestFields;
   }

   public Map<String, InterfaceField> getReplyFields()
   {
      return replyFields;
   }

   // TODO: Finish

}
