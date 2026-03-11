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
import java.util.Iterator;

@SuppressWarnings("unchecked")
public class IDLObjectSequence<T extends CDRSerializable> extends IDLSequence<IDLObjectSequence<T>> implements Iterable<T>
{
   private static final CDRSerializable[] EMPTY_ARRAY = new CDRSerializable[0];

   private final Class<T> clazz;
   private T[] elements;
   private int position;

   public IDLObjectSequence(int capacity, int maxSize, Class<T> clazz)
   {
      super(capacity, maxSize);

      this.clazz = clazz;
      elements = (T[]) EMPTY_ARRAY;
      position = 0;

      ensureMinCapacity(capacity);
   }

   public IDLObjectSequence(int capacity, Class<T> clazz)
   {
      super(capacity, IDLSequence.UNBOUNDED_MAX_SIZE);

      this.clazz = clazz;
      elements = (T[]) EMPTY_ARRAY;
      position = 0;

      ensureMinCapacity(capacity);
   }

   public IDLObjectSequence(Class<T> clazz)
   {
      this.clazz = clazz;
      elements = (T[]) EMPTY_ARRAY;
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
    * Appends an element to the end of the sequence.
    *
    * @param element the element to add
    */
   public void add(T element)
   {
      ensureMinCapacity(position + 1);

      elements[position++] = element;
   }

   /**
    * Adds a new element to the end of the sequence, creating a new instance if necessary.
    *
    * @return the element at the new position (newly created or existing)
    */
   public T add()
   {
      ensureMinCapacity(position + 1);

      if (elements[position] == null)
      {
         elements[position] = newInstance();
      }

      return elements[position++];
   }

   /**
    * Removes and returns the last element from the sequence.
    *
    * @return the last element in the sequence
    */
   public T remove()
   {
      return elements[--position];
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
      if (index == position - 1)
      {
         position--;
      }
      else
      {
         T t = elements[index];

         while (index < position - 1)
         {
            elements[index] = elements[++index];
         }

         // Do not throw away the removed element, put it at the end of the list instead.
         elements[position - 1] = t;
         position--;
      }
   }

   /**
    * Returns the element at the specified index.
    *
    * @param index the index of the element to return
    * @return the element at the specified index
    */
   public T get(int index)
   {
      return elements[index];
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
      return elements[i].calculateSizeBytes(currentAlignment);
   }

   @Override
   public void readElement(CDRBuffer buffer)
   {
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
      if (elements[i] == null)
      {
         elements[i] = newInstance();
      }

      elements[i].serialize(buffer);
   }

   @Override
   public void set(IDLObjectSequence<T> other)
   {
      clear();

      int othersElements = other.size();
      ensureMinCapacity(othersElements);

      // TODO: This could be done better if this has existing elements
      System.arraycopy(other.elements, 0, elements, 0, othersElements);
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

   @Override
   public Iterator<T> iterator()
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
         public T next()
         {
            return elements[index++];
         }

         @Override
         public void remove()
         {
            IDLObjectSequence.this.remove(--index);
         }
      };
   }
}
