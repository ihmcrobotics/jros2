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
 * State of an action goal, with the ids of {@code action_msgs/msg/GoalStatus}.
 */
public enum ROS2GoalStatus
{
   UNKNOWN, ACCEPTED, EXECUTING, CANCELING, SUCCEEDED, CANCELED, ABORTED;

   private static final ROS2GoalStatus[] BY_ID = values();

   public byte getId()
   {
      return (byte) ordinal();
   }

   public static ROS2GoalStatus fromId(byte id)
   {
      return id >= 0 && id < BY_ID.length ? BY_ID[id] : UNKNOWN;
   }

   /**
    * @return true for SUCCEEDED, CANCELED and ABORTED
    */
   public boolean isTerminal()
   {
      return this == SUCCEEDED || this == CANCELED || this == ABORTED;
   }

   /**
    * @return true for ACCEPTED, EXECUTING and CANCELING
    */
   public boolean isActive()
   {
      return this == ACCEPTED || this == EXECUTING || this == CANCELING;
   }
}
