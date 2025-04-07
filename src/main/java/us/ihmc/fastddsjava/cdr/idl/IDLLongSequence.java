package us.ihmc.fastddsjava.cdr.idl;

import us.ihmc.fastddsjava.cdr.CDRBuffer;

import java.nio.LongBuffer;

public class IDLLongSequence extends IDLSequence<IDLLongSequence>
{
   private LongBuffer buffer;

   public IDLLongSequence(int capacity)
   {
      super(capacity);
   }

   public IDLLongSequence()
   {

   }

   @Override
   public int elements()
   {
      return buffer.position();
   }

   public void add(long element)
   {
      if (buffer.position() == buffer.capacity())
         ensureMinCapacity(2 * buffer.capacity());

      buffer.put(element);
   }

   @Override
   protected void ensureMinCapacity(int capacity)
   {
      if (buffer == null || buffer.capacity() < capacity)
      {
         LongBuffer newBuffer = LongBuffer.allocate(capacity);

         if (buffer != null)
         {
            newBuffer.put(buffer);
         }

         buffer = newBuffer;
      }
   }

   @Override
   public int elementSizeBytes(int i)
   {
      return 8;
   }

   @Override
   public void readElement(CDRBuffer cdrBuffer)
   {
      assert buffer != null;

      buffer.put(cdrBuffer.readLong());
   }

   @Override
   public void writeElement(int i, CDRBuffer cdrBuffer)
   {
      assert buffer != null;

      cdrBuffer.writeLong(buffer.get(i));
   }

   @Override
   public void set(IDLLongSequence other)
   {
      ensureMinCapacity(other.elements());

      buffer.rewind();
      buffer.put(other.buffer);
      buffer.rewind();
   }
}
