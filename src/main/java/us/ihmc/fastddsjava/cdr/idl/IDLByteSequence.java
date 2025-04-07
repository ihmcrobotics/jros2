package us.ihmc.fastddsjava.cdr.idl;

import us.ihmc.fastddsjava.cdr.CDRBuffer;

import java.nio.ByteBuffer;

public class IDLByteSequence extends IDLSequence<IDLByteSequence>
{
   private ByteBuffer buffer;

   public IDLByteSequence(int capacity)
   {
      super(capacity);
   }

   public IDLByteSequence()
   {

   }

   @Override
   public int elements()
   {
      return buffer.position();
   }

   public void add(byte element)
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
         ByteBuffer newBuffer = ByteBuffer.allocate(capacity);

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
      ensureMinCapacity(other.elements());

      buffer.rewind();
      buffer.put(other.buffer);
      buffer.rewind();
   }
}
