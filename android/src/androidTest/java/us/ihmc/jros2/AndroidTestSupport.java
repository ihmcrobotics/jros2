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
package us.ihmc.jros2;

import android.os.Bundle;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * Shared helpers for the on-device jros2 tests.
 * <p>
 * Instrumentation arguments (pass with {@code -Pandroid.testInstrumentationRunnerArguments.<name>=<value>}):
 * <ul>
 *    <li>{@code rosDomainId}: DDS domain used by the tests (default 42; the emulator network is isolated anyway).</li>
 *    <li>{@code expectedPageSize}: if set, {@link PageSize16KbTest} asserts the device page size (CI: 16384 on ps16k images).</li>
 *    <li>{@code expectedAbiVersion}: if set, {@link NativeLibraryLoadTest} asserts {@code fastddsjava.abiVersion()}.</li>
 *    <li>{@code expectSecurity}: if "true", {@link NativeLibraryLoadTest} asserts that the natives were built with DDS-Security.</li>
 * </ul>
 */
final class AndroidTestSupport
{
   static final String TAG = "jros2-test";
   static final long DISCOVERY_TIMEOUT_MS = 20_000;
   static final long DELIVERY_TIMEOUT_MS = 20_000;

   private static final Random RANDOM = new Random();

   private AndroidTestSupport()
   {
   }

   static Bundle arguments()
   {
      return InstrumentationRegistry.getArguments();
   }

   static String argument(String name)
   {
      return arguments().getString(name);
   }

   static int domainId()
   {
      String value = argument("rosDomainId");
      return value == null ? 42 : Integer.parseInt(value);
   }

   /**
    * @return a topic name that is unique per call, so tests (and re-runs on the same emulator) never see each other's samples.
    */
   static String uniqueTopicName(String base)
   {
      return "/jros2_android_test/" + base + "_" + Integer.toHexString(RANDOM.nextInt() & 0x7fffffff);
   }

   static String uniqueNodeName(String base)
   {
      return base + "_" + Integer.toHexString(RANDOM.nextInt() & 0xffff);
   }

   /**
    * Polls {@code condition} every 10 ms until it is true or {@code timeoutMs} passed.
    */
   static boolean waitUntil(BooleanSupplier condition, long timeoutMs)
   {
      long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
      while (!condition.getAsBoolean())
      {
         if (System.nanoTime() > deadline)
         {
            return condition.getAsBoolean();
         }
         try
         {
            Thread.sleep(10);
         }
         catch (InterruptedException e)
         {
            Thread.currentThread().interrupt();
            return condition.getAsBoolean();
         }
      }
      return true;
   }

   /**
    * Publishes {@code message} every {@code periodMs} until {@code received} is true or the delivery timeout passes.
    * Repeating makes the tests independent of the order in which the two endpoints finish matching.
    */
   static <T extends ROS2Message<T>> boolean publishUntil(ROS2Publisher<T> publisher, T message, BooleanSupplier received, long periodMs)
   {
      long deadline = System.nanoTime() + DELIVERY_TIMEOUT_MS * 1_000_000L;
      while (!received.getAsBoolean() && System.nanoTime() < deadline)
      {
         publisher.publish(message);
         waitUntil(received, periodMs);
      }
      return received.getAsBoolean();
   }

   static void closeQuietly(List<? extends AutoCloseable> closeables)
   {
      List<AutoCloseable> reversed = new ArrayList<>(closeables);
      for (int i = reversed.size() - 1; i >= 0; --i)
      {
         try
         {
            reversed.get(i).close();
         }
         catch (Throwable t)
         {
            Log.w(TAG, "close failed", t);
         }
      }
   }
}
