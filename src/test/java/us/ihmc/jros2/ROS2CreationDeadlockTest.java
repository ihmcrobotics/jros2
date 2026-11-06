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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import us.ihmc.fastddsjava.fastddsjavaTestTools;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the cross-thread deadlock between a subscription callback that creates DDS entities on another
 * participant and a thread that is creating an entity which Fast-DDS makes wait for that callback. Both used to wait for
 * each other forever (the creator holds the global profile lock while Fast-DDS blocks it), measured with Fast-DDS 3.6.2
 * with intraprocess delivery OFF ({@code same_participant}, {@code new_node}) and FULL (all cases, including a creator on
 * a third participant whose endpoint matches the callback's subscription; jros2 uses USER_DATA_ONLY, where discovery does
 * not run inside the writers' threads). Each case runs in its own JVM
 * ({@link ROS2CreationDeadlockChild}) so that a regression cannot hang the test suite.
 */
public class ROS2CreationDeadlockTest
{
   private static String runChild(String testCase, boolean intraprocess) throws Exception
   {
      String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
      List<String> command = new ArrayList<>();
      command.add(java);
      command.addAll(fastddsjavaTestTools.coverageAgentArguments());
      command.addAll(List.of("-cp", System.getProperty("java.class.path"), ROS2CreationDeadlockChild.class.getName(), testCase));
      ProcessBuilder builder = new ProcessBuilder(command);
      builder.environment().put("FASTDDS_INTRAPROCESS_DELIVERY", Boolean.toString(intraprocess));
      builder.redirectErrorStream(true);
      File output = File.createTempFile("jros2-deadlock-child", ".txt");
      try
      {
         builder.redirectOutput(output);
         Process process = builder.start();
         if (!process.waitFor(60, TimeUnit.SECONDS))
         {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
         }
         return new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8);
      }
      finally
      {
         Files.deleteIfExists(output.toPath());
      }
   }

   private static void check(String testCase, boolean intraprocess, boolean mustBeRejected) throws Exception
   {
      String output = runChild(testCase, intraprocess);
      String context = "case " + testCase + ", intraprocess " + intraprocess + ":\n" + output;
      for (String line : output.split("\n"))
      {
         if (line.startsWith("RESULT"))
         {
            System.out.println("intraprocess=" + intraprocess + " " + line);
         }
      }
      Assumptions.assumeFalse(output.contains("RESULT SKIP"), "ROS2Subscription does not mark its callbacks with CallbackContext");
      assertFalse(output.contains("RESULT DEADLOCK"), context);
      assertTrue(output.contains("RESULT OK"), context);
      assertTrue(output.contains("callbackDone=true"), context);
      assertTrue(output.contains("CLOSED"), "the nodes must close normally afterward; " + context);
      if (mustBeRejected)
      {
         assertTrue(output.contains("creatorSeen=true"), context);
         assertTrue(output.contains("callback=rejected"), context);
      }
      else
      {
         assertTrue(output.contains("callback=rejected") || output.contains("callback=created"), context);
      }
   }

   @Test
   @Timeout(300)
   public void testCallbackCreatingElsewhereWhileTheParticipantIsBeingExtended() throws Exception
   {
      Assumptions.assumeTrue(ROS2TestTools.isNotAndroid());
      // Without intraprocess delivery the creator on the callback's participant is always blocked by the callback
      check("same_participant", false, true);
      check("new_node", false, true);
      check("same_participant", true, false);
      check("new_node", true, false);
   }

   @Test
   @Timeout(300)
   public void testCallbackCreatingElsewhereWhileAMatchingEndpointIsCreated() throws Exception
   {
      Assumptions.assumeTrue(ROS2TestTools.isNotAndroid());
      check("matching_endpoint", false, false);
      check("matching_endpoint", true, false);
   }
}
