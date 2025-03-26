package us.ihmc.fastddsjava;

public class fastddsjavaException extends Exception
{
   private final int ReturnCode_t;

   public fastddsjavaException(int ReturnCode_t)
   {
      this.ReturnCode_t = ReturnCode_t;
   }

   public int getReturnCode_t()
   {
      return ReturnCode_t;
   }

   @Override
   public String getMessage()
   {
      return fastddsjavaTools.retcodeMessage(ReturnCode_t);
   }
}
