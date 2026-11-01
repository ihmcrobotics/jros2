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

import java.util.StringJoiner;

public final class SrvParser
{
   public static final String SECTION_SEPARATOR = "---";

   /**
    * Parse a ROS 2 service (.srv) schema. The request and response sections are separated
    * by a line containing only {@code ---}. Either section may be empty.
    *
    * @param schema              the contents of the .srv file
    * @param packageResourceName e.g. {@code example_interfaces/AddTwoInts.srv}
    * @return the parsed service, with one context per section
    * @throws IllegalArgumentException      if the schema does not contain exactly one separator
    * @throws InterfaceFieldParsingException if a field cannot be parsed
    */
   public static SrvContext parseSrv(String schema, String packageResourceName) throws InterfaceFieldParsingException
   {
      StringJoiner request = new StringJoiner("\n");
      StringJoiner response = new StringJoiner("\n");
      int separators = 0;

      for (String line : schema.split("\\R", -1))
      {
         if (line.trim().equals(SECTION_SEPARATOR))
         {
            separators++;
            continue;
         }

         if (separators == 0)
            request.add(line);
         else
            response.add(line);
      }

      if (separators != 1)
      {
         throw new IllegalArgumentException(
               "Service " + packageResourceName + " must contain exactly one '" + SECTION_SEPARATOR + "' separator, found " + separators);
      }

      SrvSectionContext requestContext = parseSection(request.toString(), packageResourceName, SrvSection.REQUEST);
      SrvSectionContext responseContext = parseSection(response.toString(), packageResourceName, SrvSection.RESPONSE);

      return new SrvContext(schema, requestContext, responseContext);
   }

   private static SrvSectionContext parseSection(String section, String packageResourceName, SrvSection srvSection) throws InterfaceFieldParsingException
   {
      SrvSectionContext context = new SrvSectionContext(section, packageResourceName, srvSection);
      MsgParser.parseFields(context, section);
      return context;
   }
}
