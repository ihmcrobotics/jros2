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
import android.net.wifi.WifiManager;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Android-specific helpers for jros2.
 * <p>
 * <b>Multicast discovery on Wi-Fi.</b> ROS 2 / DDS participants find each other with UDP multicast (SPDP, group
 * 239.255.0.1). Most Android Wi-Fi drivers drop incoming multicast packets unless an app holds a
 * {@link WifiManager.MulticastLock}, so without one a jros2 node on a phone usually cannot discover other ROS 2 nodes
 * (and they discover it only one way). Hold the lock for as long as nodes should be discoverable:
 * <pre>{@code
 * try (ROS2Android.MulticastLockHandle multicast = ROS2Android.acquireMulticastLock(context);
 *      ROS2Node node = new ROS2Node("phone_node"))
 * {
 *    ...
 * }
 * }</pre>
 * The lock increases battery use, so release it (close the handle) when DDS traffic is no longer needed, for example
 * in {@code onStop()}. The jros2-android manifest already declares the required
 * {@code android.permission.CHANGE_WIFI_MULTICAST_STATE} (a normal permission granted at install time).
 * <p>
 * Alternatives that avoid multicast entirely are static peers ({@code ROS_STATIC_PEERS}) or a Fast-DDS discovery server.
 */
public final class ROS2Android
{
   /** Lock tag used by {@link #acquireMulticastLock(Context)}; visible in {@code adb shell dumpsys wifi}. */
   public static final String DEFAULT_MULTICAST_LOCK_TAG = "jros2";

   private ROS2Android()
   {
   }

   /**
    * Acquires a Wi-Fi multicast lock with the tag {@value #DEFAULT_MULTICAST_LOCK_TAG}.
    *
    * @param context any context; its application context is used to look up the {@link WifiManager}.
    * @return a handle that releases the lock when closed. On devices without Wi-Fi the handle holds nothing.
    * @see #acquireMulticastLock(Context, String)
    */
   public static MulticastLockHandle acquireMulticastLock(Context context)
   {
      return acquireMulticastLock(context, DEFAULT_MULTICAST_LOCK_TAG);
   }

   /**
    * Acquires a Wi-Fi multicast lock so that the Wi-Fi driver delivers multicast packets (DDS discovery) to this device.
    * Each call creates its own non reference counted lock; closing the returned handle releases exactly that lock and is
    * idempotent.
    *
    * @param context any context; its application context is used to look up the {@link WifiManager}.
    * @param tag     tag of the lock, shown by {@code adb shell dumpsys wifi}.
    * @return a handle that releases the lock when closed. On devices without Wi-Fi (no {@link WifiManager}, e.g. some
    *       emulators, TVs or Ethernet-only boards) the handle holds nothing and {@link MulticastLockHandle#isWifiAvailable()}
    *       returns false.
    * @throws NullPointerException if {@code context} or {@code tag} is null.
    * @throws SecurityException    if the app's merged manifest lacks {@code android.permission.CHANGE_WIFI_MULTICAST_STATE}.
    */
   public static MulticastLockHandle acquireMulticastLock(Context context, String tag)
   {
      Objects.requireNonNull(context, "context");
      Objects.requireNonNull(tag, "tag");

      Context applicationContext = context.getApplicationContext();
      if (applicationContext == null)
      {
         applicationContext = context;
      }

      WifiManager wifiManager = (WifiManager) applicationContext.getSystemService(Context.WIFI_SERVICE);
      WifiManager.MulticastLock lock = null;
      if (wifiManager != null)
      {
         lock = wifiManager.createMulticastLock(tag);
         lock.setReferenceCounted(false);
         lock.acquire();
      }

      return new MulticastLockHandle(lock);
   }

   /**
    * Owns one acquired {@link WifiManager.MulticastLock}. Closing it releases the lock; further closes do nothing.
    */
   public static final class MulticastLockHandle implements AutoCloseable
   {
      private final WifiManager.MulticastLock lock;
      private final AtomicBoolean closed = new AtomicBoolean(false);

      MulticastLockHandle(WifiManager.MulticastLock lock)
      {
         this.lock = lock;
      }

      /**
       * @return true if the device has a {@link WifiManager} and a lock was acquired by this handle.
       */
      public boolean isWifiAvailable()
      {
         return lock != null;
      }

      /**
       * @return true while this handle has not been closed and the underlying lock is held.
       */
      public boolean isHeld()
      {
         return lock != null && !closed.get() && lock.isHeld();
      }

      /**
       * @return true once {@link #close()} has been called.
       */
      public boolean isClosed()
      {
         return closed.get();
      }

      /**
       * Releases the multicast lock. Safe to call more than once and from any thread.
       */
      @Override
      public void close()
      {
         if (closed.compareAndSet(false, true) && lock != null && lock.isHeld())
         {
            lock.release();
         }
      }
   }
}
