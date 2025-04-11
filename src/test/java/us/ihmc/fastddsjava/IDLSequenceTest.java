package us.ihmc.fastddsjava;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.idl.IDLByteSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLDoubleSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLFloatSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLObjectSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLShortSequence;
import us.ihmc.jros2.testmessages.Bool;

import static org.junit.jupiter.api.Assertions.*;

public class IDLSequenceTest
{
   @Test
   public void testIDLByteSequence()
   {
      final int initialCapacity = 8;

      IDLByteSequence sequence = new IDLByteSequence(initialCapacity);

      // The sequence should have no elements
      assertEquals(0, sequence.elements());

      // It's capacity should be the requested initial capacity
      assertEquals(initialCapacity, sequence.capacity());

      // Add initialCapacity number of elements to the sequence
      for (int i = 0; i < initialCapacity; ++i)
      {
         sequence.add((byte) i);
      }

      // It should have initialCapacity elements
      assertEquals(initialCapacity, sequence.elements());

      // The capacity should not have changed
      assertEquals(initialCapacity, sequence.capacity());

      // Make sure elementSizeBytes is correct
      assertEquals(Byte.BYTES, sequence.elementSizeBytes(0));

      // Add one more element (going past the initial capacity)
      sequence.add((byte) initialCapacity);

      // The element should have been added
      assertEquals(initialCapacity + 1, sequence.elements());

      // Current capacity should be greater than the initial capacity
      assertTrue(sequence.capacity() > initialCapacity);

      // Make sure the elements we added are stored correctly
      for (int i = 0; i < sequence.elements(); ++i)
      {
         assertEquals((byte) i, sequence.get(i));
      }

      int originalCapacity = sequence.capacity();
      int originalElements = sequence.elements();

      // Make a copy of the sequence
      IDLByteSequence copySequence = new IDLByteSequence();
      copySequence.set(sequence);

      // Make sure the original wasn't affected by the copy
      assertEquals(originalCapacity, sequence.capacity());
      assertEquals(originalElements, sequence.elements());

      // The copy should have same number of elements as the original
      assertEquals(copySequence.elements(), sequence.elements());

      // Clear the original sequence
      sequence.clear();

      // Make sure it has no elements, but capacity should not be affected
      assertEquals(0, sequence.elements());
      assertEquals(originalCapacity, sequence.capacity());

      // Make sure the copy wasn't affected by changes to the original
      assertEquals(originalElements, copySequence.elements());

      // Create a new sequence with no initial buffer
      IDLByteSequence emptySequence = new IDLByteSequence();

      // Ensure methods work on empty sequences
      assertEquals(0, emptySequence.capacity());
      assertEquals(0, emptySequence.elements());
      assertDoesNotThrow(emptySequence::clear);
   }

   @Test
   public void testIDLDoubleSequence()
   {
      final int initialCapacity = 8;

      IDLDoubleSequence sequence = new IDLDoubleSequence(initialCapacity);

      // The sequence should have no elements
      assertEquals(0, sequence.elements());

      // It's capacity should be the requested initial capacity
      assertEquals(initialCapacity, sequence.capacity());

      // Add initialCapacity number of elements to the sequence
      for (int i = 0; i < initialCapacity; ++i)
      {
         sequence.add(i);
      }

      // It should have initialCapacity elements
      assertEquals(initialCapacity, sequence.elements());

      // The capacity should not have changed
      assertEquals(initialCapacity, sequence.capacity());

      // Make sure elementSizeBytes is correct
      assertEquals(Double.BYTES, sequence.elementSizeBytes(0));

      // Add one more element (going past the initial capacity)
      sequence.add(initialCapacity);

      // The element should have been added
      assertEquals(initialCapacity + 1, sequence.elements());

      // Current capacity should be greater than the initial capacity
      assertTrue(sequence.capacity() > initialCapacity);

      // Make sure the elements we added are stored correctly
      for (int i = 0; i < sequence.elements(); ++i)
      {
         assertEquals(i, sequence.get(i));
      }

      int originalCapacity = sequence.capacity();
      int originalElements = sequence.elements();

      // Make a copy of the sequence
      IDLDoubleSequence copySequence = new IDLDoubleSequence();
      copySequence.set(sequence);

      // Make sure the original wasn't affected by the copy
      assertEquals(originalCapacity, sequence.capacity());
      assertEquals(originalElements, sequence.elements());

      // The copy should have same number of elements as the original
      assertEquals(copySequence.elements(), sequence.elements());

      // Clear the original sequence
      sequence.clear();

      // Make sure it has no elements, but capacity should not be affected
      assertEquals(0, sequence.elements());
      assertEquals(originalCapacity, sequence.capacity());

      // Make sure the copy wasn't affected by changes to the original
      assertEquals(originalElements, copySequence.elements());

      // Create a new sequence with no initial buffer
      IDLDoubleSequence emptySequence = new IDLDoubleSequence();

      // Ensure methods work on empty sequences
      assertEquals(0, emptySequence.capacity());
      assertEquals(0, emptySequence.elements());
      assertDoesNotThrow(emptySequence::clear);
   }

   @Test
   public void testIDLFloatSequence()
   {
      final int initialCapacity = 8;

      IDLFloatSequence sequence = new IDLFloatSequence(initialCapacity);

      // The sequence should have no elements
      assertEquals(0, sequence.elements());

      // It's capacity should be the requested initial capacity
      assertEquals(initialCapacity, sequence.capacity());

      // Add initialCapacity number of elements to the sequence
      for (int i = 0; i < initialCapacity; ++i)
      {
         sequence.add(i);
      }

      // It should have initialCapacity elements
      assertEquals(initialCapacity, sequence.elements());

      // The capacity should not have changed
      assertEquals(initialCapacity, sequence.capacity());

      // Make sure elementSizeBytes is correct
      assertEquals(Float.BYTES, sequence.elementSizeBytes(0));

      // Add one more element (going past the initial capacity)
      sequence.add(initialCapacity);

      // The element should have been added
      assertEquals(initialCapacity + 1, sequence.elements());

      // Current capacity should be greater than the initial capacity
      assertTrue(sequence.capacity() > initialCapacity);

      // Make sure the elements we added are stored correctly
      for (int i = 0; i < sequence.elements(); ++i)
      {
         assertEquals((float) i, sequence.get(i));
      }

      int originalCapacity = sequence.capacity();
      int originalElements = sequence.elements();

      // Make a copy of the sequence
      IDLFloatSequence copySequence = new IDLFloatSequence();
      copySequence.set(sequence);

      // Make sure the original wasn't affected by the copy
      assertEquals(originalCapacity, sequence.capacity());
      assertEquals(originalElements, sequence.elements());

      // The copy should have same number of elements as the original
      assertEquals(copySequence.elements(), sequence.elements());

      // Clear the original sequence
      sequence.clear();

      // Make sure it has no elements, but capacity should not be affected
      assertEquals(0, sequence.elements());
      assertEquals(originalCapacity, sequence.capacity());

      // Make sure the copy wasn't affected by changes to the original
      assertEquals(originalElements, copySequence.elements());

      // Create a new sequence with no initial buffer
      IDLFloatSequence emptySequence = new IDLFloatSequence();

      // Ensure methods work on empty sequences
      assertEquals(0, emptySequence.capacity());
      assertEquals(0, emptySequence.elements());
      assertDoesNotThrow(emptySequence::clear);
   }

   @Test
   public void testIDLObjectSequence()
   {
      final int initialCapacity = 8;

      IDLObjectSequence<Bool> sequence = new IDLObjectSequence<>(initialCapacity, Bool.class);

      // The sequence should have no elements
      assertEquals(0, sequence.elements());

      // It's capacity should be the requested initial capacity
      assertEquals(initialCapacity, sequence.capacity());

      // Add initialCapacity number of elements to the sequence
      for (int i = 0; i < initialCapacity; ++i)
      {
         sequence.add().setData(i % 2 == 0);
      }

      // It should have initialCapacity elements
      assertEquals(initialCapacity, sequence.elements());

      // The capacity should not have changed
      assertEquals(initialCapacity, sequence.capacity());

      // Make sure elementSizeBytes is correct
      assertEquals(new Bool().calculateSizeBytes(), sequence.elementSizeBytes(0));

      // Add one more element (going past the initial capacity)
      Bool newElement = new Bool();
      newElement.setData(true);
      sequence.add(newElement);

      // The element should have been added
      assertEquals(initialCapacity + 1, sequence.elements());

      // Current capacity should be greater than the initial capacity
      assertTrue(sequence.capacity() > initialCapacity);

      // Make sure the elements we added are stored correctly
      for (int i = 0; i < sequence.elements(); ++i)
      {
         assertEquals(i % 2 == 0, sequence.get(i).getData());
      }

      int originalCapacity = sequence.capacity();
      int originalElements = sequence.elements();

      // Make a copy of the sequence
      IDLObjectSequence<Bool> copySequence = new IDLObjectSequence<>(Bool.class);
      copySequence.set(sequence);

      // Make sure the original wasn't affected by the copy
      assertEquals(originalCapacity, sequence.capacity());
      assertEquals(originalElements, sequence.elements());

      // The copy should have same number of elements as the original
      assertEquals(copySequence.elements(), sequence.elements());

      // Clear the original sequence
      sequence.clear();

      // Make sure it has no elements, but capacity should not be affected
      assertEquals(0, sequence.elements());
      assertEquals(originalCapacity, sequence.capacity());

      // Make sure the copy wasn't affected by changes to the original
      assertEquals(originalElements, copySequence.elements());

      // Create a new sequence with no initial buffer
      IDLObjectSequence<Bool> emptySequence = new IDLObjectSequence<>(Bool.class);

      // Ensure methods work on empty sequences
      assertEquals(0, emptySequence.capacity());
      assertEquals(0, emptySequence.elements());
      assertDoesNotThrow(emptySequence::clear);
   }

   @Test
   public void testIDLShortSequence()
   {
      final int initialCapacity = 8;

      IDLShortSequence sequence = new IDLShortSequence(initialCapacity);

      // The sequence should have no elements
      assertEquals(0, sequence.elements());

      // It's capacity should be the requested initial capacity
      assertEquals(initialCapacity, sequence.capacity());

      // Add initialCapacity number of elements to the sequence
      for (int i = 0; i < initialCapacity; ++i)
      {
         sequence.add((short) i);
      }

      // It should have initialCapacity elements
      assertEquals(initialCapacity, sequence.elements());

      // The capacity should not have changed
      assertEquals(initialCapacity, sequence.capacity());

      // Add one more element (going past the initial capacity)
      sequence.add((short) initialCapacity);

      // Make sure elementSizeBytes is correct
      assertEquals(Short.BYTES, sequence.elementSizeBytes(0));

      // The element should have been added
      assertEquals(initialCapacity + 1, sequence.elements());

      // Current capacity should be greater than the initial capacity
      assertTrue(sequence.capacity() > initialCapacity);

      // Make sure the elements we added are stored correctly
      for (int i = 0; i < sequence.elements(); ++i)
      {
         assertEquals((short) i, sequence.get(i));
      }

      int originalCapacity = sequence.capacity();
      int originalElements = sequence.elements();

      // Make a copy of the sequence
      IDLShortSequence copySequence = new IDLShortSequence();
      copySequence.set(sequence);

      // Make sure the original wasn't affected by the copy
      assertEquals(originalCapacity, sequence.capacity());
      assertEquals(originalElements, sequence.elements());

      // The copy should have same number of elements as the original
      assertEquals(copySequence.elements(), sequence.elements());

      // Clear the original sequence
      sequence.clear();

      // Make sure it has no elements, but capacity should not be affected
      assertEquals(0, sequence.elements());
      assertEquals(originalCapacity, sequence.capacity());

      // Make sure the copy wasn't affected by changes to the original
      assertEquals(originalElements, copySequence.elements());

      // Create a new sequence with no initial buffer
      IDLShortSequence emptySequence = new IDLShortSequence();

      // Ensure methods work on empty sequences
      assertEquals(0, emptySequence.capacity());
      assertEquals(0, emptySequence.elements());
      assertDoesNotThrow(emptySequence::clear);
   }
}
