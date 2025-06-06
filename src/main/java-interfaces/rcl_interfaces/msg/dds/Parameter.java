/*
 * This class was automatically generated by jros2.
 * Do not modify this file directly.

##################################################################################
This file was generated from the following content:
(rcl_interfaces/Parameter.msg)
##################################################################################
   # This is the message to communicate a parameter. It is an open struct with an enum in
   # the descriptor to select which value is active.

   # The full name of the parameter.
   string name

   # The parameter's value which can be one of several types, see
   # `ParameterValue.msg` and `ParameterType.msg`.
   ParameterValue value

##################################################################################

 */
package rcl_interfaces.msg.dds;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.*;
import us.ihmc.jros2.ROS2Message;

/**
   This is the message to communicate a parameter. It is an open struct with an enum in
   the descriptor to select which value is active.
*/
public class Parameter implements ROS2Message<Parameter>
{
   public static final java.lang.String name = "rcl_interfaces::msg::dds_::Parameter_";

   /**
      The full name of the parameter.
   */
   private final StringBuilder name_;
   /**
      The parameter's value which can be one of several types, see
      `ParameterValue.msg` and `ParameterType.msg`.
   */
   private final rcl_interfaces.msg.dds.ParameterValue value_;

   public Parameter()
   {
      name_ = new StringBuilder();
      value_ = new rcl_interfaces.msg.dds.ParameterValue();

   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4) + (1 * name_.length()) + 1; // name_
      currentAlignment += value_.calculateSizeBytes(currentAlignment);

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      buffer.writeString(name_);
      value_.serialize(buffer);

   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      buffer.readString(name_);
      value_.deserialize(buffer);

   }

   @Override
   public void set(Parameter from)
   {
      name_.delete(0, name_.length());
      name_.insert(0, from.name_);
      value_.set(from.value_);

   }

   public StringBuilder getName()
   {
      return name_;
   }

   public rcl_interfaces.msg.dds.ParameterValue getValue()
   {
      return value_;
   }


}