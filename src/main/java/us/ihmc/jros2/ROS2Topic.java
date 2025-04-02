package us.ihmc.jros2;

public record ROS2Topic<T extends ROS2Message<T>>(Class<T> topicType, String topicName)
{
}
