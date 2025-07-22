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

   private final ROS2Subscription<T> subscription;

   /*
    * Statistics
    */
   private long lastMessageTimestamp;
   /**
    * A method reference to the getter for the first {@link Header} field within the ROS2Message topic type.
    * Used for statistics.
    */
   private Method getHeaderMethod;

   /**
    * Use {@link ROS2Node#createSubscription(ROS2Topic, ROS2SubscriptionCallback, ROS2QoSProfile)}
    */
   protected ROS2SubscriptionReader(ROS2Subscription<T> subscription)
   {
      this.subscription = subscription;

      lastMessageTimestamp = Long.MIN_VALUE;
      getHeaderMethod = ROS2Message.getHeaderMethod(subscription.getTopicType());
   }

   /**
    * Reads data from the {@link CDRBuffer} into the given message instance without allocating heap memory.
    *
    * @param data   the message object to populate with deserialized data
    * @param reread if true, allows re-reading the last message even if no new data has been received;
    *               if false, returns immediately when data has already been read
    * @return true if data was read into {@param data}; false otherwise
    */
   public boolean read(T data, boolean reread)
   {
      synchronized (subscription.readBuffer)
      {
         /*
          * If there was no data ever received by the native callback, don't bother reading from the buffer because there
          * will be nothing in it.
          */
         if (!subscription.flagHadData)
         {
            return false;
         }

         /*
          * If the buffer has already been read from. The buffer only stores 1 message at a time.
          */
         boolean alreadyRead = subscription.readBuffer.getBufferUnsafe().position() > 0;

         if (alreadyRead)
         {
            if (reread)
            {
               subscription.readBuffer.rewind();
            }
            else
            {
               return false;
            }
         }

         subscription.readBuffer.readPayloadHeader();

         data.deserialize(subscription.readBuffer);

         subscription.flagNewData = false;
      }

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

      return true;
   }

   /**
    * Reads data from the {@link CDRBuffer} into the given message instance without allocating heap memory.
    * If the buffer has already been read from, it will reset the buffer's position and reread the same message.
    *
    * @param data the message object to populate with deserialized data
    * @return true if data was read into {@param data}; false otherwise
    */
   public boolean read(T data)
   {
      return read(data, true);
   }

   /**
    * Reads data from the {@link CDRBuffer} into a newly created instance of the message type.
    *
    * @param reread if true, allows re-reading the last message even if no new data has been received;
    *               if false, returns immediately when data has already been read
    * @return the instance of the message, null if the reader was unable to read any data from the buffer
    */
   public T read(boolean reread)
   {
      T data = ROS2Message.createInstance(subscription.getTopicType());

      if (data != null)
      {
         if (!read(data, reread))
         {
            data = null;
         }
      }

      return data;
   }

   /**
    * Reads data from the {@link CDRBuffer} into a newly created instance of the message type.
    * If the buffer has already been read from, it will reset the buffer's position and reread the same message.
    *
    * @return the instance of the message, null if the reader was unable to read any data from the buffer
    */
   public T read()
   {
      return read(true);
   }

   long getLastMessageTimestamp()
   {
      return lastMessageTimestamp;
   }
}
