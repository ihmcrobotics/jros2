package us.ihmc.fastddsjava.profiles;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import us.ihmc.fastddsjava.profiles.gen.Dds;
import us.ihmc.fastddsjava.profiles.gen.LibrarySettingsType;
import us.ihmc.fastddsjava.profiles.gen.LogType;
import us.ihmc.fastddsjava.profiles.gen.ProfilesType;
import us.ihmc.fastddsjava.profiles.gen.TypesType;

import java.io.StringWriter;

// https://fast-dds.docs.eprosima.com/en/latest/fastdds/xml_configuration/making_xml_profiles.html
public class ProfilesXML
{
   private final ProfilesType profilesType = new ProfilesType();
   private final LibrarySettingsType librarySettingsType = new LibrarySettingsType();
   private final LogType logType = new LogType();
   private final TypesType typesType = new TypesType();

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

   public static void main(String[] args)
   {
      ProfilesXML xml = new ProfilesXML();

      System.out.println(xml.marshall());
   }
}
