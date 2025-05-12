package us.ihmc.jros2;

import us.ihmc.fastddsjava.cdr.CDRBuffer;

/**
 * A way to read ROS2Message from a {@link CDRBuffer}, given a {@link ROS2Topic}.
 * Provides both an allocation-free and an allocation approach.
 */
public class ROS2SubscriptionReader<T extends ROS2Message<T>> implements MessageStatisticsProvider
{
   static
   {
      jros2.load();
   }

   private final CDRBuffer cdrBuffer;
   private final ROS2Topic<T> topic;

   /**
    * Use {@link ROS2Node#createSubscription(ROS2Topic, ROS2SubscriptionCallback, ROS2QoSProfile)}
    */
   protected ROS2SubscriptionReader(CDRBuffer cdrBuffer, ROS2Topic<T> topic)
   {
      this.cdrBuffer = cdrBuffer;
      this.topic = topic;
   }

   /**
    * Read from the {@link CDRBuffer} into data (allocation free).
    *
    * @param data The message to pack
    */
   public void read(T data)
   {
      cdrBuffer.readPayloadHeader();

      data.deserialize(cdrBuffer);
   }

   /**
    * Read from the {@link CDRBuffer} and return a new message (allocates).
    */
   public T read()
   {
      T data = ROS2Message.createInstance(topic.getType());

      if (data != null)
      {
         read(data);
      }

      return data;
   }

   @Override
   public long getNumberOfReceivedMessages()
   {
      return 0;
   }

   @Override
   public long getCurrentMessageSize()
   {
      return 0;
   }

   @Override
   public long getLargestMessageSize()
   {
      return 0;
   }

   @Override
   public long getCumulativePayloadBytes()
   {
      return 0;
   }
}
