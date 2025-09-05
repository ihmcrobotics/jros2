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
import us.ihmc.fastddsjava.cdr.CDRSerializable;
import us.ihmc.log.LogTools;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

public class IDLObjectSequence<T extends CDRSerializable> extends IDLSequence<IDLObjectSequence<T>>
{
   private final Class<T> clazz;

   private T[] elements;
   private int position;

   public IDLObjectSequence(int capacity, int maxSize, Class<T> clazz)
   {
      super(capacity, maxSize);
      this.clazz = clazz;
      position = 0;
   }

   public IDLObjectSequence(int capacity, Class<T> clazz)
   {
      super(capacity, IDLSequence.UNBOUNDED_MAX_SIZE);
      this.clazz = clazz;
      position = 0;
   }

   public IDLObjectSequence(Class<T> clazz)
   {
      this.clazz = clazz;
      position = 0;
   }

   private T newInstance()
   {
      try
      {
         return clazz.getDeclaredConstructor().newInstance();
      }
      catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e)
      {
         LogTools.error("Unable to create an instance of CDRSerializable class: " + clazz.getName());
      }

      return null;
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

   public void add(T element)
   {
      ensureMinCapacity(position + 1);

      elements[position++] = element;
   }

   public T add()
   {
      ensureMinCapacity(position + 1);

      if (elements[position] == null)
      {
         elements[position] = newInstance();
      }

      return elements[position++];
   }

   public void remove()
   {
      position--;
   }

   public T get(int index)
   {
      assert index < elements();

      return elements[index];
   }

   public T[] getArrayUnsafe()
   {
      return elements;
   }

   @Override
   @SuppressWarnings("unchecked")
   public void ensureMinCapacity(int desiredCapacity)
   {
      if (capacity() < desiredCapacity)
      {
         if (desiredCapacity > getMaxSize())
         {
            LogTools.error("Cannot add element to the sequence, reached upper bound");
         }

         if (elements == null)
         {
            elements = (T[]) new CDRSerializable[desiredCapacity];
         }
         else
         {
            desiredCapacity = Math.min(Math.max(desiredCapacity, elements.length * 2), getMaxSize());
            elements = Arrays.copyOf(elements, desiredCapacity);
         }
      }
   }

   @Override
   public int elementSizeBytes(int currentAlignment, int i)
   {
      assert elements != null;
      assert i < elements();

      return elements[i].calculateSizeBytes(currentAlignment);
   }

   @Override
   public void readElement(CDRBuffer buffer)
   {
      assert elements != null;
      assert position < elements.length;

      if (elements[position] == null)
      {
         elements[position] = newInstance();
      }

      elements[position].deserialize(buffer);

      position++;
   }

   @Override
   public void writeElement(int i, CDRBuffer buffer)
   {
      assert elements != null;
      assert i < elements();

      if (elements[i] == null)
      {
         elements[i] = newInstance();
      }

      elements[i].serialize(buffer);
   }

   @Override
   public void set(IDLObjectSequence<T> other)
   {
      assert clazz == other.clazz;
      assert other.elements != null;

      clear();

      int othersElements = other.elements();
      ensureMinCapacity(othersElements);

      // TODO: This could be done better if this has existing elements
      System.arraycopy(other.elements, 0, elements, 0, othersElements);
      position = other.elements();
   }
}
