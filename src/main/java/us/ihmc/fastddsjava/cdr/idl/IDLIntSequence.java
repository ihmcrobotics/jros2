package us.ihmc.fastddsjava.cdr.idl;

import gnu.trove.list.array.TIntArrayList;
import us.ihmc.fastddsjava.cdr.CDRBuffer;

public class IDLIntSequence extends TIntArrayList implements IDLSequence
{
   @Override
   public int elements()
   {
      return size();
   }

   @Override
   public int elementSizeBytes(int i)
   {
      return 4;
   }

   @Override
   public void readElement(CDRBuffer buffer)
   {
      add(buffer.readInt());
   }

   @Override
   public void writeElement(int i, CDRBuffer buffer)
   {
      buffer.writeInt(get(i));
   }
}
