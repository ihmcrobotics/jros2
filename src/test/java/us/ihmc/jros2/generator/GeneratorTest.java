package us.ihmc.jros2.generator;

import org.junit.jupiter.api.Test;
import us.ihmc.jros2.parser.MsgContext;
import us.ihmc.jros2.parser.MsgParser;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeneratorTest
{
   @Test
   public void testGenerateToString() throws Exception
   {
      MsgContext context = MsgParser.parseMsg("""
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

      String generatedFile = ROS2MessageGenerator.generateJavaClassContents(context, new HashMap<>());

      // GeneratorGoldenTest compiles generated classes and exercises them; here only the rendering of this schema is checked
      assertTrue(generatedFile.contains("public class TestMsg implements ROS2Message<TestMsg>"));
      assertTrue(generatedFile.contains("public static final java.lang.String name = \"test_msgs::msg::dds_::TestMsg_\";"));
      assertTrue(generatedFile.contains("public static final java.lang.String typeHash = null;"), "no hash was computed for this context");
      assertTrue(generatedFile.contains("private int test_int_; // Some additional comment about test_int"));
      assertTrue(generatedFile.contains("public static final int CONST_INT = 5;"));
      assertTrue(generatedFile.contains("def_int_ = (byte) 10;"));
      assertTrue(generatedFile.contains("buffer.writeInt(test_int_);"));
      assertTrue(generatedFile.contains("def_int_ = buffer.readByte();"));
      assertTrue(generatedFile.contains("Some comment about test_int (line 3)"));
      assertFalse(generatedFile.contains("CONST_INT = buffer"), "constants are not serialized");

      context.setTypeHash("RIHS01_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
      assertTrue(ROS2MessageGenerator.generateJavaClassContents(context, new HashMap<>())
                                     .contains("typeHash = \"RIHS01_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\";"));
   }

   @Test
   public void testEscapeForJavaComment()
   {
      // Every backslash is doubled in these Java string literals
      String noBackslash = "plain comment";
      assertSame(noBackslash, ROS2MessageGenerator.escapeForJavaComment(noBackslash));
      assertEquals("C:\\\\users", ROS2MessageGenerator.escapeForJavaComment("C:\\users"));
      assertEquals("\\\\u000a", ROS2MessageGenerator.escapeForJavaComment("\\u000a"));
      assertEquals("\\\\uabcd", ROS2MessageGenerator.escapeForJavaComment("\\\\uabcd"), "an even run is not an escape");
      assertEquals("\\\\\\\\uabcd", ROS2MessageGenerator.escapeForJavaComment("\\\\\\uabcd"));
      assertEquals("a\\b\\", ROS2MessageGenerator.escapeForJavaComment("a\\b\\"), "backslashes not followed by u are kept");
      assertEquals("\\\\uu", ROS2MessageGenerator.escapeForJavaComment("\\uu"));
      assertEquals("x\\\\u", ROS2MessageGenerator.escapeForJavaComment("x\\u"));
   }
}
