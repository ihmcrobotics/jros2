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

import static us.ihmc.fastddsjava.pointers.fastddsjava.RETCODE_OK;

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

   private static final int OK = RETCODE_OK();

   public boolean read(T data)
   {
      boolean read = false;

      if (OK == subscription.nextSample())
      {
         synchronized (subscription.readBuffer)
         {
            subscription.readBuffer.readPayloadHeader();

            data.deserialize(subscription.readBuffer);
         }

         read = true;
      }

      recordStatistics(data);

      return read;
   }

   public T read()
   {
      T data = ROS2Message.createInstance(subscription.getTopicType());

      if (data != null)
      {
         if (!read(data))
         {
            data = null;
         }
      }

      return data;
   }

   public boolean readLatest(T data)
   {
      boolean read = false;

      if (subscription.hasHadData())
      {
         synchronized (subscription.readBuffer)
         {
            /*
             * Check if the subscription callback has received data but hasn't copied it from the native side yet
             */
            if (subscription.readBuffer.getBufferUnsafe().capacity() == 1)
            {
               read(data);
            }
            else
            {
               /*
                * Check if the buffer has already been read from, if so rewind it
                */
               if (subscription.readBuffer.getBufferUnsafe().position() != 0)
               {
                  subscription.readBuffer.rewind();
               }

               subscription.readBuffer.readPayloadHeader();

               data.deserialize(subscription.readBuffer);
            }
         }

         read = true;
      }

      recordStatistics(data);

      return read;
   }

   public T readLatest()
   {
      T data = ROS2Message.createInstance(subscription.getTopicType());

      if (data != null)
      {
         if (!readLatest(data))
         {
            data = null;
         }
      }

      return data;
   }

   private void recordStatistics(T data)
   {
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

   long getLastMessageTimestamp()
   {
      return lastMessageTimestamp;
   }
}
