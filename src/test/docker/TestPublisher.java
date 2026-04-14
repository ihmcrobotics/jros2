import us.ihmc.jros2.*;

/**
 * Simple test publisher for Docker integration tests.
 * Publishes test messages on /test topic.
 */
public class TestPublisher
{
   public static void main(java.lang.String[] args) throws InterruptedException
   {
      System.out.println("Starting test publisher...");

      ROS2Node node = new ROS2Node("test_publisher");
      ROS2Topic<std_msgs.String> topic = new ROS2Topic<>("/test", std_msgs.String.class);
      ROS2Publisher<std_msgs.String> publisher = node.createPublisher(topic);

      std_msgs.String message = new std_msgs.String();
      message.setData("Hello from test publisher");

      System.out.println("Publishing messages every second...");

      for (int i = 0; i < 30; i++)
      {
         message.setData("Test message " + i);
         publisher.publish(message);
         System.out.println("Published: " + message.getData());
         Thread.sleep(1000);
      }

      node.close();
   }
}
