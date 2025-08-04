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

public interface ROS2MessageReader<T extends ROS2Message<T>>
{
   /**
    * Indicates whether the subscription has received at least one sample of data.
    *
    * @return true if at least one data sample has been received; false otherwise.
    */
   boolean hasHadData();

   /**
    * Reads the oldest unread sample, if available, and deserializes into {@param data}.
    *
    * @param data the {@link ROS2Message} to pack the data into
    * @return true if data was available and read; false otherwise.
    */
   boolean read(T data);

   /**
    * Reads the oldest unread sample, if available, and deserializes into a new instance of the {@link ROS2Message} type.
    *
    * @return a new instance of the message if data was available and read; null otherwise.
    */
   T read();

   /**
    * Reads all unread samples, if any, and deserializes the latest one info {@param data}.
    *
    * @return the number of samples that were read.
    */
   int readLatest(T data);

   /**
    * Reads all unread samples, if any, and deserializes the latest one into a new instance of the {@link ROS2Message} type.
    *
    * @return a new instance of the message if data was available and read; null otherwise.
    */
   T readLatest();
}