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
package us.ihmc.fastddsjava.cdr.allocation;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.IDLDoubleSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLIntSequence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The exception messages of CDRBuffer and the IDL sequences, which are built in their cold error classes (so that the
 * hot classes hold no string literal), are unchanged.
 */
public class ColdErrorPathsTest
{
   @Test
   public void testCDRBufferMessages()
   {
      CDRBuffer buffer = new CDRBuffer();
      buffer.writePayloadHeader();
      buffer.writeString("x");
      buffer.writeWString(new StringBuilder("y"));
      buffer.beginRead(buffer.getBufferUnsafe().position());
      buffer.readPayloadHeader();
      assertEquals("CDRBuffer.readString destination StringBuilder is null", assertThrows(NullPointerException.class, () -> buffer.readString(null)).getMessage());
      assertEquals("CDRBuffer.readWString destination StringBuilder is null", assertThrows(NullPointerException.class, () -> buffer.readWString(null)).getMessage());
      assertEquals("Sequence length cannot be negative: -1", assertThrows(IllegalArgumentException.class, () -> buffer.writeSequenceLength(-1)).getMessage());
      assertEquals("CDRBuffer capacity cannot be negative: -2", assertThrows(IllegalArgumentException.class, () -> buffer.ensureRemainingCapacity(-2)).getMessage());
   }

   @Test
   public void testSequenceMessages()
   {
      IDLDoubleSequence doubles = new IDLDoubleSequence(0, 2);
      doubles.add(1.0);
      assertEquals("Index: -1", assertThrows(IndexOutOfBoundsException.class, () -> doubles.putAt(-1, 0.0)).getMessage());
      assertEquals("Index: 3, Size: 1", assertThrows(IndexOutOfBoundsException.class, () -> doubles.remove(3)).getMessage());
      assertEquals("array size exceeds upper bound: 3 > 2", assertThrows(IllegalArgumentException.class, () -> doubles.addAll(new double[2])).getMessage());
      assertEquals("IDLSequence maxSize cannot be negative", assertThrows(IllegalArgumentException.class, () -> new IDLIntSequence(0, -1)).getMessage());
      assertEquals("IDLSequence capacity cannot be larger than maxSize", assertThrows(IllegalArgumentException.class, () -> new IDLIntSequence(3, 2)).getMessage());
      assertEquals("[1.0]", doubles.toString());
      doubles.add(-2.5);
      assertEquals("[1.0, -2.5]", doubles.toString());
   }
}
