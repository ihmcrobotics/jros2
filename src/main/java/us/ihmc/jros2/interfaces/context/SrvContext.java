package us.ihmc.jros2.interfaces.context;

import java.util.HashMap;
import java.util.Map;

public class SrvContext extends InterfaceContext
{
   private int section = 0; // 0 = request, 1 = reply

   private final Map<String, Field> requestFields;
   private final Map<String, Field> replyFields;

   public SrvContext(String packageName, String name)
   {
      super(packageName, name);

      requestFields = new HashMap<>();
      replyFields = new HashMap<>();
   }

   @Override
   protected void onToken(String[] tokens, int position)
   {
      if (tokens[position].equals("---"))
      {
         if (section < 1)
            section++;
      }
   }

   @Override
   protected void onField(Field field)
   {
      switch (section)
      {
         case 0 -> requestFields.put(field.getName(), field);
         case 1 -> replyFields.put(field.getName(), field);
      }
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
