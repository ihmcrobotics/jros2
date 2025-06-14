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

class jros2SettingsDefault implements jros2Settings
{
   private static final String NAME = "Default Values";

   @Override
   public String getSourceName()
   {
      return NAME;
   }

   @Override
   public int rosDomainId()
   {
      return 0;
   }

   @Override
   public boolean hasROSDomainId()
   {
      return true;
   }

   @Override
   public String[] interfaceWhitelist()
   {
      return new String[0];
   }

   @Override
   public boolean hasInterfaceWhitelist()
   {
      return true;
   }
}
