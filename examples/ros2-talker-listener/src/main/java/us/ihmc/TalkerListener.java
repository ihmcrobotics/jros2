/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc;

import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2Publisher;
import us.ihmc.jros2.ROS2Topic;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/*
 * A basic talker and listener example.
 * See: https://docs.ros.org/en/humble/Tutorials/Beginner-Client-Libraries/Writing-A-Simple-Cpp-Publisher-And-Subscriber.html
 */
public class TalkerListener
{
   public static void main(String[] args) throws InterruptedException
   {
      /*
       * Create a topic with type example_interfaces/String
       */
      ROS2Topic<example_interfaces.msg.dds.String> topic = new ROS2Topic<>("/chatter", example_interfaces.msg.dds.String.class);

      /*
       * Set up the subscription
       */
      ROS2Node subscriptionNode = new ROS2Node("minimal_subscriber", 0); // Make sure to .close() the ROS2Node when done using it!
      Runtime.getRuntime().addShutdownHook(new Thread(subscriptionNode::close, "SubscriptionShutdown"));
      subscriptionNode.createSubscription(topic, reader ->
      {
         // Subscription callback
         System.out.printf("I heard: '%s'%n", reader.read().getData());
      });

      /*
       * Set up the publisher
       */
      ROS2Node publisherNode = new ROS2Node("minimal_publisher", 0); // Make sure to .close() the ROS2Node when done using it!
      Runtime.getRuntime().addShutdownHook(new Thread(publisherNode::close, "PublisherShutdown"));
      ROS2Publisher<example_interfaces.msg.dds.String> publisher = publisherNode.createPublisher(topic);
      Thread publishThread = new Thread(new Runnable()
      {
         private int count;

         @Override
         public void run()
         {
            while (!publisherNode.isClosed())
            {
               example_interfaces.msg.dds.String message = new example_interfaces.msg.dds.String();
               message.getData().append("Hello world: ").append(count++);
               System.out.printf("Publishing: '%s'%n", message.getData());

               publisher.publish(message);

               LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(500));
            }
         }
      }, "PublishThread");

      publishThread.start();
      publishThread.join();
   }
}
