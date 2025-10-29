package us.ihmc.jros2.parser;

import org.junit.jupiter.api.Test;
import us.ihmc.jros2.parser.util.InterfaceTools;

import static org.junit.jupiter.api.Assertions.*;

public class InterfaceToolsTest
{
   @Test
   public void testCheckAndParsePackageResourceName()
   {
      String[] parsed = InterfaceTools.checkAndParsePackageResourceName("std_msgs/Empty");
      assertEquals("std_msgs", parsed[0]);
      assertEquals("Empty", parsed[1]);

      assertThrows(NullPointerException.class, () -> InterfaceTools.checkAndParsePackageResourceName(null));
      assertThrows(IllegalArgumentException.class, () -> InterfaceTools.checkAndParsePackageResourceName("std_msgs"));
      assertThrows(IllegalArgumentException.class, () -> InterfaceTools.checkAndParsePackageResourceName("Empty"));
      assertThrows(IllegalArgumentException.class, () -> InterfaceTools.checkAndParsePackageResourceName("std_msgs.Empty"));
      assertThrows(IllegalArgumentException.class, () -> InterfaceTools.checkAndParsePackageResourceName("std_msgs/msg/Empty"));
   }

   @Test
   public void testCheckSchema()
   {
      assertDoesNotThrow(() -> InterfaceTools.checkSchema("Hello"));
      assertThrows(NullPointerException.class, () -> InterfaceTools.checkSchema(null));
      assertThrows(IllegalArgumentException.class, () -> InterfaceTools.checkSchema(""));
   }
}
