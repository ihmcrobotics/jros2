package us.ihmc.jros2;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.jros2.testmessages.Bool;

public class ROS2PublishSubscribeTest
{
   static
   {
      fastddsjavaNativeLibrary.load();
   }

   @Test
   public void publishSubscribeTest() throws InterruptedException
   {
      ROS2Node ros2Node = new ROS2Node();
      ROS2Topic<Bool> topic = new ROS2Topic<>(Bool.class, "rt/ihmc/test_bool");
      ROS2Publisher publisher = ros2Node.createPublisher(topic, ROS2QoSProfile.DEFAULT);

      Bool bool = new Bool();
      bool.setData(true);

      int iter = 0;
      while (iter < 10000)
      {
         publisher.publish(bool);

         iter++;

         Thread.sleep(1000);
      }

      ros2Node.close();
   }
}
