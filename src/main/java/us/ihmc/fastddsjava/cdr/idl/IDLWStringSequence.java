package us.ihmc.fastddsjava.cdr.idl;

import us.ihmc.fastddsjava.cdr.CDRBuffer;

public class IDLWStringSequence extends IDLStringSequence
{
   public IDLWStringSequence(int capacity, int maxSize)
   {
      super(capacity, maxSize);
      position = 0;
   }

   public IDLWStringSequence(int maxSize)
   {
      super(maxSize);
      position = 0;
   }

   public IDLWStringSequence()
   {
      position = 0;
   }

   @Override
   public int elementSizeBytes(int i)
   {
      assert elements != null;
      assert i < elements();

      return elements[i].length() * 4; // 4 bytes per character
   }

   @Override
   public void readElement(CDRBuffer buffer)
   {
      assert elements != null;
      assert position < elements.length;

      StringBuilder element = elements[position++];
      buffer.readWString(element);
   }

   @Override
   public void writeElement(int i, CDRBuffer buffer)
   {
      assert elements != null;
      assert i < elements();

      buffer.writeWString(elements[i]);
   }
}
