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

import us.ihmc.jros2.parser.field.InterfaceField;

import java.util.LinkedList;
import java.util.List;

public class MsgContext extends InterfaceContext
{
   protected MsgContext(String schema)
   {
      super(schema);
   }

   public boolean isEnum()
   {
      int uint8_constantFieldsCount = 0;
      int uint8_otherFieldsCount = 0;
      int otherFieldsCount = 0;

      for (InterfaceField field : getFieldList())
      {
         if (field.getType().equals("uint8"))
         {
            if (field.getConstantValue() != null)
            {
               uint8_constantFieldsCount++;
            }
            else
            {
               uint8_otherFieldsCount++;
            }
         }
         else
         {
            otherFieldsCount++;
         }
      }

      /*
       * We assume a message interface is an enum if there are more than 0 uint8 constant fields,
       * exactly 1 uint8 fields, and no other fields.
       */
      return uint8_constantFieldsCount > 0 && uint8_otherFieldsCount == 1 && otherFieldsCount == 0;
   }

   public List<InterfaceField> getEnumFields()
   {
      List<InterfaceField> enumFields = new LinkedList<>();

      if (isEnum())
      {
         for (InterfaceField field : getFieldList())
         {
            if (field.getConstantValue() != null)
            {
               enumFields.add(field);
            }
         }
      }

      return enumFields;
   }

   public List<String> getEnumNames()
   {
      List<String> enumNames = new LinkedList<>();

      for (InterfaceField enumField : getEnumFields())
      {
         enumNames.add(enumField.getName());
      }

      return enumNames;
   }
}
