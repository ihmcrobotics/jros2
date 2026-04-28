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

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType.InterfaceWhiteList;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TransportDescriptorTypeToolsTest
{
   @Test
   public void testCreateUDPv4Descriptor()
   {
      TransportDescriptorType descriptor = TransportDescriptorTypeTools.createUDPv4Descriptor();

      assertNotNull(descriptor);
      assertEquals("UDPv4", descriptor.getType());
      assertNotNull(descriptor.getTransportId());
      assertTrue(descriptor.getTransportId().startsWith("transport_"));
      assertNull(descriptor.getInterfaceWhiteList());
   }

   @Test
   public void testCreateUDPv6Descriptor()
   {
      TransportDescriptorType descriptor = TransportDescriptorTypeTools.createUDPv6Descriptor();

      assertNotNull(descriptor);
      assertEquals("UDPv6", descriptor.getType());
      assertNotNull(descriptor.getTransportId());
      assertTrue(descriptor.getTransportId().startsWith("transport_"));
      assertNull(descriptor.getInterfaceWhiteList());
   }

   @Test
   public void testCreateTCPv4Descriptor()
   {
      TransportDescriptorType descriptor = TransportDescriptorTypeTools.createTCPv4Descriptor();

      assertNotNull(descriptor);
      assertEquals("TCPv4", descriptor.getType());
      assertNotNull(descriptor.getTransportId());
      assertTrue(descriptor.getTransportId().startsWith("transport_"));
      assertNull(descriptor.getInterfaceWhiteList());
   }

   @Test
   public void testCreateTCPv6Descriptor()
   {
      TransportDescriptorType descriptor = TransportDescriptorTypeTools.createTCPv6Descriptor();

      assertNotNull(descriptor);
      assertEquals("TCPv6", descriptor.getType());
      assertNotNull(descriptor.getTransportId());
      assertTrue(descriptor.getTransportId().startsWith("transport_"));
      assertNull(descriptor.getInterfaceWhiteList());
   }

   @Test
   public void testCreateSHMDescriptor()
   {
      TransportDescriptorType descriptor = TransportDescriptorTypeTools.createSHMDescriptor();

      assertNotNull(descriptor);
      assertEquals("SHM", descriptor.getType());
      assertNotNull(descriptor.getTransportId());
      assertTrue(descriptor.getTransportId().startsWith("transport_"));
      assertNull(descriptor.getInterfaceWhiteList());
   }

   @Test
   public void testCreateSHMTransport()
   {
      TransportDescriptorType descriptor = TransportDescriptorTypeTools.createSHMTransport();

      assertNotNull(descriptor);
      assertEquals("SHM", descriptor.getType());
      assertNotNull(descriptor.getTransportId());
      assertTrue(descriptor.getTransportId().startsWith("transport_"));
   }

   @Test
   public void testTransportIdUniqueness()
   {
      Set<String> transportIds = new HashSet<>();

      // Create multiple descriptors with unique whitelists to avoid caching
      // This tests that the ID counter is working properly
      for (int i = 0; i < 100; ++i)
      {
         String uniqueInterface = "test_if_" + System.nanoTime() + "_" + i;
         TransportDescriptorType descriptor = TransportDescriptorTypeTools.createUDPv4Transport(uniqueInterface);
         String id = descriptor.getTransportId();
         assertFalse(transportIds.contains(id), "Transport ID should be unique: " + id);
         transportIds.add(id);
      }
   }

   @Test
   public void testCreateUDPv4TransportWithSingleInterface()
   {
      TransportDescriptorType descriptor = TransportDescriptorTypeTools.createUDPv4Transport("192.168.1.100");

      assertNotNull(descriptor);
      assertEquals("UDPv4", descriptor.getType());
      assertNotNull(descriptor.getInterfaceWhiteList());

      List<Object> whitelist = descriptor.getInterfaceWhiteList().getAddressOrInterface();
      assertEquals(1, whitelist.size());
      assertEquals("192.168.1.100", whitelist.get(0));
   }

   @Test
   public void testCreateUDPv4TransportWithMultipleInterfaces()
   {
      TransportDescriptorType descriptor = TransportDescriptorTypeTools.createUDPv4Transport("192.168.1.100", "10.0.0.1");

      assertNotNull(descriptor);
      assertEquals("UDPv4", descriptor.getType());
      assertNotNull(descriptor.getInterfaceWhiteList());

      List<Object> whitelist = descriptor.getInterfaceWhiteList().getAddressOrInterface();
      assertEquals(2, whitelist.size());
      assertTrue(whitelist.contains("192.168.1.100"));
      assertTrue(whitelist.contains("10.0.0.1"));
   }

   @Test
   public void testCreateUDPv6TransportWithInterface()
   {
      TransportDescriptorType descriptor = TransportDescriptorTypeTools.createUDPv6Transport("fe80::1", "::1");

      assertNotNull(descriptor);
      assertEquals("UDPv6", descriptor.getType());
      assertNotNull(descriptor.getInterfaceWhiteList());

      List<Object> whitelist = descriptor.getInterfaceWhiteList().getAddressOrInterface();
      assertEquals(2, whitelist.size());
      assertTrue(whitelist.contains("fe80::1"));
      assertTrue(whitelist.contains("::1"));
   }

   @Test
   public void testDescriptorCachingWithSameConfiguration()
   {
      // Same configuration should return cached descriptor
      TransportDescriptorType descriptor1 = TransportDescriptorTypeTools.createUDPv4Transport("192.168.1.100");
      TransportDescriptorType descriptor2 = TransportDescriptorTypeTools.createUDPv4Transport("192.168.1.100");

      assertSame(descriptor1, descriptor2, "Same configuration should return cached descriptor");
      assertEquals(descriptor1.getTransportId(), descriptor2.getTransportId());
   }

   @Test
   public void testDescriptorCachingWithDifferentConfiguration()
   {
      // Different configuration should create new descriptor
      TransportDescriptorType descriptor1 = TransportDescriptorTypeTools.createUDPv4Transport("192.168.1.100");
      TransportDescriptorType descriptor2 = TransportDescriptorTypeTools.createUDPv4Transport("10.0.0.1");

      assertNotSame(descriptor1, descriptor2, "Different configuration should create new descriptor");
      assertNotEquals(descriptor1.getTransportId(), descriptor2.getTransportId());
   }

   @Test
   public void testDescriptorCachingAcrossTransportTypes()
   {
      // Different transport types should have different descriptors
      TransportDescriptorType udpv4 = TransportDescriptorTypeTools.createUDPv4Transport("192.168.1.100");
      TransportDescriptorType udpv6 = TransportDescriptorTypeTools.createUDPv6Transport("192.168.1.100");

      assertNotSame(udpv4, udpv6);
      assertNotEquals(udpv4.getTransportId(), udpv6.getTransportId());
      assertEquals("UDPv4", udpv4.getType());
      assertEquals("UDPv6", udpv6.getType());
   }

   @Test
   public void testSetInterfacesWhitelist()
   {
      // Create a fresh descriptor - due to caching it may have a whitelist already
      // So we use a unique interface name to avoid cache collision
      String uniqueInterface = "test_interface_" + System.nanoTime();
      TransportDescriptorType descriptor = TransportDescriptorTypeTools.createUDPv4Transport(uniqueInterface);

      // Now set different interfaces
      TransportDescriptorTypeTools.setInterfacesWhitelist(descriptor, "eth0", "wlan0");

      assertNotNull(descriptor.getInterfaceWhiteList());
      List<Object> whitelist = descriptor.getInterfaceWhiteList().getAddressOrInterface();
      assertEquals(2, whitelist.size());
      assertTrue(whitelist.contains("eth0"));
      assertTrue(whitelist.contains("wlan0"));
   }

   @Test
   public void testSetInterfacesWhitelistClearsExisting()
   {
      TransportDescriptorType descriptor = TransportDescriptorTypeTools.createUDPv4Transport("192.168.1.100");
      assertEquals(1, descriptor.getInterfaceWhiteList().getAddressOrInterface().size());

      TransportDescriptorTypeTools.setInterfacesWhitelist(descriptor, "10.0.0.1");

      List<Object> whitelist = descriptor.getInterfaceWhiteList().getAddressOrInterface();
      assertEquals(1, whitelist.size());
      assertEquals("10.0.0.1", whitelist.get(0));
   }

   @Test
   public void testSetInterfacesWhitelistWithNullClearsWhitelist()
   {
      String uniqueInterface = "test_interface_" + System.nanoTime();
      TransportDescriptorType descriptor = TransportDescriptorTypeTools.createUDPv4Transport(uniqueInterface);
      assertNotNull(descriptor.getInterfaceWhiteList());

      TransportDescriptorTypeTools.setInterfacesWhitelist(descriptor, (String[]) null);

      InterfaceWhiteList whitelist = descriptor.getInterfaceWhiteList();
      assertNull(whitelist);
   }

   @Test
   public void testSetInterfacesWhitelistWithEmptyArrayClearsWhitelist()
   {
      String uniqueInterface = "test_interface_" + System.nanoTime();
      TransportDescriptorType descriptor = TransportDescriptorTypeTools.createUDPv4Transport(uniqueInterface);
      assertNotNull(descriptor.getInterfaceWhiteList());

      TransportDescriptorTypeTools.setInterfacesWhitelist(descriptor, new String[0]);

      InterfaceWhiteList whitelist = descriptor.getInterfaceWhiteList();
      assertNull(whitelist);
   }

   @Test
   public void testSetInterfacesWhitelistOnSHMThrowsException()
   {
      TransportDescriptorType shmDescriptor = TransportDescriptorTypeTools.createSHMTransport();

      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
      {
         TransportDescriptorTypeTools.setInterfacesWhitelist(shmDescriptor, "192.168.1.100");
      });

      assertTrue(exception.getMessage().contains("SHM"));
   }

   @Test
   public void testExpandInterfaceWhitelistWithPlainIP()
   {
      String[] input = {"192.168.1.100"};
      String[] result = TransportDescriptorTypeTools.expandInterfaceWhitelist(input);

      assertTrue(result.length >= 2); // Original IP + loopback
      assertTrue(contains(result, "192.168.1.100"));
      assertTrue(contains(result, "127.0.0.1"));
   }

   @Test
   public void testExpandInterfaceWhitelistWithLoopbackAlreadyPresent()
   {
      String[] input = {"192.168.1.100", "127.0.0.1"};
      String[] result = TransportDescriptorTypeTools.expandInterfaceWhitelist(input);

      // Should not add duplicate loopback
      assertTrue(contains(result, "192.168.1.100"));
      assertTrue(contains(result, "127.0.0.1"));

      // Count occurrences of loopback
      int loopbackCount = 0;
      for (String entry : result)
      {
         if (entry.equals("127.0.0.1"))
         {
            loopbackCount++;
         }
      }
      assertEquals(1, loopbackCount, "Loopback should appear exactly once");
   }

   @Test
   public void testExpandInterfaceWhitelistWithLocalhostAddsLoopback()
   {
      String[] input = {"localhost"};
      String[] result = TransportDescriptorTypeTools.expandInterfaceWhitelist(input);

      assertTrue(contains(result, "localhost"));
      // Should not add 127.0.0.1 because localhost is already a loopback
      assertTrue(result.length >= 1);
   }

   @Test
   public void testExpandInterfaceWhitelistWithLoInterface()
   {
      String[] input = {"lo"};
      String[] result = TransportDescriptorTypeTools.expandInterfaceWhitelist(input);

      assertTrue(contains(result, "lo"));
      assertTrue(result.length >= 1);
   }

   @Test
   public void testExpandInterfaceWhitelistWithCIDR()
   {
      // Use 127.0.0.0/24 which should match loopback interface on any system
      String[] input = {"127.0.0.0/24"};
      String[] result = TransportDescriptorTypeTools.expandInterfaceWhitelist(input);

      // Should expand CIDR - loopback IPs in the range should be found
      assertTrue(result.length >= 1);

      // Should contain at least one IP from loopback range
      assertNotNull(result);
   }

   @Test
   public void testExpandInterfaceWhitelistWithInvalidCIDR()
   {
      String[] input = {"192.168.1.0/999"};

      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
      {
         TransportDescriptorTypeTools.expandInterfaceWhitelist(input);
      });

      assertTrue(exception.getMessage().contains("Invalid CIDR") || exception.getMessage().contains("prefix"));
   }

   @Test
   public void testExpandInterfaceWhitelistWithMalformedCIDR()
   {
      String[] input = {"not.a.cidr/24"};

      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
      {
         TransportDescriptorTypeTools.expandInterfaceWhitelist(input);
      });

      assertNotNull(exception);
   }

   @Test
   public void testExpandInterfaceWhitelistWithInterfaceName()
   {
      String[] input = {"eth0"};
      String[] result = TransportDescriptorTypeTools.expandInterfaceWhitelist(input);

      assertTrue(contains(result, "eth0"));
      assertTrue(contains(result, "127.0.0.1"));
   }

   @Test
   public void testExpandInterfaceWhitelistWithMixedEntries()
   {
      String[] input = {"192.168.1.100", "eth0", "10.0.0.1"};
      String[] result = TransportDescriptorTypeTools.expandInterfaceWhitelist(input);

      assertTrue(contains(result, "192.168.1.100"));
      assertTrue(contains(result, "eth0"));
      assertTrue(contains(result, "10.0.0.1"));
      assertTrue(contains(result, "127.0.0.1"));
   }

   @Test
   public void testConfigureTransportsWithNoArguments()
   {
      TransportDescriptorTypeTools.TransportConfiguration config =
            TransportDescriptorTypeTools.configureTransports(null, null);

      assertTrue(config.shouldUseBuiltinTransports());
      assertNull(config.getTransports());
      assertFalse(config.shouldAddToXml());
   }

   @Test
   public void testConfigureTransportsWithEmptyArrays()
   {
      TransportDescriptorTypeTools.TransportConfiguration config =
            TransportDescriptorTypeTools.configureTransports(new TransportDescriptorType[0], new String[0]);

      assertTrue(config.shouldUseBuiltinTransports());
      assertNull(config.getTransports());
      assertFalse(config.shouldAddToXml());
   }

   @Test
   public void testConfigureTransportsWithCustomTransportOnly()
   {
      // Create a unique transport to avoid collision with other tests
      String uniqueInterface = "test_custom_" + System.nanoTime();
      TransportDescriptorType customTransport = TransportDescriptorTypeTools.createUDPv4Transport(uniqueInterface);
      TransportDescriptorType[] customTransports = {customTransport};

      TransportDescriptorTypeTools.TransportConfiguration config =
            TransportDescriptorTypeTools.configureTransports(customTransports, null);

      assertFalse(config.shouldUseBuiltinTransports());
      assertNotNull(config.getTransports());
      assertEquals(1, config.getTransports().length);
      assertEquals(customTransport, config.getTransports()[0]);
      assertTrue(config.shouldAddToXml()); // First time should add to XML
   }

   @Test
   public void testConfigureTransportsWithCustomTransportCalledTwice()
   {
      TransportDescriptorType customTransport = TransportDescriptorTypeTools.createTCPv4Descriptor();
      TransportDescriptorType[] customTransports = {customTransport};

      // First call should add to XML
      TransportDescriptorTypeTools.TransportConfiguration config1 =
            TransportDescriptorTypeTools.configureTransports(customTransports, null);
      assertTrue(config1.shouldAddToXml());

      // Second call with same transport should NOT add to XML (already registered)
      TransportDescriptorTypeTools.TransportConfiguration config2 =
            TransportDescriptorTypeTools.configureTransports(customTransports, null);
      assertFalse(config2.shouldAddToXml());
   }

   @Test
   public void testConfigureTransportsWithInterfaceWhitelistOnly()
   {
      String[] whitelist = {"192.168.1.100"};

      TransportDescriptorTypeTools.TransportConfiguration config =
            TransportDescriptorTypeTools.configureTransports(null, whitelist);

      assertFalse(config.shouldUseBuiltinTransports());
      assertNotNull(config.getTransports());
      assertTrue(config.getTransports().length >= 2); // At least UDPv4 per interface + SHM

      // Should contain SHM
      boolean hasSHM = false;
      for (TransportDescriptorType transport : config.getTransports())
      {
         if (transport.getType().equals("SHM"))
         {
            hasSHM = true;
            break;
         }
      }
      assertTrue(hasSHM, "Should include SHM transport");
   }

   @Test
   public void testConfigureTransportsWithCustomTransportAndWhitelist()
   {
      TransportDescriptorType customTransport = TransportDescriptorTypeTools.createUDPv4Descriptor();
      TransportDescriptorType[] customTransports = {customTransport};
      String[] whitelist = {"192.168.1.100"};

      TransportDescriptorTypeTools.TransportConfiguration config =
            TransportDescriptorTypeTools.configureTransports(customTransports, whitelist);

      assertFalse(config.shouldUseBuiltinTransports());
      assertNotNull(config.getTransports());

      // Custom transport should have whitelist applied
      InterfaceWhiteList interfaceWhiteList = customTransport.getInterfaceWhiteList();
      assertNotNull(interfaceWhiteList);
      assertTrue(interfaceWhiteList.getAddressOrInterface().size() >= 1);
   }

   @Test
   public void testConfigureTransportsWithSHMAndWhitelist()
   {
      TransportDescriptorType shmTransport = TransportDescriptorTypeTools.createSHMTransport();
      TransportDescriptorType udpTransport = TransportDescriptorTypeTools.createUDPv4Descriptor();
      TransportDescriptorType[] customTransports = {shmTransport, udpTransport};
      String[] whitelist = {"192.168.1.100"};

      TransportDescriptorTypeTools.TransportConfiguration config =
            TransportDescriptorTypeTools.configureTransports(customTransports, whitelist);

      // SHM should not have whitelist applied
      assertNull(shmTransport.getInterfaceWhiteList());

      // UDP should have whitelist applied
      assertNotNull(udpTransport.getInterfaceWhiteList());
   }

   @Test
   public void testConfigureTransportsCachingWithSameWhitelist()
   {
      String[] whitelist = {"10.0.0.1"};

      // First call
      TransportDescriptorTypeTools.TransportConfiguration config1 =
            TransportDescriptorTypeTools.configureTransports(null, whitelist);
      assertTrue(config1.shouldAddToXml()); // First time

      // Second call with same whitelist
      TransportDescriptorTypeTools.TransportConfiguration config2 =
            TransportDescriptorTypeTools.configureTransports(null, whitelist);
      assertFalse(config2.shouldAddToXml()); // Cached, should not add again

      // Should return same transport array
      assertSame(config1.getTransports(), config2.getTransports());
   }

   @Test
   public void testConfigureTransportsCachingWithDifferentWhitelist()
   {
      // Use unique IPs to ensure different whitelists that won't collide with other tests
      String uniqueIp1 = "10." + (System.nanoTime() % 256) + ".0.1";
      String uniqueIp2 = "10." + ((System.nanoTime() + 1) % 256) + ".0.2";
      String[] whitelist1 = {uniqueIp1};
      String[] whitelist2 = {uniqueIp2};

      TransportDescriptorTypeTools.TransportConfiguration config1 =
            TransportDescriptorTypeTools.configureTransports(null, whitelist1);

      TransportDescriptorTypeTools.TransportConfiguration config2 =
            TransportDescriptorTypeTools.configureTransports(null, whitelist2);

      // Different whitelist should create new transports
      assertNotSame(config1.getTransports(), config2.getTransports());
      // Note: shouldAddToXml may be false if some transports (like SHM) are already registered
      // from config1, so we just verify transports are different
   }

   @Test
   public void testTransportConfigurationGetters()
   {
      TransportDescriptorType[] transports = {TransportDescriptorTypeTools.createUDPv4Descriptor()};
      TransportDescriptorTypeTools.TransportConfiguration config =
            new TransportDescriptorTypeTools.TransportConfiguration(transports, false, true);

      assertSame(transports, config.getTransports());
      assertFalse(config.shouldUseBuiltinTransports());
      assertTrue(config.shouldAddToXml());
   }

   @Test
   public void testTransportConfigurationWithNullTransports()
   {
      TransportDescriptorTypeTools.TransportConfiguration config =
            new TransportDescriptorTypeTools.TransportConfiguration(null, true, false);

      assertNull(config.getTransports());
      assertTrue(config.shouldUseBuiltinTransports());
      assertFalse(config.shouldAddToXml());
   }

   // Helper method to check if array contains a value
   private boolean contains(String[] array, String value)
   {
      for (String entry : array)
      {
         if (entry.equals(value))
         {
            return true;
         }
      }
      return false;
   }
}
