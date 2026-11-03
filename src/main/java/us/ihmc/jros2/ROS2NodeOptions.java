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
   private String namespace;
   private Integer domainId;
   private TransportDescriptorType[] transports;
   private Executor callbackExecutor;
   private ROS2ThreadSettings threadSettings;
   private String enclave;
   private ROS2DiscoveryRange discoveryRange;
   private String[] staticPeers;
   private ROS2Context context;

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
    * Shares one DDS participant between all nodes created with the same context, like nodes in one rclcpp context.
    * Participant-level options (domain, transports, thread settings, enclave, discovery) are then taken from the context.
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
}
