package us.ihmc.fastddsjava.msg;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.CDRSerializable;

public class TestIDLMsg implements CDRSerializable
{
   private boolean data;

   public boolean getData()
   {
      return data;
   }

   public void setData(boolean data_)
   {
      this.data = data_;
   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 1 + CDRBuffer.alignment(currentAlignment, 1);

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      buffer.writeBoolean(data);
   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      data = buffer.readBoolean();
   }
}
