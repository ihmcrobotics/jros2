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
package us.ihmc.jros2.parser;

/**
 * One side (request or response) of a ROS 2 service (.srv). It is a regular message,
 * so it can be generated as a ROS2Message, but its Java class name and DDS type name
 * follow the service conventions, e.g. {@code Foo_Request} and {@code pkg::srv::dds_::Foo_Request_}.
 */
public class SrvSectionContext extends MsgContext
{
   private final String serviceName;
   private final SrvSection section;

   protected SrvSectionContext(String schema, String packageResourceName, SrvSection section)
   {
      super(schema);
      this.section = section;
      setPackageResourceName(packageResourceName);
      serviceName = getResourceName().split("\\.")[0];
      setJavaClassName(serviceName + "_" + section.getSuffix());
   }

   public String getServiceName()
   {
      return serviceName;
   }

   public SrvSection getSection()
   {
      return section;
   }

   @Override
   public String getDDSName()
   {
      String serviceNameSanitized = serviceName.replace("_", "");
      return getPackageName() + "::srv::dds_::" + serviceNameSanitized + "_" + section.getSuffix() + "_";
   }
}
