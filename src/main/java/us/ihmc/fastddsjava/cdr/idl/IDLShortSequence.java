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

   public void add(short element)
   {
      if (buffer == null)
      {
         ensureMinCapacity(DEFAULT_INITIAL_CAPACITY);
      }
      else if (buffer.position() == buffer.capacity())
      {
         ensureMinCapacity(2 * buffer.capacity());
      }

      buffer.put(element);
   }

   public short get(int index)
   {
      assert index < elements();

      return buffer.get(index);
   }

   public ShortBuffer getBufferUnsafe()
   {
      return buffer;
   }

   @Override
   protected void ensureMinCapacity(int desiredCapacity)
   {
      if (capacity() < desiredCapacity)
      {
         ShortBuffer newBuffer = ShortBuffer.allocate(desiredCapacity);

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
      clear();

      int othersElements = other.elements();
      ensureMinCapacity(othersElements);

      buffer.put(0, other.buffer, 0, othersElements);
      buffer.position(othersElements);
   }
}
