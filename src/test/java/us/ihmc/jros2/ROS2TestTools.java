/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Tools for launching a local vanilla ROS 2 installation.
 * Only works on Linux hosts with a ROS 2 distro under {@code /opt/ros}.
 */
public class ROS2TestTools
{
   public static String ROS_DISTRO = System.getenv().getOrDefault("ROS_DISTRO", "humble");

   /**
    * True when running on the Android runtime (including emulators).
    * Note: Android reports {@code os.name=Linux}, so {@code @EnabledOnOs(LINUX)} is not sufficient to exclude it.
    */
   public static boolean isAndroid()
   {
      return System.getProperty("java.vendor", "").toLowerCase(Locale.ROOT).contains("android");
   }

   /** Inverse of {@link #isAndroid()} for {@code @EnabledIf} filters. */
   public static boolean isNotAndroid()
   {
      return !isAndroid();
   }

   /**
    * Whether tests may invoke the host {@code ros2} CLI (requires a Linux ROS install, not Android).
    */
   public static boolean isROS2CLIAvailable()
   {
      if (isAndroid())
      {
         return false;
      }
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      if (!os.contains("linux"))
      {
         return false;
      }
      return Files.isRegularFile(Path.of("/opt/ros", ROS_DISTRO, "setup.bash"));
   }

   /**
    * {@code ros2 topic pub -w/--wait-matching-subscriptions} exists on Humble+.
    */
   private static boolean supportsWaitMatchingSubscriptions()
   {
      return !ROS_DISTRO.equals("foxy") && !ROS_DISTRO.equals("galactic");
   }

   /**
    * Whether jros2 to {@code ros2 topic echo} interop tests are supported.
    * Foxy/Galactic (Ubuntu 20.04) can discover jros2 topics but cannot reliably echo
    * samples from Fast-DDS 3 publishers; ros2 to jros2 interop is still covered there.
    */
   public static boolean supportsROS2PublisherEcho()
   {
      return isROS2CLIAvailable() && !ROS_DISTRO.equals("foxy") && !ROS_DISTRO.equals("galactic");
   }

   /**
    * Adapt {@code ros2 topic pub} options for older CLI versions (e.g. Foxy on Ubuntu 20.04).
    */
   private static String adaptPublishOptions(String options)
   {
      String adapted = options;
      if (!supportsWaitMatchingSubscriptions())
      {
         adapted = adapted.replaceAll("--wait-matching-subscriptions\\s+\\d+", " ");
         adapted = adapted.replaceAll("(?<!\\w)-w\\s+\\d+", " ");
         if (!adapted.contains("keep-alive"))
         {
            // Give matching subscriptions time to discover without -w.
            adapted = adapted + " --keep-alive 5";
         }
      }
      return adapted.trim().replaceAll("\\s+", " ");
   }

   /**
    * Launches {@code ros2 subCommand}
    *
    * @param domainId       ROS_DOMAIN_ID to use
    * @param subCommand     The ros2 subcommand to run
    * @param outputRedirect Redirect for stdout
    * @param errorRedirect  Redirect for stderr
    * @return The process launched
    */
   public static Process launchROS2Process(int domainId, String subCommand, Redirect outputRedirect, Redirect errorRedirect) throws IOException
   {
      String sourceROS2 = " source /opt/ros/" + ROS_DISTRO + "/setup.bash ";
      String ros2Command = " ros2 " + subCommand + " ";

      ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", sourceROS2 + "&&" + ros2Command);
      processBuilder.environment().put("ROS_DOMAIN_ID", String.valueOf(domainId));

      if (outputRedirect != null)
      {
         processBuilder.redirectOutput(outputRedirect);
      }
      if (errorRedirect != null)
      {
         processBuilder.redirectError(errorRedirect);
      }

      return processBuilder.start();
   }

   /**
    * Launches {@code ros2 topic pub options topicName messageType values}
    *
    * @param domainId       ROS_DOMAIN_ID to publish on
    * @param options        E.g. {@code "--once"}
    * @param topicName      Topic name to publish on
    * @param messageType    Message type to publish (e.g. {@code "std_msgs/msg/Bool"})
    * @param values         YAML values representing the message data
    * @param outputRedirect Redirect for stdout
    * @param errorRedirect  Redirect for stderr
    * @return The process launched
    */
   public static Process launchROS2PublishProcess(int domainId,
                                                  String options,
                                                  String topicName,
                                                  String messageType,
                                                  String values,
                                                  Redirect outputRedirect,
                                                  Redirect errorRedirect) throws IOException
   {
      StringJoiner command = new StringJoiner(" ");
      command.add("topic pub").add(adaptPublishOptions(options)).add(topicName).add(messageType).add("\"" + values + "\"");
      return launchROS2Process(domainId, command.toString(), outputRedirect, errorRedirect);
   }

   /**
    * Launched {@code ros2 topic echo --once topicName}
    *
    * @param domainId  ROS_DOMAIN_ID to use
    * @param topicName Topic name to listen on
    * @return The stdout of {@code ros2 topic echo --once topicName}
    */
   public static String ros2EchoOnce(int domainId, String topicName) throws IOException, InterruptedException
   {
      Process echoProcess = launchROS2Process(domainId, "topic echo --once " + topicName, null, null);
      echoProcess.waitFor();

      StringBuilder output = new StringBuilder();
      // Avoid Process#inputReader() (Java 17) so this compiles on Android/ART toolchains too.
      try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(echoProcess.getInputStream(), StandardCharsets.UTF_8)))
      {
         String line;
         while ((line = stdoutReader.readLine()) != null)
         {
            output.append(line).append("\n");
         }
      }

      return output.toString();
   }
}
