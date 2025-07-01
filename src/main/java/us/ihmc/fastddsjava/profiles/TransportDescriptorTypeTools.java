package us.ihmc.fastddsjava.profiles;

import jakarta.xml.bind.JAXBElement;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType.InterfaceWhiteList;

import javax.xml.namespace.QName;
import java.io.File;
import java.nio.file.Files;
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
      if (transportDescriptorType.getType().equals("SHM"))
      {
         throw new IllegalArgumentException("Cannot set interfaces whitelist for a SHM transport type");
      }

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

   public static final boolean SHM_TRANSPORT_AVAILABLE_ON_WINDOWS;

   static
   {
      /*
      Check if SHM transport is available for use on Windows.
      Effectively checks that the directory Fast-DDS uses for shared memory is available for writing.

      https://github.com/eProsima/Fast-DDS/blob/e0c453b0ca70ef54fe9dfa0e6031c48cc6446d2f/tools/fds/CliDiscoveryManager.cpp#L113
      */
      if (System.getProperty("os.name").startsWith("Windows"))
      {
         File shmDir = new File("C:\\ProgramData\\eprosima\\fastdds_interprocess");

         // Ensure the directory structure exists
         if (!shmDir.exists())
         {
            boolean ignored = shmDir.mkdirs();
         }

         // Check that the directory structure exists again and check that it's writable
         SHM_TRANSPORT_AVAILABLE_ON_WINDOWS = Files.isDirectory(shmDir.toPath()) && Files.isWritable(shmDir.toPath());
      }
      else
      {
         SHM_TRANSPORT_AVAILABLE_ON_WINDOWS = false;
      }
   }
}
