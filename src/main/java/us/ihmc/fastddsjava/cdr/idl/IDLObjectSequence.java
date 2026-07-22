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

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
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
         throw new RuntimeException("Unable to create an instance of CDRSerializable class: " + clazz.getName(), e);
      }
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
    * Appends all elements from the array to the end of the sequence.
    * This is an efficient bulk operation using System.arraycopy.
    *
    * @param values the array of elements to add
    */
   public void addAll(T[] values)
   {
      ensureMinCapacity(position + values.length);
      System.arraycopy(values, 0, elements, position, values.length);
      position += values.length;
   }

   /**
    * Appends all elements from the collection to the end of the sequence.
    * This is an efficient bulk operation.
    *
    * @param values the collection of elements to add
    */
   public void addAll(Collection<? extends T> values)
   {
      ensureMinCapacity(position + values.size());
      for (T value : values)
      {
         elements[position++] = value;
      }
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
         T t = elements[index];

         while (index < position - 1)
         {
            elements[index] = elements[++index];
         }

         // Do not throw away the removed element, put it at the end of the list instead.
         elements[position - 1] = t;
      }

      position--;
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
    * Returns the last element in the sequence.
    *
    * @return the last element
    */
   public T getLast()
   {
      return elements[position - 1];
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

   /**
    * Overridden because {@link IDLSequence#calculateSizeBytes(int)}'s generic loop assumes
    * {@link #elementSizeBytes(int, int)} returns a size that does not itself depend on the alignment context it
    * was given - true for every fixed-size primitive sequence (e.g. {@link IDLDoubleSequence}, whose element size
    * is always the constant {@code 8}), where the loop is responsible for computing and inserting the leading
    * alignment padding before each element via {@code CDRBuffer.alignment(currentAlignment, elementSizeBytes)}.
    * <p>
    * That assumption does not hold here: {@link #elementSizeBytes(int, int)} forwards directly to
    * {@code elements[i].calculateSizeBytes(currentAlignment)}, and a well-behaved {@link CDRSerializable} (e.g. any
    * jros2-generated message class) already accounts for its own leading alignment padding relative to the given
    * {@code currentAlignment} as part of that call - see e.g. {@code std_msgs.Header#calculateSizeBytes}. Reusing
    * the base loop on top of that double-counts alignment: it re-derives a padding amount from the *total* size of
    * the (already-aligned) element and adds it again, which is not only redundant but incoherent, since
    * {@link CDRBuffer#alignment(int, int)} assumes its {@code bytes} argument is itself a valid power-of-two CDR
    * alignment boundary (1, 2, 4, or 8) - never true for an arbitrary struct's total encoded size. In practice this
    * could throw a two-element {@code tf2_msgs.TFMessage.transforms} sequence's computed size off by tens of
    * bytes in either direction (observed both over- and under-counting depending on element sizes), which matters
    * because callers such as {@code ROS2Publisher#writeAndPublish} use this value as the exact number of bytes
    * copied to the wire.
    */
   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // Length header

      for (int i = 0; i < size(); i++)
      {
         currentAlignment += elementSizeBytes(currentAlignment, i);
      }

      return currentAlignment - initialAlignment;
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
