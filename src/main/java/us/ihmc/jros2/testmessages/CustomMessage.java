package us.ihmc.jros2.testmessages;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.CDRSerializable;
import us.ihmc.fastddsjava.cdr.idl.IDLIntSequence;

public class CustomMessage implements CDRSerializable
{
   public static final String name = "ihmc_common_msgs::msg::dds_::CustomMessage_";

   private boolean data_;
   private IDLIntSequence intList_;

   public CustomMessage()
   {
      intList_ = new IDLIntSequence();
   }

   public boolean getData()
   {
      return data_;
   }

   public void setData(boolean data_)
   {
      this.data_ = data_;
   }

   public IDLIntSequence getIntList()
   {
      return intList_;
   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 1 + CDRBuffer.alignment(currentAlignment, 1); // 1 byte for data

      currentAlignment += intList_.calculateSizeBytes(currentAlignment); // n bytes for intList

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      // Write data
      buffer.writeBoolean(data_);

      // Write intList
      intList_.serialize(buffer);
   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      // Read data
      data_ = buffer.readBoolean();

      // Read intList
      intList_.deserialize(buffer);
   }
}
