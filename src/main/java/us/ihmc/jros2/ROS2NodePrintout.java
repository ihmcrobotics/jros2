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

import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

class ROS2NodePrintout extends DefaultHandler
{
   static
   {
      jros2.load();
   }

   private final StringBuilder valueReader;

   private String nodeName;
   private int domainId;
   private String intraProcessDeliveryMode;

   public ROS2NodePrintout()
   {
      valueReader = new StringBuilder();
   }

   @Override
   public void startElement(String uri, String localeName, String qualifiedName, Attributes attributes)
   {

   }

   private static final String PRINTOUT_TEMPLATE = """
         Created ROS2Node: %s
         Domain ID: %d (Specified in: %s)
         Using %s transports: %s
         """;

   public static void printInfo(String marshalledProfilesXML)
   {
      InputStream stream = new ByteArrayInputStream(marshalledProfilesXML.getBytes(StandardCharsets.UTF_8));
      try
      {
         SAXParser parser = SAXParserFactory.newDefaultInstance().newSAXParser();
         parser.parse(stream, new DefaultHandler()
         {
            @Override
            public void startDocument()
            {

            }
         });
      }
      catch (SAXException | ParserConfigurationException | IOException e)
      {
         throw new RuntimeException(e);
      }
   }
}
