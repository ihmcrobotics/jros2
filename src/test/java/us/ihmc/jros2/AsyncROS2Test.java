package us.ihmc.jros2;

import org.junit.jupiter.api.Test;
import us.ihmc.jros2.ROS2QoSProfile.Durability;
import us.ihmc.jros2.msg.Bool;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncROS2Test
{
   @Test
   public void testAsyncROS2Publisher() throws IOException, InterruptedException
   {
      boolean expectedValue = true;
      final String topicName = "/ihmc/test_bool";

      AsyncROS2Node asyncNode = new AsyncROS2Node("test_async_node");
      ROS2Topic<Bool> topic = new ROS2Topic<>(Bool.class, "rt" + topicName);

      ROS2QoSProfile qosProfile = new ROS2QoSProfile();
      qosProfile.durability(Durability.TRANSIENT_LOCAL);
      ROS2Publisher<Bool> publisher = asyncNode.createPublisher(topic, qosProfile);

      Bool bool = new Bool();
      bool.setData(expectedValue);
      publisher.publish(bool);

      String result = ROS2TestTools.ros2EchoOnce(asyncNode.getDomainId(), topicName);

      // Ensure the value received by ros2 matches the value we published
      assertTrue(result.contains(String.valueOf(expectedValue)), result);

      // Close stuff
      asyncNode.destroyPublisher(publisher);
      asyncNode.close();
   }

   @Test
   public void testPublishingManyMessages() throws InterruptedException
   {
      int messagesToPublish = 100;
      AtomicInteger messagesReceived = new AtomicInteger(0);
      boolean publish = true;
      AtomicBoolean expectedValue = new AtomicBoolean(publish);
      final String topicName = "/ihmc/test_bool";

      AsyncROS2Node asyncNode = new AsyncROS2Node("test_async_node");
      ROS2Topic<Bool> topic = new ROS2Topic<>(Bool.class, "rt" + topicName);

      ROS2Subscription<Bool> subscription = asyncNode.createSubscription(topic, reader ->
      {
         Bool value = reader.read();
         assertEquals(expectedValue.get(), value.getData());
         expectedValue.set(!expectedValue.get());

         if (messagesReceived.incrementAndGet() > messagesToPublish)
         {
            synchronized (messagesReceived)
            {
               messagesReceived.notify();
            }
         }
      });

      ROS2Publisher<Bool> publisher = asyncNode.createPublisher(topic);

      Bool bool = new Bool();
      for (int i = 0; i < messagesToPublish; ++i)
      {
         bool.setData(publish);
         publisher.publish(bool);
         publish = !publish;
         Thread.sleep(5);
      }

      synchronized (messagesReceived)
      {
         messagesReceived.wait(5000);
      }

      assertEquals(messagesToPublish, messagesReceived.get());

      // Close stuff
      asyncNode.destroySubscription(subscription);
      asyncNode.destroyPublisher(publisher);
      asyncNode.close();
   }
}
