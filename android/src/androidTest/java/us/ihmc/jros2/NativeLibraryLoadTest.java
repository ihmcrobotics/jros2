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

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import us.ihmc.fastddsjava.natives.fastddsjava;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

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
}
