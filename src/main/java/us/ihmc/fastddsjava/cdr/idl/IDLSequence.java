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
import us.ihmc.fastddsjava.cdr.CDRDeserializationException;
import us.ihmc.fastddsjava.cdr.CDRSerializable;

/**
 * Base class of the IDL sequence types ({@code T[]} and bounded {@code T[<=N]} in .msg files).
 * <p>
 * Bounded sequences never grow beyond {@link #getMaxSize()}: {@link #ensureMinCapacity(int)} returns false and the
 * {@code add}/{@code set} methods of the subclasses throw {@link IllegalArgumentException} ("array size exceeds upper
 * bound"), as rclcpp's {@code BoundedVector} does. {@link #serialize(CDRBuffer)} refuses to write a sequence above its
 * bound, and {@link #deserialize(CDRBuffer)} throws {@link CDRDeserializationException} for a received length that is
 * above the bound or cannot fit in the remaining payload, before anything is allocated.
 * </p>
 */
public abstract class IDLSequence<T extends IDLSequence<T>> implements CDRSerializable
{
   public static final int UNBOUNDED_MAX_SIZE = Integer.MAX_VALUE;
   public static final int CAPACITY_GROW_SCALAR = 2;

   /**
    * The maximum size of the sequence. {@link #UNBOUNDED_MAX_SIZE} indicates the maximum maxSize.
    */
   private final int maxSize;

   public IDLSequence(int capacity, int maxSize)
   {
      if (maxSize < 0)
      {
         throw IDLSequenceErrors.negativeMaxSize();
      }

      if (capacity > maxSize)
      {
         throw IDLSequenceErrors.capacityAboveMaxSize();
      }

      this.maxSize = maxSize;
   }

   public IDLSequence()
   {
      this.maxSize = UNBOUNDED_MAX_SIZE;
   }

   public boolean isUnbounded()
   {
      return maxSize == UNBOUNDED_MAX_SIZE;
   }

   public int getMaxSize()
   {
      return maxSize;
   }

   /**
    * @return The number of elements in the sequence.
    */
   public abstract int size();

   /**
    * @return true if the sequence contains no elements.
    */
   public boolean isEmpty()
   {
      return size() == 0;
   }

   /**
    * @return The capacity of the sequence.
    */
   public abstract int capacity();

   /**
    * @return The remaining capacity.
    */
   public int remainingCapacity()
   {
      return capacity() - size();
   }

   /**
    * Clears all elements from the sequence.
    */
   public abstract void clear();

   /**
    * Ensures the capacity is at least {@code desiredCapacity}.
    *
    * @param desiredCapacity The minimum required capacity.
    * @implSpec If the current capacity is greater than or equal to {@code desiredCapacity}, the capacity need not be changed.
    *       Otherwise, the capacity should be increased to be grater than or equal to {@code desiredCapacity}, but never
    *       above {@link #getMaxSize()}.
    *       <p>
    *       Elements should not be added or removed by this method.
    * @return true if the capacity was not needed to be changed or was changed successfully,
    *          false (without changing anything) if {@code desiredCapacity} exceeds {@link #getMaxSize()}
    */
   public abstract boolean ensureMinCapacity(int desiredCapacity);

   public abstract int elementSizeBytes(int currentAlignment, int i);

   /**
    * Read the next element out of the buffer using CDR
    */
   public abstract void readElement(CDRBuffer buffer);

   /**
    * Write element i to the buffer using CDR
    */
   public abstract void writeElement(int i, CDRBuffer buffer);

   /**
    * Sets this sequence to {@code other}.
    *
    * @param other The sequence to set from.
    * @implSpec {@link #size()} of this sequence will return the same values as {@code other} after calling this method.
    * @throws IllegalArgumentException if {@code other} has more elements than {@link #getMaxSize()}
    */
   public abstract void set(T other);

   /**
    * The minimum number of bytes one element occupies on the wire, used to validate a received length before allocating.
    * For primitive elements this is also their alignment. Must be 1, 2, 4 or 8.
    */
   protected int minimumElementSizeBytes()
   {
      return 1;
   }

   /**
    * Computes the new capacity when growing from {@code currentCapacity} to at least {@code desiredCapacity}.
    *
    * @return the new capacity, or -1 if {@code desiredCapacity} exceeds {@link #getMaxSize()}
    */
   protected int grownCapacity(int currentCapacity, int desiredCapacity)
   {
      if (desiredCapacity > maxSize)
      {
         return -1;
      }

      long grown = Math.max((long) desiredCapacity, (long) currentCapacity * CAPACITY_GROW_SCALAR);
      return (int) Math.min(grown, maxSize);
   }

   /**
    * Ensures the capacity for {@code desiredSize} elements.
    *
    * @throws IllegalArgumentException if {@code desiredSize} exceeds {@link #getMaxSize()}
    */
   protected void ensureSizeWithinBound(int desiredSize)
   {
      if (desiredSize < 0 || !ensureMinCapacity(desiredSize))
      {
         throw exceedsUpperBound(desiredSize);
      }
   }

   protected IllegalArgumentException exceedsUpperBound(long size)
   {
      return IDLSequenceErrors.exceedsUpperBound(size, maxSize);
   }

   /**
    * Throws if the current size is above the upper bound; called before serializing.
    */
   protected void checkSizeWithinBound()
   {
      int size = size();
      if (size > maxSize)
      {
         throw exceedsUpperBound(size);
      }
   }

   /**
    * Size of a sequence of {@code size} primitive elements of {@code elementSizeBytes} bytes each, including the length
    * prefix and alignment padding.
    */
   protected static int primitiveSequenceSizeBytes(int currentAlignment, int size, int elementSizeBytes)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // Length header

      if (size > 0)
      {
         currentAlignment += CDRBuffer.alignment(currentAlignment, elementSizeBytes) + size * elementSizeBytes;
      }

      return currentAlignment - initialAlignment;
   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // Length header

      for (int i = 0; i < size(); i++)
      {
         int elementSizeBytes = elementSizeBytes(currentAlignment, i);

         currentAlignment += elementSizeBytes + CDRBuffer.alignment(currentAlignment, elementSizeBytes);
      }

      return currentAlignment - initialAlignment;
   }

   /**
    * @throws IllegalArgumentException if the sequence has more elements than its upper bound
    */
   @Override
   public void serialize(CDRBuffer buffer)
   {
      checkSizeWithinBound();

      int elements = size();

      buffer.writeSequenceLength(elements);

      for (int i = 0; i < elements; i++)
      {
         writeElement(i, buffer);
      }
   }

   /**
    * @throws CDRDeserializationException if the received length exceeds the upper bound or the remaining payload
    */
   @Override
   public void deserialize(CDRBuffer buffer)
   {
      clear();

      int elements = buffer.readSequenceLength(minimumElementSizeBytes(), maxSize);

      ensureMinCapacity(elements);

      for (int i = 0; i < elements; i++)
      {
         readElement(buffer);
      }
   }
}
