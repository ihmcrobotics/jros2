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
 * Representation of a ROS 2 service (.srv): a request message and a response message.
 */
public class SrvContext
{
   private final String schema;
   private final SrvSectionContext requestContext;
   private final SrvSectionContext responseContext;

   protected SrvContext(String schema, SrvSectionContext requestContext, SrvSectionContext responseContext)
   {
      this.schema = schema;
      this.requestContext = requestContext;
      this.responseContext = responseContext;
   }

   public String getSchema()
   {
      return schema;
   }

   public SrvSectionContext getRequestContext()
   {
      return requestContext;
   }

   public SrvSectionContext getResponseContext()
   {
      return responseContext;
   }

   public String getPackageName()
   {
      return requestContext.getPackageName();
   }

   public String getServiceName()
   {
      return requestContext.getServiceName();
   }
}
