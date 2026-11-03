/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
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
package us.ihmc.jros2;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Guid}: a 16-byte value (12-byte participant prefix and 4-byte entity ID) that endpoints and the matched callbacks
 * hand out; it is compared and hashed by value and printed the way Fast-DDS prints GUIDs ({@code prefix.entityId}).
 */
public class GuidTest
{
   private static byte[] bytes(int first)
   {
      byte[] bytes = new byte[16];
      for (int i = 0; i < bytes.length; ++i)
      {
         bytes[i] = (byte) (first + i);
      }
      return bytes;
   }

   @Test
   public void testValueSemantics()
   {
      Guid a = new Guid(bytes(1));
      Guid b = new Guid(bytes(1));
      Guid c = new Guid(bytes(2));
      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
      assertNotEquals(a, c);
      assertNotEquals(a, null);
      assertNotEquals(a, "0102030405060708090a0b0c.0d0e0f10");
      assertEquals(a, a);

      Set<Guid> set = new HashSet<>();
      set.add(a);
      assertTrue(set.contains(b));
      assertFalse(set.contains(c));

      assertEquals(new Guid(), new Guid(new byte[16]), "a new Guid is all zeros");
   }

   @Test
   public void testCopiesAreIndependent()
   {
      byte[] source = bytes(1);
      Guid guid = new Guid(source);
      source[0] = 99;
      assertEquals(1, guid.getValue()[0], "the constructor copies the bytes");

      Guid copy = new Guid();
      copy.set(guid);
      assertEquals(guid, copy);
      guid.set(bytes(5));
      assertNotEquals(guid, copy, "set(Guid) copies the bytes");
      assertEquals(new Guid(bytes(1)), copy);
   }

   @Test
   public void testOnlySixteenBytesAreAccepted()
   {
      Guid guid = new Guid(bytes(1));
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> guid.set(new byte[15]));
      assertTrue(exception.getMessage().contains("15"), exception.getMessage());
      assertThrows(IllegalArgumentException.class, () -> guid.set(new byte[17]));
      assertThrows(IllegalArgumentException.class, () -> new Guid(new byte[0]));
      assertEquals(new Guid(bytes(1)), guid, "a rejected value changes nothing");
   }

   @Test
   public void testToStringSeparatesPrefixAndEntityId()
   {
      assertEquals("0102030405060708090a0b0c.0d0e0f10", new Guid(bytes(1)).toString());
      byte[] high = new byte[16];
      high[0] = (byte) 0xff;
      high[15] = (byte) 0x80;
      assertEquals("ff0000000000000000000000.00000080", new Guid(high).toString(), "bytes are unsigned, two hex digits each");
   }
}
