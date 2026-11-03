/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
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
import us.ihmc.jros2.parser.field.InterfaceField;
import us.ihmc.jros2.parser.field.InterfaceFieldParsingException;
import us.ihmc.jros2.parser.util.DefaultValueTools;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parser regressions: valid .msg lines that were silently dropped or rejected before, the default-value semantics of
 * rosidl_adapter, and clear errors for invalid lines.
 */
public class InterfaceFieldParserTest
{
   private static MsgContext parse(String schema) throws InterfaceFieldParsingException
   {
      return MsgParser.parseMsg(schema, "test_msgs/Test");
   }

   private static InterfaceField single(String line) throws InterfaceFieldParsingException
   {
      MsgContext context = parse(line);
      assertEquals(1, context.getFields().size(), "fields of: " + line);
      return context.getFields().values().iterator().next();
   }

   private static InterfaceFieldParsingException rejected(String line)
   {
      return assertThrows(InterfaceFieldParsingException.class, () -> parse(line), line);
   }

   @Test
   public void testTabsBetweenTypeAndName() throws InterfaceFieldParsingException
   {
      MsgContext context = parse("int32\tx\nint32 \t y  \t # tab-separated\n\tfloat64\tz\t1.5\n");
      assertEquals(List.of("x", "y", "z"), List.copyOf(context.getFields().keySet()));
      assertEquals("tab-separated", context.getFields().get("y").getTrailingComment());
      assertEquals("1.5", context.getFields().get("z").getDefaultValueJava());
   }

   @Test
   public void testBoundedStringDefaults() throws InterfaceFieldParsingException
   {
      MsgContext context = parse("string<=5 s5 \"abc\"\nwstring<=4 w \"ab\"\nstring<=3[2] sarr2 [\"ab\", \"cd\"]\nint32 after\n");
      assertEquals(List.of("s5", "w", "sarr2", "after"), List.copyOf(context.getFields().keySet()));

      InterfaceField s5 = context.getFields().get("s5");
      assertEquals(5, s5.getStringLength());
      assertTrue(s5.isStringUpperBounded());
      assertEquals("\"abc\"", s5.getDefaultValueJava());
      assertEquals(List.of("\"ab\"", "\"cd\""), context.getFields().get("sarr2").getDefaultValueJavaElements());

      // Like rosidl_adapter, a default longer than the bound is invalid
      rejected("string<=2 s \"abc\"");
      rejected("string<=2[1] s [\"abc\"]");
   }

   @Test
   public void testDoubleUnderscoreOnlyMattersInTheFieldName() throws InterfaceFieldParsingException
   {
      assertEquals("\"a__b\"", single("string s \"a__b\"").getDefaultValueJava());
      InterfaceField constant = single("string S=\"a__b\"");
      assertEquals("\"a__b\"", constant.getConstantValueJava());
      assertEquals("S", constant.getJavaName());

      assertTrue(rejected("int32 a__b").getMessage().contains("__"));
      assertTrue(rejected("int32 ab_").getMessage().contains("_"));
      rejected("int32 _ab");
      rejected("int32 1ab");
      rejected("int32 a-b");
   }

   @Test
   public void testBoolDefaultsAreCaseInsensitive() throws InterfaceFieldParsingException
   {
      for (String value : new String[] {"true", "True", "TRUE", "1"})
         assertEquals("true", single("bool b " + value).getDefaultValueJava(), value);
      for (String value : new String[] {"false", "False", "FALSE", "0"})
         assertEquals("false", single("bool b " + value).getDefaultValueJava(), value);
      rejected("bool b 2");
      rejected("bool b yes");
      assertEquals(List.of("true", "false", "true"), single("bool[3] b [True, false, 1]").getDefaultValueJavaElements());
   }

   @Test
   public void testStringDefaultQuoting() throws InterfaceFieldParsingException
   {
      assertEquals("\"it's\"", single("string s2 'it\\'s'").getDefaultValueJava());
      assertEquals("\"say \\\"hi\\\"\"", single("string s \"say \\\"hi\\\"\"").getDefaultValueJava());
      // Backslashes outside an escaped quote are literal (no C escapes in .msg)
      assertEquals("\"tab\\\\there\"", single("string s1 \"tab\\there\"").getDefaultValueJava());
      assertEquals("\"\\\\\\\"\\\"\\\"\"", single("string s \\\"\"\"").getDefaultValueJava());
      // Unquoted values keep inner quotes, leading/trailing whitespace is dropped
      assertEquals("\"Hello \\\"World\\\"\"", single("string s Hello \"World\"").getDefaultValueJava());
      assertEquals("\"  padded  \"", single("string s \"  padded  \"").getDefaultValueJava());
      // Non-ASCII is escaped in the generated Java source
      assertEquals("\"caf\\u00e9\"", single("string s4 café").getDefaultValueJava());
      rejected("string s \"a\"b\"");
   }

   @Test
   public void testHashInsideQuotesStartsAComment() throws InterfaceFieldParsingException
   {
      // rosidl_adapter treats everything from the first '#' as a comment, even inside quotes: string z "#x" has the
      // default value "" (the remaining '"' is stripped like Python's value[1:-1]) in rclcpp / rclpy, and so in jros2
      InterfaceField field = single("string z \"#notacomment\"");
      assertEquals("\"\"", field.getDefaultValueJava());
      assertEquals("notacomment\"", field.getTrailingComment());

      assertEquals("\"\\\"http://x\"", single("string url \"http://x#anchor\"").getDefaultValueJava());
   }

   @Test
   public void testIntegerDefaults() throws InterfaceFieldParsingException
   {
      assertEquals("(byte) 31", single("byte c 0x1f").getDefaultValueJava());
      assertEquals("(char) 65", single("char d 65").getDefaultValueJava());
      assertEquals("(byte) -16", single("int8 e -0x10").getDefaultValueJava());
      assertEquals("5", single("uint16 f 0b101").getDefaultValueJava());
      assertEquals("(byte) 200", single("uint8 u 200").getDefaultValueJava());
      assertEquals("(short) -2000", single("int16 y -2000").getDefaultValueJava());
      assertEquals("0xEE6B2800", single("uint32 big 4000000000").getDefaultValueJava());
      assertEquals("2147483647", single("uint32 max_int 2147483647").getDefaultValueJava());
      assertEquals("5000000000L", single("int64 l 5_000_000_000").getDefaultValueJava());
      assertEquals("0xFFFFFFFFFFFFFFFFL", single("uint64 m 18446744073709551615").getDefaultValueJava());
      assertEquals("8L", single("uint64 o 0o10").getDefaultValueJava());
      assertEquals("10", single("int32 leading_zero 010").getDefaultValueJava());

      rejected("uint8 u 256");
      rejected("int8 i -129");
      rejected("byte b -1");
      rejected("uint32 u -1");
      rejected("int32 i 1.5");
      rejected("int32 i abc");
      rejected("int32 i 1__0");

      // uint16 sequences store shorts
      assertEquals(List.of("(short) 65535", "(short) 1"), single("uint16[] s [65535, 1]").getDefaultValueJavaElements());
      assertEquals(List.of("65535", "1"), single("uint16[2] s [65535, 1]").getDefaultValueJavaElements());
   }

   @Test
   public void testFloatDefaults() throws InterfaceFieldParsingException
   {
      assertEquals("(float) 1.0", single("float32 g 1").getDefaultValueJava());
      assertEquals("0.001", single("float64 h 1e-3").getDefaultValueJava());
      assertEquals("1.0E20", single("float64 h3 1e20").getDefaultValueJava());
      assertEquals("java.lang.Double.POSITIVE_INFINITY", single("float64 i inf").getDefaultValueJava());
      assertEquals("(float) java.lang.Double.NEGATIVE_INFINITY", single("float32 i -Infinity").getDefaultValueJava());
      assertEquals("java.lang.Double.NaN", single("float64 n nan").getDefaultValueJava());
      rejected("float64 f 1,5");
      rejected("float64 f 0x10");
   }

   @Test
   public void testArrayDefaults() throws InterfaceFieldParsingException
   {
      assertEquals(List.of("1", "-2", "3"), single("int32[3] arr [1, -2, 3]").getDefaultValueJavaElements());
      assertEquals(List.of("1.0", "2.5"), single("float64[] farr [1, 2.5]").getDefaultValueJavaElements());
      assertEquals(List.of("\"a,b\"", "\"c\"", "\"d\""), single("string[] sarr [\"a,b\", 'c', d]").getDefaultValueJavaElements());
      assertTrue(single("uint8[] empty []").getDefaultValueJavaElements().isEmpty());
      assertTrue(single("int32[] samples [-200, -100, 0, 100, 200]").isDefaultValueArray());

      rejected("int32[3] arr [1, 2]"); // fixed size needs exactly N
      rejected("int32[<=2] arr [1, 2, 3]"); // bounded needs at most N
      rejected("int32[] arr 1, 2"); // brackets required
      rejected("string[] s [\"a]"); // unterminated quote
      rejected("string[] s [, \"a\"]");
   }

   @Test
   public void testConstants() throws InterfaceFieldParsingException
   {
      InterfaceField constant = single("uint8 DEBUG = 200");
      assertEquals("200", constant.getConstantValue());
      assertEquals("(byte) 200", constant.getConstantValueJava());
      assertEquals("\"foo\"", single("string FOO='foo'").getConstantValue());
      assertEquals("(float) 1.5", single("float32 F=1.5").getConstantValueJava());

      rejected("int32[2] A=[1, 2]");
      rejected("string<=3 S=\"x\"");
      rejected("std_msgs/Header H={}");
      rejected("int32 A=");
   }

   @Test
   public void testInvalidLinesAreRejectedNotDropped()
   {
      rejected("int32");
      rejected("int32[<=] x");
      rejected("int32[0] x");
      rejected("string<=0 s");
      rejected("int32<=5 x");
      rejected("pkg/sub/Type x");
      rejected("time stamp");
      rejected("duration d");
      rejected("std_msgs/Header header {}");
      rejected("in$t32 x");
      assertTrue(rejected("time stamp").getMessage().contains("builtin_interfaces/Time"));
      InterfaceFieldParsingException duplicate = assertThrows(InterfaceFieldParsingException.class, () -> parse("int32 x\nfloat64 x\n"));
      assertTrue(duplicate.getMessage().contains("Duplicate"));
   }

   @Test
   public void testTypesAndContainers() throws InterfaceFieldParsingException
   {
      MsgContext context = parse("""
                                       Type same_pkg
                                       other_pkg/Type qualified
                                       Type[2] arr
                                       Type[<=3] bseq
                                       Type[] useq
                                       wstring<=9[] quirk
                                       string<=6[<=4] i
                                       """);
      InterfaceField sameRef = context.getFields().get("same_pkg");
      assertEquals("test_msgs.Type", sameRef.getJavaType());
      assertEquals("other_pkg.Type", context.getFields().get("qualified").getJavaType());
      assertTrue(context.getFields().get("arr").isFixedSize());
      assertEquals(2, context.getFields().get("arr").getLength());
      assertTrue(context.getFields().get("bseq").isUpperBounded());
      assertEquals("IDLObjectSequence<test_msgs.Type>", context.getFields().get("useq").getJavaType());
      InterfaceField quirk = context.getFields().get("quirk");
      assertTrue(quirk.isUnbounded());
      assertEquals(9, quirk.getStringLength());
      InterfaceField bounded = context.getFields().get("i");
      assertEquals(6, bounded.getStringLength());
      assertEquals(4, bounded.getLength());
      assertTrue(bounded.isUpperBounded());
   }

   @Test
   public void testEmptyAndConstantOnlyStructures() throws InterfaceFieldParsingException
   {
      assertTrue(parse("").isEmptyStructure());
      assertTrue(parse("# comment only\n").isEmptyStructure());
      assertTrue(parse("int32 A=1\nstring B=\"x\"\n").isEmptyStructure());
      assertFalse(parse("int32 a\n").isEmptyStructure());
      assertEquals("test_msgs/msg/Test", parse("").getRosTypeName());
   }

   @Test
   public void testDefaultValueToolsDirectly()
   {
      assertEquals("\"\\n\\t\\\\\"", DefaultValueTools.toJavaStringLiteral("\n\t\\"));
      assertEquals("", DefaultValueTools.parseString("string", "\"", 0));
      assertEquals("x'y", DefaultValueTools.parseString("string", "\"x'y\"", 0));
      assertThrows(IllegalArgumentException.class, () -> DefaultValueTools.toJavaExpression("pkg/Type", "1", 0, false));
   }
}
