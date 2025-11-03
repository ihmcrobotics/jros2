package us.ihmc.jros2.generator;

import org.junit.jupiter.api.Test;
import us.ihmc.jros2.parser.MsgContext;
import us.ihmc.jros2.parser.MsgParser;

import java.util.HashMap;

public class GeneratorTest
{
   @Test
   public void testGenerateToString()
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
      System.out.println(generatedFile);

      // TODO: Actually assert something

      // TODO: Probably have it write to disk then use javac to make sure the file compiles.
      //       Then test all the generated methods to ensure they output the correct values.
   }
}
