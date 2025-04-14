package us.ihmc.jros2.msg;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.IDLObjectSequence;
import us.ihmc.jros2.ROS2Message;

public class CustomMessage2 implements ROS2Message<CustomMessage2>
{
   public static final String name = "ihmc_common_msgs::msg::dds_::CustomMessage2_";

   private IDLObjectSequence<CustomMessage> customMessageList_;
   private CustomMessage[] customMessageList2_;

   public CustomMessage2()
   {
      customMessageList_ = new IDLObjectSequence<>(CustomMessage.class);
      customMessageList2_ = new CustomMessage[4];
      for (int i = 0; i < customMessageList2_.length; i++)
         customMessageList2_[i] = new CustomMessage();
   }

   public IDLObjectSequence<CustomMessage> getCustomMessageList()
   {
      return customMessageList_;
   }

   public CustomMessage[] getCustomMessageList2()
   {
      return customMessageList2_;
   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += customMessageList_.calculateSizeBytes(currentAlignment); // n bytes for customMessageList

      for (int i = 0; i < customMessageList2_.length; i++)
      {
         currentAlignment +=  customMessageList2_[i].calculateSizeBytes(currentAlignment);
      }

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      // Write customMessageList
      customMessageList_.serialize(buffer);

      for (int i = 0; i < customMessageList2_.length; i++)
      {
         customMessageList2_[i].serialize(buffer);
      }
   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      // Read customMessageList
      customMessageList_.deserialize(buffer);

      for (int i = 0; i < customMessageList2_.length; i++)
      {
         customMessageList2_[i].deserialize(buffer);
      }
   }

   @Override
   public String getName()
   {
      return name;
   }

   @Override
   public void set(CustomMessage2 from)
   {

   }
}
