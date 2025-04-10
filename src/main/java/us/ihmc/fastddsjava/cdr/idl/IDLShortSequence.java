package us.ihmc.fastddsjava.cdr.idl;

import us.ihmc.fastddsjava.cdr.CDRBuffer;

import java.nio.ShortBuffer;

public class IDLShortSequence extends IDLSequence<IDLShortSequence>
{
   private ShortBuffer buffer;

   public IDLShortSequence(int capacity)
   {
      super(capacity);
   }

   public IDLShortSequence()
   {

   }

   @Override
   public int elements()
   {
      return buffer.position();
   }

   @Override
   public int capacity()
   {
      return buffer.capacity();
   }

   public void add(short element)
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
         ShortBuffer newBuffer = ShortBuffer.allocate(capacity);

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
      return 2;
   }

   @Override
   public void readElement(CDRBuffer cdrBuffer)
   {
      assert buffer != null;

      buffer.put(cdrBuffer.readShort());
   }

   @Override
   public void writeElement(int i, CDRBuffer cdrBuffer)
   {
      assert buffer != null;

      cdrBuffer.writeShort(buffer.get(i));
   }

   @Override
   public void set(IDLShortSequence other)
   {
      ensureMinCapacity(other.elements());

      buffer.rewind();
      buffer.put(other.buffer);
      buffer.rewind();
   }
}
