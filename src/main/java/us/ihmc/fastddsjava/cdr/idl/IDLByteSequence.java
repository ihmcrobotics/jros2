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

public class IDLByteSequence extends IDLSequence<IDLByteSequence> implements Iterable<Byte>
{
   private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

   private ByteBuffer buffer;

   public IDLByteSequence()
   {
      buffer = EMPTY_BUFFER;
   }

   public IDLByteSequence(int capacity)
   {
      this(capacity, IDLSequence.UNBOUNDED_MAX_SIZE);
   }

   public IDLByteSequence(int capacity, int maxSize)
   {
      super(capacity, maxSize);

      buffer = EMPTY_BUFFER;

      ensureMinCapacity(capacity);
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
    * Appends a byte value to the end of the sequence.
    *
    * @param value the byte value to add
    */
   public void add(byte value)
   {
      ensureMinCapacity(buffer.position() + 1);
      buffer.put(value);
   }

   /**
    * Appends all byte values from the array to the end of the sequence.
    * This is a highly efficient bulk operation using NIO buffer operations.
    *
    * @param values the byte array to add
    */
   public void addAll(byte[] values)
   {
      ensureMinCapacity(buffer.position() + values.length);
      buffer.put(values);
   }

   /**
    * Removes and returns the last element from the sequence.
    *
    * @return the last element in the sequence
    */
   public byte remove()
   {
      byte value = buffer.get(buffer.position() - 1);
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
   public byte remove(int index)
   {
      byte value = buffer.get(index);
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
   public byte get(int index)
   {
      return buffer.get(index);
   }

   /**
    * Get the backing heap {@link ByteBuffer} holding all byte values in the sequence.
    * Use this for efficient copy operations, however, ensure the buffer is initialized and
    * of the correct capacity first with {@link #ensureMinCapacity(int)}!
    *
    * @return the buffer of byte values, may be null
    */
   public ByteBuffer getBuffer()
   {
      return buffer;
   }

   /**
    * Copies the sequence contents into the given array without allocating.
    *
    * @return number of bytes copied
    * @throws IndexOutOfBoundsException if the destination is too small
    */
   public int copyTo(byte[] destination, int destinationOffset)
   {
      int length = size();
      if (length == 0)
         return 0;

      if (destinationOffset < 0 || length > destination.length - destinationOffset)
         throw new IndexOutOfBoundsException();

      if (buffer.hasArray())
         System.arraycopy(buffer.array(), buffer.arrayOffset(), destination, destinationOffset, length);
      else
         buffer.get(0, destination, destinationOffset, length);

      return length;
   }

   /**
    * Copies the sequence contents into the given buffer without allocating.
    *
    * @return number of bytes copied
    * @throws IndexOutOfBoundsException if the destination does not have enough remaining space
    */
   public int copyTo(ByteBuffer destination)
   {
      int length = size();
      if (length == 0)
         return 0;

      if (length > destination.remaining())
         throw new IndexOutOfBoundsException();

      if (buffer.hasArray())
         destination.put(buffer.array(), buffer.arrayOffset(), length);
      else
      {
         for (int i = 0; i < length; i++)
            destination.put(buffer.get(i));
      }

      return length;
   }

   /**
    * Returns a copy of the sequence contents as a byte array.
    *
    * @return a new byte array containing all elements in order
    */
   public byte[] toByteArray()
   {
      int length = size();
      if (length == 0)
         return new byte[0];

      byte[] bytes = new byte[length];
      copyTo(bytes, 0);
      return bytes;
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
            ByteBuffer newBuffer = ByteBuffer.allocate(desiredCapacity);
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
      return 1; // Size of byte/bool/char element (alignment added by caller)
   }

   @Override
   public void readElement(CDRBuffer cdrBuffer)
   {
      buffer.put(cdrBuffer.readByte());
   }

   @Override
   public void writeElement(int i, CDRBuffer cdrBuffer)
   {
      cdrBuffer.writeByte(buffer.get(i));
   }

   @Override
   public void set(IDLByteSequence other)
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
   public Iterator<Byte> iterator()
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
         public Byte next()
         {
            return buffer.get(index++);
         }

         @Override
         public void remove()
         {
            IDLByteSequence.this.remove(--index);
         }
      };
   }
}
