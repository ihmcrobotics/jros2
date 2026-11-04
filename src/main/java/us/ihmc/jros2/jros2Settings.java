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

interface jros2Settings
{
   String getSourceName();

   /**
    * The default domain ID jros2 will use when constructing new {@link ROS2Node}.
    * See: <a href="https://docs.ros.org/en/humble/Concepts/Intermediate/About-Domain-ID.html#the-ros-domain-id">ROS 2 Domain ID</a>.
    */
   int rosDomainId();

   /**
    * @return Whether a default domain ID has been specified.
    */
   boolean hasROSDomainId();

   /**
    * @return true if intraprocess delivery should be enabled. Using intraprocess delivery means publishers will directly
    *       call the receive-method of subscriptions. Please note that any operations the subscriptions perform in
    *       the {@link ROS2MessageReader} will block the publish method of the {@link ROS2Publisher}. Do not enable
    *       in performance critical loops without using {@link AsyncROS2Publisher}.
    *       See: <a href="https://fast-dds.docs.eprosima.com/en/v3.6.2/fastdds/transport/intraprocess.html">Intra-process delivery</a>
    */
   boolean intraprocessDelivery();

   /**
    * @return Whether enabling Fast-DDS intraprocess delivery has been specified.
    */
   boolean hasIntraprocessDelivery();

   /**
    * A list of addresses (IPv4 or IPv6) and/or interface names which correspond to network interfaces on the host system.
    * {@link ROS2Node} will only be able to communicate using whitelisted network interfaces.
    * Empty or null for no whitelist.
    * This is a Fast-DDS parameter. See: <a href="https://fast-dds.docs.eprosima.com/en/v3.6.2/fastdds/transport/whitelist.html">Interface Whitelist</a>.
    */
   String[] interfaceWhitelist();

   /**
    * @return Whether an interface whitelist has been specified.
    */
   boolean hasInterfaceWhitelist();

   /**
    * Additional string-valued settings. Each key can be given as a system property, an environment variable, or a
    * jros2.properties entry. Sources only report the raw text; callers apply the semantics (for example the exact rcl
    * parsing rules for {@code ROS_AUTOMATIC_DISCOVERY_RANGE}).
    */
   enum Key
   {
      /** ROS 2 distribution of the peers, e.g. "humble" or "jazzy". Used to pick the ROS graph message format. */
      ROS_DISTRO("ros.distro", "ROS_DISTRO", "jros2.ros.distro"),
      /** ROS graph (ros_discovery_info) compatibility: AUTO, IRON_PLUS, HUMBLE or DUAL. */
      GRAPH_COMPATIBILITY("jros2.graph.compatibility", "JROS2_GRAPH_COMPATIBILITY", "jros2.graph.compatibility"),
      /** Deprecated ROS 2 Humble variable; "1" restricts discovery to localhost. */
      LOCALHOST_ONLY("ros.localhost.only", "ROS_LOCALHOST_ONLY", "jros2.ros.localhost.only"),
      /** OFF, LOCALHOST, SUBNET or SYSTEM_DEFAULT (ROS 2 Iron and newer). */
      AUTOMATIC_DISCOVERY_RANGE("ros.automatic.discovery.range", "ROS_AUTOMATIC_DISCOVERY_RANGE", "jros2.ros.automatic.discovery.range"),
      /** Semicolon-separated hostnames or IPv4 addresses (ROS 2 Iron and newer). */
      STATIC_PEERS("ros.static.peers", "ROS_STATIC_PEERS", "jros2.ros.static.peers"),
      /** SROS2: "true" enables DDS-Security. */
      SECURITY_ENABLE("ros.security.enable", "ROS_SECURITY_ENABLE", "jros2.ros.security.enable"),
      /** SROS2: "Enforce" or anything else (Permissive). */
      SECURITY_STRATEGY("ros.security.strategy", "ROS_SECURITY_STRATEGY", "jros2.ros.security.strategy"),
      /** SROS2: path to the keystore directory. */
      SECURITY_KEYSTORE("ros.security.keystore", "ROS_SECURITY_KEYSTORE", "jros2.ros.security.keystore"),
      /** SROS2: enclave used for the keystore lookup instead of the node's enclave. */
      SECURITY_ENCLAVE_OVERRIDE("ros.security.enclave.override", "ROS_SECURITY_ENCLAVE_OVERRIDE", "jros2.ros.security.enclave.override"),
      /** SROS2: file the DDS-Security logging plugin writes to. */
      SECURITY_LOG_FILE("ros.security.log.file", "ROS_SECURITY_LOG_FILE", "jros2.ros.security.log.file"),
      /** SROS2: "true" or "false", whether DDS-Security log entries are published on the DDS log topic. */
      SECURITY_LOG_PUBLISH("ros.security.log.publish", "ROS_SECURITY_LOG_PUBLISH", "jros2.ros.security.log.publish"),
      /** SROS2: DDS-Security log verbosity: FATAL, ERROR, WARN, INFO or DEBUG. */
      SECURITY_LOG_VERBOSITY("ros.security.log.verbosity", "ROS_SECURITY_LOG_VERBOSITY", "jros2.ros.security.log.verbosity"),
      /**
       * SROS2: AUTO (default), true or false, whether DDS-Security announces its certificate algorithms with the legacy names
       * that the Fast-DDS of ROS 2 Foxy, Galactic and Humble requires. AUTO does so when ROS_DISTRO is one of these distros.
       */
      SECURITY_LEGACY_ALGORITHMS("jros2.security.legacy.algorithms", "JROS2_SECURITY_LEGACY_ALGORITHMS", "jros2.security.legacy.algorithms"),
      /**
       * Shared-memory segment size of the participants jros2 creates: bytes, optionally with a binary suffix (K, KiB, M,
       * MiB, G, GiB), e.g. "8M"; 0 keeps Fast-DDS's default (512 KiB). See {@link ROS2NodeOptions#shmSegmentSize(long)}.
       */
      SHM_SEGMENT_SIZE("jros2.shm.segment.size", "JROS2_SHM_SEGMENT_SIZE", "jros2.shm.segment.size");

      final String property;
      final String environmentVariable;
      final String fileKey;

      Key(String property, String environmentVariable, String fileKey)
      {
         this.property = property;
         this.environmentVariable = environmentVariable;
         this.fileKey = fileKey;
      }
   }

   /**
    * @return the raw value of {@code key} from this source, or null if this source does not specify it.
    */
   default String rawSetting(Key key)
   {
      return null;
   }

   /**
    * @return whether this source specifies {@code key}.
    */
   default boolean hasRawSetting(Key key)
   {
      return false;
   }

   static String[] splitInterfaceWhitelistFromCSV(String interfaceWhitelistCSV)
   {
      if (interfaceWhitelistCSV.trim().isEmpty())
      {
         return new String[0];
      }
      else
      {
         return interfaceWhitelistCSV.split("\\s*,\\s*");
      }
   }
}
