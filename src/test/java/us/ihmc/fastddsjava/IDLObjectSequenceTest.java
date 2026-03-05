package us.ihmc.fastddsjava;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.idl.IDLObjectSequence;
import us.ihmc.fastddsjava.msg.TestIDLMsg;

import static org.junit.jupiter.api.Assertions.*;

public class IDLObjectSequenceTest
{
   private static final int INITIAL_CAPACITY = 8;

   @Test
   public void testInitialization()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);

      assertEquals(0, sequence.size());
      assertEquals(INITIAL_CAPACITY, sequence.capacity());
   }

   @Test
   public void testAddWithElement()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);

      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         TestIDLMsg msg = new TestIDLMsg();
         msg.setData(i % 2 == 0);
         sequence.add(msg);
      }

      assertEquals(INITIAL_CAPACITY, sequence.size());
      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         assertEquals(i % 2 == 0, sequence.get(i).getData());
      }
   }

   @Test
   public void testAddWithAutoCreate()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);

      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         sequence.add().setData(i % 2 == 0);
      }

      assertEquals(INITIAL_CAPACITY, sequence.size());
      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         assertEquals(i % 2 == 0, sequence.get(i).getData());
      }
   }

   @Test
   public void testRemove()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);
      sequence.add().setData(true);
      sequence.add().setData(false);
      sequence.add().setData(true);

      TestIDLMsg removed = sequence.remove();

      assertTrue(removed.getData());
      assertEquals(2, sequence.size());
   }

   @Test
   public void testRemoveAtIndex()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);
      sequence.add().setData(true);
      sequence.add().setData(false);
      sequence.add().setData(true);
      sequence.add().setData(false);

      TestIDLMsg removed = sequence.remove(1);

      assertFalse(removed.getData());
      assertEquals(3, sequence.size());
      assertTrue(sequence.get(0).getData());
      assertTrue(sequence.get(1).getData()); // Shifted from index 2
      assertFalse(sequence.get(2).getData()); // Shifted from index 3
   }

   @Test
   public void testRemoveAtBeginning()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);
      sequence.add().setData(true);
      sequence.add().setData(false);
      sequence.add().setData(true);

      TestIDLMsg removed = sequence.remove(0);

      assertTrue(removed.getData());
      assertEquals(2, sequence.size());
      assertFalse(sequence.get(0).getData());
      assertTrue(sequence.get(1).getData());
   }

   @Test
   public void testIterator()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);
      sequence.add().setData(true);
      sequence.add().setData(false);
      sequence.add().setData(true);

      int index = 0;
      boolean[] expected = {true, false, true};
      for (TestIDLMsg msg : sequence)
      {
         assertEquals(expected[index++], msg.getData());
      }
      assertEquals(3, index);
   }

   @Test
   public void testIteratorRemove()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);
      sequence.add().setData(true);
      sequence.add().setData(false);
      sequence.add().setData(true);

      var iterator = sequence.iterator();
      iterator.next();
      iterator.next();
      iterator.remove();

      assertEquals(2, sequence.size());
      assertTrue(sequence.get(0).getData());
      assertTrue(sequence.get(1).getData());
   }

   @Test
   public void testClear()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);
      sequence.add().setData(true);
      sequence.add().setData(false);

      int originalCapacity = sequence.capacity();
      sequence.clear();

      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());
   }

   @Test
   public void testSet()
   {
      IDLObjectSequence<TestIDLMsg> source = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);
      source.add().setData(true);
      source.add().setData(false);
      source.add().setData(true);

      IDLObjectSequence<TestIDLMsg> target = new IDLObjectSequence<>(TestIDLMsg.class);
      target.set(source);

      assertEquals(source.size(), target.size());
      for (int i = 0; i < source.size(); ++i)
      {
         assertEquals(source.get(i).getData(), target.get(i).getData());
      }

      // Verify source is unaffected
      assertEquals(3, source.size());
   }

   @Test
   public void testEmptySequence()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(TestIDLMsg.class);

      assertEquals(0, sequence.capacity());
      assertEquals(0, sequence.size());
      assertDoesNotThrow(sequence::clear);
   }

   @Test
   public void testAddBeyondCapacity()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);

      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         sequence.add().setData(i % 2 == 0);
      }

      TestIDLMsg newMsg = new TestIDLMsg();
      newMsg.setData(true);
      sequence.add(newMsg);

      assertEquals(INITIAL_CAPACITY + 1, sequence.size());
      assertTrue(sequence.capacity() > INITIAL_CAPACITY);
      assertTrue(sequence.get(INITIAL_CAPACITY).getData());
   }
}
