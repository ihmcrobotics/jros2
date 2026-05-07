package us.ihmc;

import my_interfaces.MyPoint3DList;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2Publisher;
import us.ihmc.jros2.ROS2Topic;

import java.util.Random;

public class CustomMessageClassTest
{
   public static void main(String[] args)
   {
      System.setProperty("fastdds.intraprocess.delivery", "true");

      final MyPoint3D[] points = points();

      ROS2Node node = new ROS2Node("test_node");
      ROS2Topic<MyPoint3DList> pointListTopic = new ROS2Topic<>("/point_list", MyPoint3DList.class);

      node.createSubscription(pointListTopic, reader ->
      {
         MyPoint3DList pointList = reader.read();

         for (int i = 0; i < pointList.getPointList().size(); i++)
         {
            MyPoint3D point = pointList.getPointList().get(i).getSrc();

            if (!points[i].equals(point))
            {
               throw new RuntimeException("Mismatched data");
            }
         }

         System.out.println("All data received by the subscription matched what was published.");
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

      publisher.publish(pointList);

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
