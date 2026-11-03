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
package us.ihmc.jros2;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static us.ihmc.jros2.AndroidTestSupport.DISCOVERY_TIMEOUT_MS;
import static us.ihmc.jros2.AndroidTestSupport.domainId;
import static us.ihmc.jros2.AndroidTestSupport.publishUntil;
import static us.ihmc.jros2.AndroidTestSupport.uniqueNodeName;
import static us.ihmc.jros2.AndroidTestSupport.uniqueTopicName;

/**
 * Every ROS 2 QoS preset must translate into a Fast-DDS profile the native library accepts on the device, and a publisher
 * and a subscription with the same preset must communicate. The presets are looked up by name so this test also reports
 * presets that are missing from {@link ROS2QoSProfile}.
 */
@RunWith(AndroidJUnit4.class)
public class QoSPresetsOnDeviceTest
{
   /** rclcpp / rmw presets (rmw/qos_profiles.h) that jros2 exposes as public static fields of ROS2QoSProfile. */
   static final String[] PRESETS = {"DEFAULT", "RELIABLE", "BEST_EFFORT", "SENSOR_DATA", "PARAMETERS", "SERVICES_DEFAULT", "PARAMETER_EVENTS",
                                    "SYSTEM_DEFAULT", "ROSOUT", "CLOCK"};

   @Rule
   public Timeout timeout = new Timeout(300, TimeUnit.SECONDS);

   private ROS2Node node;

   @After
   public void closeNode()
   {
      if (node != null)
      {
         node.close();
      }
   }

   static Map<String, ROS2QoSProfile> presets(List<String> missing)
   {
      Map<String, ROS2QoSProfile> presets = new LinkedHashMap<>();
      for (String name : PRESETS)
      {
         try
         {
            Field field = ROS2QoSProfile.class.getField(name);
            assertTrue(name + " must be public static", Modifier.isStatic(field.getModifiers()));
            presets.put(name, (ROS2QoSProfile) field.get(null));
         }
         catch (NoSuchFieldException e)
         {
            missing.add(name);
         }
         catch (IllegalAccessException e)
         {
            throw new AssertionError(e);
         }
      }
      return presets;
   }

   /**
    * ROS 2's default profile (rmw_qos_profile_default) is RELIABLE, VOLATILE, KEEP_LAST(10).
    */
   @Test
   public void defaultPresetMatchesRos2Default()
   {
      ROS2QoSProfile qos = ROS2QoSProfile.DEFAULT;
      assertEquals(ROS2QoSProfile.Reliability.RELIABLE, qos.getReliability());
      assertEquals(ROS2QoSProfile.Durability.VOLATILE, qos.getDurability());
      assertEquals(ROS2QoSProfile.History.KEEP_LAST, qos.getHistory());
      assertEquals(10, qos.getDepth());
   }

   @Test
   public void everyPresetDeliversMessages()
   {
      List<String> missing = new ArrayList<>();
      Map<String, ROS2QoSProfile> presets = presets(missing);
      List<String> failures = new ArrayList<>();

      node = new ROS2Node(uniqueNodeName("qos_presets"), domainId());
      for (Map.Entry<String, ROS2QoSProfile> preset : presets.entrySet())
      {
         String name = preset.getKey();
         try
         {
            ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(uniqueTopicName("qos_" + name.toLowerCase(java.util.Locale.ROOT)), std_msgs.String_.class);
            AtomicBoolean received = new AtomicBoolean();
            ROS2Subscription<std_msgs.String_> subscription = node.createSubscription(topic, reader ->
            {
               if (reader.read() != null)
               {
                  received.set(true);
               }
            }, preset.getValue());
            ROS2Publisher<std_msgs.String_> publisher = node.createPublisher(topic, preset.getValue());

            if (!publisher.waitForSubscription(DISCOVERY_TIMEOUT_MS) || !subscription.waitForPublisher(DISCOVERY_TIMEOUT_MS))
            {
               failures.add(name + ": endpoints did not match");
               continue;
            }

            std_msgs.String_ message = new std_msgs.String_();
            message.getData().append("preset ").append(name);
            if (!publishUntil(publisher, message, received::get, 100))
            {
               failures.add(name + ": no message received");
            }
            Log.i(AndroidTestSupport.TAG, "QoS preset " + name + (received.get() ? " OK" : " FAILED"));

            node.destroyPublisher(publisher);
            node.destroySubscription(subscription);
         }
         catch (RuntimeException e)
         {
            Log.e(AndroidTestSupport.TAG, "QoS preset " + name, e);
            failures.add(name + ": " + e);
         }
      }

      assertTrue("Presets missing from ROS2QoSProfile: " + missing, missing.isEmpty());
      assertTrue("Presets that did not work on this device:\n" + String.join("\n", failures), failures.isEmpty());
   }
}
