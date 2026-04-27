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

import java.util.Arrays;
import java.util.Iterator;

public class IDLStringSequence extends IDLSequence<IDLStringSequence> implements Iterable<String>
{
   private static final StringBuilder[] EMPTY_ARRAY = new StringBuilder[0];
   private static final int DEFAULT_MAX_STRING_LENGTH = 16;

   protected StringBuilder[] elements;
   protected int position;
   private final int defaultStringLength;

   public IDLStringSequence()
   {
      elements = EMPTY_ARRAY;
      defaultStringLength = -1;
   }

   public IDLStringSequence(int capacity)
   {
      super(capacity, IDLSequence.UNBOUNDED_MAX_SIZE);

      elements = EMPTY_ARRAY;
      defaultStringLength = -1;

      ensureMinCapacity(capacity);
   }

   public IDLStringSequence(int capacity, int maxSize, int defaultStringLength)
   {
      super(capacity, maxSize);

      elements = EMPTY_ARRAY;
      this.defaultStringLength = defaultStringLength;

      ensureMinCapacity(capacity);
   }

   @Override
   public int size()
   {
      return position;
   }

   @Override
   public int capacity()
   {
      return elements.length;
   }

   @Override
   public void clear()
   {
      position = 0;
   }

   /**
    * Appends a String element to the end of the sequence.
    *
    * @param element the String to add
    */
   public void add(String element)
   {
      add(new StringBuilder(element));
   }

   /**
    * Appends a StringBuilder element to the end of the sequence.
    *
    * @param element the StringBuilder to add
    */
   public void add(StringBuilder element)
   {
      ensureMinCapacity(position + 1);

      elements[position++] = element;
   }

   /**
    * Adds a new StringBuilder to the end of the sequence using the default string length.
    *
    * @return the StringBuilder at the new position (newly created or existing)
    */
   public StringBuilder add()
   {
      return add(defaultStringLength);
   }

   /**
    * Adds a new StringBuilder to the end of the sequence with the specified capacity.
    *
    * @param stringLength the initial capacity for the StringBuilder
    * @return the StringBuilder at the new position (newly created or existing)
    */
   public StringBuilder add(int stringLength)
   {
      ensureMinCapacity(position + 1);

      if (elements[position] == null)
      {
         elements[position] = new StringBuilder(stringLength > 0 ? stringLength : DEFAULT_MAX_STRING_LENGTH);
      }
      else if (stringLength > 0)
      {
         elements[position].ensureCapacity(stringLength);
      }

      return elements[position++];
   }

   /**
    * Removes the last element from the sequence.
    */
   public void removeLast()
   {
      position--;
   }

   /**
    * Removes the element at the specified position in this list.
    * Shifts any subsequent elements to the left (subtracts one from their
    * indices).
    *
    * @param index the index of the element to be removed
    */
   public void remove(int index)
   {
      if (index != position - 1)
      {
         StringBuilder sb = elements[index];

         while (index < position - 1)
         {
            elements[index] = elements[++index];
         }

         // Do not throw away the removed element, put it at the end of the list instead.
         elements[position - 1] = sb;
      }

      position--;
   }

   /**
    * Returns the StringBuilder at the specified index.
    *
    * @param index the index of the element to return
    * @return the StringBuilder at the specified index
    */
   public StringBuilder get(int index)
   {
      return elements[index];
   }

   /**
    * Returns the String representation of the element at the specified index.
    *
    * @param index the index of the element to return
    * @return the String at the specified index
    */
   public String getAsString(int index)
   {
      return get(index).toString();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean ensureMinCapacity(int desiredCapacity)
   {
      if (elements.length < desiredCapacity)
      {
         desiredCapacity = Math.min(Math.max(desiredCapacity, elements.length * CAPACITY_GROW_SCALAR), getMaxSize());

         if (desiredCapacity > getMaxSize())
         {
            return false;
         }
         else
         {
            elements = Arrays.copyOf(elements, desiredCapacity);
         }
      }

      return true;
   }

   @Override
   public int elementSizeBytes(int currentAlignment, int i)
   {
      // CDR string serialization format:
      // - Alignment padding (to 4-byte boundary)
      // - 4 bytes for length prefix (int)
      // - string characters (1 byte each)
      // - 1 byte for null terminator
      int stringLength = elements[i].length();
      int alignment = CDRBuffer.alignment(currentAlignment, 4);
      return alignment + 4 + stringLength + 1;
   }

   @Override
   public void readElement(CDRBuffer buffer)
   {
      // Initialize StringBuilder if needed
      if (elements[position] == null)
      {
         int capacity = defaultStringLength > 0 ? defaultStringLength : DEFAULT_MAX_STRING_LENGTH;
         elements[position] = new StringBuilder(capacity);
      }

      StringBuilder element = elements[position++];
      buffer.readString(element);
   }

   @Override
   public void writeElement(int i, CDRBuffer buffer)
   {
      buffer.writeString(elements[i]);
   }

   @Override
   public void set(IDLStringSequence other)
   {
      clear();

      int othersElements = other.size();
      ensureMinCapacity(othersElements);

      for (int i = 0; i < othersElements; ++i)
      {
         if (elements[i] != null)
         {
            elements[i].delete(0, elements[i].length());
            elements[i].insert(0, other.elements[i]);
         }
         else
         {
            elements[i] = new StringBuilder(other.elements[i]);
         }
      }

      position = othersElements;
   }

   @Override
   public String toString()
   {
      StringBuilder builder = new StringBuilder();
      builder.append("[");
      for (int i = 0; i < size(); ++i)
      {
         builder.append(elements[i].toString());
         if (i < size() - 1)
         {
            builder.append(", ");
         }
      }
      builder.append("]");
      return builder.toString();
   }

   public String[] toStringArray()
   {
      String[] array = new String[size()];
      for (int i = 0; i < size(); i++)
      {
         array[i] = elements[i].toString();
      }
      return array;
   }

   @Override
   public Iterator<String> iterator()
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
         public String next()
         {
            return elements[index++].toString();
         }

         @Override
         public void remove()
         {
            IDLStringSequence.this.remove(--index);
         }
      };
   }
}
