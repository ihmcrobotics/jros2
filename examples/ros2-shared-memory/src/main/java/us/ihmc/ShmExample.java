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

import example_interfaces.msg.dds.Empty;
import us.ihmc.fastddsjava.profiles.TransportDescriptorTypeTools;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2Publisher;
import us.ihmc.jros2.ROS2Topic;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Demo on creating a ROS2Node with only Shared Memory Transport.
 */
public class ShmExample
{
   public static void main(String[] args) throws InterruptedException
   {
      int domainId = 0;
      TransportDescriptorType shmTransport = TransportDescriptorTypeTools.createSHMDescriptor();
      ROS2Node shmNode = new ROS2Node("shm_node", domainId, shmTransport);
      ROS2Topic<Empty> emptyTopic = new ROS2Topic<>("/empty_test_topic", Empty.class);
      ROS2Publisher<Empty> publisher = shmNode.createPublisher(emptyTopic);

      Thread publishThread = new Thread(() ->
      {
         while (!publisher.isClosed())
         {
            publisher.publish(new Empty());

            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
         }
      }, "PublishThread");
      publishThread.start();

      shmNode.createSubscription(emptyTopic, reader -> System.out.println("Received empty message"));

      LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));

      shmNode.close();

      publishThread.join();
   }
}
