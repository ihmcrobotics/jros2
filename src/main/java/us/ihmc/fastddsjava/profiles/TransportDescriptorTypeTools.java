package us.ihmc.fastddsjava.profiles;

import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType.InterfaceWhiteList;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Factory methods for creating and configuring Fast-DDS {@link TransportDescriptorType} instances.
 * Supports UDPv4, UDPv6, TCPv4, TCPv6, and SHM (Shared Memory) transports with optional interface whitelisting.
 *
 * @see <a href="https://fast-dds.docs.eprosima.com/en/v3.2.2/fastdds/transport/transport.html">Fast-DDS Transport Layer</a>
 * @see <a href="https://fast-dds.docs.eprosima.com/en/v3.2.2/fastdds/transport/whitelist.html">Fast-DDS Interface Whitelist</a>
 */
public final class TransportDescriptorTypeTools
{
   /**
    * Create a UDPv4 transport descriptor.
    *
    * @return UDPv4 transport descriptor with auto-generated transport ID
    */
   public static TransportDescriptorType createUDPv4Descriptor()
   {
      TransportDescriptorType descriptor = new TransportDescriptorType();
      descriptor.setTransportId(UUID.randomUUID().toString());
      descriptor.setType("UDPv4");
      return descriptor;
   }

   /**
    * Create a UDPv6 transport descriptor.
    *
    * @return UDPv6 transport descriptor with auto-generated transport ID
    */
   public static TransportDescriptorType createUDPv6Descriptor()
   {
      TransportDescriptorType descriptor = new TransportDescriptorType();
      descriptor.setTransportId(UUID.randomUUID().toString());
      descriptor.setType("UDPv6");
      return descriptor;
   }

   /**
    * Create a TCPv4 transport descriptor.
    *
    * @return TCPv4 transport descriptor with auto-generated transport ID
    */
   public static TransportDescriptorType createTCPv4Descriptor()
   {
      TransportDescriptorType descriptor = new TransportDescriptorType();
      descriptor.setTransportId(UUID.randomUUID().toString());
      descriptor.setType("TCPv4");
      return descriptor;
   }

   /**
    * Create a TCPv6 transport descriptor.
    *
    * @return TCPv6 transport descriptor with auto-generated transport ID
    */
   public static TransportDescriptorType createTCPv6Descriptor()
   {
      TransportDescriptorType descriptor = new TransportDescriptorType();
      descriptor.setTransportId(UUID.randomUUID().toString());
      descriptor.setType("TCPv6");
      return descriptor;
   }

   /**
    * Create a Shared Memory (SHM) transport descriptor.
    * <p>
    * SHM provides zero-copy communication between processes on the same machine.
    * On Windows, check {@link #SHM_TRANSPORT_AVAILABLE_ON_WINDOWS} before use.
    *
    * @return SHM transport descriptor with auto-generated transport ID
    */
   public static TransportDescriptorType createSHMDescriptor()
   {
      TransportDescriptorType descriptor = new TransportDescriptorType();
      descriptor.setTransportId(UUID.randomUUID().toString());
      descriptor.setType("SHM");
      return descriptor;
   }

   /**
    * Create a UDPv4 transport with interface whitelist.
    *
    * @param interfaceWhitelist IP addresses (e.g., "192.168.1.100"), CIDR ranges (e.g., "192.168.1.0/24"),
    *                           or interface names (e.g., "eth0", "wlan0")
    * @return UDPv4 transport descriptor with whitelist applied
    */
   public static TransportDescriptorType createUDPv4Transport(String... interfaceWhitelist)
   {
      TransportDescriptorType descriptor = createUDPv4Descriptor();
      setInterfacesWhitelist(descriptor, interfaceWhitelist);
      return descriptor;
   }

   /**
    * Create a UDPv6 transport with interface whitelist.
    *
    * @param interfaceWhitelist IPv6 addresses (e.g., "fe80::1"), CIDR ranges (e.g., "fe80::/10"),
    *                           or interface names (e.g., "eth0", "wlan0")
    * @return UDPv6 transport descriptor with whitelist applied
    */
   public static TransportDescriptorType createUDPv6Transport(String... interfaceWhitelist)
   {
      TransportDescriptorType descriptor = createUDPv6Descriptor();
      setInterfacesWhitelist(descriptor, interfaceWhitelist);
      return descriptor;
   }

   /**
    * Create a SHM (Shared Memory) transport descriptor.
    *
    * @return SHM transport descriptor
    */
   public static TransportDescriptorType createSHMTransport()
   {
      return createSHMDescriptor();
   }

   /**
    * Configure interface whitelist for a transport descriptor.
    * Restricts communication to specific network interfaces or IP address ranges.
    * <p>
    * Supports IP addresses ("192.168.1.100"), CIDR notation ("192.168.1.0/24"),
    * and interface names ("eth0", "wlan0", "lo"). When an interface name is specified,
    * Fast-DDS binds to all IP addresses on that interface.
    *
    * @param descriptor         transport descriptor to configure (must not be SHM type)
    * @param interfaceWhitelist addresses or interface names to whitelist (null/empty clears whitelist)
    * @throws IllegalArgumentException if called on SHM transport type
    */
   public static void setInterfacesWhitelist(TransportDescriptorType descriptor, String... interfaceWhitelist)
   {
      if (descriptor.getType().equals("SHM"))
      {
         throw new IllegalArgumentException("Cannot set interface whitelist for SHM transport");
      }

      if (interfaceWhitelist == null || interfaceWhitelist.length == 0)
      {
         descriptor.setInterfaceWhiteList(null);
      }
      else
      {
         InterfaceWhiteList whiteList = new InterfaceWhiteList();
         for (String entry : interfaceWhitelist)
         {
            whiteList.getAddressOrInterface().add(entry);
         }
         descriptor.setInterfaceWhiteList(whiteList);
      }
   }

   /**
    * Indicates whether SHM transport is available on Windows.
    * <p>
    * On Windows, SHM requires write access to {@code C:\ProgramData\eprosima\fastdds_interprocess}.
    * This is {@code true} only if running on Windows and the directory is writable.
    * On non-Windows platforms, this is always {@code false}.
    */
   public static final boolean SHM_TRANSPORT_AVAILABLE_ON_WINDOWS;

   static
   {
      if (System.getProperty("os.name").startsWith("Windows"))
      {
         File shmDir = new File("C:\\ProgramData\\eprosima\\fastdds_interprocess");

         if (!shmDir.exists())
         {
            shmDir.mkdirs();
         }

         SHM_TRANSPORT_AVAILABLE_ON_WINDOWS = Files.isDirectory(shmDir.toPath()) && Files.isWritable(shmDir.toPath());
      }
      else
      {
         SHM_TRANSPORT_AVAILABLE_ON_WINDOWS = false;
      }
   }
}
