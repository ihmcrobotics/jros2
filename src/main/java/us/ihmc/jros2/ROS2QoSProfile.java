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
package us.ihmc.jros2;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * <a href="https://docs.ros.org/en/rolling/Concepts/Intermediate/About-Quality-of-Service-Settings.html">Quality of Service settings</a>,
 * mirroring {@code rmw_qos_profile_t} and mapped to Fast-DDS exactly like {@code rmw_fastrtps} does.
 * <p>
 * A new profile ({@link #ROS2QoSProfile()}) has the values of {@link #DEFAULT}: {@code rmw_qos_profile_default}, i.e.
 * KEEP_LAST 10, RELIABLE, VOLATILE. The public presets are frozen: their setters throw {@link UnsupportedOperationException}.
 * Use the copy constructor to derive a modified profile:
 * <pre>{@code
 * ROS2QoSProfile qos = new ROS2QoSProfile(ROS2QoSProfile.SENSOR_DATA);
 * qos.depth(1);
 * }</pre>
 * <p>
 * Durations follow the rmw conventions: {@link #DURATION_UNSPECIFIED} ({@link Duration#ZERO}) leaves the middleware default,
 * {@link #DURATION_INFINITE} (or any duration of {@code 2^31 - 1} seconds or more) means infinite.
 * <p>
 * <b>Behavior change in jros2 1.6.0:</b> {@link #DEFAULT} and new profiles used to leave reliability and durability at
 * "system default", which made subscriptions BEST_EFFORT. They are now RELIABLE / VOLATILE / KEEP_LAST 10 for publishers and
 * subscriptions, like ROS 2. Use {@link #SENSOR_DATA} or {@link #BEST_EFFORT} for best-effort subscriptions, and
 * {@link #SYSTEM_DEFAULT} to leave everything to the Fast-DDS defaults (note that the Fast-DDS default durability of a
 * DataWriter is TRANSIENT_LOCAL).
 */
public class ROS2QoSProfile
{
   static
   {
      jros2.load();
   }

   /** rmw {@code RMW_DURATION_UNSPECIFIED}: leave the middleware default. Equal to {@link Duration#ZERO}. */
   public static final Duration DURATION_UNSPECIFIED = Duration.ZERO;
   /** rmw {@code RMW_DURATION_INFINITE}. Any duration of {@link #INFINITE_SECONDS_THRESHOLD} seconds or more is also infinite. */
   public static final Duration DURATION_INFINITE = ChronoUnit.FOREVER.getDuration();
   /** Durations of this many seconds or more cannot be represented by DDS and are treated as infinite (DDS uses int32 seconds). */
   public static final long INFINITE_SECONDS_THRESHOLD = Integer.MAX_VALUE;

   /** rmw {@code RMW_QOS_POLICY_DEPTH_SYSTEM_DEFAULT}: leave the middleware history depth (Fast-DDS: 1). */
   public static final int DEPTH_SYSTEM_DEFAULT = 0;

   /** {@code rmw_qos_profile_default}: KEEP_LAST 10, RELIABLE, VOLATILE. */
   public static final ROS2QoSProfile DEFAULT = preset(History.KEEP_LAST, 10, Reliability.RELIABLE, Durability.VOLATILE, DURATION_UNSPECIFIED);
   /** Same as {@link #DEFAULT}: KEEP_LAST 10, RELIABLE, VOLATILE. */
   public static final ROS2QoSProfile RELIABLE = preset(History.KEEP_LAST, 10, Reliability.RELIABLE, Durability.VOLATILE, DURATION_UNSPECIFIED);
   /** {@link #DEFAULT} with BEST_EFFORT reliability: KEEP_LAST 10, BEST_EFFORT, VOLATILE. */
   public static final ROS2QoSProfile BEST_EFFORT = preset(History.KEEP_LAST, 10, Reliability.BEST_EFFORT, Durability.VOLATILE, DURATION_UNSPECIFIED);
   /** {@code rmw_qos_profile_sensor_data}: KEEP_LAST 5, BEST_EFFORT, VOLATILE. */
   public static final ROS2QoSProfile SENSOR_DATA = preset(History.KEEP_LAST, 5, Reliability.BEST_EFFORT, Durability.VOLATILE, DURATION_UNSPECIFIED);
   /** {@code rmw_qos_profile_parameters}: KEEP_LAST 1000, RELIABLE, VOLATILE. */
   public static final ROS2QoSProfile PARAMETERS = preset(History.KEEP_LAST, 1000, Reliability.RELIABLE, Durability.VOLATILE, DURATION_UNSPECIFIED);
   /** {@code rmw_qos_profile_services_default}: KEEP_LAST 10, RELIABLE, VOLATILE. */
   public static final ROS2QoSProfile SERVICES_DEFAULT = preset(History.KEEP_LAST, 10, Reliability.RELIABLE, Durability.VOLATILE, DURATION_UNSPECIFIED);
   /** {@code rmw_qos_profile_parameter_events}: KEEP_LAST 1000, RELIABLE, VOLATILE. */
   public static final ROS2QoSProfile PARAMETER_EVENTS = preset(History.KEEP_LAST, 1000, Reliability.RELIABLE, Durability.VOLATILE, DURATION_UNSPECIFIED);
   /**
    * {@code rmw_qos_profile_system_default}: every policy is left to the Fast-DDS defaults (history KEEP_LAST 1; a DataWriter is
    * RELIABLE and TRANSIENT_LOCAL, a DataReader BEST_EFFORT and VOLATILE).
    */
   public static final ROS2QoSProfile SYSTEM_DEFAULT = preset(History.SYSTEM_DEFAULT,
                                                              DEPTH_SYSTEM_DEFAULT,
                                                              Reliability.SYSTEM_DEFAULT,
                                                              Durability.SYSTEM_DEFAULT,
                                                              DURATION_UNSPECIFIED);
   /** {@code rcl_qos_profile_rosout_default}: KEEP_LAST 1000, RELIABLE, TRANSIENT_LOCAL, lifespan 10 s. */
   public static final ROS2QoSProfile ROSOUT = preset(History.KEEP_LAST, 1000, Reliability.RELIABLE, Durability.TRANSIENT_LOCAL, Duration.ofSeconds(10));
   /** rclcpp {@code ClockQoS}: KEEP_LAST 1, BEST_EFFORT, VOLATILE. */
   public static final ROS2QoSProfile CLOCK = preset(History.KEEP_LAST, 1, Reliability.BEST_EFFORT, Durability.VOLATILE, DURATION_UNSPECIFIED);

   public enum History
   {
      /** Leave the middleware default (Fast-DDS: KEEP_LAST). */
      SYSTEM_DEFAULT,
      KEEP_LAST,
      KEEP_ALL
   }

   public enum Reliability
   {
      /** Leave the middleware default (Fast-DDS: RELIABLE for DataWriters, BEST_EFFORT for DataReaders). */
      SYSTEM_DEFAULT,
      RELIABLE,
      BEST_EFFORT
   }

   public enum Durability
   {
      /** Leave the middleware default (Fast-DDS: TRANSIENT_LOCAL for DataWriters, VOLATILE for DataReaders). */
      SYSTEM_DEFAULT,
      VOLATILE,
      TRANSIENT_LOCAL
   }

   public enum Liveliness
   {
      /** Leave the middleware default (Fast-DDS: AUTOMATIC). */
      SYSTEM_DEFAULT,
      AUTOMATIC,
      MANUAL_BY_TOPIC
   }

   private final boolean frozen;
   private History history;
   private int depth;
   private Reliability reliability;
   private Durability durability;
   private Duration deadline;
   private Duration lifespan;
   private Liveliness liveliness;
   private Duration leaseDuration;
   private Duration maxBlockingTime;

   /**
    * Creates a profile with the values of {@link #DEFAULT} ({@code rmw_qos_profile_default}): KEEP_LAST 10, RELIABLE,
    * VOLATILE, all durations unspecified, liveliness system default.
    */
   public ROS2QoSProfile()
   {
      this(false, History.KEEP_LAST, 10, Reliability.RELIABLE, Durability.VOLATILE, DURATION_UNSPECIFIED);
   }

   /**
    * Copy constructor. The copy is never frozen, so it can be used to derive a profile from a preset.
    *
    * @param other the profile to copy
    */
   public ROS2QoSProfile(ROS2QoSProfile other)
   {
      frozen = false;
      history = other.history;
      depth = other.depth;
      reliability = other.reliability;
      durability = other.durability;
      deadline = other.deadline;
      lifespan = other.lifespan;
      liveliness = other.liveliness;
      leaseDuration = other.leaseDuration;
      maxBlockingTime = other.maxBlockingTime;
   }

   private ROS2QoSProfile(boolean frozen, History history, int depth, Reliability reliability, Durability durability, Duration lifespan)
   {
      this.frozen = frozen;
      this.history = history;
      this.depth = depth;
      this.reliability = reliability;
      this.durability = durability;
      this.deadline = DURATION_UNSPECIFIED;
      this.lifespan = lifespan;
      this.liveliness = Liveliness.SYSTEM_DEFAULT;
      this.leaseDuration = DURATION_UNSPECIFIED;
      this.maxBlockingTime = DURATION_UNSPECIFIED;
   }

   private static ROS2QoSProfile preset(History history, int depth, Reliability reliability, Durability durability, Duration lifespan)
   {
      return new ROS2QoSProfile(true, history, depth, reliability, durability, lifespan);
   }

   /**
    * @return true if this profile is one of the immutable presets; setters then throw {@link UnsupportedOperationException}.
    */
   public boolean isFrozen()
   {
      return frozen;
   }

   private void checkMutable()
   {
      if (frozen)
      {
         throw new UnsupportedOperationException("ROS2QoSProfile presets are immutable; use new ROS2QoSProfile(preset) to create a modifiable copy");
      }
   }

   private static Duration checkDuration(String name, Duration duration)
   {
      if (duration == null)
      {
         return DURATION_UNSPECIFIED;
      }
      if (duration.isNegative())
      {
         throw new IllegalArgumentException(name + " must not be negative: " + duration);
      }
      return duration;
   }

   /**
    * @param duration a QoS duration
    * @return true if {@code duration} is {@link #DURATION_INFINITE} or too large for DDS ({@code >= 2^31 - 1} seconds)
    */
   public static boolean isInfinite(Duration duration)
   {
      return duration != null && duration.getSeconds() >= INFINITE_SECONDS_THRESHOLD;
   }

   /**
    * @param duration a QoS duration
    * @return true if {@code duration} is null or {@link #DURATION_UNSPECIFIED}
    */
   public static boolean isUnspecified(Duration duration)
   {
      return duration == null || duration.isZero();
   }

   /** History kind. */
   public void history(History history)
   {
      checkMutable();
      if (history == null)
      {
         throw new IllegalArgumentException("history must not be null");
      }
      this.history = history;
   }

   /**
    * History depth; {@link #DEPTH_SYSTEM_DEFAULT} (0) leaves the middleware default. Like rmw_fastrtps, the depth is applied
    * to KEEP_ALL as well.
    *
    * @throws IllegalArgumentException if {@code depth} is negative
    */
   public void depth(int depth)
   {
      checkMutable();
      if (depth < 0)
      {
         throw new IllegalArgumentException("depth must not be negative: " + depth);
      }
      this.depth = depth;
   }

   /** Reliability kind. */
   public void reliability(Reliability reliability)
   {
      checkMutable();
      if (reliability == null)
      {
         throw new IllegalArgumentException("reliability must not be null");
      }
      this.reliability = reliability;
   }

   /** Durability kind. */
   public void durability(Durability durability)
   {
      checkMutable();
      if (durability == null)
      {
         throw new IllegalArgumentException("durability must not be null");
      }
      this.durability = durability;
   }

   /** Deadline period; {@link #DURATION_UNSPECIFIED} (or null) leaves the default (infinite). */
   public void deadline(Duration deadline)
   {
      checkMutable();
      this.deadline = checkDuration("deadline", deadline);
   }

   /** Lifespan; {@link #DURATION_UNSPECIFIED} (or null) leaves the default (infinite). */
   public void lifespan(Duration lifespan)
   {
      checkMutable();
      this.lifespan = checkDuration("lifespan", lifespan);
   }

   /** Liveliness kind. MANUAL_BY_NODE is deprecated in ROS 2 and rejected by rmw_fastrtps, so it is not offered. */
   public void liveliness(Liveliness liveliness)
   {
      checkMutable();
      if (liveliness == null)
      {
         throw new IllegalArgumentException("liveliness must not be null");
      }
      this.liveliness = liveliness;
   }

   /**
    * Liveliness lease duration; {@link #DURATION_UNSPECIFIED} (or null) leaves the default (infinite). When set, the
    * liveliness announcement period is 2/3 of the lease, like rmw_fastrtps.
    */
   public void leaseDuration(Duration leaseDuration)
   {
      checkMutable();
      this.leaseDuration = checkDuration("leaseDuration", leaseDuration);
   }

   /**
    * Maximum time a RELIABLE {@code publish()} blocks when the writer history is full (Fast-DDS
    * {@code reliability.max_blocking_time}). {@link #DURATION_UNSPECIFIED} (or null) keeps the Fast-DDS default of 100 ms.
    * Fast-DDS rejects an infinite value for RELIABLE endpoints, so {@link #DURATION_INFINITE} is mapped to the largest finite
    * DDS duration (about 68 years). This setting is not part of {@code rmw_qos_profile_t}; ROS 2 nodes always use 100 ms.
    */
   public void maxBlockingTime(Duration maxBlockingTime)
   {
      checkMutable();
      this.maxBlockingTime = checkDuration("maxBlockingTime", maxBlockingTime);
   }

   public History getHistory()
   {
      return history;
   }

   public int getDepth()
   {
      return depth;
   }

   public Reliability getReliability()
   {
      return reliability;
   }

   public Durability getDurability()
   {
      return durability;
   }

   public Duration getDeadline()
   {
      return deadline;
   }

   public Duration getLifespan()
   {
      return lifespan;
   }

   public Liveliness getLiveliness()
   {
      return liveliness;
   }

   public Duration getLeaseDuration()
   {
      return leaseDuration;
   }

   public Duration getMaxBlockingTime()
   {
      return maxBlockingTime;
   }

   @Override
   public boolean equals(Object object)
   {
      if (this == object)
      {
         return true;
      }
      if (!(object instanceof ROS2QoSProfile))
      {
         return false;
      }
      ROS2QoSProfile other = (ROS2QoSProfile) object;
      return history == other.history && depth == other.depth && reliability == other.reliability && durability == other.durability
             && liveliness == other.liveliness && sameDuration(deadline, other.deadline) && sameDuration(lifespan, other.lifespan)
             && sameDuration(leaseDuration, other.leaseDuration) && sameDuration(maxBlockingTime, other.maxBlockingTime);
   }

   /**
    * Durations are compared by meaning: all infinite durations are equal.
    */
   private static boolean sameDuration(Duration a, Duration b)
   {
      if (isInfinite(a) || isInfinite(b))
      {
         return isInfinite(a) && isInfinite(b);
      }
      return a.equals(b);
   }

   private static int durationHash(Duration duration)
   {
      return isInfinite(duration) ? -1 : duration.hashCode();
   }

   @Override
   public int hashCode()
   {
      int result = history.hashCode();
      result = 31 * result + depth;
      result = 31 * result + reliability.hashCode();
      result = 31 * result + durability.hashCode();
      result = 31 * result + liveliness.hashCode();
      result = 31 * result + durationHash(deadline);
      result = 31 * result + durationHash(lifespan);
      result = 31 * result + durationHash(leaseDuration);
      result = 31 * result + durationHash(maxBlockingTime);
      return result;
   }

   static void appendDuration(StringBuilder builder, Duration duration)
   {
      if (isUnspecified(duration))
      {
         builder.append("unspecified");
      }
      else if (isInfinite(duration))
      {
         builder.append("infinite");
      }
      else
      {
         builder.append(duration.getSeconds()).append('s');
         if (duration.getNano() != 0)
         {
            builder.append('+').append(duration.getNano()).append("ns");
         }
      }
   }

   @Override
   public String toString()
   {
      StringBuilder builder = new StringBuilder(160);
      builder.append("ROS2QoSProfile{history=").append(history);
      builder.append(", depth=").append(depth);
      builder.append(", reliability=").append(reliability);
      builder.append(", durability=").append(durability);
      builder.append(", deadline=");
      appendDuration(builder, deadline);
      builder.append(", lifespan=");
      appendDuration(builder, lifespan);
      builder.append(", liveliness=").append(liveliness);
      builder.append(", leaseDuration=");
      appendDuration(builder, leaseDuration);
      builder.append(", maxBlockingTime=");
      appendDuration(builder, maxBlockingTime);
      if (frozen)
      {
         builder.append(", frozen");
      }
      builder.append('}');
      return builder.toString();
   }
}
