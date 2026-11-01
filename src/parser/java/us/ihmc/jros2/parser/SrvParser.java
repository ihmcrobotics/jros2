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

import java.util.ArrayList;
import java.util.List;
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
      List<String> sections = splitSections(schema, 2, packageResourceName);

      SrvSectionContext requestContext = parseSection(sections.get(0), packageResourceName, SrvSection.REQUEST);
      SrvSectionContext responseContext = parseSection(sections.get(1), packageResourceName, SrvSection.RESPONSE);

      return new SrvContext(schema, requestContext, responseContext);
   }

   /**
    * Split a .srv or .action schema on its {@value #SECTION_SEPARATOR} lines.
    *
    * @param sectionCount the number of sections expected (2 for a service, 3 for an action)
    * @throws IllegalArgumentException if the number of separators does not match
    */
   static List<String> splitSections(String schema, int sectionCount, String packageResourceName)
   {
      List<StringJoiner> sections = new ArrayList<>();
      sections.add(new StringJoiner("\n"));

      for (String line : schema.split("\\R", -1))
      {
         if (line.trim().equals(SECTION_SEPARATOR))
         {
            sections.add(new StringJoiner("\n"));
            continue;
         }
         sections.get(sections.size() - 1).add(line);
      }

      if (sections.size() != sectionCount)
      {
         throw new IllegalArgumentException(
               packageResourceName + " must contain exactly " + (sectionCount - 1) + " '" + SECTION_SEPARATOR + "' separator" + (sectionCount > 2 ? "s" : "")
               + ", found " + (sections.size() - 1));
      }

      List<String> result = new ArrayList<>();
      for (StringJoiner section : sections)
         result.add(section.toString());
      return result;
   }

   private static SrvSectionContext parseSection(String section, String packageResourceName, SrvSection srvSection) throws InterfaceFieldParsingException
   {
      SrvSectionContext context = new SrvSectionContext(section, packageResourceName, srvSection);
      MsgParser.parseFields(context, section);
      return context;
   }
}
