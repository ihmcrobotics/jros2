package us.ihmc.fastddsjava.cdr.idl;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.CDRSerializable;

public abstract class IDLSequence<T extends IDLSequence<T>> implements CDRSerializable
{
   protected static final int DEFAULT_INITIAL_CAPACITY = 8;

   public IDLSequence(int capacity)
   {
      ensureMinCapacity(capacity);
   }

   public IDLSequence()
   {

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
    * @implSpec {@link #elements()} of this sequence should return the same values as {@code other} after calling this method.
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
