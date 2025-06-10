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

import jakarta.xml.bind.JAXBElement;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType.InterfaceWhiteList;
import us.ihmc.log.LogTools;

import java.util.List;
import java.util.StringJoiner;

class ROS2NodePrintout
{
   static
   {
      jros2.load();
   }

   public static void print(ParticipantProfileType participantProfile, TransportDescriptorType... transportDescriptors)
   {
      StringJoiner printout = new StringJoiner("\n\t", "\n\t", "");

      // Get the name
      String nodeName = participantProfile.getRtps().getName();
      printout.add("Created ROS2Node: %s".formatted(nodeName));

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
      printout.add("DomainID: %d (Specified by: %s)".formatted(domainId, domainIdSource));

      // Check if we're using builtin transports
      boolean usingBuiltinTransports = participantProfile.getRtps().isUseBuiltinTransports();
      if (usingBuiltinTransports) // If so, default builtin transports are UDPv4 and SHM
      {
         printout.add("Using builtin transports: UDPv4, SHM");
      }
      else if (transportDescriptors != null) // Otherwise we must be using custom transports specified by the descriptors
      {
         printout.add("Using custom transports:");

         for (int i = 0; i < transportDescriptors.length; ++i)
         {
            // Get the transport type (e.g. UDPv4, TCPv6, SHM, etc.)
            String type = transportDescriptors[i].getType();

            // See if an interface whitelist is specified for this transport
            InterfaceWhiteList interfaceWhiteList = transportDescriptors[i].getInterfaceWhiteList();
            if (interfaceWhiteList == null || interfaceWhiteList.getAddressOrInterface().isEmpty())
            {  // No whitelist. Transport is available on all interfaces
               printout.add("\t%s: on all interfaces".formatted(type));
            }
            else // Whitelist specified
            {
               List<JAXBElement<?>> whitelistElements = transportDescriptors[i].getInterfaceWhiteList().getAddressOrInterface();
               StringJoiner whitelistString = new StringJoiner(", ");
               for (int j = 0; j < whitelistElements.size(); ++j)
               {
                  // The value can either be a List<String> or String
                  Object value = whitelistElements.get(j).getValue();
                  if (value instanceof List<?> list)
                  {
                     for (int k = 0; k < list.size(); ++k)
                     {
                        whitelistString.add(list.get(k).toString());
                     }
                  }
                  else if (value instanceof String string)
                  {
                     whitelistString.add(string);
                  }
               }

               printout.add("\t%s: on %s".formatted(type, whitelistString));
            }
         }
      }

      LogTools.info(printout);
   }
}
