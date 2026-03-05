package us.ihmc.fastddsjava.cdr.idl;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.msg.TestIDLMsg;
import us.ihmc.log.LogTools;

import static org.junit.jupiter.api.Assertions.*;

public class IDLSequenceTest
{
   @Test
   public void testIDLBoolSequence()
   {
      final int initialCapacity = 8;

      IDLBoolSequence sequence = new IDLBoolSequence(initialCapacity);

      // The sequence should have no elements
      assertEquals(0, sequence.size());

      // Its capacity should be the requested initial capacity
      assertEquals(initialCapacity, sequence.capacity());

      // Add initialCapacity number of elements to the sequence
      for (int i = 0; i < initialCapacity; ++i)
      {
         sequence.add(i % 2 == 0);
      }

      // It should have initialCapacity elements
      assertEquals(initialCapacity, sequence.size());

      // The capacity should not have changed
      assertEquals(initialCapacity, sequence.capacity());

      // Make sure elementSizeBytes is correct
      int booleanSizeBytes = 1;
      assertEquals(booleanSizeBytes, sequence.elementSizeBytes(0, 0));

      // Add one more element (going past the initial capacity)
      sequence.ensureMinCapacity(initialCapacity + 1);
      sequence.add(true);

      // The element should have been added
      assertEquals(initialCapacity + 1, sequence.size());

      // Current capacity should be greater than the initial capacity
      assertTrue(sequence.capacity() > initialCapacity);

      // Make sure the elements we added are stored correctly
      for (int i = 0; i < sequence.size(); ++i)
      {
         assertEquals(i % 2 == 0, sequence.get(i));
      }

      int originalCapacity = sequence.capacity();
      int originalElements = sequence.size();

      // Make a copy of the sequence
      IDLBoolSequence copySequence = new IDLBoolSequence();
      copySequence.set(sequence);

      // Make sure the original wasn't affected by the copy
      assertEquals(originalCapacity, sequence.capacity());
      assertEquals(originalElements, sequence.size());

      // The copy should have same number of elements as the original
      assertEquals(copySequence.size(), sequence.size());

      // Make sure elements are equal in the copy
      for (int i = 0; i < copySequence.size(); ++i)
      {
         assertEquals(copySequence.get(i), sequence.get(i));
      }

      // Clear the original sequence
      sequence.clear();

      // Make sure it has no elements, but capacity should not be affected
      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());

      // Make sure the copy wasn't affected by changes to the original
      assertEquals(originalElements, copySequence.size());

      // Create a new sequence with no initial buffer
      IDLByteSequence emptySequence = new IDLByteSequence();

      // Ensure methods work on empty sequences
      assertEquals(0, emptySequence.capacity());
      assertEquals(0, emptySequence.size());
      assertDoesNotThrow(emptySequence::clear);
   }

   @Test
   public void testIDLByteSequence()
   {
      final int initialCapacity = 8;

      IDLByteSequence sequence = new IDLByteSequence(initialCapacity);

      // The sequence should have no elements
      assertEquals(0, sequence.size());

      // Its capacity should be the requested initial capacity
      assertEquals(initialCapacity, sequence.capacity());

      // Add initialCapacity number of elements to the sequence
      for (int i = 0; i < initialCapacity; ++i)
      {
         sequence.add((byte) i);
      }

      // It should have initialCapacity elements
      assertEquals(initialCapacity, sequence.size());

      // The capacity should not have changed
      assertEquals(initialCapacity, sequence.capacity());

      // Make sure elementSizeBytes is correct
      assertEquals(Byte.BYTES, sequence.elementSizeBytes(0, 0));

      // Add one more element (going past the initial capacity)
      sequence.ensureMinCapacity(initialCapacity + 1);
      sequence.add((byte) initialCapacity);

      // The element should have been added
      assertEquals(initialCapacity + 1, sequence.size());

      // Current capacity should be greater than the initial capacity
      assertTrue(sequence.capacity() > initialCapacity);

      // Make sure the elements we added are stored correctly
      for (int i = 0; i < sequence.size(); ++i)
      {
         assertEquals((byte) i, sequence.get(i));
      }

      int originalCapacity = sequence.capacity();
      int originalElements = sequence.size();

      // Make a copy of the sequence
      IDLByteSequence copySequence = new IDLByteSequence();
      copySequence.set(sequence);

      // Make sure the original wasn't affected by the copy
      assertEquals(originalCapacity, sequence.capacity());
      assertEquals(originalElements, sequence.size());

      // The copy should have same number of elements as the original
      assertEquals(copySequence.size(), sequence.size());

      // Make sure elements are equal in the copy
      for (int i = 0; i < copySequence.size(); ++i)
      {
         assertEquals(copySequence.get(i), sequence.get(i));
      }

      // Clear the original sequence
      sequence.clear();

      // Make sure it has no elements, but capacity should not be affected
      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());

      // Make sure the copy wasn't affected by changes to the original
      assertEquals(originalElements, copySequence.size());

      // Create a new sequence with no initial buffer
      IDLByteSequence emptySequence = new IDLByteSequence();

      // Ensure methods work on empty sequences
      assertEquals(0, emptySequence.capacity());
      assertEquals(0, emptySequence.size());
      assertDoesNotThrow(emptySequence::clear);
   }

   @Test
   public void testIDLCharSequence()
   {
      final int initialCapacity = 8;

      IDLCharSequence sequence = new IDLCharSequence(initialCapacity);

      // The sequence should have no elements
      assertEquals(0, sequence.size());

      // Its capacity should be the requested initial capacity
      assertEquals(initialCapacity, sequence.capacity());

      // Add initialCapacity number of elements to the sequence
      for (int i = 0; i < initialCapacity; ++i)
      {
         sequence.add((char) i);
      }

      // It should have initialCapacity elements
      assertEquals(initialCapacity, sequence.size());

      // The capacity should not have changed
      assertEquals(initialCapacity, sequence.capacity());

      // Make sure elementSizeBytes is correct
      assertEquals(Byte.BYTES, sequence.elementSizeBytes(0, 0));

      // Add one more element (going past the initial capacity)
      sequence.ensureMinCapacity(initialCapacity + 1);
      sequence.add((char) initialCapacity);

      // The element should have been added
      assertEquals(initialCapacity + 1, sequence.size());

      // Current capacity should be greater than the initial capacity
      assertTrue(sequence.capacity() > initialCapacity);

      // Make sure the elements we added are stored correctly
      for (int i = 0; i < sequence.size(); ++i)
      {
         assertEquals((byte) i, sequence.get(i));
      }

      int originalCapacity = sequence.capacity();
      int originalElements = sequence.size();

      // Make a copy of the sequence
      IDLCharSequence copySequence = new IDLCharSequence();
      copySequence.set(sequence);

      // Make sure the original wasn't affected by the copy
      assertEquals(originalCapacity, sequence.capacity());
      assertEquals(originalElements, sequence.size());

      // The copy should have same number of elements as the original
      assertEquals(copySequence.size(), sequence.size());

      // Make sure elements are equal in the copy
      for (int i = 0; i < copySequence.size(); ++i)
      {
         assertEquals(copySequence.get(i), sequence.get(i));
      }

      // Clear the original sequence
      sequence.clear();

      // Make sure it has no elements, but capacity should not be affected
      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());

      // Make sure the copy wasn't affected by changes to the original
      assertEquals(originalElements, copySequence.size());

      // Create a new sequence with no initial buffer
      IDLCharSequence emptySequence = new IDLCharSequence();

      // Ensure methods work on empty sequences
      assertEquals(0, emptySequence.capacity());
      assertEquals(0, emptySequence.size());
      assertDoesNotThrow(emptySequence::clear);
   }

   @Test
   public void testIDLDoubleSequence()
   {
      final int initialCapacity = 8;

      IDLDoubleSequence sequence = new IDLDoubleSequence(initialCapacity);

      // The sequence should have no elements
      assertEquals(0, sequence.size());

      // Its capacity should be the requested initial capacity
      assertEquals(initialCapacity, sequence.capacity());

      // Add initialCapacity number of elements to the sequence
      for (int i = 0; i < initialCapacity; ++i)
      {
         sequence.add(i);
      }

      // It should have initialCapacity elements
      assertEquals(initialCapacity, sequence.size());

      // The capacity should not have changed
      assertEquals(initialCapacity, sequence.capacity());

      // Make sure elementSizeBytes is correct
      assertEquals(Double.BYTES, sequence.elementSizeBytes(0, 0));

      // Add one more element (going past the initial capacity)
      sequence.ensureMinCapacity(initialCapacity + 1);
      sequence.add(initialCapacity);

      // The element should have been added
      assertEquals(initialCapacity + 1, sequence.size());

      // Current capacity should be greater than the initial capacity
      assertTrue(sequence.capacity() > initialCapacity);

      // Make sure the elements we added are stored correctly
      for (int i = 0; i < sequence.size(); ++i)
      {
         assertEquals(i, sequence.get(i));
      }

      int originalCapacity = sequence.capacity();
      int originalElements = sequence.size();

      // Make a copy of the sequence
      IDLDoubleSequence copySequence = new IDLDoubleSequence();
      copySequence.set(sequence);

      // Make sure the original wasn't affected by the copy
      assertEquals(originalCapacity, sequence.capacity());
      assertEquals(originalElements, sequence.size());

      // The copy should have same number of elements as the original
      assertEquals(copySequence.size(), sequence.size());

      // Make sure elements are equal in the copy
      for (int i = 0; i < copySequence.size(); ++i)
      {
         assertEquals(copySequence.get(i), sequence.get(i));
      }

      // Clear the original sequence
      sequence.clear();

      // Make sure it has no elements, but capacity should not be affected
      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());

      // Make sure the copy wasn't affected by changes to the original
      assertEquals(originalElements, copySequence.size());

      // Create a new sequence with no initial buffer
      IDLDoubleSequence emptySequence = new IDLDoubleSequence();

      // Ensure methods work on empty sequences
      assertEquals(0, emptySequence.capacity());
      assertEquals(0, emptySequence.size());
      assertDoesNotThrow(emptySequence::clear);
   }

   @Test
   public void testIDLFloatSequence()
   {
      final int initialCapacity = 8;

      IDLFloatSequence sequence = new IDLFloatSequence(initialCapacity);

      // The sequence should have no elements
      assertEquals(0, sequence.size());

      // Its capacity should be the requested initial capacity
      assertEquals(initialCapacity, sequence.capacity());

      // Add initialCapacity number of elements to the sequence
      for (int i = 0; i < initialCapacity; ++i)
      {
         sequence.add(i);
      }

      // It should have initialCapacity elements
      assertEquals(initialCapacity, sequence.size());

      // The capacity should not have changed
      assertEquals(initialCapacity, sequence.capacity());

      // Make sure elementSizeBytes is correct
      assertEquals(Float.BYTES, sequence.elementSizeBytes(0, 0));

      // Add one more element (going past the initial capacity)
      sequence.ensureMinCapacity(initialCapacity + 1);
      sequence.add(initialCapacity);

      // The element should have been added
      assertEquals(initialCapacity + 1, sequence.size());

      // Current capacity should be greater than the initial capacity
      assertTrue(sequence.capacity() > initialCapacity);

      // Make sure the elements we added are stored correctly
      for (int i = 0; i < sequence.size(); ++i)
      {
         assertEquals((float) i, sequence.get(i));
      }

      int originalCapacity = sequence.capacity();
      int originalElements = sequence.size();

      // Make a copy of the sequence
      IDLFloatSequence copySequence = new IDLFloatSequence();
      copySequence.set(sequence);

      // Make sure the original wasn't affected by the copy
      assertEquals(originalCapacity, sequence.capacity());
      assertEquals(originalElements, sequence.size());

      // The copy should have same number of elements as the original
      assertEquals(copySequence.size(), sequence.size());

      // Make sure elements are equal in the copy
      for (int i = 0; i < copySequence.size(); ++i)
      {
         assertEquals(copySequence.get(i), sequence.get(i));
      }

      // Clear the original sequence
      sequence.clear();

      // Make sure it has no elements, but capacity should not be affected
      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());

      // Make sure the copy wasn't affected by changes to the original
      assertEquals(originalElements, copySequence.size());

      // Create a new sequence with no initial buffer
      IDLFloatSequence emptySequence = new IDLFloatSequence();

      // Ensure methods work on empty sequences
      assertEquals(0, emptySequence.capacity());
      assertEquals(0, emptySequence.size());
      assertDoesNotThrow(emptySequence::clear);
   }

   @Test
   public void testIDLObjectSequence()
   {
      final int initialCapacity = 8;

      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(initialCapacity, TestIDLMsg.class);

      // The sequence should have no elements
      assertEquals(0, sequence.size());

      // Its capacity should be the requested initial capacity
      assertEquals(initialCapacity, sequence.capacity());

      // Add initialCapacity number of elements to the sequence
      for (int i = 0; i < initialCapacity; ++i)
      {
         sequence.add().setData(i % 2 == 0);
      }

      // Test enhanced-for
      int i0 = 0;
      for (TestIDLMsg testIDLMsg : sequence)
      {
         assertEquals(i0 % 2 == 0, testIDLMsg.getData());
         ++i0;
      }

      // It should have initialCapacity elements
      assertEquals(initialCapacity, sequence.size());

      // The capacity should not have changed
      assertEquals(initialCapacity, sequence.capacity());

      // Make sure elementSizeBytes is correct
      assertEquals(new TestIDLMsg().calculateSizeBytes(0), sequence.elementSizeBytes(0, 0));

      // Add one more element (going past the initial capacity)
      TestIDLMsg newElement = new TestIDLMsg();
      newElement.setData(true);
      sequence.add(newElement);

      // The element should have been added
      assertEquals(initialCapacity + 1, sequence.size());

      // Current capacity should be greater than the initial capacity
      assertTrue(sequence.capacity() > initialCapacity);

      // Make sure the elements we added are stored correctly
      for (int i = 0; i < sequence.size(); ++i)
      {
         assertEquals(i % 2 == 0, sequence.get(i).getData());
      }

      int originalCapacity = sequence.capacity();
      int originalElements = sequence.size();

      // Make a copy of the sequence
      IDLObjectSequence<TestIDLMsg> copySequence = new IDLObjectSequence<>(TestIDLMsg.class);
      copySequence.set(sequence);

      // Make sure the original wasn't affected by the copy
      assertEquals(originalCapacity, sequence.capacity());
      assertEquals(originalElements, sequence.size());

      // The copy should have same number of elements as the original
      assertEquals(copySequence.size(), sequence.size());

      // Make sure elements are equal in the copy
      for (int i = 0; i < copySequence.size(); ++i)
      {
         assertEquals(copySequence.get(i), sequence.get(i));
      }

      // Clear the original sequence
      sequence.clear();

      // Make sure it has no elements, but capacity should not be affected
      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());

      // Make sure the copy wasn't affected by changes to the original
      assertEquals(originalElements, copySequence.size());

      // Create a new sequence with no initial buffer
      IDLObjectSequence<TestIDLMsg> emptySequence = new IDLObjectSequence<>(TestIDLMsg.class);

      // Ensure methods work on empty sequences
      assertEquals(0, emptySequence.capacity());
      assertEquals(0, emptySequence.size());
      assertDoesNotThrow(emptySequence::clear);
   }

   @Test
   public void testIDLShortSequence()
   {
      final int initialCapacity = 8;

      IDLShortSequence sequence = new IDLShortSequence(initialCapacity);

      // The sequence should have no elements
      assertEquals(0, sequence.size());

      // Its capacity should be the requested initial capacity
      assertEquals(initialCapacity, sequence.capacity());

      // Add initialCapacity number of elements to the sequence
      for (int i = 0; i < initialCapacity; ++i)
      {
         sequence.add((short) i);
      }

      // It should have initialCapacity elements
      assertEquals(initialCapacity, sequence.size());

      // The capacity should not have changed
      assertEquals(initialCapacity, sequence.capacity());

      // Add one more element (going past the initial capacity)
      sequence.ensureMinCapacity(initialCapacity + 1);
      sequence.add((short) initialCapacity);

      // Make sure elementSizeBytes is correct
      assertEquals(Short.BYTES, sequence.elementSizeBytes(0, 0));

      // The element should have been added
      assertEquals(initialCapacity + 1, sequence.size());

      // Current capacity should be greater than the initial capacity
      assertTrue(sequence.capacity() > initialCapacity);

      // Make sure the elements we added are stored correctly
      for (int i = 0; i < sequence.size(); ++i)
      {
         assertEquals((short) i, sequence.get(i));
      }

      int originalCapacity = sequence.capacity();
      int originalElements = sequence.size();

      // Make a copy of the sequence
      IDLShortSequence copySequence = new IDLShortSequence();
      copySequence.set(sequence);

      // Make sure the original wasn't affected by the copy
      assertEquals(originalCapacity, sequence.capacity());
      assertEquals(originalElements, sequence.size());

      // The copy should have same number of elements as the original
      assertEquals(copySequence.size(), sequence.size());

      // Make sure elements are equal in the copy
      for (int i = 0; i < copySequence.size(); ++i)
      {
         assertEquals(copySequence.get(i), sequence.get(i));
      }

      // Clear the original sequence
      sequence.clear();

      // Make sure it has no elements, but capacity should not be affected
      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());

      // Make sure the copy wasn't affected by changes to the original
      assertEquals(originalElements, copySequence.size());

      // Create a new sequence with no initial buffer
      IDLShortSequence emptySequence = new IDLShortSequence();

      // Ensure methods work on empty sequences
      assertEquals(0, emptySequence.capacity());
      assertEquals(0, emptySequence.size());
      assertDoesNotThrow(emptySequence::clear);
   }

   @Test
   public void testIDLStringSequence()
   {
      final int initialCapacity = 8;

      IDLStringSequence sequence = new IDLStringSequence(initialCapacity);

      // The sequence should have no elements
      assertEquals(0, sequence.size());

      // Its capacity should be the requested initial capacity
      assertEquals(initialCapacity, sequence.capacity());

      // Add initialCapacity number of elements to the sequence
      for (int i = 0; i < initialCapacity; ++i)
      {
         sequence.add(String.valueOf(i));
      }

      // Test enhanced-for
      int i0 = 0;
      for (String string : sequence)
      {
         assertEquals(String.valueOf(i0), string);
         ++i0;
      }

      // It should have initialCapacity elements
      assertEquals(initialCapacity, sequence.size());

      // The capacity should not have changed
      assertEquals(initialCapacity, sequence.capacity());

      // Add one more element (going past the initial capacity)
      sequence.add(String.valueOf(initialCapacity));

      // Make sure elementSizeBytes is correct
      assertEquals(1, sequence.elementSizeBytes(0, 0));

      // The element should have been added
      assertEquals(initialCapacity + 1, sequence.size());

      // Current capacity should be greater than the initial capacity
      assertTrue(sequence.capacity() > initialCapacity);

      // Make sure the elements we added are stored correctly
      for (int i = 0; i < sequence.size(); ++i)
      {
         assertEquals(String.valueOf(i), sequence.getAsString(i));
      }

      int originalCapacity = sequence.capacity();
      int originalElements = sequence.size();

      // Make a copy of the sequence
      IDLStringSequence copySequence = new IDLStringSequence();
      copySequence.set(sequence);

      // Make sure the original wasn't affected by the copy
      assertEquals(originalCapacity, sequence.capacity());
      assertEquals(originalElements, sequence.size());

      // The copy should have same number of elements as the original
      assertEquals(copySequence.size(), sequence.size());

      // Make sure elements are equal in the copy
      for (int i = 0; i < copySequence.size(); ++i)
      {
         assertEquals(copySequence.getAsString(i), sequence.getAsString(i));
      }

      // Clear the original sequence
      sequence.clear();

      // Make sure it has no elements, but capacity should not be affected
      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());

      // Make sure the copy wasn't affected by changes to the original
      assertEquals(originalElements, copySequence.size());

      // Create a new sequence with no initial buffer
      IDLStringSequence emptySequence = new IDLStringSequence();

      // Ensure methods work on empty sequences
      assertEquals(0, emptySequence.capacity());
      assertEquals(0, emptySequence.size());
      assertDoesNotThrow(emptySequence::clear);
   }

   @Test
   public void testIDLWStringSequence()
   {
      final int initialCapacity = 8;
      final int maxSize = Integer.MAX_VALUE;
      final int defaultStringLength = -1;
      final int codepointStart = 78419; // Starting at U+13253 (codepoint 78419)

      IDLWStringSequence sequence = new IDLWStringSequence(initialCapacity, maxSize, defaultStringLength);

      // The sequence should have no elements
      assertEquals(0, sequence.size());

      // Its capacity should be the requested initial capacity
      assertEquals(initialCapacity, sequence.capacity());

      // Add initialCapacity number of elements to the sequence
      int codepoint = codepointStart;
      for (int i = 0; i < initialCapacity; ++i)
      {
         // Print to see characters being tested
         LogTools.debug(new String(Character.toChars(codepoint)));
         sequence.add(new String(Character.toChars(codepoint)));
         codepoint++;
      }

      // It should have initialCapacity elements
      assertEquals(initialCapacity, sequence.size());

      // The capacity should not have changed
      assertEquals(initialCapacity, sequence.capacity());

      // Add one more element (going past the initial capacity)
      sequence.add(new String(Character.toChars(codepoint)));

      // Make sure elementSizeBytes is correct
      assertEquals(8, sequence.elementSizeBytes(0, 0));

      // The element should have been added
      assertEquals(initialCapacity + 1, sequence.size());

      // Current capacity should be greater than the initial capacity
      assertTrue(sequence.capacity() > initialCapacity);

      // Make sure the elements we added are stored correctly
      codepoint = codepointStart;
      for (int i = 0; i < sequence.size(); ++i)
      {
         assertEquals(new String(Character.toChars(codepoint)), sequence.getAsString(i));
         codepoint++;
      }

      int originalCapacity = sequence.capacity();
      int originalElements = sequence.size();

      // Make a copy of the sequence
      IDLWStringSequence copySequence = new IDLWStringSequence();
      copySequence.set(sequence);

      // Make sure the original wasn't affected by the copy
      assertEquals(originalCapacity, sequence.capacity());
      assertEquals(originalElements, sequence.size());

      // The copy should have same number of elements as the original
      assertEquals(copySequence.size(), sequence.size());

      // Make sure elements are equal in the copy
      for (int i = 0; i < copySequence.size(); ++i)
      {
         assertEquals(copySequence.getAsString(i), sequence.getAsString(i));
      }

      // Clear the original sequence
      sequence.clear();

      // Make sure it has no elements, but capacity should not be affected
      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());

      // Make sure the copy wasn't affected by changes to the original
      assertEquals(originalElements, copySequence.size());

      // Create a new sequence with no initial buffer
      IDLWStringSequence emptySequence = new IDLWStringSequence();

      // Ensure methods work on empty sequences
      assertEquals(0, emptySequence.capacity());
      assertEquals(0, emptySequence.size());
      assertDoesNotThrow(emptySequence::clear);
   }
}
