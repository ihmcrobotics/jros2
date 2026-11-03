/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.fastddsjava;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static us.ihmc.fastddsjava.natives.fastddsjava.*;

/**
 * {@link fastddsjavaTools} and {@link fastddsjavaException}: the Fast-DDS return codes jros2 reports in its errors, and the
 * decoding of {@code dataReaderTakeNextToBuffer} results. No native library is needed.
 */
public class fastddsjavaToolsTest
{
   /** The Fast-DDS 3 {@code ReturnCode_t} values (fastdds/dds/core/ReturnCode.hpp) and their names. */
   private static final Object[][] RETCODES = {{RETCODE_OK, "RETCODE_OK", 0},
                                               {RETCODE_ERROR, "RETCODE_ERROR", 1},
                                               {RETCODE_UNSUPPORTED, "RETCODE_UNSUPPORTED", 2},
                                               {RETCODE_BAD_PARAMETER, "RETCODE_BAD_PARAMETER", 3},
                                               {RETCODE_PRECONDITION_NOT_MET, "RETCODE_PRECONDITION_NOT_MET", 4},
                                               {RETCODE_OUT_OF_RESOURCES, "RETCODE_OUT_OF_RESOURCES", 5},
                                               {RETCODE_NOT_ENABLED, "RETCODE_NOT_ENABLED", 6},
                                               {RETCODE_IMMUTABLE_POLICY, "RETCODE_IMMUTABLE_POLICY", 7},
                                               {RETCODE_INCONSISTENT_POLICY, "RETCODE_INCONSISTENT_POLICY", 8},
                                               {RETCODE_ALREADY_DELETED, "RETCODE_ALREADY_DELETED", 9},
                                               {RETCODE_TIMEOUT, "RETCODE_TIMEOUT", 10},
                                               {RETCODE_NO_DATA, "RETCODE_NO_DATA", 11},
                                               {RETCODE_ILLEGAL_OPERATION, "RETCODE_ILLEGAL_OPERATION", 12}};

   @Test
   public void testEveryReturnCodeHasItsFastDDSValueAndName()
   {
      Set<String> names = new HashSet<>();
      for (Object[] retcode : RETCODES)
      {
         int value = (Integer) retcode[0];
         assertEquals(retcode[2], value, retcode[1] + " must keep its Fast-DDS value");
         assertEquals(retcode[1], fastddsjavaTools.retcodeName(value));
         names.add(fastddsjavaTools.retcodeName(value));
      }
      assertEquals(RETCODES.length, names.size(), "every return code has its own name");
   }

   @Test
   public void testUnknownReturnCodes()
   {
      for (int value : new int[] {-1, 13, 100, Integer.MIN_VALUE, Integer.MAX_VALUE})
      {
         assertEquals("RETCODE_UNKNOWN", fastddsjavaTools.retcodeName(value), Integer.toString(value));
      }
      assertEquals("Fast-DDS retcode (42): RETCODE_UNKNOWN", fastddsjavaTools.retcodeMessage(42));
   }

   @Test
   public void testReturnCodeMessageNamesValueAndName()
   {
      assertEquals("Fast-DDS retcode (0): RETCODE_OK", fastddsjavaTools.retcodeMessage(RETCODE_OK));
      assertEquals("Fast-DDS retcode (10): RETCODE_TIMEOUT", fastddsjavaTools.retcodeMessage(RETCODE_TIMEOUT));
      assertEquals("Fast-DDS retcode (3): RETCODE_BAD_PARAMETER", fastddsjavaTools.retcodeMessage(RETCODE_BAD_PARAMETER));
   }

   @Test
   public void testTakeResultDecoding()
   {
      // Sizes and the two non-error markers are not errors
      for (int result : new int[] {0, 1, 4, 65536, Integer.MAX_VALUE, TAKE_NO_DATA, TAKE_INVALID_SAMPLE, -99})
      {
         assertEquals(RETCODE_OK, fastddsjavaTools.takeResultRetcode(result), Integer.toString(result));
      }

      // Results at or below -TAKE_ERROR_BASE encode -(TAKE_ERROR_BASE + retcode)
      assertEquals(RETCODE_OK, fastddsjavaTools.takeResultRetcode(-TAKE_ERROR_BASE));
      for (Object[] retcode : RETCODES)
      {
         int value = (Integer) retcode[0];
         assertEquals(value, fastddsjavaTools.takeResultRetcode(-(TAKE_ERROR_BASE + value)), (String) retcode[1]);
      }
   }

   @Test
   public void testThrowOnError()
   {
      assertDoesNotThrow(() -> fastddsjavaTools.retcodeThrowOnError(RETCODE_OK));

      for (Object[] retcode : RETCODES)
      {
         int value = (Integer) retcode[0];
         if (value == RETCODE_OK)
            continue;

         fastddsjavaException exception = assertThrows(fastddsjavaException.class, () -> fastddsjavaTools.retcodeThrowOnError(value));
         assertEquals(value, exception.getReturnCode_t());
         assertEquals(fastddsjavaTools.retcodeMessage(value), exception.getMessage());
         assertTrue(exception.getMessage().contains((String) retcode[1]), exception.getMessage());
      }
   }

   @Test
   public void testExceptionMessage()
   {
      fastddsjavaException exception = new fastddsjavaException(RETCODE_PRECONDITION_NOT_MET);
      assertEquals(RETCODE_PRECONDITION_NOT_MET, exception.getReturnCode_t());
      assertEquals("Fast-DDS retcode (4): RETCODE_PRECONDITION_NOT_MET", exception.getMessage());
      assertTrue(exception.toString().contains("RETCODE_PRECONDITION_NOT_MET"), exception.toString());
   }

   @Test
   public void testPrintOnErrorPrintsOnlyErrors()
   {
      PrintStream original = System.err;
      ByteArrayOutputStream captured = new ByteArrayOutputStream();
      try (PrintStream capture = new PrintStream(captured, true, StandardCharsets.UTF_8))
      {
         System.setErr(capture);
         fastddsjavaTools.retcodePrintOnError(RETCODE_OK);
         capture.flush();
         assertFalse(captured.toString(StandardCharsets.UTF_8).contains("Fast-DDS error"), "RETCODE_OK prints nothing");

         fastddsjavaTools.retcodePrintOnError(RETCODE_OUT_OF_RESOURCES);
         capture.flush();
         // Other threads of the test JVM may print to System.err as well; only this call's line is checked
         String printed = captured.toString(StandardCharsets.UTF_8);
         assertTrue(printed.lines().anyMatch("Fast-DDS error: Fast-DDS retcode (5): RETCODE_OUT_OF_RESOURCES"::equals), printed);
         assertEquals(1, printed.lines().filter(line -> line.contains("Fast-DDS error")).count(), printed);
      }
      finally
      {
         System.setErr(original);
      }
   }
}
