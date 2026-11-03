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
package us.ihmc.fastddsjava.natives;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.fastddsjavaTools;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static us.ihmc.fastddsjava.natives.fastddsjava.*;

/**
 * ABI detection of the loaded natives. The committed natives may be ABI v1 (until they are rebuilt), in which case
 * Java must fall back to the v1 functions; the v2 tests run when the suite is started with
 * {@code JROS2_NATIVE_PATH=<dir with v2 natives>}. Set {@code JROS2_EXPECT_NATIVE_SECURITY=true|false} to also assert
 * the DDS-Security support of those natives.
 */
public class NativeAbiTest
{
   @BeforeAll
   public static void loadNatives()
   {
      assertTrue(fastddsjavaNativeLibrary.load(), "native libraries must load");
   }

   @Test
   public void abiVersionIsKnownAndStable()
   {
      int version = abiVersion();
      assertTrue(version == ABI_V1 || version >= ABI_V2, "unexpected ABI version " + version);
      assertEquals(version, abiVersion());
      assertEquals(hasSecurity(), hasSecurity());
   }

   @Test
   public void abiV1NativesFallBackWithoutV2Functions()
   {
      assumeTrue(abiVersion() == ABI_V1, "loaded natives implement ABI v2");

      assertFalse(hasSecurity(), "ABI v1 natives never report DDS-Security");
      ByteBuffer buffer = ByteBuffer.allocateDirect(8);
      // The v2 entry points are absent, which is exactly what abiVersion() detected.
      assertThrows(UnsatisfiedLinkError.class, () -> dataWriterWriteBuffer(0, buffer, 8));
      assertThrows(UnsatisfiedLinkError.class, () -> dataReaderTakeNextToBuffer(0, 0, 0, buffer, 8));
   }

   @Test
   public void abiV2NativesExposeV2Functions()
   {
      assumeTrue(abiVersion() >= ABI_V2, "requires ABI v2 natives");

      ByteBuffer buffer = ByteBuffer.allocateDirect(8);
      assertEquals(RETCODE_BAD_PARAMETER, dataWriterWriteBuffer(0, buffer, 8));
      int result = dataReaderTakeNextToBuffer(0, 0, 0, buffer, 8);
      assertEquals(-(TAKE_ERROR_BASE + RETCODE_BAD_PARAMETER), result);
      assertEquals(RETCODE_BAD_PARAMETER, fastddsjavaTools.takeResultRetcode(result));

      String expectedSecurity = System.getenv("JROS2_EXPECT_NATIVE_SECURITY");
      if (expectedSecurity != null && !expectedSecurity.isEmpty())
         assertEquals(Boolean.parseBoolean(expectedSecurity), hasSecurity(), "nativeHasSecurity");
   }

   @Test
   public void takeResultRetcodeDecodesOnlyErrors()
   {
      assertEquals(RETCODE_OK, fastddsjavaTools.takeResultRetcode(0));
      assertEquals(RETCODE_OK, fastddsjavaTools.takeResultRetcode(1234));
      assertEquals(RETCODE_OK, fastddsjavaTools.takeResultRetcode(TAKE_NO_DATA));
      assertEquals(RETCODE_OK, fastddsjavaTools.takeResultRetcode(TAKE_INVALID_SAMPLE));
      assertEquals(RETCODE_OK, fastddsjavaTools.takeResultRetcode(-TAKE_ERROR_BASE));
      assertEquals(RETCODE_ERROR, fastddsjavaTools.takeResultRetcode(-(TAKE_ERROR_BASE + RETCODE_ERROR)));
      assertEquals(RETCODE_TIMEOUT, fastddsjavaTools.takeResultRetcode(-(TAKE_ERROR_BASE + RETCODE_TIMEOUT)));
      assertEquals("Fast-DDS retcode (3): RETCODE_BAD_PARAMETER", fastddsjavaTools.retcodeMessage(RETCODE_BAD_PARAMETER));
   }

   @Test
   public void nativesAreLoadedFromTheConfiguredLocation()
   {
      assertTrue(fastddsjavaNativeLibrary.load(), "load() is idempotent");
      assertNull(fastddsjavaNativeLibrary.getLoadError());
      Path directory = fastddsjavaNativeLibrary.getLoadedDirectory();
      assertNotNull(directory);

      String property = System.getProperty(fastddsjavaNativeLibrary.NATIVE_PATH_PROPERTY);
      String nativePath = property != null && !property.trim().isEmpty() ? property : System.getenv(fastddsjavaNativeLibrary.NATIVE_PATH_ENV);
      if (nativePath != null && !nativePath.trim().isEmpty())
         assertEquals(Paths.get(nativePath.trim()), directory, "direct mode loads from " + fastddsjavaNativeLibrary.NATIVE_PATH_ENV);
      else
         assertTrue(directory.getFileName().toString().contains("-"), "extraction cache directory <platform>-<hash>: " + directory);
   }

   /**
    * abiVersion() and hasSecurity() called before the natives are loaded (here: on a copy of the class in a class loader
    * that never loads them) answer ABI v1 without DDS-Security instead of failing. That this early answer is not cached, so
    * that natives loaded later are still detected with their real ABI and DDS-Security support, is checked through the
    * public API in a fresh JVM by fastddsjavaNativeLibraryProcessTest.abiQueriedBeforeLoadingIsNotPinnedToV1.
    */
   @Test
   public void abiQueriesBeforeTheNativesAreLoadedAnswerV1WithoutSecurity() throws Throwable
   {
      URL location = fastddsjava.class.getProtectionDomain().getCodeSource().getLocation();
      try (URLClassLoader isolated = new URLClassLoader(new URL[] {location}, ClassLoader.getPlatformClassLoader()))
      {
         Class<?> notLoaded = Class.forName(fastddsjava.class.getName(), true, isolated);
         assertNotSame(fastddsjava.class, notLoaded);
         assertThrows(UnsatisfiedLinkError.class, () -> invokeStatic(notLoaded, "deleteSampleInfo", 0L), "no natives in this class loader");

         assertEquals(ABI_V1, invokeStatic(notLoaded, "abiVersion"));
         assertEquals(Boolean.FALSE, invokeStatic(notLoaded, "hasSecurity"));
      }
   }

   private static Object invokeStatic(Class<?> type, String name, Object... arguments) throws Throwable
   {
      for (java.lang.reflect.Method method : type.getMethods())
      {
         if (method.getName().equals(name) && method.getParameterCount() == arguments.length)
         {
            try
            {
               return method.invoke(null, arguments);
            }
            catch (java.lang.reflect.InvocationTargetException e)
            {
               throw e.getCause();
            }
         }
      }
      throw new NoSuchMethodException(name);
   }
}
