/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
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

   /**
    * A method reference to the getter for the first {@link Header} field within the ROS2Message topic type.
    * Used for statistics.
    */
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
    * Read from the {@link CDRBuffer} into data (does not allocate any heap memory).
    *
    * @param data The message to pack
    */
   public void read(T data)
   {
      cdrBuffer.readPayloadHeader();

      data.deserialize(cdrBuffer);

      /*
       * Generate age statistics for messages which have a Header field (https://github.com/ros2/common_interfaces/blob/humble/std_msgs/msg/Header.msg)
       *
       * Note:
       * The age statistic value will only be calculated for messages which have a Header field and subscriptions which call the read method.
       */
      if (getHeaderMethod != null)
      {
         try
         {
            Header header = (Header) getHeaderMethod.invoke(data);
            lastMessageTimestamp = (1000L * header.getStamp().getSec()) + (header.getStamp().getNanosec() / 1000000L);
         }
         catch (IllegalAccessException | InvocationTargetException e)
         {
            LogTools.error("Failed to get the message header. Not recording message age statistics from now on.");
            getHeaderMethod = null;
         }
      }
   }

   /**
    * Read from the {@link CDRBuffer} and return a new message (allocates a new instance of the message type).
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
