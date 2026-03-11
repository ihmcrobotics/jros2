package us.ihmc.fastddsjava.cdr.idl;

import org.junit.jupiter.api.Test;
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
         msg.setData(i * 10);
         sequence.add(msg);
      }

      assertEquals(INITIAL_CAPACITY, sequence.size());
      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         assertEquals(i * 10, sequence.get(i).getData());
      }
   }

   @Test
   public void testAddWithAutoCreate()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);

      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         sequence.add().setData(i + 100);
      }

      assertEquals(INITIAL_CAPACITY, sequence.size());
      for (int i = 0; i < INITIAL_CAPACITY; ++i)
      {
         assertEquals(i + 100, sequence.get(i).getData());
      }
   }

   @Test
   public void testRemove()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);
      sequence.add().setData(10);
      sequence.add().setData(20);
      sequence.add().setData(30);

      TestIDLMsg removed = sequence.remove();

      assertEquals(30, removed.getData());
      assertEquals(2, sequence.size());
   }

   @Test
   public void testRemoveAtIndex()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);
      sequence.add().setData(100);
      sequence.add().setData(200);
      sequence.add().setData(300);
      sequence.add().setData(400);

      sequence.remove(1);

      assertEquals(3, sequence.size());
      assertEquals(100, sequence.get(0).getData());
      assertEquals(300, sequence.get(1).getData()); // Shifted from index 2
      assertEquals(400, sequence.get(2).getData()); // Shifted from index 3
   }

   @Test
   public void testRemoveAtBeginning()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);
      sequence.add().setData(50);
      sequence.add().setData(60);
      sequence.add().setData(70);

      sequence.remove(0);

      assertEquals(2, sequence.size());
      assertEquals(60, sequence.get(0).getData());
      assertEquals(70, sequence.get(1).getData());
   }

   @Test
   public void testIterator()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);
      sequence.add().setData(111);
      sequence.add().setData(222);
      sequence.add().setData(333);

      int index = 0;
      int[] expected = {111, 222, 333};
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
      sequence.add().setData(1000);
      sequence.add().setData(2000);
      sequence.add().setData(3000);

      var iterator = sequence.iterator();
      iterator.next();
      iterator.next();
      iterator.remove();

      assertEquals(2, sequence.size());
      assertEquals(1000, sequence.get(0).getData());
      assertEquals(3000, sequence.get(1).getData());
   }

   @Test
   public void testClear()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);
      sequence.add().setData(42);
      sequence.add().setData(84);

      int originalCapacity = sequence.capacity();
      sequence.clear();

      assertEquals(0, sequence.size());
      assertEquals(originalCapacity, sequence.capacity());
   }

   @Test
   public void testSet()
   {
      IDLObjectSequence<TestIDLMsg> source = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);
      source.add().setData(500);
      source.add().setData(600);
      source.add().setData(700);

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
         sequence.add().setData(i);
      }

      TestIDLMsg newMsg = new TestIDLMsg();
      newMsg.setData(999);
      sequence.add(newMsg);

      assertEquals(INITIAL_CAPACITY + 1, sequence.size());
      assertTrue(sequence.capacity() > INITIAL_CAPACITY);
      assertEquals(999, sequence.get(INITIAL_CAPACITY).getData());
   }

   @Test
   public void testRemoveAtEnd()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);
      sequence.add().setData(11);
      sequence.add().setData(22);
      sequence.add().setData(33);
      sequence.add().setData(44);

      sequence.remove(3);

      assertEquals(3, sequence.size());
      assertEquals(11, sequence.get(0).getData());
      assertEquals(22, sequence.get(1).getData());
      assertEquals(33, sequence.get(2).getData());
   }

   @Test
   public void testRemoveAtMiddle()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);
      sequence.add().setData(10);
      sequence.add().setData(20);
      sequence.add().setData(30);
      sequence.add().setData(40);
      sequence.add().setData(50);

      sequence.remove(2);

      assertEquals(4, sequence.size());
      assertEquals(10, sequence.get(0).getData());
      assertEquals(20, sequence.get(1).getData());
      assertEquals(40, sequence.get(2).getData());
      assertEquals(50, sequence.get(3).getData());
   }

   @Test
   public void testObjectReuse()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);

      // Add elements using auto-create
      for (int i = 0; i < 5; ++i)
      {
         sequence.add().setData(i);
      }

      // Remove from middle
      sequence.remove(2);

      assertEquals(4, sequence.size());

      // Add a new element - should reuse the removed object
      TestIDLMsg reused = sequence.add();
      reused.setData(999);

      assertEquals(5, sequence.size());
      assertEquals(999, sequence.get(4).getData());

      // Verify the sequence is correct
      assertEquals(0, sequence.get(0).getData());
      assertEquals(1, sequence.get(1).getData());
      assertEquals(3, sequence.get(2).getData());
      assertEquals(4, sequence.get(3).getData());
      assertEquals(999, sequence.get(4).getData());
   }

   @Test
   public void testMultipleRemoves()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);

      for (int i = 0; i < 10; ++i)
      {
         sequence.add().setData(i * 10);
      }

      // Starting: [0, 10, 20, 30, 40, 50, 60, 70, 80, 90]
      sequence.remove(5); // Remove 50 -> [0, 10, 20, 30, 40, 60, 70, 80, 90]
      sequence.remove(3); // Remove 30 -> [0, 10, 20, 40, 60, 70, 80, 90]
      sequence.remove(1); // Remove 10 -> [0, 20, 40, 60, 70, 80, 90]

      assertEquals(7, sequence.size());
      assertEquals(0, sequence.get(0).getData());
      assertEquals(20, sequence.get(1).getData());
      assertEquals(40, sequence.get(2).getData());
      assertEquals(60, sequence.get(3).getData());
      assertEquals(70, sequence.get(4).getData());
      assertEquals(80, sequence.get(5).getData());
      assertEquals(90, sequence.get(6).getData());
   }

   @Test
   public void testNegativeAndEdgeCaseValues()
   {
      IDLObjectSequence<TestIDLMsg> sequence = new IDLObjectSequence<>(INITIAL_CAPACITY, TestIDLMsg.class);

      sequence.add().setData(Integer.MAX_VALUE);
      sequence.add().setData(Integer.MIN_VALUE);
      sequence.add().setData(0);
      sequence.add().setData(-1);
      sequence.add().setData(12345);

      assertEquals(5, sequence.size());
      assertEquals(Integer.MAX_VALUE, sequence.get(0).getData());
      assertEquals(Integer.MIN_VALUE, sequence.get(1).getData());
      assertEquals(0, sequence.get(2).getData());
      assertEquals(-1, sequence.get(3).getData());
      assertEquals(12345, sequence.get(4).getData());

      sequence.remove(1);

      assertEquals(4, sequence.size());
      assertEquals(Integer.MAX_VALUE, sequence.get(0).getData());
      assertEquals(0, sequence.get(1).getData());
      assertEquals(-1, sequence.get(2).getData());
      assertEquals(12345, sequence.get(3).getData());
   }
}
