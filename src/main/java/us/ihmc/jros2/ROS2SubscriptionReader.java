package us.ihmc.jros2;

import std_msgs.msg.dds.Header;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.log.LogTools;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A way to read ROS2Message from a {@link CDRBuffer}, given a {@link ROS2Topic}.
 * Provides both an allocation-free and an allocation approach.
 */
public class ROS2SubscriptionReader<T extends ROS2Message<T>>
{
   static
   {
      jros2.load();
   }

   private final CDRBuffer cdrBuffer;
   private final ROS2Topic<T> topic;

   private long lastMessageTimestamp;
   private Method getHeaderMethod;

   /**
    * Use {@link ROS2Node#createSubscription(ROS2Topic, ROS2SubscriptionCallback, ROS2QoSProfile)}
    */
   protected ROS2SubscriptionReader(CDRBuffer cdrBuffer, ROS2Topic<T> topic)
   {
      this.cdrBuffer = cdrBuffer;
      this.topic = topic;

      getHeaderMethod = ROS2Message.getHeaderMethod(topic.getType());
      lastMessageTimestamp = Long.MIN_VALUE;
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

      if (getHeaderMethod != null)
      {
         try
         {
            Header header = (Header) getHeaderMethod.invoke(data);
            // TODO: Uncomment when Header message is included in generated Java messages, and the timestamp is included in the Header Java message.
//            lastMessageTimestamp = (1000L * header.getstamp().getsec()) + (header.getstamp().getnanosec() / 1000000L);
         }
         catch (IllegalAccessException | InvocationTargetException e)
         {
            LogTools.error("Failed to get the message header. Not recording message age statistics from now on.");
            getHeaderMethod = null;
         }
      }
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

   long getLastMessageTimestamp()
   {
      return lastMessageTimestamp;
   }
}
