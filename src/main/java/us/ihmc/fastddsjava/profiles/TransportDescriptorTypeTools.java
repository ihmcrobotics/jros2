package us.ihmc.fastddsjava.profiles;

import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType.InterfaceWhiteList;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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
    * Result of transport configuration, containing the transports to use and whether builtin transports should be used.
    */
   public static class TransportConfiguration
   {
      private final TransportDescriptorType[] transports;
      private final boolean useBuiltinTransports;
      private final boolean shouldAddToXml;

      public TransportConfiguration(TransportDescriptorType[] transports, boolean useBuiltinTransports, boolean shouldAddToXml)
      {
         this.transports = transports;
         this.useBuiltinTransports = useBuiltinTransports;
         this.shouldAddToXml = shouldAddToXml;
      }

      public TransportDescriptorType[] getTransports()
      {
         return transports;
      }

      public boolean shouldUseBuiltinTransports()
      {
         return useBuiltinTransports;
      }

      public boolean shouldAddToXml()
      {
         return shouldAddToXml;
      }
   }

   /*
    * Atomic counter for transport ID generation
    */
   private static final AtomicLong transportIdCounter = new AtomicLong(0);

   /*
    * Cached transport descriptors (shared across all nodes to avoid Fast-DDS XML conflicts)
    */
   private static final Object transportCacheLock = new Object();
   private static String cachedWhitelistKey = null;
   private static TransportDescriptorType[] cachedTransports = null;

   /*
    * Transport descriptor cache by configuration (type + whitelist)
    */
   private static final Map<String, TransportDescriptorType> descriptorCache = new HashMap<>();

   /**
    * Create a UDPv4 transport descriptor.
    *
    * @return UDPv4 transport descriptor with auto-generated transport ID
    */
   public static TransportDescriptorType createUDPv4Descriptor()
   {
      return createCachedDescriptor("UDPv4", null);
   }

   /**
    * Create a UDPv6 transport descriptor.
    *
    * @return UDPv6 transport descriptor with auto-generated transport ID
    */
   public static TransportDescriptorType createUDPv6Descriptor()
   {
      return createCachedDescriptor("UDPv6", null);
   }

   /**
    * Create a TCPv4 transport descriptor.
    *
    * @return TCPv4 transport descriptor with auto-generated transport ID
    */
   public static TransportDescriptorType createTCPv4Descriptor()
   {
      return createCachedDescriptor("TCPv4", null);
   }

   /**
    * Create a TCPv6 transport descriptor.
    *
    * @return TCPv6 transport descriptor with auto-generated transport ID
    */
   public static TransportDescriptorType createTCPv6Descriptor()
   {
      return createCachedDescriptor("TCPv6", null);
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
      return createCachedDescriptor("SHM", null);
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
      String whitelistKey = interfaceWhitelist != null && interfaceWhitelist.length > 0
            ? String.join(",", interfaceWhitelist) : null;
      return createCachedDescriptor("UDPv4", whitelistKey);
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
      String whitelistKey = interfaceWhitelist != null && interfaceWhitelist.length > 0
            ? String.join(",", interfaceWhitelist) : null;
      return createCachedDescriptor("UDPv6", whitelistKey);
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
    * Create or retrieve cached transport descriptor with atomic ID generation.
    *
    * @param type transport type (UDPv4, UDPv6, TCPv4, TCPv6, SHM)
    * @param whitelistKey comma-separated whitelist entries, or null for no whitelist
    * @return cached or newly created transport descriptor
    */
   private static TransportDescriptorType createCachedDescriptor(String type, String whitelistKey)
   {
      String cacheKey = type + (whitelistKey != null ? ":" + whitelistKey : "");

      synchronized (descriptorCache)
      {
         TransportDescriptorType cached = descriptorCache.get(cacheKey);
         if (cached != null)
         {
            return cached;
         }

         // Create new descriptor
         TransportDescriptorType descriptor = new TransportDescriptorType();
         descriptor.setTransportId("transport_" + transportIdCounter.getAndIncrement());
         descriptor.setType(type);

         // Apply whitelist if provided
         if (whitelistKey != null && !type.equals("SHM"))
         {
            String[] whitelist = whitelistKey.split(",");
            InterfaceWhiteList whiteList = new InterfaceWhiteList();
            for (String entry : whitelist)
            {
               whiteList.getAddressOrInterface().add(entry);
            }
            descriptor.setInterfaceWhiteList(whiteList);
         }

         descriptorCache.put(cacheKey, descriptor);
         return descriptor;
      }
   }

   /**
    * Configure transports based on custom transports and interface whitelist settings.
    * This method handles the logic for determining whether to use builtin transports, custom transports,
    * or create transports from an interface whitelist.
    *
    * @param customTransports   optional custom transport descriptors (null/empty to auto-configure)
    * @param interfaceWhitelist optional interface whitelist (null/empty for no restrictions)
    * @return TransportConfiguration containing the transports to use and builtin transport flag
    */
   public static TransportConfiguration configureTransports(TransportDescriptorType[] customTransports, String[] interfaceWhitelist)
   {
      // Expand and validate whitelist
      if (interfaceWhitelist != null && interfaceWhitelist.length > 0)
      {
         interfaceWhitelist = expandInterfaceWhitelist(interfaceWhitelist);
      }

      boolean hasCustomTransports = customTransports != null && customTransports.length > 0;
      boolean hasWhitelist = interfaceWhitelist != null && interfaceWhitelist.length > 0;

      // Custom transports with optional whitelist
      if (hasCustomTransports)
      {
         if (hasWhitelist)
         {
            for (TransportDescriptorType transport : customTransports)
            {
               if (!transport.getType().equals("SHM"))
               {
                  setInterfacesWhitelist(transport, interfaceWhitelist);
               }
            }
         }
         return new TransportConfiguration(customTransports, false, true);
      }

      // Whitelist without custom transports - create separate transport per interface
      if (hasWhitelist)
      {
         return createWhitelistedTransports(interfaceWhitelist);
      }

      // No custom transports or whitelist - use builtin
      return new TransportConfiguration(null, true, false);
   }

   /**
    * Create separate UDPv4 transport for each interface entry.
    * This is necessary because Jackson XML cannot serialize multiple whitelist entries properly.
    */
   private static TransportConfiguration createWhitelistedTransports(String[] interfaceWhitelist)
   {
      String whitelistKey = String.join(",", interfaceWhitelist);

      synchronized (transportCacheLock)
      {
         boolean isFirstTime = cachedTransports == null;

         if (!whitelistKey.equals(cachedWhitelistKey) || isFirstTime)
         {
            List<TransportDescriptorType> transports = new ArrayList<>();

            // Create one UDPv4 transport per interface
            for (String interfaceEntry : interfaceWhitelist)
            {
               transports.add(createUDPv4Transport(interfaceEntry));
            }

            // Add SHM for local communication
            transports.add(createSHMTransport());

            cachedTransports = transports.toArray(new TransportDescriptorType[0]);
            cachedWhitelistKey = whitelistKey;
         }

         return new TransportConfiguration(cachedTransports, false, isFirstTime);
      }
   }

   /**
    * Configure interface whitelist for a transport descriptor.
    * Restricts communication to specific network interfaces or IP address ranges.
    * <p>
    * Supports IP addresses ("192.168.1.100"), CIDR notation ("192.168.1.0/24"),
    * and interface names ("eth0", "wlan0", "lo"). When an interface name is specified,
    * Fast-DDS binds to all IP addresses on that interface.
    * <p>
    * Note: Due to Jackson XML serialization limitations, only single entries work reliably.
    * For multiple interfaces, create separate transport descriptors using {@link #configureTransports}.
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
    * Expands interface whitelist by converting CIDR notation to IP addresses.
    * Automatically adds loopback (127.0.0.1) for local discovery.
    *
    * @param interfaceWhitelist IP addresses, CIDR ranges, or interface names
    * @return expanded whitelist with CIDR resolved and loopback added
    * @throws IllegalArgumentException if CIDR notation is invalid
    */
   public static String[] expandInterfaceWhitelist(String[] interfaceWhitelist)
   {
      List<String> expanded = new ArrayList<>();
      boolean hasLoopback = false;

      for (String entry : interfaceWhitelist)
      {
         entry = entry.trim();

         if (isLoopback(entry))
         {
            hasLoopback = true;
         }

         if (entry.contains("/"))
         {
            expandCIDR(entry, expanded);
            if (entry.startsWith("127."))
            {
               hasLoopback = true;
            }
         }
         else if (!expanded.contains(entry))
         {
            expanded.add(entry);
         }
      }

      // Auto-add loopback for local discovery
      if (!hasLoopback && !expanded.isEmpty())
      {
         expanded.add("127.0.0.1");
      }

      if (expanded.isEmpty())
      {
         throw new IllegalArgumentException("Interface whitelist expansion resulted in no entries");
      }

      return expanded.toArray(new String[0]);
   }

   private static boolean isLoopback(String entry)
   {
      return entry.equals("127.0.0.1") || entry.equals("localhost") || entry.equals("lo") || entry.startsWith("127.");
   }

   private static void expandCIDR(String cidr, List<String> result)
   {
      String[] parts = cidr.split("/");
      if (parts.length != 2)
      {
         throw new IllegalArgumentException("Invalid CIDR notation: " + cidr);
      }

      try
      {
         InetAddress networkAddress = InetAddress.getByName(parts[0]);
         int prefixLength = Integer.parseInt(parts[1]);

         if (prefixLength < 0 || prefixLength > 32)
         {
            throw new IllegalArgumentException("Invalid CIDR prefix: " + prefixLength + " (must be 0-32)");
         }

         findIPsInCIDR(networkAddress, prefixLength, result);
      }
      catch (UnknownHostException e)
      {
         throw new IllegalArgumentException("Invalid IP in CIDR: " + parts[0], e);
      }
      catch (NumberFormatException e)
      {
         throw new IllegalArgumentException("Invalid CIDR prefix: " + parts[1], e);
      }
      catch (SocketException e)
      {
         throw new IllegalArgumentException("Failed to enumerate interfaces for CIDR: " + cidr, e);
      }
   }

   private static void findIPsInCIDR(InetAddress networkAddress, int prefixLength, List<String> result) throws SocketException
   {
      byte[] networkBytes = networkAddress.getAddress();
      int mask = 0xffffffff << (32 - prefixLength);
      byte[] maskBytes = {(byte) (mask >>> 24), (byte) (mask >>> 16), (byte) (mask >>> 8), (byte) mask};

      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements())
      {
         Enumeration<InetAddress> addresses = interfaces.nextElement().getInetAddresses();
         while (addresses.hasMoreElements())
         {
            InetAddress address = addresses.nextElement();
            if (address.getAddress().length == 4 && isInRange(address.getAddress(), networkBytes, maskBytes))
            {
               String ip = address.getHostAddress();
               if (!result.contains(ip))
               {
                  result.add(ip);
               }
            }
         }
      }
   }

   private static boolean isInRange(byte[] address, byte[] network, byte[] mask)
   {
      for (int i = 0; i < 4; i++)
      {
         if ((network[i] & mask[i]) != (address[i] & mask[i]))
         {
            return false;
         }
      }
      return true;
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
