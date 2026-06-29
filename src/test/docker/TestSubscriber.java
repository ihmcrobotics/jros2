import us.ihmc.jros2.*;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple test subscriber for Docker integration tests.
 * Subscribes to /test topic and counts received messages.
 */
public class TestSubscriber
{
   public static void main(java.lang.String[] args) throws InterruptedException
   {
      System.out.println("Starting test subscriber...");

      ROS2Node node = new ROS2Node("test_subscriber");
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>("/test", std_msgs.String_.class);

      AtomicInteger messageCount = new AtomicInteger(0);

      node.createSubscription(topic, reader -> {
         std_msgs.String_ msg = reader.read();
         if (msg != null)
         {
            int count = messageCount.incrementAndGet();
            System.out.println("Received (" + count + "): " + msg.getData());
         }
      });

      System.out.println("Waiting for messages for 10 seconds...");
      Thread.sleep(10000);

      int finalCount = messageCount.get();
      System.out.println("\nTotal messages received: " + finalCount);

      node.close();

      // Exit with status based on message count
      // Expected: > 0 messages on whitelisted network, 0 on blocked network
      if (finalCount > 0)
      {
         System.out.println("SUCCESS: Received messages");
         System.exit(0);
      }
      else
      {
         System.out.println("NO MESSAGES: Network likely blocked");
         System.exit(1);
      }
   }
}
