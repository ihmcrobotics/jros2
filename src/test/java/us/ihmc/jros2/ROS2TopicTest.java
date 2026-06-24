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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import std_msgs.Empty;

public class ROS2TopicTest
{
   @Test
   public void testDefaultQoS()
   {
      ROS2Topic<?> topic = new ROS2Topic<>("/test");
      Assertions.assertSame(ROS2QoSProfile.DEFAULT, topic.getQoS());
   }

   @Test
   public void testWithQoS()
   {
      ROS2Topic<?> topic = new ROS2Topic<>("/test").withQoS(ROS2QoSProfile.BEST_EFFORT);
      Assertions.assertSame(ROS2QoSProfile.BEST_EFFORT, topic.getQoS());
   }

   @Test
   public void testQoSPropagatesOnAppend()
   {
      ROS2Topic<?> child = new ROS2Topic<>("/camera").withQoS(ROS2QoSProfile.RELIABLE).appendedWith("color");
      Assertions.assertSame(ROS2QoSProfile.RELIABLE, child.getQoS());
   }

   @Test
   public void testQoSPropagatesOnPrepend()
   {
      ROS2Topic<?> child = new ROS2Topic<>("/camera").withQoS(ROS2QoSProfile.RELIABLE).prependedWith("robot");
      Assertions.assertSame(ROS2QoSProfile.RELIABLE, child.getQoS());
   }

   @Test
   public void testQoSPropagatesOnInsert()
   {
      ROS2Topic<?> child = new ROS2Topic<>("/camera/color").withQoS(ROS2QoSProfile.RELIABLE).insert(1, "zed");
      Assertions.assertSame(ROS2QoSProfile.RELIABLE, child.getQoS());
   }

   @Test
   public void testQoSPropagatesOnWithType()
   {
      ROS2Topic<Empty> typedTopic = new ROS2Topic<>("/test").withQoS(ROS2QoSProfile.BEST_EFFORT).withType(Empty.class);
      Assertions.assertSame(ROS2QoSProfile.BEST_EFFORT, typedTopic.getQoS());
   }
}
