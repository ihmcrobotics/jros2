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

import java.util.Map;

/**
 * Settings for jros2, parsed from environment variables
 */
class jros2SettingsEnv implements jros2Settings
{
   static final String DOMAIN_ID_KEY = "ROS_DOMAIN_ID";
   static final String INTERFACE_WHITELIST_KEY = "FASTDDS_INTERFACE_WHITELIST";

   private final Map<String, String> env;

   jros2SettingsEnv()
   {
      this(System.getenv());
   }

   /**
    * Constructor mainly for unit tests, so a fake environment can be given.
    *
    * @param env The system environment.
    */
   jros2SettingsEnv(Map<String, String> env)
   {
      this.env = env;
   }

   @Override
   public int rosDomainId()
   {
      try
      {
         return Integer.parseInt(env.get(DOMAIN_ID_KEY));
      }
      catch (NumberFormatException ignored)
      {
         return new jros2SettingsDefault().rosDomainId();
      }
   }

   @Override
   public boolean hasROSDomainId()
   {
      return env.containsKey(DOMAIN_ID_KEY);
   }

   @Override
   public String[] interfaceWhitelist()
   {
      String property = env.get(INTERFACE_WHITELIST_KEY);
      if (property != null)
      {
         return jros2Settings.splitInterfaceWhitelistFromCSV(property);
      }
      else
      {
         return new jros2SettingsDefault().interfaceWhitelist();
      }
   }

   @Override
   public boolean hasInterfaceWhitelist()
   {
      return env.containsKey(INTERFACE_WHITELIST_KEY);
   }
}
