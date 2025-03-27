package us.ihmc.ros2.testmessages;

import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;

import java.nio.ByteBuffer;

public abstract class ROS2Message
{
   public abstract int calculateSize();

   public abstract void serialize(ByteBuffer buffer);

   public abstract void deserialize(ByteBuffer buffer);

   public void packTopicDataWrapper(fastddsjava_TopicDataWrapper topicDataWrapper, ByteBuffer buffer)
   {
      serialize(buffer);
      topicDataWrapper.data_vector().put(buffer.array());
   }
}
