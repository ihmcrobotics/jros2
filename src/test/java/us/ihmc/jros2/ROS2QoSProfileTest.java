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
import us.ihmc.jros2.ROS2QoSProfile.Durability;
import us.ihmc.jros2.ROS2QoSProfile.History;
import us.ihmc.jros2.ROS2QoSProfile.Liveliness;
import us.ihmc.jros2.ROS2QoSProfile.Reliability;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Preset values (rmw qos_profiles.h, rcl rosout, rclcpp ClockQoS), immutability, copying, validation and value semantics of
 * {@link ROS2QoSProfile}.
 */
public class ROS2QoSProfileTest
{
   private static void assertProfile(ROS2QoSProfile profile, History history, int depth, Reliability reliability, Durability durability, Duration lifespan)
   {
      assertEquals(history, profile.getHistory(), "history");
      assertEquals(depth, profile.getDepth(), "depth");
      assertEquals(reliability, profile.getReliability(), "reliability");
      assertEquals(durability, profile.getDurability(), "durability");
      assertEquals(lifespan, profile.getLifespan(), "lifespan");
      assertEquals(Duration.ZERO, profile.getDeadline(), "deadline");
      assertEquals(Liveliness.SYSTEM_DEFAULT, profile.getLiveliness(), "liveliness");
      assertEquals(Duration.ZERO, profile.getLeaseDuration(), "lease");
      assertEquals(Duration.ZERO, profile.getMaxBlockingTime(), "max blocking time");
   }

   @Test
   public void testPresetValuesMatchROS2()
   {
      // rmw_qos_profile_default; user decision: DEFAULT is RELIABLE for publishers and subscriptions
      assertProfile(ROS2QoSProfile.DEFAULT, History.KEEP_LAST, 10, Reliability.RELIABLE, Durability.VOLATILE, Duration.ZERO);
      assertProfile(ROS2QoSProfile.RELIABLE, History.KEEP_LAST, 10, Reliability.RELIABLE, Durability.VOLATILE, Duration.ZERO);
      assertProfile(ROS2QoSProfile.BEST_EFFORT, History.KEEP_LAST, 10, Reliability.BEST_EFFORT, Durability.VOLATILE, Duration.ZERO);
      // rmw_qos_profile_sensor_data
      assertProfile(ROS2QoSProfile.SENSOR_DATA, History.KEEP_LAST, 5, Reliability.BEST_EFFORT, Durability.VOLATILE, Duration.ZERO);
      // rmw_qos_profile_parameters
      assertProfile(ROS2QoSProfile.PARAMETERS, History.KEEP_LAST, 1000, Reliability.RELIABLE, Durability.VOLATILE, Duration.ZERO);
      // rmw_qos_profile_services_default
      assertProfile(ROS2QoSProfile.SERVICES_DEFAULT, History.KEEP_LAST, 10, Reliability.RELIABLE, Durability.VOLATILE, Duration.ZERO);
      // rmw_qos_profile_parameter_events
      assertProfile(ROS2QoSProfile.PARAMETER_EVENTS, History.KEEP_LAST, 1000, Reliability.RELIABLE, Durability.VOLATILE, Duration.ZERO);
      // rmw_qos_profile_system_default
      assertProfile(ROS2QoSProfile.SYSTEM_DEFAULT, History.SYSTEM_DEFAULT, 0, Reliability.SYSTEM_DEFAULT, Durability.SYSTEM_DEFAULT, Duration.ZERO);
      // rcl_qos_profile_rosout_default
      assertProfile(ROS2QoSProfile.ROSOUT, History.KEEP_LAST, 1000, Reliability.RELIABLE, Durability.TRANSIENT_LOCAL, Duration.ofSeconds(10));
      // rclcpp ClockQoS
      assertProfile(ROS2QoSProfile.CLOCK, History.KEEP_LAST, 1, Reliability.BEST_EFFORT, Durability.VOLATILE, Duration.ZERO);
   }

   @Test
   public void testNewProfileEqualsDefault()
   {
      ROS2QoSProfile profile = new ROS2QoSProfile();
      assertProfile(profile, History.KEEP_LAST, 10, Reliability.RELIABLE, Durability.VOLATILE, Duration.ZERO);
      assertEquals(ROS2QoSProfile.DEFAULT, profile);
      assertEquals(ROS2QoSProfile.DEFAULT.hashCode(), profile.hashCode());
      assertFalse(profile.isFrozen());
   }

   @Test
   public void testPresetsAreFrozen()
   {
      ROS2QoSProfile[] presets = {ROS2QoSProfile.DEFAULT,
                                  ROS2QoSProfile.RELIABLE,
                                  ROS2QoSProfile.BEST_EFFORT,
                                  ROS2QoSProfile.SENSOR_DATA,
                                  ROS2QoSProfile.PARAMETERS,
                                  ROS2QoSProfile.SERVICES_DEFAULT,
                                  ROS2QoSProfile.PARAMETER_EVENTS,
                                  ROS2QoSProfile.SYSTEM_DEFAULT,
                                  ROS2QoSProfile.ROSOUT,
                                  ROS2QoSProfile.CLOCK};
      for (ROS2QoSProfile preset : presets)
      {
         ROS2QoSProfile before = new ROS2QoSProfile(preset);
         assertTrue(preset.isFrozen());
         assertSetterThrows(preset, p -> p.history(History.KEEP_ALL));
         assertSetterThrows(preset, p -> p.depth(3));
         assertSetterThrows(preset, p -> p.reliability(Reliability.BEST_EFFORT));
         assertSetterThrows(preset, p -> p.durability(Durability.TRANSIENT_LOCAL));
         assertSetterThrows(preset, p -> p.deadline(Duration.ofSeconds(1)));
         assertSetterThrows(preset, p -> p.lifespan(Duration.ofSeconds(1)));
         assertSetterThrows(preset, p -> p.liveliness(Liveliness.MANUAL_BY_TOPIC));
         assertSetterThrows(preset, p -> p.leaseDuration(Duration.ofSeconds(1)));
         assertSetterThrows(preset, p -> p.maxBlockingTime(Duration.ofSeconds(1)));
         assertEquals(before, preset, "a rejected setter must not change the preset");
      }
   }

   private static void assertSetterThrows(ROS2QoSProfile profile, Consumer<ROS2QoSProfile> setter)
   {
      assertThrows(UnsupportedOperationException.class, () -> setter.accept(profile));
   }

   @Test
   public void testCopyConstructorIsMutableAndIndependent()
   {
      ROS2QoSProfile copy = new ROS2QoSProfile(ROS2QoSProfile.SENSOR_DATA);
      assertFalse(copy.isFrozen());
      assertEquals(ROS2QoSProfile.SENSOR_DATA, copy);

      copy.depth(1);
      copy.reliability(Reliability.RELIABLE);
      copy.deadline(Duration.ofMillis(250));
      copy.maxBlockingTime(Duration.ofMillis(5));

      assertEquals(1, copy.getDepth());
      assertEquals(Reliability.RELIABLE, copy.getReliability());
      assertEquals(Duration.ofMillis(250), copy.getDeadline());
      assertEquals(Duration.ofMillis(5), copy.getMaxBlockingTime());
      assertEquals(5, ROS2QoSProfile.SENSOR_DATA.getDepth());
      assertEquals(Reliability.BEST_EFFORT, ROS2QoSProfile.SENSOR_DATA.getReliability());
      assertNotEquals(ROS2QoSProfile.SENSOR_DATA, copy);

      ROS2QoSProfile copyOfCopy = new ROS2QoSProfile(copy);
      assertEquals(copy, copyOfCopy);
      assertEquals(copy.hashCode(), copyOfCopy.hashCode());
   }

   @Test
   public void testEqualsAndHashCode()
   {
      ROS2QoSProfile a = new ROS2QoSProfile();
      ROS2QoSProfile b = new ROS2QoSProfile();
      assertEquals(a, a);
      assertEquals(a, b);
      assertNotEquals(a, null);
      assertNotEquals(a, "not a profile");

      b.durability(Durability.TRANSIENT_LOCAL);
      assertNotEquals(a, b);
      b.durability(Durability.VOLATILE);
      assertEquals(a, b);

      b.liveliness(Liveliness.AUTOMATIC);
      assertNotEquals(a, b);
      a.liveliness(Liveliness.AUTOMATIC);
      assertEquals(a, b);

      b.leaseDuration(Duration.ofSeconds(2));
      assertNotEquals(a, b);
      a.leaseDuration(Duration.ofSeconds(2));
      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());

      b.history(History.KEEP_ALL);
      assertNotEquals(a, b);
   }

   @Test
   public void testInfiniteDurations()
   {
      assertTrue(ROS2QoSProfile.isInfinite(ROS2QoSProfile.DURATION_INFINITE));
      assertTrue(ROS2QoSProfile.isInfinite(ChronoUnit.FOREVER.getDuration()));
      assertTrue(ROS2QoSProfile.isInfinite(Duration.ofSeconds(Integer.MAX_VALUE)));
      assertTrue(ROS2QoSProfile.isInfinite(Duration.ofSeconds(5_000_000_000L)));
      assertFalse(ROS2QoSProfile.isInfinite(Duration.ofSeconds(Integer.MAX_VALUE - 1L, 999_999_999)));
      assertFalse(ROS2QoSProfile.isInfinite(Duration.ZERO));
      assertFalse(ROS2QoSProfile.isInfinite(null));

      assertTrue(ROS2QoSProfile.isUnspecified(null));
      assertTrue(ROS2QoSProfile.isUnspecified(ROS2QoSProfile.DURATION_UNSPECIFIED));
      assertFalse(ROS2QoSProfile.isUnspecified(Duration.ofNanos(1)));

      // All infinite representations are equal
      ROS2QoSProfile a = new ROS2QoSProfile();
      ROS2QoSProfile b = new ROS2QoSProfile();
      a.deadline(ROS2QoSProfile.DURATION_INFINITE);
      b.deadline(Duration.ofSeconds(Integer.MAX_VALUE));
      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
   }

   @Test
   public void testValidation()
   {
      ROS2QoSProfile profile = new ROS2QoSProfile();
      assertThrows(IllegalArgumentException.class, () -> profile.depth(-1));
      assertThrows(IllegalArgumentException.class, () -> profile.deadline(Duration.ofSeconds(-1)));
      assertThrows(IllegalArgumentException.class, () -> profile.lifespan(Duration.ofNanos(-1)));
      assertThrows(IllegalArgumentException.class, () -> profile.leaseDuration(Duration.ofMillis(-5)));
      assertThrows(IllegalArgumentException.class, () -> profile.maxBlockingTime(Duration.ofSeconds(-3)));
      assertThrows(IllegalArgumentException.class, () -> profile.history(null));
      assertThrows(IllegalArgumentException.class, () -> profile.reliability(null));
      assertThrows(IllegalArgumentException.class, () -> profile.durability(null));
      assertThrows(IllegalArgumentException.class, () -> profile.liveliness(null));
      assertEquals(ROS2QoSProfile.DEFAULT, profile, "rejected values must not change the profile");

      // depth 0 is "system default", null durations are "unspecified"
      profile.depth(0);
      assertEquals(0, profile.getDepth());
      profile.deadline(Duration.ofSeconds(3));
      profile.deadline(null);
      assertEquals(Duration.ZERO, profile.getDeadline());
      profile.lifespan(null);
      profile.leaseDuration(null);
      profile.maxBlockingTime(null);
      assertEquals(Duration.ZERO, profile.getLifespan());
      assertEquals(Duration.ZERO, profile.getLeaseDuration());
      assertEquals(Duration.ZERO, profile.getMaxBlockingTime());
   }

   @Test
   public void testToString()
   {
      String text = ROS2QoSProfile.ROSOUT.toString();
      assertTrue(text.contains("history=KEEP_LAST"), text);
      assertTrue(text.contains("depth=1000"), text);
      assertTrue(text.contains("reliability=RELIABLE"), text);
      assertTrue(text.contains("durability=TRANSIENT_LOCAL"), text);
      assertTrue(text.contains("lifespan=10s"), text);
      assertTrue(text.contains("deadline=unspecified"), text);
      assertTrue(text.contains("frozen"), text);

      ROS2QoSProfile profile = new ROS2QoSProfile();
      profile.deadline(ROS2QoSProfile.DURATION_INFINITE);
      profile.leaseDuration(Duration.ofSeconds(1, 500));
      String mutableText = profile.toString();
      assertTrue(mutableText.contains("deadline=infinite"), mutableText);
      assertTrue(mutableText.contains("leaseDuration=1s+500ns"), mutableText);
      assertFalse(mutableText.contains("frozen"), mutableText);
   }
}
