package us.ihmc.fastddsjava;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.idl.IDLByteSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLSequence;

import static org.junit.jupiter.api.Assertions.*;

public class IDLSequenceTest
{
   @Test
   public void testIDLByteSequence()
   {
      final int initialCapacity = 8;

      IDLByteSequence byteSequence = new IDLByteSequence(initialCapacity);

      assertEquals(0, byteSequence.elements());
      assertEquals(initialCapacity, byteSequence.capacity());

      for (int i = 0; i < initialCapacity; ++i)
      {
         byteSequence.add((byte) i);
      }

      assertEquals(initialCapacity, byteSequence.elements());
      assertEquals(initialCapacity, byteSequence.capacity());

      byteSequence.add((byte) (initialCapacity + 1));

      assertEquals(initialCapacity + 1, byteSequence.elements());
      assertEquals(2 * initialCapacity, byteSequence.elements());
   }
}
