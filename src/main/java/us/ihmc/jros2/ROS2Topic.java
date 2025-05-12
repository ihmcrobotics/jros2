package us.ihmc.jros2;

/**
 * A record to map a "topic type" AKA some specific {@link ROS2Message} type to topic name.
 */
public class ROS2Topic<T extends ROS2Message<T>>
{
   static
   {
      jros2.load();
   }

   private final Class<T> topicType;
   private final String topicName;
   private final String rtPrefixedName;
   private final int numberOfSegments; // not including "rt" segment

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
    *                  <p>
    *                  See the "ROS 2 Topic and Servicec Name Constrains" section in:
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
    *                  <p>
    *                  See the "ROS 2 Topic and Servicec Name Constrains" section in:
    *                  <a href="https://design.ros2.org/articles/topic_and_service_names.html">Topic and Service name mapping to DDS</a>
    * @param topicType The message type sent over this topic.
    */
   public ROS2Topic(String topicName, Class<T> topicType)
   {
      // Treat null as empty string
      if (topicName == null)
      {
         topicName = "";
      }

      if (topicName.isEmpty())
      {
         this.topicName = "";
         numberOfSegments = 0;
      }
      else
      {
         this.topicName = topicName;
         numberOfSegments = topicName.split("/").length - 1;
      }

      rtPrefixedName = "rt" + this.topicName;
      this.topicType = topicType;
   }

   /**
    * Creates a copy of this topic with a name segment appended to this topic's name.
    * <p>
    * This method does NOT modify {@code this} object.
    *
    * @param nameSegment The name segment to append. Should not start or end with a "/".
    * @return A new topic object with the name segment appended to this topic's name.
    */
   public ROS2Topic<T> appendedWith(String nameSegment)
   {
      if (nameSegment == null || nameSegment.isEmpty())
      {
         return new ROS2Topic<>(topicName, topicType);
      }

      return new ROS2Topic<>(topicName + "/" + nameSegment, topicType);
   }

   /**
    * Creates a copy of this topic with a name segment prepended to this topic's name.
    * <p>
    * This method does NOT modify {@code this} object.
    *
    * @param nameSegment The name segment to prepend. Should not start or end with a "/".
    * @return A new topic object with the name segment prepended to this topic's name.
    */
   public ROS2Topic<T> prependedWith(String nameSegment)
   {
      if (nameSegment == null || nameSegment.isEmpty())
      {
         return new ROS2Topic<>(topicName, topicType);
      }

      return new ROS2Topic<>("/" + nameSegment + topicName, topicType);
   }

   /**
    * Creates a copy of this topic with a name segment inserted into this topic's name at the given position.
    * <p>
    * This method does NOT modify {@code this} object.
    *
    * @param position    The position to insert the name segment at. Can be in the range of {@code [0, numberOfSegments]}.
    *                    <p> If {@code position == 0}, the behavior is identical to calling {@link #prependedWith(String)}.
    *                    <p> If {@code position == numberOfSegments}, the behavior is identical to calling {@link #appendedWith(String)}.
    * @param nameSegment The name segment to insert. Should not start or end with a "/".
    * @return A new topic object with the name segment inserted into this topic's name at the given position.
    */
   public ROS2Topic<T> insert(int position, String nameSegment)
   {
      // Ensure good position was given
      if (position < 0 || position > numberOfSegments)
      {
         throw new IllegalArgumentException("Position must be in the range [0, numberOfSegments]");
      }

      // If inserting nothing, we can return early
      if (nameSegment == null || nameSegment.isEmpty())
      {
         return new ROS2Topic<>(topicName, topicType);
      }

      // Faster options for some insertion positions
      if (position == 0)
      {
         return prependedWith(nameSegment);
      }
      else if (position == numberOfSegments)
      {
         return appendedWith(nameSegment);
      }

      String[] segments = topicName.split("/"); // Array of existing segments. Note that segments[0] == "".
      String[] newSegments = new String[numberOfSegments + 1]; // Array where we'll store new segments
      for (int insert = 0, read = 1; insert < newSegments.length; ++insert, ++read)
      {
         if (insert == position) // If we're at the insertion position, insert the passed in name segment
         {
            newSegments[insert] = nameSegment;
            read--; // Decrement read position since we didn't read from the existing segments
         }
         else // Not at insertion position. Just copy values over.
         {
            newSegments[insert] = segments[read];
         }
      }

      return new ROS2Topic<>("/" + String.join("/", newSegments), getType());
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
    * @return This topic's FastDDS name (i.e. this topic's name with "rt" prefixed)
    */
   public String getFastDDSTopicName()
   {
      return rtPrefixedName;
   }

   /**
    * @return The number of segments in this topic's name. Segments are separated by "/".
    */
   public int numberOfSegments()
   {
      return numberOfSegments;
   }

   /**
    * @return The type of messages sent over this topic.
    */
   public Class<T> getType()
   {
      return topicType;
   }
}
