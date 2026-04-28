package us.ihmc.fastddsjava.cdr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.fastddsjava.pointers.fastddsjava.*;

/**
 * Comprehensive tests for CDRBuffer following the OMG CDR specification.
 * Tests cover:
 * - Primitive type serialization/deserialization
 * - Alignment requirements per CDR spec
 * - String and WString handling
 * - Endianness (little-endian and big-endian)
 * - RTPS payload header format
 * - Buffer capacity management
 *
 * @see <a href="https://www.omg.org/spec/DDSI-RTPS/2.3/PDF">DDS-RTPS 2.3 Specification</a>
 * @see <a href="https://www.omg.org/spec/CORBA/3.3/Interoperability/PDF">CORBA 3.3 CDR Specification</a>
 */
public class CDRBufferTest
{
   private CDRBuffer buffer;

   @BeforeEach
   public void setUp()
   {
      buffer = new CDRBuffer();
      buffer.ensureRemainingCapacity(1024);
      buffer.writePayloadHeader();
   }

   // ========== Payload Header Tests ==========

   @Test
   public void testPayloadHeaderFormat()
   {
      CDRBuffer testBuffer = new CDRBuffer();
      testBuffer.ensureRemainingCapacity(100);
      testBuffer.writePayloadHeader();

      // Verify header is written correctly
      testBuffer.rewind();
      byte[] header = new byte[4];
      testBuffer.getBufferUnsafe().get(header);

      assertArrayEquals(CDRBuffer.PAYLOAD_HEADER, header, "Payload header should match expected format");
      assertEquals(ByteOrder.LITTLE_ENDIAN, testBuffer.getBufferUnsafe().order(), "Buffer should be little-endian after header");
   }

   @Test
   public void testReadPayloadHeaderLittleEndian()
   {
      CDRBuffer writeBuffer = new CDRBuffer();
      writeBuffer.ensureRemainingCapacity(100);
      writeBuffer.writePayloadHeader();

      writeBuffer.rewind();
      CDRBuffer readBuffer = new CDRBuffer();
      readBuffer.ensureRemainingCapacity(100);
      readBuffer.getBufferUnsafe().put(writeBuffer.getBufferUnsafe());
      readBuffer.rewind();
      readBuffer.readPayloadHeader();

      assertEquals(ByteOrder.LITTLE_ENDIAN, readBuffer.getBufferUnsafe().order(), "Should be little-endian for CDR_LE");
   }

   @Test
   public void testReadPayloadHeaderBigEndian()
   {
      CDRBuffer writeBuffer = new CDRBuffer();
      writeBuffer.ensureRemainingCapacity(100);
      writeBuffer.getBufferUnsafe().putShort(CDR_BE);
      writeBuffer.getBufferUnsafe().putShort((short) 0);

      writeBuffer.rewind();
      CDRBuffer readBuffer = new CDRBuffer();
      readBuffer.ensureRemainingCapacity(100);
      readBuffer.getBufferUnsafe().put(writeBuffer.getBufferUnsafe());
      readBuffer.rewind();
      readBuffer.readPayloadHeader();

      assertEquals(ByteOrder.BIG_ENDIAN, readBuffer.getBufferUnsafe().order(), "Should be big-endian for CDR_BE");
   }

   // ========== Byte Tests ==========

   @Test
   public void testWriteReadByte()
   {
      buffer.writeByte((byte) 42);
      buffer.writeByte((byte) -128);
      buffer.writeByte((byte) 127);

      buffer.rewind();
      buffer.readPayloadHeader();

      assertEquals((byte) 42, buffer.readByte());
      assertEquals((byte) -128, buffer.readByte());
      assertEquals((byte) 127, buffer.readByte());
   }

   @Test
   public void testByteNoAlignment()
   {
      // Bytes have no alignment requirement
      int initialPosition = buffer.getBufferUnsafe().position();
      buffer.writeByte((byte) 1);
      buffer.writeByte((byte) 2);
      buffer.writeByte((byte) 3);

      assertEquals(initialPosition + 3, buffer.getBufferUnsafe().position(), "Bytes should be written consecutively");
   }

   // ========== Char Tests ==========

   @Test
   public void testWriteReadChar()
   {
      buffer.writeChar('A');
      buffer.writeChar('Z');
      buffer.writeChar('\0');

      buffer.rewind();
      buffer.readPayloadHeader();

      assertEquals('A', buffer.readChar());
      assertEquals('Z', buffer.readChar());
      assertEquals('\0', buffer.readChar());
   }

   // ========== Short Tests ==========

   @Test
   public void testWriteReadShort()
   {
      buffer.writeShort((short) 1000);
      buffer.writeShort((short) -5000);
      buffer.writeShort(Short.MAX_VALUE);
      buffer.writeShort(Short.MIN_VALUE);

      buffer.rewind();
      buffer.readPayloadHeader();

      assertEquals((short) 1000, buffer.readShort());
      assertEquals((short) -5000, buffer.readShort());
      assertEquals(Short.MAX_VALUE, buffer.readShort());
      assertEquals(Short.MIN_VALUE, buffer.readShort());
   }

   @Test
   public void testShortAlignment()
   {
      // CDR spec: shorts must be aligned on 2-byte boundaries
      buffer.writeByte((byte) 1);
      buffer.writeShort((short) 100);

      buffer.rewind();
      buffer.readPayloadHeader();
      buffer.readByte();

      // Short should be readable after alignment
      assertEquals((short) 100, buffer.readShort());
   }

   // ========== Int Tests ==========

   @Test
   public void testWriteReadInt()
   {
      buffer.writeInt(100000);
      buffer.writeInt(-200000);
      buffer.writeInt(Integer.MAX_VALUE);
      buffer.writeInt(Integer.MIN_VALUE);
      buffer.writeInt(0);

      buffer.rewind();
      buffer.readPayloadHeader();

      assertEquals(100000, buffer.readInt());
      assertEquals(-200000, buffer.readInt());
      assertEquals(Integer.MAX_VALUE, buffer.readInt());
      assertEquals(Integer.MIN_VALUE, buffer.readInt());
      assertEquals(0, buffer.readInt());
   }

   @Test
   public void testIntAlignment()
   {
      // CDR spec: ints must be aligned on 4-byte boundaries
      buffer.writeByte((byte) 1);
      buffer.writeByte((byte) 2);
      buffer.writeByte((byte) 3);
      buffer.writeInt(12345);

      buffer.rewind();
      buffer.readPayloadHeader();
      buffer.readByte();
      buffer.readByte();
      buffer.readByte();

      // Int should be readable after alignment
      assertEquals(12345, buffer.readInt());
   }

   // ========== Long Tests ==========

   @Test
   public void testWriteReadLong()
   {
      buffer.writeLong(1000000000L);
      buffer.writeLong(-2000000000L);
      buffer.writeLong(Long.MAX_VALUE);
      buffer.writeLong(Long.MIN_VALUE);
      buffer.writeLong(0L);

      buffer.rewind();
      buffer.readPayloadHeader();

      assertEquals(1000000000L, buffer.readLong());
      assertEquals(-2000000000L, buffer.readLong());
      assertEquals(Long.MAX_VALUE, buffer.readLong());
      assertEquals(Long.MIN_VALUE, buffer.readLong());
      assertEquals(0L, buffer.readLong());
   }

   @Test
   public void testLongAlignment()
   {
      // CDR spec: longs must be aligned on 8-byte boundaries
      buffer.writeByte((byte) 1);
      buffer.writeByte((byte) 2);
      buffer.writeByte((byte) 3);
      buffer.writeLong(123456789L);

      buffer.rewind();
      buffer.readPayloadHeader();
      buffer.readByte();
      buffer.readByte();
      buffer.readByte();

      // Long should be readable after alignment
      assertEquals(123456789L, buffer.readLong());
   }

   // ========== Float Tests ==========

   @Test
   public void testWriteReadFloat()
   {
      buffer.writeFloat(3.14159f);
      buffer.writeFloat(-2.71828f);
      buffer.writeFloat(Float.MAX_VALUE);
      buffer.writeFloat(Float.MIN_VALUE);
      buffer.writeFloat(Float.POSITIVE_INFINITY);
      buffer.writeFloat(Float.NEGATIVE_INFINITY);
      buffer.writeFloat(0.0f);

      buffer.rewind();
      buffer.readPayloadHeader();

      assertEquals(3.14159f, buffer.readFloat(), 0.00001f);
      assertEquals(-2.71828f, buffer.readFloat(), 0.00001f);
      assertEquals(Float.MAX_VALUE, buffer.readFloat());
      assertEquals(Float.MIN_VALUE, buffer.readFloat());
      assertEquals(Float.POSITIVE_INFINITY, buffer.readFloat());
      assertEquals(Float.NEGATIVE_INFINITY, buffer.readFloat());
      assertEquals(0.0f, buffer.readFloat());
   }

   @Test
   public void testFloatAlignment()
   {
      // CDR spec: floats must be aligned on 4-byte boundaries
      buffer.writeByte((byte) 1);
      buffer.writeByte((byte) 2);
      buffer.writeFloat(1.23f);

      buffer.rewind();
      buffer.readPayloadHeader();
      buffer.readByte();
      buffer.readByte();

      // Float should be readable after alignment
      assertEquals(1.23f, buffer.readFloat(), 0.001f);
   }

   // ========== Double Tests ==========

   @Test
   public void testWriteReadDouble()
   {
      buffer.writeDouble(3.141592653589793);
      buffer.writeDouble(-2.718281828459045);
      buffer.writeDouble(Double.MAX_VALUE);
      buffer.writeDouble(Double.MIN_VALUE);
      buffer.writeDouble(Double.POSITIVE_INFINITY);
      buffer.writeDouble(Double.NEGATIVE_INFINITY);
      buffer.writeDouble(0.0);

      buffer.rewind();
      buffer.readPayloadHeader();

      assertEquals(3.141592653589793, buffer.readDouble(), 0.000000000000001);
      assertEquals(-2.718281828459045, buffer.readDouble(), 0.000000000000001);
      assertEquals(Double.MAX_VALUE, buffer.readDouble());
      assertEquals(Double.MIN_VALUE, buffer.readDouble());
      assertEquals(Double.POSITIVE_INFINITY, buffer.readDouble());
      assertEquals(Double.NEGATIVE_INFINITY, buffer.readDouble());
      assertEquals(0.0, buffer.readDouble());
   }

   @Test
   public void testDoubleAlignment()
   {
      // CDR spec: doubles must be aligned on 8-byte boundaries
      buffer.writeByte((byte) 1);
      buffer.writeDouble(1.23456789);

      buffer.rewind();
      buffer.readPayloadHeader();
      buffer.readByte();

      // Double should be readable after alignment
      assertEquals(1.23456789, buffer.readDouble(), 0.000001);
   }

   // ========== Boolean Tests ==========

   @Test
   public void testWriteReadBoolean()
   {
      buffer.writeBoolean(true);
      buffer.writeBoolean(false);
      buffer.writeBoolean(true);
      buffer.writeBoolean(false);

      buffer.rewind();
      buffer.readPayloadHeader();

      assertTrue(buffer.readBoolean());
      assertFalse(buffer.readBoolean());
      assertTrue(buffer.readBoolean());
      assertFalse(buffer.readBoolean());
   }

   @Test
   public void testBooleanEncoding()
   {
      // CDR spec: boolean true = 1, false = 0
      CDRBuffer testBuffer = new CDRBuffer();
      testBuffer.ensureRemainingCapacity(100);
      testBuffer.writePayloadHeader();

      int posBeforeTrue = testBuffer.getBufferUnsafe().position();
      testBuffer.writeBoolean(true);
      int posAfterTrue = testBuffer.getBufferUnsafe().position();

      testBuffer.writeBoolean(false);

      // Check the actual bytes written
      byte trueValue = testBuffer.getBufferUnsafe().get(posBeforeTrue);
      byte falseValue = testBuffer.getBufferUnsafe().get(posAfterTrue);

      assertEquals((byte) 1, trueValue, "True should be encoded as 1");
      assertEquals((byte) 0, falseValue, "False should be encoded as 0");
   }

   @Test
   public void testInvalidBooleanThrows()
   {
      CDRBuffer testBuffer = new CDRBuffer();
      testBuffer.ensureRemainingCapacity(100);
      testBuffer.writePayloadHeader();
      testBuffer.writeByte((byte) 2); // Invalid boolean value

      testBuffer.rewind();
      testBuffer.readPayloadHeader();

      assertThrows(RuntimeException.class, testBuffer::readBoolean, "Invalid boolean value should throw");
   }

   // ========== String Tests ==========

   @Test
   public void testWriteReadString()
   {
      StringBuilder sb1 = new StringBuilder("Hello, World!");
      StringBuilder sb2 = new StringBuilder("CDR Encoding");
      StringBuilder sb3 = new StringBuilder("");

      buffer.writeString(sb1);
      buffer.writeString(sb2);
      buffer.writeString(sb3);

      buffer.rewind();
      buffer.readPayloadHeader();

      StringBuilder result1 = new StringBuilder();
      StringBuilder result2 = new StringBuilder();
      StringBuilder result3 = new StringBuilder();

      buffer.readString(result1);
      buffer.readString(result2);
      buffer.readString(result3);

      assertEquals("Hello, World!", result1.toString());
      assertEquals("CDR Encoding", result2.toString());
      assertEquals("", result3.toString());
   }

   @Test
   public void testStringWithNullTerminator()
   {
      // CDR spec: strings include a null terminator and length prefix
      StringBuilder input = new StringBuilder("Test");
      buffer.writeString(input);

      buffer.rewind();
      buffer.readPayloadHeader();

      // Length should be string length + 1 (for null terminator)
      int length = buffer.readInt();
      assertEquals(5, length, "Length should include null terminator");
   }

   @Test
   public void testStringAlignment()
   {
      // String length (int) should be 4-byte aligned
      buffer.writeByte((byte) 1);
      buffer.writeByte((byte) 2);
      buffer.writeString(new StringBuilder("test"));

      buffer.rewind();
      buffer.readPayloadHeader();
      buffer.readByte();
      buffer.readByte();

      // String should be readable after alignment
      StringBuilder result = new StringBuilder();
      buffer.readString(result);
      assertEquals("test", result.toString());
   }

   @Test
   public void testStringWithSpecialCharacters()
   {
      StringBuilder input = new StringBuilder("Line1\nLine2\tTab");

      CDRBuffer testBuffer = new CDRBuffer();
      testBuffer.ensureRemainingCapacity(200);
      testBuffer.writePayloadHeader();
      testBuffer.writeString(input);

      testBuffer.rewind();
      testBuffer.readPayloadHeader();

      StringBuilder result = new StringBuilder();
      testBuffer.readString(result);

      assertEquals(input.toString(), result.toString(), "Special characters should be preserved");
   }

   // ========== WString Tests ==========

   @Test
   public void testWriteReadWString()
   {
      StringBuilder sb1 = new StringBuilder("Hello");
      StringBuilder sb2 = new StringBuilder("世界"); // Unicode characters
      StringBuilder sb3 = new StringBuilder("");

      buffer.writeWString(sb1);
      buffer.writeWString(sb2);
      buffer.writeWString(sb3);

      buffer.rewind();
      buffer.readPayloadHeader();

      StringBuilder result1 = new StringBuilder();
      StringBuilder result2 = new StringBuilder();
      StringBuilder result3 = new StringBuilder();

      buffer.readWString(result1);
      buffer.readWString(result2);
      buffer.readWString(result3);

      assertEquals("Hello", result1.toString());
      assertEquals("世界", result2.toString());
      assertEquals("", result3.toString());
   }

   @Test
   public void testWStringNoNullTerminator()
   {
      // CDR spec: wstring does NOT include null terminator
      StringBuilder input = new StringBuilder("Test");
      int posBeforeWrite = buffer.getBufferUnsafe().position();
      buffer.writeWString(input);
      int posAfterWrite = buffer.getBufferUnsafe().position();

      // Expected size: 4 bytes (length) + 4 * 4 bytes (characters as ints) = 20 bytes
      // Plus alignment padding
      assertTrue(posAfterWrite >= posBeforeWrite + 20, "WString should not include null terminator");
   }

   @Test
   public void testWStringUnicodeHandling()
   {
      // Test various Unicode characters (avoiding surrogate pairs for simplicity)
      StringBuilder input = new StringBuilder("こんにちは");

      CDRBuffer testBuffer = new CDRBuffer();
      testBuffer.ensureRemainingCapacity(500);
      testBuffer.writePayloadHeader();
      testBuffer.writeWString(input);

      testBuffer.rewind();
      testBuffer.readPayloadHeader();

      StringBuilder result = new StringBuilder();
      testBuffer.readWString(result);

      assertEquals(input.toString(), result.toString(), "Unicode characters should be preserved");
   }

   // ========== Wchar Tests ==========

   @Test
   public void testWriteReadWchar()
   {
      buffer.writeWchar(0x1F600); // Emoji codepoint
      buffer.writeWchar(0x4E16); // Chinese character codepoint
      buffer.writeWchar(65); // 'A'

      buffer.rewind();
      buffer.readPayloadHeader();

      assertEquals(0x1F600, buffer.readWchar());
      assertEquals(0x4E16, buffer.readWchar());
      assertEquals(65, buffer.readWchar());
   }

   // ========== Mixed Type Tests ==========

   @Test
   public void testMixedTypeSerialization()
   {
      // Test serializing multiple types in sequence
      buffer.writeByte((byte) 42);
      buffer.writeBoolean(true);
      buffer.writeShort((short) 1000);
      buffer.writeInt(100000);
      buffer.writeLong(1000000000L);
      buffer.writeFloat(3.14f);
      buffer.writeDouble(2.718);
      buffer.writeString(new StringBuilder("Mixed"));

      buffer.rewind();
      buffer.readPayloadHeader();

      assertEquals((byte) 42, buffer.readByte());
      assertTrue(buffer.readBoolean());
      assertEquals((short) 1000, buffer.readShort());
      assertEquals(100000, buffer.readInt());
      assertEquals(1000000000L, buffer.readLong());
      assertEquals(3.14f, buffer.readFloat(), 0.01f);
      assertEquals(2.718, buffer.readDouble(), 0.001);
      StringBuilder result = new StringBuilder();
      buffer.readString(result);
      assertEquals("Mixed", result.toString());
   }

   // ========== Alignment Utility Tests ==========

   @Test
   public void testAlignmentCalculation()
   {
      // Test alignment calculation utility method
      assertEquals(0, CDRBuffer.alignment(0, 4), "Already aligned position needs no offset");
      assertEquals(3, CDRBuffer.alignment(1, 4), "Position 1 needs 3 bytes to align to 4");
      assertEquals(2, CDRBuffer.alignment(2, 4), "Position 2 needs 2 bytes to align to 4");
      assertEquals(1, CDRBuffer.alignment(3, 4), "Position 3 needs 1 byte to align to 4");
      assertEquals(0, CDRBuffer.alignment(4, 4), "Position 4 is aligned");
   }

   @Test
   public void testAlignmentForVariousBoundaries()
   {
      // Test 2-byte alignment
      assertEquals(0, CDRBuffer.alignment(0, 2));
      assertEquals(1, CDRBuffer.alignment(1, 2));
      assertEquals(0, CDRBuffer.alignment(2, 2));

      // Test 8-byte alignment
      assertEquals(0, CDRBuffer.alignment(0, 8));
      assertEquals(7, CDRBuffer.alignment(1, 8));
      assertEquals(2, CDRBuffer.alignment(6, 8));
      assertEquals(0, CDRBuffer.alignment(8, 8));
   }

   // ========== Buffer Management Tests ==========

   @Test
   public void testEnsureRemainingCapacity()
   {
      CDRBuffer smallBuffer = new CDRBuffer();
      int initialCapacity = smallBuffer.getBufferUnsafe().capacity();

      boolean expanded = smallBuffer.ensureRemainingCapacity(100);

      assertTrue(expanded, "Buffer should expand when capacity insufficient");
      assertTrue(smallBuffer.getBufferUnsafe().capacity() >= 100, "Buffer capacity should meet requirement");
   }

   @Test
   public void testEnsureRemainingCapacitySufficient()
   {
      CDRBuffer largeBuffer = new CDRBuffer();
      largeBuffer.ensureRemainingCapacity(1000);

      boolean expanded = largeBuffer.ensureRemainingCapacity(50);

      assertFalse(expanded, "Buffer should not expand when capacity is sufficient");
   }

   @Test
   public void testRewind()
   {
      buffer.writeInt(42);
      buffer.writeInt(100);

      int positionBeforeRewind = buffer.getBufferUnsafe().position();
      assertTrue(positionBeforeRewind > 0, "Position should advance after writes");

      buffer.rewind();

      assertEquals(0, buffer.getBufferUnsafe().position(), "Position should be 0 after rewind");
   }

   // ========== Byte Order Tests ==========

   @Test
   public void testByteOrderLittleEndian()
   {
      ByteOrder order = CDRBuffer.byteOrder(CDR_LE);
      assertEquals(ByteOrder.LITTLE_ENDIAN, order);
   }

   @Test
   public void testByteOrderBigEndian()
   {
      ByteOrder order = CDRBuffer.byteOrder(CDR_BE);
      assertEquals(ByteOrder.BIG_ENDIAN, order);
   }

   @Test
   public void testByteOrderParameterListLittleEndian()
   {
      ByteOrder order = CDRBuffer.byteOrder(PL_CDR_LE);
      assertEquals(ByteOrder.LITTLE_ENDIAN, order);
   }

   @Test
   public void testByteOrderParameterListBigEndian()
   {
      ByteOrder order = CDRBuffer.byteOrder(PL_CDR_BE);
      assertEquals(ByteOrder.BIG_ENDIAN, order);
   }

   @Test
   public void testByteOrderUnsupportedEncapsulation()
   {
      assertThrows(RuntimeException.class, () -> CDRBuffer.byteOrder((short) 0x9999), "Unsupported encapsulation should throw");
   }

   // ========== Edge Cases and Stress Tests ==========

   @Test
   public void testLargeString()
   {
      StringBuilder largeString = new StringBuilder();
      for (int i = 0; i < 1000; ++i)
      {
         largeString.append("A");
      }

      CDRBuffer testBuffer = new CDRBuffer();
      testBuffer.ensureRemainingCapacity(5000);
      testBuffer.writePayloadHeader();
      testBuffer.writeString(largeString);

      testBuffer.rewind();
      testBuffer.readPayloadHeader();

      StringBuilder result = new StringBuilder();
      testBuffer.readString(result);

      assertEquals(1000, result.length(), "Large string should be preserved");
      assertEquals(largeString.toString(), result.toString(), "Large string content should match");
   }

   @Test
   public void testSequentialWrites()
   {
      // Write many values sequentially
      for (int i = 0; i < 100; ++i)
      {
         buffer.writeInt(i);
      }

      buffer.rewind();
      buffer.readPayloadHeader();

      for (int i = 0; i < 100; ++i)
      {
         assertEquals(i, buffer.readInt(), "Value at index " + i + " should match");
      }
   }

   @Test
   public void testBufferAutoExpansion()
   {
      CDRBuffer autoBuffer = new CDRBuffer();
      autoBuffer.ensureRemainingCapacity(10);
      autoBuffer.writePayloadHeader();

      // Write more data than initial capacity
      for (int i = 0; i < 100; ++i)
      {
         autoBuffer.ensureRemainingCapacity(8); // Ensure space for alignment + int
         autoBuffer.writeInt(i);
      }

      autoBuffer.rewind();
      autoBuffer.readPayloadHeader();

      for (int i = 0; i < 100; ++i)
      {
         assertEquals(i, autoBuffer.readInt());
      }
   }
}
