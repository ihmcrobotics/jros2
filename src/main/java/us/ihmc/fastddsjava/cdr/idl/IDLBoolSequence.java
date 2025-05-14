package us.ihmc.fastddsjava.cdr.idl;

import us.ihmc.fastddsjava.cdr.CDRBuffer;

import java.nio.ByteBuffer;

public class IDLBoolSequence extends IDLSequence<IDLBoolSequence>
{
   private ByteBuffer buffer;

   public IDLBoolSequence(int capacity, int maxSize)
   {
      super(capacity, maxSize);
   }

   public IDLBoolSequence(int maxSize)
   {
      super(maxSize);
   }

   public IDLBoolSequence()
   {

   }

   @Override
   public int elements()
   {
      if (buffer == null)
      {
         return 0;
      }

      return buffer.position();
   }

   @Override
   public int capacity()
   {
      if (buffer == null)
      {
         return 0;
      }

      return buffer.capacity();
   }

   @Override
   public void clear()
   {
      if (buffer != null)
      {
         buffer.clear();
      }
   }

   public void add(boolean element)
   {
      if (buffer == null)
      {
         ensureMinCapacity(Math.min(getMaxSize(), DEFAULT_INITIAL_CAPACITY));
      }
      else if (!isUnbounded() && (buffer.position() >= getMaxSize()))
      {
         throw new RuntimeException("Cannot add element to the sequence, reached upper bound");
      }
      else if (buffer.position() == buffer.capacity())
      {
         ensureMinCapacity(2 * buffer.capacity());
      }

      buffer.put((byte) (element ? 1 : 0));
   }

   public boolean get(int index)
   {
      assert index < elements();

      return buffer.get(index) == 1;
   }

   public ByteBuffer getBufferUnsafe()
   {
      return buffer;
   }

   @Override
   protected void ensureMinCapacity(int desiredCapacity)
   {
      if (capacity() < desiredCapacity)
      {
         ByteBuffer newBuffer = ByteBuffer.allocate(desiredCapacity);

         int currentElements = elements();
         if (currentElements != 0)
         {
            newBuffer.put(0, buffer, 0, currentElements);
            newBuffer.position(currentElements);
         }

         buffer = newBuffer;
      }
   }

   @Override
   public int elementSizeBytes(int i)
   {
      return 1;
   }

   @Override
   public void readElement(CDRBuffer cdrBuffer)
   {
      assert buffer != null;

      buffer.put(cdrBuffer.readByte());
   }

   @Override
   public void writeElement(int i, CDRBuffer cdrBuffer)
   {
      assert buffer != null;

      cdrBuffer.writeByte(buffer.get(i));
   }

   @Override
   public void set(IDLBoolSequence other)
   {
      clear();

      int othersElements = other.elements();
      ensureMinCapacity(othersElements);

      buffer.put(0, other.buffer, 0, othersElements);
      buffer.position(othersElements);
   }
}
