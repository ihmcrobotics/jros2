package us.ihmc.jros2;

import us.ihmc.fastddsjava.cdr.CDRBuffer;

public class ROS2SubscriptionReader<T extends ROS2Message<T>>
{
   private final CDRBuffer cdrBuffer;

   protected ROS2SubscriptionReader(CDRBuffer cdrBuffer)
   {
      this.cdrBuffer = cdrBuffer;
   }

   public void takeNextSample(T data)
   {
      data.deserialize(cdrBuffer);
   }
}
