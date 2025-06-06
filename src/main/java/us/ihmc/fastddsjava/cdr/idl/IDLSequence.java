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

public abstract class IDLSequence<T extends IDLSequence<T>> implements CDRSerializable
{
   protected static final int INFINITE_MAX_SIZE = -1;
   protected static final int DEFAULT_INITIAL_CAPACITY = 1;

   /**
    * The maximum size of the sequence. -1 indicates no maximum size.
    */
   private final int maxSize;

   public IDLSequence(int capacity, int maxSize)
   {
      this.maxSize = maxSize;

      if (!isUnbounded() && (capacity > maxSize))
      {
         throw new RuntimeException("capacity cannot be larger than maxSize for an IDLSequence");
      }

      ensureMinCapacity(capacity);
   }

   public IDLSequence(int maxSize)
   {
      this.maxSize = maxSize;
   }

   public IDLSequence()
   {
      this.maxSize = INFINITE_MAX_SIZE;
   }

   public boolean isUnbounded()
   {
      return maxSize == INFINITE_MAX_SIZE;
   }

   public int getMaxSize()
   {
      return maxSize;
   }

   /**
    * @return The number of elements in the sequence.
    */
   public abstract int elements();

   /**
    * @return The capacity of the sequence.
    */
   public abstract int capacity();

   /**
    * @return The remaining capacity.
    */
   public int remainingCapacity()
   {
      return capacity() - elements();
   }

   /**
    * Clears all elements from the sequence.
    */
   public abstract void clear();

   /**
    * Ensures the capacity is at least {@code capacity}.
    *
    * @param capacity The minimum required capacity.
    * @implSpec If the current capacity is greater than or equal to {@code capacity}, the capacity need not be changed.
    *       Otherwise, the capacity should be increased to be grater than or equal to {@code capacity}.
    *       <p>
    *       Elements should not be added or removed by this method.
    */
   protected abstract void ensureMinCapacity(int capacity);

   public abstract int elementSizeBytes(int i);

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
    * @implSpec {@link #elements()} of this sequence will return the same values as {@code other} after calling this method.
    */
   public abstract void set(T other);

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // Length header

      for (int i = 0; i < elements(); i++)
      {
         currentAlignment += elementSizeBytes(i);
      }

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      int elements = elements();

      buffer.writeInt(elements);

      for (int i = 0; i < elements; i++)
      {
         writeElement(i, buffer);
      }
   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      int elements = buffer.readInt();

      for (int i = 0; i < elements; i++)
      {
         readElement(buffer);
      }
   }
}
