package us.ihmc;

import my_interfaces.MyPoint3DList;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2Publisher;
import us.ihmc.jros2.ROS2Topic;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CustomMessageClassTest
{
   public static void main(String[] args) throws InterruptedException
   {
      System.setProperty("fastdds.intraprocess.delivery", "true");

      final MyPoint3D[] points = points();
      CountDownLatch received = new CountDownLatch(1);

      ROS2Node node = new ROS2Node("test_node");
      ROS2Topic<MyPoint3DList> pointListTopic = new ROS2Topic<>("/point_list", MyPoint3DList.class);

      node.createSubscription(pointListTopic, reader ->
      {
         MyPoint3DList pointList = reader.read();
         if (pointList == null)
         {
            return;
         }

         for (int i = 0; i < pointList.getPointList().size(); i++)
         {
            MyPoint3D point = pointList.getPointList().get(i).getSrc();

            if (!points[i].equals(point))
            {
               throw new RuntimeException("Mismatched data");
            }
         }

         System.out.println("All data received by the subscription matched what was published.");
         received.countDown();
      });
      ROS2Publisher<MyPoint3DList> publisher = node.createPublisher(pointListTopic);

      MyPoint3DList pointList = new MyPoint3DList();
      // Add all points to the point list
      {
         for (MyPoint3D point : points)
         {
            pointList.getPointList().add(new MyPoint3DMessage(point));
         }
      }

      // Both endpoints use ROS2QoSProfile.DEFAULT (RELIABLE, VOLATILE, KEEP_LAST 10, like ROS 2). A VOLATILE publisher only
      // delivers to subscriptions it has already matched, so wait for the match before publishing.
      if (!publisher.waitForSubscription(5000))
      {
         System.err.println("The subscription was not matched within 5 s");
      }
      publisher.publish(pointList);

      if (!received.await(5, TimeUnit.SECONDS))
      {
         System.err.println("No message received within 5 s");
      }

      node.close();
   }

   private static MyPoint3D[] points()
   {
      MyPoint3D[] points = new MyPoint3D[100];

      Random random = new Random();

      for (int i = 0; i < points.length; i++)
      {
         MyPoint3D point = new MyPoint3D();

         point.setX(random.nextDouble());
         point.setY(random.nextDouble());
         point.setZ(random.nextDouble());

         points[i] = point;
      }

      return points;
   }
}
