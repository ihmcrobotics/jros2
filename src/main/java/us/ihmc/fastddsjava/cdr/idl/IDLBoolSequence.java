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

public class IDLBoolSequence extends IDLSequence<IDLBoolSequence>
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
    * Use this for efficient copy operations, however ensure the buffer is the correct capacity
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
      return 1 + CDRBuffer.alignment(currentAlignment, 1);
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
