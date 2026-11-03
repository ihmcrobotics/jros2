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
package us.ihmc.fastddsjava.cdr.idl;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.CDRSerializable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bulk appends of the reference sequences ({@link IDLStringSequence}, {@link IDLWStringSequence}, {@link IDLObjectSequence}):
 * the content after {@code addAll}, the upper bound (nothing is added when it would be exceeded), and reuse of the element
 * slots that a later {@code addAll(Collection)} or {@code add(int)} writes into.
 */
public class IDLReferenceSequenceBulkTest
{
   @Test
   public void testStringSequenceAddAll()
   {
      for (IDLStringSequence sequence : new IDLStringSequence[] {new IDLStringSequence(1, 4), new IDLWStringSequence(1, 4)})
      {
         String type = sequence.getClass().getSimpleName();
         sequence.add().append("first");
         sequence.addAll(List.of("a", "bc"));
         assertArrayEquals(new String[] {"first", "a", "bc"}, sequence.toStringArray(), type);

         IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> sequence.addAll(List.of("x", "y")), type);
         assertTrue(exception.getMessage().contains("5 > 4"), exception.getMessage());
         assertThrows(IllegalArgumentException.class, () -> sequence.addAll(new StringBuilder[] {new StringBuilder("x"), new StringBuilder("y")}), type);
         assertEquals(3, sequence.size(), type + ": nothing is added when the bound would be exceeded");

         StringBuilder last = new StringBuilder("last");
         sequence.addAll(new StringBuilder[] {last});
         assertSame(last, sequence.get(3), type + ": addAll(StringBuilder[]) keeps the given builders");
         assertArrayEquals(new String[] {"first", "a", "bc", "last"}, sequence.toStringArray(), type);

         // Slots are reused: after clear, addAll(Collection) writes into the existing builders
         StringBuilder firstSlot = sequence.get(0);
         sequence.clear();
         sequence.addAll(List.of("reused"));
         assertSame(firstSlot, sequence.get(0), type + ": the slot builder is reused");
         assertEquals("reused", sequence.getAsString(0), type);

         // add(int) on an existing slot grows it to the requested capacity
         sequence.clear();
         StringBuilder slot = sequence.add(1000);
         assertSame(firstSlot, slot, type);
         assertTrue(slot.capacity() >= 1000, type);
      }
   }

   @Test
   public void testStringSequenceRoundTripAfterAddAll()
   {
      IDLStringSequence strings = new IDLStringSequence();
      strings.addAll(List.of("x", "yz", ""));
      strings.addAll(new StringBuilder[] {new StringBuilder("café")});
      CDRBuffer buffer = new CDRBuffer();
      buffer.writePayloadHeader();
      strings.serialize(buffer);
      int size = buffer.getBufferUnsafe().position();
      assertEquals(size - 4, strings.calculateSizeBytes(0));

      IDLStringSequence decoded = new IDLStringSequence();
      buffer.beginRead(size);
      buffer.readPayloadHeader();
      decoded.deserialize(buffer);
      assertArrayEquals(new String[] {"x", "yz", "", "café"}, decoded.toStringArray());
   }

   /** A minimal serializable element. */
   public static class Element implements CDRSerializable
   {
      int value;

      public Element()
      {
      }

      Element(int value)
      {
         this.value = value;
      }

      @Override
      public int calculateSizeBytes(int currentAlignment)
      {
         return 4 + CDRBuffer.alignment(currentAlignment, 4);
      }

      @Override
      public void serialize(CDRBuffer buffer)
      {
         buffer.writeInt(value);
      }

      @Override
      public void deserialize(CDRBuffer buffer)
      {
         value = buffer.readInt();
      }
   }

   /** No public no-argument constructor: the sequence cannot create elements of this type. */
   public static class NotInstantiable extends Element
   {
      public NotInstantiable(int value)
      {
         super(value);
      }
   }

   @Test
   public void testObjectSequenceAddAll()
   {
      IDLObjectSequence<Element> sequence = new IDLObjectSequence<>(1, 3, Element.class);
      sequence.add().value = 1;
      Element two = new Element(2);
      sequence.addAll(new Element[] {two});
      sequence.addAll(List.of(new Element(3)));
      assertEquals(3, sequence.size());
      assertSame(two, sequence.get(1), "addAll stores the given elements");
      assertEquals(3, sequence.getLast().value);

      assertThrows(IllegalArgumentException.class, () -> sequence.addAll(new Element[] {new Element(4)}));
      assertThrows(IllegalArgumentException.class, () -> sequence.addAll(List.of(new Element(4))));
      assertEquals(3, sequence.size(), "nothing is added when the bound would be exceeded");
      assertEquals(3, sequence.getLast().value);

      CDRBuffer buffer = new CDRBuffer();
      buffer.writePayloadHeader();
      sequence.serialize(buffer);
      int size = buffer.getBufferUnsafe().position();
      IDLObjectSequence<Element> decoded = new IDLObjectSequence<>(Element.class);
      buffer.beginRead(size);
      buffer.readPayloadHeader();
      decoded.deserialize(buffer);
      assertEquals(3, decoded.size());
      for (int i = 0; i < 3; ++i)
      {
         assertEquals(i + 1, decoded.get(i).value);
      }
   }

   @Test
   public void testElementsThatCannotBeCreated()
   {
      IDLObjectSequence<NotInstantiable> sequence = new IDLObjectSequence<>(NotInstantiable.class);
      RuntimeException exception = assertThrows(RuntimeException.class, sequence::add);
      assertTrue(exception.getMessage().contains(NotInstantiable.class.getName()), exception.getMessage());
      assertInstanceOf(NoSuchMethodException.class, exception.getCause());
      assertEquals(0, sequence.size());

      // Given elements can still be added
      sequence.addAll(List.of(new NotInstantiable(5)));
      assertEquals(5, sequence.getLast().value);
   }
}
