package us.ihmc.fastddsjava.cdr.idl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class IDLBoolSequenceTest
{
   private static final int INITIAL_CAPACITY = 8;

   @Test
   public void testInitialization()
   {
      IDLBoolSequence sequence = new IDLBoolSequence(INITIAL_CAPACITY);

      assertEquals(0, sequence.size());
      assertEquals(INITIAL_CAPACITY, sequence.capacity());
   }

   @Test
   public void testAdd()
   {
      IDLBoolSequence sequence = new IDLBoolSequence(INITIAL_CAPACITY);

      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         sequence.add(i % 2 == 0);
      }

      assertEquals(INITIAL_CAPACITY, sequence.size());
      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         assertEquals(i % 2 == 0, sequence.get(i));
      }
   }

   @Test
   public void testRemove()
   {
      IDLBoolSequence sequence = new IDLBoolSequence(INITIAL_CAPACITY);
      sequence.add(true);
      sequence.add(false);
      sequence.add(true);

      boolean removed = sequence.remove();

      assertEquals(true, removed);
      assertEquals(2, sequence.size());
   }

   @Test
   public void testRemoveAtIndex()
   {
      IDLBoolSequence sequence = new IDLBoolSequence(INITIAL_CAPACITY);
      sequence.add(true);
      sequence.add(false);
      sequence.add(true);
      sequence.add(false);

      boolean removed = sequence.remove(1);

      assertEquals(false, removed);
      assertEquals(3, sequence.size());
      assertEquals(true, sequence.get(0));
      assertEquals(true, sequence.get(1)); // Shifted from index 2
      assertEquals(false, sequence.get(2)); // Shifted from index 3
   }

   @Test
   public void testIterator()
   {
      IDLBoolSequence sequence = new IDLBoolSequence(INITIAL_CAPACITY);
      sequence.add(true);
      sequence.add(false);
      sequence.add(true);

      int index = 0;
      boolean[] expected = {true, false, true};
      for (boolean value : sequence)
      {
         assertEquals(expected[index++], value);
      }
   }

   @Test
   public void testIteratorRemove()
   {
      IDLBoolSequence sequence = new IDLBoolSequence(INITIAL_CAPACITY);
      sequence.add(true);
      sequence.add(false);
      sequence.add(true);

      var iterator = sequence.iterator();
      iterator.next();
      iterator.next();
      iterator.remove();

      assertEquals(2, sequence.size());
      assertEquals(true, sequence.get(0));
      assertEquals(true, sequence.get(1));
   }

   @Test
   public void testClear()
   {
      IDLBoolSequence sequence = new IDLBoolSequence(INITIAL_CAPACITY);
      sequence.add(true);
      sequence.add(false);

      int originalCapacity = sequence.capacity();
      sequence.clear();

      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());
   }

   @Test
   public void testSet()
   {
      IDLBoolSequence source = new IDLBoolSequence(INITIAL_CAPACITY);
      source.add(true);
      source.add(false);
      source.add(true);

      IDLBoolSequence target = new IDLBoolSequence();
      target.set(source);

      assertEquals(source.size(), target.size());
      for (int i = 0; i < source.size(); ++i)
      {
         assertEquals(source.get(i), target.get(i));
      }
   }

   @Test
   public void testEmptySequence()
   {
      IDLBoolSequence sequence = new IDLBoolSequence();

      assertEquals(0, sequence.capacity());
      assertEquals(0, sequence.size());
      assertDoesNotThrow(sequence::clear);
   }
}
