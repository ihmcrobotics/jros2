/*
 * This class was automatically generated by jros2.
 * Do not modify this file directly.

##################################################################################
This file was generated from the following content:
(example_interfaces/Int8.msg)
##################################################################################
   # This is an example message of using a primitive datatype, in8.
   # If you want to test with this that's fine, but if you are deploying
   # it into a system you should create a semantically meaningful message type.
   # If you want to embed it in another message, use the primitive data type instead.
   int8 data

##################################################################################

 */
package example_interfaces.msg.dds;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.*;
import us.ihmc.jros2.ROS2Message;

public class Int8 implements ROS2Message<Int8>
{
   public static final java.lang.String name = "example_interfaces::msg::dds_::Int8_";

   /**
      This is an example message of using a primitive datatype, in8.
      If you want to test with this that's fine, but if you are deploying
      it into a system you should create a semantically meaningful message type.
      If you want to embed it in another message, use the primitive data type instead.
   */
   private byte data_;

   public Int8()
   {
   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 1 + CDRBuffer.alignment(currentAlignment, 1); // data_

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      buffer.writeByte(data_);

   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      data_ = buffer.readByte();

   }

   @Override
   public void set(Int8 from)
   {
      data_ = from.data_;

   }

   public byte getData()
   {
      return data_;
   }

   public void setData(byte data_)
   {
      this.data_ = data_;
   }


}