package us.ihmc.fastddsjava.cdr.idl;

import us.ihmc.fastddsjava.cdr.CDRBuffer;

import java.nio.IntBuffer;

public class IDLIntSequence extends IDLSequence<IDLIntSequence>
{
   private IntBuffer buffer;

   public IDLIntSequence(int capacity)
   {
      super(capacity);
   }

   public IDLIntSequence()
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

   @Override
   public void clear()
   {
      buffer.clear();
   }

   public void add(int element)
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
         IntBuffer newBuffer = IntBuffer.allocate(capacity);

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
      return 4;
   }

   @Override
   public void readElement(CDRBuffer cdrBuffer)
   {
      assert buffer != null;

      buffer.put(cdrBuffer.readInt());
   }

   @Override
   public void writeElement(int i, CDRBuffer cdrBuffer)
   {
      assert buffer != null;

      cdrBuffer.writeInt(buffer.get(i));
   }

   @Override
   public void set(IDLIntSequence other)
   {
      ensureMinCapacity(other.elements());

      buffer.rewind();
      buffer.put(other.buffer);
      buffer.rewind();
   }
}
