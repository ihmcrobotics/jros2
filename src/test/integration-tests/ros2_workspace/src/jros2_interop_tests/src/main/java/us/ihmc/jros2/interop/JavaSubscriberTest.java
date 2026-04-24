package us.ihmc.jros2.interop;

import std_msgs.String_;
import std_msgs.Int32;
import std_msgs.Bool;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2Subscription;
import us.ihmc.jros2.ROS2Topic;

public class JavaSubscriberTest
{
   private static volatile int stringCount = 0;
   private static volatile int int32Count = 0;
   private static volatile int boolCount = 0;

   public static void main(java.lang.String[] args) throws InterruptedException
   {
      int domainId = Integer.parseInt(System.getenv().getOrDefault("ROS_DOMAIN_ID", "200"));
      System.out.println("Starting Java Subscriber Test (Domain: " + domainId + ")");

      ROS2Node node = new ROS2Node("java_subscriber_test", domainId);

      ROS2Topic<String_> stringTopic = new ROS2Topic<>("/test/string", String_.class);
      ROS2Topic<Int32> int32Topic = new ROS2Topic<>("/test/int32", Int32.class);
      ROS2Topic<Bool> boolTopic = new ROS2Topic<>("/test/bool", Bool.class);

      ROS2Subscription<String_> stringSubscription = node.createSubscription(stringTopic, reader -> {
         String_ message = reader.read();
         stringCount++;
         System.out.println("Received String: " + message.getData() + " (count: " + stringCount + ")");
      });

      ROS2Subscription<Int32> int32Subscription = node.createSubscription(int32Topic, reader -> {
         Int32 message = reader.read();
         int32Count++;
         System.out.println("Received Int32: " + message.getData() + " (count: " + int32Count + ")");
      });

      ROS2Subscription<Bool> boolSubscription = node.createSubscription(boolTopic, reader -> {
         Bool message = reader.read();
         boolCount++;
         System.out.println("Received Bool: " + message.getData() + " (count: " + boolCount + ")");
      });

      System.out.println("Subscriptions created, waiting for messages...");

      long startTime = System.currentTimeMillis();
      while (System.currentTimeMillis() - startTime < 30000)
      {
         Thread.sleep(100);

         if (stringCount >= 5 && int32Count >= 5 && boolCount >= 5)
         {
            System.out.println("SUCCESS: Received all expected messages");
            break;
         }
      }

      System.out.println("Final counts - String: " + stringCount + ", Int32: " + int32Count + ", Bool: " + boolCount);

      node.close();
      System.out.println("Java Subscriber Test Complete");
   }
}
