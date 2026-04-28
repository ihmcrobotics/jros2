package us.ihmc.fastddsjava.cdr.idl;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.CDRBuffer;

import static org.junit.jupiter.api.Assertions.*;

public class IDLByteSequenceTest
{
   private static final int INITIAL_CAPACITY = 8;

   @Test
   public void testInitialization()
   {
      IDLByteSequence sequence = new IDLByteSequence(INITIAL_CAPACITY);

      assertEquals(0, sequence.size());
      assertEquals(INITIAL_CAPACITY, sequence.capacity());
   }

   @Test
   public void testAdd()
   {
      IDLByteSequence sequence = new IDLByteSequence(INITIAL_CAPACITY);

      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         sequence.add((byte) i);
      }

      assertEquals(INITIAL_CAPACITY, sequence.size());
      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         assertEquals((byte) i, sequence.get(i));
      }
   }

   @Test
   public void testRemove()
   {
      IDLByteSequence sequence = new IDLByteSequence(INITIAL_CAPACITY);
      sequence.add((byte) 10);
      sequence.add((byte) 20);
      sequence.add((byte) 30);

      byte removed = sequence.remove();

      assertEquals((byte) 30, removed);
      assertEquals(2, sequence.size());
   }

   @Test
   public void testRemoveAtIndex()
   {
      IDLByteSequence sequence = new IDLByteSequence(INITIAL_CAPACITY);
      sequence.add((byte) 10);
      sequence.add((byte) 20);
      sequence.add((byte) 30);
      sequence.add((byte) 40);

      byte removed = sequence.remove(1);

      assertEquals((byte) 20, removed);
      assertEquals(3, sequence.size());
      assertEquals((byte) 10, sequence.get(0));
      assertEquals((byte) 30, sequence.get(1));
      assertEquals((byte) 40, sequence.get(2));
   }

   @Test
   public void testIterator()
   {
      IDLByteSequence sequence = new IDLByteSequence(INITIAL_CAPACITY);
      sequence.add((byte) 10);
      sequence.add((byte) 20);
      sequence.add((byte) 30);

      int index = 0;
      byte[] expected = {10, 20, 30};
      for (byte value : sequence)
      {
         assertEquals(expected[index++], value);
      }
   }

   @Test
   public void testIteratorRemove()
   {
      IDLByteSequence sequence = new IDLByteSequence(INITIAL_CAPACITY);
      sequence.add((byte) 10);
      sequence.add((byte) 20);
      sequence.add((byte) 30);

      var iterator = sequence.iterator();
      iterator.next();
      iterator.next();
      iterator.remove();

      assertEquals(2, sequence.size());
      assertEquals((byte) 10, sequence.get(0));
      assertEquals((byte) 30, sequence.get(1));
   }

   @Test
   public void testClear()
   {
      IDLByteSequence sequence = new IDLByteSequence(INITIAL_CAPACITY);
      sequence.add((byte) 1);
      sequence.add((byte) 2);

      int originalCapacity = sequence.capacity();
      sequence.clear();

      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());
   }

   @Test
   public void testSet()
   {
      IDLByteSequence source = new IDLByteSequence(INITIAL_CAPACITY);
      source.add((byte) 1);
      source.add((byte) 2);
      source.add((byte) 3);

      IDLByteSequence target = new IDLByteSequence();
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
      IDLByteSequence sequence = new IDLByteSequence();

      assertEquals(0, sequence.capacity());
      assertEquals(0, sequence.size());
      assertDoesNotThrow(sequence::clear);
   }

   @Test
   public void testElementSizeBytes()
   {
      IDLByteSequence sequence = new IDLByteSequence(INITIAL_CAPACITY);
      sequence.add((byte) 42);

      assertEquals(Byte.BYTES, sequence.elementSizeBytes(CDRBuffer.PAYLOAD_HEADER.length, 0));
   }
}
