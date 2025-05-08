package us.ihmc.fastddsjava;

import us.ihmc.log.LogTools;

import static us.ihmc.fastddsjava.pointers.fastddsjava.*;

public final class fastddsjavaTools
{
   private static final int OK = RETCODE_OK(); // Minor optimization

   public static String retcodeName(int ReturnCode_t)
   {
      if (OK == ReturnCode_t)
         return "RETCODE_OK";
      else if (RETCODE_ERROR() == ReturnCode_t)
         return "RETCODE_ERROR";
      else if (RETCODE_UNSUPPORTED() == ReturnCode_t)
         return "RETCODE_UNSUPPORTED";
      else if (RETCODE_BAD_PARAMETER() == ReturnCode_t)
         return "RETCODE_BAD_PARAMETER";
      else if (RETCODE_PRECONDITION_NOT_MET() == ReturnCode_t)
         return "RETCODE_PRECONDITION_NOT_MET";
      else if (RETCODE_OUT_OF_RESOURCES() == ReturnCode_t)
         return "RETCODE_OUT_OF_RESOURCES";
      else if (RETCODE_NOT_ENABLED() == ReturnCode_t)
         return "RETCODE_NOT_ENABLED";
      else if (RETCODE_IMMUTABLE_POLICY() == ReturnCode_t)
         return "RETCODE_IMMUTABLE_POLICY";
      else if (RETCODE_INCONSISTENT_POLICY() == ReturnCode_t)
         return "RETCODE_INCONSISTENT_POLICY";
      else if (RETCODE_ALREADY_DELETED() == ReturnCode_t)
         return "RETCODE_ALREADY_DELETED";
      else if (RETCODE_TIMEOUT() == ReturnCode_t)
         return "RETCODE_TIMEOUT";
      else if (RETCODE_NO_DATA() == ReturnCode_t)
         return "RETCODE_NO_DATA";
      else if (RETCODE_ILLEGAL_OPERATION() == ReturnCode_t)
         return "RETCODE_ILLEGAL_OPERATION";
      else
         return "RETCODE_UNKNOWN";
   }

   public static String retcodeMessage(int ReturnCode_t)
   {
      return "Fast-DDS retcode (%d): %s".formatted(ReturnCode_t, retcodeName(ReturnCode_t));
   }

   public static void retcodeThrowOnError(int ReturnCode_t) throws fastddsjavaException
   {
      if (OK != ReturnCode_t)
      {
         throw new fastddsjavaException(ReturnCode_t);
      }
   }

   public static void retcodePrintOnError(int ReturnCode_t)
   {
      try
      {
         retcodeThrowOnError(ReturnCode_t);
      }
      catch (fastddsjavaException e)
      {
         LogTools.error(e);
      }
   }
}
