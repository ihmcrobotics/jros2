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

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
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

import javax.xml.namespace.QName;
import java.io.StringWriter;

// https://fast-dds.docs.eprosima.com/en/v3.2.2/fastdds/xml_configuration/making_xml_profiles.html
public class ProfilesXML
{
   public static final String FAST_DDS_NAMESPACE_URI = "http://www.eprosima.com";
   private static final Object loadLock = new Object();

   private final ProfilesType profilesType;
   private final LibrarySettingsType librarySettingsType;
   private final LogType logType;
   private final TypesType typesType;

   public ProfilesXML()
   {
      profilesType = new ProfilesType();
      librarySettingsType = new LibrarySettingsType();
      logType = new LogType();
      typesType = new TypesType();

      librarySettingsType.setIntraprocessDelivery("FULL"); // Default to enable Intraprocess delivery
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
      profilesType.getDomainparticipantFactoryOrParticipantOrDataWriter()
                  .add(new JAXBElement<>(new QName(FAST_DDS_NAMESPACE_URI, "participant"), ParticipantProfileType.class, participantProfileType));
   }

   public void addPublisherProfile(PublisherProfileType publisherProfileType)
   {
      profilesType.getDomainparticipantFactoryOrParticipantOrDataWriter()
                  .add(new JAXBElement<>(new QName(FAST_DDS_NAMESPACE_URI, "data_writer"), PublisherProfileType.class, publisherProfileType));
   }

   public void addSubscriberProfile(SubscriberProfileType subscriberProfileType)
   {
      profilesType.getDomainparticipantFactoryOrParticipantOrDataWriter()
                  .add(new JAXBElement<>(new QName(FAST_DDS_NAMESPACE_URI, "data_reader"), SubscriberProfileType.class, subscriberProfileType));
   }

   public void addTopicProfile(TopicProfileType topicProfileType)
   {
      profilesType.getDomainparticipantFactoryOrParticipantOrDataWriter()
                  .add(new JAXBElement<>(new QName(FAST_DDS_NAMESPACE_URI, "topic"), TopicProfileType.class, topicProfileType));
   }

   public void addTransportDescriptorsProfile(TransportDescriptorListType transportDescriptorListType)
   {
      profilesType.getDomainparticipantFactoryOrParticipantOrDataWriter()
                  .add(new JAXBElement<>(new QName(FAST_DDS_NAMESPACE_URI, "transport_descriptors"),
                                         TransportDescriptorListType.class,
                                         transportDescriptorListType));
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

   private static String marshall(Dds dds)
   {
      StringWriter writer = new StringWriter();

      try
      {
         JAXBContext context = JAXBContext.newInstance(Dds.class);
         Marshaller m = context.createMarshaller();
         m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
         m.marshal(dds, writer);
      }
      catch (JAXBException e)
      {
         e.printStackTrace();
      }

      return writer.toString();
   }
}
