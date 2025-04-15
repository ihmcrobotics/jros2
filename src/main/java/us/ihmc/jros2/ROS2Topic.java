package us.ihmc.jros2;

/**
 * A record to map a "topic type" AKA some specific {@link ROS2Message} type to topic name.
 */
public record ROS2Topic<T extends ROS2Message<T>>(Class<T> topicType, String topicName)
{
   static
   {
      jros2.load();
   }
}
