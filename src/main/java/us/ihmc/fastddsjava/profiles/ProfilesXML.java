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
package us.ihmc.fastddsjava.profiles;

import us.ihmc.fastddsjava.fastddsjavaException;
import us.ihmc.fastddsjava.fastddsjavaTools;
import us.ihmc.fastddsjava.profiles.gen.LibrarySettingsType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;
import us.ihmc.fastddsjava.profiles.gen.TopicProfileType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorListType;

// https://fast-dds.docs.eprosima.com/en/v3.6.2/fastdds/xml_configuration/making_xml_profiles.html
public class ProfilesXML
{
   public static final String FAST_DDS_NAMESPACE_URI = "http://www.eprosima.com";
   private static final Object loadLock = new Object();
   public static Object getLoadLock()
   {
      return loadLock;
   }
   private static String intraprocessDelivery = "OFF"; // Default to intraprocess delivery OFF

   /**
    * Set the intraprocess delivery mode for the entire library.
    * Call this before creating a new {@link ProfilesXML}
    */
   public static void setIntraprocessDelivery(String value)
   {
      switch (value)
      {
         case "OFF":
         case "USER_DATA_ONLY":
         case "FULL":
            break;
         default:
            throw new IllegalArgumentException("Invalid intraprocess delivery mode: " + value);
      }

      intraprocessDelivery = value;
   }

   private static class ProfileElement
   {
      String elementName;
      Object profile;

      ProfileElement(String elementName, Object profile)
      {
         this.elementName = elementName;
         this.profile = profile;
      }
   }

   private final LibrarySettingsType librarySettingsType;
   private final java.util.List<ProfileElement> profileElements;

   public ProfilesXML()
   {
      librarySettingsType = new LibrarySettingsType();
      profileElements = new java.util.ArrayList<>();

      librarySettingsType.setIntraprocessDelivery(intraprocessDelivery);
   }

   public void load() throws fastddsjavaException
   {
      String xml = marshall();

      // This synchronize seems to be required, DomainParticipantFactory#load_XML_profiles_string doesn't seem
      // to be fully thread-safe and can sometimes result in a native crash.
      synchronized (loadLock)
      {
         fastddsjavaTools.retcodeThrowOnError(us.ihmc.fastddsjava.natives.fastddsjava.loadXmlProfilesString(xml));
      }
   }

   public LibrarySettingsType getLibrarySettingsType()
   {
      return librarySettingsType;
   }

   public void addParticipantProfile(ParticipantProfileType participantProfileType)
   {
      profileElements.add(new ProfileElement("participant", participantProfileType));
   }

   public void addPublisherProfile(PublisherProfileType publisherProfileType)
   {
      profileElements.add(new ProfileElement("data_writer", publisherProfileType));
   }

   public void addSubscriberProfile(SubscriberProfileType subscriberProfileType)
   {
      profileElements.add(new ProfileElement("data_reader", subscriberProfileType));
   }

   public void addTopicProfile(TopicProfileType topicProfileType)
   {
      profileElements.add(new ProfileElement("topic", topicProfileType));
   }

   public void addTransportDescriptorsProfile(TransportDescriptorListType transportDescriptorListType)
   {
      profileElements.add(new ProfileElement("transport_descriptors", transportDescriptorListType));
   }

   public String marshall()
   {
      StringBuilder xml = new StringBuilder();
      xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
      xml.append("<dds xmlns=\"").append(FAST_DDS_NAMESPACE_URI).append("\">\n");

      // Add profiles
      if (!profileElements.isEmpty())
      {
         xml.append("    <profiles>\n");
         for (ProfileElement element : profileElements)
         {
            xml.append("        ");
            ProfilesXMLWriter.writeProfile(xml, element.elementName, element.profile);
            xml.append('\n');
         }
         xml.append("    </profiles>\n");
      }
      else
      {
         xml.append("    <profiles/>\n");
      }

      // Add types (always empty)
      xml.append("    <types/>\n");

      // Add log (always empty)
      xml.append("    <log/>\n");

      // Add library settings
      xml.append("    <library_settings>\n");
      xml.append("        <intraprocess_delivery>").append(librarySettingsType.getIntraprocessDelivery()).append("</intraprocess_delivery>\n");
      xml.append("    </library_settings>\n");

      xml.append("</dds>\n");

      return xml.toString();
   }
}
