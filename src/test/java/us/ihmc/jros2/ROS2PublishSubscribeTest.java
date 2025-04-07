package us.ihmc.jros2;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.jros2.testmessages.Bool;

import java.util.function.Consumer;

public class ROS2PublishSubscribeTest
{
   static
   {
      fastddsjavaNativeLibrary.load();
   }

   @Test
   public void publishSubscribeTest() throws InterruptedException
   {
      ROS2Node ros2Node = new ROS2Node("test_node", 113);
      ROS2Topic<Bool> topic = new ROS2Topic<>(Bool.class, "rt/ihmc/test_bool");
      ROS2Publisher<Bool> publisher = ros2Node.createPublisher(topic, ROS2QoSProfile.DEFAULT);

      Bool bool = new Bool();
      bool.setData(true);

      int iter = 0;
      while (iter < 10000)
      {
         publisher.publish(bool);

         System.out.println("Wrote");

         iter++;

         Thread.sleep(1000);
      }

      ros2Node.close();
   }

   @Test
   public void subscribeTest() throws InterruptedException
   {
      ROS2Node ros2Node = new ROS2Node("test_node", 113);
      ROS2Topic<Bool> topic = new ROS2Topic<>(Bool.class, "rt/ihmc/test_bool");
      ROS2Subscription<Bool> subscription = ros2Node.createSubscription(topic, (ROS2SubscriptionCallback<Bool>) reader ->
      {
         Bool msg = new Bool();
         reader.takeNextSample(msg);

         System.out.println(msg.getData());

      }, ROS2QoSProfile.DEFAULT);

      Thread.sleep(10000000);
   }
}
