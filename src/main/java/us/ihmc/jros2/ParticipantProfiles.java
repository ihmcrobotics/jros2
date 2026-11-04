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

import us.ihmc.fastddsjava.fastddsjavaException;
import us.ihmc.fastddsjava.natives.fastddsjava;
import us.ihmc.fastddsjava.profiles.OctetVectorTools;
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.TransportDescriptorTypeTools;
import us.ihmc.fastddsjava.profiles.gen.AllocationConfigType;
import us.ihmc.fastddsjava.profiles.gen.BuiltinAttributesType;
import us.ihmc.fastddsjava.profiles.gen.BuiltinTransportKind;
import us.ihmc.fastddsjava.profiles.gen.BuiltinTransportsType;
import us.ihmc.fastddsjava.profiles.gen.HistoryMemoryPolicyType;
import us.ihmc.fastddsjava.profiles.gen.LocatorListType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps.UserTransports;
import us.ihmc.fastddsjava.profiles.gen.PropertyPolicyType;
import us.ihmc.fastddsjava.profiles.gen.PropertyType;
import us.ihmc.fastddsjava.profiles.gen.RtpsParticipantAllocationAttributesType;
import us.ihmc.fastddsjava.profiles.gen.ThreadSettingsType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;
import us.ihmc.fastddsjava.profiles.gen.Udpv4LocatorType;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Builds the Fast-DDS participant profile for a {@link ROS2Node} or {@link ROS2Context}: transports, discovery,
 * participant user data (ROS enclave), DDS-Security properties and thread settings, reproducing what rmw_fastrtps (Jazzy)
 * configures for a ROS 2 participant:
 * <ul>
 *    <li>USER_DATA {@code enclave=<enclave>;} followed by a NUL byte (enclave from {@link ROS2NodeOptions#enclave}, default
 *    "/"), which rmw_fastrtps peers need to add the participant to their ROS graph.</li>
 *    <li>Builtin (discovery) reader and writer history memory policy PREALLOCATED_WITH_REALLOC.</li>
 *    <li>Transports: shared memory with a {@link ROS2NodeOptions#DEFAULT_SHM_SEGMENT_SIZE}-byte segment and UDPv4 by default
 *    ({@link ROS2SharedMemoryConfiguration}; Fast-DDS's builtin transports, as rmw_fastrtps, with a segment size of 0), UDPv4
 *    only on Android, or the interface whitelist or custom transports.</li>
 *    <li>Discovery ({@link ROS2DiscoveryConfiguration}):
 *       <ul>
 *          <li>OFF: at most one participant (itself), a unicast metatraffic locator and no multicast; transports unchanged.</li>
 *          <li>LOCALHOST: unicast metatraffic locator, no multicast, initial peers = static peers + 127.0.0.1, and UDPv4
 *          transports with {@code maxInitialPeersRange} 32. With the default transports these are SHM + UDPv4 (UDPv4 only on
 *          Android); an interface whitelist or custom transports are kept, their UDPv4 descriptors get the larger peer range
 *          unless they set one.</li>
 *          <li>SUBNET: Fast-DDS defaults; with static peers the initial peers are the peers followed by 239.255.0.1.</li>
 *          <li>SYSTEM_DEFAULT: Fast-DDS defaults, static peers ignored.</li>
 *       </ul>
 *       Static peers are resolved to IPv4 in Java (first IPv4 address of the name); an unresolvable peer is an error.</li>
 *    <li>SROS2 security ({@link ROS2SecurityConfiguration}).</li>
 *    <li>Thread settings ({@link ROS2ThreadSettings}) for the participant threads and the transport reception threads.</li>
 * </ul>
 */
final class ParticipantProfiles
{
   /** Default ROS enclave. */
   static final String DEFAULT_ENCLAVE = "/";

   private ParticipantProfiles()
   {
   }

   /**
    * The inputs that come from the process environment, so that tests can replace them.
    */
   static final class Environment
   {
      final Function<jros2Settings.Key, String> settings;
      final String[] interfaceWhitelist;
      final BooleanSupplier nativeHasSecurity;
      final boolean android;
      final ROS2DiscoveryConfiguration.PeerResolver peerResolver;
      /** The value of the FASTDDS_BUILTIN_TRANSPORTS environment variable, or null. */
      final String builtinTransportsVariable;
      /** The free space of /dev/shm in bytes, or a negative value when unknown. */
      final LongSupplier sharedMemoryFreeBytes;

      Environment(Function<jros2Settings.Key, String> settings,
                  String[] interfaceWhitelist,
                  BooleanSupplier nativeHasSecurity,
                  boolean android,
                  ROS2DiscoveryConfiguration.PeerResolver peerResolver)
      {
         this(settings, interfaceWhitelist, nativeHasSecurity, android, peerResolver, null, () -> -1L);
      }

      Environment(Function<jros2Settings.Key, String> settings,
                  String[] interfaceWhitelist,
                  BooleanSupplier nativeHasSecurity,
                  boolean android,
                  ROS2DiscoveryConfiguration.PeerResolver peerResolver,
                  String builtinTransportsVariable,
                  LongSupplier sharedMemoryFreeBytes)
      {
         this.settings = settings;
         this.interfaceWhitelist = interfaceWhitelist;
         this.nativeHasSecurity = nativeHasSecurity;
         this.android = android;
         this.peerResolver = peerResolver;
         this.builtinTransportsVariable = builtinTransportsVariable;
         this.sharedMemoryFreeBytes = sharedMemoryFreeBytes;
      }

      /** @return the environment of this process: jros2 settings, loaded natives, platform and DNS */
      static Environment current()
      {
         jros2 settings = jros2.get();
         return new Environment(settings::rawSetting,
                                settings.interfaceWhitelist(),
                                fastddsjava::hasSecurity,
                                TransportDescriptorTypeTools.isAndroid(),
                                InetAddress::getAllByName,
                                System.getenv(ROS2SharedMemoryConfiguration.FASTDDS_BUILTIN_TRANSPORTS),
                                ROS2SharedMemoryConfiguration::sharedMemoryFreeBytes);
      }
   }

   /**
    * Everything {@link #build} decided for a participant.
    */
   static final class ParticipantConfiguration
   {
      final ParticipantProfileType profile;
      /** User transports, or null when the Fast-DDS builtin transports are used. */
      final TransportDescriptorType[] transports;
      /** Whether {@link #transports} are the node's or context's own (ROS2NodeOptions.transports), not selected by jros2. */
      final boolean customTransports;
      final ROS2DiscoveryConfiguration discovery;
      /** The shared-memory segment size of jros2's shared-memory transport (unused with custom transports or UDPv4 only). */
      final ROS2SharedMemoryConfiguration sharedMemory;
      final List<String> resolvedStaticPeers;
      final String enclave;
      final ROS2SecurityConfiguration security;
      final ROS2ThreadSettings threadSettings;
      final List<String> warnings;

      ParticipantConfiguration(ParticipantProfileType profile,
                               TransportDescriptorType[] transports,
                               boolean customTransports,
                               ROS2DiscoveryConfiguration discovery,
                               ROS2SharedMemoryConfiguration sharedMemory,
                               List<String> resolvedStaticPeers,
                               String enclave,
                               ROS2SecurityConfiguration security,
                               ROS2ThreadSettings threadSettings,
                               List<String> warnings)
      {
         this.profile = profile;
         this.transports = transports;
         this.customTransports = customTransports;
         this.discovery = discovery;
         this.sharedMemory = sharedMemory;
         this.resolvedStaticPeers = Collections.unmodifiableList(resolvedStaticPeers);
         this.enclave = enclave;
         this.security = security;
         this.threadSettings = threadSettings;
         this.warnings = Collections.unmodifiableList(warnings);
      }

      /** @return whether the participant uses the Fast-DDS builtin transports (implicitly or via builtinTransports) */
      boolean usesBuiltinTransports()
      {
         return transports == null;
      }
   }

   /**
    * Adds a participant profile named {@code profileName} to {@code profilesXML}. The transport descriptors it references are
    * registered with Fast-DDS before this method returns ({@link TransportDescriptorTypeTools#ensureRegistered}), so the
    * caller only has to load {@code profilesXML}.
    *
    * @param profileName     unique profile name (e.g. "p_3")
    * @param participantName name advertised for the DDS participant
    * @param domainId        validated ROS domain ID
    * @param options         node or context options; may be null
    * @param nodeClass       class used for the configuration printout
    * @param profilesXML     the XML document to add the profile to; the caller loads it
    * @return the participant profile that was added
    * @throws IllegalStateException    if the requested configuration cannot be satisfied (for example SROS2 security is
    *                                  enforced but the native library lacks DDS-Security, or a transport cannot be registered)
    * @throws IllegalArgumentException if an option or setting is invalid (enclave name, unresolvable static peer, ...)
    */
   static ParticipantProfileType create(String profileName,
                                        String participantName,
                                        int domainId,
                                        ROS2NodeOptions options,
                                        Class<? extends ROS2Node> nodeClass,
                                        ProfilesXML profilesXML)
   {
      ParticipantConfiguration configuration = build(profileName, participantName, domainId, options, Environment.current());

      try
      {
         TransportDescriptorTypeTools.ensureRegistered(configuration.transports);
      }
      catch (fastddsjavaException e)
      {
         throw new IllegalStateException("Failed to register the Fast-DDS transport descriptors of participant profile '" + profileName + "'", e);
      }

      for (String warning : configuration.warnings)
      {
         jros2.getLogger().warning(warning);
      }
      String sharedMemoryWarning = configuration.sharedMemory.fallbackWarning(participantName);
      if (sharedMemoryWarning != null)
      {
         // Once per process: in a container with a small /dev/shm, every further participant falls back the same way
         if (SHARED_MEMORY_FALLBACK_WARNED.compareAndSet(false, true))
            jros2.getLogger().warning(sharedMemoryWarning + " (Logged once; later participants fall back silently.)");
         else
            jros2.getLogger().fine(sharedMemoryWarning);
      }

      checkSHMAvailabilityWindows(configuration);

      profilesXML.addParticipantProfile(configuration.profile);

      ROS2NodePrintout.print(nodeClass, configuration);

      return configuration.profile;
   }

   private static final AtomicBoolean SHARED_MEMORY_FALLBACK_WARNED = new AtomicBoolean();

   /**
    * Builds the participant profile without side effects on Fast-DDS (transports are selected but not registered).
    */
   static ParticipantConfiguration build(String profileName, String participantName, int domainId, ROS2NodeOptions options, Environment environment)
   {
      if (options == null)
      {
         options = ROS2NodeOptions.defaults();
      }
      List<String> warnings = new ArrayList<>();

      String enclave = options.getEnclave() == null ? DEFAULT_ENCLAVE : options.getEnclave();
      ROS2SecurityConfiguration.validateEnclave(enclave);

      ROS2DiscoveryConfiguration discovery = ROS2DiscoveryConfiguration.resolve(options.getDiscoveryRange(), options.getStaticPeers(), environment.settings);
      warnings.addAll(discovery.getWarnings());
      List<String> resolvedPeers = discovery.resolveStaticPeers(environment.peerResolver);

      ROS2SecurityConfiguration security = ROS2SecurityConfiguration.resolve(enclave, environment.settings, environment.nativeHasSecurity);
      warnings.addAll(security.getWarnings());

      ROS2ThreadSettings threadSettings = options.getThreadSettings() == null ? null : new ROS2ThreadSettings(options.getThreadSettings());

      ParticipantProfileType participantProfile = new ParticipantProfileType();
      participantProfile.setDomainId(domainId);
      participantProfile.setProfileName(profileName);
      Rtps rtps = new Rtps();
      rtps.setAllocation(allocationAttributes());
      rtps.setName(participantName);
      participantProfile.setRtps(rtps);

      // Transports
      ROS2SharedMemoryConfiguration sharedMemory = ROS2SharedMemoryConfiguration.resolve(options.getShmSegmentSize(),
                                                                                         environment.settings,
                                                                                         environment.builtinTransportsVariable,
                                                                                         environment.android,
                                                                                         environment.sharedMemoryFreeBytes.getAsLong());
      TransportDescriptorType[] transports = selectTransports(options.getTransports(), discovery.getRange(), threadSettings, sharedMemory.getSegmentSize(), environment);
      if (transports == null)
      {
         ThreadSettingsType receptionThreads = threadSettings == null ? null : threadSettings.toThreadSettingsType(ROS2ThreadSettings.Kind.RECEPTION);
         if (receptionThreads != null)
         {
            // Fast-DDS only applies builtin_transports_reception_threads when the builtin transports are created by an
            // explicit <builtinTransports> element that follows it.
            rtps.setBuiltinTransportsReceptionThreads(receptionThreads);
            BuiltinTransportsType builtinTransports = new BuiltinTransportsType();
            builtinTransports.setValue(BuiltinTransportKind.DEFAULT);
            rtps.setBuiltinTransports(builtinTransports);
         }
         else
         {
            rtps.setUseBuiltinTransports(true);
         }
      }
      else
      {
         rtps.setUseBuiltinTransports(false);
         UserTransports userTransports = new UserTransports();
         for (TransportDescriptorType transport : transports)
         {
            userTransports.getTransportId().add(transport.getTransportId());
         }
         rtps.setUserTransports(userTransports);
      }

      // Discovery
      BuiltinAttributesType builtin = new BuiltinAttributesType();
      // rmw_fastrtps: allow reallocation to support discovery messages bigger than 5000 bytes
      builtin.setReaderHistoryMemoryPolicy(HistoryMemoryPolicyType.PREALLOCATED_WITH_REALLOC);
      builtin.setWriterHistoryMemoryPolicy(HistoryMemoryPolicyType.PREALLOCATED_WITH_REALLOC);
      switch (discovery.getRange())
      {
         case OFF ->
         {
            builtin.setMetatrafficUnicastLocatorList(locators(new String[] {null}));
            AllocationConfigType participants = new AllocationConfigType();
            participants.setInitial(1L);
            participants.setMaximum(1L);
            participants.setIncrement(0L);
            rtps.getAllocation().setTotalParticipants(participants);
         }
         case LOCALHOST ->
         {
            builtin.setMetatrafficUnicastLocatorList(locators(new String[] {null}));
            List<String> initialPeers = new ArrayList<>(resolvedPeers);
            initialPeers.add(ROS2DiscoveryConfiguration.LOCALHOST_ADDRESS);
            builtin.setInitialPeersList(locators(initialPeers.toArray(new String[0])));
         }
         case SUBNET ->
         {
            if (!resolvedPeers.isEmpty())
            {
               List<String> initialPeers = new ArrayList<>(resolvedPeers);
               initialPeers.add(ROS2DiscoveryConfiguration.MULTICAST_DISCOVERY_ADDRESS);
               builtin.setInitialPeersList(locators(initialPeers.toArray(new String[0])));
            }
         }
         case SYSTEM_DEFAULT ->
         {
            // Fast-DDS defaults
         }
      }
      rtps.setBuiltin(builtin);

      // Security
      if (security.getStatus() == ROS2SecurityConfiguration.Status.ENABLED)
      {
         PropertyPolicyType propertyPolicy = new PropertyPolicyType();
         PropertyPolicyType.Properties properties = new PropertyPolicyType.Properties();
         for (Map.Entry<String, String> entry : security.getProperties().entrySet())
         {
            PropertyType property = new PropertyType();
            property.setName(entry.getKey());
            property.setValue(entry.getValue());
            properties.getProperty().add(property);
         }
         propertyPolicy.getPropertiesOrBinaryProperties().add(properties);
         rtps.setPropertiesPolicy(propertyPolicy);
      }

      // ROS graph: rmw_fastrtps peers only add participants announcing "enclave=<name>;" (rmw writes a terminating NUL)
      rtps.setUserData(OctetVectorTools.fromAscii("enclave=" + enclave + ";", true));

      // Participant threads
      if (threadSettings != null)
      {
         rtps.setTimedEventsThread(threadSettings.toThreadSettingsType(ROS2ThreadSettings.Kind.TIMED_EVENTS));
         rtps.setBuiltinControllersSenderThread(threadSettings.toThreadSettingsType(ROS2ThreadSettings.Kind.BUILTIN_CONTROLLERS_SENDER));
         rtps.setTypelookupServiceThread(threadSettings.toThreadSettingsType(ROS2ThreadSettings.Kind.TYPELOOKUP_SERVICE));
         rtps.setDiscoveryServerThread(threadSettings.toThreadSettingsType(ROS2ThreadSettings.Kind.DISCOVERY_SERVER));
         if (security.getStatus() == ROS2SecurityConfiguration.Status.ENABLED)
         {
            rtps.setSecurityLogThread(threadSettings.toThreadSettingsType(ROS2ThreadSettings.Kind.SECURITY_LOG));
         }
      }

      boolean customTransports = options.getTransports() != null && options.getTransports().length > 0;
      return new ParticipantConfiguration(participantProfile, transports, customTransports, discovery, sharedMemory, resolvedPeers, enclave, security, threadSettings, warnings);
   }

   /**
    * Selects the participant's user transports, or null for the Fast-DDS builtin transports.
    *
    * @param shmSegmentSize the segment size of jros2's shared-memory transport, or 0 for Fast-DDS's default
    */
   static TransportDescriptorType[] selectTransports(TransportDescriptorType[] requestedTransports,
                                                     ROS2DiscoveryRange range,
                                                     ROS2ThreadSettings threadSettings,
                                                     long shmSegmentSize,
                                                     Environment environment)
   {
      TransportDescriptorType[] transports = TransportDescriptorTypeTools.selectTransports(requestedTransports, environment.interfaceWhitelist, environment.android);
      boolean localhost = range == ROS2DiscoveryRange.LOCALHOST;

      boolean customTransports = requestedTransports != null && requestedTransports.length > 0;
      boolean whitelist = !customTransports && environment.interfaceWhitelist != null && environment.interfaceWhitelist.length > 0;
      boolean defaultTransports = !customTransports && !whitelist;
      if (defaultTransports && environment.android)
      {
         // UDPv4 only (SHM is unreliable on Android); rmw_fastrtps LOCALHOST: maxInitialPeersRange 32
         transports = localhost ? new TransportDescriptorType[] {LocalhostTransports.UDPV4} : transports;
      }
      else if (defaultTransports && (localhost || shmSegmentSize > 0))
      {
         // Shared memory with jros2's segment size (ROS2SharedMemoryConfiguration) and UDPv4 instead of the builtin
         // transports; rmw_fastrtps LOCALHOST: SHM + UDPv4 with maxInitialPeersRange 32 (no interface whitelist). Dedicated
         // descriptors: the shared cached ones may have been handed out (and modified) as custom transports.
         transports = new TransportDescriptorType[] {shmSegmentSize > 0 ? ROS2SharedMemoryConfiguration.sharedMemoryDescriptor(shmSegmentSize) : LocalhostTransports.SHM,
                                                     localhost ? LocalhostTransports.UDPV4 : ROS2SharedMemoryConfiguration.UDPV4};
      }
      else if (whitelist && shmSegmentSize > 0)
      {
         // The whitelist's UDPv4 transport plus jros2's shared-memory transport
         transports = transports.clone();
         for (int i = 0; i < transports.length; ++i)
         {
            if ("SHM".equals(transports[i].getType()))
               transports[i] = ROS2SharedMemoryConfiguration.sharedMemoryDescriptor(shmSegmentSize);
         }
      }
      if (transports == null)
      {
         return null; // Fast-DDS builtin transports (SHM + UDPv4)
      }

      ThreadSettingsType receptionThreads = threadSettings == null ? null : threadSettings.toThreadSettingsType(ROS2ThreadSettings.Kind.RECEPTION);
      TransportDescriptorType[] result = new TransportDescriptorType[transports.length];
      for (int i = 0; i < transports.length; ++i)
      {
         TransportDescriptorType transport = transports[i];
         Long peersRange = null;
         if (localhost && "UDPv4".equals(transport.getType()) && transport.getMaxInitialPeersRange() == null)
         {
            peersRange = ROS2DiscoveryConfiguration.LOCALHOST_MAX_INITIAL_PEERS_RANGE;
         }
         result[i] = TransportDescriptorTypeTools.deriveVariant(transport, peersRange, receptionThreads);
      }
      return result;
   }

   /**
    * The transports rmw_fastrtps uses for the LOCALHOST discovery range, created once per process.
    */
   static final class LocalhostTransports
   {
      static final TransportDescriptorType SHM = descriptor("jros2_localhost_shm", "SHM", null);
      static final TransportDescriptorType UDPV4 = descriptor("jros2_localhost_udpv4", "UDPv4", ROS2DiscoveryConfiguration.LOCALHOST_MAX_INITIAL_PEERS_RANGE);

      private static TransportDescriptorType descriptor(String transportId, String type, Long maxInitialPeersRange)
      {
         TransportDescriptorType descriptor = new TransportDescriptorType();
         descriptor.setTransportId(transportId);
         descriptor.setType(type);
         descriptor.setMaxInitialPeersRange(maxInitialPeersRange);
         return descriptor;
      }
   }

   /**
    * @param addresses IPv4 addresses; a null entry is an empty UDPv4 locator ({@code <udpv4/>}, rmw's {@code Locator_t{}})
    */
   /**
    * Network buffers preallocated per send buffer (Fast-DDS {@code send_buffers.network_buffers_config.initial}).
    * <p>
    * Fast-DDS gathers the parts of an RTPS message (submessage headers, payloads, padding) in a vector of network buffers
    * owned by each send buffer of the participant's pool, and flushes the message when the vector holds more than 61 (62
    * without statistics) entries; one more submessage adds at most 3 entries and the statistics submessage 1, so the vector
    * never needs more than 66. Its default capacity is 16 and grows by 16, separately for every pooled send buffer, the
    * first time that buffer serves a message with more parts than before: with fragmented large messages that happens at
    * unpredictable times in steady state (a malloc on the sending thread, e.g. the flow controller's thread of a 1 MB
    * image). Preallocating 80 entries (about 1.3 kB per send buffer, plus the same number of payload references) removes
    * that growth.
    */
   static final long NETWORK_BUFFERS_PER_SEND_BUFFER = 80L;

   private static RtpsParticipantAllocationAttributesType allocationAttributes()
   {
      RtpsParticipantAllocationAttributesType allocation = new RtpsParticipantAllocationAttributesType();
      RtpsParticipantAllocationAttributesType.SendBuffers sendBuffers = new RtpsParticipantAllocationAttributesType.SendBuffers();
      AllocationConfigType networkBuffers = new AllocationConfigType();
      networkBuffers.setInitial(NETWORK_BUFFERS_PER_SEND_BUFFER);
      networkBuffers.setIncrement(16L);
      sendBuffers.setNetworkBuffersConfig(networkBuffers);
      allocation.setSendBuffers(sendBuffers);
      return allocation;
   }

   private static LocatorListType locators(String[] addresses)
   {
      LocatorListType locatorList = new LocatorListType();
      for (String address : addresses)
      {
         LocatorListType.Locator locator = new LocatorListType.Locator();
         Udpv4LocatorType udpv4 = new Udpv4LocatorType();
         udpv4.setAddress(address);
         locator.getUdpv4().add(udpv4);
         locatorList.getLocator().add(locator);
      }
      return locatorList;
   }

   /**
    * Check if SHM is usable on Windows (sometimes the SHM directory can lose write permissions)
    */
   private static void checkSHMAvailabilityWindows(ParticipantConfiguration configuration)
   {
      if (System.getProperty("os.name", "").startsWith("Windows") && !TransportDescriptorTypeTools.SHM_TRANSPORT_AVAILABLE_ON_WINDOWS)
      {
         boolean shmEnabled = configuration.usesBuiltinTransports();
         if (!shmEnabled)
         {
            for (TransportDescriptorType transport : configuration.transports)
            {
               if ("SHM".equals(transport.getType()))
               {
                  shmEnabled = true;
                  break;
               }
            }
         }

         if (shmEnabled)
         {
            jros2.getLogger().severe("Shared Memory Transport (SHM) is not available. Could not write to: C:\\ProgramData\\eprosima\\fastdds_interprocess");
            jros2.getLogger().severe("Try restarting the process after deleting the directory.");
         }
      }
   }
}
