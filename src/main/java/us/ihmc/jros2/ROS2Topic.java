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

import us.ihmc.log.LogTools;

/**
 * Represents a ROS 2 topic which has a name and the message type sent over it.
 * <p>
 * Names are composed of multiple tokens delimited by forward slashes ({@code /}).
 * This class provides methods for creating new {@link ROS2Topic} objects by
 * prepending, appending, or inserting tokens into the name of an existing topic object.
 * <p>
 * The following is example usage for creating topics for a camera that provides color and depth images:
 * <pre> {@code
 * // Create a camera topic namespace
 * ROS2Topic<?> cameraTopic = new ROS2Topic<>("/camera1");
 *
 * // Create color and depth topic namespaces off of the camera topic
 * ROS2Topic<?> colorTopic = cameraTopic.appendedWith("color");
 * ROS2Topic<?> depthTopic = cameraTopic.appendedWith("depth");
 *
 * // Create base topics off of color and depth namespaces
 * ROS2Topic<Image> colorImageTopic = colorTopic.appendedWith("image_raw").withType(Image.class);
 * ROS2Topic<CameraInfo> colorInfoTopic = colorTopic.appendedWith("camera_info").withType(CameraInfo.class);
 * ROS2Topic<Image> depthImageTopic = depthTopic.appendedWith("image_raw").withType(Image.class);
 * ROS2Topic<CameraInfo> depthInfoTopic = depthTopic.appendedWith("camera_info").withType(CameraInfo.class);
 * } </pre>
 * To learn more about ROS topics, see:
 * <a href="https://design.ros2.org/articles/topic_and_service_names.html">Topic and Service name mapping to DDS - ROS 2 Design</a>
 */
public class ROS2Topic<T extends ROS2Message<T>>
{
   static
   {
      jros2.load();
   }

   private final Class<T> topicType;
   private final String topicName;
   private final int numberOfTokens;

   /**
    * Creates a blank topic with an empty name and no type.
    * <p>
    * This topic is not useful by itself.
    * Use the {@link #prependedWith(String)}, {@link #appendedWith(String)}, {@link #insert(int, String)}, and {@link #withType(Class)}
    * methods to create useful topics stemming from this one.
    */
   public ROS2Topic()
   {
      this(null);
   }

   /**
    * Creates a topic with a name and no type.
    *
    * @param topicName The topic name. Must satisfy constraints set by ROS 2.
    *                  See the "ROS 2 Topic and Service Name Constraints" section in:
    *                  <a href="https://design.ros2.org/articles/topic_and_service_names.html">Topic and Service name mapping to DDS</a>
    */
   public ROS2Topic(String topicName)
   {
      this(topicName, null);
   }

   /**
    * Creates a topic with a name and type.
    *
    * @param topicName The topic name. Must satisfy constraints set by ROS 2.
    *                  See the "ROS 2 Topic and Service Name Constraints" section in:
    *                  <a href="https://design.ros2.org/articles/topic_and_service_names.html">Topic and Service name mapping to DDS</a>
    * @param topicType The message type sent over this topic.
    */
   public ROS2Topic(String topicName, Class<T> topicType)
   {
      if (topicName == null || topicName.isEmpty())
      {
         this.topicName = "";
         numberOfTokens = 0;
      }
      else
      {
         if (!topicName.startsWith("/"))
         {
            LogTools.warn("Possible invalid topic name ({}). Topics must start with a leading forward slash (/).", topicName);
         }
         this.topicName = topicName;
         numberOfTokens = topicName.split("/").length - 1;
      }

      this.topicType = topicType;
   }

   /**
    * Creates a copy of this topic with a token appended to this topic's name.
    * <p>
    * This method does not modify {@code this} object.
    *
    * @param token The token to append. Should not start or end with a {@code /}.
    * @return A new topic object with the token appended to this topic's name.
    */
   public ROS2Topic<T> appendedWith(String token)
   {
      if (token == null || token.isEmpty())
      {
         return new ROS2Topic<>(topicName, topicType);
      }

      return new ROS2Topic<>(topicName + "/" + token, topicType);
   }

   /**
    * Creates a copy of this topic with a token prepended to this topic's name.
    * <p>
    * This method does not modify {@code this} object.
    *
    * @param token The token to prepend. Should not start or end with a {@code /}.
    * @return A new topic object with the token prepended to this topic's name.
    */
   public ROS2Topic<T> prependedWith(String token)
   {
      if (token == null || token.isEmpty())
      {
         return new ROS2Topic<>(topicName, topicType);
      }

      return new ROS2Topic<>("/" + token + topicName, topicType);
   }

   /**
    * Creates a copy of this topic with a token inserted into this topic's name at the given position.
    * <p>
    * This method does not modify {@code this} object.
    *
    * @param position    The position to insert the token at. Can be in the range of {@code [0, numberOfTokens]}.
    *                    <p> If {@code position == 0}, the behavior is identical to calling {@link #prependedWith(String)}.
    *                    <p> If {@code position == numberOfTokens}, the behavior is identical to calling {@link #appendedWith(String)}.
    * @param token The token to insert. Should not start or end with a {@code /}.
    * @return A new topic object with the token inserted into this topic's name at the given position.
    */
   public ROS2Topic<T> insert(int position, String token)
   {
      // Ensure good position was given
      if (position < 0 || position > numberOfTokens)
      {
         throw new IllegalArgumentException("Position must be in the range [0, numberOfTokens]");
      }

      // If inserting nothing, we can return early
      if (token == null || token.isEmpty())
      {
         return new ROS2Topic<>(topicName, topicType);
      }

      // Faster options for some insertion positions
      if (position == 0)
      {
         return prependedWith(token);
      }
      else if (position == numberOfTokens)
      {
         return appendedWith(token);
      }

      String[] tokens = topicName.split("/"); // Array of existing tokens. Note that tokens[0] == "".

      // Sanity check
      if (!tokens[0].equals(""))
      {
         LogTools.error("Malformed topic name: {}. Failed to insert token at position {}.", topicName, position);
         return this;
      }

      String[] newTokens = new String[numberOfTokens + 1]; // Array where we'll store new tokens
      for (int insert = 0, read = 1; insert < newTokens.length; ++insert, ++read)
      {
         if (insert == position) // If we're at the insertion position, insert the passed in token
         {
            newTokens[insert] = token;
            read--; // Decrement read position since we didn't read from the existing tokens
         }
         else // Not at insertion position. Just copy values over.
         {
            newTokens[insert] = tokens[read];
         }
      }

      return new ROS2Topic<>("/" + String.join("/", newTokens), getType());
   }

   /**
    * Creates a copy of this topic with the given type.
    *
    * @param topicType The topic type of the copy.
    * @param <U>       Type of messages sent over the topic.
    * @return A new topic object with the given type.
    */
   public <U extends ROS2Message<U>> ROS2Topic<U> withType(Class<U> topicType)
   {
      assert topicType != null;

      return new ROS2Topic<>(topicName, topicType);
   }

   /**
    * @return This topic's name.
    */
   public String getName()
   {
      return topicName;
   }

   /**
    * @return The number of tokens in this topic's name. Tokens are separated by {@code /}.
    */
   public int numberOfTokens()
   {
      return numberOfTokens;
   }

   /**
    * @return The type of messages sent over this topic.
    */
   public Class<T> getType()
   {
      return topicType;
   }
}
