/*
 * This class was automatically generated by jros2.
 * Do not modify this file directly.

##################################################################################
This file was generated from the following content:
(sensor_msgs/JoyFeedbackArray.msg)
##################################################################################
   # This message publishes values for multiple feedback at once.
   JoyFeedback[] array

##################################################################################

 */
package sensor_msgs.msg.dds;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.*;
import us.ihmc.jros2.ROS2Message;

public class JoyFeedbackArray implements ROS2Message<JoyFeedbackArray>
{
   public static final java.lang.String name = "sensor_msgs::msg::dds_::JoyFeedbackArray_";

   /**
      This message publishes values for multiple feedback at once.
   */
   private final IDLObjectSequence<sensor_msgs.msg.dds.JoyFeedback> array_;

   public JoyFeedbackArray()
   {
      array_ = new IDLObjectSequence<sensor_msgs.msg.dds.JoyFeedback>(sensor_msgs.msg.dds.JoyFeedback.class);

   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += array_.calculateSizeBytes(currentAlignment);

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      array_.serialize(buffer);

   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      array_.deserialize(buffer);

   }

   @Override
   public void set(JoyFeedbackArray from)
   {
      array_.set(from.array_);

   }

   public IDLObjectSequence<sensor_msgs.msg.dds.JoyFeedback> getArray()
   {
      return array_;
   }


}