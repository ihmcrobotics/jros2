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

import java.util.List;

public class ActionParserTest
{
   @Test
   public void testFibonacci() throws InterfaceFieldParsingException
   {
      ActionContext action = ActionParser.parseAction("""
                                                      # Goal
                                                      int32 order
                                                      ---
                                                      # Result
                                                      int32[] sequence
                                                      ---
                                                      # Feedback
                                                      int32[] sequence
                                                      """, "example_interfaces/Fibonacci.action");

      Assertions.assertEquals("example_interfaces", action.getPackageName());
      Assertions.assertEquals("Fibonacci", action.getActionName());

      Assertions.assertEquals(List.of("Fibonacci_Goal",
                                      "Fibonacci_Result",
                                      "Fibonacci_Feedback",
                                      "Fibonacci_SendGoal_Request",
                                      "Fibonacci_SendGoal_Response",
                                      "Fibonacci_GetResult_Request",
                                      "Fibonacci_GetResult_Response",
                                      "Fibonacci_FeedbackMessage"), action.getAllContexts().stream().map(MsgContext::getJavaClassName).toList());
      Assertions.assertEquals("example_interfaces::action::dds_::Fibonacci_Goal_", action.getGoalContext().getDDSName());
      Assertions.assertEquals("example_interfaces::action::dds_::Fibonacci_SendGoal_Request_", action.getSendGoalRequestContext().getDDSName());
      Assertions.assertEquals("example_interfaces::action::dds_::Fibonacci_GetResult_Response_", action.getGetResultResponseContext().getDDSName());
      Assertions.assertEquals("example_interfaces::action::dds_::Fibonacci_FeedbackMessage_", action.getFeedbackMessageContext().getDDSName());

      Assertions.assertEquals("int32", action.getGoalContext().getFields().get("order").getType());
      Assertions.assertTrue(action.getResultContext().getFields().get("sequence").isArray());
      Assertions.assertTrue(action.getFeedbackContext().getFields().containsKey("sequence"));

      // Derived messages, as defined by rosidl_parser
      Assertions.assertEquals(List.of("goal_id", "goal"), List.copyOf(action.getSendGoalRequestContext().getFields().keySet()));
      Assertions.assertEquals("unique_identifier_msgs.UUID", action.getSendGoalRequestContext().getFields().get("goal_id").getJavaType());
      Assertions.assertEquals("example_interfaces.Fibonacci_Goal", action.getSendGoalRequestContext().getFields().get("goal").getJavaType());
      Assertions.assertEquals(List.of("accepted", "stamp"), List.copyOf(action.getSendGoalResponseContext().getFields().keySet()));
      Assertions.assertEquals("builtin_interfaces.Time", action.getSendGoalResponseContext().getFields().get("stamp").getJavaType());
      Assertions.assertEquals(List.of("goal_id"), List.copyOf(action.getGetResultRequestContext().getFields().keySet()));
      Assertions.assertEquals(List.of("status", "result"), List.copyOf(action.getGetResultResponseContext().getFields().keySet()));
      Assertions.assertEquals("int8", action.getGetResultResponseContext().getFields().get("status").getType());
      Assertions.assertEquals(List.of("goal_id", "feedback"), List.copyOf(action.getFeedbackMessageContext().getFields().keySet()));
   }

   @Test
   public void testEmptySectionsAndConstants() throws InterfaceFieldParsingException
   {
      ActionContext action = ActionParser.parseAction("""
                                                      ---
                                                      uint8 OK=0
                                                      uint8 FAILED=255
                                                      uint8 code
                                                      ---
                                                      """, "test_msgs/Empty.action");

      Assertions.assertTrue(action.getGoalContext().getFields().isEmpty());
      Assertions.assertTrue(action.getFeedbackContext().getFields().isEmpty());
      Assertions.assertEquals("(byte) 255", action.getResultContext().getFields().get("FAILED").getConstantValue());
   }

   @Test
   public void testInvalidSeparatorCount()
   {
      Assertions.assertThrows(IllegalArgumentException.class, () -> ActionParser.parseAction("int32 a\n---\nint32 b\n", "test_msgs/Bad.action"));
      Assertions.assertThrows(IllegalArgumentException.class, () -> ActionParser.parseAction("int32 a\n---\n---\n---\n", "test_msgs/Bad.action"));
   }
}
