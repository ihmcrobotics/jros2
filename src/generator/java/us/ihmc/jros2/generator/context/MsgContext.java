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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MsgContext extends InterfaceContext
{
   private final Map<String, InterfaceField> fields;

   public MsgContext(String packageName, String fileName, String fileContent)
   {
      super(packageName, fileName, fileContent);

      assert fileName.endsWith(".msg");

      fields = new LinkedHashMap<>(); // LinkedHashMap to keep insertion order
   }

   @Override
   protected void onSection()
   {
      // Do nothing, msg definitions do not have sections
   }

   @Override
   protected void onField(InterfaceField field)
   {
      fields.put(field.getName(), field);
   }

   public InterfaceField getField(String name)
   {
      return fields.get(name);
   }

   public List<InterfaceField> getFields()
   {
      List<InterfaceField> fields = new ArrayList<>();
      this.fields.forEach((fieldName, field) -> fields.add(field));
      return fields;
   }
}
