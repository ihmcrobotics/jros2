/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.jros2.r8check;

import us.ihmc.jros2.ROS2Message;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2Publisher;
import us.ihmc.jros2.ROS2QoSProfile;
import us.ihmc.jros2.ROS2Subscription;
import us.ihmc.jros2.ROS2Topic;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A tiny "app" that uses jros2 the way an Android app does. validate.sh shrinks and obfuscates it together with jros2 with
 * R8, with and without android/consumer-rules.pro, and runs the result on the JVM. Each check exercises one runtime name
 * lookup that the consumer rules must protect; the exit code is the number of failed checks.
 */
public final class R8Sample
{
   private static int failures = 0;

   private R8Sample()
   {
   }

   private interface Check
   {
      String run() throws Exception;
   }

   private static void check(String what, Check check)
   {
      String result;
      try
      {
         result = check.run();
      }
      catch (Throwable t)
      {
         result = "FAIL " + t;
      }
      if (!result.startsWith("OK"))
      {
         failures++;
      }
      System.out.println("[r8-check] " + what + ": " + result);
   }

   public static void main(String[] args) throws Exception
   {
      boolean expectTypeHash = Boolean.getBoolean("expect.typeHash");
      int domainId = Integer.parseInt(System.getProperty("ros.domain.id", "176"));

      check("Class.getField(\"name\") on std_msgs.String_", () ->
      {
         String name = ROS2Message.getNameFromMessageClass(std_msgs.String_.class);
         return "std_msgs::msg::dds_::String_".equals(name) ? "OK" : "FAIL name=" + name;
      });

      check("Class.getField(\"typeHash\") on std_msgs.String_", () ->
      {
         String typeHash = ROS2Message.getTypeHashFromMessageClass(std_msgs.String_.class);
         if (!expectTypeHash)
         {
            return "OK (skipped: this jros2 jar has no typeHash fields)";
         }
         return typeHash != null && typeHash.startsWith("RIHS01_") ? "OK" : "FAIL typeHash=" + typeHash;
      });

      check("getDeclaredConstructor().newInstance() on a message only referenced as a class literal", () ->
      {
         std_msgs.ColorRGBA color = ROS2Message.createInstance(std_msgs.ColorRGBA.class);
         return color != null ? "OK" : "FAIL createInstance returned null";
      });

      check("getDeclaredMethods() finds the std_msgs.Header getter of sensor_msgs.Image", () ->
         ROS2Message.getHeaderMethod(sensor_msgs.Image.class) != null ? "OK" : "FAIL no Header getter");

      check("IDLObjectSequence creates sensor_msgs.PointField elements reflectively", () ->
      {
         sensor_msgs.PointCloud2 cloud = new sensor_msgs.PointCloud2();
         cloud.getFields().add().getName().append("x");
         return cloud.getFields().size() == 1 ? "OK" : "FAIL";
      });

      check("JNI natives + fastddsjavaCallback.call() (pub/sub loopback on domain " + domainId + ")", () ->
      {
         ROS2Node node = new ROS2Node("r8_sample", domainId);
         try
         {
            ROS2QoSProfile qos = new ROS2QoSProfile();
            qos.reliability(ROS2QoSProfile.Reliability.RELIABLE);
            qos.history(ROS2QoSProfile.History.KEEP_LAST);
            ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>("/r8_check/chatter", std_msgs.String_.class);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> received = new AtomicReference<>();
            ROS2Subscription<std_msgs.String_> subscription = node.createSubscription(topic, reader ->
            {
               std_msgs.String_ message = reader.read();
               if (message != null && received.compareAndSet(null, message.getDataAsString()))
               {
                  latch.countDown();
               }
            }, qos);
            ROS2Publisher<std_msgs.String_> publisher = node.createPublisher(topic, qos);
            if (!publisher.waitForSubscription(10_000) || !subscription.waitForPublisher(10_000))
            {
               return "FAIL endpoints did not match";
            }
            std_msgs.String_ message = new std_msgs.String_();
            message.getData().append("hello from a shrunk app");
            for (int i = 0; i < 50 && latch.getCount() > 0; ++i)
            {
               publisher.publish(message);
               latch.await(100, TimeUnit.MILLISECONDS);
            }
            return "hello from a shrunk app".equals(received.get()) ? "OK" : "FAIL received=" + received.get();
         }
         finally
         {
            node.close();
         }
      });

      System.out.println("[r8-check] " + (failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED"));
      System.exit(failures);
   }
}
