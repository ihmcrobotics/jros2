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

import java.nio.CharBuffer;
import java.util.Iterator;

public class IDLCharSequence extends IDLSequence<IDLCharSequence> implements Iterable<Character>
{
   private static final CharBuffer EMPTY_BUFFER = CharBuffer.allocate(0);

   private CharBuffer buffer;

   public IDLCharSequence()
   {
      buffer = EMPTY_BUFFER;
   }

   public IDLCharSequence(int capacity)
   {
      this(capacity, IDLSequence.UNBOUNDED_MAX_SIZE);
   }

   public IDLCharSequence(int capacity, int maxSize)
   {
      super(capacity, maxSize);

      buffer = EMPTY_BUFFER;

      ensureMinCapacity(capacity);
   }

   /**
    * Get the backing heap {@link CharBuffer} holding all char values in the sequence.
    * Use this for efficient copy operations, however, ensure the buffer is the correct
    * capacity first with {@link #ensureMinCapacity(int)}!
    *
    * @return the buffer of char values
    */
   public CharBuffer getBuffer()
   {
      return buffer;
   }

   public int copyTo(char[] destination, int destinationOffset)
   {
      int length = size();
      if (length == 0)
         return 0;

      if (destinationOffset < 0 || length > destination.length - destinationOffset)
         throw new IndexOutOfBoundsException();

      buffer.get(0, destination, destinationOffset, length);
      return length;
   }

   public int copyTo(CharBuffer destination)
   {
      int length = size();
      if (length == 0)
         return 0;

      if (length > destination.remaining())
         throw new IndexOutOfBoundsException();

      CharBuffer readView = buffer.duplicate();
      readView.limit(length).position(0);
      destination.put(readView);
      return length;
   }

   public char[] toCharArray()
   {
      int length = size();
      if (length == 0)
         return new char[0];

      char[] array = new char[length];
      buffer.get(0, array, 0, length);
      return array;
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
    * Appends a char value to the end of the sequence.
    *
    * @param value the char value to add
    */
   public void add(char value)
   {
      ensureMinCapacity(buffer.position() + 1);
      buffer.put(value);
   }

   /**
    * Appends all char values from the array to the end of the sequence.
    * This is a highly efficient bulk operation using NIO buffer operations.
    *
    * @param values the char array to add
    */
   public void addAll(char[] values)
   {
      ensureMinCapacity(buffer.position() + values.length);
      buffer.put(values);
   }

   /**
    * Removes and returns the last element from the sequence.
    *
    * @return the last element in the sequence
    */
   public char remove()
   {
      char value = buffer.get(buffer.position() - 1);
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
   public char remove(int index)
   {
      char value = buffer.get(index);
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
   public char get(int index)
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
            CharBuffer newBuffer = CharBuffer.allocate(desiredCapacity);
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
      buffer.put(cdrBuffer.readChar());
   }

   @Override
   public void writeElement(int i, CDRBuffer cdrBuffer)
   {
      cdrBuffer.writeChar(buffer.get(i));
   }

   @Override
   public void set(IDLCharSequence other)
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
   public Iterator<Character> iterator()
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
         public Character next()
         {
            return buffer.get(index++);
         }

         @Override
         public void remove()
         {
            IDLCharSequence.this.remove(--index);
         }
      };
   }
}
