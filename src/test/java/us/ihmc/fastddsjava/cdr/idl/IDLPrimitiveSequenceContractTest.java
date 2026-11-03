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

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.CDRBuffer;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The parts of the primitive sequence API that the per-type tests do not cover, checked for every primitive sequence type
 * with the same expectations: {@code putAt}, {@code addAll}, {@code remove(int)} bounds, growth limited by the upper bound
 * ({@code ensureMinCapacity}), {@code set} from a larger sequence, the constructor checks, and the element-wise CDR
 * methods ({@code writeElement}/{@code readElement}), which must produce the same bytes as the bulk {@code serialize}.
 */
public class IDLPrimitiveSequenceContractTest
{
   /**
    * Uniform access to one primitive sequence type. Values are passed as {@code long} and converted to the element type
    * (small values only, so every type represents them exactly).
    */
   abstract static class Type<S extends IDLSequence<S>>
   {
      final String name;
      final int elementSizeBytes;

      Type(String name, int elementSizeBytes)
      {
         this.name = name;
         this.elementSizeBytes = elementSizeBytes;
      }

      abstract S create(int capacity, int maxSize);

      abstract S create();

      abstract void add(S sequence, long value);

      abstract void putAt(S sequence, int index, long value);

      abstract void addAll(S sequence, long... values);

      abstract long get(S sequence, int index);

      abstract long remove(S sequence);

      abstract long remove(S sequence, int index);

      abstract Buffer buffer(S sequence);

      long[] values(S sequence)
      {
         long[] values = new long[sequence.size()];
         for (int i = 0; i < values.length; ++i)
         {
            values[i] = get(sequence, i);
         }
         return values;
      }

      S of(long... values)
      {
         S sequence = create();
         for (long value : values)
         {
            add(sequence, value);
         }
         return sequence;
      }

      @Override
      public String toString()
      {
         return name;
      }
   }

   /** Creates a {@link Type} from method references of one sequence class. */
   private static <S extends IDLSequence<S>, A> Type<S> type(String name,
                                                            int elementSizeBytes,
                                                            BiFunction<Integer, Integer, S> constructor,
                                                            java.util.function.Supplier<S> unbounded,
                                                            ElementAccess<S> access,
                                                            IntFunction<A> newArray,
                                                            ArraySetter<A> arraySetter,
                                                            BulkAdd<S, A> bulkAdd)
   {
      return new Type<>(name, elementSizeBytes)
      {
         @Override
         S create(int capacity, int maxSize)
         {
            return constructor.apply(capacity, maxSize);
         }

         @Override
         S create()
         {
            return unbounded.get();
         }

         @Override
         void add(S sequence, long value)
         {
            access.add(sequence, value);
         }

         @Override
         void putAt(S sequence, int index, long value)
         {
            access.putAt(sequence, index, value);
         }

         @Override
         void addAll(S sequence, long... values)
         {
            A array = newArray.apply(values.length);
            for (int i = 0; i < values.length; ++i)
            {
               arraySetter.set(array, i, values[i]);
            }
            bulkAdd.addAll(sequence, array);
         }

         @Override
         long get(S sequence, int index)
         {
            return access.get(sequence, index);
         }

         @Override
         long remove(S sequence)
         {
            return access.remove(sequence);
         }

         @Override
         long remove(S sequence, int index)
         {
            return access.remove(sequence, index);
         }

         @Override
         Buffer buffer(S sequence)
         {
            return access.buffer(sequence);
         }
      };
   }

   interface ElementAccess<S>
   {
      void add(S sequence, long value);

      void putAt(S sequence, int index, long value);

      long get(S sequence, int index);

      long remove(S sequence);

      long remove(S sequence, int index);

      Buffer buffer(S sequence);
   }

   interface ArraySetter<A>
   {
      void set(A array, int index, long value);
   }

   interface BulkAdd<S, A>
   {
      void addAll(S sequence, A values);
   }

   /** Runs {@code check} for every type; a failure names the type. */
   private static void forEachType(java.util.function.Consumer<Type<?>> check)
   {
      for (Type<?> type : types())
      {
         try
         {
            check.accept(type);
         }
         catch (AssertionError | RuntimeException e)
         {
            throw new AssertionError(type + " sequence: " + e.getMessage(), e);
         }
      }
   }

   static List<Type<?>> types()
   {
      return List.of(IDLPrimitiveSequenceContractTest.<IDLByteSequence, byte[]>type("byte", 1, IDLByteSequence::new, IDLByteSequence::new, new ElementAccess<IDLByteSequence>()
      {
         public void add(IDLByteSequence s, long v)
         {
            s.add((byte) v);
         }

         public void putAt(IDLByteSequence s, int i, long v)
         {
            s.putAt(i, (byte) v);
         }

         public long get(IDLByteSequence s, int i)
         {
            return s.get(i);
         }

         public long remove(IDLByteSequence s)
         {
            return s.remove();
         }

         public long remove(IDLByteSequence s, int i)
         {
            return s.remove(i);
         }

         public Buffer buffer(IDLByteSequence s)
         {
            return s.getBuffer();
         }
      }, byte[]::new, (a, i, v) -> a[i] = (byte) v, IDLByteSequence::addAll),
                     IDLPrimitiveSequenceContractTest.<IDLCharSequence, char[]>type("char", 1, IDLCharSequence::new, IDLCharSequence::new, new ElementAccess<IDLCharSequence>()
      {
         public void add(IDLCharSequence s, long v)
         {
            s.add((char) v);
         }

         public void putAt(IDLCharSequence s, int i, long v)
         {
            s.putAt(i, (char) v);
         }

         public long get(IDLCharSequence s, int i)
         {
            return s.get(i);
         }

         public long remove(IDLCharSequence s)
         {
            return s.remove();
         }

         public long remove(IDLCharSequence s, int i)
         {
            return s.remove(i);
         }

         public Buffer buffer(IDLCharSequence s)
         {
            return s.getBuffer();
         }
      }, char[]::new, (a, i, v) -> a[i] = (char) v, IDLCharSequence::addAll),
                     IDLPrimitiveSequenceContractTest.<IDLShortSequence, short[]>type("short", 2, IDLShortSequence::new, IDLShortSequence::new, new ElementAccess<IDLShortSequence>()
      {
         public void add(IDLShortSequence s, long v)
         {
            s.add((short) v);
         }

         public void putAt(IDLShortSequence s, int i, long v)
         {
            s.putAt(i, (short) v);
         }

         public long get(IDLShortSequence s, int i)
         {
            return s.get(i);
         }

         public long remove(IDLShortSequence s)
         {
            return s.remove();
         }

         public long remove(IDLShortSequence s, int i)
         {
            return s.remove(i);
         }

         public Buffer buffer(IDLShortSequence s)
         {
            return s.getBuffer();
         }
      }, short[]::new, (a, i, v) -> a[i] = (short) v, IDLShortSequence::addAll),
                     IDLPrimitiveSequenceContractTest.<IDLIntSequence, int[]>type("int", 4, IDLIntSequence::new, IDLIntSequence::new, new ElementAccess<IDLIntSequence>()
      {
         public void add(IDLIntSequence s, long v)
         {
            s.add((int) v);
         }

         public void putAt(IDLIntSequence s, int i, long v)
         {
            s.putAt(i, (int) v);
         }

         public long get(IDLIntSequence s, int i)
         {
            return s.get(i);
         }

         public long remove(IDLIntSequence s)
         {
            return s.remove();
         }

         public long remove(IDLIntSequence s, int i)
         {
            return s.remove(i);
         }

         public Buffer buffer(IDLIntSequence s)
         {
            return s.getBuffer();
         }
      }, int[]::new, (a, i, v) -> a[i] = (int) v, IDLIntSequence::addAll),
                     IDLPrimitiveSequenceContractTest.<IDLLongSequence, long[]>type("long", 8, IDLLongSequence::new, IDLLongSequence::new, new ElementAccess<IDLLongSequence>()
      {
         public void add(IDLLongSequence s, long v)
         {
            s.add(v);
         }

         public void putAt(IDLLongSequence s, int i, long v)
         {
            s.putAt(i, v);
         }

         public long get(IDLLongSequence s, int i)
         {
            return s.get(i);
         }

         public long remove(IDLLongSequence s)
         {
            return s.remove();
         }

         public long remove(IDLLongSequence s, int i)
         {
            return s.remove(i);
         }

         public Buffer buffer(IDLLongSequence s)
         {
            return s.getBuffer();
         }
      }, long[]::new, (a, i, v) -> a[i] = v, IDLLongSequence::addAll),
                     IDLPrimitiveSequenceContractTest.<IDLFloatSequence, float[]>type("float", 4, IDLFloatSequence::new, IDLFloatSequence::new, new ElementAccess<IDLFloatSequence>()
      {
         public void add(IDLFloatSequence s, long v)
         {
            s.add((float) v);
         }

         public void putAt(IDLFloatSequence s, int i, long v)
         {
            s.putAt(i, (float) v);
         }

         public long get(IDLFloatSequence s, int i)
         {
            return (long) s.get(i);
         }

         public long remove(IDLFloatSequence s)
         {
            return (long) s.remove();
         }

         public long remove(IDLFloatSequence s, int i)
         {
            return (long) s.remove(i);
         }

         public Buffer buffer(IDLFloatSequence s)
         {
            return s.getBuffer();
         }
      }, float[]::new, (a, i, v) -> a[i] = (float) v, IDLFloatSequence::addAll),
                     IDLPrimitiveSequenceContractTest.<IDLDoubleSequence, double[]>type("double", 8, IDLDoubleSequence::new, IDLDoubleSequence::new, new ElementAccess<IDLDoubleSequence>()
      {
         public void add(IDLDoubleSequence s, long v)
         {
            s.add((double) v);
         }

         public void putAt(IDLDoubleSequence s, int i, long v)
         {
            s.putAt(i, (double) v);
         }

         public long get(IDLDoubleSequence s, int i)
         {
            return (long) s.get(i);
         }

         public long remove(IDLDoubleSequence s)
         {
            return (long) s.remove();
         }

         public long remove(IDLDoubleSequence s, int i)
         {
            return (long) s.remove(i);
         }

         public Buffer buffer(IDLDoubleSequence s)
         {
            return s.getBuffer();
         }
      }, double[]::new, (a, i, v) -> a[i] = (double) v, IDLDoubleSequence::addAll));
   }

   @Test
   public void testPutAt()
   {
      forEachType(IDLPrimitiveSequenceContractTest::testPutAtOf);
   }

   private static <S extends IDLSequence<S>> void testPutAtOf(Type<S> type)
   {
      S sequence = type.create(2, 5);
      type.putAt(sequence, 0, 7);
      assertArrayEquals(new long[] {7}, type.values(sequence), "putAt(size) appends");

      type.putAt(sequence, 3, 9);
      assertArrayEquals(new long[] {7, 0, 0, 9}, type.values(sequence), "putAt beyond the size extends it; the gap is zero");
      assertEquals(4, type.buffer(sequence).position(), "the buffer position is the size");

      type.putAt(sequence, 1, 8);
      assertArrayEquals(new long[] {7, 8, 0, 9}, type.values(sequence), "putAt inside the sequence overwrites without changing the size");

      type.putAt(sequence, 4, 10);
      assertEquals(5, sequence.size());

      IllegalArgumentException overBound = assertThrows(IllegalArgumentException.class, () -> type.putAt(sequence, 5, 11));
      assertTrue(overBound.getMessage().startsWith("array size exceeds upper bound"), overBound.getMessage());
      assertThrows(IndexOutOfBoundsException.class, () -> type.putAt(sequence, -1, 11));
      assertArrayEquals(new long[] {7, 8, 0, 9, 10}, type.values(sequence), "failed puts change nothing");
   }

   @Test
   public void testAddAll()
   {
      forEachType(IDLPrimitiveSequenceContractTest::testAddAllOf);
   }

   private static <S extends IDLSequence<S>> void testAddAllOf(Type<S> type)
   {
      S unbounded = type.of(1, 2);
      long[] many = new long[100];
      for (int i = 0; i < many.length; ++i)
      {
         many[i] = i % 100;
      }
      type.addAll(unbounded, many);
      assertEquals(102, unbounded.size());
      assertTrue(unbounded.capacity() >= 102);
      assertEquals(1, type.get(unbounded, 0));
      assertEquals(2, type.get(unbounded, 1));
      assertEquals(99, type.get(unbounded, 101));
      type.addAll(unbounded);
      assertEquals(102, unbounded.size(), "adding nothing changes nothing");

      S bounded = type.create(1, 4);
      type.addAll(bounded, 1, 2, 3);
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> type.addAll(bounded, 4, 5));
      assertTrue(exception.getMessage().contains("5 > 4"), exception.getMessage());
      assertArrayEquals(new long[] {1, 2, 3}, type.values(bounded), "nothing is added when the bound would be exceeded");
      type.addAll(bounded, 4);
      assertArrayEquals(new long[] {1, 2, 3, 4}, type.values(bounded), "filling up to the bound is allowed");
   }

   @Test
   public void testRemove()
   {
      forEachType(IDLPrimitiveSequenceContractTest::testRemoveOf);
   }

   private static <S extends IDLSequence<S>> void testRemoveOf(Type<S> type)
   {
      S sequence = type.of(1, 2, 3, 4);
      assertEquals(4, type.remove(sequence));
      assertEquals(1, type.remove(sequence, 0));
      assertArrayEquals(new long[] {2, 3}, type.values(sequence));
      assertThrows(IndexOutOfBoundsException.class, () -> type.remove(sequence, 2));
      assertThrows(IndexOutOfBoundsException.class, () -> type.remove(sequence, -1));
      assertArrayEquals(new long[] {2, 3}, type.values(sequence));
   }

   @Test
   public void testGrowthIsLimitedByTheUpperBound()
   {
      forEachType(IDLPrimitiveSequenceContractTest::testGrowthIsLimitedByTheUpperBoundOf);
   }

   private static <S extends IDLSequence<S>> void testGrowthIsLimitedByTheUpperBoundOf(Type<S> type)
   {
      // Growing the capacity of 2 by IDLSequence.CAPACITY_GROW_SCALAR would go beyond the bound of 3
      assertTrue(2 * IDLSequence.CAPACITY_GROW_SCALAR > 3);
      S bounded = type.create(2, 3);
      type.add(bounded, 5);
      assertEquals(2, bounded.capacity());
      assertTrue(bounded.ensureMinCapacity(3));
      assertEquals(3, bounded.capacity(), "a bounded sequence never grows beyond its bound");
      assertFalse(bounded.ensureMinCapacity(4), "growing beyond the bound is refused");
      assertArrayEquals(new long[] {5}, type.values(bounded), "a refused growth keeps the content");

      S unbounded = type.create();
      assertTrue(unbounded.isUnbounded());
      assertFalse(bounded.isUnbounded());
      assertTrue(unbounded.ensureMinCapacity(1000));
      assertTrue(unbounded.capacity() >= 1000);
      assertTrue(unbounded.isEmpty());
      assertEquals(unbounded.capacity(), unbounded.remainingCapacity());
   }

   @Test
   public void testSetFromALargerSequenceIsRejected()
   {
      forEachType(IDLPrimitiveSequenceContractTest::testSetFromALargerSequenceIsRejectedOf);
   }

   private static <S extends IDLSequence<S>> void testSetFromALargerSequenceIsRejectedOf(Type<S> type)
   {
      S small = type.create(0, 2);
      type.add(small, 9);
      S large = type.of(1, 2, 3);
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> small.set(large));
      assertTrue(exception.getMessage().startsWith("array size exceeds upper bound"), exception.getMessage());
      assertArrayEquals(new long[] {9}, type.values(small), "a rejected set changes nothing");

      large.set(small);
      assertArrayEquals(new long[] {9}, type.values(large), "setting from a smaller sequence replaces the content");
   }

   @Test
   public void testConstructorChecks()
   {
      forEachType(IDLPrimitiveSequenceContractTest::testConstructorChecksOf);
   }

   private static <S extends IDLSequence<S>> void testConstructorChecksOf(Type<S> type)
   {
      assertThrows(IllegalArgumentException.class, () -> type.create(0, -1), "negative upper bound");
      assertThrows(IllegalArgumentException.class, () -> type.create(5, 4), "capacity above the upper bound");
      S sequence = type.create(4, 4);
      assertEquals(4, sequence.getMaxSize());
      assertEquals(0, sequence.size());
   }

   /**
    * The element-wise methods must encode exactly like the bulk serializer, at every alignment of the sequence start.
    */
   @Test
   public void testElementWiseEncodingMatchesBulkEncoding()
   {
      forEachType(IDLPrimitiveSequenceContractTest::testElementWiseEncodingMatchesBulkEncodingOf);
   }

   private static <S extends IDLSequence<S>> void testElementWiseEncodingMatchesBulkEncodingOf(Type<S> type)
   {
      S sequence = type.of(1, 2, 3, 100, 0, 7);
      for (int offset = 0; offset < 8; ++offset)
      {
         CDRBuffer bulk = new CDRBuffer();
         bulk.writePayloadHeader();
         for (int i = 0; i < offset; ++i)
         {
            bulk.writeByte((byte) 0x55);
         }
         sequence.serialize(bulk);

         CDRBuffer elementWise = new CDRBuffer();
         elementWise.writePayloadHeader();
         for (int i = 0; i < offset; ++i)
         {
            elementWise.writeByte((byte) 0x55);
         }
         elementWise.writeSequenceLength(sequence.size());
         for (int i = 0; i < sequence.size(); ++i)
         {
            assertEquals(type.elementSizeBytes, sequence.elementSizeBytes(0, i));
            sequence.writeElement(i, elementWise);
         }

         int size = bulk.getBufferUnsafe().position();
         assertEquals(size, elementWise.getBufferUnsafe().position(), type + " offset " + offset);
         assertArrayEquals(bytes(bulk, size), bytes(elementWise, size), type + " offset " + offset);

         // And read back element by element
         elementWise.beginRead(size);
         elementWise.readPayloadHeader();
         for (int i = 0; i < offset; ++i)
         {
            elementWise.readByte();
         }
         S result = type.of(42);
         result.clear();
         int length = elementWise.readSequenceLength(type.elementSizeBytes, result.getMaxSize());
         for (int i = 0; i < length; ++i)
         {
            result.readElement(elementWise);
         }
         assertArrayEquals(type.values(sequence), type.values(result), type + " offset " + offset);
         assertEquals(size, elementWise.getBufferUnsafe().position());
      }
   }

   private static byte[] bytes(CDRBuffer buffer, int size)
   {
      ByteBuffer byteBuffer = buffer.getBufferUnsafe();
      byte[] bytes = new byte[size];
      for (int i = 0; i < size; ++i)
      {
         bytes[i] = byteBuffer.get(i);
      }
      return bytes;
   }

   @Test
   public void testValuesSurviveGrowth()
   {
      forEachType(IDLPrimitiveSequenceContractTest::testValuesSurviveGrowthOf);
   }

   private static <S extends IDLSequence<S>> void testValuesSurviveGrowthOf(Type<S> type)
   {
      S sequence = type.create(1, IDLSequence.UNBOUNDED_MAX_SIZE);
      long[] expected = new long[70];
      for (int i = 0; i < expected.length; ++i)
      {
         expected[i] = (i * 7) % 120;
         type.add(sequence, expected[i]);
      }
      assertArrayEquals(expected, type.values(sequence));
      assertEquals(Arrays.toString(expected).replace(" ", ""), Arrays.toString(type.values(sequence)).replace(" ", ""));
   }
}
