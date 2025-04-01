package us.ihmc.ros2.testmessages;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.CDRSerializable;

public class Bool implements CDRSerializable
{
   public static final String name = "std_msgs::msg::dds_::Bool_";

   private boolean data_; // _ in case the field is named a Java keyword

   public boolean getData()
   {
      return data_;
   }

   public void setData(boolean data_)
   {
      this.data_ = data_;
   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 1 + CDRBuffer.alignment(currentAlignment, 1); // 1 byte for data

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      // Write data
      buffer.writeBoolean(data_);
   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      // Read data
      data_ = buffer.readBoolean();
   }
}
