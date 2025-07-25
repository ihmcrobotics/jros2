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

/**
 * Reads a {@link ROS2Message} from a {@link ROS2Subscription}'s read buffer
 */
public class ROS2MessageReader<T extends ROS2Message<T>>
{
   private final ROS2Subscription<T> subscription;

   public ROS2MessageReader(ROS2Subscription<T> subscription)
   {
      this.subscription = subscription;
   }

   public boolean read(T data)
   {
      boolean read = false;

      if (subscription.readBuffer.getBufferUnsafe().position() == 0)
      {
         subscription.readBuffer.readPayloadHeader();

         data.deserialize(subscription.readBuffer);

         read = true;

         subscription.latest = data;
      }

      return read;
   }

   public T read()
   {
      T data = ROS2Message.createInstance(subscription.getTopicType());

      return read(data) ? data : null;
   }
}
