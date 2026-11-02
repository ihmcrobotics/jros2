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
package us.ihmc.jros2.generator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.ihmc.jros2.parser.MsgContext;
import us.ihmc.jros2.parser.MsgParser;
import us.ihmc.jros2.parser.SrvContext;
import us.ihmc.jros2.parser.SrvParser;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

public class SrvGeneratorTest
{
   private static final String CONFIGURE_SRV = """
                                               # Request header comment

                                               uint8 MODE_A=0
                                               uint8 MODE_B=1
                                               # Selected mode
                                               uint8 mode # trailing
                                               string[] names
                                               ---
                                               # Response header comment

                                               int32 ERROR_NONE=0
                                               int32 code
                                               string message
                                               """;

   @Test
   public void testGenerateRequestAndResponse() throws Exception
   {
      SrvContext srv = SrvParser.parseSrv(CONFIGURE_SRV, "test_srvs/Configure.srv");

      String request = ROS2MessageGenerator.generateJavaClassContents(srv.getRequestContext(), new HashMap<>());
      Assertions.assertTrue(request.contains("package test_srvs;"));
      Assertions.assertTrue(request.contains("public class Configure_Request implements ROS2Message<Configure_Request>"));
      Assertions.assertTrue(request.contains("\"test_srvs::srv::dds_::Configure_Request_\""));
      Assertions.assertTrue(request.contains("MODE_A"));
      Assertions.assertTrue(request.contains("names"));
      Assertions.assertFalse(request.contains("ERROR_NONE"));

      String response = ROS2MessageGenerator.generateJavaClassContents(srv.getResponseContext(), new HashMap<>());
      Assertions.assertTrue(response.contains("public class Configure_Response implements ROS2Message<Configure_Response>"));
      Assertions.assertTrue(response.contains("\"test_srvs::srv::dds_::Configure_Response_\""));
      Assertions.assertTrue(response.contains("ERROR_NONE"));
      Assertions.assertFalse(response.contains("MODE_A"));
   }

   @Test
   public void testGenerateEmptySection() throws Exception
   {
      SrvContext srv = SrvParser.parseSrv("---\nbool success\nstring message\n", "test_srvs/Trigger.srv");

      String request = ROS2MessageGenerator.generateJavaClassContents(srv.getRequestContext(), new HashMap<>());
      Assertions.assertTrue(request.contains("public class Trigger_Request implements ROS2Message<Trigger_Request>"));
      Assertions.assertTrue(request.contains("\"test_srvs::srv::dds_::Trigger_Request_\""));
   }

   @Test
   public void testMsgGenerationUnchanged() throws Exception
   {
      MsgContext msg = MsgParser.parseMsg("int32 a\n", "test_msgs/TestMsg.msg");
      String generated = ROS2MessageGenerator.generateJavaClassContents(msg, new HashMap<>());
      Assertions.assertTrue(generated.contains("\"test_msgs::msg::dds_::TestMsg_\""));
      Assertions.assertTrue(generated.contains("public class TestMsg implements ROS2Message<TestMsg>"));
   }

   /**
    * Compiles the generated request/response classes against the test classpath (which contains ROS2Message and the CDR classes).
    */
   @Test
   public void testGeneratedSrvClassesCompile(@TempDir Path tempDir) throws Exception
   {
      JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
      Assertions.assertNotNull(compiler, "A JDK is required to run this test");

      SrvContext configure = SrvParser.parseSrv(CONFIGURE_SRV, "test_srvs/Configure.srv");
      SrvContext trigger = SrvParser.parseSrv("---\nbool success\nstring message\n", "test_srvs/Trigger.srv");

      Path sourceDir = tempDir.resolve("src/test_srvs");
      Files.createDirectories(sourceDir);
      String[] sources = new String[4];
      int i = 0;
      for (MsgContext context : new MsgContext[] {configure.getRequestContext(),
                                                  configure.getResponseContext(),
                                                  trigger.getRequestContext(),
                                                  trigger.getResponseContext()})
      {
         Path file = sourceDir.resolve(context.getJavaClassName() + ".java");
         Files.writeString(file, ROS2MessageGenerator.generateJavaClassContents(context, new HashMap<>()));
         sources[i++] = file.toString();
      }

      Path classesDir = tempDir.resolve("classes");
      Files.createDirectories(classesDir);
      String[] args = new String[4 + sources.length];
      args[0] = "-cp";
      args[1] = System.getProperty("java.class.path");
      args[2] = "-d";
      args[3] = classesDir.toString();
      System.arraycopy(sources, 0, args, 4, sources.length);

      ByteArrayOutputStream errors = new ByteArrayOutputStream();
      int result = compiler.run(null, null, errors, args);
      Assertions.assertEquals(0, result, errors.toString());
      Assertions.assertTrue(Files.exists(classesDir.resolve("test_srvs/Configure_Request.class")));
      Assertions.assertTrue(Files.exists(classesDir.resolve("test_srvs/Trigger_Response.class")));
   }
}
