/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.fastddsjava.cdr.idl;

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.CDRDeserializationException;
import us.ihmc.fastddsjava.msg.TestIDLMsg;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Upper bounds, wire-length validation and bulk wire format of all IDL sequence types.
 */
public class IDLSequenceBoundsAndWireTest
{
   private static final int BOUND = 3;

   /** Creates bounded sequences of every type and a way to fill them with {@code n} elements. */
   private record SequenceFactory(String name, Supplier<IDLSequence<?>> bounded, java.util.function.Function<Integer, IDLSequence<?>> unboundedFilled,
                                  java.util.function.BiConsumer<IDLSequence<?>, Integer> addOne)
   {
   }

   private static final List<SequenceFactory> FACTORIES = List.of(new SequenceFactory("byte",
                                                                                      () -> new IDLByteSequence(0, BOUND),
                                                                                      n -> fill(new IDLByteSequence(), n, (s, i) -> s.add((byte) (i - 3))),
                                                                                      (s, i) -> ((IDLByteSequence) s).add((byte) (int) i)),
                                                                  new SequenceFactory("char",
                                                                                      () -> new IDLCharSequence(0, BOUND),
                                                                                      n -> fill(new IDLCharSequence(), n, (s, i) -> s.add((char) (200 + i % 50))),
                                                                                      (s, i) -> ((IDLCharSequence) s).add((char) (int) i)),
                                                                  new SequenceFactory("bool",
                                                                                      () -> new IDLBoolSequence(0, BOUND),
                                                                                      n -> fill(new IDLBoolSequence(), n, (s, i) -> s.add(i % 3 == 0)),
                                                                                      (s, i) -> ((IDLBoolSequence) s).add(true)),
                                                                  new SequenceFactory("short",
                                                                                      () -> new IDLShortSequence(0, BOUND),
                                                                                      n -> fill(new IDLShortSequence(),
                                                                                                n,
                                                                                                (s, i) -> s.add((short) (60000 + i))),
                                                                                      (s, i) -> ((IDLShortSequence) s).add((short) (int) i)),
                                                                  new SequenceFactory("int",
                                                                                      () -> new IDLIntSequence(0, BOUND),
                                                                                      n -> fill(new IDLIntSequence(), n, (s, i) -> s.add(i * 1000003)),
                                                                                      (s, i) -> ((IDLIntSequence) s).add(i)),
                                                                  new SequenceFactory("long",
                                                                                      () -> new IDLLongSequence(0, BOUND),
                                                                                      n -> fill(new IDLLongSequence(), n, (s, i) -> s.add(-i * 10000000019L)),
                                                                                      (s, i) -> ((IDLLongSequence) s).add(i)),
                                                                  new SequenceFactory("float",
                                                                                      () -> new IDLFloatSequence(0, BOUND),
                                                                                      n -> fill(new IDLFloatSequence(), n, (s, i) -> s.add(i * 0.5f)),
                                                                                      (s, i) -> ((IDLFloatSequence) s).add(i)),
                                                                  new SequenceFactory("double",
                                                                                      () -> new IDLDoubleSequence(0, BOUND),
                                                                                      n -> fill(new IDLDoubleSequence(), n, (s, i) -> s.add(i / 3.0)),
                                                                                      (s, i) -> ((IDLDoubleSequence) s).add(i)),
                                                                  new SequenceFactory("string",
                                                                                      () -> new IDLStringSequence(0, BOUND),
                                                                                      n -> fill(new IDLStringSequence(), n, (s, i) -> s.add("sé" + i)),
                                                                                      (s, i) -> ((IDLStringSequence) s).add("x")),
                                                                  new SequenceFactory("wstring",
                                                                                      () -> new IDLWStringSequence(0, BOUND),
                                                                                      n -> fill(new IDLWStringSequence(),
                                                                                                n,
                                                                                                (s, i) -> s.add("w😀" + i)),
                                                                                      (s, i) -> ((IDLWStringSequence) s).add("x")),
                                                                  new SequenceFactory("object",
                                                                                      () -> new IDLObjectSequence<>(0, BOUND, Element.class),
                                                                                      n -> fill(new IDLObjectSequence<>(Element.class),
                                                                                                n,
                                                                                                (s, i) -> s.add().set(i * 7, "e\u00e8" + i)),
                                                                                      (s, i) -> ((IDLObjectSequence<?>) s).add()));

   /** A nested message with a fixed-size and a variable-size member. */
   public static class Element implements us.ihmc.fastddsjava.cdr.CDRSerializable
   {
      private int id;
      private final StringBuilder label = new StringBuilder();

      public Element()
      {
      }

      public void set(int id, String label)
      {
         this.id = id;
         this.label.setLength(0);
         this.label.append(label);
      }

      @Override
      public int calculateSizeBytes(int currentAlignment)
      {
         int initialAlignment = currentAlignment;
         currentAlignment += CDRBuffer.alignment(currentAlignment, 4) + 4;
         currentAlignment += CDRBuffer.alignment(currentAlignment, 4) + 4 + CDRBuffer.utf8Length(label) + 1;
         return currentAlignment - initialAlignment;
      }

      @Override
      public void serialize(CDRBuffer buffer)
      {
         buffer.writeInt(id);
         buffer.writeString(label);
      }

      @Override
      public void deserialize(CDRBuffer buffer)
      {
         id = buffer.readInt();
         buffer.readString(label);
      }

      @Override
      public String toString()
      {
         return id + ":" + label;
      }
   }

   private interface Filler<S>
   {
      void add(S sequence, int index);
   }

   private static <S extends IDLSequence<?>> S fill(S sequence, int n, Filler<S> filler)
   {
      for (int i = 0; i < n; i++)
         filler.add(sequence, i);
      return sequence;
   }

   @Test
   public void testAddBeyondUpperBoundThrows()
   {
      for (SequenceFactory factory : FACTORIES)
      {
         IDLSequence<?> sequence = factory.bounded().get();
         for (int i = 0; i < BOUND; i++)
            factory.addOne().accept(sequence, i);
         assertEquals(BOUND, sequence.size(), factory.name());

         IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> factory.addOne().accept(sequence, 9), factory.name());
         assertTrue(exception.getMessage().contains("exceeds upper bound"), exception.getMessage());
         assertEquals(BOUND, sequence.size(), factory.name());
         assertEquals(BOUND, sequence.capacity(), factory.name());
         assertFalse(sequence.ensureMinCapacity(BOUND + 1), factory.name());
         assertEquals(BOUND, sequence.capacity(), "ensureMinCapacity must not grow beyond the bound: " + factory.name());
         assertTrue(sequence.ensureMinCapacity(BOUND), factory.name());
      }
   }

   @Test
   public void testAddAllAndSetBeyondUpperBoundThrowWithoutSideEffects()
   {
      IDLDoubleSequence doubles = new IDLDoubleSequence(0, BOUND);
      doubles.add(1.0);
      assertThrows(IllegalArgumentException.class, () -> doubles.addAll(new double[] {2, 3, 4}));
      assertEquals(1, doubles.size());
      assertEquals(1.0, doubles.get(0));
      assertThrows(IllegalArgumentException.class, () -> doubles.putAt(BOUND, 5.0));
      doubles.putAt(2, 5.0);
      assertEquals(3, doubles.size());

      IDLDoubleSequence big = new IDLDoubleSequence();
      big.addAll(new double[] {1, 2, 3, 4});
      IDLDoubleSequence target = new IDLDoubleSequence(0, BOUND);
      target.add(9.0);
      assertThrows(IllegalArgumentException.class, () -> target.set(big));
      assertEquals(1, target.size());
      assertEquals(9.0, target.get(0));

      IDLBoolSequence bools = new IDLBoolSequence(0, BOUND);
      assertThrows(IllegalArgumentException.class, () -> bools.addAll(new boolean[] {true, true, true, true}));
      assertEquals(0, bools.size());

      IDLStringSequence strings = new IDLStringSequence(0, BOUND);
      assertThrows(IllegalArgumentException.class, () -> strings.addAll(new String[] {"a", "b", "c", "d"}));
      assertEquals(0, strings.size());
      assertThrows(IllegalArgumentException.class, () -> strings.addAll(List.of("a", "b", "c", "d")));
      IDLStringSequence manyStrings = new IDLStringSequence();
      manyStrings.addAll(new String[] {"a", "b", "c", "d"});
      assertThrows(IllegalArgumentException.class, () -> strings.set(manyStrings));

      IDLObjectSequence<TestIDLMsg> objects = new IDLObjectSequence<>(0, BOUND, TestIDLMsg.class);
      assertThrows(IllegalArgumentException.class, () -> objects.addAll(new TestIDLMsg[4]));
      assertThrows(IllegalArgumentException.class, () -> objects.addAll(List.of(new TestIDLMsg(), new TestIDLMsg(), new TestIDLMsg(), new TestIDLMsg())));
      IDLObjectSequence<TestIDLMsg> manyObjects = new IDLObjectSequence<>(TestIDLMsg.class);
      for (int i = 0; i < 4; i++)
         manyObjects.add();
      assertThrows(IllegalArgumentException.class, () -> objects.set(manyObjects));
      assertEquals(0, objects.size());
   }

   @Test
   public void testSerializeOverBoundThrows()
   {
      // The public API cannot overfill a bounded sequence; a subclass that reports a larger size must still be refused
      IDLStringSequence overfilled = new IDLStringSequence(0, BOUND)
      {
         @Override
         public int size()
         {
            return BOUND + 1;
         }
      };
      CDRBuffer buffer = new CDRBuffer();
      buffer.writePayloadHeader();
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> overfilled.serialize(buffer));
      assertTrue(exception.getMessage().startsWith("array size exceeds upper bound"), exception.getMessage());
      assertEquals(4, buffer.getBufferUnsafe().position(), "Nothing may be written");
   }

   @Test
   public void testRoundTripAtEveryOffsetMatchesCalculatedSize()
   {
      for (SequenceFactory factory : FACTORIES)
      {
         for (int n : new int[] {0, 1, 2, 5, 17})
         {
            IDLSequence<?> sequence = factory.unboundedFilled().apply(n);
            for (int offset = 0; offset < 8; offset++)
            {
               String context = factory.name() + " n=" + n + " offset=" + offset;
               CDRBuffer buffer = new CDRBuffer();
               buffer.writePayloadHeader();
               for (int i = 0; i < offset; i++)
                  buffer.writeByte((byte) 0x7F);
               sequence.serialize(buffer);
               int size = buffer.getBufferUnsafe().position();
               assertEquals(size - 4 - offset, sequence.calculateSizeBytes(offset), "calculateSizeBytes: " + context);

               IDLSequence<?> result = factory.unboundedFilled().apply(3); // reused with stale content
               buffer.beginRead(size);
               buffer.readPayloadHeader();
               for (int i = 0; i < offset; i++)
                  buffer.readByte();
               result.deserialize(buffer);
               assertEquals(size, buffer.getBufferUnsafe().position(), context);
               assertEquals(sequence.toString(), result.toString(), context);
            }
         }
      }
   }

   @Test
   public void testBigEndianSequences()
   {
      ByteBuffer payload = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN);
      payload.put(new byte[] {0, 0, 0, 0}); // CDR_BE
      payload.putInt(2).putInt(0).putDouble(1.5).putDouble(-2.0); // count, padding to 8, values
      payload.putInt(3).putShort((short) 0xFFFF).putShort((short) 2).putShort((short) 3);

      CDRBuffer buffer = new CDRBuffer();
      buffer.ensureRemainingCapacity(payload.position());
      buffer.getBufferUnsafe().put(payload.array(), 0, payload.position());
      buffer.beginRead(payload.position());
      buffer.readPayloadHeader();

      IDLDoubleSequence doubles = new IDLDoubleSequence();
      doubles.deserialize(buffer);
      assertEquals("[1.5, -2.0]", doubles.toString());
      IDLShortSequence shorts = new IDLShortSequence();
      shorts.deserialize(buffer);
      assertEquals(65535, Short.toUnsignedInt(shorts.get(0)));
      assertEquals(3, shorts.get(2));
   }

   @Test
   public void testMalformedLengthsAreRejectedBeforeAllocation()
   {
      ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
      long threadId = Thread.currentThread().getId();

      int[] lengths = {-1, Integer.MAX_VALUE, 0x10000000, 100};
      for (SequenceFactory factory : FACTORIES)
      {
         for (int length : lengths)
         {
            CDRBuffer buffer = payloadWithLength(length);
            IDLSequence<?> sequence = factory.unboundedFilled().apply(0);
            long before = threadMXBean.getThreadAllocatedBytes(threadId);
            assertThrows(CDRDeserializationException.class, () -> sequence.deserialize(buffer), factory.name() + " length " + length);
            long allocated = threadMXBean.getThreadAllocatedBytes(threadId) - before;
            assertTrue(allocated < 256 * 1024, factory.name() + " length " + length + " allocated " + allocated + " bytes");
            assertEquals(0, sequence.capacity(), factory.name() + " must not grow for a rejected length");
         }
      }
   }

   @Test
   public void testOverBoundLengthIsRejected()
   {
      for (SequenceFactory factory : FACTORIES)
      {
         IDLSequence<?> unbounded = factory.unboundedFilled().apply(BOUND + 1);
         CDRBuffer buffer = new CDRBuffer();
         buffer.writePayloadHeader();
         unbounded.serialize(buffer);
         int size = buffer.getBufferUnsafe().position();
         buffer.beginRead(size);
         buffer.readPayloadHeader();

         IDLSequence<?> bounded = factory.bounded().get();
         CDRDeserializationException exception = assertThrows(CDRDeserializationException.class, () -> bounded.deserialize(buffer), factory.name());
         assertTrue(exception.getMessage().contains("upper bound"), exception.getMessage());
         assertEquals(0, bounded.size());
      }
   }

   @Test
   public void testInvalidBooleanInSequence()
   {
      CDRBuffer buffer = new CDRBuffer();
      buffer.ensureRemainingCapacity(16);
      buffer.getBufferUnsafe().put(new byte[] {0, 1, 0, 0, 3, 0, 0, 0, 1, 0, 7});
      buffer.beginRead(11);
      buffer.readPayloadHeader();
      assertThrows(CDRDeserializationException.class, () -> new IDLBoolSequence().deserialize(buffer));
   }

   @Test
   public void testUtf8StringSequenceSize()
   {
      IDLStringSequence strings = new IDLStringSequence();
      strings.add("€");
      strings.add("😀");
      // count(4) + [len(4) + 3 + NUL] + pad(0) + [len(4) + 4 + NUL]
      assertEquals(4 + 8 + 9, strings.calculateSizeBytes(0));
      assertEquals(8, strings.elementSizeBytes(0, 0));

      IDLWStringSequence wstrings = new IDLWStringSequence();
      wstrings.add("😀");
      assertEquals(4 + 4 + 8, wstrings.calculateSizeBytes(0));
   }

   @Test
   public void testDeserializedStringSequenceIsIndependentOfSetSource()
   {
      IDLStringSequence source = new IDLStringSequence();
      source.add("abc");
      IDLStringSequence copy = new IDLStringSequence();
      copy.set(source);
      source.get(0).setLength(0);
      assertEquals("abc", copy.getAsString(0));
   }

   @Test
   public void testRemoveShiftsAndChecksIndex()
   {
      IDLIntSequence ints = new IDLIntSequence();
      ints.addAll(new int[] {1, 2, 3, 4});
      assertEquals(2, ints.remove(1));
      assertEquals("[1, 3, 4]", ints.toString());
      assertThrows(IndexOutOfBoundsException.class, () -> ints.remove(3));
      assertThrows(IndexOutOfBoundsException.class, () -> ints.remove(-1));

      IDLBoolSequence bools = new IDLBoolSequence();
      bools.addAll(new boolean[] {true, false, true});
      assertFalse(bools.remove(1));
      assertEquals("[true, true]", bools.toString());
   }

   private static CDRBuffer payloadWithLength(int length)
   {
      CDRBuffer buffer = new CDRBuffer();
      buffer.ensureRemainingCapacity(32);
      ByteBuffer bytes = buffer.getBufferUnsafe();
      bytes.put(new byte[] {0, 1, 0, 0});
      bytes.order(ByteOrder.LITTLE_ENDIAN).putInt(length);
      bytes.put(new byte[] {1, 0, 0, 0, 1, 0, 0, 0});
      buffer.beginRead(bytes.position());
      buffer.readPayloadHeader();
      return buffer;
   }
}
