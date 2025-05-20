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

import java.time.Duration;

/**
 * <a href="https://docs.ros.org/en/rolling/Concepts/Intermediate/About-Quality-of-Service-Settings.html">Quality of Service settings</a>
 */
public class ROS2QoSProfile
{
   static
   {
      jros2.load();
   }

   public static final ROS2QoSProfile DEFAULT = new ROS2QoSProfile();

   public enum History
   {
      SYSTEM_DEFAULT, KEEP_LAST, KEEP_ALL
   }

   public enum Reliability
   {
      SYSTEM_DEFAULT, RELIABLE, BEST_EFFORT
   }

   public enum Durability
   {
      SYSTEM_DEFAULT, VOLATILE, TRANSIENT_LOCAL
   }

   public enum Liveliness
   {
      SYSTEM_DEFAULT, AUTOMATIC, MANUAL_BY_TOPIC
   }

   private History history;
   private int depth;
   private Reliability reliability;
   private Durability durability;
   private Duration deadline;
   private Duration lifespan;
   private Liveliness liveliness;
   private Duration leaseDuration;

   public ROS2QoSProfile()
   {
      // Defaults
      history = History.SYSTEM_DEFAULT;
      depth = 10;
      reliability = Reliability.SYSTEM_DEFAULT;
      durability = Durability.SYSTEM_DEFAULT;
      deadline = Duration.ofSeconds(0);
      lifespan = Duration.ofSeconds(0);
      liveliness = Liveliness.SYSTEM_DEFAULT;
      leaseDuration = Duration.ofSeconds(0);
   }

   public void history(History history)
   {
      this.history = history;
   }

   public void depth(int depth)
   {
      this.depth = depth;
   }

   public void reliability(Reliability reliability)
   {
      this.reliability = reliability;
   }

   public void durability(Durability durability)
   {
      this.durability = durability;
   }

   public void deadline(Duration deadline)
   {
      this.deadline = deadline;
   }

   public void lifespan(Duration lifespan)
   {
      this.lifespan = lifespan;
   }

   public void liveliness(Liveliness liveliness)
   {
      this.liveliness = liveliness;
   }

   public void leaseDuration(Duration leaseDuration)
   {
      this.leaseDuration = leaseDuration;
   }

   public History getHistory()
   {
      return history;
   }

   public int getDepth()
   {
      return depth;
   }

   public Reliability getReliability()
   {
      return reliability;
   }

   public Durability getDurability()
   {
      return durability;
   }

   public Duration getDeadline()
   {
      return deadline;
   }

   public Duration getLifespan()
   {
      return lifespan;
   }

   public Liveliness getLiveliness()
   {
      return liveliness;
   }

   public Duration getLeaseDuration()
   {
      return leaseDuration;
   }
}
