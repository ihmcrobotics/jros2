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
    * Whether {@code ros2 topic pub --once/--times} waits for a matching subscription before it publishes (Humble and newer: an
    * implicit {@code -w 1}). Foxy and Galactic publish right after a fixed 0.5 s discovery spin. Discovering the jros2
    * participant can take longer than that: Galactic's default middleware, Cyclone DDS, needs about 0.4 to 1.1 s to match a
    * Fast-DDS subscription (measured against jros2 and against Galactic's own rmw_fastrtps). A burst published before the CLI
    * publisher has matched never reaches a VOLATILE subscription.
    */
   public static boolean cliPublishWaitsForSubscription()
   {
      return supportsWaitMatchingSubscriptions();
   }

   /**
    * Prepares a burst published by the ROS 2 CLI ({@code --times N} at a high rate) so that every sample reaches the jros2
    * subscription although the CLI may publish before the two have matched. Where the CLI cannot wait for the subscription
    * ({@link #cliPublishWaitsForSubscription()}), the CLI publisher and {@code subscriptionQos} are made TRANSIENT_LOCAL: the
    * samples written before the match are then delivered when it happens (the CLI publisher stays alive after the burst, see
    * {@link #launchROS2PublishProcess}). Elsewhere nothing changes.
    *
    * @param cliOptions      the {@code ros2 topic pub} options of the burst; they must keep all samples ({@code --qos-depth})
    * @param subscriptionQos the QoS of the jros2 subscription, changed in place; call this before creating the subscription
    * @return the {@code ros2 topic pub} options to use
    */
   public static String prepareCLIBurst(String cliOptions, ROS2QoSProfile subscriptionQos)
   {
      if (cliPublishWaitsForSubscription())
      {
         return cliOptions;
      }
      subscriptionQos.durability(ROS2QoSProfile.Durability.TRANSIENT_LOCAL);
      return cliOptions + " --qos-durability transient_local";
   }

   /**
    * Whether jros2 to {@code ros2 topic echo} interop tests are supported: wherever a ROS 2 CLI is available. Foxy and Galactic
    * receive jros2 samples as well; their {@code topic echo} only lacks {@code --once} and the matching of the publisher's
    * QoS, which {@link #ros2EchoOnce} makes up for.
    */
   public static boolean supportsROS2PublisherEcho()
   {
      return isROS2CLIAvailable();
   }

   /**
    * {@code ros2 topic echo --once}, and the adaptation of the subscription QoS to the publishers (needed to receive a
    * TRANSIENT_LOCAL sample published before the CLI started), exist since Humble.
    */
   private static boolean supportsEchoOnce()
   {
      return !ROS_DISTRO.equals("foxy") && !ROS_DISTRO.equals("galactic");
   }

   /**
    * {@code ros2 topic info} accepts {@code --no-daemon} and {@code --spin-time} since Galactic. Foxy's {@code topic info} has
    * neither: it always asks the ROS 2 daemon (starting one on first use) and rejects both options.
    */
   public static boolean supportsTopicInfoSpinTime()
   {
      return !ROS_DISTRO.equals("foxy");
   }

   /**
    * Runs {@code ros2 topic info -v topicName} after {@code spinTimeSeconds} of discovery: {@code --no-daemon --spin-time} where
    * the CLI has these options. On Foxy the daemon is started first and queried after the same discovery time; the query is
    * repeated (until {@code timeoutSeconds}) only while the daemon has not discovered any endpoint of the topic yet.
    *
    * @return everything the last {@code ros2 topic info} printed
    */
   public static String ros2TopicInfoVerbose(int domainId, String topicName, int spinTimeSeconds, long timeoutSeconds)
         throws IOException, InterruptedException
   {
      if (supportsTopicInfoSpinTime())
      {
         return runROS2Command(domainId, "topic info -v " + topicName + " --no-daemon --spin-time " + spinTimeSeconds, timeoutSeconds);
      }

      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(timeoutSeconds);
      runROS2Command(domainId, "daemon start", timeoutSeconds);
      Thread.sleep(spinTimeSeconds * 1000L);
      String output = runROS2Command(domainId, "topic info -v " + topicName, timeoutSeconds);
      while (!output.contains("Endpoint type:") && System.nanoTime() - deadline < 0)
      {
         Thread.sleep(500);
         output = runROS2Command(domainId, "topic info -v " + topicName, timeoutSeconds);
      }
      return output;
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
    * Runs {@code ros2 subCommand} to completion and returns its combined stdout and stderr.
    *
    * @param domainId       ROS_DOMAIN_ID to use
    * @param subCommand     the ros2 subcommand, e.g. {@code "node list --no-daemon --spin-time 3"}
    * @param timeoutSeconds the process is killed after this long
    * @return everything the command printed
    */
   public static String runROS2Command(int domainId, String subCommand, long timeoutSeconds) throws IOException, InterruptedException
   {
      String sourceROS2 = " source /opt/ros/" + ROS_DISTRO + "/setup.bash ";
      ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", sourceROS2 + "&& ros2 " + subCommand);
      processBuilder.environment().put("ROS_DOMAIN_ID", String.valueOf(domainId));
      processBuilder.redirectErrorStream(true);
      Process process = processBuilder.start();

      StringBuilder output = new StringBuilder();
      Thread reader = new Thread(() ->
      {
         try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
         {
            String line;
            while ((line = stdoutReader.readLine()) != null)
            {
               synchronized (output)
               {
                  output.append(line).append("\n");
               }
            }
         }
         catch (IOException ignored)
         {
            // The process was killed
         }
      }, "ros2-output-reader");
      reader.setDaemon(true);
      reader.start();

      if (!process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS))
      {
         process.destroyForcibly();
      }
      reader.join(5000);

      synchronized (output)
      {
         return output.toString();
      }
   }

   /**
    * Launched {@code ros2 topic echo --once topicName}. On Foxy and Galactic, whose {@code topic echo} has no {@code --once}
    * and subscribes with fixed QoS, the CLI subscribes RELIABLE and TRANSIENT_LOCAL (like the newer CLIs do for such a
    * publisher) and is stopped after the first message.
    *
    * @param domainId  ROS_DOMAIN_ID to use
    * @param topicName Topic name to listen on
    * @return The stdout of {@code ros2 topic echo --once topicName}
    */
   public static String ros2EchoOnce(int domainId, String topicName) throws IOException, InterruptedException
   {
      // ros2 topic echo exits when it cannot determine the topic type, which happens when the CLI (or its daemon) has not
      // discovered the publisher yet; try again in that case only.
      String output = "";
      for (int attempt = 0; attempt < 3; ++attempt)
      {
         output = ros2EchoOnceAttempt(domainId, topicName);
         boolean notDiscoveredYet = output.contains("does not appear to be published yet") || output.contains("Could not determine the type");
         if (!notDiscoveredYet || output.contains("---"))
         {
            break;
         }
         Thread.sleep(500);
      }
      return output;
   }

   private static String ros2EchoOnceAttempt(int domainId, String topicName) throws IOException, InterruptedException
   {
      if (!supportsEchoOnce())
      {
         return ros2EchoFirstMessage(domainId, topicName);
      }

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

   /**
    * Foxy/Galactic: {@code ros2 topic echo} subscribing RELIABLE and TRANSIENT_LOCAL, stopped after the first message (or after
    * 15 s); returns stdout and stderr, which carries the "could not determine the type" error.
    */
   private static String ros2EchoFirstMessage(int domainId, String topicName) throws IOException, InterruptedException
   {
      ProcessBuilder processBuilder = new ProcessBuilder("bash",
                                                         "-c",
                                                         "source /opt/ros/" + ROS_DISTRO + "/setup.bash && exec ros2 topic echo --qos-reliability reliable "
                                                         + "--qos-durability transient_local " + topicName);
      processBuilder.environment().put("ROS_DOMAIN_ID", String.valueOf(domainId));
      // Python block-buffers stdout into a pipe; the CLI is stopped rather than exiting, so it must not buffer
      processBuilder.environment().put("PYTHONUNBUFFERED", "1");
      processBuilder.redirectErrorStream(true);
      Process echoProcess = processBuilder.start();

      Thread watchdog = new Thread(() ->
      {
         try
         {
            if (!echoProcess.waitFor(15, java.util.concurrent.TimeUnit.SECONDS))
            {
               echoProcess.destroy();
            }
         }
         catch (InterruptedException e)
         {
            echoProcess.destroy();
         }
      }, "ros2-echo-watchdog");
      watchdog.setDaemon(true);
      watchdog.start();

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(echoProcess.getInputStream(), StandardCharsets.UTF_8)))
      {
         String line;
         while ((line = reader.readLine()) != null)
         {
            output.append(line).append("\n");
            if (line.equals("---"))
            {
               // The first message is complete
               break;
            }
         }
      }
      catch (IOException e)
      {
         // The watchdog stopped the CLI (Process#destroy closes its streams)
      }
      echoProcess.destroy();
      echoProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
      watchdog.interrupt();
      return output.toString();
   }
}
