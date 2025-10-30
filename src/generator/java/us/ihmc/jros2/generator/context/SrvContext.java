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
package us.ihmc.jros2.generator.context;

import java.util.LinkedHashMap;
import java.util.Map;

public class SrvContext extends InterfaceContext
{
   private int section; // 0 = request, 1 = reply

   private final Map<String, InterfaceField> requestFields;
   private final Map<String, InterfaceField> replyFields;

   public SrvContext(String packageName, String fileName, String fileContent)
   {
      super(packageName, fileName, fileContent);

      assert fileName.endsWith(".srv");

      requestFields = new LinkedHashMap<>(); // LinkedHashMap to keep insertion order
      replyFields = new LinkedHashMap<>(); // LinkedHashMap to keep insertion order
   }

   @Override
   protected void onSection()
   {
      if (section < 1)
      {
         section++;
      }
   }

   @Override
   protected void onField(InterfaceField field)
   {
      switch (section)
      {
         case 0 -> requestFields.put(field.getName(), field);
         case 1 -> replyFields.put(field.getName(), field);
      }
   }

   public Map<String, InterfaceField> getRequestFields()
   {
      return requestFields;
   }

   public Map<String, InterfaceField> getReplyFields()
   {
      return replyFields;
   }

   // TODO: Finish
}
