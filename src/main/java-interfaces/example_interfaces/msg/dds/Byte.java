/*
 * This class was automatically generated by jros2.
 * Do not modify this file directly.

##################################################################################
This file was generated from the following content:
(example_interfaces/Byte.msg)
##################################################################################
   # This is an example message of using a primitive datatype, byte.
   # If you want to test with this that's fine, but if you are deploying
   # it into a system you should create a semantically meaningful message type.
   # If you want to embed it in another message, use the primitive data type instead.
   byte data

##################################################################################

 */
package example_interfaces.msg.dds;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.*;
import us.ihmc.jros2.ROS2Message;

public class Byte implements ROS2Message<Byte>
{
   public static final java.lang.String name = "example_interfaces::msg::dds_::Byte_";

   /**
      This is an example message of using a primitive datatype, byte.
      If you want to test with this that's fine, but if you are deploying
      it into a system you should create a semantically meaningful message type.
      If you want to embed it in another message, use the primitive data type instead.
   */
   private byte data_;

   public Byte()
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
   public void set(Byte from)
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