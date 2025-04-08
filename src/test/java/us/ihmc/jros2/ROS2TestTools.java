package us.ihmc.jros2;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.StringJoiner;

public class ROS2TestTools
{
   public static String ROS_DISTRO = System.getProperty("ROS_DISTRO", "humble");

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
         processBuilder.redirectOutput(outputRedirect);
      if (errorRedirect != null)
         processBuilder.redirectError(errorRedirect);

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
      command.add("topic pub").add(options).add(topicName).add(messageType).add("\"" + values + "\"");
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
      try (BufferedReader stdoutReader = echoProcess.inputReader())
      {
         String line;
         while ((line = stdoutReader.readLine()) != null)
            output.append(line).append("\n");
      }

      return output.toString();
   }
}
