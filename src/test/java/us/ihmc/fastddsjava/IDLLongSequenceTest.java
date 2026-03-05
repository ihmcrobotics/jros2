package us.ihmc.fastddsjava;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.idl.IDLLongSequence;

import static org.junit.jupiter.api.Assertions.*;

public class IDLLongSequenceTest
{
   private static final int INITIAL_CAPACITY = 8;

   @Test
   public void testInitialization()
   {
      IDLLongSequence sequence = new IDLLongSequence(INITIAL_CAPACITY);

      assertEquals(0, sequence.size());
      assertEquals(INITIAL_CAPACITY, sequence.capacity());
   }

   @Test
   public void testAdd()
   {
      IDLLongSequence sequence = new IDLLongSequence(INITIAL_CAPACITY);

      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         sequence.add(i * 1000L);
      }

      assertEquals(INITIAL_CAPACITY, sequence.size());
      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         assertEquals(i * 1000L, sequence.get(i));
      }
   }

   @Test
   public void testRemove()
   {
      IDLLongSequence sequence = new IDLLongSequence(INITIAL_CAPACITY);
      sequence.add(100L);
      sequence.add(200L);
      sequence.add(300L);

      long removed = sequence.remove();

      assertEquals(300L, removed);
      assertEquals(2, sequence.size());
   }

   @Test
   public void testRemoveAtIndex()
   {
      IDLLongSequence sequence = new IDLLongSequence(INITIAL_CAPACITY);
      sequence.add(100L);
      sequence.add(200L);
      sequence.add(300L);
      sequence.add(400L);

      long removed = sequence.remove(1);

      assertEquals(200L, removed);
      assertEquals(3, sequence.size());
      assertEquals(100L, sequence.get(0));
      assertEquals(300L, sequence.get(1));
      assertEquals(400L, sequence.get(2));
   }

   @Test
   public void testIterator()
   {
      IDLLongSequence sequence = new IDLLongSequence(INITIAL_CAPACITY);
      sequence.add(10L);
      sequence.add(20L);
      sequence.add(30L);

      int index = 0;
      long[] expected = {10L, 20L, 30L};
      for (long value : sequence)
      {
         assertEquals(expected[index++], value);
      }
   }

   @Test
   public void testIteratorRemove()
   {
      IDLLongSequence sequence = new IDLLongSequence(INITIAL_CAPACITY);
      sequence.add(10L);
      sequence.add(20L);
      sequence.add(30L);

      var iterator = sequence.iterator();
      iterator.next();
      iterator.next();
      iterator.remove();

      assertEquals(2, sequence.size());
      assertEquals(10L, sequence.get(0));
      assertEquals(30L, sequence.get(1));
   }

   @Test
   public void testClear()
   {
      IDLLongSequence sequence = new IDLLongSequence(INITIAL_CAPACITY);
      sequence.add(1L);
      sequence.add(2L);

      int originalCapacity = sequence.capacity();
      sequence.clear();

      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());
   }

   @Test
   public void testSet()
   {
      IDLLongSequence source = new IDLLongSequence(INITIAL_CAPACITY);
      source.add(1L);
      source.add(2L);
      source.add(3L);

      IDLLongSequence target = new IDLLongSequence();
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
      IDLLongSequence sequence = new IDLLongSequence();

      assertEquals(0, sequence.capacity());
      assertEquals(0, sequence.size());
      assertDoesNotThrow(sequence::clear);
   }
}
