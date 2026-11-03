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
import java.util.List;
import java.util.RandomAccess;
import java.util.function.BiConsumer;

/**
 * IDL sequence of nested messages (or other {@link CDRSerializable} elements).
 * <p>
 * The element objects are pooled: {@link #clear()} keeps them, and {@link #add()}, deserialization and
 * {@link #set(IDLObjectSequence, BiConsumer)} reuse them (a new element is created by reflection only the first time the
 * sequence reaches a size). Once the sequence has reached its working size, these calls and serialization allocate
 * nothing. Iterating with {@link #iterator()} (a for-each loop) allocates an iterator; use {@link #get(int)} and
 * {@link #size()} on a control path.
 * </p>
 */
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
         throw IDLSequenceErrors.cannotCreateElement(clazz, e);
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
      ensureSizeWithinBound(position + 1);

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
      ensureSizeWithinBound(checkedNewSize(values.length));
      System.arraycopy(values, 0, elements, position, values.length);
      position += values.length;
   }

   /**
    * Appends all elements from the collection to the end of the sequence.
    * This is an efficient bulk operation. A {@link RandomAccess} {@link List} (such as an {@link java.util.ArrayList}) is
    * read by index, allocation-free; iterating any other {@link Collection} allocates an iterator.
    *
    * @param values the collection of elements to add
    */
   public void addAll(Collection<? extends T> values)
   {
      ensureSizeWithinBound(checkedNewSize(values.size()));
      if (values instanceof List && values instanceof RandomAccess)
      {
         List<? extends T> list = (List<? extends T>) values;
         for (int i = 0, size = list.size(); i < size; ++i)
         {
            elements[position++] = list.get(i);
         }
      }
      else
      {
         for (T value : values)
         {
            elements[position++] = value;
         }
      }
   }

   /**
    * Adds a new element to the end of the sequence, creating a new instance if necessary.
    *
    * @return the element at the new position (newly created or existing)
    */
   public T add()
   {
      ensureSizeWithinBound(position + 1);

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
      if (elements.length >= desiredCapacity)
      {
         return true;
      }

      int newCapacity = grownCapacity(elements.length, desiredCapacity);
      if (newCapacity < 0)
      {
         return false;
      }

      elements = Arrays.copyOf(elements, newCapacity);

      return true;
   }

   private int checkedNewSize(int additionalElements)
   {
      long newSize = (long) position + additionalElements;
      if (newSize > getMaxSize())
      {
         throw exceedsUpperBound(newSize);
      }
      return (int) newSize;
   }

   @Override
   public int elementSizeBytes(int currentAlignment, int i)
   {
      return elements[i].calculateSizeBytes(currentAlignment);
   }

   /**
    * Object elements already include their own alignment padding in {@link #elementSizeBytes(int, int)},
    * so the base {@link IDLSequence#calculateSizeBytes(int)} loop must not add a second alignment pass.
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

   /**
    * Sets this sequence to the elements of {@code other} by copying the element <em>references</em>: afterwards both
    * sequences share the same element objects, so changing an element through one sequence (including deserializing
    * into it) changes the other. Use {@link #set(IDLObjectSequence, BiConsumer)} for an independent copy; the {@code set}
    * method of generated messages does.
    *
    * @throws IllegalArgumentException if {@code other} has more elements than {@link #getMaxSize()}
    */
   @Override
   public void set(IDLObjectSequence<T> other)
   {
      int othersElements = other.size();
      if (othersElements > getMaxSize())
      {
         throw exceedsUpperBound(othersElements);
      }

      if (other == this)
      {
         return;
      }

      clear();
      ensureMinCapacity(othersElements);

      System.arraycopy(other.elements, 0, elements, 0, othersElements);
      position = othersElements;
   }

   /**
    * Sets this sequence to an independent (deep) copy of {@code other}: element {@code i} of this sequence is set from
    * element {@code i} of {@code other} with {@code elementCopier}, e.g. {@code geometry_msgs.Pose::set} for a sequence
    * of generated messages.
    * <p>
    * The element objects of this sequence are reused (spare elements beyond the current size included), so the copy is
    * allocation-free once this sequence has grown to the size of {@code other}. A slot that holds the very object found
    * at the same index of {@code other} (as left by {@link #set(IDLObjectSequence)}) gets a new element instead, so the
    * copy never writes into {@code other}.
    * </p>
    *
    * @param other         the sequence to copy
    * @param elementCopier copies the state of its second argument (an element of {@code other}) into its first argument
    *                      (an element of this sequence)
    * @throws IllegalArgumentException if {@code other} has more elements than {@link #getMaxSize()}; this sequence is
    *                                  not changed
    */
   public void set(IDLObjectSequence<T> other, BiConsumer<? super T, ? super T> elementCopier)
   {
      int othersElements = other.size();
      if (othersElements > getMaxSize())
      {
         throw exceedsUpperBound(othersElements);
      }

      if (other == this)
      {
         return;
      }

      clear();
      ensureMinCapacity(othersElements);

      for (int i = 0; i < othersElements; ++i)
      {
         T source = other.elements[i];
         T destination = elements[i];
         if (destination == null || destination == source)
         {
            destination = newInstance();
            elements[i] = destination;
         }

         elementCopier.accept(destination, source);
      }

      position = othersElements;
   }

   @Override
   public String toString()
   {
      StringBuilder builder = new StringBuilder();
      builder.append('[');
      for (int i = 0; i < size(); ++i)
      {
         builder.append(elements[i].toString());
         if (i < size() - 1)
         {
            builder.append(',').append(' ');
         }
      }
      builder.append(']');
      return builder.toString();
   }

   /**
    * Iterates over the elements. Allocates an iterator; use {@link #get(int)} and {@link #size()} for allocation-free
    * access.
    */
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
