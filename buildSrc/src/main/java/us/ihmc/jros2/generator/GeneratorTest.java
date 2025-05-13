//package us.ihmc.jros2.generator;
//
//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.api.Test;
//import us.ihmc.jros2.generator.context.ActionContext;
//import us.ihmc.jros2.generator.context.MsgContext;
//import us.ihmc.jros2.generator.context.SrvContext;
//
//import java.util.LinkedList;
//import java.util.List;
//
//public class GeneratorTest
//{
//   @Test
//   public void testMsgContextAndParsing()
//   {
//      List<MsgContext> knownMsgs = new LinkedList<>();
//
//      MsgContext testMsg = new MsgContext("test_msgs", "TestMsg.msg", """
//            # Header comment for the entire message
//            # Header comment for the entire message (line 2)
//            # Header comment for the entire message (line 3)
//
//            # Some comment about test_int # # # #
//            # Some comment about test_int (line 2) # # # #
//            # Some comment about test_int (line 3) # # # #
//            uint32 test_int # Some additional comment about test_int
//            uint32 const_int=5
//            uint8 def_int 10
//            """);
//      knownMsgs.add(testMsg);
//      testMsg.parse(knownMsgs);
//
//      Assertions.assertEquals("test_msgs", testMsg.getPackageName());
//      Assertions.assertEquals("TestMsg.msg", testMsg.getFileName());
//      Assertions.assertEquals("TestMsg", testMsg.getName());
//      Assertions.assertEquals(
//            "# Header comment for the entire message\n# Header comment for the entire message (line 2)\n# Header comment for the entire message (line 3)",
//            testMsg.getHeaderComment());
//      Assertions.assertEquals(3, testMsg.getFields().size());
//
//      Assertions.assertEquals("uint32", testMsg.getField("test_int").getType());
//      Assertions.assertEquals(
//            "# Some comment about test_int # # # #\n# Some comment about test_int (line 2) # # # #\n# Some comment about test_int (line 3) # # # #",
//            testMsg.getField("test_int").getHeaderComment());
//      Assertions.assertEquals("# Some additional comment about test_int", testMsg.getField("test_int").getTrailingComment());
//      Assertions.assertEquals("uint32", testMsg.getField("const_int").getType());
//      Assertions.assertEquals("5", testMsg.getField("const_int").getConstantValue());
//      Assertions.assertEquals("10", testMsg.getField("def_int").getDefaultValue());
//
//      MsgContext testMsg2 = new MsgContext("test_msgs", "TestMsg2.msg", """
//            TestMsg test_msg
//            float32 other_data
//            float32 other_const = 4.0
//            string s
//            """);
//      testMsg2.parse(knownMsgs);
//
//      Assertions.assertEquals("test_msgs", testMsg2.getPackageName());
//      Assertions.assertEquals("TestMsg2.msg", testMsg2.getFileName());
//      Assertions.assertEquals("TestMsg2", testMsg2.getName());
//      Assertions.assertEquals(4, testMsg2.getFields().size());
//
//      Assertions.assertEquals("TestMsg", testMsg2.getField("test_msg").getType());
//      Assertions.assertEquals("float32", testMsg2.getField("other_data").getType());
//      Assertions.assertEquals("float32", testMsg2.getField("other_const").getType());
//      Assertions.assertEquals("4.0", testMsg2.getField("other_const").getConstantValue());
//      Assertions.assertEquals("string", testMsg2.getField("s").getType());
//   }
//
//   @Test
//   public void testSrvContextAndParsing()
//   {
//      List<MsgContext> knownMsgs = new LinkedList<>();
//
//      MsgContext testMsg = new MsgContext("test_msgs", "TestMsg.msg", """
//            # Some comment about test_int # # # #
//            # Some comment about test_int (line 2) # # # #
//            # Some comment about test_int (line 3) # # # #
//            uint32 test_int # Some additional comment about test_int
//            uint32 const_int=5
//            uint8 def_int 10
//            """);
//      knownMsgs.add(testMsg);
//      testMsg.parse(knownMsgs);
//
//      SrvContext testSrv = new SrvContext("test_srvs", "TestService.srv", """
//            int64 a # request field a
//            int64 b # request field b
//            TestMsg c # request field c
//            ---
//            int64 r # reply field r
//            """);
//      testSrv.parse(knownMsgs);
//
//      Assertions.assertEquals(3, testSrv.getRequestFields().size());
//      Assertions.assertEquals(1, testSrv.getReplyFields().size());
//      Assertions.assertEquals("int64", testSrv.getRequestFields().get("a").getType());
//      Assertions.assertEquals("int64", testSrv.getRequestFields().get("b").getType());
//      Assertions.assertEquals("TestMsg", testSrv.getRequestFields().get("c").getType());
//      Assertions.assertEquals("int64", testSrv.getReplyFields().get("r").getType());
//   }
//
//   @Test
//   public void testActionContextAndParsing()
//   {
//      List<MsgContext> knownMsgs = new LinkedList<>();
//
//      MsgContext testMsg = new MsgContext("test_msgs", "TestMsg.msg", """
//            # Some comment about test_int # # # #
//            # Some comment about test_int (line 2) # # # #
//            # Some comment about test_int (line 3) # # # #
//            uint32 test_int # Some additional comment about test_int
//            uint32 const_int=5
//            uint8 def_int 10
//            """);
//      knownMsgs.add(testMsg);
//      testMsg.parse(knownMsgs);
//
//      ActionContext testAction = new ActionContext("test_actions", "TestAction.action", """
//            # Some header for the entire action file
//
//            # Goal header comment
//            float32[3] g # goal
//            ---
//            # Result header comment
//            float32 r # result
//            ---
//            # Feedback header comment
//            TestMsg f # feedback
//            """);
//      testAction.parse(knownMsgs);
//
//      Assertions.assertEquals("# Some header for the entire action file", testAction.getHeaderComment());
//      Assertions.assertEquals(1, testAction.getGoalFields().size());
//      Assertions.assertEquals(1, testAction.getResultFields().size());
//      Assertions.assertEquals(1, testAction.getFeedbackFields().size());
//
//      Assertions.assertEquals("# Goal header comment", testAction.getGoalFields().get("g").getHeaderComment());
//      Assertions.assertEquals("# goal", testAction.getGoalFields().get("g").getTrailingComment());
//      Assertions.assertEquals("float32", testAction.getGoalFields().get("g").getType());
//      Assertions.assertTrue(testAction.getGoalFields().get("g").getArray());
//      Assertions.assertEquals(3, testAction.getGoalFields().get("g").getLength());
//
//      Assertions.assertEquals("# Result header comment", testAction.getResultFields().get("r").getHeaderComment());
//      Assertions.assertEquals("# result", testAction.getResultFields().get("r").getTrailingComment());
//      Assertions.assertEquals("float32", testAction.getResultFields().get("r").getType());
//      Assertions.assertFalse(testAction.getResultFields().get("r").getArray());
//
//      Assertions.assertEquals("# Feedback header comment", testAction.getFeedbackFields().get("f").getHeaderComment());
//      Assertions.assertEquals("# feedback", testAction.getFeedbackFields().get("f").getTrailingComment());
//      Assertions.assertEquals("TestMsg", testAction.getFeedbackFields().get("f").getType());
//      Assertions.assertFalse(testAction.getFeedbackFields().get("f").getArray());
//   }
//}
