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

import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The shared-memory segment size of a participant ({@link ROS2NodeOptions#shmSegmentSize(long)}, setting
 * {@code JROS2_SHM_SEGMENT_SIZE}).
 * <p>
 * Fast-DDS's shared-memory transport copies every message (or fragment of a large message) that a participant sends to
 * the participants on its host into the sending participant's own segment; the receivers read it from there and release it.
 * When the segment is full, the sender discards what does not fit. Fast-DDS's default segment is 512 KiB, so every larger
 * message lost fragments: a RELIABLE reader asked for them again (NACK_FRAG after its 5 ms heartbeat response delay, resent
 * after the writer's 5 ms NACK response delay), which delayed every such message by about 11 ms, and BEST_EFFORT lost it.
 * jros2 therefore gives its participants a {@value ROS2NodeOptions#DEFAULT_SHM_SEGMENT_SIZE}-byte segment, through its
 * own shared-memory and UDPv4 transport descriptors (the same transports as Fast-DDS's builtin ones, which have no
 * segment size option of their own). Only the segment size differs, which is not announced in discovery: peers, including
 * rmw_fastrtps nodes, read from the larger segment as from any other.
 * <p>
 * Resolution: the node or context option, then the {@code JROS2_SHM_SEGMENT_SIZE} setting, then 0 (Fast-DDS's default
 * transports) when the {@code FASTDDS_BUILTIN_TRANSPORTS} environment variable is set or on Android (UDPv4 only), then
 * {@link ROS2NodeOptions#DEFAULT_SHM_SEGMENT_SIZE}. A positive size falls back to 0, with a warning, when {@code /dev/shm}
 * has less than twice that size free.
 */
final class ROS2SharedMemoryConfiguration
{
   /** Name of the Fast-DDS environment variable that selects its builtin transports. */
   static final String FASTDDS_BUILTIN_TRANSPORTS = "FASTDDS_BUILTIN_TRANSPORTS";
   /** Directory of the POSIX shared memory on Linux, where Fast-DDS creates its segments. */
   static final String SHARED_MEMORY_DIRECTORY = "/dev/shm";

   private static final Map<Long, TransportDescriptorType> SHARED_MEMORY_DESCRIPTORS = new HashMap<>();

   /** UDPv4 transport that goes with jros2's shared-memory transport: Fast-DDS's defaults, like the builtin UDPv4 transport. */
   static final TransportDescriptorType UDPV4 = descriptor("jros2_udpv4", "UDPv4");

   private final long segmentSize;
   private final String source;
   private final long requestedSegmentSize;
   private final long freeBytes;

   private ROS2SharedMemoryConfiguration(long segmentSize, String source, long requestedSegmentSize, long freeBytes)
   {
      this.segmentSize = segmentSize;
      this.source = source;
      this.requestedSegmentSize = requestedSegmentSize;
      this.freeBytes = freeBytes;
   }

   /**
    * @param optionValue               the node or context option, or null
    * @param settings                  the jros2 settings
    * @param builtinTransportsVariable the value of {@code FASTDDS_BUILTIN_TRANSPORTS}, or null
    * @param android                   whether this is Android
    * @param freeBytes                 the free space of {@code /dev/shm} in bytes, or a negative value when unknown
    * @return the resolved configuration
    * @throws IllegalArgumentException if the setting is not a valid size
    */
   static ROS2SharedMemoryConfiguration resolve(Long optionValue,
                                                Function<jros2Settings.Key, String> settings,
                                                String builtinTransportsVariable,
                                                boolean android,
                                                long freeBytes)
   {
      long size;
      String source;
      String setting = settings.apply(jros2Settings.Key.SHM_SEGMENT_SIZE);
      if (optionValue != null)
      {
         size = ROS2NodeOptions.validateShmSegmentSize(optionValue, "ROS2NodeOptions.shmSegmentSize");
         source = "ROS2NodeOptions";
      }
      else if (setting != null && !setting.trim().isEmpty())
      {
         size = parseSize(setting, jros2Settings.Key.SHM_SEGMENT_SIZE.environmentVariable);
         source = jros2Settings.Key.SHM_SEGMENT_SIZE.environmentVariable;
      }
      else if (builtinTransportsVariable != null && !builtinTransportsVariable.trim().isEmpty())
      {
         size = ROS2NodeOptions.FASTDDS_DEFAULT_SHM_SEGMENT_SIZE;
         source = FASTDDS_BUILTIN_TRANSPORTS;
      }
      else if (android)
      {
         size = ROS2NodeOptions.FASTDDS_DEFAULT_SHM_SEGMENT_SIZE;
         source = "default (Android)";
      }
      else
      {
         size = ROS2NodeOptions.DEFAULT_SHM_SEGMENT_SIZE;
         source = "default";
      }

      if (size > 0 && freeBytes >= 0 && freeBytes < 2 * size)
      {
         return new ROS2SharedMemoryConfiguration(ROS2NodeOptions.FASTDDS_DEFAULT_SHM_SEGMENT_SIZE, source, size, freeBytes);
      }
      return new ROS2SharedMemoryConfiguration(size, source, 0, freeBytes);
   }

   /**
    * Parses a size in bytes with an optional binary suffix: K or KiB (1024), M or MiB, G or GiB, case-insensitive, with or
    * without a space before the suffix ("4194304", "4M", "4 MiB", "512k").
    *
    * @throws IllegalArgumentException if {@code text} is not such a size or is out of range
    */
   static long parseSize(String text, String name)
   {
      String trimmed = text.trim();
      String upper = trimmed.toUpperCase(Locale.ROOT);
      long multiplier = 1;
      String[][] suffixes = {{"KIB", "1024"}, {"MIB", "1048576"}, {"GIB", "1073741824"}, {"K", "1024"}, {"M", "1048576"}, {"G", "1073741824"}};
      for (String[] suffix : suffixes)
      {
         if (upper.endsWith(suffix[0]))
         {
            multiplier = Long.parseLong(suffix[1]);
            upper = upper.substring(0, upper.length() - suffix[0].length()).trim();
            break;
         }
      }
      long value;
      try
      {
         value = Long.parseLong(upper);
      }
      catch (NumberFormatException e)
      {
         throw new IllegalArgumentException(name + " is not a valid size: '" + text + "' (use bytes, optionally with K, KiB, M, MiB, G or GiB, e.g. 8M; 0 for the Fast-DDS default)");
      }
      if (value < 0 || value > ROS2NodeOptions.MAX_SHM_SEGMENT_SIZE / multiplier)
      {
         throw new IllegalArgumentException(name + " is out of range: '" + text + "'");
      }
      return ROS2NodeOptions.validateShmSegmentSize(value * multiplier, name);
   }

   /**
    * @return the free space of {@code /dev/shm} in bytes, or -1 when there is no such directory or it cannot be read
    */
   static long sharedMemoryFreeBytes()
   {
      try
      {
         Path directory = Paths.get(SHARED_MEMORY_DIRECTORY);
         if (!Files.isDirectory(directory))
         {
            return -1;
         }
         return Files.getFileStore(directory).getUsableSpace();
      }
      catch (IOException | RuntimeException e)
      {
         return -1;
      }
   }

   /**
    * @return jros2's shared-memory transport descriptor with this segment size (one per size and process, so the same size
    *       always has the same transport ID)
    */
   static TransportDescriptorType sharedMemoryDescriptor(long segmentSize)
   {
      synchronized (SHARED_MEMORY_DESCRIPTORS)
      {
         TransportDescriptorType descriptor = SHARED_MEMORY_DESCRIPTORS.get(segmentSize);
         if (descriptor == null)
         {
            descriptor = descriptor("jros2_shm_" + segmentSize, "SHM");
            descriptor.setSegmentSize(segmentSize);
            SHARED_MEMORY_DESCRIPTORS.put(segmentSize, descriptor);
         }
         return descriptor;
      }
   }

   private static TransportDescriptorType descriptor(String transportId, String type)
   {
      TransportDescriptorType descriptor = new TransportDescriptorType();
      descriptor.setTransportId(transportId);
      descriptor.setType(type);
      return descriptor;
   }

   /** @return the segment size in bytes, or 0 for Fast-DDS's default shared-memory transport */
   long getSegmentSize()
   {
      return segmentSize;
   }

   /** @return what selected the size: "ROS2NodeOptions", "JROS2_SHM_SEGMENT_SIZE", "FASTDDS_BUILTIN_TRANSPORTS" or "default" */
   String getSource()
   {
      return source;
   }

   /** @return true if the requested size did not fit {@code /dev/shm} and Fast-DDS's default is used instead */
   boolean isFallback()
   {
      return requestedSegmentSize != 0;
   }

   /** @return the size that was requested before the fallback, or 0 */
   long getRequestedSegmentSize()
   {
      return requestedSegmentSize;
   }

   /** @return the warning for a fallback, or null */
   String fallbackWarning(String participantName)
   {
      if (!isFallback())
      {
         return null;
      }
      return "Only " + mebibytes(freeBytes) + " are free in " + SHARED_MEMORY_DIRECTORY + ": participant '" + participantName
             + "' uses Fast-DDS's default 512 KiB shared-memory segment instead of " + mebibytes(requestedSegmentSize) + " (" + source
             + "). Messages larger than about 512 KiB sent through shared memory are delayed by about 11 ms (RELIABLE) or lost (BEST_EFFORT). Give "
             + SHARED_MEMORY_DIRECTORY + " more space (e.g. docker run --shm-size=512m), share participants with ROS2Context, or set "
             + jros2Settings.Key.SHM_SEGMENT_SIZE.environmentVariable + ".";
   }

   /** @return a size in MiB with one decimal, e.g. "4.0 MiB" */
   static String mebibytes(long bytes)
   {
      return String.format(Locale.ROOT, "%.1f MiB", bytes / 1048576.0);
   }

   @Override
   public String toString()
   {
      if (segmentSize == 0)
      {
         return "Fast-DDS default (512 KiB)" + (isFallback() ? ", " + mebibytes(requestedSegmentSize) + " did not fit " + SHARED_MEMORY_DIRECTORY : "");
      }
      return mebibytes(segmentSize);
   }
}
