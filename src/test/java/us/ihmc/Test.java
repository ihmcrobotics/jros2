package us.ihmc;

import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2Publisher;
import us.ihmc.jros2.ROS2Topic;

public class Test
{
   public static void main(String[] args) throws InterruptedException
   {
      System.setProperty("ros.domain.id", "55");
      System.setProperty("fastdds.interface.whitelist", "10.100.4.76");

      // Test that it works - subscriber should receive messages

      ROS2Topic<std_msgs.String> testTopic = new ROS2Topic<>("/test_topic", std_msgs.String.class);

      ROS2Node publisherNode = new ROS2Node("publisher_node");
      ROS2Publisher<std_msgs.String> publisher = publisherNode.createPublisher(testTopic);

      ROS2Node subscriberNode = new ROS2Node("subscriber_node");
      subscriberNode.createSubscriptionSampler(testTopic, message -> System.out.println("Received message: " + message.getData()));

      // Give time for discovery to complete
      Thread.sleep(1000);
      System.out.println("Starting to publish messages...");

      for (int i = 0; i < 10000; i++)
      {
         std_msgs.String message = new std_msgs.String();
         message.getData().append("Hello " + i);
         publisher.publish(message);

         System.out.println("Published message " + i);

         Thread.sleep(1000);
      }

      publisherNode.close();
      subscriberNode.close();
   }
}
