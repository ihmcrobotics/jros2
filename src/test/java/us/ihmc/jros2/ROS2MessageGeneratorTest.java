package us.ihmc.jros2;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import us.ihmc.jros2.generator.context.MsgContext;

import java.util.HashMap;
import java.util.Map;

public class ROS2MessageGeneratorTest
{
   @Test
   public void testInterfaceContextAndParsing()
   {
      Map<String, MsgContext> knownMsgs = new HashMap<>();

      MsgContext testMsg = new MsgContext("test_msgs", "TestMsg.msg", """
            uint32 test_int
            uint32 const_int=5
            uint8 def_int 10
            """);
      knownMsgs.put(testMsg.getName(), testMsg);
      testMsg.parse(knownMsgs);

      Assertions.assertEquals("test_msgs", testMsg.getROS2PackageName());
      Assertions.assertEquals("TestMsg.msg", testMsg.getName());
      Assertions.assertEquals("TestMsg", testMsg.getNameWithoutExtension());
      Assertions.assertEquals(3, testMsg.getFields().size());

      Assertions.assertEquals("uint32", testMsg.getFields().get("test_int").getType());
      Assertions.assertEquals("uint32", testMsg.getFields().get("const_int").getType());
      Assertions.assertEquals("5", testMsg.getFields().get("const_int").getConstantValue());
      Assertions.assertEquals("10", testMsg.getFields().get("def_int").getDefaultValue());

      MsgContext testMsg2 = new MsgContext("test_msgs", "TestMsg2.msg", """
            TestMsg test_msg
            float32 other_data
            float32 other_const = 4.0
            string s
            """);
      testMsg2.parse(knownMsgs);

      Assertions.assertEquals("test_msgs", testMsg2.getROS2PackageName());
      Assertions.assertEquals("TestMsg2.msg", testMsg2.getName());
      Assertions.assertEquals("TestMsg2", testMsg2.getNameWithoutExtension());
      Assertions.assertEquals(4, testMsg2.getFields().size());

      Assertions.assertEquals("TestMsg", testMsg2.getFields().get("test_msg").getType());
      Assertions.assertEquals("float32", testMsg2.getFields().get("other_data").getType());
      Assertions.assertEquals("float32", testMsg2.getFields().get("other_const").getType());
      Assertions.assertEquals("string", testMsg2.getFields().get("s").getType());
   }
}
