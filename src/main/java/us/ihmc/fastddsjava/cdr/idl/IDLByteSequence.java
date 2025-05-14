package us.ihmc.fastddsjava.cdr.idl;

import us.ihmc.fastddsjava.cdr.CDRBuffer;

import java.nio.ByteBuffer;

public class IDLByteSequence extends IDLSequence<IDLByteSequence>
{
   private ByteBuffer buffer;

   public IDLByteSequence(int capacity, int maxSize)
   {
      super(capacity, maxSize);
   }

   public IDLByteSequence(int maxSize)
   {
      super(maxSize);
   }

   public IDLByteSequence()
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

   public void add(byte element)
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

      buffer.put(element);
   }

   public byte get(int index)
   {
      assert index < elements();

      return buffer.get(index);
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
   public void set(IDLByteSequence other)
   {
      clear();

      int othersElements = other.elements();
      ensureMinCapacity(othersElements);

      buffer.put(0, other.buffer, 0, othersElements);
      buffer.position(othersElements);
   }
}
