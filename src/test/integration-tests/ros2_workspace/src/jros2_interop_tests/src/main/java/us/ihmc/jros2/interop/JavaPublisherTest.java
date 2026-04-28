package us.ihmc.jros2.interop;

import std_msgs.String_;
import std_msgs.Int32;
import std_msgs.Bool;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2Publisher;
import us.ihmc.jros2.ROS2Topic;

public class JavaPublisherTest
{
   public static void main(java.lang.String[] args) throws InterruptedException
   {
      int domainId = Integer.parseInt(System.getenv().getOrDefault("ROS_DOMAIN_ID", "200"));
      System.out.println("Starting Java Publisher Test (Domain: " + domainId + ")");

      ROS2Node node = new ROS2Node("java_publisher_test", domainId);

      ROS2Topic<String_> stringTopic = new ROS2Topic<>("/test/string", String_.class);
      ROS2Topic<Int32> int32Topic = new ROS2Topic<>("/test/int32", Int32.class);
      ROS2Topic<Bool> boolTopic = new ROS2Topic<>("/test/bool", Bool.class);

      ROS2Publisher<String_> stringPublisher = node.createPublisher(stringTopic);
      ROS2Publisher<Int32> int32Publisher = node.createPublisher(int32Topic);
      ROS2Publisher<Bool> boolPublisher = node.createPublisher(boolTopic);

      System.out.println("Publishers created, starting to publish...");

      for (int i = 0; i < 10; ++i)
      {
         String_ stringMsg = new String_();
         stringMsg.setData("Java message " + i);
         stringPublisher.publish(stringMsg);

         Int32 int32Msg = new Int32();
         int32Msg.setData(i * 100);
         int32Publisher.publish(int32Msg);

         Bool boolMsg = new Bool();
         boolMsg.setData(i % 2 == 0);
         boolPublisher.publish(boolMsg);

         System.out.println("Published message set " + i);

         Thread.sleep(500);
      }

      System.out.println("Publishing complete, keeping node alive for 2 seconds...");
      Thread.sleep(2000);

      node.close();
      System.out.println("Java Publisher Test Complete");
   }
}
