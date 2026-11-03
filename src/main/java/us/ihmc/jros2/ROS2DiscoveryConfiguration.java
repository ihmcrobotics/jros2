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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * The effective automatic discovery range and static peers of a participant, resolved with the exact rcl (Jazzy) rules:
 * <ul>
 *    <li>{@code ROS_LOCALHOST_ONLY}: empty or unset is the default; a value starting with "1" enables it (rcl uses a prefix
 *    test), anything else disables it. When enabled, the range is LOCALHOST and every static peer is ignored, whatever else
 *    is configured (also programmatically).</li>
 *    <li>The range comes from {@link ROS2NodeOptions#discoveryRange} if set, otherwise from
 *    {@code ROS_AUTOMATIC_DISCOVERY_RANGE}: empty or unset is SUBNET; "OFF", "LOCALHOST", "SUBNET" and "SYSTEM_DEFAULT" (exact,
 *    case-sensitive) select that range; any other value logs a warning and selects LOCALHOST.</li>
 *    <li>Static peers come from {@link ROS2NodeOptions#staticPeers} if any are given, otherwise (and only if the range is not
 *    OFF) from {@code ROS_STATIC_PEERS}, split at ';' with empty tokens dropped and no whitespace trimming. Entries longer than
 *    255 characters are skipped with rcl's warning. (rcl leaves an empty slot for a skipped entry, which rmw_fastrtps then
 *    fails to resolve; jros2 follows rcl's documented intent and simply drops the entry. No valid host name is that long.)</li>
 *    <li>Static peers are ignored with a warning when the range is OFF, and are not used with SYSTEM_DEFAULT (rmw_fastrtps only
 *    applies them for LOCALHOST and SUBNET).</li>
 * </ul>
 */
final class ROS2DiscoveryConfiguration
{
   /** rmw {@code RMW_DISCOVERY_OPTIONS_STATIC_PEERS_MAX_LENGTH - 1}. */
   static final int STATIC_PEER_MAX_LENGTH = 255;
   /** maxInitialPeersRange rmw_fastrtps uses for the UDPv4 transport of a LOCALHOST participant. */
   static final long LOCALHOST_MAX_INITIAL_PEERS_RANGE = 32L;
   /** The default Fast-DDS multicast discovery address, appended to SUBNET initial peers so multicast announcements continue. */
   static final String MULTICAST_DISCOVERY_ADDRESS = "239.255.0.1";
   /** The initial peer every LOCALHOST participant announces itself to. */
   static final String LOCALHOST_ADDRESS = "127.0.0.1";

   /** rcl {@code rmw_localhost_only_t}. */
   enum LocalhostOnly
   {
      DEFAULT, ENABLED, DISABLED
   }

   /** Resolves a host name; {@link InetAddress#getAllByName} in production. */
   interface PeerResolver
   {
      InetAddress[] resolve(String host) throws UnknownHostException;
   }

   private final ROS2DiscoveryRange range;
   private final List<String> staticPeers;
   private final String rangeSource;
   private final String staticPeersSource;
   private final List<String> warnings;

   private ROS2DiscoveryConfiguration(ROS2DiscoveryRange range, List<String> staticPeers, String rangeSource, String staticPeersSource, List<String> warnings)
   {
      this.range = range;
      this.staticPeers = Collections.unmodifiableList(staticPeers);
      this.rangeSource = rangeSource;
      this.staticPeersSource = staticPeersSource;
      this.warnings = Collections.unmodifiableList(warnings);
   }

   /** @return the effective discovery range */
   ROS2DiscoveryRange getRange()
   {
      return range;
   }

   /** @return the static peers that apply (unresolved host names or addresses); empty for OFF and SYSTEM_DEFAULT */
   List<String> getStaticPeers()
   {
      return staticPeers;
   }

   /** @return where the range came from, e.g. "ROS_AUTOMATIC_DISCOVERY_RANGE" */
   String getRangeSource()
   {
      return rangeSource;
   }

   /** @return where the static peers came from, or null if there are none */
   String getStaticPeersSource()
   {
      return staticPeersSource;
   }

   /** @return warnings produced while resolving (rcl logs the same situations) */
   List<String> getWarnings()
   {
      return warnings;
   }

   /**
    * rcl {@code rcl_get_localhost_only} (Jazzy).
    *
    * @param value the raw {@code ROS_LOCALHOST_ONLY} value, or null if unset
    */
   static LocalhostOnly parseLocalhostOnly(String value)
   {
      if (value == null || value.isEmpty())
      {
         return LocalhostOnly.DEFAULT;
      }
      return value.startsWith("1") ? LocalhostOnly.ENABLED : LocalhostOnly.DISABLED;
   }

   /**
    * rcl {@code rcl_get_automatic_discovery_range}.
    *
    * @param value    the raw {@code ROS_AUTOMATIC_DISCOVERY_RANGE} value, or null if unset
    * @param warnings receives a warning for an invalid value
    */
   static ROS2DiscoveryRange parseDiscoveryRange(String value, List<String> warnings)
   {
      if (value == null || value.isEmpty())
      {
         return ROS2DiscoveryRange.SUBNET;
      }
      switch (value)
      {
         case "OFF":
            return ROS2DiscoveryRange.OFF;
         case "LOCALHOST":
            return ROS2DiscoveryRange.LOCALHOST;
         case "SUBNET":
            return ROS2DiscoveryRange.SUBNET;
         case "SYSTEM_DEFAULT":
            return ROS2DiscoveryRange.SYSTEM_DEFAULT;
         default:
            warnings.add("Invalid value '" + value + "' specified for 'ROS_AUTOMATIC_DISCOVERY_RANGE', assuming localhost only");
            return ROS2DiscoveryRange.LOCALHOST;
      }
   }

   /**
    * rcl {@code rcl_get_discovery_static_peers}: split at ';', drop empty tokens, keep whitespace, skip entries longer than
    * {@value #STATIC_PEER_MAX_LENGTH} characters with a warning.
    *
    * @param value    the raw {@code ROS_STATIC_PEERS} value, or null if unset
    * @param warnings receives a warning for every skipped entry
    */
   static List<String> parseStaticPeers(String value, List<String> warnings)
   {
      List<String> peers = new ArrayList<>();
      if (value == null || value.isEmpty())
      {
         return peers;
      }
      int start = 0;
      while (start <= value.length())
      {
         int end = value.indexOf(';', start);
         if (end < 0)
         {
            end = value.length();
         }
         String token = value.substring(start, end);
         if (!token.isEmpty())
         {
            if (token.length() > STATIC_PEER_MAX_LENGTH)
            {
               warnings.add("Static peer " + token + " specified to 'ROS_STATIC_PEERS' is too long (maximum of " + STATIC_PEER_MAX_LENGTH + "); skipping");
            }
            else
            {
               peers.add(token);
            }
         }
         start = end + 1;
      }
      return peers;
   }

   /**
    * Resolves the effective discovery configuration with the rcl precedence rules.
    *
    * @param optionRange the programmatic range ({@link ROS2NodeOptions#getDiscoveryRange()}), or null
    * @param optionPeers the programmatic static peers ({@link ROS2NodeOptions#getStaticPeers()}), or null/empty
    * @param settings    raw settings lookup (system properties, environment variables, jros2.properties)
    */
   static ROS2DiscoveryConfiguration resolve(ROS2DiscoveryRange optionRange, String[] optionPeers, Function<jros2Settings.Key, String> settings)
   {
      List<String> warnings = new ArrayList<>();

      LocalhostOnly localhostOnly = parseLocalhostOnly(settings.apply(jros2Settings.Key.LOCALHOST_ONLY));
      if (localhostOnly == LocalhostOnly.ENABLED)
      {
         warnings.add("ROS_LOCALHOST_ONLY is deprecated but still honored if it is enabled. Use ROS_AUTOMATIC_DISCOVERY_RANGE and ROS_STATIC_PEERS instead.");
         boolean somethingIgnored = optionRange != null || (optionPeers != null && optionPeers.length > 0) || !isEmpty(settings.apply(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE))
                                    || !isEmpty(settings.apply(jros2Settings.Key.STATIC_PEERS));
         if (somethingIgnored)
         {
            warnings.add("'localhost_only' is enabled, 'automatic_discovery_range' and 'static_peers' will be ignored.");
         }
         return new ROS2DiscoveryConfiguration(ROS2DiscoveryRange.LOCALHOST, new ArrayList<>(), "ROS_LOCALHOST_ONLY", null, warnings);
      }

      ROS2DiscoveryRange range;
      String rangeSource;
      if (optionRange != null)
      {
         range = optionRange;
         rangeSource = "ROS2NodeOptions";
      }
      else
      {
         String rawRange = settings.apply(jros2Settings.Key.AUTOMATIC_DISCOVERY_RANGE);
         range = parseDiscoveryRange(rawRange, warnings);
         rangeSource = isEmpty(rawRange) ? "default" : "ROS_AUTOMATIC_DISCOVERY_RANGE";
      }

      List<String> peers = new ArrayList<>();
      String peersSource = null;
      if (optionPeers != null && optionPeers.length > 0)
      {
         for (String peer : optionPeers)
         {
            if (peer == null || peer.isEmpty())
            {
               throw new IllegalArgumentException("Static peers must not be null or empty");
            }
            if (peer.length() > STATIC_PEER_MAX_LENGTH)
            {
               throw new IllegalArgumentException("Static peer '" + peer + "' is too long (maximum of " + STATIC_PEER_MAX_LENGTH + " characters)");
            }
            peers.add(peer);
         }
         peersSource = "ROS2NodeOptions";
      }
      else if (range != ROS2DiscoveryRange.OFF)
      {
         peers = parseStaticPeers(settings.apply(jros2Settings.Key.STATIC_PEERS), warnings);
         peersSource = peers.isEmpty() ? null : "ROS_STATIC_PEERS";
      }

      if (!peers.isEmpty() && range == ROS2DiscoveryRange.OFF)
      {
         warnings.add("Note: ROS_AUTOMATIC_DISCOVERY_RANGE is set to OFF, but found static peers in ROS_STATIC_PEERS. ROS_STATIC_PEERS will be ignored.");
         peers = new ArrayList<>();
         peersSource = null;
      }
      else if (!peers.isEmpty() && range == ROS2DiscoveryRange.SYSTEM_DEFAULT)
      {
         warnings.add("Static peers are not used with the SYSTEM_DEFAULT discovery range and will be ignored.");
         peers = new ArrayList<>();
         peersSource = null;
      }

      return new ROS2DiscoveryConfiguration(range, peers, rangeSource, peersSource, warnings);
   }

   /**
    * Resolves the static peers to IPv4 addresses like rmw_fastrtps: the first IPv4 address of each name is used, and a name
    * without one is an error.
    *
    * @param resolver the DNS resolver
    * @return the dotted IPv4 addresses, in order
    * @throws IllegalArgumentException if a peer cannot be resolved to an IPv4 address
    */
   List<String> resolveStaticPeers(PeerResolver resolver)
   {
      List<String> addresses = new ArrayList<>(staticPeers.size());
      for (String peer : staticPeers)
      {
         InetAddress[] candidates;
         try
         {
            // InetAddress resolves an empty name to the loopback address; rmw fails to resolve it
            candidates = peer.isEmpty() ? new InetAddress[0] : resolver.resolve(peer);
         }
         catch (UnknownHostException | SecurityException e)
         {
            throw new IllegalArgumentException("Unable to resolve static peer '" + peer + "' (" + (staticPeersSource == null ? "static peers" : staticPeersSource) + ")", e);
         }

         String address = null;
         if (candidates != null)
         {
            for (InetAddress candidate : candidates)
            {
               if (candidate instanceof Inet4Address)
               {
                  address = candidate.getHostAddress();
                  break;
               }
            }
         }
         if (address == null)
         {
            throw new IllegalArgumentException("Unable to resolve static peer '" + peer + "' to an IPv4 address");
         }
         addresses.add(address);
      }
      return addresses;
   }

   private static boolean isEmpty(String value)
   {
      return value == null || value.isEmpty();
   }
}
