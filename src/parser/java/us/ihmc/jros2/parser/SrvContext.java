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
 * Representation of a ROS 2 service (.srv) interface.
 * A service consists of a request message and a response message separated by '---'.
 */
public class SrvContext extends InterfaceContext
{
   private final MsgContext requestContext;
   private final MsgContext responseContext;

   public SrvContext(String schema, MsgContext requestContext, MsgContext responseContext)
   {
      super(schema);
      this.requestContext = requestContext;
      this.responseContext = responseContext;
   }

   public MsgContext getRequestContext()
   {
      return requestContext;
   }

   public MsgContext getResponseContext()
   {
      return responseContext;
   }

   @Override
   public String getDDSName()
   {
      String javaClassNameSanitized = getJavaClassName().replace("_", "");
      return getPackageName() + "::srv::dds_::" + javaClassNameSanitized + "_";
   }
}
