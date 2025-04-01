package us.ihmc.jros2.testmessages;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.CDRSerializable;
import us.ihmc.fastddsjava.cdr.idl.IDLObjectSequence;

public class CustomMessage2 implements CDRSerializable
{
   public static final String name = "ihmc_common_msgs::msg::dds_::CustomMessage2_";

   private IDLObjectSequence<CustomMessage> customMessageList_;

   public CustomMessage2()
   {
      customMessageList_ = new IDLObjectSequence<>(CustomMessage.class);
   }

   public IDLObjectSequence<CustomMessage> getCustomMessageList()
   {
      return customMessageList_;
   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += customMessageList_.calculateSizeBytes(currentAlignment); // n bytes for customMessageList

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      // Write customMessageList
      customMessageList_.serialize(buffer);
   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      // Read customMessageList
      customMessageList_.deserialize(buffer);
   }
}
