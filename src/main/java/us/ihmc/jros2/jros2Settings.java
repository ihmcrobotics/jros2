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
    * A list of addresses (IPv4 or IPv6) and/or interface names which correspond to network interfaces on the host system.
    * {@link ROS2Node} will only be able to communicate using whitelisted network interfaces.
    * Empty or null for no whitelist.
    * This is a Fast-DDS parameter. See: <a href="https://fast-dds.docs.eprosima.com/en/v3.2.2/fastdds/transport/whitelist.html">Interface Whitelist</a>.
    */
   String[] interfaceWhitelist();

   /**
    * @return Whether an interface whitelist has been specified.
    */
   boolean hasInterfaceWhitelist();

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
