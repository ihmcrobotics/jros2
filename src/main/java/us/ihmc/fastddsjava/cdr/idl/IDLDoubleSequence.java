package us.ihmc.fastddsjava.cdr.idl;

import us.ihmc.fastddsjava.cdr.CDRBuffer;

import java.nio.DoubleBuffer;

public class IDLDoubleSequence extends IDLSequence<IDLDoubleSequence>
{
   private DoubleBuffer buffer;

   public IDLDoubleSequence(int capacity)
   {
      super(capacity);
   }

   public IDLDoubleSequence()
   {

   }

   @Override
   public int elements()
   {
      return buffer.position();
   }

   public void add(double element)
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
         DoubleBuffer newBuffer = DoubleBuffer.allocate(capacity);

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

      buffer.put(cdrBuffer.readDouble());
   }

   @Override
   public void writeElement(int i, CDRBuffer cdrBuffer)
   {
      assert buffer != null;

      cdrBuffer.writeDouble(buffer.get(i));
   }

   @Override
   public void set(IDLDoubleSequence other)
   {
      ensureMinCapacity(other.elements());

      buffer.rewind();
      buffer.put(other.buffer);
      buffer.rewind();
   }
}
