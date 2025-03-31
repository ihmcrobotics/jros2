package us.ihmc.fastddsjava.cdr;

public interface CDRSerializable
{
   default int calculateSizeBytes()
   {
      return calculateSizeBytes(0);
   }

   int calculateSizeBytes(int currentAlignment);

   void serialize(CDRBuffer buffer);

   void deserialize(CDRBuffer buffer);
}
