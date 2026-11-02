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
package us.ihmc.jros2.parser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import us.ihmc.jros2.parser.field.InterfaceFieldParsingException;

public class SrvParserTest
{
   @Test
   public void testSimpleSrv() throws InterfaceFieldParsingException
   {
      SrvContext srv = SrvParser.parseSrv("""
                                          int64 a
                                          int64 b
                                          ---
                                          int64 sum
                                          """, "example_interfaces/AddTwoInts.srv");

      Assertions.assertEquals("example_interfaces", srv.getPackageName());
      Assertions.assertEquals("AddTwoInts", srv.getServiceName());

      SrvSectionContext request = srv.getRequestContext();
      Assertions.assertEquals(SrvSection.REQUEST, request.getSection());
      Assertions.assertEquals("AddTwoInts_Request", request.getJavaClassName());
      Assertions.assertEquals("example_interfaces", request.getJavaPackageName());
      Assertions.assertEquals("example_interfaces::srv::dds_::AddTwoInts_Request_", request.getDDSName());
      Assertions.assertEquals(2, request.getFields().size());
      Assertions.assertEquals("int64", request.getFields().get("a").getType());
      Assertions.assertEquals("int64", request.getFields().get("b").getType());

      SrvSectionContext response = srv.getResponseContext();
      Assertions.assertEquals(SrvSection.RESPONSE, response.getSection());
      Assertions.assertEquals("AddTwoInts_Response", response.getJavaClassName());
      Assertions.assertEquals("example_interfaces::srv::dds_::AddTwoInts_Response_", response.getDDSName());
      Assertions.assertEquals(1, response.getFields().size());
      Assertions.assertEquals("int64", response.getFields().get("sum").getType());
   }

   @Test
   public void testEmptyRequest() throws InterfaceFieldParsingException
   {
      // Same layout as std_srvs/Trigger.srv
      SrvContext srv = SrvParser.parseSrv("""
                                          ---
                                          bool success   # indicate successful run of triggered service
                                          string message # informational, e.g. for error messages
                                          """, "std_srvs/Trigger.srv");

      Assertions.assertTrue(srv.getRequestContext().getFields().isEmpty());
      Assertions.assertEquals("std_srvs::srv::dds_::Trigger_Request_", srv.getRequestContext().getDDSName());

      SrvSectionContext response = srv.getResponseContext();
      Assertions.assertEquals(2, response.getFields().size());
      Assertions.assertEquals("bool", response.getFields().get("success").getType());
      Assertions.assertEquals("indicate successful run of triggered service", response.getFields().get("success").getTrailingComment());
      Assertions.assertEquals("string", response.getFields().get("message").getType());
   }

   @Test
   public void testEmptyResponse() throws InterfaceFieldParsingException
   {
      SrvContext srv = SrvParser.parseSrv("""
                                          string name
                                          ---
                                          """, "test_msgs/Fire.srv");

      Assertions.assertEquals(1, srv.getRequestContext().getFields().size());
      Assertions.assertTrue(srv.getResponseContext().getFields().isEmpty());
      Assertions.assertEquals("test_msgs::srv::dds_::Fire_Response_", srv.getResponseContext().getDDSName());
   }

   @Test
   public void testBothSectionsEmpty() throws InterfaceFieldParsingException
   {
      SrvContext srv = SrvParser.parseSrv("---\n", "test_msgs/Empty.srv");

      Assertions.assertTrue(srv.getRequestContext().getFields().isEmpty());
      Assertions.assertTrue(srv.getResponseContext().getFields().isEmpty());
      Assertions.assertEquals("Empty_Request", srv.getRequestContext().getJavaClassName());
      Assertions.assertEquals("Empty_Response", srv.getResponseContext().getJavaClassName());
   }

   @Test
   public void testConstantsAndComments() throws InterfaceFieldParsingException
   {
      SrvContext srv = SrvParser.parseSrv("""
                                          # Header comment for the request
                                          # (line 2)

                                          # Mode constants
                                          uint8 MODE_A=0
                                          uint8 MODE_B = 1
                                          # Selected mode
                                          uint8 mode # trailing comment on mode
                                          float64 gain 2.5
                                          string[] names
                                          ---
                                          # Header comment for the response

                                          int32 ERROR_NONE=0
                                          # Result code
                                          int32 code
                                          ParameterValue[] values
                                          """, "test_msgs/Configure.srv");

      SrvSectionContext request = srv.getRequestContext();
      Assertions.assertEquals("Header comment for the request\n(line 2)", request.getHeaderComment());
      Assertions.assertEquals(5, request.getFields().size());
      Assertions.assertEquals("0", request.getFields().get("MODE_A").getConstantValue());
      Assertions.assertEquals("Mode constants", request.getFields().get("MODE_A").getHeaderComment());
      Assertions.assertEquals("1", request.getFields().get("MODE_B").getConstantValue());
      Assertions.assertEquals("Selected mode", request.getFields().get("mode").getHeaderComment());
      Assertions.assertEquals("trailing comment on mode", request.getFields().get("mode").getTrailingComment());
      Assertions.assertEquals("2.5", request.getFields().get("gain").getDefaultValue());
      Assertions.assertEquals("string", request.getFields().get("names").getType());

      SrvSectionContext response = srv.getResponseContext();
      Assertions.assertEquals("Header comment for the response", response.getHeaderComment());
      Assertions.assertEquals(3, response.getFields().size());
      Assertions.assertEquals("0", response.getFields().get("ERROR_NONE").getConstantValue());
      Assertions.assertEquals("Result code", response.getFields().get("code").getHeaderComment());
      Assertions.assertEquals("ParameterValue", response.getFields().get("values").getType());

      // The schema of each section only contains its own lines
      Assertions.assertFalse(request.getSchema().contains("ERROR_NONE"));
      Assertions.assertFalse(response.getSchema().contains("MODE_A"));
      Assertions.assertFalse(request.getSchema().contains("---"));
   }

   @Test
   public void testInvalidSeparatorCount()
   {
      Assertions.assertThrows(IllegalArgumentException.class, () -> SrvParser.parseSrv("int64 a\n", "test_msgs/NoSep.srv"));
      Assertions.assertThrows(IllegalArgumentException.class, () -> SrvParser.parseSrv("int64 a\n---\nint64 b\n---\nint64 c\n", "test_msgs/Action.srv"));
   }

   @Test
   public void testMsgDDSNameUnchanged() throws InterfaceFieldParsingException
   {
      Assertions.assertEquals("std_msgs::msg::dds_::String_", MsgParser.parseMsg("string data\n", "std_msgs/String.msg").getDDSName());
      Assertions.assertEquals("test_msgs::msg::dds_::TestMsg_", MsgParser.parseMsg("int32 a\n", "test_msgs/TestMsg.msg").getDDSName());
   }
}
