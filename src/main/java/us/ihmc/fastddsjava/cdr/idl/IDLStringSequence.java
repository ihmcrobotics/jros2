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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.RandomAccess;

/**
 * IDL sequence of strings, each element a {@link StringBuilder}.
 * <p>
 * The element builders are pooled: {@link #clear()} keeps them, and {@link #add()}, {@link #add(String)},
 * {@link #addAll(String[])}, {@link #set(IDLStringSequence)} and deserialization overwrite the pooled builders instead of
 * creating new ones. Once the sequence and its builders have reached their working size, these calls and serialization
 * allocate nothing (the builders the sequence creates are made with {@link CDRBuffer#newStringBuilder(int)}, so a first
 * non-Latin-1 character does not allocate either). Methods that return {@link String}s ({@link #getAsString(int)},
 * {@link #toStringArray()}, {@link #toString()}, iteration with {@link #iterator()}) allocate by nature; use
 * {@link #get(int)} and {@link #size()} on a control path.
 * </p>
 */
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

   public IDLStringSequence(int capacity, int maxSize)
   {
      this(capacity, maxSize, -1);
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
    * Appends a copy of {@code element} to the end of the sequence, in the pooled {@link StringBuilder} of the new position
    * (created only the first time the sequence reaches that size). Allocation-free once the pooled builder is large
    * enough.
    *
    * @param element the characters to add
    * @throws NullPointerException if {@code element} is null
    */
   public void add(String element)
   {
      if (element == null)
      {
         throw IDLSequenceErrors.nullStringElement();
      }

      ensureSizeWithinBound(position + 1);

      StringBuilder pooled = elementAtCurrentPosition();
      pooled.setLength(0);
      pooled.append(element);
      position++;
   }

   /**
    * Appends {@code element} itself (not a copy) to the end of the sequence. The sequence keeps the reference in its pool:
    * after {@link #clear()} or {@link #remove(int)}, later calls such as {@link #add()}, {@link #add(String)} or
    * deserialization may reuse and overwrite it. Use {@code add().append(element)} to add a copy.
    *
    * @param element the StringBuilder to add
    */
   public void add(StringBuilder element)
   {
      ensureSizeWithinBound(position + 1);

      elements[position++] = element;
   }

   /**
    * Appends all String elements from the array to the end of the sequence.
    * This is an efficient bulk operation.
    *
    * @param values the String array to add
    */
   public void addAll(String[] values)
   {
      ensureSizeWithinBound(checkedNewSize(values.length));
      for (String value : values)
      {
         addPooled(value);
      }
   }

   /**
    * Appends all StringBuilder elements from the array to the end of the sequence.
    * This is an efficient bulk operation using System.arraycopy. Like {@link #add(StringBuilder)}, the references are
    * stored (and later reused by the pool), not copies.
    *
    * @param values the StringBuilder array to add
    */
   public void addAll(StringBuilder[] values)
   {
      ensureSizeWithinBound(checkedNewSize(values.length));
      System.arraycopy(values, 0, elements, position, values.length);
      position += values.length;
   }

   /**
    * Appends all String elements from the collection to the end of the sequence.
    * Reuses pre-allocated {@link StringBuilder} slots. A {@link RandomAccess} {@link List} (such as an
    * {@link java.util.ArrayList}) is read by index, allocation-free; iterating any other {@link Collection} allocates an
    * iterator.
    */
   public void addAll(Collection<String> values)
   {
      ensureSizeWithinBound(checkedNewSize(values.size()));
      if (values instanceof List && values instanceof RandomAccess)
      {
         List<String> list = (List<String>) values;
         for (int i = 0, size = list.size(); i < size; ++i)
         {
            addPooled(list.get(i));
         }
      }
      else
      {
         for (String value : values)
         {
            addPooled(value);
         }
      }
   }

   private void addPooled(CharSequence value)
   {
      StringBuilder element = elementAtCurrentPosition();
      element.setLength(0);
      element.append(value);
      position++;
   }

   /**
    * Adds an empty string to the end of the sequence and returns its {@link StringBuilder} to fill, e.g.
    * {@code add().append("joint_").append(i)}: the pooled builder of the new position, cleared (created with the default
    * string length only the first time the sequence reaches that size).
    *
    * @return the empty StringBuilder at the new position
    */
   public StringBuilder add()
   {
      return add(defaultStringLength);
   }

   /**
    * Adds an empty string to the end of the sequence and returns its {@link StringBuilder} to fill, with a capacity of at
    * least {@code stringLength}: the pooled builder of the new position, cleared (created only the first time the sequence
    * reaches that size).
    *
    * @param stringLength the minimum capacity of the StringBuilder (ignored if not positive)
    * @return the empty StringBuilder at the new position
    */
   public StringBuilder add(int stringLength)
   {
      ensureSizeWithinBound(position + 1);

      StringBuilder element = elements[position];
      if (element == null)
      {
         element = CDRBuffer.newStringBuilder(stringLength > 0 ? stringLength : DEFAULT_MAX_STRING_LENGTH);
         elements[position] = element;
      }
      else
      {
         element.setLength(0);
         if (stringLength > 0)
         {
            element.ensureCapacity(stringLength);
         }
      }

      position++;
      return element;
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
    * Returns the String representation of the element at the specified index. Allocates a new {@link String}; use
    * {@link #get(int)} for allocation-free access.
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
      // Body of a CDR string excluding the 4-byte alignment padding before the length prefix:
      // length int + UTF-8 bytes + null terminator.
      return 4 + CDRBuffer.utf8Length(elements[i]) + 1;
   }

   /**
    * Every string occupies at least its 4-byte length prefix on the wire.
    */
   @Override
   protected int minimumElementSizeBytes()
   {
      return 4;
   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // sequence length prefix

      for (int i = 0; i < size(); i++)
      {
         currentAlignment += CDRBuffer.alignment(currentAlignment, 4); // align before string length
         currentAlignment += 4 + CDRBuffer.utf8Length(elements[i]) + 1; // length int + UTF-8 bytes + null terminator
      }

      return currentAlignment - initialAlignment;
   }

   @Override
   public void readElement(CDRBuffer buffer)
   {
      StringBuilder element = elementAtCurrentPosition();
      buffer.readString(element);
      position++;
   }

   /**
    * Returns the {@link StringBuilder} at {@link #position}, creating one if the pre-allocated slot is still null.
    */
   protected StringBuilder elementAtCurrentPosition()
   {
      if (elements[position] == null)
      {
         elements[position] = CDRBuffer.newStringBuilder(defaultStringLength > 0 ? defaultStringLength : DEFAULT_MAX_STRING_LENGTH);
      }

      return elements[position];
   }

   @Override
   public void writeElement(int i, CDRBuffer buffer)
   {
      buffer.writeString(elements[i]);
   }

   @Override
   public void set(IDLStringSequence other)
   {
      int othersElements = other.size();
      if (othersElements > getMaxSize())
      {
         throw exceedsUpperBound(othersElements);
      }

      if (other == this)
      {
         return; // clearing each element before copying it would erase it
      }

      clear();
      ensureMinCapacity(othersElements);

      for (int i = 0; i < othersElements; ++i)
      {
         // A null slot, or an element object shared with other (e.g. added to both with add(StringBuilder)), gets a new
         // StringBuilder so that the sequences stay independent; clearing a shared element would erase the source.
         if (elements[i] == null || elements[i] == other.elements[i])
         {
            elements[i] = CDRBuffer.newStringBuilder(other.elements[i]);
         }
         else
         {
            elements[i].setLength(0);
            elements[i].append(other.elements[i]);
         }
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
    * @return a new array with a new {@link String} per element (allocates)
    */
   public String[] toStringArray()
   {
      String[] array = new String[size()];
      for (int i = 0; i < size(); i++)
      {
         array[i] = elements[i].toString();
      }
      return array;
   }

   /**
    * Iterates over the elements as {@link String}s. Allocates an iterator and a {@link String} per element; use
    * {@link #get(int)} and {@link #size()} for allocation-free access.
    */
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
