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
package us.ihmc.jros2;

/**
 * Helpers for tests that act from inside subscription callbacks.
 */
final class ROS2CallbackTestTools
{
   private ROS2CallbackTestTools()
   {
   }

   /**
    * Runs {@code action} as code inside a Fast-DDS callback: {@link ROS2Subscription} marks its callbacks with
    * {@link CallbackContext}; if the running subscription implementation does not (yet), the mark is added here so the
    * node-level behavior can still be tested.
    */
   static void asCallback(Runnable action)
   {
      boolean mark = !CallbackContext.isInCallback();
      if (mark)
      {
         CallbackContext.enter();
      }
      try
      {
         action.run();
      }
      finally
      {
         if (mark)
         {
            CallbackContext.exit();
         }
      }
   }
}
