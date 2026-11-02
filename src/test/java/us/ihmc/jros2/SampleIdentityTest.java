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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SampleIdentityTest
{
   @Test
   public void testUnknown()
   {
      SampleIdentity identity = new SampleIdentity();
      Assertions.assertTrue(identity.isUnknown());
      Assertions.assertEquals(SampleIdentity.UNKNOWN_SEQUENCE_NUMBER, identity.getSequenceNumber());

      identity.setSequenceNumber(1);
      Assertions.assertFalse(identity.isUnknown());

      identity.setSequenceNumber(SampleIdentity.UNKNOWN_SEQUENCE_NUMBER);
      identity.getWriterGuid().getValue()[15] = 3;
      Assertions.assertFalse(identity.isUnknown());
   }

   @Test
   public void testSetAndEquals()
   {
      byte[] bytes = new byte[16];
      for (int i = 0; i < 16; i++)
         bytes[i] = (byte) i;
      SampleIdentity a = new SampleIdentity(new Guid(bytes), 42);
      SampleIdentity b = new SampleIdentity();
      b.set(a);

      Assertions.assertEquals(a, b);
      Assertions.assertEquals(a.hashCode(), b.hashCode());
      Assertions.assertNotSame(a.getWriterGuid(), b.getWriterGuid());

      b.setSequenceNumber(43);
      Assertions.assertNotEquals(a, b);
      Assertions.assertEquals("000102030405060708090a0b.0c0d0e0f:42", a.toString());
   }
}
