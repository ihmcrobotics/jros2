/*
 * This class was automatically generated by jros2.
 * Do not modify this file directly.

##################################################################################
This file was generated from the following content:
(example_interfaces/String.msg)
##################################################################################
   # This is an example message of using a primitive datatype, string.
   # If you want to test with this that's fine, but if you are deploying
   # it into a system you should create a semantically meaningful message type.
   # If you want to embed it in another message, use the primitive data type instead.
   string data

##################################################################################

 */
package example_interfaces.msg.dds;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.*;
import us.ihmc.jros2.ROS2Message;

public class String implements ROS2Message<String>
{
   public static final java.lang.String name = "example_interfaces::msg::dds_::String_";

   /**
      This is an example message of using a primitive datatype, string.
      If you want to test with this that's fine, but if you are deploying
      it into a system you should create a semantically meaningful message type.
      If you want to embed it in another message, use the primitive data type instead.
   */
   private final StringBuilder data_;

   public String()
   {
      data_ = new StringBuilder();

   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4) + (1 * data_.length()) + 1; // data_

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      buffer.writeString(data_);

   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      buffer.readString(data_);

   }

   @Override
   public void set(String from)
   {
      data_.delete(0, data_.length());
      data_.insert(0, from.data_);

   }

   public StringBuilder getData()
   {
      return data_;
   }


}