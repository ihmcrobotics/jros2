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
package us.ihmc.jros2;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.natives.fastddsjava;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that the AAR's bundled Fast-DDS / JNI libraries load and JNI resolves on device.
 */
@RunWith(AndroidJUnit4.class)
public class NativeLibraryLoadTest
{
   @Test
   public void nativeLibrariesLoadAndJniResolves()
   {
      // Load in dependency order (matches fastddsjavaNativeLibrary Android path).
      System.loadLibrary("log");
      System.loadLibrary("c++_shared");
      System.loadLibrary("fastcdr");
      System.loadLibrary("fastdds");
      System.loadLibrary("jnifastddsjava");

      long type = fastddsjava.createTopicDataWrapperType("android_native_load_test", fastddsjava.CDR_LE);
      assertNotEquals(0L, type);
      assertEquals("android_native_load_test", fastddsjava.topicDataWrapperTypeGetName(type));
   }

   @Test
   public void jros2LoaderUsesAndroidPath()
   {
      assertTrue("fastddsjavaNativeLibrary.load() failed; see logcat for the UnsatisfiedLinkError", fastddsjavaNativeLibrary.load());
   }

   /**
    * Reports the native ABI version and DDS-Security support of the bundled libraries. The checks only become strict when
    * the instrumentation arguments {@code expectedAbiVersion} / {@code expectSecurity} are given, so the same test runs
    * against older (ABI v1, no security) and rebuilt natives.
    */
   @Test
   public void nativeAbiVersionAndSecurityAreReported()
   {
      assertTrue(fastddsjavaNativeLibrary.load());

      int abiVersion = fastddsjava.abiVersion();
      boolean security = fastddsjava.hasSecurity();
      Log.i(AndroidTestSupport.TAG, "fastddsjava.abiVersion()=" + abiVersion + " hasSecurity()=" + security);
      assertTrue("abiVersion() must be >= 1, was " + abiVersion, abiVersion >= fastddsjava.ABI_V1);

      String expectedAbiVersion = AndroidTestSupport.argument("expectedAbiVersion");
      if (expectedAbiVersion != null)
      {
         assertEquals("native ABI version", Integer.parseInt(expectedAbiVersion), abiVersion);
      }

      if ("true".equalsIgnoreCase(AndroidTestSupport.argument("expectSecurity")))
      {
         assertTrue("natives were built without DDS-Security (SECURITY=OFF)", security);
      }
   }
}
