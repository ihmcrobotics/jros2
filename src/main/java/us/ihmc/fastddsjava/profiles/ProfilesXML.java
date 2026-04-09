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

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import us.ihmc.fastddsjava.fastddsjavaException;
import us.ihmc.fastddsjava.fastddsjavaTools;
import us.ihmc.fastddsjava.pointers.fastddsjava;
import us.ihmc.fastddsjava.profiles.gen.Dds;
import us.ihmc.fastddsjava.profiles.gen.LibrarySettingsType;
import us.ihmc.fastddsjava.profiles.gen.LogType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.ProfilesType;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;
import us.ihmc.fastddsjava.profiles.gen.TopicProfileType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorListType;
import us.ihmc.fastddsjava.profiles.gen.TypesType;

import java.io.StringWriter;

// https://fast-dds.docs.eprosima.com/en/v3.2.2/fastdds/xml_configuration/making_xml_profiles.html
public class ProfilesXML
{
   public static final String FAST_DDS_NAMESPACE_URI = "http://www.eprosima.com";
   private static final Object loadLock = new Object();
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

   private final ProfilesType profilesType;
   private final LibrarySettingsType librarySettingsType;
   private final LogType logType;
   private final TypesType typesType;
   private final java.util.List<ProfileElement> profileElements;

   public ProfilesXML()
   {
      profilesType = new ProfilesType();
      librarySettingsType = new LibrarySettingsType();
      logType = new LogType();
      typesType = new TypesType();
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
         fastddsjavaTools.retcodeThrowOnError(fastddsjava.fastddsjava_load_xml_profiles_string(xml));
      }
   }

   public ProfilesType getProfilesType()
   {
      return profilesType;
   }

   public LibrarySettingsType getLibrarySettingsType()
   {
      return librarySettingsType;
   }

   public LogType getLogType()
   {
      return logType;
   }

   public TypesType getTypesType()
   {
      return typesType;
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
      Dds dds = new Dds();

      dds.setProfiles(profilesType);
      dds.setLibrarySettings(librarySettingsType);
      dds.setLog(logType);
      dds.setTypes(typesType);

      return marshall(dds);
   }

   private String marshall(Dds dds)
   {
      StringBuilder xml = new StringBuilder();
      xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
      xml.append("<dds xmlns=\"").append(FAST_DDS_NAMESPACE_URI).append("\">\n");

      // Add profiles
      if (!profileElements.isEmpty())
      {
         xml.append("    <profiles>\n");

         XmlMapper xmlMapper = new XmlMapper();
         xmlMapper.enable(SerializationFeature.INDENT_OUTPUT);
         xmlMapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
         xmlMapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY);

         for (ProfileElement element : profileElements)
         {
            try
            {
               // Serialize the profile object to XML
               String profileXml = xmlMapper.writeValueAsString(element.profile);

               // Remove XML declaration if present
               profileXml = profileXml.replaceFirst("<\\?xml[^>]*\\?>\\s*", "");

               // Replace the root element name with the correct element name
               profileXml = profileXml.replaceFirst("<[A-Za-z][^/>\\s]*", "<" + element.elementName);
               profileXml = profileXml.replaceFirst("</[A-Za-z][^>]*>\\s*$", "</" + element.elementName + ">");

               // Add proper indentation (2 levels: 8 spaces)
               String[] lines = profileXml.split("\n");
               for (String line : lines)
               {
                  if (!line.trim().isEmpty())
                  {
                     xml.append("        ").append(line).append("\n");
                  }
               }
            }
            catch (Exception e)
            {
               e.printStackTrace();
            }
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
