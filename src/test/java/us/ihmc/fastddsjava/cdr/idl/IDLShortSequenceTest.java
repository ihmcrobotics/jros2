package us.ihmc.fastddsjava.cdr.idl;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.CDRBuffer;

import static org.junit.jupiter.api.Assertions.*;

public class IDLShortSequenceTest
{
   private static final int INITIAL_CAPACITY = 8;

   @Test
   public void testInitialization()
   {
      IDLShortSequence sequence = new IDLShortSequence(INITIAL_CAPACITY);

      assertEquals(0, sequence.size());
      assertEquals(INITIAL_CAPACITY, sequence.capacity());
   }

   @Test
   public void testAdd()
   {
      IDLShortSequence sequence = new IDLShortSequence(INITIAL_CAPACITY);

      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         sequence.add((short) (i * 10));
      }

      assertEquals(INITIAL_CAPACITY, sequence.size());
      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         assertEquals((short) (i * 10), sequence.get(i));
      }
   }

   @Test
   public void testRemove()
   {
      IDLShortSequence sequence = new IDLShortSequence(INITIAL_CAPACITY);
      sequence.add((short) 100);
      sequence.add((short) 200);
      sequence.add((short) 300);

      short removed = sequence.remove();

      assertEquals((short) 300, removed);
      assertEquals(2, sequence.size());
   }

   @Test
   public void testRemoveAtIndex()
   {
      IDLShortSequence sequence = new IDLShortSequence(INITIAL_CAPACITY);
      sequence.add((short) 100);
      sequence.add((short) 200);
      sequence.add((short) 300);
      sequence.add((short) 400);

      short removed = sequence.remove(1);

      assertEquals((short) 200, removed);
      assertEquals(3, sequence.size());
      assertEquals((short) 100, sequence.get(0));
      assertEquals((short) 300, sequence.get(1));
      assertEquals((short) 400, sequence.get(2));
   }

   @Test
   public void testIterator()
   {
      IDLShortSequence sequence = new IDLShortSequence(INITIAL_CAPACITY);
      sequence.add((short) 10);
      sequence.add((short) 20);
      sequence.add((short) 30);

      int index = 0;
      short[] expected = {10, 20, 30};
      for (short value : sequence)
      {
         assertEquals(expected[index++], value);
      }
   }

   @Test
   public void testIteratorRemove()
   {
      IDLShortSequence sequence = new IDLShortSequence(INITIAL_CAPACITY);
      sequence.add((short) 10);
      sequence.add((short) 20);
      sequence.add((short) 30);

      var iterator = sequence.iterator();
      iterator.next();
      iterator.next();
      iterator.remove();

      assertEquals(2, sequence.size());
      assertEquals((short) 10, sequence.get(0));
      assertEquals((short) 30, sequence.get(1));
   }

   @Test
   public void testClear()
   {
      IDLShortSequence sequence = new IDLShortSequence(INITIAL_CAPACITY);
      sequence.add((short) 1);
      sequence.add((short) 2);

      int originalCapacity = sequence.capacity();
      sequence.clear();

      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());
   }

   @Test
   public void testSet()
   {
      IDLShortSequence source = new IDLShortSequence(INITIAL_CAPACITY);
      source.add((short) 1);
      source.add((short) 2);
      source.add((short) 3);

      IDLShortSequence target = new IDLShortSequence();
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
      IDLShortSequence sequence = new IDLShortSequence();

      assertEquals(0, sequence.capacity());
      assertEquals(0, sequence.size());
      assertDoesNotThrow(sequence::clear);
   }

   @Test
   public void testElementSizeBytes()
   {
      IDLShortSequence sequence = new IDLShortSequence(INITIAL_CAPACITY);
      sequence.add((short) 42);

      assertEquals(Short.BYTES, sequence.elementSizeBytes(CDRBuffer.PAYLOAD_HEADER.length, 0));
   }
}
