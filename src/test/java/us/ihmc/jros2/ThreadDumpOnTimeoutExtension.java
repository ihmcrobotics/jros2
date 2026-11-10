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

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

import java.util.concurrent.TimeoutException;

/**
 * Prints the stack of every thread, with the locks each one holds and waits for, when a test exceeds its JUnit timeout:
 * JUnit's own report shows only the test thread, and a hang on a CI runner cannot be inspected afterward. Registered for
 * every test through the service file in src/test/resources (JUnit extension autodetection).
 */
public class ThreadDumpOnTimeoutExtension implements TestExecutionExceptionHandler
{
   @Override
   public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable
   {
      for (Throwable cause = throwable; cause != null; cause = cause.getCause())
      {
         if (cause instanceof TimeoutException)
         {
            System.err.println("Thread dump after the timeout of " + context.getRequiredTestClass().getSimpleName() + "." + context.getDisplayName() + ":\n"
                               + PubSubTestTools.threadDump());
            break;
         }
      }
      throw throwable;
   }
}
