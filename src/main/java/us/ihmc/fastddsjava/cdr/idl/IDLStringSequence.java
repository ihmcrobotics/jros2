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

public class IDLStringSequence extends IDLSequence<IDLStringSequence>
{
   private static final int DEFAULT_STRING_CAPACITY = 16;

   protected StringBuilder[] elements;
   protected int position;

   public IDLStringSequence(int capacity, int maxSize)
   {
      super(capacity, maxSize);
      position = 0;
   }

   public IDLStringSequence(int maxSize)
   {
      super(maxSize);
      position = 0;
   }

   public IDLStringSequence()
   {
      position = 0;
   }

   @Override
   public int elements()
   {
      return position;
   }

   @Override
   public int capacity()
   {
      if (elements == null)
      {
         return 0;
      }

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
      if (elements == null)
      {
         ensureMinCapacity(Math.min(getMaxSize(), DEFAULT_INITIAL_CAPACITY));
      }
      else if (!isUnbounded() && (position >= getMaxSize()))
      {
         throw new RuntimeException("Cannot add element to the sequence, reached upper bound");
      }
      else if (position == elements.length)
      {
         ensureMinCapacity(2 * elements.length);
      }

      elements[position++] = element;
   }

   public StringBuilder add()
   {
      return add(-1);
   }

   public StringBuilder add(int stringLength)
   {
      if (elements == null)
      {
         ensureMinCapacity(DEFAULT_INITIAL_CAPACITY);
      }
      else if (position == elements.length)
      {
         ensureMinCapacity(2 * elements.length);
      }

      if (elements[position] == null)
      {
         elements[position] = new StringBuilder(stringLength > 0 ? stringLength : DEFAULT_STRING_CAPACITY);
      }
      else if (stringLength > 0)
      {
         elements[position].ensureCapacity(stringLength);
      }

      return elements[position++];
   }

   public StringBuilder get(int index)
   {
      assert index < elements();

      return elements[index];
   }

   public String getAsString(int index)
   {
      return get(index).toString();
   }

   public StringBuilder[] getArrayUnsafe()
   {
      return elements;
   }

   @Override
   protected void ensureMinCapacity(int desiredCapacity)
   {
      if (elements == null)
      {
         elements = new StringBuilder[desiredCapacity];
      }
      else if (elements.length < desiredCapacity)
      {
         elements = Arrays.copyOf(elements, desiredCapacity);
      }
   }

   @Override
   public int elementSizeBytes(int i)
   {
      assert elements != null;
      assert i < elements();

      // We treat each character as 1 byte (8 bits) in a standard string
      return elements[i].length();
   }

   @Override
   public void readElement(CDRBuffer buffer)
   {
      assert elements != null;
      assert position < elements.length;

      StringBuilder element = elements[position++];
      buffer.readString(element);
   }

   @Override
   public void writeElement(int i, CDRBuffer buffer)
   {
      assert elements != null;
      assert i < elements();

      buffer.writeString(elements[i]);
   }

   @Override
   public void set(IDLStringSequence other)
   {
      assert other.elements != null;

      clear();

      int othersElements = other.elements();
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
}
