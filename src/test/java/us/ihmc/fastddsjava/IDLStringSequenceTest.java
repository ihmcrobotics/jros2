package us.ihmc.fastddsjava;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.idl.IDLStringSequence;

import static org.junit.jupiter.api.Assertions.*;

public class IDLStringSequenceTest
{
   private static final int INITIAL_CAPACITY = 8;

   @Test
   public void testInitialization()
   {
      IDLStringSequence sequence = new IDLStringSequence(INITIAL_CAPACITY);

      assertEquals(0, sequence.size());
      assertEquals(INITIAL_CAPACITY, sequence.capacity());
   }

   @Test
   public void testAddString()
   {
      IDLStringSequence sequence = new IDLStringSequence(INITIAL_CAPACITY);

      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         sequence.add("String" + i);
      }

      assertEquals(INITIAL_CAPACITY, sequence.size());
      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         assertEquals("String" + i, sequence.getAsString(i));
      }
   }

   @Test
   public void testAddStringBuilder()
   {
      IDLStringSequence sequence = new IDLStringSequence(INITIAL_CAPACITY);

      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         sequence.add(new StringBuilder("Builder" + i));
      }

      assertEquals(INITIAL_CAPACITY, sequence.size());
      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         assertEquals("Builder" + i, sequence.getAsString(i));
      }
   }

   @Test
   public void testAddWithAutoCreate()
   {
      IDLStringSequence sequence = new IDLStringSequence(INITIAL_CAPACITY);

      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         sequence.add().append("Auto").append(i);
      }

      assertEquals(INITIAL_CAPACITY, sequence.size());
      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         assertEquals("Auto" + i, sequence.getAsString(i));
      }
   }

   @Test
   public void testAddWithLength()
   {
      IDLStringSequence sequence = new IDLStringSequence(INITIAL_CAPACITY);

      StringBuilder sb = sequence.add(32);
      sb.append("TestString");

      assertEquals(1, sequence.size());
      assertEquals("TestString", sequence.getAsString(0));
      assertTrue(sequence.get(0).capacity() >= 32);
   }

   @Test
   public void testRemove()
   {
      IDLStringSequence sequence = new IDLStringSequence(INITIAL_CAPACITY);
      sequence.add("First");
      sequence.add("Second");
      sequence.add("Third");

      StringBuilder removed = sequence.remove();

      assertEquals("Third", removed.toString());
      assertEquals(2, sequence.size());
   }

   @Test
   public void testRemoveAtIndex()
   {
      IDLStringSequence sequence = new IDLStringSequence(INITIAL_CAPACITY);
      sequence.add("A");
      sequence.add("B");
      sequence.add("C");
      sequence.add("D");

      StringBuilder removed = sequence.remove(1);

      assertEquals("B", removed.toString());
      assertEquals(3, sequence.size());
      assertEquals("A", sequence.getAsString(0));
      assertEquals("C", sequence.getAsString(1));
      assertEquals("D", sequence.getAsString(2));
   }

   @Test
   public void testRemoveAtBeginning()
   {
      IDLStringSequence sequence = new IDLStringSequence(INITIAL_CAPACITY);
      sequence.add("First");
      sequence.add("Second");
      sequence.add("Third");

      StringBuilder removed = sequence.remove(0);

      assertEquals("First", removed.toString());
      assertEquals(2, sequence.size());
      assertEquals("Second", sequence.getAsString(0));
      assertEquals("Third", sequence.getAsString(1));
   }

   @Test
   public void testGet()
   {
      IDLStringSequence sequence = new IDLStringSequence(INITIAL_CAPACITY);
      sequence.add("TestA");
      sequence.add("TestB");

      assertEquals("TestA", sequence.get(0).toString());
      assertEquals("TestB", sequence.get(1).toString());
   }

   @Test
   public void testGetAsString()
   {
      IDLStringSequence sequence = new IDLStringSequence(INITIAL_CAPACITY);
      sequence.add("Hello");
      sequence.add("World");

      assertEquals("Hello", sequence.getAsString(0));
      assertEquals("World", sequence.getAsString(1));
   }

   @Test
   public void testIterator()
   {
      IDLStringSequence sequence = new IDLStringSequence(INITIAL_CAPACITY);
      sequence.add("One");
      sequence.add("Two");
      sequence.add("Three");

      int index = 0;
      String[] expected = {"One", "Two", "Three"};
      for (String str : sequence)
      {
         assertEquals(expected[index++], str);
      }
      assertEquals(3, index);
   }

   @Test
   public void testIteratorRemove()
   {
      IDLStringSequence sequence = new IDLStringSequence(INITIAL_CAPACITY);
      sequence.add("First");
      sequence.add("Second");
      sequence.add("Third");

      var iterator = sequence.iterator();
      iterator.next();
      iterator.next();
      iterator.remove();

      assertEquals(2, sequence.size());
      assertEquals("First", sequence.getAsString(0));
      assertEquals("Third", sequence.getAsString(1));
   }

   @Test
   public void testClear()
   {
      IDLStringSequence sequence = new IDLStringSequence(INITIAL_CAPACITY);
      sequence.add("A");
      sequence.add("B");

      int originalCapacity = sequence.capacity();
      sequence.clear();

      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());
   }

   @Test
   public void testSet()
   {
      IDLStringSequence source = new IDLStringSequence(INITIAL_CAPACITY);
      source.add("X");
      source.add("Y");
      source.add("Z");

      IDLStringSequence target = new IDLStringSequence();
      target.set(source);

      assertEquals(source.size(), target.size());
      for (int i = 0; i < source.size(); ++i)
      {
         assertEquals(source.getAsString(i), target.getAsString(i));
      }

      // Verify source is unaffected
      assertEquals(3, source.size());
   }

   @Test
   public void testToStringArray()
   {
      IDLStringSequence sequence = new IDLStringSequence(INITIAL_CAPACITY);
      sequence.add("Alpha");
      sequence.add("Beta");
      sequence.add("Gamma");

      String[] array = sequence.toStringArray();

      assertEquals(3, array.length);
      assertEquals("Alpha", array[0]);
      assertEquals("Beta", array[1]);
      assertEquals("Gamma", array[2]);
   }

   @Test
   public void testEmptySequence()
   {
      IDLStringSequence sequence = new IDLStringSequence();

      assertEquals(0, sequence.capacity());
      assertEquals(0, sequence.size());
      assertDoesNotThrow(sequence::clear);
   }

   @Test
   public void testAddBeyondCapacity()
   {
      IDLStringSequence sequence = new IDLStringSequence(INITIAL_CAPACITY);

      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         sequence.add("Item" + i);
      }

      sequence.add("Extra");

      assertEquals(INITIAL_CAPACITY + 1, sequence.size());
      assertTrue(sequence.capacity() > INITIAL_CAPACITY);
      assertEquals("Extra", sequence.getAsString(INITIAL_CAPACITY));
   }
}
