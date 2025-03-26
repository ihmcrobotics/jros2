package us.ihmc.fastddsjava;

import java.util.Random;

public final class fastddsjavaTestTools
{
   private static final Random RANDOM = new Random(1881108);

   public static byte[] generateRandomBytes(int length)
   {
      byte[] randomBytes = new byte[length];
      RANDOM.nextBytes(randomBytes);
      return randomBytes;
   }
}
