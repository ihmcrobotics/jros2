/*
 * This class was automatically generated by jros2.
 * Do not modify this file directly.

##################################################################################
This file was generated from the following content:
(std_msgs/Float64.msg)
##################################################################################
   # This was originally provided as an example message.
   # It is deprecated as of Foxy
   # It is recommended to create your own semantically meaningful message.
   # However if you would like to continue using this please use the equivalent in example_msgs.

   float64 data

##################################################################################

 */
package std_msgs.msg.dds;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.*;
import us.ihmc.jros2.ROS2Message;

/**
   This was originally provided as an example message.
   It is deprecated as of Foxy
   It is recommended to create your own semantically meaningful message.
   However if you would like to continue using this please use the equivalent in example_msgs.
*/
public class Float64 implements ROS2Message<Float64>
{
   public static final java.lang.String name = "std_msgs::msg::dds_::Float64_";

   private double data_;

   public Float64()
   {
   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 8 + CDRBuffer.alignment(currentAlignment, 8); // data_

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      buffer.writeDouble(data_);

   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      data_ = buffer.readDouble();

   }

   @Override
   public void set(Float64 from)
   {
      data_ = from.data_;

   }

   public double getData()
   {
      return data_;
   }

   public void setData(double data_)
   {
      this.data_ = data_;
   }


}