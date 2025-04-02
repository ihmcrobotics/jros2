package us.ihmc.fastddsjava.cdr.idl;

import gnu.trove.list.array.TIntArrayList;
import us.ihmc.fastddsjava.cdr.CDRBuffer;

public class IDLIntSequence extends TIntArrayList implements IDLSequence<IDLIntSequence>
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

   @Override
   public void set(IDLIntSequence other)
   {
      resetQuick();

      for (int i = 0; i < other.size(); i++)
      {
         add(other.get(i));
      }
   }
}
