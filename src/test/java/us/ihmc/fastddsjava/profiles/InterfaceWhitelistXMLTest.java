/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
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
package us.ihmc.fastddsjava.profiles;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType.InterfaceWhiteList;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@code <interfaceWhiteList>} of a transport descriptor. Its entries are addresses or interface names, and an entry can
 * also be a list of addresses (the schema's address list type; the node printout lists them as well). Every address must
 * become its own {@code <address>} element: the list's {@code toString()} ("[a, b]") is no address Fast-DDS can match.
 */
public class InterfaceWhitelistXMLTest
{
   private static String whitelistXml(Object... entries)
   {
      TransportDescriptorType descriptor = new TransportDescriptorType();
      descriptor.setTransportId("cov_whitelist");
      descriptor.setType("UDPv4");
      InterfaceWhiteList whiteList = new InterfaceWhiteList();
      whiteList.getAddressOrInterface().addAll(Arrays.asList(entries));
      descriptor.setInterfaceWhiteList(whiteList);

      StringBuilder xml = new StringBuilder();
      ProfilesXMLWriter.writeTransportDescriptor(xml, descriptor);
      String text = xml.toString();
      return text.substring(text.indexOf("<interfaceWhiteList>"), text.indexOf("</interfaceWhiteList>") + "</interfaceWhiteList>".length());
   }

   @Test
   public void testAddressesAndInterfaceNames()
   {
      assertEquals("<interfaceWhiteList><address>192.168.1.41</address><address>eth0</address></interfaceWhiteList>", whitelistXml("192.168.1.41", "eth0"));
      assertEquals("<interfaceWhiteList></interfaceWhiteList>", whitelistXml());
   }

   @Test
   public void testListEntriesBecomeOneAddressPerElement()
   {
      assertEquals("<interfaceWhiteList><address>127.0.0.1</address><address>10.0.0.1</address><address>10.0.0.2</address><address>eth1</address>"
                   + "</interfaceWhiteList>",
                   whitelistXml("127.0.0.1", List.of("10.0.0.1", "10.0.0.2"), "eth1"));
      assertEquals("<interfaceWhiteList></interfaceWhiteList>", whitelistXml(List.of()));
      // Entries are escaped like every other text
      assertEquals("<interfaceWhiteList><address>a&lt;b</address></interfaceWhiteList>", whitelistXml(List.of("a<b")));
   }
}
