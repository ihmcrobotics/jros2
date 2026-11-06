/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
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
package us.ihmc.jros2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import us.ihmc.fastddsjava.profiles.ProfilesXML;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static us.ihmc.jros2.PubSubTestTools.awaitCondition;
import static us.ihmc.jros2.PubSubTestTools.awaitMatched;
import static us.ihmc.jros2.PubSubTestTools.reliableKeepAll;
import static us.ihmc.jros2.PubSubTestTools.uniqueTopicName;

/**
 * Fast-DDS applies the library settings (intraprocess delivery) of every profile XML it loads to the whole process. jros2
 * nodes keep jros2's intraprocess mode ({@code FASTDDS_INTRAPROCESS_DELIVERY}) even when another component of the process
 * changes the process-wide default of {@link ProfilesXML} after jros2 started: every profile jros2 loads states jros2's
 * mode. (Before, a test of the XML layer that changed that default without restoring it switched the delivery mode of
 * every jros2 endpoint created later in the same JVM, which made the intraprocess delivery-thread assertions of
 * AsyncROS2PublisherTest and ROS2PublisherConcurrencyTest fail whenever Gradle ran a jros2 test class first.)
 */
public class IntraprocessModeIsolationTest
{
   @Test
   @Timeout(60)
   public void testJros2KeepsItsIntraprocessModeWhenTheProfilesXmlDefaultChanges()
   {
      // jros2 starts first (it sets the process-wide default to its mode), as in a process that uses jros2
      jros2.load();
      boolean intraprocess = jros2.get().intraprocessDelivery();
      String previous = ProfilesXML.getIntraprocessDelivery();
      // Another component of this process switches the process-wide default to the other mode
      ProfilesXML.setIntraprocessDelivery(intraprocess ? "OFF" : "FULL");
      ROS2Node node = new ROS2Node("intraprocess_isolation");
      try
      {
         ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("intraprocess_isolation"), example_interfaces.Int64.class);
         AtomicReference<Thread> callbackThread = new AtomicReference<>();
         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(topic, reader ->
         {
            example_interfaces.Int64 message = new example_interfaces.Int64();
            if (reader.read(message))
            {
               callbackThread.compareAndSet(null, Thread.currentThread());
            }
         }, reliableKeepAll());
         ROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic, reliableKeepAll());
         awaitMatched(publisher, subscription);

         example_interfaces.Int64 message = new example_interfaces.Int64();
         message.setData(42);
         publisher.publish(message);
         awaitCondition(() -> callbackThread.get() != null, 30_000, "message not delivered");
         if (intraprocess)
         {
            assertSame(Thread.currentThread(), callbackThread.get(), "jros2's intraprocess delivery (USER_DATA_ONLY) runs the callback inside publish()");
         }
         else
         {
            assertNotSame(Thread.currentThread(), callbackThread.get(), "without intraprocess delivery a Fast-DDS thread runs the callback");
         }
         assertEquals(intraprocess ? "USER_DATA_ONLY" : "OFF", ProfilesXML.getIntraprocessDelivery(), "jros2 set the process-wide default back to its mode");
      }
      finally
      {
         node.close();
         ProfilesXML.setIntraprocessDelivery(previous);
      }
   }
}
