package us.ihmc.fastddsjava.profiles;

import jakarta.xml.bind.JAXBElement;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType.InterfaceWhiteList;

import javax.xml.namespace.QName;
import java.util.UUID;

/**
 * Methods for creating {@link TransportDescriptorType}
 * See: <a href="https://fast-dds.docs.eprosima.com/en/v3.2.2/fastdds/transport/transport.html">Transport Layer</a>
 */
public final class TransportDescriptorTypeTools
{
   public static TransportDescriptorType createUDPv4Descriptor()
   {
      TransportDescriptorType descriptorType = new TransportDescriptorType();
      descriptorType.setTransportId(UUID.randomUUID().toString());
      descriptorType.setType("UDPv4");

      return descriptorType;
   }

   public static TransportDescriptorType createUDPv6Descriptor()
   {
      TransportDescriptorType descriptorType = new TransportDescriptorType();
      descriptorType.setTransportId(UUID.randomUUID().toString());
      descriptorType.setType("UDPv6");

      return descriptorType;
   }

   public static TransportDescriptorType createTCPv4Descriptor()
   {
      TransportDescriptorType descriptorType = new TransportDescriptorType();
      descriptorType.setTransportId(UUID.randomUUID().toString());
      descriptorType.setType("TCPv4");

      return descriptorType;
   }

   public static TransportDescriptorType createTCPv6Descriptor()
   {
      TransportDescriptorType descriptorType = new TransportDescriptorType();
      descriptorType.setTransportId(UUID.randomUUID().toString());
      descriptorType.setType("TCPv6");

      return descriptorType;
   }

   public static TransportDescriptorType createSHMDescriptor()
   {
      TransportDescriptorType descriptorType = new TransportDescriptorType();
      descriptorType.setTransportId(UUID.randomUUID().toString());
      descriptorType.setType("SHM");

      return descriptorType;
   }

   /**
    * Set the whitelisted addresses or interface names for a transport descriptor.
    */
   public static void setInterfacesWhitelist(TransportDescriptorType transportDescriptorType, String... addressOrInterfaceNames)
   {
      if (addressOrInterfaceNames == null || addressOrInterfaceNames.length == 0)
      {
         transportDescriptorType.setInterfaceWhiteList(null);
      }
      else
      {
         InterfaceWhiteList interfaceWhiteList = new InterfaceWhiteList();

         for (int i = 0; i < addressOrInterfaceNames.length; i++)
         {
            String addressOrInterfaceName = addressOrInterfaceNames[i];
            JAXBElement<String> element = new JAXBElement<>(new QName(ProfilesXML.FAST_DDS_NAMESPACE_URI, "address"), String.class, addressOrInterfaceName);
            interfaceWhiteList.getAddressOrInterface().add(element);
         }

         transportDescriptorType.setInterfaceWhiteList(interfaceWhiteList);
      }
   }
}
