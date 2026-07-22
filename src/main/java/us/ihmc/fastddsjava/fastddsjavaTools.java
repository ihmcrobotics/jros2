/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.fastddsjava;

import static us.ihmc.fastddsjava.natives.fastddsjava.*;

public final class fastddsjavaTools
{
   private fastddsjavaTools()
   {
   }

   public static String retcodeName(int ReturnCode_t)
   {
      String name;

      if (RETCODE_OK == ReturnCode_t)
      {
         name = "RETCODE_OK";
      }
      else if (RETCODE_ERROR == ReturnCode_t)
      {
         name = "RETCODE_ERROR";
      }
      else if (RETCODE_UNSUPPORTED == ReturnCode_t)
      {
         name = "RETCODE_UNSUPPORTED";
      }
      else if (RETCODE_BAD_PARAMETER == ReturnCode_t)
      {
         name = "RETCODE_BAD_PARAMETER";
      }
      else if (RETCODE_PRECONDITION_NOT_MET == ReturnCode_t)
      {
         name = "RETCODE_PRECONDITION_NOT_MET";
      }
      else if (RETCODE_OUT_OF_RESOURCES == ReturnCode_t)
      {
         name = "RETCODE_OUT_OF_RESOURCES";
      }
      else if (RETCODE_NOT_ENABLED == ReturnCode_t)
      {
         name = "RETCODE_NOT_ENABLED";
      }
      else if (RETCODE_IMMUTABLE_POLICY == ReturnCode_t)
      {
         name = "RETCODE_IMMUTABLE_POLICY";
      }
      else if (RETCODE_INCONSISTENT_POLICY == ReturnCode_t)
      {
         name = "RETCODE_INCONSISTENT_POLICY";
      }
      else if (RETCODE_ALREADY_DELETED == ReturnCode_t)
      {
         name = "RETCODE_ALREADY_DELETED";
      }
      else if (RETCODE_TIMEOUT == ReturnCode_t)
      {
         name = "RETCODE_TIMEOUT";
      }
      else if (RETCODE_NO_DATA == ReturnCode_t)
      {
         name = "RETCODE_NO_DATA";
      }
      else if (RETCODE_ILLEGAL_OPERATION == ReturnCode_t)
      {
         name = "RETCODE_ILLEGAL_OPERATION";
      }
      else
      {
         name = "RETCODE_UNKNOWN";
      }

      return name;
   }

   public static String retcodeMessage(int ReturnCode_t)
   {
      return "Fast-DDS retcode (%d): %s".formatted(ReturnCode_t, retcodeName(ReturnCode_t));
   }

   public static void retcodeThrowOnError(int ReturnCode_t) throws fastddsjavaException
   {
      if (RETCODE_OK != ReturnCode_t)
      {
         throw new fastddsjavaException(ReturnCode_t);
      }
   }

   public static void retcodePrintOnError(int ReturnCode_t)
   {
      // Avoid allocating an exception on the publish hot path when the retcode is OK.
      if (RETCODE_OK != ReturnCode_t)
      {
         System.err.println("Fast-DDS error: %s".formatted(retcodeMessage(ReturnCode_t)));
      }
   }
}
