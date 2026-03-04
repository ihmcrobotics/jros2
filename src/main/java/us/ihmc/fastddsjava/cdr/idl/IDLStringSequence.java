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

   public void add(String element)
   {
      add(new StringBuilder(element));
   }

   public void add(StringBuilder element)
   {
      ensureMinCapacity(position + 1);

      elements[position++] = element;
   }

   public StringBuilder add()
   {
      return add(defaultStringLength);
   }

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

   public void remove()
   {
      position--;
   }

   public StringBuilder get(int index)
   {
      return elements[index];
   }

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
      // We treat each character as 1 byte (8 bits) in a standard string
      return elements[i].length() + CDRBuffer.alignment(currentAlignment, elements[i].length());
   }

   @Override
   public void readElement(CDRBuffer buffer)
   {
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
      };
   }
}
