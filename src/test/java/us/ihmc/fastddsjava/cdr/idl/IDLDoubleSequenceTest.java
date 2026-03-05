package us.ihmc.fastddsjava.cdr.idl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class IDLDoubleSequenceTest
{
   private static final int INITIAL_CAPACITY = 8;

   @Test
   public void testInitialization()
   {
      IDLDoubleSequence sequence = new IDLDoubleSequence(INITIAL_CAPACITY);

      assertEquals(0, sequence.size());
      assertEquals(INITIAL_CAPACITY, sequence.capacity());
   }

   @Test
   public void testAdd()
   {
      IDLDoubleSequence sequence = new IDLDoubleSequence(INITIAL_CAPACITY);

      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         sequence.add(i * 1.5);
      }

      assertEquals(INITIAL_CAPACITY, sequence.size());
      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         assertEquals(i * 1.5, sequence.get(i), 0.001);
      }
   }

   @Test
   public void testRemove()
   {
      IDLDoubleSequence sequence = new IDLDoubleSequence(INITIAL_CAPACITY);
      sequence.add(10.5);
      sequence.add(20.5);
      sequence.add(30.5);

      double removed = sequence.remove();

      assertEquals(30.5, removed, 0.001);
      assertEquals(2, sequence.size());
   }

   @Test
   public void testRemoveAtIndex()
   {
      IDLDoubleSequence sequence = new IDLDoubleSequence(INITIAL_CAPACITY);
      sequence.add(10.1);
      sequence.add(20.2);
      sequence.add(30.3);
      sequence.add(40.4);

      double removed = sequence.remove(1);

      assertEquals(20.2, removed, 0.001);
      assertEquals(3, sequence.size());
      assertEquals(10.1, sequence.get(0), 0.001);
      assertEquals(30.3, sequence.get(1), 0.001);
      assertEquals(40.4, sequence.get(2), 0.001);
   }

   @Test
   public void testIterator()
   {
      IDLDoubleSequence sequence = new IDLDoubleSequence(INITIAL_CAPACITY);
      sequence.add(1.1);
      sequence.add(2.2);
      sequence.add(3.3);

      int index = 0;
      double[] expected = {1.1, 2.2, 3.3};
      for (double value : sequence)
      {
         assertEquals(expected[index++], value, 0.001);
      }
   }

   @Test
   public void testIteratorRemove()
   {
      IDLDoubleSequence sequence = new IDLDoubleSequence(INITIAL_CAPACITY);
      sequence.add(1.0);
      sequence.add(2.0);
      sequence.add(3.0);

      var iterator = sequence.iterator();
      iterator.next();
      iterator.next();
      iterator.remove();

      assertEquals(2, sequence.size());
      assertEquals(1.0, sequence.get(0), 0.001);
      assertEquals(3.0, sequence.get(1), 0.001);
   }

   @Test
   public void testClear()
   {
      IDLDoubleSequence sequence = new IDLDoubleSequence(INITIAL_CAPACITY);
      sequence.add(1.0);
      sequence.add(2.0);

      int originalCapacity = sequence.capacity();
      sequence.clear();

      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());
   }

   @Test
   public void testSet()
   {
      IDLDoubleSequence source = new IDLDoubleSequence(INITIAL_CAPACITY);
      source.add(1.1);
      source.add(2.2);
      source.add(3.3);

      IDLDoubleSequence target = new IDLDoubleSequence();
      target.set(source);

      assertEquals(source.size(), target.size());
      for (int i = 0; i < source.size(); ++i)
      {
         assertEquals(source.get(i), target.get(i), 0.001);
      }
   }

   @Test
   public void testEmptySequence()
   {
      IDLDoubleSequence sequence = new IDLDoubleSequence();

      assertEquals(0, sequence.capacity());
      assertEquals(0, sequence.size());
      assertDoesNotThrow(sequence::clear);
   }

   @Test
   public void testElementSizeBytes()
   {
      IDLDoubleSequence sequence = new IDLDoubleSequence(INITIAL_CAPACITY);
      sequence.add(42.0);

      assertEquals(Double.BYTES, sequence.elementSizeBytes(0, 0));
   }
}
