package us.ihmc.jros2;

import java.util.Objects;

public record ROS2Topic<T extends ROS2Message<T>>(Class<T> topicType, String topicName)
{
   @Override
   public boolean equals(Object o)
   {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;
      ROS2Topic<?> ros2Topic = (ROS2Topic<?>) o;
      return Objects.equals(topicType, ros2Topic.topicType) && Objects.equals(topicName, ros2Topic.topicName);
   }
}
