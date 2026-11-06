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
package us.ihmc;

import us.ihmc.fastddsjava.natives.fastddsjava;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2Publisher;
import us.ihmc.jros2.ROS2QoSProfile;
import us.ihmc.jros2.ROS2Subscription;
import us.ihmc.jros2.ROS2Topic;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Native check: loads the bundled natives for the current platform (e.g. musl or riscv64), then sends a string and
 * an image through Fast-DDS in one process. Used by CI on platforms where the full test suite is too slow (QEMU) or
 * which only need a quick "do the natives work here" check. Exit code 0 means every step worked.
 * <p>
 * Arguments: {@code --expect-security} additionally requires natives built with DDS-Security,
 * {@code --expect-abi <n>} requires the given native ABI version.
 */
public final class NativeCheck
{
   private NativeCheck()
   {
   }

   public static void main(String[] args) throws Exception
   {
      List<String> arguments = Arrays.asList(args);
      System.out.printf("jros2 native check on %s %s (%s), Java %s %s%n", System.getProperty("os.name"), System.getProperty("os.arch"),
                        System.getProperty("os.version"), System.getProperty("java.version"), System.getProperty("java.vendor"));

      int domainId = Integer.parseInt(System.getenv().getOrDefault("ROS_DOMAIN_ID", "0"));
      try (ROS2Node node = new ROS2Node("jros2_native_check", domainId))
      {
         int abiVersion = fastddsjava.abiVersion();
         boolean security = fastddsjava.hasSecurity();
         System.out.println("natives loaded: ABI v" + abiVersion + ", DDS-Security " + (security ? "available" : "not built in"));
         int expectAbi = arguments.indexOf("--expect-abi");
         if (expectAbi >= 0 && abiVersion != Integer.parseInt(arguments.get(expectAbi + 1)))
         {
            fail("expected native ABI v" + arguments.get(expectAbi + 1) + " but found v" + abiVersion);
         }
         if (arguments.contains("--expect-security") && !security)
         {
            fail("natives were built without DDS-Security");
         }

         ROS2QoSProfile qos = new ROS2QoSProfile();
         qos.reliability(ROS2QoSProfile.Reliability.RELIABLE);
         qos.history(ROS2QoSProfile.History.KEEP_LAST);
         qos.depth(10);

         // 1. std_msgs/String
         ROS2Topic<std_msgs.String_> chatter = new ROS2Topic<>("/jros2_check/chatter", std_msgs.String_.class);
         AtomicReference<String> text = new AtomicReference<>();
         ROS2Subscription<std_msgs.String_> textSubscription = node.createSubscription(chatter, reader ->
         {
            std_msgs.String_ message = reader.read();
            if (message != null)
            {
               text.compareAndSet(null, message.getDataAsString());
            }
         }, qos);
         ROS2Publisher<std_msgs.String_> textPublisher = node.createPublisher(chatter, qos);
         awaitMatch(textPublisher, textSubscription);
         std_msgs.String_ message = new std_msgs.String_();
         message.getData().append("hello from ").append(System.getProperty("os.arch"));
         for (int i = 0; i < 100 && text.get() == null; ++i)
         {
            textPublisher.publish(message);
            Thread.sleep(100);
         }
         if (!message.getDataAsString().equals(text.get()))
         {
            fail("std_msgs/String loopback failed, received: " + text.get());
         }
         System.out.println("std_msgs/String loopback OK");

         // 2. sensor_msgs/Image (nested message + growing uint8[] + fragmentation)
         ROS2Topic<sensor_msgs.Image> imageTopic = new ROS2Topic<>("/jros2_check/image", sensor_msgs.Image.class);
         AtomicReference<sensor_msgs.Image> received = new AtomicReference<>();
         ROS2Subscription<sensor_msgs.Image> imageSubscription = node.createSubscription(imageTopic, reader ->
         {
            sensor_msgs.Image image = reader.read();
            if (image != null)
            {
               received.compareAndSet(null, image);
            }
         }, qos);
         ROS2Publisher<sensor_msgs.Image> imagePublisher = node.createPublisher(imageTopic, qos);
         awaitMatch(imagePublisher, imageSubscription);
         sensor_msgs.Image image = new sensor_msgs.Image();
         image.setWidth(320);
         image.setHeight(240);
         image.setStep(320 * 3);
         image.setEncoding("rgb8");
         byte[] pixels = new byte[320 * 240 * 3];
         for (int i = 0; i < pixels.length; ++i)
         {
            pixels[i] = (byte) (i * 7);
         }
         image.getData().addAll(pixels);
         for (int i = 0; i < 100 && received.get() == null; ++i)
         {
            imagePublisher.publish(image);
            Thread.sleep(100);
         }
         sensor_msgs.Image copy = received.get();
         if (copy == null || copy.getData().size() != pixels.length || copy.getData().get(pixels.length - 1) != pixels[pixels.length - 1])
         {
            fail("sensor_msgs/Image loopback failed");
         }
         System.out.println("sensor_msgs/Image loopback OK (" + pixels.length + " bytes)");
      }

      System.out.println("NATIVE CHECK PASSED");
      System.exit(0);
   }

   private static <T extends us.ihmc.jros2.ROS2Message<T>> void awaitMatch(ROS2Publisher<T> publisher, ROS2Subscription<T> subscription)
   {
      if (!publisher.waitForSubscription(30_000) || !subscription.waitForPublisher(30_000))
      {
         fail("publisher and subscription of " + publisher.getTopicName() + " did not match within 30 s");
      }
   }

   private static void fail(String message)
   {
      System.out.println("NATIVE CHECK FAILED: " + message);
      System.exit(1);
   }
}
