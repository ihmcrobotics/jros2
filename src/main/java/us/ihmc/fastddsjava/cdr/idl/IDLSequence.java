package us.ihmc.fastddsjava.cdr.idl;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.CDRSerializable;

public interface IDLSequence extends CDRSerializable
{
   int elements();

   int elementSizeBytes(int i);

   /**
    * Read the next element out of the buffer using CDR
    */
   void readElement(CDRBuffer buffer);

   /**
    * Write element i to the buffer using CDR
    */
   void writeElement(int i, CDRBuffer buffer);

   @Override
   default int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // Length header

      for (int i = 0; i < elements(); i++)
      {
         currentAlignment += elementSizeBytes(i) + CDRBuffer.alignment(currentAlignment, elementSizeBytes(i));
      }

      return currentAlignment - initialAlignment;
   }

   @Override
   default void serialize(CDRBuffer buffer)
   {
      int elements = elements();

      buffer.writeInt(elements);

      for (int i = 0; i < elements; i++)
      {
         writeElement(i, buffer);
      }
   }

   @Override
   default void deserialize(CDRBuffer buffer)
   {
      int elements = buffer.readInt();

      for (int i = 0; i < elements; i++)
      {
         readElement(buffer);
      }
   }
}
