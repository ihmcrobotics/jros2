package us.ihmc;

import my_interfaces.msg.dds.MyPoint3DList;
import us.ihmc.jros2.AsyncROS2Node;
import us.ihmc.jros2.ROS2Publisher;
import us.ihmc.jros2.ROS2Topic;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class CustomMessageClassTest
{
   public static void main(String[] args)
   {
      AsyncROS2Node asyncNode = new AsyncROS2Node("test_node");
      ROS2Topic<MyPoint3DList> pointListTopic = new ROS2Topic<>("/point_list", MyPoint3DList.class);

      asyncNode.createSubscriptionSampler(pointListTopic, sample ->
      {
         System.out.println("GOT THE MSG");


      });

      ROS2Publisher<MyPoint3DList> publisher = asyncNode.createPublisher(pointListTopic);

      Random random = new Random();

      MyPoint3DList pointList = new MyPoint3DList();

      MyPoint3DMessage point = new MyPoint3DMessage();
      point.getData().setX(random.nextDouble());
      point.getData().setY(random.nextDouble());
      point.getData().setZ(random.nextDouble());
      pointList.getPointList().add(point);

      publisher.publish(pointList);

      LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(4));

      asyncNode.close();
   }
}
