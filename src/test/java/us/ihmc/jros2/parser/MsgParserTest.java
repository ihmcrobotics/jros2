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

public class MsgParserTest
{
   @Test
   public void testMsgParse() throws InterfaceFieldParsingException
   {
      MsgContext testMsg = MsgParser.parseMsg("""
                                                    # Header comment for the entire message
                                                    # Header comment for the entire message (line 2)
                                                    # Header comment for the entire message (line 3)
                                                    
                                                    # Some comment about test_int # # # #
                                                    # Some comment about test_int (line 2) # # # #
                                                    # Some comment about test_int (line 3) # # # #
                                                    uint32 test_int # Some additional comment about test_int
                                                    uint32 const_int=5
                                                    uint8 def_int 10
                                                    """, "test_msgs/TestMsg.msg");

      Assertions.assertEquals("test_msgs", testMsg.getPackageName());
      Assertions.assertEquals("TestMsg.msg", testMsg.getResourceName());
      Assertions.assertEquals("TestMsg", testMsg.getJavaClassName());
      Assertions.assertEquals(
            "Header comment for the entire message\nHeader comment for the entire message (line 2)\nHeader comment for the entire message (line 3)",
            testMsg.getHeaderComment());
      Assertions.assertEquals(3, testMsg.getFields().size());

      Assertions.assertEquals("uint32", testMsg.getFields().get("test_int").getType());
      Assertions.assertEquals(
            "Some comment about test_int # # # #\nSome comment about test_int (line 2) # # # #\nSome comment about test_int (line 3) # # # #",
            testMsg.getFields().get("test_int").getHeaderComment());
      Assertions.assertEquals("Some additional comment about test_int", testMsg.getFields().get("test_int").getTrailingComment());
      Assertions.assertEquals("uint32", testMsg.getFields().get("const_int").getType());
      Assertions.assertEquals("5", testMsg.getFields().get("const_int").getConstantValue());
      Assertions.assertEquals("10", testMsg.getFields().get("def_int").getDefaultValue());

      MsgContext testMsg2 = MsgParser.parseMsg("""
                                                     TestMsg test_msg
                                                     float32 other_data
                                                     float32 other_const = 4.0
                                                     string s
                                                     """, "test_msgs/TestMsg2.msg");

      Assertions.assertEquals("test_msgs", testMsg2.getPackageName());
      Assertions.assertEquals("TestMsg2.msg", testMsg2.getResourceName());
      Assertions.assertEquals("TestMsg2", testMsg2.getJavaClassName());
      Assertions.assertEquals(4, testMsg2.getFields().size());

      Assertions.assertEquals("TestMsg", testMsg2.getFields().get("test_msg").getType());
      Assertions.assertEquals("float32", testMsg2.getFields().get("other_data").getType());
      Assertions.assertEquals("float32", testMsg2.getFields().get("other_const").getType());
      Assertions.assertEquals("4.0", testMsg2.getFields().get("other_const").getConstantValue());
      Assertions.assertEquals("string", testMsg2.getFields().get("s").getType());
   }
}
