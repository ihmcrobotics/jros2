package us.ihmc.ros2.testmessages;

import us.ihmc.fastddsjava.CDR;

import java.nio.ByteBuffer;

public class Bool extends ROS2Message
{
   private static final String name = "std_msgs::msg::dds_::Bool_";

   private boolean data_; // _ in case the field is named a Java keyword

   public boolean getData()
   {
      return data_;
   }

   public void setData_(boolean data_)
   {
      this.data_ = data_;
   }

   @Override
   public int calculateSize()
   {
      int size = 0;

      size += 4; // 4 bytes for payload header
      size += 1; // 1 byte for data_

      // handle any unbounded types here

      return size;
   }

   @Override
   public void serialize(ByteBuffer buffer)
   {
      CDR.writeSerializationPayloadHeader(buffer);
      CDR.writeBoolean(data_, buffer);
   }

   @Override
   public void deserialize(ByteBuffer buffer)
   {
      CDR.readSerializationPayloadHeader(buffer);
      data_ = CDR.readBoolean(buffer);
   }
}
