/*
 * This class was automatically generated by jros2.
 * Do not modify this file directly.

##################################################################################
This file was generated from the following content:
(example_interfaces/UInt32.msg)
##################################################################################
   # This is an example message of using a primitive datatype, uint32.
   # If you want to test with this that's fine, but if you are deploying
   # it into a system you should create a semantically meaningful message type.
   # If you want to embed it in another message, use the primitive data type instead.
   uint32 data

##################################################################################

 */
package example_interfaces.msg.dds;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.*;
import us.ihmc.jros2.ROS2Message;

public class UInt32 implements ROS2Message<UInt32>
{
   public static final java.lang.String name = "example_interfaces::msg::dds_::UInt32_";

   /**
      This is an example message of using a primitive datatype, uint32.
      If you want to test with this that's fine, but if you are deploying
      it into a system you should create a semantically meaningful message type.
      If you want to embed it in another message, use the primitive data type instead.
   */
   private int data_;

   public UInt32()
   {
   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // data_

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      buffer.writeInt(data_);

   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      data_ = buffer.readInt();

   }

   @Override
   public void set(UInt32 from)
   {
      data_ = from.data_;

   }

   public int getData()
   {
      return data_;
   }

   public void setData(int data_)
   {
      this.data_ = data_;
   }


}