package us.ihmc.jros2;

import us.ihmc.fastddsjava.cdr.CDRBuffer;

public class ROS2SubscriptionReader<T extends ROS2Message<T>>
{
   private final CDRBuffer cdrBuffer;
   private final ROS2Topic<T> topic;

   protected ROS2SubscriptionReader(CDRBuffer cdrBuffer, ROS2Topic<T> topic)
   {
      this.cdrBuffer = cdrBuffer;
      this.topic = topic;
   }

   public void read(T data)
   {
      cdrBuffer.readPayloadHeader();

      data.deserialize(cdrBuffer);
   }

   public T read()
   {
      T data = ROS2Message.createInstance(topic.topicType());

      if (data != null)
      {
         read(data);
      }

      return data;
   }
}
