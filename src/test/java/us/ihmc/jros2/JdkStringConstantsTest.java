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

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.fastddsjavaTestTools;
import us.ihmc.jros2.allocation.ClassFileStrings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JdkStringConstants} interns every string constant of the JDK classes on jros2's hot paths, so that their first C2
 * compilation does not create strings on a hot thread. Checked in child JVMs with {@code -Xshare:off}, where the JDK's
 * constants are not in the string table from the start (as with ZGC, ParallelGC or large heaps): without the preparation
 * many are missing (control), after it none is.
 */
public class JdkStringConstantsTest
{
   /** Child JVM: prints how many string constants of {@link JdkStringConstants#CLASSES} are not in the string table. */
   public static final class Child
   {
      public static void main(String[] args) throws IOException
      {
         if (args.length > 0 && args[0].equals("prepare"))
            JdkStringConstants.prepare();
         int classes = 0;
         int constants = 0;
         List<String> missing = new ArrayList<>();
         for (String className : JdkStringConstants.CLASSES)
         {
            try (InputStream classFile = Object.class.getResourceAsStream("/" + className.replace('.', '/') + ".class"))
            {
               if (classFile == null)
                  continue;
               classes++;
               for (String constant : ClassFileStrings.parse(classFile.readAllBytes()))
               {
                  constants++;
                  // intern() returns its argument only if the string table did not have that text yet
                  String copy = new String(constant.toCharArray());
                  if (copy.intern() == copy)
                     missing.add(className + ": " + constant);
               }
            }
         }
         System.out.println("CLASSES " + classes + " CONSTANTS " + constants + " MISSING " + missing.size() + " " + missing);
      }
   }

   private static String runChild(String mode) throws IOException, InterruptedException
   {
      List<String> command = new ArrayList<>();
      command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
      command.add("-Xshare:off");
      command.addAll(fastddsjavaTestTools.coverageAgentArguments());
      command.add("-cp");
      command.add(System.getProperty("java.class.path"));
      command.add(Child.class.getName());
      command.add(mode);
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      byte[] output;
      try (InputStream in = process.getInputStream())
      {
         output = in.readAllBytes();
      }
      assertTrue(process.waitFor(60, TimeUnit.SECONDS));
      String text = new String(output, StandardCharsets.UTF_8);
      assertEquals(0, process.exitValue(), text);
      return text;
   }

   private static int count(String text, String key)
   {
      String[] words = text.substring(text.indexOf("CLASSES ")).split(" ");
      for (int i = 0; i < words.length - 1; ++i)
      {
         if (words[i].equals(key))
            return Integer.parseInt(words[i + 1]);
      }
      throw new AssertionError(key + " not in " + text);
   }

   @Test
   public void testPreparationInternsEveryConstant() throws IOException, InterruptedException
   {
      String control = runChild("control");
      String prepared = runChild("prepare");
      // Every listed class exists on this JDK and has string constants
      assertEquals(JdkStringConstants.CLASSES.length, count(prepared, "CLASSES"), prepared);
      assertTrue(count(prepared, "CONSTANTS") > 100, prepared);
      // Without class data sharing many are missing from the string table (the check is meaningful), after prepare() none
      assertTrue(count(control, "MISSING") > 20, control);
      assertEquals(0, count(prepared, "MISSING"), prepared);
   }

   @Test
   public void testInThisJvm()
   {
      JdkStringConstants.prepare();
      int count = JdkStringConstants.getInternedConstantCount();
      assertTrue(count > 100, "interned " + count);
      JdkStringConstants.prepare();
      assertEquals(count, JdkStringConstants.getInternedConstantCount(), "prepare() runs once");
   }
}
