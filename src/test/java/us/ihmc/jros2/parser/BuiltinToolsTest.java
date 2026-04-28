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
      // 1 byte
      assertEquals(1, BuiltinTools.getBuiltinTypeSize("bool"));
      // 1 byte
      assertEquals(1, BuiltinTools.getBuiltinTypeSize("byte"));
      assertEquals(1, BuiltinTools.getBuiltinTypeSize("int8"));
      assertEquals(1, BuiltinTools.getBuiltinTypeSize("char"));
      // 2 bytes
      assertEquals(2, BuiltinTools.getBuiltinTypeSize("uint8"));
      assertEquals(2, BuiltinTools.getBuiltinTypeSize("int16"));
      // 4 bytes
      assertEquals(4, BuiltinTools.getBuiltinTypeSize("uint16"));
      assertEquals(4, BuiltinTools.getBuiltinTypeSize("float32"));
      assertEquals(4, BuiltinTools.getBuiltinTypeSize("int32"));
      assertEquals(4, BuiltinTools.getBuiltinTypeSize("uint32"));
      // 8 bytes
      assertEquals(8, BuiltinTools.getBuiltinTypeSize("float64"));
      assertEquals(8, BuiltinTools.getBuiltinTypeSize("int64"));
      assertEquals(8, BuiltinTools.getBuiltinTypeSize("uint64"));
      // Variable length strings (returns size of length prefix)
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
      // 1 bit
      assertEquals("boolean", BuiltinTools.getBuiltinTypeJavaType("bool"));
      // 1 byte
      assertEquals("byte", BuiltinTools.getBuiltinTypeJavaType("byte"));
      assertEquals("byte", BuiltinTools.getBuiltinTypeJavaType("int8"));
      // 2 bytes
      assertEquals("short", BuiltinTools.getBuiltinTypeJavaType("uint8"));
      assertEquals("short", BuiltinTools.getBuiltinTypeJavaType("int16"));
      // 2 bytes (unsigned)
      assertEquals("char", BuiltinTools.getBuiltinTypeJavaType("char"));
      // 4 bytes
      assertEquals("float", BuiltinTools.getBuiltinTypeJavaType("float32"));
      assertEquals("int", BuiltinTools.getBuiltinTypeJavaType("uint16"));
      assertEquals("int", BuiltinTools.getBuiltinTypeJavaType("int32"));
      assertEquals("int", BuiltinTools.getBuiltinTypeJavaType("uint32"));
      // 8 bytes
      assertEquals("double", BuiltinTools.getBuiltinTypeJavaType("float64"));
      assertEquals("long", BuiltinTools.getBuiltinTypeJavaType("int64"));
      assertEquals("long", BuiltinTools.getBuiltinTypeJavaType("uint64"));
      // Variable length strings
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
      // 1 bit
      assertEquals("IDLBoolSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("bool"));
      // 1 byte
      assertEquals("IDLByteSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("byte"));
      assertEquals("IDLByteSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("int8"));
      // 2 bytes
      assertEquals("IDLShortSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("uint8"));
      assertEquals("IDLShortSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("int16"));
      // 2 bytes (unsigned)
      assertEquals("IDLCharSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("char"));
      // 4 bytes
      assertEquals("IDLFloatSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("float32"));
      assertEquals("IDLIntSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("uint16"));
      assertEquals("IDLIntSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("int32"));
      assertEquals("IDLIntSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("uint32"));
      // 8 bytes
      assertEquals("IDLDoubleSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("float64"));
      assertEquals("IDLLongSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("int64"));
      assertEquals("IDLLongSequence", BuiltinTools.getBuiltinTypeIDLSequenceType("uint64"));
      // Variable length strings
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
      // 1 bit
      assertEquals("writeBoolean", BuiltinTools.getBuiltinCDRBufferWriteMethod("bool"));
      // 1 byte
      assertEquals("writeByte", BuiltinTools.getBuiltinCDRBufferWriteMethod("byte"));
      assertEquals("writeByte", BuiltinTools.getBuiltinCDRBufferWriteMethod("int8"));
      // 2 bytes
      assertEquals("writeShort", BuiltinTools.getBuiltinCDRBufferWriteMethod("uint8"));
      assertEquals("writeShort", BuiltinTools.getBuiltinCDRBufferWriteMethod("int16"));
      // 2 bytes (unsigned)
      assertEquals("writeChar", BuiltinTools.getBuiltinCDRBufferWriteMethod("char"));
      // 4 bytes
      assertEquals("writeFloat", BuiltinTools.getBuiltinCDRBufferWriteMethod("float32"));
      assertEquals("writeInt", BuiltinTools.getBuiltinCDRBufferWriteMethod("uint16"));
      assertEquals("writeInt", BuiltinTools.getBuiltinCDRBufferWriteMethod("int32"));
      assertEquals("writeInt", BuiltinTools.getBuiltinCDRBufferWriteMethod("uint32"));
      // 8 bytes
      assertEquals("writeDouble", BuiltinTools.getBuiltinCDRBufferWriteMethod("float64"));
      assertEquals("writeLong", BuiltinTools.getBuiltinCDRBufferWriteMethod("int64"));
      assertEquals("writeLong", BuiltinTools.getBuiltinCDRBufferWriteMethod("uint64"));
      // Variable length strings
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
      // 1 bit
      assertEquals("readBoolean", BuiltinTools.getBuiltinCDRBufferReadMethod("bool"));
      // 1 byte
      assertEquals("readByte", BuiltinTools.getBuiltinCDRBufferReadMethod("byte"));
      assertEquals("readByte", BuiltinTools.getBuiltinCDRBufferReadMethod("int8"));
      // 2 bytes
      assertEquals("readShort", BuiltinTools.getBuiltinCDRBufferReadMethod("uint8"));
      assertEquals("readShort", BuiltinTools.getBuiltinCDRBufferReadMethod("int16"));
      // 2 bytes (unsigned)
      assertEquals("readChar", BuiltinTools.getBuiltinCDRBufferReadMethod("char"));
      // 4 bytes
      assertEquals("readFloat", BuiltinTools.getBuiltinCDRBufferReadMethod("float32"));
      assertEquals("readInt", BuiltinTools.getBuiltinCDRBufferReadMethod("uint16"));
      assertEquals("readInt", BuiltinTools.getBuiltinCDRBufferReadMethod("int32"));
      assertEquals("readInt", BuiltinTools.getBuiltinCDRBufferReadMethod("uint32"));
      // 8 bytes
      assertEquals("readDouble", BuiltinTools.getBuiltinCDRBufferReadMethod("float64"));
      assertEquals("readLong", BuiltinTools.getBuiltinCDRBufferReadMethod("int64"));
      assertEquals("readLong", BuiltinTools.getBuiltinCDRBufferReadMethod("uint64"));
      // Variable length strings
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
