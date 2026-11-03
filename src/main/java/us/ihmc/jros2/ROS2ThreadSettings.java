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

import us.ihmc.fastddsjava.profiles.gen.ThreadSettingsType;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.Map;

/**
 * Scheduling settings for the native Fast-DDS threads of a participant (receive threads, event thread, asynchronous
 * sender), mapped to Fast-DDS {@code <thread_settings>}.
 * See: <a href="https://fast-dds.docs.eprosima.com/en/v3.6.2/fastdds/api_reference/dds_pim/core/policy/threadsettings.html">ThreadSettings</a>
 * <p>
 * The values set on this object apply to every Fast-DDS thread of the participant. {@link #forThreads(Kind, ROS2ThreadSettings)}
 * overrides them for one kind of thread; fields the override leaves unset fall back to this object's values:
 * <pre>{@code
 * ROS2ThreadSettings threads = new ROS2ThreadSettings().affinityMask(0b1100)          // all threads on CPU 2 and 3
 *       .forThreads(ROS2ThreadSettings.Kind.RECEPTION,
 *                   new ROS2ThreadSettings().schedulingPolicy(1).priority(30));     // receive threads SCHED_FIFO 30
 * ROS2Node node = new ROS2Node("robot", ROS2NodeOptions.defaults().threadSettings(threads));
 * }</pre>
 * Unset values keep the Fast-DDS defaults. Failing to apply a policy, priority or affinity (for example SCHED_FIFO without the
 * required privileges) is logged by Fast-DDS and does not prevent the node from starting. A stack size the operating system
 * refuses (too small, or not page aligned on macOS), however, would abort the process inside Fast-DDS, so it is validated
 * here.
 * <p>
 * When the participant uses the Fast-DDS builtin transports and reception thread settings are given, jros2 configures the
 * builtin transports explicitly ({@code <builtinTransports>DEFAULT</builtinTransports>}), which also means the
 * {@code FASTDDS_BUILTIN_TRANSPORTS} environment variable no longer applies to that participant.
 */
public final class ROS2ThreadSettings
{
   /** Value meaning "keep the Fast-DDS default" for the integer fields. */
   public static final int UNSET = Integer.MIN_VALUE;
   /**
    * Smallest accepted stack size. glibc on aarch64 refuses stacks below 128 KiB ({@code PTHREAD_STACK_MIN}), which makes
    * Fast-DDS throw from a native thread constructor and abort the JVM; 131072 bytes is safe on every supported platform.
    */
   public static final int MIN_STACK_SIZE_BYTES = 131072;
   /**
    * Stack sizes must be a multiple of this many bytes. macOS refuses stacks that are not a multiple of its page size (16 KiB on
    * Apple silicon), which makes Fast-DDS throw from a native thread constructor and abort the JVM; 16384 works on every
    * supported platform.
    */
   public static final int STACK_SIZE_ALIGNMENT_BYTES = 16384;
   /** Fast-DDS value for "use the default stack size" (also accepted). */
   public static final int DEFAULT_STACK_SIZE = -1;

   /** The Fast-DDS threads of a participant that can be configured separately. */
   public enum Kind
   {
      /** Transport receive threads ({@code dds.udp.<port>}, {@code dds.shm.<port>}, {@code dds.tcp.<port>}). */
      RECEPTION,
      /** The participant event thread ({@code dds.ev.<id>}): timers for heartbeats, announcements, deadlines and liveliness. */
      TIMED_EVENTS,
      /** The sender threads of the builtin asynchronous flow controllers ({@code dds.asyn.<id>.<n>}). */
      BUILTIN_CONTROLLERS_SENDER,
      /** The type lookup service threads ({@code dds.tls.request}, {@code dds.tls.replies}). */
      TYPELOOKUP_SERVICE,
      /** The discovery server event thread ({@code dds.ds_ev.<id>}); only exists with Fast-DDS discovery server. */
      DISCOVERY_SERVER,
      /** The DDS-Security logging thread ({@code dds.slog.<id>}); only exists when security logging is enabled. */
      SECURITY_LOG
   }

   private int schedulingPolicy = UNSET;
   private int priority = UNSET;
   private long affinityMask = 0L;
   private int stackSizeBytes = UNSET;
   private final Map<Kind, ROS2ThreadSettings> overrides = new EnumMap<>(Kind.class);

   public ROS2ThreadSettings()
   {
   }

   /** Copy constructor (copies the overrides too). */
   public ROS2ThreadSettings(ROS2ThreadSettings other)
   {
      schedulingPolicy = other.schedulingPolicy;
      priority = other.priority;
      affinityMask = other.affinityMask;
      stackSizeBytes = other.stackSizeBytes;
      for (Map.Entry<Kind, ROS2ThreadSettings> entry : other.overrides.entrySet())
      {
         overrides.put(entry.getKey(), new ROS2ThreadSettings(entry.getValue()));
      }
   }

   /**
    * OS scheduling policy, e.g. SCHED_OTHER = 0, SCHED_FIFO = 1, SCHED_RR = 2, SCHED_BATCH = 3, SCHED_IDLE = 5 on Linux;
    * -1 or {@link #UNSET} keep the default.
    *
    * @throws IllegalArgumentException if the value is below -1 (and not {@link #UNSET})
    */
   public ROS2ThreadSettings schedulingPolicy(int schedulingPolicy)
   {
      if (schedulingPolicy != UNSET && schedulingPolicy < -1)
      {
         throw new IllegalArgumentException("Thread scheduling policy must be >= -1 (or UNSET): " + schedulingPolicy);
      }
      this.schedulingPolicy = schedulingPolicy;
      return this;
   }

   /** Priority for the scheduling policy (the nice value for SCHED_OTHER, 1..99 for SCHED_FIFO/SCHED_RR on Linux). */
   public ROS2ThreadSettings priority(int priority)
   {
      this.priority = priority;
      return this;
   }

   /** Bit mask of the CPU cores the threads may run on (bit n = core n, all 64 bits usable); 0 keeps the default. */
   public ROS2ThreadSettings affinityMask(long affinityMask)
   {
      this.affinityMask = affinityMask;
      return this;
   }

   /**
    * Thread stack size in bytes: {@link #DEFAULT_STACK_SIZE} (-1), {@link #UNSET}, or a multiple of
    * {@link #STACK_SIZE_ALIGNMENT_BYTES} that is at least {@link #MIN_STACK_SIZE_BYTES}. Every Fast-DDS thread of the participant
    * that this setting applies to reserves a stack of this size; if the operating system cannot create such a thread (for
    * example very large stacks on a 32-bit platform), Fast-DDS aborts the process.
    *
    * @throws IllegalArgumentException for any other value, which would abort the process inside Fast-DDS on some platforms
    */
   public ROS2ThreadSettings stackSizeBytes(int stackSizeBytes)
   {
      if (stackSizeBytes != UNSET && stackSizeBytes != DEFAULT_STACK_SIZE
          && (stackSizeBytes < MIN_STACK_SIZE_BYTES || stackSizeBytes % STACK_SIZE_ALIGNMENT_BYTES != 0))
      {
         throw new IllegalArgumentException("Thread stack size must be -1 (default) or a multiple of " + STACK_SIZE_ALIGNMENT_BYTES + " bytes that is at least "
                                            + MIN_STACK_SIZE_BYTES + " bytes: " + stackSizeBytes);
      }
      this.stackSizeBytes = stackSizeBytes;
      return this;
   }

   /**
    * Overrides the settings for one kind of thread. Fields that {@code settings} leaves unset fall back to this object's
    * values. Overrides of {@code settings} itself are ignored.
    *
    * @param kind     the kind of thread
    * @param settings the settings, or null to remove the override
    * @return this
    */
   public ROS2ThreadSettings forThreads(Kind kind, ROS2ThreadSettings settings)
   {
      if (kind == null)
      {
         throw new IllegalArgumentException("kind must not be null");
      }
      if (settings == null)
      {
         overrides.remove(kind);
      }
      else
      {
         ROS2ThreadSettings copy = new ROS2ThreadSettings(settings);
         copy.overrides.clear();
         overrides.put(kind, copy);
      }
      return this;
   }

   public int getSchedulingPolicy()
   {
      return schedulingPolicy;
   }

   public int getPriority()
   {
      return priority;
   }

   public long getAffinityMask()
   {
      return affinityMask;
   }

   public int getStackSizeBytes()
   {
      return stackSizeBytes;
   }

   /**
    * @param kind a kind of thread
    * @return the override for {@code kind}, or null
    */
   public ROS2ThreadSettings getOverride(Kind kind)
   {
      return overrides.get(kind);
   }

   /**
    * @param kind a kind of thread
    * @return the settings that apply to {@code kind}: the override's set fields, then this object's fields (no overrides)
    */
   public ROS2ThreadSettings getEffective(Kind kind)
   {
      ROS2ThreadSettings effective = new ROS2ThreadSettings();
      effective.schedulingPolicy = schedulingPolicy;
      effective.priority = priority;
      effective.affinityMask = affinityMask;
      effective.stackSizeBytes = stackSizeBytes;

      ROS2ThreadSettings override = overrides.get(kind);
      if (override != null)
      {
         if (override.schedulingPolicy != UNSET)
         {
            effective.schedulingPolicy = override.schedulingPolicy;
         }
         if (override.priority != UNSET)
         {
            effective.priority = override.priority;
         }
         if (override.affinityMask != 0L)
         {
            effective.affinityMask = override.affinityMask;
         }
         if (override.stackSizeBytes != UNSET)
         {
            effective.stackSizeBytes = override.stackSizeBytes;
         }
      }
      return effective;
   }

   /**
    * @return true if no field is set on this object (overrides are not considered)
    */
   public boolean isUnset()
   {
      return schedulingPolicy == UNSET && priority == UNSET && affinityMask == 0L && stackSizeBytes == UNSET;
   }

   /**
    * @param kind a kind of thread
    * @return the Fast-DDS XML thread settings for {@code kind}, or null if nothing is set for it
    */
   ThreadSettingsType toThreadSettingsType(Kind kind)
   {
      ROS2ThreadSettings effective = getEffective(kind);
      if (effective.isUnset())
      {
         return null;
      }
      ThreadSettingsType threadSettings = new ThreadSettingsType();
      if (effective.schedulingPolicy != UNSET)
      {
         threadSettings.setSchedulingPolicy(effective.schedulingPolicy);
      }
      if (effective.priority != UNSET)
      {
         threadSettings.setPriority(effective.priority);
      }
      if (effective.affinityMask != 0L)
      {
         // uint64 bit mask
         threadSettings.setAffinity(new BigInteger(Long.toUnsignedString(effective.affinityMask)));
      }
      if (effective.stackSizeBytes != UNSET)
      {
         threadSettings.setStackSize(effective.stackSizeBytes);
      }
      return threadSettings;
   }

   /**
    * Appends a compact description, e.g. {@code policy=1 priority=30 affinity=0xc stack=1048576}.
    */
   void appendTo(StringBuilder builder)
   {
      int start = builder.length();
      if (schedulingPolicy != UNSET)
      {
         builder.append("policy=").append(schedulingPolicy);
      }
      if (priority != UNSET)
      {
         appendSeparator(builder, start).append("priority=").append(priority);
      }
      if (affinityMask != 0L)
      {
         appendSeparator(builder, start).append("affinity=0x").append(Long.toHexString(affinityMask));
      }
      if (stackSizeBytes != UNSET)
      {
         appendSeparator(builder, start).append("stack=").append(stackSizeBytes);
      }
      if (builder.length() == start)
      {
         builder.append("default");
      }
   }

   private static StringBuilder appendSeparator(StringBuilder builder, int start)
   {
      if (builder.length() > start)
      {
         builder.append(' ');
      }
      return builder;
   }

   @Override
   public String toString()
   {
      StringBuilder builder = new StringBuilder(64);
      builder.append("ROS2ThreadSettings{");
      appendTo(builder);
      for (Map.Entry<Kind, ROS2ThreadSettings> entry : overrides.entrySet())
      {
         builder.append(", ").append(entry.getKey()).append(": ");
         entry.getValue().appendTo(builder);
      }
      builder.append('}');
      return builder.toString();
   }
}
