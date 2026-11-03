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

/**
 * How far automatic discovery reaches, matching ROS 2's {@code ROS_AUTOMATIC_DISCOVERY_RANGE} (Iron and newer) and configured
 * exactly like rmw_fastrtps (Jazzy) configures Fast-DDS.
 * See: <a href="https://docs.ros.org/en/jazzy/Tutorials/Advanced/Improved-Dynamic-Discovery.html">Improved Dynamic Discovery</a>
 * <p>
 * The range is taken from {@link ROS2NodeOptions#discoveryRange}, otherwise from the {@code ROS_AUTOMATIC_DISCOVERY_RANGE}
 * setting (system property {@code ros.automatic.discovery.range}, environment variable, or jros2.properties), otherwise SUBNET.
 * The deprecated {@code ROS_LOCALHOST_ONLY=1} forces LOCALHOST and drops all static peers, like rcl.
 */
public enum ROS2DiscoveryRange
{
   /** Use the middleware defaults (multicast discovery on all interfaces); static peers are ignored. */
   SYSTEM_DEFAULT,
   /** No discovery at all: the participant does not discover or accept any other participant; static peers are ignored. */
   OFF,
   /**
    * Only discover participants on this host (no multicast; unicast announcements to 127.0.0.1) plus the static peers.
    */
   LOCALHOST,
   /** Discover participants reachable by multicast on the local subnet (the ROS 2 default), plus the static peers. */
   SUBNET
}
