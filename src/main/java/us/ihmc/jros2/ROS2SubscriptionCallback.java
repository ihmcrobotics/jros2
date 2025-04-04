package us.ihmc.jros2;

public interface ROS2SubscriptionCallback<T extends ROS2Message<T>>
{
   void onMessage(ROS2SubscriptionReader<T> reader);
}
