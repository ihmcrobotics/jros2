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

public class TalkerListener
{
   public static void main(String[] args) throws InterruptedException
   {
      ROS2Node publisherNode = new ROS2Node("minimal_publisher", 0);
      Runtime.getRuntime().addShutdownHook(new Thread(publisherNode::close, "Shutdown"));
      ROS2Topic<std_msgs.msg.dds.String> topic = new ROS2Topic<>("/topic", std_msgs.msg.dds.String.class);
      ROS2Publisher<std_msgs.msg.dds.String> publisher = publisherNode.createPublisher(topic);

      Thread publishThread = new Thread(new Runnable()
      {
         private int count;

         @Override
         public void run()
         {
            while (!publisherNode.isClosed())
            {
               std_msgs.msg.dds.String message = new std_msgs.msg.dds.String();
               message.getData().append("Hello, world! ").append(count++);
               System.out.printf("Publishing: '%s'%n", message.getData().toString());

               publisher.publish(message);

               LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(500));
            }
         }
      }, "PublishThread");

      publishThread.start();
      publishThread.join();
   }
}
