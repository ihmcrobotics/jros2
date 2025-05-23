/*
 * This class was automatically generated by jros2.
 * Do not modify this file directly.

##################################################################################
This file was generated from the following content:
(jros2_example_interfaces/BoolTest.msg)
##################################################################################
   bool bool_1 1
   bool bool_2 true
   bool bool_3 0
   bool bool_4 false
   # bool bool_5 -1
   # bool bool_6 TRUE
##################################################################################

 */
package jros2_example_interfaces.msg.dds;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.*;
import us.ihmc.jros2.ROS2Message;

public class BoolTest implements ROS2Message<BoolTest>
{
   public static final java.lang.String name = "jros2_example_interfaces::msg::dds_::BoolTest_";

   private boolean bool_1_;
   private boolean bool_2_;
   private boolean bool_3_;
   private boolean bool_4_;

   public BoolTest()
   {
      bool_1_ = (boolean) true;
      bool_2_ = (boolean) true;
      bool_3_ = (boolean) false;
      bool_4_ = (boolean) false;

   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 1 + CDRBuffer.alignment(currentAlignment, 1); // bool_1_
      currentAlignment += 1 + CDRBuffer.alignment(currentAlignment, 1); // bool_2_
      currentAlignment += 1 + CDRBuffer.alignment(currentAlignment, 1); // bool_3_
      currentAlignment += 1 + CDRBuffer.alignment(currentAlignment, 1); // bool_4_

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      buffer.writeBoolean(bool_1_);
      buffer.writeBoolean(bool_2_);
      buffer.writeBoolean(bool_3_);
      buffer.writeBoolean(bool_4_);

   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      bool_1_ = buffer.readBoolean();
      bool_2_ = buffer.readBoolean();
      bool_3_ = buffer.readBoolean();
      bool_4_ = buffer.readBoolean();

   }

   @Override
   public void set(BoolTest from)
   {
      bool_1_ = from.bool_1_;
      bool_2_ = from.bool_2_;
      bool_3_ = from.bool_3_;
      bool_4_ = from.bool_4_;

   }

   public boolean getBool1()
   {
      return bool_1_;
   }

   public void setBool1(boolean bool_1_)
   {
      this.bool_1_ = bool_1_;
   }

   public boolean getBool2()
   {
      return bool_2_;
   }

   public void setBool2(boolean bool_2_)
   {
      this.bool_2_ = bool_2_;
   }

   public boolean getBool3()
   {
      return bool_3_;
   }

   public void setBool3(boolean bool_3_)
   {
      this.bool_3_ = bool_3_;
   }

   public boolean getBool4()
   {
      return bool_4_;
   }

   public void setBool4(boolean bool_4_)
   {
      this.bool_4_ = bool_4_;
   }


}