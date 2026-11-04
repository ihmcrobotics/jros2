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

import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType.InterfaceWhiteList;

import java.util.List;

/**
 * Utility class for printing ROS2 node configuration information.
 * <p>
 * This class generates formatted output describing the configuration of a ROS2 node: its domain ID, transports and
 * interface whitelists, discovery range and static peers, enclave and SROS2 security state, thread settings and intraprocess
 * delivery. It runs once per participant (never on a publish or subscribe path) and reuses thread-local
 * {@link StringBuilder}s.
 */
final class ROS2NodePrintout
{
   private static final ThreadLocal<StringBuilder> PRINTOUT = ThreadLocal.withInitial(() -> new StringBuilder(1024));
   private static final ThreadLocal<StringBuilder> WHITELIST = ThreadLocal.withInitial(() -> new StringBuilder(256));

   static
   {
      jros2.load();
   }

   private ROS2NodePrintout()
   {
   }

   /**
    * Prints the configuration details of a ROS2 node to the logger.
    * Can be disabled by setting the system property "jros2.node.printout" to "false".
    *
    * @param nodeClass     The class of the ROS2 node being created
    * @param configuration The participant configuration built by {@link ParticipantProfiles}
    */
   static void print(Class<? extends ROS2Node> nodeClass, ParticipantProfiles.ParticipantConfiguration configuration)
   {
      // Check if printout is disabled via system property
      if ("false".equalsIgnoreCase(System.getProperty("jros2.node.printout")))
      {
         return;
      }

      jros2.getLogger().info(format(nodeClass, configuration).toString());
   }

   /**
    * Formats the printout into a thread-local builder (valid until the next call on the same thread).
    */
   static StringBuilder format(Class<? extends ROS2Node> nodeClass, ParticipantProfiles.ParticipantConfiguration configuration)
   {
      ParticipantProfileType participantProfile = configuration.profile;
      StringBuilder printout = PRINTOUT.get();
      printout.setLength(0);
      printout.append('\n').append('\t');

      // Get the name
      String nodeName = participantProfile.getRtps().getName();
      printout.append("Created ").append(nodeClass.getSimpleName()).append(": ").append(nodeName);

      // Get the domain id and its source
      int domainId = participantProfile.getDomainId();
      String domainIdSource = "constructor";
      jros2Settings[] sources = jros2.get().getSettingsSources();
      for (int i = 0; i < sources.length; ++i)
      {
         if (sources[i].hasROSDomainId() && sources[i].rosDomainId() == domainId)
         {
            domainIdSource = sources[i].getSourceName();
            break;
         }
      }
      newLine(printout).append("DomainID: ").append(domainId).append(" (Specified by: ").append(domainIdSource).append(')');

      appendTransports(printout, configuration);
      appendDiscovery(printout, configuration);

      newLine(printout).append("Enclave: ").append(configuration.enclave);
      appendSecurity(printout, configuration.security);

      if (configuration.threadSettings != null)
      {
         newLine(printout).append("Thread settings: ").append(configuration.threadSettings);
      }

      if (jros2.get().intraprocessDelivery())
      {
         newLine(printout).append("Intraprocess delivery enabled - https://github.com/ihmcrobotics/jros2/wiki/Intraprocess-Delivery");
      }

      return printout;
   }

   private static StringBuilder newLine(StringBuilder printout)
   {
      return printout.append('\n').append('\t');
   }

   private static void appendTransports(StringBuilder printout, ParticipantProfiles.ParticipantConfiguration configuration)
   {
      TransportDescriptorType[] transportDescriptors = configuration.transports;
      if (transportDescriptors == null) // Default builtin transports are UDPv4 and SHM
      {
         newLine(printout).append("Using builtin transports: UDPv4, SHM (segment ").append(configuration.sharedMemory).append(", Specified by: ")
                          .append(configuration.sharedMemory.getSource()).append(')');
         return;
      }

      newLine(printout).append(configuration.customTransports ? "Using custom transports:" : "Using transports:");
      for (int i = 0; i < transportDescriptors.length; ++i)
      {
         // Get the transport type (e.g. UDPv4, TCPv6, SHM, etc.)
         String type = transportDescriptors[i].getType();

         // See if an interface whitelist is specified for this transport
         InterfaceWhiteList interfaceWhiteList = transportDescriptors[i].getInterfaceWhiteList();
         if (interfaceWhiteList == null || interfaceWhiteList.getAddressOrInterface().isEmpty())
         {
            // SHM uses shared memory, not network interfaces
            if ("SHM".equals(type))
            {
               newLine(printout).append('\t').append(type).append(": local only, segment ");
               Long segmentSize = transportDescriptors[i].getSegmentSize();
               if (segmentSize == null || segmentSize == 0)
                  printout.append("Fast-DDS default (512 KiB)");
               else
                  printout.append(ROS2SharedMemoryConfiguration.mebibytes(segmentSize));
               if (!configuration.customTransports)
                  printout.append(" (Specified by: ").append(configuration.sharedMemory.getSource()).append(')');
            }
            else
            {
               newLine(printout).append('\t').append(type).append(": on all interfaces");
            }
         }
         else // Whitelist specified
         {
            List<Object> whitelistElements = interfaceWhiteList.getAddressOrInterface();
            StringBuilder whitelistString = WHITELIST.get();
            whitelistString.setLength(0);

            for (int j = 0; j < whitelistElements.size(); ++j)
            {
               if (j > 0)
               {
                  whitelistString.append(", ");
               }

               // The value can either be a List<String> or String
               Object value = whitelistElements.get(j);
               if (value instanceof List<?> list)
               {
                  for (int k = 0; k < list.size(); ++k)
                  {
                     if (k > 0)
                     {
                        whitelistString.append(", ");
                     }
                     whitelistString.append(list.get(k));
                  }
               }
               else if (value instanceof String string)
               {
                  whitelistString.append(string);
               }
            }

            newLine(printout).append('\t').append(type).append(": on ").append(whitelistString);
         }
      }
   }

   private static void appendDiscovery(StringBuilder printout, ParticipantProfiles.ParticipantConfiguration configuration)
   {
      ROS2DiscoveryConfiguration discovery = configuration.discovery;
      newLine(printout).append("Discovery range: ").append(discovery.getRange()).append(" (Specified by: ").append(discovery.getRangeSource()).append(')');
      if (!configuration.resolvedStaticPeers.isEmpty())
      {
         newLine(printout).append("Static peers: ");
         List<String> peers = discovery.getStaticPeers();
         for (int i = 0; i < peers.size(); ++i)
         {
            if (i > 0)
            {
               printout.append(", ");
            }
            printout.append(peers.get(i));
            String resolved = configuration.resolvedStaticPeers.get(i);
            if (!resolved.equals(peers.get(i)))
            {
               printout.append(" (").append(resolved).append(')');
            }
         }
         printout.append(" (Specified by: ").append(discovery.getStaticPeersSource()).append(')');
      }
   }

   private static void appendSecurity(StringBuilder printout, ROS2SecurityConfiguration security)
   {
      switch (security.getStatus())
      {
         case DISABLED -> newLine(printout).append("SROS2 security: disabled");
         case ENABLED -> newLine(printout)
               .append("SROS2 security: enabled (")
               .append(security.isEnforce() ? "Enforce" : "Permissive")
               .append(", ")
               .append(security.getSecurityDirectory().getPath())
               .append(security.getProperties().containsKey(ROS2SecurityConfiguration.LEGACY_AUTHENTICATION_ALGORITHMS)
                             ? ", legacy algorithm names"
                             : "")
               .append(')');
         case PERMISSIVE_UNSECURED -> newLine(printout).append("SROS2 security: requested but NOT active (Permissive strategy, running unsecured)");
      }
   }
}
