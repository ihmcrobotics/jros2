package us.ihmc.fastddsjava.cdr.idl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class IDLCharSequenceTest
{
   private static final int INITIAL_CAPACITY = 8;

   @Test
   public void testInitialization()
   {
      IDLCharSequence sequence = new IDLCharSequence(INITIAL_CAPACITY);

      assertEquals(0, sequence.size());
      assertEquals(INITIAL_CAPACITY, sequence.capacity());
   }

   @Test
   public void testAdd()
   {
      IDLCharSequence sequence = new IDLCharSequence(INITIAL_CAPACITY);

      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         sequence.add((char) ('a' + i));
      }

      assertEquals(INITIAL_CAPACITY, sequence.size());
      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         assertEquals((char) ('a' + i), sequence.get(i));
      }
   }

   @Test
   public void testRemove()
   {
      IDLCharSequence sequence = new IDLCharSequence(INITIAL_CAPACITY);
      sequence.add('x');
      sequence.add('y');
      sequence.add('z');

      char removed = sequence.remove();

      assertEquals('z', removed);
      assertEquals(2, sequence.size());
   }

   @Test
   public void testRemoveAtIndex()
   {
      IDLCharSequence sequence = new IDLCharSequence(INITIAL_CAPACITY);
      sequence.add('a');
      sequence.add('b');
      sequence.add('c');
      sequence.add('d');

      char removed = sequence.remove(1);

      assertEquals('b', removed);
      assertEquals(3, sequence.size());
      assertEquals('a', sequence.get(0));
      assertEquals('c', sequence.get(1));
      assertEquals('d', sequence.get(2));
   }

   @Test
   public void testIterator()
   {
      IDLCharSequence sequence = new IDLCharSequence(INITIAL_CAPACITY);
      sequence.add('x');
      sequence.add('y');
      sequence.add('z');

      int index = 0;
      char[] expected = {'x', 'y', 'z'};
      for (char value : sequence)
      {
         assertEquals(expected[index++], value);
      }
   }

   @Test
   public void testIteratorRemove()
   {
      IDLCharSequence sequence = new IDLCharSequence(INITIAL_CAPACITY);
      sequence.add('a');
      sequence.add('b');
      sequence.add('c');

      var iterator = sequence.iterator();
      iterator.next();
      iterator.next();
      iterator.remove();

      assertEquals(2, sequence.size());
      assertEquals('a', sequence.get(0));
      assertEquals('c', sequence.get(1));
   }

   @Test
   public void testClear()
   {
      IDLCharSequence sequence = new IDLCharSequence(INITIAL_CAPACITY);
      sequence.add('a');
      sequence.add('b');

      int originalCapacity = sequence.capacity();
      sequence.clear();

      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());
   }

   @Test
   public void testSet()
   {
      IDLCharSequence source = new IDLCharSequence(INITIAL_CAPACITY);
      source.add('x');
      source.add('y');
      source.add('z');

      IDLCharSequence target = new IDLCharSequence();
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
      IDLCharSequence sequence = new IDLCharSequence();

      assertEquals(0, sequence.capacity());
      assertEquals(0, sequence.size());
      assertDoesNotThrow(sequence::clear);
   }
}
