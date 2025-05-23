/*
 * This class was automatically generated by jros2.
 * Do not modify this file directly.

##################################################################################
This file was generated from the following content:
(std_msgs/MultiArrayDimension.msg)
##################################################################################
   # This was originally provided as an example message.
   # It is deprecated as of Foxy
   # It is recommended to create your own semantically meaningful message.
   # However if you would like to continue using this please use the equivalent in example_msgs.

   string label   # label of given dimension
   uint32 size    # size of given dimension (in type units)
   uint32 stride  # stride of given dimension

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
public class MultiArrayDimension implements ROS2Message<MultiArrayDimension>
{
   public static final java.lang.String name = "std_msgs::msg::dds_::MultiArrayDimension_";

   private final StringBuilder label_; // label of given dimension
   private int size_; // size of given dimension (in type units)
   private int stride_; // stride of given dimension

   public MultiArrayDimension()
   {
      label_ = new StringBuilder();

   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4) + (1 * label_.length()) + 1; // label_
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // size_
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // stride_

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      buffer.writeString(label_);
      buffer.writeInt(size_);
      buffer.writeInt(stride_);

   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      buffer.readString(label_);
      size_ = buffer.readInt();
      stride_ = buffer.readInt();

   }

   @Override
   public void set(MultiArrayDimension from)
   {
      label_.delete(0, label_.length());
      label_.insert(0, from.label_);
      size_ = from.size_;
      stride_ = from.stride_;

   }

   public StringBuilder getLabel()
   {
      return label_;
   }

   public int getSize()
   {
      return size_;
   }

   public void setSize(int size_)
   {
      this.size_ = size_;
   }

   public int getStride()
   {
      return stride_;
   }

   public void setStride(int stride_)
   {
      this.stride_ = stride_;
   }


}