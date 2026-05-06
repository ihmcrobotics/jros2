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

import java.nio.ByteBuffer;
import java.util.Iterator;

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
    */
   public void add(boolean value)
   {
      ensureMinCapacity(buffer.position() + 1);
      buffer.put(value);
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
      boolean value = buffer.get(index);
      buffer.put(index, buffer, index + 1, buffer.position() - index - 1);
      buffer.position(buffer.position() - 1);
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
      if (buffer.capacity() < desiredCapacity)
      {
         desiredCapacity = Math.min(Math.max(desiredCapacity, buffer.capacity() * CAPACITY_GROW_SCALAR), getMaxSize());

         if (desiredCapacity > getMaxSize())
         {
            return false;
         }
         else
         {
            BooleanBufferWrapper newBuffer = new BooleanBufferWrapper(desiredCapacity);
            newBuffer.put(0, buffer, 0, buffer.position());
            newBuffer.position(buffer.position());

            buffer = newBuffer;
         }
      }

      return true;
   }

   @Override
   public int elementSizeBytes(int currentAlignment, int i)
   {
      return 1; // Size of boolean element (alignment added by caller)
   }

   @Override
   public void readElement(CDRBuffer cdrBuffer)
   {
      buffer.put(cdrBuffer.readByte() == 1);
   }

   @Override
   public void writeElement(int i, CDRBuffer cdrBuffer)
   {
      cdrBuffer.writeByte((byte) (buffer.get(i) ? 1 : 0));
   }

   @Override
   public void set(IDLBoolSequence other)
   {
      clear();

      int othersElements = other.size();
      ensureMinCapacity(othersElements);

      buffer.put(0, other.buffer, 0, othersElements);
      buffer.position(othersElements);
   }

   @Override
   public String toString()
   {
      StringBuilder builder = new StringBuilder();
      builder.append("[");
      for (int i = 0; i < size(); ++i)
      {
         builder.append(buffer.get(i));
         if (i < size() - 1)
         {
            builder.append(", ");
         }
      }
      builder.append("]");
      return builder.toString();
   }

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

      public boolean get()
      {
         return byteBuffer.get() != 0;
      }

      public BooleanBufferWrapper put(int dstIndex, BooleanBufferWrapper src, int srcIndex, int length)
      {
         for (int i = 0; i < length; i++)
         {
            byteBuffer.put(dstIndex + i, src.byteBuffer.get(srcIndex + i));
         }
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
