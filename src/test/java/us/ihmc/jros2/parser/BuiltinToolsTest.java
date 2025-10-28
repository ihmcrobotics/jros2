package us.ihmc.jros2.parser;

import org.junit.jupiter.api.Test;
import us.ihmc.jros2.parser.util.BuiltinTools;

import static org.junit.jupiter.api.Assertions.*;

public class BuiltinToolsTest
{
   @Test
   public void testSanitizeStringAsJavaFieldValue()
   {
      testSanitizeStringAsJavaFieldValue(null, "\"\"");
      testSanitizeStringAsJavaFieldValue("", "\"\"");
      testSanitizeStringAsJavaFieldValue("\"\"", "\"\"");
      testSanitizeStringAsJavaFieldValue("''", "\"\"");
      testSanitizeStringAsJavaFieldValue("\"''\"", "\"''\"");
      testSanitizeStringAsJavaFieldValue("'\"\"'", "\"\\\"\\\"\"");
      testSanitizeStringAsJavaFieldValue("Hello World", "\"Hello World\"");
      testSanitizeStringAsJavaFieldValue("\"Hello World\"", "\"Hello World\"");
      testSanitizeStringAsJavaFieldValue("'Hello World'", "\"Hello World\"");
      testSanitizeStringAsJavaFieldValue("Hello \"World\"", "\"Hello \\\"World\\\"\"");
      testSanitizeStringAsJavaFieldValue("Hello 'World'", "\"Hello 'World'\"");
      testSanitizeStringAsJavaFieldValue("\"Hello\" World", "\"\\\"Hello\\\" World\"");
      testSanitizeStringAsJavaFieldValue("'Hello' World", "\"'Hello' World\"");
      testSanitizeStringAsJavaFieldValue("\"Hello 'World'\"", "\"Hello 'World'\"");
      testSanitizeStringAsJavaFieldValue("'Hello \"World\"'", "\"Hello \\\"World\\\"\"");
      testSanitizeStringAsJavaFieldValue("\"Hello\" 'World'", "\"\\\"Hello\\\" 'World'\"");
      testSanitizeStringAsJavaFieldValue("'Hello' \"World\"", "\"'Hello' \\\"World\\\"\"");
      testSanitizeStringAsJavaFieldValue("  Hello World  ", "\"Hello World\"");
      testSanitizeStringAsJavaFieldValue("\"  Hello World  \"", "\"  Hello World  \"");
      testSanitizeStringAsJavaFieldValue("'  Hello World  '", "\"  Hello World  \"");

      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.sanitizeStringAsJavaFieldValue("\" \" \""));
      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.sanitizeStringAsJavaFieldValue("' ' '"));
   }

   private void testSanitizeStringAsJavaFieldValue(String input, String expected)
   {
      assertEquals(expected, BuiltinTools.sanitizeStringAsJavaFieldValue(input));
   }

   @Test
   public void testSanitizeBoolAsJavaFieldValue()
   {
      assertFalse(Boolean.parseBoolean(BuiltinTools.sanitizeBoolAsJavaFieldValue(null)));
      assertFalse(Boolean.parseBoolean(BuiltinTools.sanitizeBoolAsJavaFieldValue("")));
      assertFalse(Boolean.parseBoolean(BuiltinTools.sanitizeBoolAsJavaFieldValue("false")));
      assertFalse(Boolean.parseBoolean(BuiltinTools.sanitizeBoolAsJavaFieldValue("0")));
      assertTrue(Boolean.parseBoolean(BuiltinTools.sanitizeBoolAsJavaFieldValue("true")));
      assertTrue(Boolean.parseBoolean(BuiltinTools.sanitizeBoolAsJavaFieldValue("1")));

      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.sanitizeBoolAsJavaFieldValue("Hello"));
      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.sanitizeBoolAsJavaFieldValue("2"));
   }
}
