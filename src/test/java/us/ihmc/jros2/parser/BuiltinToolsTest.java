package us.ihmc.jros2.parser;

import org.junit.jupiter.api.Test;
import us.ihmc.jros2.parser.util.BuiltinTools;

import static org.junit.jupiter.api.Assertions.*;

public class BuiltinToolsTest
{
   @Test
   public void testIsBuiltinType()
   {
      assertTrue(BuiltinTools.isBuiltinType("bool"));
      assertTrue(BuiltinTools.isBuiltinType("byte"));
      assertTrue(BuiltinTools.isBuiltinType("char"));
      assertTrue(BuiltinTools.isBuiltinType("float32"));
      assertTrue(BuiltinTools.isBuiltinType("float64"));
      assertTrue(BuiltinTools.isBuiltinType("int8"));
      assertTrue(BuiltinTools.isBuiltinType("uint8"));
      assertTrue(BuiltinTools.isBuiltinType("int16"));
      assertTrue(BuiltinTools.isBuiltinType("uint16"));
      assertTrue(BuiltinTools.isBuiltinType("int32"));
      assertTrue(BuiltinTools.isBuiltinType("uint32"));
      assertTrue(BuiltinTools.isBuiltinType("int64"));
      assertTrue(BuiltinTools.isBuiltinType("uint64"));
      assertTrue(BuiltinTools.isBuiltinType("string"));
      assertTrue(BuiltinTools.isBuiltinType("wstring"));

      assertFalse(BuiltinTools.isBuiltinType("Hello"));
      assertFalse(BuiltinTools.isBuiltinType("World"));
      assertFalse(BuiltinTools.isBuiltinType("1234"));
      assertFalse(BuiltinTools.isBuiltinType("0xFF"));

      assertThrows(RuntimeException.class, () -> BuiltinTools.isBuiltinType(null));
   }

   @Test
   public void testGetBuiltinTypeSize()
   {
      assertEquals(1, BuiltinTools.getBuiltinTypeSize("bool"));
      assertEquals(1, BuiltinTools.getBuiltinTypeSize("byte"));
      assertEquals(1, BuiltinTools.getBuiltinTypeSize("char"));
      assertEquals(1, BuiltinTools.getBuiltinTypeSize("int8"));
      assertEquals(1, BuiltinTools.getBuiltinTypeSize("uint8"));
      assertEquals(2, BuiltinTools.getBuiltinTypeSize("int16"));
      assertEquals(2, BuiltinTools.getBuiltinTypeSize("uint16"));
      assertEquals(4, BuiltinTools.getBuiltinTypeSize("float32"));
      assertEquals(4, BuiltinTools.getBuiltinTypeSize("int32"));
      assertEquals(4, BuiltinTools.getBuiltinTypeSize("uint32"));
      assertEquals(8, BuiltinTools.getBuiltinTypeSize("float64"));
      assertEquals(8, BuiltinTools.getBuiltinTypeSize("int64"));
      assertEquals(8, BuiltinTools.getBuiltinTypeSize("uint64"));
      assertEquals(1, BuiltinTools.getBuiltinTypeSize("string"));
      assertEquals(4, BuiltinTools.getBuiltinTypeSize("wstring"));

      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinTypeSize("Hello"));
      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinTypeSize("World"));
      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinTypeSize("123"));
      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinTypeSize("0xFF"));
   }

   @Test
   public void testGetBuiltinTypeJavaType()
   {
      assertEquals("boolean", BuiltinTools.getBuiltinTypeJavaType("bool"));
      assertEquals("byte", BuiltinTools.getBuiltinTypeJavaType("byte"));
      assertEquals("byte", BuiltinTools.getBuiltinTypeJavaType("int8"));
      assertEquals("char", BuiltinTools.getBuiltinTypeJavaType("char"));
      assertEquals("short", BuiltinTools.getBuiltinTypeJavaType("uint8"));
      assertEquals("short", BuiltinTools.getBuiltinTypeJavaType("int16"));
      assertEquals("float", BuiltinTools.getBuiltinTypeJavaType("float32"));
      assertEquals("int", BuiltinTools.getBuiltinTypeJavaType("uint16"));
      assertEquals("int", BuiltinTools.getBuiltinTypeJavaType("int32"));
      assertEquals("double", BuiltinTools.getBuiltinTypeJavaType("float64"));
      assertEquals("long", BuiltinTools.getBuiltinTypeJavaType("uint32"));
      assertEquals("long", BuiltinTools.getBuiltinTypeJavaType("int64"));
      assertEquals("long", BuiltinTools.getBuiltinTypeJavaType("uint64"));
      assertEquals("StringBuilder", BuiltinTools.getBuiltinTypeJavaType("string"));
      assertEquals("StringBuilder", BuiltinTools.getBuiltinTypeJavaType("wstring"));

      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinTypeJavaType("Hello"));
      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinTypeJavaType("World"));
      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinTypeJavaType("123"));
      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinTypeJavaType("0xFF"));
   }

   @Test
   public void testGetBuiltinTypeIDLSequenceType()
   {
      assertEquals("IDLBoolSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("bool"));
      assertEquals("IDLByteSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("byte"));
      assertEquals("IDLByteSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("int8"));
      assertEquals("IDLCharSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("char"));
      assertEquals("IDLShortSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("uint8"));
      assertEquals("IDLShortSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("int16"));
      assertEquals("IDLFloatSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("float32"));
      assertEquals("IDLIntSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("uint16"));
      assertEquals("IDLIntSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("int32"));
      assertEquals("IDLLongSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("uint32"));
      assertEquals("IDLLongSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("int64"));
      assertEquals("IDLLongSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("uint64"));
      assertEquals("IDLStringSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("string"));
      assertEquals("IDLWStringSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("wstring"));

      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinTypeIDLSequenceType("Hello"));
      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinTypeIDLSequenceType("World"));
      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinTypeIDLSequenceType("123"));
      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinTypeIDLSequenceType("0xFF"));
   }

   @Test
   public void testGetBuiltinCDRBufferWriteMethod()
   {
      assertEquals("writeBoolean", BuiltinTools.getBuiltinCDRBufferWriteMethod("bool"));
      assertEquals("writeByte", BuiltinTools.getBuiltinCDRBufferWriteMethod("byte"));
      assertEquals("writeByte", BuiltinTools.getBuiltinCDRBufferWriteMethod("int8"));
      assertEquals("writeChar", BuiltinTools.getBuiltinCDRBufferWriteMethod("char"));
      assertEquals("writeShort", BuiltinTools.getBuiltinCDRBufferWriteMethod("uint8"));
      assertEquals("writeShort", BuiltinTools.getBuiltinCDRBufferWriteMethod("int16"));
      assertEquals("writeFloat", BuiltinTools.getBuiltinCDRBufferWriteMethod("float32"));
      assertEquals("writeInt", BuiltinTools.getBuiltinCDRBufferWriteMethod("uint16"));
      assertEquals("writeInt", BuiltinTools.getBuiltinCDRBufferWriteMethod("int32"));
      assertEquals("writeDouble", BuiltinTools.getBuiltinCDRBufferWriteMethod("float64"));
      assertEquals("writeLong", BuiltinTools.getBuiltinCDRBufferWriteMethod("uint32"));
      assertEquals("writeLong", BuiltinTools.getBuiltinCDRBufferWriteMethod("int64"));
      assertEquals("writeLong", BuiltinTools.getBuiltinCDRBufferWriteMethod("uint64"));
      assertEquals("writeString", BuiltinTools.getBuiltinCDRBufferWriteMethod("string"));
      assertEquals("writeWString", BuiltinTools.getBuiltinCDRBufferWriteMethod("wstring"));

      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinCDRBufferWriteMethod("Hello"));
      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinCDRBufferWriteMethod("World"));
      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinCDRBufferWriteMethod("123"));
      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinCDRBufferWriteMethod("0xFF"));
   }

   @Test
   public void testGetBuiltinCDRBufferReadMethod()
   {
      assertEquals("readBoolean", BuiltinTools.getBuiltinCDRBufferReadMethod("bool"));
      assertEquals("readByte", BuiltinTools.getBuiltinCDRBufferReadMethod("byte"));
      assertEquals("readByte", BuiltinTools.getBuiltinCDRBufferReadMethod("int8"));
      assertEquals("readChar", BuiltinTools.getBuiltinCDRBufferReadMethod("char"));
      assertEquals("readShort", BuiltinTools.getBuiltinCDRBufferReadMethod("uint8"));
      assertEquals("readShort", BuiltinTools.getBuiltinCDRBufferReadMethod("int16"));
      assertEquals("readFloat", BuiltinTools.getBuiltinCDRBufferReadMethod("float32"));
      assertEquals("readInt", BuiltinTools.getBuiltinCDRBufferReadMethod("uint16"));
      assertEquals("readInt", BuiltinTools.getBuiltinCDRBufferReadMethod("int32"));
      assertEquals("readDouble", BuiltinTools.getBuiltinCDRBufferReadMethod("float64"));
      assertEquals("readLong", BuiltinTools.getBuiltinCDRBufferReadMethod("uint32"));
      assertEquals("readLong", BuiltinTools.getBuiltinCDRBufferReadMethod("int64"));
      assertEquals("readLong", BuiltinTools.getBuiltinCDRBufferReadMethod("uint64"));
      assertEquals("readString", BuiltinTools.getBuiltinCDRBufferReadMethod("string"));
      assertEquals("readWString", BuiltinTools.getBuiltinCDRBufferReadMethod("wstring"));

      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinCDRBufferReadMethod("Hello"));
      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinCDRBufferReadMethod("World"));
      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinCDRBufferReadMethod("123"));
      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.getBuiltinCDRBufferReadMethod("0xFF"));
   }

   @Test
   public void testSanitizeStringAsJavaFieldValue()
   {
      assertEquals("\"\"", BuiltinTools.sanitizeStringAsJavaFieldValue(null));
      assertEquals("\"\"", BuiltinTools.sanitizeStringAsJavaFieldValue(""));
      assertEquals("\"\"", BuiltinTools.sanitizeStringAsJavaFieldValue("\"\""));
      assertEquals("\"\"", BuiltinTools.sanitizeStringAsJavaFieldValue("''"));
      assertEquals("\"''\"", BuiltinTools.sanitizeStringAsJavaFieldValue("\"''\""));
      assertEquals("\"\\\"\\\"\"", BuiltinTools.sanitizeStringAsJavaFieldValue("'\"\"'"));
      assertEquals("\"Hello World\"", BuiltinTools.sanitizeStringAsJavaFieldValue("Hello World"));
      assertEquals("\"Hello World\"", BuiltinTools.sanitizeStringAsJavaFieldValue("\"Hello World\""));
      assertEquals("\"Hello World\"", BuiltinTools.sanitizeStringAsJavaFieldValue("'Hello World'"));
      assertEquals("\"Hello \\\"World\\\"\"", BuiltinTools.sanitizeStringAsJavaFieldValue("Hello \"World\""));
      assertEquals("\"Hello 'World'\"", BuiltinTools.sanitizeStringAsJavaFieldValue("Hello 'World'"));
      assertEquals("\"\\\"Hello\\\" World\"", BuiltinTools.sanitizeStringAsJavaFieldValue("\"Hello\" World"));
      assertEquals("\"'Hello' World\"", BuiltinTools.sanitizeStringAsJavaFieldValue("'Hello' World"));
      assertEquals("\"Hello 'World'\"", BuiltinTools.sanitizeStringAsJavaFieldValue("\"Hello 'World'\""));
      assertEquals("\"Hello \\\"World\\\"\"", BuiltinTools.sanitizeStringAsJavaFieldValue("'Hello \"World\"'"));
      assertEquals("\"\\\"Hello\\\" 'World'\"", BuiltinTools.sanitizeStringAsJavaFieldValue("\"Hello\" 'World'"));
      assertEquals("\"'Hello' \\\"World\\\"\"", BuiltinTools.sanitizeStringAsJavaFieldValue("'Hello' \"World\""));
      assertEquals("\"Hello World\"", BuiltinTools.sanitizeStringAsJavaFieldValue("  Hello World  "));
      assertEquals("\"  Hello World  \"", BuiltinTools.sanitizeStringAsJavaFieldValue("\"  Hello World  \""));
      assertEquals("\"  Goodbye World  \"", BuiltinTools.sanitizeStringAsJavaFieldValue("'  Goodbye World  '"));

      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.sanitizeStringAsJavaFieldValue("\" \" \""));
      assertThrows(IllegalArgumentException.class, () -> BuiltinTools.sanitizeStringAsJavaFieldValue("' ' '"));
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
