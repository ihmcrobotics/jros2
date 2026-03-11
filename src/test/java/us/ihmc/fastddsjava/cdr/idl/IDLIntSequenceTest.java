package us.ihmc.fastddsjava.cdr.idl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class IDLIntSequenceTest
{
   private static final int INITIAL_CAPACITY = 8;

   @Test
   public void testInitialization()
   {
      IDLIntSequence sequence = new IDLIntSequence(INITIAL_CAPACITY);

      assertEquals(0, sequence.size(), "New sequence should have no elements");
      assertEquals(INITIAL_CAPACITY, sequence.capacity(), "Capacity should match initial capacity");
   }

   @Test
   public void testAdd()
   {
      IDLIntSequence sequence = new IDLIntSequence(INITIAL_CAPACITY);

      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         sequence.add(i);
      }

      assertEquals(INITIAL_CAPACITY, sequence.size(), "Size should equal number of elements added");
      assertEquals(INITIAL_CAPACITY, sequence.capacity(), "Capacity should not change when within bounds");

      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         assertEquals(i, sequence.get(i), "Element at index " + i + " should match");
      }
   }

   @Test
   public void testAddBeyondCapacity()
   {
      IDLIntSequence sequence = new IDLIntSequence(INITIAL_CAPACITY);

      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         sequence.add(i);
      }

      sequence.add(INITIAL_CAPACITY);

      assertEquals(INITIAL_CAPACITY + 1, sequence.size(), "Size should include new element");
      assertTrue(sequence.capacity() > INITIAL_CAPACITY, "Capacity should grow beyond initial capacity");
      assertEquals(INITIAL_CAPACITY, sequence.get(INITIAL_CAPACITY), "New element should be stored correctly");
   }

   @Test
   public void testRemove()
   {
      IDLIntSequence sequence = new IDLIntSequence(INITIAL_CAPACITY);
      sequence.add(10);
      sequence.add(20);
      sequence.add(30);

      int removed = sequence.remove();

      assertEquals(30, removed, "Should remove and return last element");
      assertEquals(2, sequence.size(), "Size should decrease after remove");
      assertEquals(20, sequence.get(1), "Second element should still be accessible");
   }

   @Test
   public void testRemoveAtIndex()
   {
      IDLIntSequence sequence = new IDLIntSequence(INITIAL_CAPACITY);
      sequence.add(10);
      sequence.add(20);
      sequence.add(30);
      sequence.add(40);

      int removed = sequence.remove(1);

      assertEquals(20, removed, "Should remove and return element at index 1");
      assertEquals(3, sequence.size(), "Size should decrease after remove");
      assertEquals(10, sequence.get(0), "First element should be unchanged");
      assertEquals(30, sequence.get(1), "Element at index 2 should shift to index 1");
      assertEquals(40, sequence.get(2), "Element at index 3 should shift to index 2");
   }

   @Test
   public void testRemoveAtBeginning()
   {
      IDLIntSequence sequence = new IDLIntSequence(INITIAL_CAPACITY);
      sequence.add(10);
      sequence.add(20);
      sequence.add(30);

      int removed = sequence.remove(0);

      assertEquals(10, removed, "Should remove first element");
      assertEquals(2, sequence.size(), "Size should decrease");
      assertEquals(20, sequence.get(0), "Second element should become first");
      assertEquals(30, sequence.get(1), "Third element should become second");
   }

   @Test
   public void testRemoveAtEnd()
   {
      IDLIntSequence sequence = new IDLIntSequence(INITIAL_CAPACITY);
      sequence.add(10);
      sequence.add(20);
      sequence.add(30);

      int removed = sequence.remove(2);

      assertEquals(30, removed, "Should remove last element");
      assertEquals(2, sequence.size(), "Size should decrease");
      assertEquals(10, sequence.get(0), "First element unchanged");
      assertEquals(20, sequence.get(1), "Second element unchanged");
   }

   @Test
   public void testGet()
   {
      IDLIntSequence sequence = new IDLIntSequence(INITIAL_CAPACITY);
      sequence.add(100);
      sequence.add(200);

      assertEquals(100, sequence.get(0), "First element should be 100");
      assertEquals(200, sequence.get(1), "Second element should be 200");
   }

   @Test
   public void testClear()
   {
      IDLIntSequence sequence = new IDLIntSequence(INITIAL_CAPACITY);
      sequence.add(10);
      sequence.add(20);
      sequence.add(30);

      int originalCapacity = sequence.capacity();
      sequence.clear();

      assertEquals(0, sequence.size(), "Size should be 0 after clear");
      assertEquals(originalCapacity, sequence.capacity(), "Capacity should not change after clear");
   }

   @Test
   public void testSet()
   {
      IDLIntSequence source = new IDLIntSequence(INITIAL_CAPACITY);
      source.add(1);
      source.add(2);
      source.add(3);

      IDLIntSequence target = new IDLIntSequence();
      target.set(source);

      assertEquals(source.size(), target.size(), "Target should have same size as source");
      for (int i = 0; i < source.size(); ++i)
      {
         assertEquals(source.get(i), target.get(i), "Elements should match at index " + i);
      }

      // Verify source is unaffected
      assertEquals(3, source.size(), "Source size should be unchanged");
   }

   @Test
   public void testIterator()
   {
      IDLIntSequence sequence = new IDLIntSequence(INITIAL_CAPACITY);
      sequence.add(10);
      sequence.add(20);
      sequence.add(30);

      int index = 0;
      int[] expected = {10, 20, 30};
      for (int value : sequence)
      {
         assertEquals(expected[index], value, "Iterator should return elements in order");
         index++;
      }
      assertEquals(3, index, "Iterator should iterate over all elements");
   }

   @Test
   public void testIteratorRemove()
   {
      IDLIntSequence sequence = new IDLIntSequence(INITIAL_CAPACITY);
      sequence.add(10);
      sequence.add(20);
      sequence.add(30);
      sequence.add(40);

      var iterator = sequence.iterator();
      iterator.next(); // 10
      iterator.next(); // 20
      iterator.remove(); // Remove 20

      assertEquals(3, sequence.size(), "Size should decrease after iterator remove");
      assertEquals(10, sequence.get(0), "First element unchanged");
      assertEquals(30, sequence.get(1), "Third element should shift to index 1");
      assertEquals(40, sequence.get(2), "Fourth element should shift to index 2");
   }

   @Test
   public void testEmptySequence()
   {
      IDLIntSequence sequence = new IDLIntSequence();

      assertEquals(0, sequence.capacity(), "Empty sequence should have 0 capacity");
      assertEquals(0, sequence.size(), "Empty sequence should have 0 size");
      assertDoesNotThrow(sequence::clear, "Clear should not throw on empty sequence");
   }

   @Test
   public void testEnsureMinCapacity()
   {
      IDLIntSequence sequence = new IDLIntSequence(INITIAL_CAPACITY);

      assertTrue(sequence.ensureMinCapacity(INITIAL_CAPACITY * 2), "Should successfully increase capacity");
      assertTrue(sequence.capacity() >= INITIAL_CAPACITY * 2, "Capacity should be at least requested amount");
   }

   @Test
   public void testElementSizeBytes()
   {
      IDLIntSequence sequence = new IDLIntSequence(INITIAL_CAPACITY);
      sequence.add(42);

      assertEquals(Integer.BYTES, sequence.elementSizeBytes(0, 0), "Element size should match int size");
   }
}
