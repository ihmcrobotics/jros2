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
import org.junit.jupiter.api.Timeout;
import us.ihmc.fastddsjava.fastddsjavaException;
import us.ihmc.fastddsjava.profiles.NativeProfileProbe;
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.TransportDescriptorTypeTools;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;
import us.ihmc.fastddsjava.profiles.gen.ThreadSettingsType;
import us.ihmc.fastddsjava.profiles.gen.TopicProfileType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every participant configuration {@link ParticipantProfiles} produces, and the endpoint profiles {@link QoSTools} produces,
 * loaded through the bundled Fast-DDS natives and used to create participants, topics, writers and readers.
 * Creates DDS participants: run under the shared DDS lock in this workstream's domain.
 */
public class ParticipantProfilesNativeTest
{
   private static final int DOMAIN_ID = NativeProfileProbe.testDomainId();

   static
   {
      jros2.load();
   }

   private static ParticipantProfiles.Environment environment(Function<jros2Settings.Key, String> settings, String[] whitelist, boolean android, boolean security)
   {
      return new ParticipantProfiles.Environment(settings, whitelist, () -> security, android, InetAddress::getAllByName);
   }

   /**
    * Registers the transports, then loads participant + topic (+ endpoints) and creates every entity.
    */
   static void loadAndCreate(ParticipantProfiles.ParticipantConfiguration configuration, ROS2QoSProfile endpointQoS) throws fastddsjavaException
   {
      TransportDescriptorTypeTools.ensureRegistered(configuration.transports);

      String suffix = NativeProfileProbe.uniqueSuffix();
      ProfilesXML profilesXML = new ProfilesXML();
      profilesXML.addParticipantProfile(configuration.profile);
      TopicProfileType topic = new TopicProfileType();
      topic.setProfileName("t" + suffix);
      profilesXML.addTopicProfile(topic);
      String writerName = null;
      String readerName = null;
      if (endpointQoS != null)
      {
         writerName = "w" + suffix;
         PublisherProfileType writer = new PublisherProfileType();
         writer.setProfileName(writerName);
         QoSTools.translateQoS(endpointQoS, writer, QoSToolsTest.STRING_TYPE_HASH);
         profilesXML.addPublisherProfile(writer);
         readerName = "r" + suffix;
         SubscriberProfileType reader = new SubscriberProfileType();
         reader.setProfileName(readerName);
         QoSTools.translateQoS(endpointQoS, reader, QoSToolsTest.STRING_TYPE_HASH);
         profilesXML.addSubscriberProfile(reader);
      }
      NativeProfileProbe.loadAndCreate(profilesXML.marshall(), configuration.profile.getProfileName(), "t" + suffix, writerName, readerName);
   }

   private static ParticipantProfiles.ParticipantConfiguration build(ROS2NodeOptions options, ParticipantProfiles.Environment environment)
   {
      return ParticipantProfiles.build("p" + NativeProfileProbe.uniqueSuffix(), "g2_native", DOMAIN_ID, options, environment);
   }

   private static ParticipantProfiles.ParticipantConfiguration build(ROS2NodeOptions options, Object... keyValues)
   {
      return build(options, environment(ROS2DiscoveryConfigurationTest.settings(keyValues), new String[0], false, false));
   }

   @Test
   @Timeout(60)
   public void testEveryDiscoveryRange() throws fastddsjavaException
   {
      for (ROS2DiscoveryRange range : ROS2DiscoveryRange.values())
      {
         loadAndCreate(build(ROS2NodeOptions.defaults().discoveryRange(range)), ROS2QoSProfile.DEFAULT);
      }
      // Static peers: LOCALHOST (peers + 127.0.0.1) and SUBNET (peers + 239.255.0.1); "localhost" is resolved in Java
      loadAndCreate(build(ROS2NodeOptions.defaults().discoveryRange(ROS2DiscoveryRange.LOCALHOST).staticPeers("localhost", "127.0.0.2")), null);
      loadAndCreate(build(ROS2NodeOptions.defaults().discoveryRange(ROS2DiscoveryRange.SUBNET).staticPeers("localhost")), null);
      // Shared-memory segment sizes: Fast-DDS's default (builtin transports), the smallest and a large one, LOCALHOST too
      loadAndCreate(build(ROS2NodeOptions.defaults().shmSegmentSize(0)), ROS2QoSProfile.BEST_EFFORT);
      loadAndCreate(build(ROS2NodeOptions.defaults().shmSegmentSize(0).discoveryRange(ROS2DiscoveryRange.LOCALHOST)), null);
      loadAndCreate(build(ROS2NodeOptions.defaults().shmSegmentSize(ROS2NodeOptions.MIN_SHM_SEGMENT_SIZE)), null);
      loadAndCreate(build(ROS2NodeOptions.defaults().shmSegmentSize(16L << 20).discoveryRange(ROS2DiscoveryRange.LOCALHOST)), null);
      loadAndCreate(build(null, jros2Settings.Key.SHM_SEGMENT_SIZE, "8M"), null);
      // Environment variants
      loadAndCreate(build(null, jros2Settings.Key.LOCALHOST_ONLY, "1"), null);
      loadAndCreate(build(null, jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE, "OFF", jros2Settings.Key.STATIC_PEERS, "10.0.0.1"), null);
   }

   @Test
   @Timeout(60)
   public void testEnclavesAndAndroidAndWhitelistTransports() throws fastddsjavaException
   {
      loadAndCreate(build(ROS2NodeOptions.defaults().enclave("/talker_listener/talker")), null);

      // Android: UDPv4 only, also for LOCALHOST
      ParticipantProfiles.Environment android = environment(ROS2DiscoveryConfigurationTest.settings(), new String[0], true, false);
      loadAndCreate(build(null, android), ROS2QoSProfile.SENSOR_DATA);
      loadAndCreate(build(ROS2NodeOptions.defaults().discoveryRange(ROS2DiscoveryRange.LOCALHOST), android), null);

      // Interface whitelist (loopback only), SUBNET and LOCALHOST
      ParticipantProfiles.Environment whitelisted = environment(ROS2DiscoveryConfigurationTest.settings(), new String[] {"127.0.0.1"}, false, false);
      loadAndCreate(build(null, whitelisted), null);
      loadAndCreate(build(ROS2NodeOptions.defaults().discoveryRange(ROS2DiscoveryRange.LOCALHOST), whitelisted), null);
      loadAndCreate(build(ROS2NodeOptions.defaults().discoveryRange(ROS2DiscoveryRange.OFF), whitelisted), null);

      // Custom transports (SHM only with OFF: the UDPv4 metatraffic locator is simply unused)
      TransportDescriptorType shm = TransportDescriptorTypeTools.createSHMDescriptor();
      loadAndCreate(build(ROS2NodeOptions.defaults().transports(shm).discoveryRange(ROS2DiscoveryRange.OFF)), null);
      loadAndCreate(build(ROS2NodeOptions.defaults().transports(TransportDescriptorTypeTools.createUDPv4Descriptor()).discoveryRange(ROS2DiscoveryRange.LOCALHOST)),
                    null);
   }

   @Test
   @Timeout(60)
   public void testInterfaceWhitelistChangeBetweenParticipants() throws fastddsjavaException
   {
      // Regression (whitelist cache): after the interface whitelist changes, the next participant references a new UDPv4
      // descriptor, which must be registered with Fast-DDS too; otherwise its profile fails to load ("Transport Node not found").
      String[][] whitelists = {{"127.0.0.1"}, {"lo"}, {"127.0.0.1"}};
      String previousUdpId = null;
      for (String[] whitelist : whitelists)
      {
         for (ROS2DiscoveryRange range : new ROS2DiscoveryRange[] {ROS2DiscoveryRange.SUBNET, ROS2DiscoveryRange.LOCALHOST})
         {
            ParticipantProfiles.ParticipantConfiguration configuration = build(ROS2NodeOptions.defaults().discoveryRange(range),
                                                                               environment(ROS2DiscoveryConfigurationTest.settings(), whitelist, false, false));
            loadAndCreate(configuration, ROS2QoSProfile.DEFAULT);
            for (TransportDescriptorType transport : configuration.transports)
            {
               assertTrue(TransportDescriptorTypeTools.isRegistered(transport.getTransportId()), transport.getTransportId());
            }
            if (range == ROS2DiscoveryRange.SUBNET)
            {
               String udpId = configuration.transports[0].getTransportId();
               if (previousUdpId != null)
               {
                  assertNotEquals(previousUdpId, udpId, "a changed whitelist must give a new UDPv4 descriptor");
               }
               previousUdpId = udpId;
            }
         }
      }
   }

   @Test
   @Timeout(60)
   public void testThreadSettings() throws fastddsjavaException
   {
      ROS2ThreadSettings threads = new ROS2ThreadSettings().schedulingPolicy(0)
                                                           .priority(0)
                                                           .stackSizeBytes(ROS2ThreadSettings.MIN_STACK_SIZE_BYTES)
                                                           .forThreads(ROS2ThreadSettings.Kind.RECEPTION, new ROS2ThreadSettings().stackSizeBytes(1048576))
                                                           .forThreads(ROS2ThreadSettings.Kind.TIMED_EVENTS, new ROS2ThreadSettings().affinityMask(1L));
      // jros2's default transports (default_reception_threads on derived descriptors)
      loadAndCreate(build(ROS2NodeOptions.defaults().threadSettings(threads)), ROS2QoSProfile.DEFAULT);
      // Builtin transports (explicit <builtinTransports> after the reception threads)
      loadAndCreate(build(ROS2NodeOptions.defaults().threadSettings(threads).shmSegmentSize(0)), ROS2QoSProfile.DEFAULT);
      // User transports (default_reception_threads on derived descriptors)
      loadAndCreate(build(ROS2NodeOptions.defaults().threadSettings(threads).discoveryRange(ROS2DiscoveryRange.LOCALHOST)), ROS2QoSProfile.DEFAULT);
      // Default stack size (-1) and a priority that the OS may refuse (only logged by Fast-DDS)
      loadAndCreate(build(ROS2NodeOptions.defaults().threadSettings(new ROS2ThreadSettings().stackSizeBytes(-1).priority(-1))), null);

      // A custom descriptor whose thread stack would make Fast-DDS abort the JVM is refused before anything reaches the natives
      TransportDescriptorType custom = new TransportDescriptorType();
      custom.setTransportId("g2_bad_stack" + NativeProfileProbe.uniqueSuffix());
      custom.setType("UDPv4");
      ThreadSettingsType tinyStack = new ThreadSettingsType();
      tinyStack.setStackSize(16384);
      custom.setDefaultReceptionThreads(tinyStack);
      assertThrows(IllegalArgumentException.class, () -> TransportDescriptorTypeTools.ensureRegistered(custom));
      assertFalse(TransportDescriptorTypeTools.isRegistered(custom.getTransportId()));
      assertThrows(IllegalArgumentException.class, () -> new ROS2Node("g2_bad_stack", ROS2NodeOptions.defaults().transports(custom)).close());
   }

   @Test
   @Timeout(120)
   public void testConcurrentParticipantCreationRegistersSharedTransportsOnce() throws Exception
   {
      // Nodes created concurrently share the LOCALHOST descriptors and their derived variants. Each descriptor must reach
      // Fast-DDS before any participant profile references it, and only once (a duplicate transport ID is an error).
      // A stack size unique to this run makes the reception-thread variants new, so the registration really races.
      int stackSize = 524288 + ROS2ThreadSettings.STACK_SIZE_ALIGNMENT_BYTES * (int) (System.nanoTime() % 256);
      ROS2ThreadSettings threads = new ROS2ThreadSettings().forThreads(ROS2ThreadSettings.Kind.RECEPTION, new ROS2ThreadSettings().stackSizeBytes(stackSize));
      int participants = 6;
      ExecutorService executor = Executors.newFixedThreadPool(participants);
      try
      {
         CountDownLatch start = new CountDownLatch(1);
         List<Future<String[]>> futures = new ArrayList<>();
         for (int i = 0; i < participants; ++i)
         {
            futures.add(executor.submit(() ->
            {
               start.await();
               ParticipantProfiles.ParticipantConfiguration configuration = build(ROS2NodeOptions.defaults()
                                                                                                .discoveryRange(ROS2DiscoveryRange.LOCALHOST)
                                                                                                .threadSettings(threads));
               loadAndCreate(configuration, ROS2QoSProfile.DEFAULT);
               return new String[] {configuration.transports[0].getTransportId(), configuration.transports[1].getTransportId()};
            }));
         }
         start.countDown();

         String[] first = null;
         for (Future<String[]> future : futures)
         {
            String[] transportIds = future.get(90, TimeUnit.SECONDS);
            if (first == null)
            {
               first = transportIds;
            }
            assertArrayEquals(first, transportIds, "concurrent nodes must share the same derived transports");
         }
         for (String transportId : first)
         {
            assertTrue(TransportDescriptorTypeTools.isRegistered(transportId), transportId);
         }
      }
      finally
      {
         executor.shutdownNow();
      }
   }

   @Test
   @Timeout(60)
   public void testSecurityPropertiesLoad() throws fastddsjavaException
   {
      // The research keystore; with SECURITY=OFF natives the properties are parsed and ignored, with a security build they apply
      ParticipantProfiles.Environment secure = environment(ROS2DiscoveryConfigurationTest.settings(jros2Settings.Key.SECURITY_ENABLE,
                                                                                                    "true",
                                                                                                    jros2Settings.Key.SECURITY_STRATEGY,
                                                                                                    "Enforce",
                                                                                                    jros2Settings.Key.SECURITY_KEYSTORE,
                                                                                                    SROS2Test.KEYSTORE),
                                                           new String[0],
                                                           false,
                                                           true);
      org.junit.jupiter.api.Assumptions.assumeTrue(new java.io.File(SROS2Test.KEYSTORE).isDirectory(), "research keystore not available");
      loadAndCreate(build(ROS2NodeOptions.defaults().enclave("/talker_listener/talker").threadSettings(new ROS2ThreadSettings().priority(0)), secure), null);
   }

   @Test
   @Timeout(60)
   public void testEndpointQoSMappingsLoad() throws fastddsjavaException
   {
      ParticipantProfiles.ParticipantConfiguration participant = build(null);
      ROS2QoSProfile[] presets = {ROS2QoSProfile.SENSOR_DATA,
                                  ROS2QoSProfile.PARAMETERS,
                                  ROS2QoSProfile.SERVICES_DEFAULT,
                                  ROS2QoSProfile.PARAMETER_EVENTS,
                                  ROS2QoSProfile.SYSTEM_DEFAULT,
                                  ROS2QoSProfile.ROSOUT,
                                  ROS2QoSProfile.CLOCK,
                                  ROS2QoSProfile.BEST_EFFORT};
      // One participant profile per load (Fast-DDS keeps the first profile of a name)
      for (ROS2QoSProfile preset : presets)
      {
         loadAndCreate(build(null), preset);
      }

      ROS2QoSProfile edge = new ROS2QoSProfile();
      edge.history(ROS2QoSProfile.History.KEEP_ALL);
      edge.depth(0);
      edge.deadline(Duration.ofSeconds(5_000_000_000L));
      edge.lifespan(ROS2QoSProfile.DURATION_INFINITE);
      edge.liveliness(ROS2QoSProfile.Liveliness.MANUAL_BY_TOPIC);
      edge.leaseDuration(Duration.ofSeconds(Integer.MAX_VALUE - 1L, 999_999_999));
      edge.maxBlockingTime(ROS2QoSProfile.DURATION_INFINITE);
      loadAndCreate(build(null), edge);

      ROS2QoSProfile finite = new ROS2QoSProfile();
      finite.durability(ROS2QoSProfile.Durability.TRANSIENT_LOCAL);
      finite.depth(5000);
      finite.deadline(Duration.ofMillis(100));
      finite.lifespan(Duration.ofMillis(1500));
      finite.liveliness(ROS2QoSProfile.Liveliness.AUTOMATIC);
      finite.leaseDuration(Duration.ofSeconds(3));
      finite.maxBlockingTime(Duration.ofMillis(5));
      loadAndCreate(build(null), finite);

      // The largest depth a profile accepts (rmw: anything up to INT32_MAX) must not make Fast-DDS preallocate the history
      ROS2QoSProfile hugeDepth = new ROS2QoSProfile();
      hugeDepth.depth(Integer.MAX_VALUE);
      loadAndCreate(build(null), hugeDepth);

      ROS2QoSProfile historyDepthOnly = new ROS2QoSProfile(ROS2QoSProfile.SYSTEM_DEFAULT);
      historyDepthOnly.depth(25);
      historyDepthOnly.maxBlockingTime(Duration.ofSeconds(1));
      loadAndCreate(participant, historyDepthOnly);
   }
}
