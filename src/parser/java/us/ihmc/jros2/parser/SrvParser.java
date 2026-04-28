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

import us.ihmc.jros2.parser.field.InterfaceFieldParsingException;

/**
 * Parser for ROS 2 service (.srv) definition files.
 * Service files are divided into request and response sections separated by '---'.
 */
public final class SrvParser
{
   public static SrvContext parseSrv(String schema, String packageResourceName) throws InterfaceFieldParsingException
   {
      // Split the schema into request and response sections
      String[] sections = schema.split("---", 2);

      if (sections.length != 2)
      {
         throw new InterfaceFieldParsingException("service_definition", schema,
            new RuntimeException("Invalid service definition: must contain exactly one '---' separator"));
      }

      String requestSchema = sections[0].trim();
      String responseSchema = sections[1].trim();

      // Parse request and response using service-specific contexts
      // These ensure the correct DDS type name (::srv::dds_::) instead of (::msg::dds_::)
      SrvRequestContext requestContext = MsgParser.parseMsgInto(new SrvRequestContext(requestSchema), requestSchema, packageResourceName + "_Request");
      SrvResponseContext responseContext = MsgParser.parseMsgInto(new SrvResponseContext(responseSchema), responseSchema, packageResourceName + "_Response");

      // Create the service context
      SrvContext srvContext = new SrvContext(schema, requestContext, responseContext);
      srvContext.setPackageResourceName(packageResourceName);

      return srvContext;
   }
}
