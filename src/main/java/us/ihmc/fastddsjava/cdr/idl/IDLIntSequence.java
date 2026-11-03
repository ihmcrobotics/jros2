/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
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

import us.ihmc.fastddsjava.cdr.CDRBuffer;

import java.nio.IntBuffer;
import java.util.Iterator;

/**
 * IDL sequence of int32 / uint32 (stored as int) values backed by a heap {@link IntBuffer}.
 * <p>
 * The sequence is (de)serialized in bulk: aligned once, then copied in one operation honouring the CDR byte order.
 * </p>
 */
public class IDLIntSequence extends IDLSequence<IDLIntSequence> implements Iterable<Integer>
{
   private static final IntBuffer EMPTY_BUFFER = IntBuffer.allocate(0);
   private static final int ELEMENT_SIZE_BYTES = 4;

   private IntBuffer buffer;

   public IDLIntSequence()
   {
      buffer = EMPTY_BUFFER;
   }

   public IDLIntSequence(int capacity)
   {
      this(capacity, IDLSequence.UNBOUNDED_MAX_SIZE);
   }

   public IDLIntSequence(int capacity, int maxSize)
   {
      super(capacity, maxSize);

      buffer = EMPTY_BUFFER;

      ensureMinCapacity(capacity);
   }

   /**
    * Get the backing heap {@link IntBuffer} holding all int values in the sequence.
    * Use this for efficient copy operations, however, ensure the buffer is the correct capacity
    * first with {@link #ensureMinCapacity(int)}! The buffer's position is the size of the sequence.
    *
    * @return the buffer of int values
    */
   public IntBuffer getBuffer()
   {
      return buffer;
   }

   @Override
   public int size()
   {
      return buffer.position();
   }

   @Override
   public int capacity()
   {
      return buffer.capacity();
   }

   @Override
   public void clear()
   {
      buffer.clear();
   }

   /**
    * Sets the element at {@code index} without requiring sequential {@link #add(int)} calls.
    * Extends the logical {@link #size()} when {@code index} is at or beyond the current size.
    *
    * @throws IllegalArgumentException if {@code index} is not below the upper bound of the sequence
    */
   public void putAt(int index, int value)
   {
      if (index < 0)
      {
         throw IDLSequenceErrors.negativeIndex(index);
      }
      ensureSizeWithinBound(index + 1);
      buffer.put(index, value);
      if (buffer.position() <= index)
         buffer.position(index + 1);
   }

   /**
    * Appends a int value to the end of the sequence.
    *
    * @param value the int value to add
    * @throws IllegalArgumentException if the sequence is bounded and already full
    */
   public void add(int value)
   {
      ensureSizeWithinBound(buffer.position() + 1);
      buffer.put(value);
   }

   /**
    * Appends all int values from the array to the end of the sequence.
    * This is a highly efficient bulk operation using NIO buffer operations.
    *
    * @param values the int array to add
    * @throws IllegalArgumentException if the result would exceed the upper bound (nothing is added)
    */
   public void addAll(int[] values)
   {
      long newSize = (long) buffer.position() + values.length;
      if (newSize > getMaxSize())
      {
         throw exceedsUpperBound(newSize);
      }
      ensureSizeWithinBound((int) newSize);
      buffer.put(values);
   }

   /**
    * Removes and returns the last element from the sequence.
    *
    * @return the last element in the sequence
    */
   public int remove()
   {
      int value = buffer.get(buffer.position() - 1);
      buffer.position(buffer.position() - 1);
      return value;
   }

   /**
    * Removes and returns the element at the specified index.
    * Shifts subsequent elements left by one position.
    *
    * @param index the index of the element to remove
    * @return the element at the specified index
    */
   public int remove(int index)
   {
      int size = buffer.position();
      if (index < 0 || index >= size)
      {
         throw IDLSequenceErrors.indexOutOfBounds(index, size);
      }
      int[] array = buffer.array();
      int offset = buffer.arrayOffset();
      int value = array[offset + index];
      System.arraycopy(array, offset + index + 1, array, offset + index, size - index - 1);
      buffer.position(size - 1);
      return value;
   }

   /**
    * Returns the element at the specified index.
    *
    * @param index the index of the element to return
    * @return the element at the specified index
    */
   public int get(int index)
   {
      return buffer.get(index);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean ensureMinCapacity(int desiredCapacity)
   {
      if (buffer.capacity() >= desiredCapacity)
      {
         return true;
      }

      int newCapacity = grownCapacity(buffer.capacity(), desiredCapacity);
      if (newCapacity < 0)
      {
         return false;
      }

      int size = buffer.position();
      IntBuffer newBuffer = IntBuffer.allocate(newCapacity);
      System.arraycopy(buffer.array(), buffer.arrayOffset(), newBuffer.array(), newBuffer.arrayOffset(), size);
      newBuffer.position(size);

      buffer = newBuffer;

      return true;
   }

   @Override
   public int elementSizeBytes(int currentAlignment, int i)
   {
      return ELEMENT_SIZE_BYTES; // Size of int element (alignment added by caller)
   }

   @Override
   protected int minimumElementSizeBytes()
   {
      return ELEMENT_SIZE_BYTES;
   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      return primitiveSequenceSizeBytes(currentAlignment, size(), ELEMENT_SIZE_BYTES);
   }

   @Override
   public void readElement(CDRBuffer cdrBuffer)
   {
      add(cdrBuffer.readInt());
   }

   @Override
   public void writeElement(int i, CDRBuffer cdrBuffer)
   {
      cdrBuffer.writeInt(buffer.get(i));
   }

   /**
    * Writes the length and all elements in bulk.
    *
    * @throws IllegalArgumentException if the sequence has more elements than its upper bound
    */
   @Override
   public void serialize(CDRBuffer cdrBuffer)
   {
      checkSizeWithinBound();

      int size = buffer.position();
      cdrBuffer.writeSequenceLength(size);
      cdrBuffer.writeIntArray(buffer.array(), buffer.arrayOffset(), size);
   }

   /**
    * Reads the length and all elements in bulk, replacing the content of this sequence.
    *
    * @throws us.ihmc.fastddsjava.cdr.CDRDeserializationException if the length exceeds the upper bound or the remaining payload
    */
   @Override
   public void deserialize(CDRBuffer cdrBuffer)
   {
      buffer.clear();

      int size = cdrBuffer.readSequenceLength(ELEMENT_SIZE_BYTES, getMaxSize());
      ensureMinCapacity(size);
      cdrBuffer.readIntArray(buffer.array(), buffer.arrayOffset(), size);
      buffer.position(size);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void set(IDLIntSequence other)
   {
      int othersElements = other.size();
      if (othersElements > getMaxSize())
      {
         throw exceedsUpperBound(othersElements);
      }

      buffer.clear();
      ensureMinCapacity(othersElements);

      System.arraycopy(other.buffer.array(), other.buffer.arrayOffset(), buffer.array(), buffer.arrayOffset(), othersElements);
      buffer.position(othersElements);
   }

   @Override
   public String toString()
   {
      StringBuilder builder = new StringBuilder();
      builder.append('[');
      for (int i = 0; i < size(); ++i)
      {
         builder.append(buffer.get(i));
         if (i < size() - 1)
         {
            builder.append(',').append(' ');
         }
      }
      builder.append(']');
      return builder.toString();
   }

   /**
    * Iterates over the elements as {@link Integer} objects. Allocates an iterator and boxes the elements; use
    * {@link #get(int)} and {@link #size()} for allocation-free access.
    */
   @Override
   public Iterator<Integer> iterator()
   {
      return new Iterator<>()
      {
         private int index = 0;

         @Override
         public boolean hasNext()
         {
            return index < size();
         }

         @Override
         public Integer next()
         {
            return buffer.get(index++);
         }

         @Override
         public void remove()
         {
            IDLIntSequence.this.remove(--index);
         }
      };
   }
}
