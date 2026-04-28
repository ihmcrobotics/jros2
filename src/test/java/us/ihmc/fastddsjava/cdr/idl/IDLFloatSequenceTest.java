package us.ihmc.fastddsjava.cdr.idl;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.CDRBuffer;

import static org.junit.jupiter.api.Assertions.*;

public class IDLFloatSequenceTest
{
   private static final int INITIAL_CAPACITY = 8;

   @Test
   public void testInitialization()
   {
      IDLFloatSequence sequence = new IDLFloatSequence(INITIAL_CAPACITY);

      assertEquals(0, sequence.size());
      assertEquals(INITIAL_CAPACITY, sequence.capacity());
   }

   @Test
   public void testAdd()
   {
      IDLFloatSequence sequence = new IDLFloatSequence(INITIAL_CAPACITY);

      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         sequence.add(i * 1.5f);
      }

      assertEquals(INITIAL_CAPACITY, sequence.size());
      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         assertEquals(i * 1.5f, sequence.get(i), 0.001f);
      }
   }

   @Test
   public void testRemove()
   {
      IDLFloatSequence sequence = new IDLFloatSequence(INITIAL_CAPACITY);
      sequence.add(10.5f);
      sequence.add(20.5f);
      sequence.add(30.5f);

      float removed = sequence.remove();

      assertEquals(30.5f, removed, 0.001f);
      assertEquals(2, sequence.size());
   }

   @Test
   public void testRemoveAtIndex()
   {
      IDLFloatSequence sequence = new IDLFloatSequence(INITIAL_CAPACITY);
      sequence.add(10.1f);
      sequence.add(20.2f);
      sequence.add(30.3f);
      sequence.add(40.4f);

      float removed = sequence.remove(1);

      assertEquals(20.2f, removed, 0.001f);
      assertEquals(3, sequence.size());
      assertEquals(10.1f, sequence.get(0), 0.001f);
      assertEquals(30.3f, sequence.get(1), 0.001f);
      assertEquals(40.4f, sequence.get(2), 0.001f);
   }

   @Test
   public void testIterator()
   {
      IDLFloatSequence sequence = new IDLFloatSequence(INITIAL_CAPACITY);
      sequence.add(1.1f);
      sequence.add(2.2f);
      sequence.add(3.3f);

      int index = 0;
      float[] expected = {1.1f, 2.2f, 3.3f};
      for (float value : sequence)
      {
         assertEquals(expected[index++], value, 0.001f);
      }
   }

   @Test
   public void testIteratorRemove()
   {
      IDLFloatSequence sequence = new IDLFloatSequence(INITIAL_CAPACITY);
      sequence.add(1.0f);
      sequence.add(2.0f);
      sequence.add(3.0f);

      var iterator = sequence.iterator();
      iterator.next();
      iterator.next();
      iterator.remove();

      assertEquals(2, sequence.size());
      assertEquals(1.0f, sequence.get(0), 0.001f);
      assertEquals(3.0f, sequence.get(1), 0.001f);
   }

   @Test
   public void testClear()
   {
      IDLFloatSequence sequence = new IDLFloatSequence(INITIAL_CAPACITY);
      sequence.add(1.0f);
      sequence.add(2.0f);

      int originalCapacity = sequence.capacity();
      sequence.clear();

      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());
   }

   @Test
   public void testSet()
   {
      IDLFloatSequence source = new IDLFloatSequence(INITIAL_CAPACITY);
      source.add(1.1f);
      source.add(2.2f);
      source.add(3.3f);

      IDLFloatSequence target = new IDLFloatSequence();
      target.set(source);

      assertEquals(source.size(), target.size());
      for (int i = 0; i < source.size(); ++i)
      {
         assertEquals(source.get(i), target.get(i), 0.001f);
      }
   }

   @Test
   public void testEmptySequence()
   {
      IDLFloatSequence sequence = new IDLFloatSequence();

      assertEquals(0, sequence.capacity());
      assertEquals(0, sequence.size());
      assertDoesNotThrow(sequence::clear);
   }

   @Test
   public void testElementSizeBytes()
   {
      IDLFloatSequence sequence = new IDLFloatSequence(INITIAL_CAPACITY);
      sequence.add(42.0f);

      assertEquals(Float.BYTES, sequence.elementSizeBytes(CDRBuffer.PAYLOAD_HEADER.length, 0));
   }
}
