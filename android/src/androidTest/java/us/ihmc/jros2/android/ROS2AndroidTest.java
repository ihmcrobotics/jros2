/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
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
package us.ihmc.jros2.android;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ROS2AndroidTest
{
   private static Context context()
   {
      return InstrumentationRegistry.getInstrumentation().getTargetContext();
   }

   @Test
   public void manifestDeclaresMulticastPermission()
   {
      assertEquals("CHANGE_WIFI_MULTICAST_STATE must be merged from the jros2-android manifest", PackageManager.PERMISSION_GRANTED,
                   context().checkSelfPermission(android.Manifest.permission.CHANGE_WIFI_MULTICAST_STATE));
   }

   @Test
   public void acquireAndReleaseMulticastLock()
   {
      boolean hasWifi = context().getApplicationContext().getSystemService(Context.WIFI_SERVICE) != null;

      ROS2Android.MulticastLockHandle handle = ROS2Android.acquireMulticastLock(context());
      assertEquals(hasWifi, handle.isWifiAvailable());
      assertEquals(hasWifi, handle.isHeld());
      assertFalse(handle.isClosed());

      handle.close();
      assertTrue(handle.isClosed());
      assertFalse(handle.isHeld());

      handle.close(); // idempotent
      assertFalse(handle.isHeld());
   }

   @Test
   public void handlesAreIndependent()
   {
      try (ROS2Android.MulticastLockHandle first = ROS2Android.acquireMulticastLock(context(), "jros2-test-1");
           ROS2Android.MulticastLockHandle second = ROS2Android.acquireMulticastLock(context(), "jros2-test-2"))
      {
         if (!first.isWifiAvailable())
         {
            return; // nothing to hold on devices without Wi-Fi
         }
         first.close();
         assertFalse(first.isHeld());
         assertTrue("closing one handle must not release another", second.isHeld());
      }
   }

   @Test
   public void nullArgumentsAreRejected()
   {
      assertThrows(NullPointerException.class, () -> ROS2Android.acquireMulticastLock(null));
      assertThrows(NullPointerException.class, () -> ROS2Android.acquireMulticastLock(context(), null));
   }
}
