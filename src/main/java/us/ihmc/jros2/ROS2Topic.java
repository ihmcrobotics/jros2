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

   public ROS2Topic()
   {
      this(null);
   }

   public ROS2Topic(String topicName)
   {
      this(topicName, null);
   }

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

   public ROS2Topic<T> appendedWith(String nameSegment)
   {
      if (nameSegment == null || nameSegment.isEmpty())
      {
         return new ROS2Topic<>(topicName, topicType);
      }

      return new ROS2Topic<>(topicName + "/" + nameSegment, topicType);
   }

   public ROS2Topic<T> prependedWith(String nameSegment)
   {
      if (nameSegment == null || nameSegment.isEmpty())
      {
         return new ROS2Topic<>(topicName, topicType);
      }

      return new ROS2Topic<>("/" + nameSegment + topicName, topicType);
   }

   public ROS2Topic<T> insert(int position, String nameSegment)
   {
      // Ensure good position was given
      if (position < 0 || position > numberOfSegments)
      {
         throw new IllegalArgumentException("Position must be greater than or equal to 0.");
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

   public <U extends ROS2Message<U>> ROS2Topic<U> withType(Class<U> topicType)
   {
      assert topicType != null;

      return new ROS2Topic<>(topicName, topicType);
   }

   public String getName()
   {
      return topicName;
   }

   public String getFastDDSTopicName()
   {
      return rtPrefixedName;
   }

   public int numberOfSegments()
   {
      return numberOfSegments;
   }

   public Class<T> getType()
   {
      return topicType;
   }
}
