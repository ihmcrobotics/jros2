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

import java.util.concurrent.Executor;

/**
 * Options for constructing a {@link ROS2Node}. Unset options fall back to the jros2 settings (system properties,
 * environment variables, jros2.properties) and then to the ROS 2 defaults.
 * <pre>{@code
 * ROS2Node node = new ROS2Node("talker", ROS2NodeOptions.defaults().namespace("/robot").domainId(42));
 * }</pre>
 */
public final class ROS2NodeOptions
{
   /**
    * Shared-memory segment size, in bytes, that jros2 gives the participants it creates unless configured otherwise
    * ({@link #shmSegmentSize(long)}, setting {@code JROS2_SHM_SEGMENT_SIZE}): 4 MiB. See {@link #shmSegmentSize(long)}.
    */
   public static final long DEFAULT_SHM_SEGMENT_SIZE = 4L * 1024L * 1024L;
   /** {@link #shmSegmentSize(long)} value that keeps Fast-DDS's default shared-memory transport (512 KiB segment), as rmw_fastrtps. */
   public static final long FASTDDS_DEFAULT_SHM_SEGMENT_SIZE = 0L;
   /** Smallest shared-memory segment size, in bytes, other than {@link #FASTDDS_DEFAULT_SHM_SEGMENT_SIZE} (64 KiB). */
   public static final long MIN_SHM_SEGMENT_SIZE = 64L * 1024L;
   /** Largest shared-memory segment size, in bytes (1 GiB). */
   public static final long MAX_SHM_SEGMENT_SIZE = 1024L * 1024L * 1024L;

   private String namespace;
   private Integer domainId;
   private TransportDescriptorType[] transports;
   private Executor callbackExecutor;
   private ROS2ThreadSettings threadSettings;
   private String enclave;
   private ROS2DiscoveryRange discoveryRange;
   private String[] staticPeers;
   private ROS2Context context;
   private Long shmSegmentSize;

   /** @return a new options object with nothing set. */
   public static ROS2NodeOptions defaults()
   {
      return new ROS2NodeOptions();
   }

   public ROS2NodeOptions()
   {
   }

   /** Copy constructor. */
   public ROS2NodeOptions(ROS2NodeOptions other)
   {
      namespace = other.namespace;
      domainId = other.domainId;
      transports = other.transports == null ? null : other.transports.clone();
      callbackExecutor = other.callbackExecutor;
      threadSettings = other.threadSettings == null ? null : new ROS2ThreadSettings(other.threadSettings);
      enclave = other.enclave;
      discoveryRange = other.discoveryRange;
      staticPeers = other.staticPeers == null ? null : other.staticPeers.clone();
      context = other.context;
      shmSegmentSize = other.shmSegmentSize;
   }

   /**
    * Node namespace, e.g. "/robot". Defaults to "/". Like rcl, an empty namespace means "/" and a relative namespace
    * ("robot") is made absolute ("/robot"); the result must be valid for {@code rmw_validate_namespace} (for example no
    * trailing '/', no '-'), otherwise the node constructor throws an {@link IllegalArgumentException}.
    */
   public ROS2NodeOptions namespace(String namespace)
   {
      this.namespace = namespace;
      return this;
   }

   /** ROS domain ID in [0, 232]. Defaults to the ROS_DOMAIN_ID setting, then 0. */
   public ROS2NodeOptions domainId(int domainId)
   {
      this.domainId = domainId;
      return this;
   }

   /** Explicit Fast-DDS transports. See {@link us.ihmc.fastddsjava.profiles.TransportDescriptorTypeTools}. */
   public ROS2NodeOptions transports(TransportDescriptorType... transports)
   {
      this.transports = transports;
      return this;
   }

   /**
    * Executor that runs subscription callbacks. When null (the default) callbacks run directly on the Fast-DDS thread
    * that received the data, which has the lowest latency but blocks reception of other topics while a callback runs.
    * <p>
    * Callbacks that run on the executor may also create publishers and subscriptions on their own node (or on nodes
    * sharing its participant), which throws an {@link IllegalStateException} from a callback on a Fast-DDS thread. This
    * needs an executor that runs tasks on its own threads, not inline on the calling thread.
    * <p>
    * jros2's side of the dispatch is allocation-free, but the JDK's executors allocate for every task (a queue node) or
    * every idle wait. Use {@link ROS2CallbackExecutor} for an executor that does not allocate.
    */
   public ROS2NodeOptions callbackExecutor(Executor callbackExecutor)
   {
      this.callbackExecutor = callbackExecutor;
      return this;
   }

   /** Scheduling settings for the native Fast-DDS threads. */
   public ROS2NodeOptions threadSettings(ROS2ThreadSettings threadSettings)
   {
      this.threadSettings = threadSettings;
      return this;
   }

   /** SROS2 security enclave, e.g. "/my_robot/talker". Defaults to "/". */
   public ROS2NodeOptions enclave(String enclave)
   {
      this.enclave = enclave;
      return this;
   }

   /** Overrides the ROS_AUTOMATIC_DISCOVERY_RANGE setting. */
   public ROS2NodeOptions discoveryRange(ROS2DiscoveryRange discoveryRange)
   {
      this.discoveryRange = discoveryRange;
      return this;
   }

   /** Overrides the ROS_STATIC_PEERS setting (hostnames or IPv4 addresses). */
   public ROS2NodeOptions staticPeers(String... staticPeers)
   {
      this.staticPeers = staticPeers;
      return this;
   }

   /**
    * Size, in bytes, of the shared-memory segment of the participant's shared-memory transport. Overrides the
    * {@code JROS2_SHM_SEGMENT_SIZE} setting; the default is {@link #DEFAULT_SHM_SEGMENT_SIZE} (4 MiB).
    * <p>
    * A participant writes every message it sends through shared memory (to subscriptions in other participants on the same
    * host) into its own segment, and the receiving participants read it from there. A message, or the part of it that the
    * receivers have not consumed yet, must fit: Fast-DDS discards the fragments of a large message that do not fit, a
    * RELIABLE subscription gets them again through the RTPS repair about 11 ms later (heartbeat and NACK response delays),
    * and a BEST_EFFORT subscription loses the message. Fast-DDS's own default (and rmw_fastrtps's) is 512 KiB, which
    * delays every larger message; the 4 MiB default delivers messages up to about 4 MB without repair. Give a publisher of
    * larger messages a segment of at least about 1.25 times its largest message.
    * <p>
    * The segment is allocated when the participant is created (Linux: in {@code /dev/shm}; macOS and Windows: as a file on
    * disk) and stays allocated until it is closed, so each participant costs about this much shared memory. On Linux, jros2
    * falls back to Fast-DDS's default segment, with a warning, when {@code /dev/shm} has less than twice the segment size
    * free (for example in a container with Docker's default 64 MB {@code /dev/shm}).
    * <p>
    * Applies to jros2's default transports (shared memory and UDPv4), to the LOCALHOST discovery range and to the
    * interface whitelist. Transports given with {@link #transports} are used as they are, and Android uses UDPv4 only.
    * {@link #FASTDDS_DEFAULT_SHM_SEGMENT_SIZE} (0) keeps Fast-DDS's default shared-memory transport, and with it the
    * {@code FASTDDS_BUILTIN_TRANSPORTS} environment variable; when that variable is set, 0 is also the default.
    *
    * @param bytes 0, or a size in [{@link #MIN_SHM_SEGMENT_SIZE}, {@link #MAX_SHM_SEGMENT_SIZE}]
    * @throws IllegalArgumentException if {@code bytes} is out of range
    */
   public ROS2NodeOptions shmSegmentSize(long bytes)
   {
      this.shmSegmentSize = validateShmSegmentSize(bytes, "ROS2NodeOptions.shmSegmentSize");
      return this;
   }

   static long validateShmSegmentSize(long bytes, String source)
   {
      if (bytes != FASTDDS_DEFAULT_SHM_SEGMENT_SIZE && (bytes < MIN_SHM_SEGMENT_SIZE || bytes > MAX_SHM_SEGMENT_SIZE))
      {
         throw new IllegalArgumentException(source + " must be 0 (Fast-DDS default) or between " + MIN_SHM_SEGMENT_SIZE + " and " + MAX_SHM_SEGMENT_SIZE
                                            + " bytes, but is " + bytes);
      }
      return bytes;
   }

   /**
    * Shares one DDS participant between all nodes created with the same context, like nodes in one rclcpp context.
    * Participant-level options (domain, transports, shared-memory segment size, thread settings, enclave, discovery) are
    * then taken from the context.
    */
   public ROS2NodeOptions context(ROS2Context context)
   {
      this.context = context;
      return this;
   }

   public String getNamespace()
   {
      return namespace;
   }

   /** @return the domain ID, or null if not set. */
   public Integer getDomainId()
   {
      return domainId;
   }

   public TransportDescriptorType[] getTransports()
   {
      return transports;
   }

   public Executor getCallbackExecutor()
   {
      return callbackExecutor;
   }

   public ROS2ThreadSettings getThreadSettings()
   {
      return threadSettings;
   }

   public String getEnclave()
   {
      return enclave;
   }

   public ROS2DiscoveryRange getDiscoveryRange()
   {
      return discoveryRange;
   }

   public String[] getStaticPeers()
   {
      return staticPeers;
   }

   public ROS2Context getContext()
   {
      return context;
   }

   /** @return the shared-memory segment size in bytes, or null if not set */
   public Long getShmSegmentSize()
   {
      return shmSegmentSize;
   }
}
