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
package us.ihmc.fastddsjava.cdr;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

import static us.ihmc.fastddsjava.natives.fastddsjava.*;

/**
 * A buffer wrapper for reading and writing data using Common Data Representation (CDR) encoding,
 * as specified by the OMG DDS-RTPS and CORBA specifications (XCDR1 / "classic" CDR, as used by ROS 2).
 * <p>
 * This class provides methods to serialize and deserialize primitive types, strings and primitive arrays according to
 * CDR encoding rules, including proper alignment and endianness handling. Alignment is relative to the end of the
 * 4-byte RTPS payload (encapsulation) header.
 * </p>
 * <ul>
 *    <li><b>Writing</b> always uses little endian ({@link #writePayloadHeader()} writes {@code CDR_LE}). Every write
 *    method grows the buffer on demand, so an underestimated {@code calculateSizeBytes} can never overflow it. Alignment
 *    padding is written as zero bytes.</li>
 *    <li><b>Reading</b> honours the byte order of the received encapsulation header (little or big endian). Every wire
 *    length is validated against the bytes remaining before the buffer's limit (see {@link #beginRead(int)}) before
 *    anything is allocated; malformed input throws {@link CDRDeserializationException}.</li>
 *    <li><b>Strings</b> are UTF-8 on the wire ({@code uint32 length} including the terminating NUL, the bytes, NUL), as
 *    produced by rclcpp/rclpy. Wide strings are UTF-16 code units, each written as a {@code uint32}, without a
 *    terminator, as produced by rmw_fastrtps.</li>
 * </ul>
 * All read and write methods are allocation-free once the buffer (and any destination {@link StringBuilder}) has grown to
 * its working size; only growing allocates, and error paths that throw. Destination builders created with
 * {@link #newStringBuilder(int)} (as generated messages and IDL string sequences do) also never allocate for the first
 * non-Latin-1 character they receive.
 *
 * @see <a href="https://www.omg.org/spec/DDSI-RTPS/2.3/PDF">DDS-RTPS 2.3 Specification</a>
 * @see <a href="https://www.omg.org/spec/CORBA/3.3/Interoperability/PDF">CORBA 3.3 Interoperability Specification</a>
 */
public final class CDRBuffer
{
   // RepresentationIdentifier, RepresentationOptions
   public static final byte[] PAYLOAD_HEADER = {0, 1, 0, 0};

   /** Position of the CDR stream origin: alignment is relative to the end of the 4-byte payload header. */
   private static final int ORIGIN = 4;
   /** The largest buffer this class will allocate (the practical maximum Java array size). */
   private static final int MAX_CAPACITY = Integer.MAX_VALUE - 8;
   /** Substitute written for an unpaired UTF-16 surrogate in a UTF-8 string (same as {@code String.getBytes(UTF_8)}). */
   private static final byte UTF8_UNPAIRED_SURROGATE_REPLACEMENT = '?';
   /** Substitute written for an unpaired UTF-16 surrogate in a wide string, and decoded for invalid UTF-8. */
   private static final char REPLACEMENT_CHARACTER = (char) 0xFFFD;
   /** A character outside Latin-1: appending it switches a JDK StringBuilder to two bytes per character for good. */
   private static final char UTF16_CODER_CHARACTER = (char) 0x0100;
   private static final int LITTLE_ENDIAN_VIEW = 0;
   private static final int BIG_ENDIAN_VIEW = 1;

   // What is read, for error messages: codes of CDRBufferErrors, which holds their names. This class has no string
   // constant at all and every exception message is built in CDRBufferErrors: HotSpot creates the string literals of a
   // class that are not resolved yet on the thread that first requests a C2 compilation of one of its methods.
   private static final int ENCAPSULATION_HEADER = CDRBufferErrors.ENCAPSULATION_HEADER;
   private static final int OCTET = CDRBufferErrors.OCTET;
   private static final int INT16 = CDRBufferErrors.INT16;
   private static final int INT32 = CDRBufferErrors.INT32;
   private static final int INT64 = CDRBufferErrors.INT64;
   private static final int FLOAT32 = CDRBufferErrors.FLOAT32;
   private static final int FLOAT64 = CDRBufferErrors.FLOAT64;
   private static final int BOOLEAN = CDRBufferErrors.BOOLEAN;
   private static final int STRING = CDRBufferErrors.STRING;
   private static final int WSTRING = CDRBufferErrors.WSTRING;
   private static final int SEQUENCE = CDRBufferErrors.SEQUENCE;
   private static final int OCTET_ARRAY = CDRBufferErrors.OCTET_ARRAY;
   private static final int BOOLEAN_ARRAY = CDRBufferErrors.BOOLEAN_ARRAY;
   private static final int CHAR_ARRAY = CDRBufferErrors.CHAR_ARRAY;
   private static final int INT16_ARRAY = CDRBufferErrors.INT16_ARRAY;
   private static final int UINT16_ARRAY = CDRBufferErrors.UINT16_ARRAY;
   private static final int INT32_ARRAY = CDRBufferErrors.INT32_ARRAY;
   private static final int INT64_ARRAY = CDRBufferErrors.INT64_ARRAY;
   private static final int FLOAT32_ARRAY = CDRBufferErrors.FLOAT32_ARRAY;
   private static final int FLOAT64_ARRAY = CDRBufferErrors.FLOAT64_ARRAY;
   private static final int ALIGNMENT_PADDING = CDRBufferErrors.ALIGNMENT_PADDING;

   static
   {
      // Now, during setup, rather than on a hot thread when C2 first compiles a JDK method of the read/write path
      JdkPreparation.prepare();
   }

   private ByteBuffer buffer;

   // Typed views of the buffer, starting at ORIGIN, created lazily per byte order and dropped whenever the buffer is
   // reallocated. They provide Android-safe bulk copies (relative bulk get/put) for primitive arrays.
   private final ShortBuffer[] shortViews = new ShortBuffer[2];
   private final IntBuffer[] intViews = new IntBuffer[2];
   private final LongBuffer[] longViews = new LongBuffer[2];
   private final FloatBuffer[] floatViews = new FloatBuffer[2];
   private final DoubleBuffer[] doubleViews = new DoubleBuffer[2];

   public CDRBuffer()
   {
      // Must be direct: JNI topicData*Buffer uses GetDirectBufferAddress.
      // Do not call ByteBuffer.array(); sequential get/put on this buffer is the supported path.
      buffer = ByteBuffer.allocateDirect(1);
   }

   /**
    * Returns the underlying CDR {@link ByteBuffer}.
    * <p>
    * The buffer is always direct ({@link ByteBuffer#isDirect()} is {@code true}).
    * JNI publish/subscribe paths use {@code GetDirectBufferAddress}, which has no heap
    * fallback, so callers must not replace this with a heap buffer or call
    * {@link ByteBuffer#array()}. Use sequential {@code get}/{@code put} on this buffer.
    * The buffer object is replaced whenever it grows, so do not cache it across writes.
    *
    * @return the live direct buffer; position, limit, and capacity are owned by this {@link CDRBuffer}
    */
   public ByteBuffer getBufferUnsafe()
   {
      return buffer;
   }

   /**
    * Ensures that at least {@code capacity} bytes can be written after the current position. Grows by powers of two
    * (keeping the content before the position, the position and the byte order) and resets the limit to the capacity.
    *
    * @param capacity the number of bytes required after the current position
    * @return true if the buffer was reallocated
    */
   public boolean ensureRemainingCapacity(int capacity)
   {
      if (capacity < 0)
      {
         throw CDRBufferErrors.negativeCapacity(capacity);
      }

      long requiredCapacity = (long) buffer.position() + capacity;

      if (buffer.capacity() < requiredCapacity)
      {
         if (requiredCapacity > MAX_CAPACITY)
         {
            throw CDRBufferErrors.cannotGrow(requiredCapacity);
         }

         int oldPosition = buffer.position();
         ByteOrder oldOrder = buffer.order();
         // Grow by powers of two to reduce repeated reallocations for variable-size payloads.
         long newCapacity = 1;
         while (newCapacity < requiredCapacity)
         {
            newCapacity <<= 1;
         }
         ByteBuffer newBuffer = ByteBuffer.allocateDirect((int) Math.min(newCapacity, MAX_CAPACITY));
         newBuffer.order(oldOrder);

         buffer.limit(oldPosition);
         buffer.position(0);
         newBuffer.put(buffer);
         newBuffer.position(oldPosition);

         buffer = newBuffer;
         invalidateViews();

         return true;
      }

      if (buffer.limit() != buffer.capacity())
      {
         buffer.limit(buffer.capacity());
      }

      return false;
   }

   /**
    * Sets the position to zero and the limit to the capacity, ready to write a new payload.
    */
   public void rewind()
   {
      buffer.limit(buffer.capacity());
      buffer.position(0);
   }

   /**
    * Prepares the buffer to deserialize a payload of {@code payloadSizeBytes} bytes that has been copied to the start of
    * the buffer: the position is set to zero and the limit to {@code payloadSizeBytes}, so every wire length is
    * validated against the real end of the payload instead of the buffer capacity.
    *
    * @param payloadSizeBytes the payload size including the 4-byte encapsulation header
    */
   public void beginRead(int payloadSizeBytes)
   {
      if (payloadSizeBytes < 0 || payloadSizeBytes > buffer.capacity())
      {
         throw CDRBufferErrors.payloadSizeOutsideCapacity(payloadSizeBytes, buffer.capacity());
      }

      buffer.limit(payloadSizeBytes);
      buffer.position(0);
   }

   public void writePayloadHeader()
   {
      ensureWritable(PAYLOAD_HEADER.length);
      buffer.put(PAYLOAD_HEADER);

      buffer.order(ByteOrder.LITTLE_ENDIAN);
   }

   public void readPayloadHeader()
   {
      requireReadable(4, ENCAPSULATION_HEADER);
      buffer.order(ByteOrder.BIG_ENDIAN); // BE for reading header
      // RepresentationIdentifier (encapsulation)
      short encapsulation = buffer.getShort();
      buffer.order(byteOrder(encapsulation));
      // RepresentationOptions
      buffer.getShort();
   }

   public void writeByte(byte value)
   {
      ensureWritable(1);
      buffer.put(value);
   }

   public byte readByte()
   {
      requireReadable(1, OCTET);
      return buffer.get();
   }

   /**
    * Writes a ROS 2 {@code char} (IDL {@code uint8}). Only the low 8 bits of {@code value} are written.
    */
   public void writeChar(char value)
   {
      writeByte((byte) value);
   }

   /**
    * Reads a ROS 2 {@code char} (IDL {@code uint8}) as a value in the range [0, 255].
    */
   public char readChar()
   {
      return (char) (readByte() & 0xFF);
   }

   /**
    * Writes a single IDL {@code wchar} as a 4-byte value (Fast-CDR 1.x / ROS 2 Humble layout).
    * Note that {@code wchar} cannot be declared in .msg files; wide strings use {@link #writeWString(StringBuilder)}.
    */
   public void writeWchar(int value)
   {
      writeInt(value);
   }

   /**
    * Reads a single IDL {@code wchar} written by {@link #writeWchar(int)}.
    */
   public int readWchar()
   {
      return readInt();
   }

   public void writeShort(short value)
   {
      alignForWrite(2, 2);
      buffer.putShort(value);
   }

   public short readShort()
   {
      alignForRead(2, 2, INT16);
      return buffer.getShort();
   }

   /** Reads a CDR uint16 value as an unsigned 16-bit integer in the range [0, 65535]. */
   public int readUInt16()
   {
      return Short.toUnsignedInt(readShort());
   }

   /** Writes a CDR uint16 value. Values outside [0, 65535] are truncated to 16 bits. */
   public void writeUInt16(int value)
   {
      writeShort((short) value);
   }

   public void writeInt(int value)
   {
      alignForWrite(4, 4);
      buffer.putInt(value);
   }

   public int readInt()
   {
      alignForRead(4, 4, INT32);
      return buffer.getInt();
   }

   public void writeLong(long value)
   {
      alignForWrite(8, 8);
      buffer.putLong(value);
   }

   public long readLong()
   {
      alignForRead(8, 8, INT64);
      return buffer.getLong();
   }

   public void writeFloat(float value)
   {
      alignForWrite(4, 4);
      buffer.putFloat(value);
   }

   public float readFloat()
   {
      alignForRead(4, 4, FLOAT32);
      return buffer.getFloat();
   }

   public void writeDouble(double value)
   {
      alignForWrite(8, 8);
      buffer.putDouble(value);
   }

   public double readDouble()
   {
      alignForRead(8, 8, FLOAT64);
      return buffer.getDouble();
   }

   public void writeBoolean(boolean value)
   {
      ensureWritable(1);
      buffer.put((byte) (value ? 1 : 0));
   }

   /**
    * Reads a CDR boolean. Like Fast-CDR, only 0 and 1 are accepted.
    *
    * @throws CDRDeserializationException for any other value
    */
   public boolean readBoolean()
   {
      requireReadable(1, BOOLEAN);
      return toBoolean(buffer.get());
   }

   /**
    * Writes the 1-byte {@code uint8 structure_needs_at_least_one_member} that ROS 2 adds to messages without fields
    * (for example {@code std_msgs/Empty} or constants-only messages).
    */
   public void writeStructureDummyMember()
   {
      writeByte((byte) 0);
   }

   /**
    * Reads the 1-byte {@code structure_needs_at_least_one_member} of a message without fields. The value is ignored.
    * A payload that ends before the member (as sent by jros2 1.5 and older) is accepted.
    */
   public void readStructureDummyMember()
   {
      if (buffer.hasRemaining())
      {
         buffer.get();
      }
   }

   // ========== Strings ==========

   /**
    * Creates an empty {@link StringBuilder} for CDR string content (message string fields, sequence elements, read
    * destinations) that never allocates while its length stays within {@code capacity}, whatever characters it holds.
    * <p>
    * A {@code new StringBuilder(capacity)} of the JDK (compact strings) stores Latin-1 content in one byte per character
    * and reallocates its storage the first time a character above U+00FF is appended, even below its capacity. Such a
    * builder that only ever held ASCII would allocate on the first received string with, e.g., a {@code '€'}. The builder
    * returned here is switched to two bytes per character once, at creation, so that afterwards only growing beyond its
    * capacity allocates. (On Android, StringBuilder always stores two bytes per character.)
    * </p>
    *
    * @param capacity the initial capacity in UTF-16 code units
    * @return a new, empty builder
    */
   public static StringBuilder newStringBuilder(int capacity)
   {
      StringBuilder builder = new StringBuilder(Math.max(capacity, 1));
      builder.append(UTF16_CODER_CHARACTER);
      builder.setLength(0);
      return builder;
   }

   /**
    * Creates a {@link StringBuilder} like {@link #newStringBuilder(int)} that holds {@code content}, with the capacity of
    * {@code new StringBuilder(content)}.
    *
    * @param content the initial content
    * @return a new builder holding {@code content}
    */
   public static StringBuilder newStringBuilder(CharSequence content)
   {
      StringBuilder builder = newStringBuilder(content.length() + 16);
      builder.append(content);
      return builder;
   }

   /**
    * Returns the number of bytes {@code value} occupies when encoded as UTF-8, not including the NUL terminator.
    * An unpaired surrogate counts as one byte (it is written as {@code '?'}).
    *
    * @param value the characters to measure
    * @return the UTF-8 encoded length in bytes
    */
   public static int utf8Length(CharSequence value)
   {
      return utf8Length(value, false);
   }

   private static int utf8Length(CharSequence value, boolean rejectNul)
   {
      int length = value.length();
      long utf8Length = length;

      for (int i = 0; i < length; ++i)
      {
         char c = value.charAt(i);

         if (c == '\0' && rejectNul)
         {
            throw CDRBufferErrors.nulCharacter(i);
         }

         if (c >= 0x80)
         {
            if (c < 0x800)
            {
               utf8Length += 1;
            }
            else if (Character.isHighSurrogate(c) && i + 1 < length && Character.isLowSurrogate(value.charAt(i + 1)))
            {
               utf8Length += 2; // 4 bytes for 2 chars
               ++i;
            }
            else if (!Character.isSurrogate(c))
            {
               utf8Length += 2;
            }
         }
      }

      if (utf8Length > MAX_CAPACITY)
      {
         throw CDRBufferErrors.stringTooLong(utf8Length);
      }

      return (int) utf8Length;
   }

   /**
    * Reads a CDR string into {@code destination}, replacing its content.
    * <p>
    * The wire format is {@code uint32 length} followed by {@code length} bytes. A length of 0 is accepted and yields an
    * empty string; one trailing NUL is dropped if present. The bytes are decoded as UTF-8; each malformed sequence is
    * replaced by U+FFFD (like rclpy).
    * </p>
    *
    * @throws CDRDeserializationException if the length is negative or exceeds the remaining payload
    */
   public void readString(StringBuilder destination)
   {
      if (destination == null)
      {
         throw CDRBufferErrors.nullDestination(false);
      }

      int length = readLength(STRING);

      destination.setLength(0);

      if (length == 0)
      {
         return;
      }

      requireReadable(length, STRING);

      int start = buffer.position();
      int end = start + length;
      int dataEnd = buffer.get(end - 1) == 0 ? end - 1 : end;

      decodeUtf8(start, dataEnd, destination);

      buffer.position(end);
   }

   /**
    * Writes {@code value} as a CDR string: {@code uint32} UTF-8 length + 1, the UTF-8 bytes, and a NUL terminator.
    *
    * @throws IllegalArgumentException if {@code value} contains a NUL character (like rclcpp)
    */
   public void writeString(StringBuilder value)
   {
      writeString((CharSequence) value);
   }

   /**
    * Writes {@code value} as a CDR string: {@code uint32} UTF-8 length + 1, the UTF-8 bytes, and a NUL terminator.
    * An unpaired surrogate is written as {@code '?'}.
    *
    * @throws IllegalArgumentException if {@code value} contains a NUL character (like rclcpp)
    */
   public void writeString(CharSequence value)
   {
      int length = value.length();
      int utf8Length = utf8Length(value, true);
      if (utf8Length > MAX_CAPACITY - 16)
      {
         throw CDRBufferErrors.stringTooLong(utf8Length);
      }

      alignForWrite(4, 4 + utf8Length + 1);
      buffer.putInt(utf8Length + 1); // Length of string + null terminator

      for (int i = 0; i < length; ++i)
      {
         char c = value.charAt(i);

         if (c < 0x80)
         {
            buffer.put((byte) c);
         }
         else if (c < 0x800)
         {
            buffer.put((byte) (0xC0 | (c >> 6)));
            buffer.put((byte) (0x80 | (c & 0x3F)));
         }
         else if (Character.isSurrogate(c))
         {
            char low;
            if (Character.isHighSurrogate(c) && i + 1 < length && Character.isLowSurrogate(low = value.charAt(i + 1)))
            {
               int codePoint = Character.toCodePoint(c, low);
               buffer.put((byte) (0xF0 | (codePoint >> 18)));
               buffer.put((byte) (0x80 | ((codePoint >> 12) & 0x3F)));
               buffer.put((byte) (0x80 | ((codePoint >> 6) & 0x3F)));
               buffer.put((byte) (0x80 | (codePoint & 0x3F)));
               ++i;
            }
            else
            {
               buffer.put(UTF8_UNPAIRED_SURROGATE_REPLACEMENT);
            }
         }
         else
         {
            buffer.put((byte) (0xE0 | (c >> 12)));
            buffer.put((byte) (0x80 | ((c >> 6) & 0x3F)));
            buffer.put((byte) (0x80 | (c & 0x3F)));
         }
      }

      // Add null terminator
      buffer.put((byte) 0);
   }

   /**
    * Reads a CDR wide string into {@code destination}, replacing its content: {@code uint32} count of UTF-16 code units,
    * then one {@code uint32} per code unit, no terminator.
    *
    * @throws CDRDeserializationException if the count exceeds the remaining payload or a code unit is above 0xFFFF
    */
   public void readWString(StringBuilder destination)
   {
      if (destination == null)
      {
         throw CDRBufferErrors.nullDestination(true);
      }

      int charLength = readLength(WSTRING);
      if ((long) charLength * 4 > buffer.remaining())
      {
         throw CDRBufferErrors.wideStringExceedsPayload(charLength, buffer.remaining(), buffer.position());
      }

      // Clear the destination and read all characters into it
      destination.setLength(0);
      for (int i = 0; i < charLength; ++i)
      {
         int wchar = buffer.getInt(); // already 4-byte aligned after the length
         if ((wchar & 0xFFFF0000) != 0)
         {
            throw CDRBufferErrors.wideCharacterTooLarge(wchar);
         }
         destination.append((char) wchar);
      }

      // wstring has no null terminator
   }

   /**
    * Writes {@code value} as a CDR wide string: {@code uint32} count of UTF-16 code units followed by each code unit as a
    * {@code uint32}, without terminator (the rmw_fastrtps format). Characters outside the BMP are written as surrogate
    * pairs; an unpaired surrogate is written as U+FFFD.
    */
   public void writeWString(StringBuilder value)
   {
      int charLength = value.length();
      if (charLength > (MAX_CAPACITY - 16) / 4)
      {
         throw CDRBufferErrors.wideStringTooLong(charLength);
      }

      alignForWrite(4, 4 + 4 * charLength);
      buffer.putInt(charLength);

      for (int i = 0; i < charLength; i++)
      {
         char c = value.charAt(i);
         if (Character.isHighSurrogate(c))
         {
            if (i + 1 < charLength && Character.isLowSurrogate(value.charAt(i + 1)))
            {
               buffer.putInt(c);
               buffer.putInt(value.charAt(++i));
            }
            else
            {
               buffer.putInt(REPLACEMENT_CHARACTER);
            }
         }
         else if (Character.isLowSurrogate(c))
         {
            buffer.putInt(REPLACEMENT_CHARACTER);
         }
         else
         {
            buffer.putInt(c); // char -> int is zero-extended
         }
      }

      // wstring has no null terminator
   }

   // ========== Sequences ==========

   /**
    * Writes the {@code uint32} element count of a sequence.
    */
   public void writeSequenceLength(int length)
   {
      if (length < 0)
      {
         throw CDRBufferErrors.negativeSequenceLength(length);
      }
      writeInt(length);
   }

   /**
    * Reads the {@code uint32} element count of a sequence and validates it before anything is allocated.
    *
    * @param minElementSizeBytes the minimum number of bytes one element occupies on the wire (1, 2, 4 or 8). For
    *                            primitive elements this is also their alignment.
    * @param maxSize             the upper bound of the sequence ({@code Integer.MAX_VALUE} when unbounded)
    * @return the element count
    * @throws CDRDeserializationException if the count is negative, exceeds {@code maxSize}, or cannot fit in the remaining
    *                                     payload
    */
   public int readSequenceLength(int minElementSizeBytes, int maxSize)
   {
      int length = readLength(SEQUENCE);

      if (length > maxSize)
      {
         throw CDRBufferErrors.sequenceExceedsBound(length, maxSize);
      }

      if (length > 0)
      {
         int position = buffer.position();
         long requiredBytes = padding(position, minElementSizeBytes) + (long) length * minElementSizeBytes;
         int remaining = buffer.limit() - position;
         if (requiredBytes > remaining)
         {
            throw CDRBufferErrors.sequenceExceedsPayload(length, requiredBytes, remaining, position);
         }
      }

      return length;
   }

   // ========== Bulk primitive arrays ==========

   /** Writes {@code src} as consecutive octets. */
   public void writeByteArray(byte[] src)
   {
      writeByteArray(src, 0, src.length);
   }

   /** Writes {@code length} elements of {@code src} starting at {@code offset} as consecutive octets. */
   public void writeByteArray(byte[] src, int offset, int length)
   {
      checkRange(src.length, offset, length);
      ensureWritable(length);
      buffer.put(src, offset, length);
   }

   /** Fills {@code dst} with consecutive octets. */
   public void readByteArray(byte[] dst)
   {
      readByteArray(dst, 0, dst.length);
   }

   /** Reads {@code length} consecutive octets into {@code dst} starting at {@code offset}. */
   public void readByteArray(byte[] dst, int offset, int length)
   {
      checkRange(dst.length, offset, length);
      requireReadable(length, OCTET_ARRAY);
      buffer.get(dst, offset, length);
   }

   /** Writes {@code src} as consecutive CDR booleans (0 or 1). */
   public void writeBooleanArray(boolean[] src)
   {
      writeBooleanArray(src, 0, src.length);
   }

   /** Writes {@code length} elements of {@code src} starting at {@code offset} as consecutive CDR booleans (0 or 1). */
   public void writeBooleanArray(boolean[] src, int offset, int length)
   {
      checkRange(src.length, offset, length);
      ensureWritable(length);
      for (int i = offset, end = offset + length; i < end; ++i)
      {
         buffer.put((byte) (src[i] ? 1 : 0));
      }
   }

   /** Fills {@code dst} with consecutive CDR booleans. */
   public void readBooleanArray(boolean[] dst)
   {
      readBooleanArray(dst, 0, dst.length);
   }

   /**
    * Reads {@code length} consecutive CDR booleans into {@code dst} starting at {@code offset}.
    *
    * @throws CDRDeserializationException if a value other than 0 or 1 is found
    */
   public void readBooleanArray(boolean[] dst, int offset, int length)
   {
      checkRange(dst.length, offset, length);
      requireReadable(length, BOOLEAN_ARRAY);
      for (int i = offset, end = offset + length; i < end; ++i)
      {
         dst[i] = toBoolean(buffer.get());
      }
   }

   /** Writes the low 8 bits of each char of {@code src} (ROS 2 {@code char} is {@code uint8}). */
   public void writeCharArray(char[] src)
   {
      writeCharArray(src, 0, src.length);
   }

   /** Writes the low 8 bits of {@code length} chars of {@code src} starting at {@code offset}. */
   public void writeCharArray(char[] src, int offset, int length)
   {
      checkRange(src.length, offset, length);
      ensureWritable(length);
      for (int i = offset, end = offset + length; i < end; ++i)
      {
         buffer.put((byte) src[i]);
      }
   }

   /** Fills {@code dst} with consecutive ROS 2 {@code char} ({@code uint8}) values in the range [0, 255]. */
   public void readCharArray(char[] dst)
   {
      readCharArray(dst, 0, dst.length);
   }

   /** Reads {@code length} consecutive ROS 2 {@code char} ({@code uint8}) values into {@code dst} starting at {@code offset}. */
   public void readCharArray(char[] dst, int offset, int length)
   {
      checkRange(dst.length, offset, length);
      requireReadable(length, CHAR_ARRAY);
      for (int i = offset, end = offset + length; i < end; ++i)
      {
         dst[i] = (char) (buffer.get() & 0xFF);
      }
   }

   /** Writes {@code src} as consecutive int16 values (aligned once). */
   public void writeShortArray(short[] src)
   {
      writeShortArray(src, 0, src.length);
   }

   /** Writes {@code length} elements of {@code src} starting at {@code offset} as consecutive int16 values. */
   public void writeShortArray(short[] src, int offset, int length)
   {
      checkRange(src.length, offset, length);
      if (length == 0)
         return;
      alignForWrite(2, checkedByteCount(length, 2));
      int position = buffer.position();
      if (position >= ORIGIN)
      {
         ShortBuffer view = shortView();
         view.position((position - ORIGIN) >> 1);
         view.put(src, offset, length);
         buffer.position(position + (length << 1));
      }
      else
      {
         for (int i = offset, end = offset + length; i < end; ++i)
            buffer.putShort(src[i]);
      }
   }

   /** Fills {@code dst} with consecutive int16 values. */
   public void readShortArray(short[] dst)
   {
      readShortArray(dst, 0, dst.length);
   }

   /** Reads {@code length} consecutive int16 values into {@code dst} starting at {@code offset}. */
   public void readShortArray(short[] dst, int offset, int length)
   {
      checkRange(dst.length, offset, length);
      if (length == 0)
         return;
      alignForRead(2, checkedByteCount(length, 2), INT16_ARRAY);
      int position = buffer.position();
      if (position >= ORIGIN)
      {
         ShortBuffer view = shortView();
         view.position((position - ORIGIN) >> 1);
         view.get(dst, offset, length);
         buffer.position(position + (length << 1));
      }
      else
      {
         for (int i = offset, end = offset + length; i < end; ++i)
            dst[i] = buffer.getShort();
      }
   }

   /** Writes {@code src} as consecutive uint16 values (the low 16 bits of each element, aligned once). */
   public void writeUInt16Array(int[] src)
   {
      writeUInt16Array(src, 0, src.length);
   }

   /** Writes the low 16 bits of {@code length} elements of {@code src} starting at {@code offset} as consecutive uint16 values. */
   public void writeUInt16Array(int[] src, int offset, int length)
   {
      checkRange(src.length, offset, length);
      if (length == 0)
         return;
      alignForWrite(2, checkedByteCount(length, 2));
      for (int i = offset, end = offset + length; i < end; ++i)
      {
         buffer.putShort((short) src[i]);
      }
   }

   /** Fills {@code dst} with consecutive uint16 values in the range [0, 65535]. */
   public void readUInt16Array(int[] dst)
   {
      readUInt16Array(dst, 0, dst.length);
   }

   /** Reads {@code length} consecutive uint16 values (range [0, 65535]) into {@code dst} starting at {@code offset}. */
   public void readUInt16Array(int[] dst, int offset, int length)
   {
      checkRange(dst.length, offset, length);
      if (length == 0)
         return;
      alignForRead(2, checkedByteCount(length, 2), UINT16_ARRAY);
      for (int i = offset, end = offset + length; i < end; ++i)
      {
         dst[i] = buffer.getShort() & 0xFFFF;
      }
   }

   /** Writes {@code src} as consecutive int32/uint32 values (aligned once). */
   public void writeIntArray(int[] src)
   {
      writeIntArray(src, 0, src.length);
   }

   /** Writes {@code length} elements of {@code src} starting at {@code offset} as consecutive int32/uint32 values. */
   public void writeIntArray(int[] src, int offset, int length)
   {
      checkRange(src.length, offset, length);
      if (length == 0)
         return;
      alignForWrite(4, checkedByteCount(length, 4));
      int position = buffer.position();
      if (position >= ORIGIN)
      {
         IntBuffer view = intView();
         view.position((position - ORIGIN) >> 2);
         view.put(src, offset, length);
         buffer.position(position + (length << 2));
      }
      else
      {
         for (int i = offset, end = offset + length; i < end; ++i)
            buffer.putInt(src[i]);
      }
   }

   /** Fills {@code dst} with consecutive int32/uint32 values. */
   public void readIntArray(int[] dst)
   {
      readIntArray(dst, 0, dst.length);
   }

   /** Reads {@code length} consecutive int32/uint32 values into {@code dst} starting at {@code offset}. */
   public void readIntArray(int[] dst, int offset, int length)
   {
      checkRange(dst.length, offset, length);
      if (length == 0)
         return;
      alignForRead(4, checkedByteCount(length, 4), INT32_ARRAY);
      int position = buffer.position();
      if (position >= ORIGIN)
      {
         IntBuffer view = intView();
         view.position((position - ORIGIN) >> 2);
         view.get(dst, offset, length);
         buffer.position(position + (length << 2));
      }
      else
      {
         for (int i = offset, end = offset + length; i < end; ++i)
            dst[i] = buffer.getInt();
      }
   }

   /** Writes {@code src} as consecutive int64/uint64 values (aligned once). */
   public void writeLongArray(long[] src)
   {
      writeLongArray(src, 0, src.length);
   }

   /** Writes {@code length} elements of {@code src} starting at {@code offset} as consecutive int64/uint64 values. */
   public void writeLongArray(long[] src, int offset, int length)
   {
      checkRange(src.length, offset, length);
      if (length == 0)
         return;
      alignForWrite(8, checkedByteCount(length, 8));
      int position = buffer.position();
      if (position >= ORIGIN)
      {
         LongBuffer view = longView();
         view.position((position - ORIGIN) >> 3);
         view.put(src, offset, length);
         buffer.position(position + (length << 3));
      }
      else
      {
         for (int i = offset, end = offset + length; i < end; ++i)
            buffer.putLong(src[i]);
      }
   }

   /** Fills {@code dst} with consecutive int64/uint64 values. */
   public void readLongArray(long[] dst)
   {
      readLongArray(dst, 0, dst.length);
   }

   /** Reads {@code length} consecutive int64/uint64 values into {@code dst} starting at {@code offset}. */
   public void readLongArray(long[] dst, int offset, int length)
   {
      checkRange(dst.length, offset, length);
      if (length == 0)
         return;
      alignForRead(8, checkedByteCount(length, 8), INT64_ARRAY);
      int position = buffer.position();
      if (position >= ORIGIN)
      {
         LongBuffer view = longView();
         view.position((position - ORIGIN) >> 3);
         view.get(dst, offset, length);
         buffer.position(position + (length << 3));
      }
      else
      {
         for (int i = offset, end = offset + length; i < end; ++i)
            dst[i] = buffer.getLong();
      }
   }

   /** Writes {@code src} as consecutive float32 values (aligned once). */
   public void writeFloatArray(float[] src)
   {
      writeFloatArray(src, 0, src.length);
   }

   /** Writes {@code length} elements of {@code src} starting at {@code offset} as consecutive float32 values. */
   public void writeFloatArray(float[] src, int offset, int length)
   {
      checkRange(src.length, offset, length);
      if (length == 0)
         return;
      alignForWrite(4, checkedByteCount(length, 4));
      int position = buffer.position();
      if (position >= ORIGIN)
      {
         FloatBuffer view = floatView();
         view.position((position - ORIGIN) >> 2);
         view.put(src, offset, length);
         buffer.position(position + (length << 2));
      }
      else
      {
         for (int i = offset, end = offset + length; i < end; ++i)
            buffer.putFloat(src[i]);
      }
   }

   /** Fills {@code dst} with consecutive float32 values. */
   public void readFloatArray(float[] dst)
   {
      readFloatArray(dst, 0, dst.length);
   }

   /** Reads {@code length} consecutive float32 values into {@code dst} starting at {@code offset}. */
   public void readFloatArray(float[] dst, int offset, int length)
   {
      checkRange(dst.length, offset, length);
      if (length == 0)
         return;
      alignForRead(4, checkedByteCount(length, 4), FLOAT32_ARRAY);
      int position = buffer.position();
      if (position >= ORIGIN)
      {
         FloatBuffer view = floatView();
         view.position((position - ORIGIN) >> 2);
         view.get(dst, offset, length);
         buffer.position(position + (length << 2));
      }
      else
      {
         for (int i = offset, end = offset + length; i < end; ++i)
            dst[i] = buffer.getFloat();
      }
   }

   /** Writes {@code src} as consecutive float64 values (aligned once). */
   public void writeDoubleArray(double[] src)
   {
      writeDoubleArray(src, 0, src.length);
   }

   /** Writes {@code length} elements of {@code src} starting at {@code offset} as consecutive float64 values. */
   public void writeDoubleArray(double[] src, int offset, int length)
   {
      checkRange(src.length, offset, length);
      if (length == 0)
         return;
      alignForWrite(8, checkedByteCount(length, 8));
      int position = buffer.position();
      if (position >= ORIGIN)
      {
         DoubleBuffer view = doubleView();
         view.position((position - ORIGIN) >> 3);
         view.put(src, offset, length);
         buffer.position(position + (length << 3));
      }
      else
      {
         for (int i = offset, end = offset + length; i < end; ++i)
            buffer.putDouble(src[i]);
      }
   }

   /** Fills {@code dst} with consecutive float64 values. */
   public void readDoubleArray(double[] dst)
   {
      readDoubleArray(dst, 0, dst.length);
   }

   /** Reads {@code length} consecutive float64 values into {@code dst} starting at {@code offset}. */
   public void readDoubleArray(double[] dst, int offset, int length)
   {
      checkRange(dst.length, offset, length);
      if (length == 0)
         return;
      alignForRead(8, checkedByteCount(length, 8), FLOAT64_ARRAY);
      int position = buffer.position();
      if (position >= ORIGIN)
      {
         DoubleBuffer view = doubleView();
         view.position((position - ORIGIN) >> 3);
         view.get(dst, offset, length);
         buffer.position(position + (length << 3));
      }
      else
      {
         for (int i = offset, end = offset + length; i < end; ++i)
            dst[i] = buffer.getDouble();
      }
   }

   // ========== Alignment ==========

   /**
    * Advances the position to the next multiple of {@code byteBoundary} relative to the CDR origin (the end of the
    * payload header), as done before reading a value of that size.
    *
    * @param byteBoundary 1, 2, 4 or 8
    * @throws CDRDeserializationException if the padding would go past the limit
    */
   public void alignBuffer(int byteBoundary)
   {
      alignForRead(byteBoundary, 0, ALIGNMENT_PADDING);
   }

   public static int alignment(int currentAlignment, int bytes)
   {
      return (bytes - (currentAlignment % bytes)) & (bytes - 1);
   }

   public static ByteOrder byteOrder(short encapsulation)
   {
      return switch (encapsulation)
      {
         case CDR_LE, PL_CDR_LE -> ByteOrder.LITTLE_ENDIAN;
         case CDR_BE, PL_CDR_BE -> ByteOrder.BIG_ENDIAN;
         default -> throw CDRBufferErrors.unsupportedEncapsulation(encapsulation);
      };
   }

   // ========== Internals ==========

   /** Number of padding bytes needed at {@code position} to reach a multiple of {@code alignment} (a power of two) from the origin. */
   private static int padding(int position, int alignment)
   {
      return (ORIGIN - position) & (alignment - 1);
   }

   private static boolean toBoolean(byte value)
   {
      if (value == 0)
         return false;
      if (value == 1)
         return true;
      throw CDRBufferErrors.unexpectedBoolean(value);
   }

   private static void checkRange(int arrayLength, int offset, int length)
   {
      if (offset < 0 || length < 0 || offset > arrayLength - length)
      {
         throw CDRBufferErrors.outOfRange(offset, length, arrayLength);
      }
   }

   private static int checkedByteCount(int length, int elementSizeBytes)
   {
      long byteCount = (long) length * elementSizeBytes;
      if (byteCount > MAX_CAPACITY)
      {
         throw CDRBufferErrors.arrayTooLarge(length);
      }
      return (int) byteCount;
   }

   private void ensureWritable(int byteCount)
   {
      if (buffer.remaining() < byteCount)
      {
         ensureRemainingCapacity(byteCount);
      }
   }

   private void alignForWrite(int alignment, int byteCount)
   {
      int pad = padding(buffer.position(), alignment);
      ensureWritable(pad + byteCount);
      for (; pad > 0; --pad)
      {
         buffer.put((byte) 0);
      }
   }

   private void alignForRead(int alignment, int byteCount, int what)
   {
      int position = buffer.position();
      int pad = padding(position, alignment);
      if ((long) pad + byteCount > buffer.limit() - position)
      {
         throw truncated(what, (long) pad + byteCount);
      }
      if (pad != 0)
      {
         buffer.position(position + pad);
      }
   }

   private void requireReadable(int byteCount, int what)
   {
      if (buffer.remaining() < byteCount)
      {
         throw truncated(what, byteCount);
      }
   }

   private CDRDeserializationException truncated(int what, long byteCount)
   {
      return CDRBufferErrors.truncated(what, byteCount, buffer.remaining(), buffer.position());
   }

   /** Reads a {@code uint32} length prefix, rejecting values that do not fit in a non-negative int. */
   private int readLength(int what)
   {
      int length = readInt();
      if (length < 0)
      {
         throw CDRBufferErrors.lengthTooLarge(what, length);
      }
      return length;
   }

   /**
    * Decodes the UTF-8 bytes in [start, end) of the buffer into {@code destination}. Each maximal malformed subsequence
    * (as defined by the Unicode standard, chapter 3.9) is replaced by U+FFFD, which is what rclpy and
    * {@code new String(bytes, UTF_8)} produce.
    */
   private void decodeUtf8(int start, int end, StringBuilder destination)
   {
      ByteBuffer b = buffer;
      int i = start;
      while (i < end)
      {
         int b0 = b.get(i) & 0xFF;

         if (b0 < 0x80)
         {
            destination.append((char) b0);
            ++i;
            continue;
         }

         int needed;
         int codePoint;
         int lowerBound = 0x80; // valid range of the second byte
         int upperBound = 0xBF;

         if (b0 >= 0xC2 && b0 <= 0xDF)
         {
            needed = 1;
            codePoint = b0 & 0x1F;
         }
         else if (b0 >= 0xE0 && b0 <= 0xEF)
         {
            needed = 2;
            codePoint = b0 & 0x0F;
            if (b0 == 0xE0)
               lowerBound = 0xA0; // no overlong encodings
            else if (b0 == 0xED)
               upperBound = 0x9F; // no surrogates
         }
         else if (b0 >= 0xF0 && b0 <= 0xF4)
         {
            needed = 3;
            codePoint = b0 & 0x07;
            if (b0 == 0xF0)
               lowerBound = 0x90; // no overlong encodings
            else if (b0 == 0xF4)
               upperBound = 0x8F; // not above U+10FFFF
         }
         else
         {
            // 0x80..0xC1 or 0xF5..0xFF can never start a sequence
            destination.append(REPLACEMENT_CHARACTER);
            ++i;
            continue;
         }

         int j = i + 1;
         boolean valid = true;
         for (int k = 0; k < needed; ++k, ++j)
         {
            if (j >= end)
            {
               valid = false;
               break;
            }

            int bk = b.get(j) & 0xFF;
            int lower = k == 0 ? lowerBound : 0x80;
            int upper = k == 0 ? upperBound : 0xBF;
            if (bk < lower || bk > upper)
            {
               valid = false;
               break;
            }

            codePoint = (codePoint << 6) | (bk & 0x3F);
         }

         if (!valid)
         {
            // Replace the maximal subpart [i, j) and resume at the offending byte
            destination.append(REPLACEMENT_CHARACTER);
            i = j;
            continue;
         }

         if (codePoint < 0x10000)
         {
            destination.append((char) codePoint);
         }
         else
         {
            destination.append(Character.highSurrogate(codePoint));
            destination.append(Character.lowSurrogate(codePoint));
         }
         i = j;
      }
   }

   private int orderIndex()
   {
      return buffer.order() == ByteOrder.LITTLE_ENDIAN ? LITTLE_ENDIAN_VIEW : BIG_ENDIAN_VIEW;
   }

   /** A duplicate of the buffer positioned at the CDR origin, with the current byte order, to create typed views from. */
   private ByteBuffer viewSource()
   {
      ByteBuffer duplicate = buffer.duplicate();
      duplicate.order(buffer.order());
      duplicate.limit(duplicate.capacity());
      duplicate.position(Math.min(ORIGIN, duplicate.capacity()));
      return duplicate;
   }

   private ShortBuffer shortView()
   {
      int index = orderIndex();
      if (shortViews[index] == null)
         shortViews[index] = viewSource().asShortBuffer();
      return shortViews[index];
   }

   private IntBuffer intView()
   {
      int index = orderIndex();
      if (intViews[index] == null)
         intViews[index] = viewSource().asIntBuffer();
      return intViews[index];
   }

   private LongBuffer longView()
   {
      int index = orderIndex();
      if (longViews[index] == null)
         longViews[index] = viewSource().asLongBuffer();
      return longViews[index];
   }

   private FloatBuffer floatView()
   {
      int index = orderIndex();
      if (floatViews[index] == null)
         floatViews[index] = viewSource().asFloatBuffer();
      return floatViews[index];
   }

   private DoubleBuffer doubleView()
   {
      int index = orderIndex();
      if (doubleViews[index] == null)
         doubleViews[index] = viewSource().asDoubleBuffer();
      return doubleViews[index];
   }

   private void invalidateViews()
   {
      for (int i = 0; i < 2; ++i)
      {
         shortViews[i] = null;
         intViews[i] = null;
         longViews[i] = null;
         floatViews[i] = null;
         doubleViews[i] = null;
      }
   }
}
