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
package us.ihmc.jros2.allocation.messages;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Runs the tests of allocation test classes in this JVM WITHOUT the JUnit engine: a JVM that has run nothing but this
 * class before the first measured window, like a small application whose control loop starts right after setup.
 * {@link HotSpotAllocationTest} starts it without the java-allocation-instrumenter agent, so the gates check HotSpot's
 * allocated-bytes counter of the thread ({@link ThreadAllocatedBytesGate}).
 * <p>
 * The JUnit engine is a large program: its discovery reads jars and runs much of the JDK (zip files, NIO buffers,
 * reflection) until it is compiled, before the first test runs. What the JVM does once per JDK class at the first C2
 * compilation of one of its methods (resolving the string literals of the class, loading the classes of the method's
 * signature, which creates their {@code java.lang.Class} objects on the requesting thread) has then already happened, and
 * a JIT transition of a JDK method on the message path would not be seen in the measured windows. Here it is.
 * </p>
 * <p>
 * Supports what the allocation test classes use: a public no-argument constructor, {@link BeforeEach} and
 * {@link AfterEach} methods, {@link Test} methods and {@link TestFactory} methods returning a collection of
 * {@link DynamicTest}s; test methods run in the order of their names. Prints the failures and the same last line as
 * {@link HotSpotAllocationProbe}; exits with 0 only if every test passed.
 * </p>
 */
public final class MinimalJvmAllocationProbe
{
   private static int found;
   private static int failed;
   private static final StringWriter failures = new StringWriter();

   private MinimalJvmAllocationProbe()
   {
   }

   public static void main(String[] testClassNames) throws ReflectiveOperationException
   {
      for (String testClassName : testClassNames)
         runClass(Class.forName(testClassName));

      PrintWriter out = new PrintWriter(System.out, true);
      out.print(failures);
      out.println("PROBE agent=" + SingleThreadAllocationGate.Text.isAgentLoaded() + " found=" + found + " succeeded=" + (found - failed) + " failed=" + failed
                  + " skipped=0 archivedHeap=" + HotSpotAllocationProbe.isArchivedHeapUsable() + " vm=[" + System.getProperty("java.vm.info") + "]");
      out.flush();
      System.exit(failed == 0 && found > 0 ? 0 : 1);
   }

   @SuppressWarnings("unchecked")
   private static void runClass(Class<?> testClass) throws ReflectiveOperationException
   {
      List<Method> before = new ArrayList<>();
      List<Method> after = new ArrayList<>();
      List<Method> tests = new ArrayList<>();
      Method[] methods = testClass.getMethods();
      Arrays.sort(methods, Comparator.comparing(Method::getName));
      for (Method method : methods)
      {
         if (method.isAnnotationPresent(BeforeEach.class))
            before.add(method);
         if (method.isAnnotationPresent(AfterEach.class))
            after.add(method);
         if (method.isAnnotationPresent(Test.class) || method.isAnnotationPresent(TestFactory.class))
            tests.add(method);
      }

      for (Method test : tests)
      {
         Object instance = testClass.getDeclaredConstructor().newInstance();
         String name = testClass.getSimpleName() + "." + test.getName();
         try
         {
            for (Method method : before)
               method.invoke(instance);
            if (test.isAnnotationPresent(TestFactory.class))
            {
               for (DynamicTest dynamicTest : (Collection<DynamicTest>) test.invoke(instance))
               {
                  found++;
                  try
                  {
                     dynamicTest.getExecutable().execute();
                  }
                  catch (Throwable e)
                  {
                     fail(name + "[" + dynamicTest.getDisplayName() + "]", e);
                  }
               }
            }
            else
            {
               found++;
               test.invoke(instance);
            }
         }
         catch (InvocationTargetException e)
         {
            fail(name, e.getCause());
         }
         finally
         {
            for (Method method : after)
               method.invoke(instance);
         }
      }
   }

   private static void fail(String name, Throwable e)
   {
      failed++;
      PrintWriter out = new PrintWriter(failures);
      out.println("FAILED " + name + ": " + e);
      StackTraceElement[] trace = e.getStackTrace();
      for (int i = 0; i < Math.min(trace.length, 8); i++)
         out.println("    at " + trace[i]);
      out.flush();
   }
}
