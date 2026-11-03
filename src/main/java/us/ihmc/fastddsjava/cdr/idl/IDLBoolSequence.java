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
import us.ihmc.fastddsjava.cdr.CDRDeserializationException;

import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * IDL sequence of booleans, stored as one byte (0 or 1) per element.
 */
public class IDLBoolSequence extends IDLSequence<IDLBoolSequence> implements Iterable<Boolean>
{
   private static final BooleanBufferWrapper EMPTY_BUFFER = new BooleanBufferWrapper(0);

   private BooleanBufferWrapper buffer;

   public IDLBoolSequence()
   {
      buffer = EMPTY_BUFFER;
   }

   public IDLBoolSequence(int capacity)
   {
      this(capacity, IDLSequence.UNBOUNDED_MAX_SIZE);
   }

   public IDLBoolSequence(int capacity, int maxSize)
   {
      super(capacity, maxSize);

      buffer = EMPTY_BUFFER;

      ensureMinCapacity(capacity);
   }

   /**
    * Get the backing heap {@link BooleanBufferWrapper} holding all boolean values in the sequence.
    * Use this for efficient copy operations, however, ensure the buffer is the correct capacity
    * first with {@link #ensureMinCapacity(int)}!
    *
    * @return the buffer of boolean values
    */
   public BooleanBufferWrapper getBuffer()
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
    * Appends a boolean value to the end of the sequence.
    *
    * @param value the boolean value to add
    * @throws IllegalArgumentException if the sequence is bounded and already full
    */
   public void add(boolean value)
   {
      ensureSizeWithinBound(buffer.position() + 1);
      buffer.put(value);
   }

   /**
    * Appends all boolean values from the array to the end of the sequence.
    * This is an efficient bulk operation.
    *
    * @param values the boolean array to add
    * @throws IllegalArgumentException if the result would exceed the upper bound (nothing is added)
    */
   public void addAll(boolean[] values)
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
   public boolean remove()
   {
      boolean value = buffer.get(buffer.position() - 1);
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
   public boolean remove(int index)
   {
      int size = buffer.position();
      if (index < 0 || index >= size)
      {
         throw IDLSequenceErrors.indexOutOfBounds(index, size);
      }
      boolean value = buffer.get(index);
      buffer.put(index, buffer, index + 1, size - index - 1);
      buffer.position(size - 1);
      return value;
   }

   /**
    * Returns the element at the specified index.
    *
    * @param index the index of the element to return
    * @return the element at the specified index
    */
   public boolean get(int index)
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

      BooleanBufferWrapper newBuffer = new BooleanBufferWrapper(newCapacity);
      newBuffer.put(0, buffer, 0, buffer.position());
      newBuffer.position(buffer.position());

      buffer = newBuffer;

      return true;
   }

   @Override
   public int elementSizeBytes(int currentAlignment, int i)
   {
      return 1; // Size of boolean element (alignment added by caller)
   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      return primitiveSequenceSizeBytes(currentAlignment, size(), 1);
   }

   @Override
   public void readElement(CDRBuffer cdrBuffer)
   {
      add(cdrBuffer.readBoolean());
   }

   @Override
   public void writeElement(int i, CDRBuffer cdrBuffer)
   {
      cdrBuffer.writeBoolean(buffer.get(i));
   }

   /**
    * Writes the length and all elements (each as 0 or 1).
    *
    * @throws IllegalArgumentException if the sequence has more elements than its upper bound
    */
   @Override
   public void serialize(CDRBuffer cdrBuffer)
   {
      checkSizeWithinBound();

      int size = buffer.position();
      cdrBuffer.writeSequenceLength(size);

      byte[] array = buffer.byteBuffer.array();
      int offset = buffer.byteBuffer.arrayOffset();
      for (int i = 0; i < size; ++i)
      {
         cdrBuffer.writeBoolean(array[offset + i] != 0);
      }
   }

   /**
    * Reads the length and all elements, replacing the content of this sequence.
    *
    * @throws CDRDeserializationException if the length exceeds the upper bound or the remaining payload, or a value is
    *                                     not 0 or 1
    */
   @Override
   public void deserialize(CDRBuffer cdrBuffer)
   {
      buffer.clear();

      int size = cdrBuffer.readSequenceLength(1, getMaxSize());
      ensureMinCapacity(size);

      byte[] array = buffer.byteBuffer.array();
      int offset = buffer.byteBuffer.arrayOffset();
      cdrBuffer.readByteArray(array, offset, size);
      for (int i = 0; i < size; ++i)
      {
         byte value = array[offset + i];
         if (value != 0 && value != 1)
         {
            throw IDLSequenceErrors.unexpectedBoolean(value, i);
         }
      }
      buffer.position(size);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void set(IDLBoolSequence other)
   {
      int othersElements = other.size();
      if (othersElements > getMaxSize())
      {
         throw exceedsUpperBound(othersElements);
      }

      buffer.clear();
      ensureMinCapacity(othersElements);

      buffer.put(0, other.buffer, 0, othersElements);
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
    * Iterates over the elements as {@link Boolean} objects. Allocates an iterator and boxes the elements; use
    * {@link #get(int)} and {@link #size()} for allocation-free access.
    */
   @Override
   public Iterator<Boolean> iterator()
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
         public Boolean next()
         {
            return buffer.get(index++);
         }

         @Override
         public void remove()
         {
            IDLBoolSequence.this.remove(--index);
         }
      };
   }

   /**
    * Heap storage of booleans as bytes (0 or 1). Its position is the size of the owning sequence.
    */
   public static class BooleanBufferWrapper
   {
      private final ByteBuffer byteBuffer;
      private final int capacity;

      public BooleanBufferWrapper(int capacity)
      {
         this.capacity = capacity;
         this.byteBuffer = ByteBuffer.allocate(capacity);
      }

      public ByteBuffer getByteBuffer()
      {
         return byteBuffer;
      }

      public void put(int index, boolean value)
      {
         byteBuffer.put(index, (byte) (value ? 1 : 0));
      }

      public boolean get(int index)
      {
         return byteBuffer.get(index) != 0;
      }

      public void put(boolean value)
      {
         byteBuffer.put((byte) (value ? 1 : 0));
      }

      public void put(boolean[] values)
      {
         for (boolean value : values)
         {
            byteBuffer.put((byte) (value ? 1 : 0));
         }
      }

      public boolean get()
      {
         return byteBuffer.get() != 0;
      }

      /**
       * Copies {@code length} values from {@code src} (starting at {@code srcIndex}) to this buffer (starting at
       * {@code dstIndex}). Overlapping copies within the same buffer are handled. Positions are not changed.
       */
      public BooleanBufferWrapper put(int dstIndex, BooleanBufferWrapper src, int srcIndex, int length)
      {
         System.arraycopy(src.byteBuffer.array(), src.byteBuffer.arrayOffset() + srcIndex, byteBuffer.array(), byteBuffer.arrayOffset() + dstIndex, length);
         return this;
      }

      public int capacity()
      {
         return capacity;
      }

      public int position()
      {
         return byteBuffer.position();
      }

      public BooleanBufferWrapper position(int newPosition)
      {
         byteBuffer.position(newPosition);
         return this;
      }

      public BooleanBufferWrapper clear()
      {
         byteBuffer.clear();
         return this;
      }
   }
}
