/*
 * This class was automatically generated by jros2.
 * Do not modify this file directly.

##################################################################################
This file was generated from the following content:
(example_interfaces/Int16.msg)
##################################################################################
   # This is an example message of using a primitive datatype, int16.
   # If you want to test with this that's fine, but if you are deploying
   # it into a system you should create a semantically meaningful message type.
   # If you want to embed it in another message, use the primitive data type instead.
   int16 data

##################################################################################

 */
package example_interfaces.msg.dds;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.*;
import us.ihmc.jros2.ROS2Message;

public class Int16 implements ROS2Message<Int16>
{
   public static final java.lang.String name = "example_interfaces::msg::dds_::Int16_";

   /**
      This is an example message of using a primitive datatype, int16.
      If you want to test with this that's fine, but if you are deploying
      it into a system you should create a semantically meaningful message type.
      If you want to embed it in another message, use the primitive data type instead.
   */
   private short data_;

   public Int16()
   {
   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 2 + CDRBuffer.alignment(currentAlignment, 2); // data_

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      buffer.writeShort(data_);

   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      data_ = buffer.readShort();

   }

   @Override
   public void set(Int16 from)
   {
      data_ = from.data_;

   }

   public short getData()
   {
      return data_;
   }

   public void setData(short data_)
   {
      this.data_ = data_;
   }


}