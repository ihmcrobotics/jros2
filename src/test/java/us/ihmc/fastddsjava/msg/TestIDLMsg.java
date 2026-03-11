package us.ihmc.fastddsjava.msg;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.CDRSerializable;

public class TestIDLMsg implements CDRSerializable
{
   private int data;

   public int getData()
   {
      return data;
   }

   public void setData(int data_)
   {
      this.data = data_;
   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4);

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      buffer.writeInt(data);
   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      data = buffer.readInt();
   }
}
