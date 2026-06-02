/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
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
 * Information about a subscription matched event from a remote DataWriter.
 */
public class ROS2SubscriptionMatchedInfo
{
   private final Guid guid = new Guid();
   private ROS2SubscriptionMatchedStatus status;

   public Guid getGuid()
   {
      return guid;
   }

   public ROS2SubscriptionMatchedStatus getStatus()
   {
      return status;
   }

   void set(Guid guid, ROS2SubscriptionMatchedStatus status)
   {
      this.guid.set(guid);
      this.status = status;
   }
}
