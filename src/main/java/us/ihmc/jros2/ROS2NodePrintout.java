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
 * This class generates formatted output describing the configuration of a ROS2 node,
 * including its domain ID, transport descriptors, interface whitelists, and intraprocess delivery settings.
 * <p>
 * Uses thread-local {@link StringBuilder}s to avoid generating garbage during string construction.
 */
final class ROS2NodePrintout
{
   private static final ThreadLocal<StringBuilder> PRINTOUT = ThreadLocal.withInitial(() -> new StringBuilder(512));
   private static final ThreadLocal<StringBuilder> WHITELIST = ThreadLocal.withInitial(() -> new StringBuilder(256));

   static
   {
      jros2.load();
   }

   /**
    * Prints the configuration details of a ROS2 node to the logger.
    * Can be disabled by setting the system property "jros2.node.printout" to "false".
    *
    * @param nodeClass            The class of the ROS2 node being created
    * @param participantProfile   The participant profile containing domain ID and RTPS configuration
    * @param transportDescriptors Optional transport descriptors specifying custom transports and interface whitelists
    */
   static void print(Class<? extends ROS2Node> nodeClass, ParticipantProfileType participantProfile, TransportDescriptorType... transportDescriptors)
   {
      // Check if printout is disabled via system property
      if ("false".equalsIgnoreCase(System.getProperty("jros2.node.printout")))
      {
         return;
      }

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
      printout.append('\n').append('\t').append("DomainID: ").append(domainId).append(" (Specified by: ").append(domainIdSource).append(')');

      // Check if we're using builtin transports
      boolean usingBuiltinTransports = participantProfile.getRtps().isUseBuiltinTransports();
      if (usingBuiltinTransports) // If so, default builtin transports are UDPv4 and SHM
      {
         printout.append('\n').append('\t').append("Using builtin transports: UDPv4, SHM");
      }
      else if (transportDescriptors != null) // Otherwise we must be using custom transports specified by the descriptors
      {
         printout.append('\n').append('\t').append("Using custom transports:");

         for (int i = 0; i < transportDescriptors.length; ++i)
         {
            // Get the transport type (e.g. UDPv4, TCPv6, SHM, etc.)
            String type = transportDescriptors[i].getType();

            // See if an interface whitelist is specified for this transport
            InterfaceWhiteList interfaceWhiteList = transportDescriptors[i].getInterfaceWhiteList();
            if (interfaceWhiteList == null || interfaceWhiteList.getAddressOrInterface().isEmpty())
            {
               // SHM uses shared memory, not network interfaces
               if (type.equals("SHM"))
               {
                  printout.append('\n').append('\t').append('\t').append(type).append(": local only");
               }
               else
               {
                  printout.append('\n').append('\t').append('\t').append(type).append(": on all interfaces");
               }
            }
            else // Whitelist specified
            {
               List<Object> whitelistElements = transportDescriptors[i].getInterfaceWhiteList().getAddressOrInterface();
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

               printout.append('\n').append('\t').append('\t').append(type).append(": on ").append(whitelistString);
            }
         }
      }

      if (jros2.get().intraprocessDelivery())
      {
         printout.append('\n').append('\t').append("Intraprocess delivery enabled - https://github.com/ihmcrobotics/jros2/wiki/Intraprocess-Delivery");
      }

      jros2.getLogger().info(printout.toString());
   }
}
