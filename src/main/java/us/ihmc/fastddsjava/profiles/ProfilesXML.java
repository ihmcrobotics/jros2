package us.ihmc.fastddsjava.profiles;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
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

// https://fast-dds.docs.eprosima.com/en/latest/fastdds/xml_configuration/making_xml_profiles.html
public class ProfilesXML
{
   private static final String FAST_DDS_NAMESPACE_URI = "http://www.eprosima.com";

   private final ProfilesType profilesType = new ProfilesType();
   private final LibrarySettingsType librarySettingsType = new LibrarySettingsType();
   private final LogType logType = new LogType();
   private final TypesType typesType = new TypesType();

   public ProfilesXML()
   {
      librarySettingsType.setIntraprocessDelivery("FULL"); // Default to enable Intraprocess delivery
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
                  .add(new JAXBElement<>(new QName(FAST_DDS_NAMESPACE_URI, "data_writer"),
                                         PublisherProfileType.class,
                                         publisherProfileType));
   }

   public void addSubscriberProfile(SubscriberProfileType subscriberProfileType)
   {
      profilesType.getDomainparticipantFactoryOrParticipantOrDataWriter()
                  .add(new JAXBElement<>(new QName(FAST_DDS_NAMESPACE_URI, "data_reader"),
                                         SubscriberProfileType.class,
                                         subscriberProfileType));
   }

   public void addTopicProfile(TopicProfileType topicProfileType)
   {
      profilesType.getDomainparticipantFactoryOrParticipantOrDataWriter()
                  .add(new JAXBElement<>(new QName(FAST_DDS_NAMESPACE_URI, "topic"),
                                         TopicProfileType.class,
                                         topicProfileType));
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
