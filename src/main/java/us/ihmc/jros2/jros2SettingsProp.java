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

/**
 * Settings for jros2, parsed from system properties
 */
class jros2SettingsProp implements jros2Settings
{
   private static final String SOURCE_NAME = "System Properties";

   static final String DOMAIN_ID_KEY = "ros.domain.id";
   static final String INTERFACE_WHITELIST_KEY = "fastdds.interface.whitelist";

   @Override
   public String getSourceName()
   {
      return SOURCE_NAME;
   }

   @Override
   public int rosDomainId()
   {
      try
      {
         return Integer.parseInt(System.getProperty(DOMAIN_ID_KEY));
      }
      catch (NumberFormatException ignored)
      {
         return new jros2SettingsDefault().rosDomainId();
      }
   }

   @Override
   public boolean hasROSDomainId()
   {
      return System.getProperties().containsKey(DOMAIN_ID_KEY);
   }

   @Override
   public String[] interfaceWhitelist()
   {
      String property = System.getProperty(INTERFACE_WHITELIST_KEY);
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
      return System.getProperties().containsKey(INTERFACE_WHITELIST_KEY);
   }
}
