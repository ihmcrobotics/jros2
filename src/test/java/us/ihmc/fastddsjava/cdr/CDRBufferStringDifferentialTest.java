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
package us.ihmc.fastddsjava.cdr;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The all-ASCII paths of {@link CDRBuffer#writeString(StringBuilder)}, {@link CDRBuffer#readString(StringBuilder)} and
 * {@link CDRBuffer#utf8Length(StringBuilder)} must be invisible: for every input they do exactly what the general UTF-8 paths
 * do, which are checked here against a frozen copy of the character-by-character encoder and decoder (and, where it applies,
 * the JDK's), byte for byte. The inputs concentrate on what could separate the paths: characters at the UTF-8 boundaries
 * (0x7F/0x80, 0x7FF/0x800), surrogates, NUL, and every kind of them at the edges of the scratch chunks.
 */
public class CDRBufferStringDifferentialTest
{
   /** Lengths around every chunk size a scratch array could have, and the one it has. */
   private static final int[] LENGTHS = {0, 1, 2, 3, 7, 8, 9, 15, 16, 17, 31, 32, 33, 63, 64, 65, 127, 128, 129, 255, 256, 257, 511, 512, 513, 1023, 1024,
                                         1025, 2047, 2048, 2049, 8193};
   private static final char[] BOUNDARY_CHARACTERS = {0, 1, 0x7E, 0x7F, 0x80, 0x81, 0xFF, 0x100, 0x7FF, 0x800, 0x801, 0xD7FF, 0xD800, 0xDBFF, 0xDC00, 0xDFFF,
                                                      0xE000, 0xFFFD, 0xFFFF};
   private static final byte[][] MULTI_BYTE_AND_MALFORMED = {{(byte) 0xC3, (byte) 0xA9},
                                                             {(byte) 0xE2, (byte) 0x82, (byte) 0xAC},
                                                             {(byte) 0xF0, (byte) 0x9F, (byte) 0x98, (byte) 0x80},
                                                             {(byte) 0xC3},
                                                             {(byte) 0xE2, (byte) 0x82},
                                                             {(byte) 0xF0, (byte) 0x9F, (byte) 0x98},
                                                             {(byte) 0xFF},
                                                             {(byte) 0x80},
                                                             {(byte) 0xC0, (byte) 0x80},
                                                             {(byte) 0xED, (byte) 0xA0, (byte) 0x80},
                                                             {(byte) 0xF4, (byte) 0x90, (byte) 0x80, (byte) 0x80},
                                                             {0}};

   /** A CharSequence that is no StringBuilder or String: it can only take the general path. */
   private record PlainCharSequence(String value) implements CharSequence
   {
      @Override
      public int length()
      {
         return value.length();
      }

      @Override
      public char charAt(int index)
      {
         return value.charAt(index);
      }

      @Override
      public CharSequence subSequence(int start, int end)
      {
         return value.subSequence(start, end);
      }
   }

   // ========== Frozen reference: the general paths of CDRBuffer when the ASCII paths were added ==========

   private static byte[] referenceEncode(CharSequence value)
   {
      int length = value.length();
      byte[] out = new byte[length * 3];
      int n = 0;
      for (int i = 0; i < length; ++i)
      {
         char c = value.charAt(i);

         if (c < 0x80)
         {
            out[n++] = (byte) c;
         }
         else if (c < 0x800)
         {
            out[n++] = (byte) (0xC0 | (c >> 6));
            out[n++] = (byte) (0x80 | (c & 0x3F));
         }
         else if (Character.isSurrogate(c))
         {
            char low;
            if (Character.isHighSurrogate(c) && i + 1 < length && Character.isLowSurrogate(low = value.charAt(i + 1)))
            {
               int codePoint = Character.toCodePoint(c, low);
               out[n++] = (byte) (0xF0 | (codePoint >> 18));
               out[n++] = (byte) (0x80 | ((codePoint >> 12) & 0x3F));
               out[n++] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
               out[n++] = (byte) (0x80 | (codePoint & 0x3F));
               ++i;
            }
            else
            {
               out[n++] = '?';
            }
         }
         else
         {
            out[n++] = (byte) (0xE0 | (c >> 12));
            out[n++] = (byte) (0x80 | ((c >> 6) & 0x3F));
            out[n++] = (byte) (0x80 | (c & 0x3F));
         }
      }
      return Arrays.copyOf(out, n);
   }

   private static String referenceDecode(byte[] b, int start, int end)
   {
      StringBuilder destination = new StringBuilder();
      int i = start;
      while (i < end)
      {
         int b0 = b[i] & 0xFF;

         if (b0 < 0x80)
         {
            destination.append((char) b0);
            ++i;
            continue;
         }

         int needed;
         int codePoint;
         int lowerBound = 0x80;
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
               lowerBound = 0xA0;
            else if (b0 == 0xED)
               upperBound = 0x9F;
         }
         else if (b0 >= 0xF0 && b0 <= 0xF4)
         {
            needed = 3;
            codePoint = b0 & 0x07;
            if (b0 == 0xF0)
               lowerBound = 0x90;
            else if (b0 == 0xF4)
               upperBound = 0x8F;
         }
         else
         {
            destination.append('�');
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

            int bk = b[j] & 0xFF;
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
            destination.append('�');
            i = j;
            continue;
         }

         destination.appendCodePoint(codePoint);
         i = j;
      }
      return destination.toString();
   }

   // ========== Inputs ==========

   private static String ascii(int length)
   {
      StringBuilder value = new StringBuilder(length);
      for (int i = 0; i < length; i++)
         value.append((char) ('a' + i % 26));
      return value.toString();
   }

   private static byte[] concat(byte[]... parts)
   {
      int length = 0;
      for (byte[] part : parts)
         length += part.length;
      byte[] result = new byte[length];
      int offset = 0;
      for (byte[] part : parts)
      {
         System.arraycopy(part, 0, result, offset, part.length);
         offset += part.length;
      }
      return result;
   }

   private static String escape(CharSequence value)
   {
      StringBuilder builder = new StringBuilder(value.length() + ": ");
      for (int i = 0; i < Math.min(value.length(), 40); i++)
         builder.append(String.format("\\u%04x", (int) value.charAt(i)));
      return builder.toString();
   }

   private static String hex(byte[] value)
   {
      StringBuilder builder = new StringBuilder(value.length + ": ");
      for (int i = 0; i < Math.min(value.length, 40); i++)
         builder.append(String.format("%02x", value[i]));
      return builder.toString();
   }

   /** The whole buffer, also what is after the limit. */
   private static byte[] content(ByteBuffer buffer)
   {
      ByteBuffer all = buffer.duplicate();
      all.clear();
      byte[] content = new byte[all.capacity()];
      all.get(content);
      return content;
   }

   /** The strings of the write tests: one boundary character (and surrogate pairs) at the edges of every length. */
   private static List<String> writeInputs()
   {
      List<String> inputs = new ArrayList<>();
      String[] specials = new String[BOUNDARY_CHARACTERS.length + 6];
      for (int i = 0; i < BOUNDARY_CHARACTERS.length; i++)
         specials[i] = String.valueOf(BOUNDARY_CHARACTERS[i]);
      specials[BOUNDARY_CHARACTERS.length] = "😀"; // paired
      specials[BOUNDARY_CHARACTERS.length + 1] = "\uDE00\uD83D"; // swapped
      specials[BOUNDARY_CHARACTERS.length + 2] = "\uD83D😀"; // unpaired high, then a pair
      specials[BOUNDARY_CHARACTERS.length + 3] = "\uD83Dx"; // high followed by ASCII
      specials[BOUNDARY_CHARACTERS.length + 4] = "\uDE00x";
      specials[BOUNDARY_CHARACTERS.length + 5] = "é€😀"; // 2-, 3- and 4-byte
      for (int length : LENGTHS)
      {
         inputs.add(ascii(length));
         for (String special : specials)
         {
            for (int position : new int[] {0, 1, length / 2, length - 1, length})
            {
               if (position >= 0 && position <= length)
                  inputs.add(ascii(position) + special + ascii(length - position));
            }
         }
      }
      Random random = new Random(1234);
      for (int i = 0; i < 5000; i++)
      {
         int length = i % 50 == 0 ? random.nextInt(3000) : random.nextInt(80);
         double specialRate = i % 3 == 0 ? 0.0 : random.nextDouble() * random.nextDouble();
         StringBuilder value = new StringBuilder(length);
         for (int j = 0; j < length; j++)
         {
            value.append(random.nextDouble() < specialRate ? (char) random.nextInt(0x10000) : (char) (1 + random.nextInt(0x7F)));
         }
         inputs.add(value.toString());
      }
      return inputs;
   }

   // ========== Writing ==========

   private static CDRBuffer writerAt(int offset, int reserve)
   {
      CDRBuffer buffer = new CDRBuffer();
      if (reserve >= 0)
         buffer.ensureRemainingCapacity(reserve);
      ByteBuffer bytes = buffer.getBufferUnsafe();
      for (int i = 0; i < bytes.capacity(); i++)
         bytes.put(i, (byte) 0xA5); // to see padding and stray writes
      buffer.writePayloadHeader();
      for (int i = 0; i < offset; i++)
         buffer.writeByte((byte) 0x55);
      return buffer;
   }

   /** What a call did: its exception message, or the position, limit, capacity and the whole content of the buffer. */
   private static void assertSameWrite(String input, StringBuilder fast, CharSequence general, int offset, int reserve)
   {
      CDRBuffer fastBuffer = writerAt(offset, reserve);
      CDRBuffer generalBuffer = writerAt(offset, reserve);
      String fastFailure = null;
      String generalFailure = null;
      try
      {
         fastBuffer.writeString(fast);
      }
      catch (IllegalArgumentException | CDRDeserializationException e)
      {
         fastFailure = e.getClass().getName() + e.getMessage();
      }
      try
      {
         generalBuffer.writeString(general);
      }
      catch (IllegalArgumentException | CDRDeserializationException e)
      {
         generalFailure = e.getClass().getName() + e.getMessage();
      }

      String description = escape(input) + " offset " + offset + " reserve " + reserve;
      assertEquals(generalFailure, fastFailure, description);
      ByteBuffer fastBytes = fastBuffer.getBufferUnsafe();
      ByteBuffer generalBytes = generalBuffer.getBufferUnsafe();
      assertEquals(generalBytes.position(), fastBytes.position(), description);
      assertEquals(generalBytes.limit(), fastBytes.limit(), description);
      assertEquals(generalBytes.capacity(), fastBytes.capacity(), description);
      assertEquals(generalBytes.order(), fastBytes.order(), description);
      assertArrayEquals(content(generalBytes), content(fastBytes), description);
   }

   @Test
   public void testWriteMatchesGeneralPathAndReference()
   {
      int[] reserves = {-1, 0, 8, 1 << 15};
      List<String> inputs = writeInputs();
      for (int i = 0; i < inputs.size(); i++)
      {
         String input = inputs.get(i);
         int offset = i & 7;
         int reserve = reserves[i % reserves.length];
         PlainCharSequence general = new PlainCharSequence(input);
         // A builder in UTF-16 mode (the library's), in Latin-1 mode where possible (a plain JDK builder) and one with
         // stale characters after its length, which must be ignored
         assertSameWrite(input, CDRBuffer.newStringBuilder(input), general, offset, reserve);
         assertSameWrite(input, new StringBuilder(input), general, offset, reserve);
         StringBuilder stale = new StringBuilder(input).append('\0').append('€');
         stale.setLength(input.length());
         assertSameWrite(input, stale, general, offset, reserve);
         assertSameWrite(input, new StringBuilder(input), input, offset, reserve);

         assertEquals(CDRBuffer.utf8Length(general), CDRBuffer.utf8Length(new StringBuilder(input)), () -> escape(input));
         assertEquals(CDRBuffer.utf8Length(general), CDRBuffer.utf8Length(CDRBuffer.newStringBuilder(input)), () -> escape(input));

         if (input.indexOf('\0') < 0)
         {
            byte[] reference = referenceEncode(input);
            assertEquals(reference.length, CDRBuffer.utf8Length(new StringBuilder(input)), () -> escape(input));
            assertArrayEquals(input.getBytes(StandardCharsets.UTF_8), reference, () -> escape(input));

            CDRBuffer buffer = new CDRBuffer();
            buffer.writePayloadHeader();
            buffer.writeString(CDRBuffer.newStringBuilder(input));
            ByteBuffer bytes = buffer.getBufferUnsafe();
            assertEquals(4 + 4 + reference.length + 1, bytes.position(), () -> escape(input));
            assertEquals(reference.length + 1, bytes.order(ByteOrder.LITTLE_ENDIAN).getInt(4), () -> escape(input));
            for (int j = 0; j < reference.length; j++)
               assertEquals(reference[j], bytes.get(8 + j), () -> escape(input));
            assertEquals(0, bytes.get(8 + reference.length));
         }
      }
   }

   @Test
   public void testEveryCodeUnit()
   {
      CDRBuffer buffer = new CDRBuffer();
      CDRBuffer generalBuffer = new CDRBuffer();
      StringBuilder value = CDRBuffer.newStringBuilder(8);
      StringBuilder latin1 = new StringBuilder(8);
      for (int c = 0; c <= 0xFFFF; c++)
      {
         for (StringBuilder builder : new StringBuilder[] {value, c < 0x100 ? latin1 : value})
         {
            builder.setLength(0);
            builder.append("ab").append((char) c).append("cd");
            String text = builder.toString();
            buffer.rewind();
            generalBuffer.rewind();
            buffer.writePayloadHeader();
            generalBuffer.writePayloadHeader();
            if (c == 0)
            {
               int code = c;
               String message = assertThrows(IllegalArgumentException.class, () -> buffer.writeString(builder)).getMessage();
               assertEquals(assertThrows(IllegalArgumentException.class, () -> generalBuffer.writeString(new PlainCharSequence(text))).getMessage(), message);
               assertEquals(4, buffer.getBufferUnsafe().position(), () -> "rejected " + code);
               continue;
            }
            buffer.writeString(builder);
            generalBuffer.writeString(new PlainCharSequence(text));
            int size = buffer.getBufferUnsafe().position();
            assertEquals(generalBuffer.getBufferUnsafe().position(), size);
            for (int i = 0; i < size; i++)
               assertEquals(generalBuffer.getBufferUnsafe().get(i), buffer.getBufferUnsafe().get(i));
         }
      }
   }

   @Test
   public void testLimitBelowCapacityAndExactFit()
   {
      for (int length : LENGTHS)
      {
         for (int offset = 0; offset < 4; offset++)
         {
            for (int back : new int[] {0, 1, 7})
            {
               String input = ascii(length);
               int padding = (4 - offset % 4) % 4;
               int needed = 4 + offset + padding + 4 + length + 1;
               CDRBuffer fast = writerAt(offset, needed + 16);
               CDRBuffer general = writerAt(offset, needed + 16);
               fast.getBufferUnsafe().limit(needed - back);
               general.getBufferUnsafe().limit(needed - back);
               fast.writeString(new StringBuilder(input));
               general.writeString(new PlainCharSequence(input));
               assertEquals(general.getBufferUnsafe().position(), fast.getBufferUnsafe().position());
               assertEquals(general.getBufferUnsafe().limit(), fast.getBufferUnsafe().limit());
               assertEquals(general.getBufferUnsafe().capacity(), fast.getBufferUnsafe().capacity());
               assertArrayEquals(content(general.getBufferUnsafe()), content(fast.getBufferUnsafe()));
            }
         }
      }
   }

   // ========== Reading ==========

   /** Reads {@code wireLength} and {@code body} (at the given offset after the header) and compares with the reference. */
   private static void assertRead(byte[] body, int offset, boolean bigEndian, StringBuilder destination)
   {
      int padding = (4 - offset % 4) % 4;
      ByteBuffer payload = ByteBuffer.allocate(4 + offset + padding + 4 + body.length);
      payload.put((byte) 0).put((byte) (bigEndian ? 0 : 1)).put((byte) 0).put((byte) 0);
      payload.position(4 + offset + padding);
      payload.order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
      payload.putInt(body.length);
      payload.put(body);
      byte[] bytes = payload.array();

      CDRBuffer buffer = new CDRBuffer();
      buffer.ensureRemainingCapacity(bytes.length + 8);
      ByteBuffer direct = buffer.getBufferUnsafe();
      for (int i = 0; i < direct.capacity(); i++)
         direct.put(i, (byte) 0xA5);
      direct.put(bytes);
      byte[] before = content(direct);
      buffer.beginRead(bytes.length);
      buffer.readPayloadHeader();
      direct.position(4 + offset);

      buffer.readString(destination);

      int dataEnd = body.length > 0 && body[body.length - 1] == 0 ? body.length - 1 : body.length;
      String description = hex(body) + " offset " + offset;
      assertEquals(referenceDecode(body, 0, dataEnd), destination.toString(), description);
      assertEquals(bytes.length, direct.position(), description);
      assertEquals(bytes.length, direct.limit(), description);
      assertEquals(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN, direct.order(), description);
      assertArrayEquals(before, content(direct), description);
   }

   private static void assertReadInAllDestinations(byte[] body, int offset)
   {
      assertRead(body, offset, false, CDRBuffer.newStringBuilder("previous € content"));
      assertRead(body, offset + 1, true, new StringBuilder("previous"));
      assertRead(body, offset + 2, false, new StringBuilder(0));
   }

   @Test
   public void testReadMatchesReference()
   {
      for (int length : LENGTHS)
      {
         byte[] asciiBytes = ascii(length).getBytes(StandardCharsets.US_ASCII);
         assertReadInAllDestinations(asciiBytes, length & 3);
         assertReadInAllDestinations(concat(asciiBytes, new byte[1]), length & 3);
         for (byte[] special : MULTI_BYTE_AND_MALFORMED)
         {
            for (int position : new int[] {0, 1, length / 2, length - 3, length - 2, length - 1, length})
            {
               if (position >= 0 && position <= length)
               {
                  byte[] before = Arrays.copyOf(asciiBytes, position);
                  byte[] after = Arrays.copyOfRange(asciiBytes, position, length);
                  assertReadInAllDestinations(concat(before, special, after), position & 3);
                  assertReadInAllDestinations(concat(before, special, after, new byte[1]), position & 3);
               }
            }
         }
      }
   }

   @Test
   public void testReadAllOneAndTwoByteContents()
   {
      for (int a = 0; a < 256; a++)
      {
         assertRead(new byte[] {(byte) a}, 0, false, CDRBuffer.newStringBuilder(4));
         for (int b = 0; b < 256; b++)
         {
            assertRead(new byte[] {(byte) a, (byte) b}, 0, false, CDRBuffer.newStringBuilder(4));
            assertRead(new byte[] {'x', (byte) a, (byte) b, 0}, 1, false, new StringBuilder(4));
         }
      }
   }

   @Test
   public void testReadRandomBytesMatchReference()
   {
      Random random = new Random(5678);
      for (int i = 0; i < 20000; i++)
      {
         int length = i % 50 == 0 ? random.nextInt(3000) : random.nextInt(80);
         double specialRate = i % 3 == 0 ? 0.0 : random.nextDouble() * random.nextDouble();
         byte[] body = new byte[length];
         for (int j = 0; j < length; j++)
            body[j] = random.nextDouble() < specialRate ? (byte) random.nextInt(256) : (byte) (32 + random.nextInt(95));
         assertRead(body, i & 7, (i & 8) != 0, i % 2 == 0 ? CDRBuffer.newStringBuilder(4) : new StringBuilder(4));
      }
   }

   @Test
   public void testReadValidUtf8MatchesJdk()
   {
      Random random = new Random(91011);
      for (int i = 0; i < 5000; i++)
      {
         int length = random.nextInt(60);
         StringBuilder value = new StringBuilder();
         for (int j = 0; j < length; j++)
         {
            int codePoint = random.nextInt(4) == 0 ? random.nextInt(0x110000) : 32 + random.nextInt(95);
            if (codePoint < 0xD800 || codePoint > 0xDFFF)
               value.appendCodePoint(codePoint);
         }
         byte[] utf8 = value.toString().getBytes(StandardCharsets.UTF_8);
         assertEquals(value.toString(), new String(utf8, StandardCharsets.UTF_8));
         assertRead(concat(utf8, new byte[1]), i & 3, false, CDRBuffer.newStringBuilder(4));
      }
   }

   @Test
   public void testTruncatedAndMalformedLengthsThrowLikeBefore()
   {
      for (int length : LENGTHS)
      {
         byte[] body = concat(ascii(length).getBytes(StandardCharsets.US_ASCII), new byte[1]);
         ByteBuffer payload = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
         payload.put((byte) 0).put((byte) 1).put((byte) 0).put((byte) 0);
         payload.putInt(body.length);
         payload.put(body);

         CDRBuffer buffer = new CDRBuffer();
         buffer.ensureRemainingCapacity(payload.capacity());
         buffer.getBufferUnsafe().put(payload.array());

         // The payload ends one byte early, and right after the length field
         for (int end : new int[] {payload.capacity() - 1, 8})
         {
            StringBuilder destination = CDRBuffer.newStringBuilder("previous");
            buffer.beginRead(end);
            buffer.readPayloadHeader();
            CDRDeserializationException exception = assertThrows(CDRDeserializationException.class, () -> buffer.readString(destination));
            assertTrue(exception.getMessage().contains("string"), exception.getMessage());
            assertEquals("", destination.toString(), "the destination is cleared before the length is checked");
         }

         // A length that is negative as an int
         buffer.getBufferUnsafe().order(ByteOrder.LITTLE_ENDIAN).putInt(4, -1);
         buffer.beginRead(payload.capacity());
         buffer.readPayloadHeader();
         assertThrows(CDRDeserializationException.class, () -> buffer.readString(new StringBuilder()));
      }
   }

   // ========== State between calls ==========

   @Test
   public void testScratchStateDoesNotLeakBetweenCalls()
   {
      String[] pool = {"", "a", ascii(5), ascii(600), ascii(5000), "é", "ascii then €", "😀 tail", ascii(511) + "€", ascii(512) + "€",
                       ascii(513) + "😀", "x\uD800y", ascii(30)};
      Random random = new Random(1213);
      CDRBuffer buffer = new CDRBuffer();
      StringBuilder destination = CDRBuffer.newStringBuilder(4);
      for (int i = 0; i < 5000; i++)
      {
         String first = pool[random.nextInt(pool.length)];
         String second = pool[random.nextInt(pool.length)];
         buffer.rewind();
         buffer.writePayloadHeader();
         buffer.writeString(new StringBuilder(first));
         buffer.writeString(CDRBuffer.newStringBuilder(second));
         buffer.beginRead(buffer.getBufferUnsafe().position());
         buffer.readPayloadHeader();
         buffer.readString(destination);
         assertEquals(first.replace("\uD800", "?"), destination.toString());
         buffer.readString(destination);
         assertEquals(second.replace("\uD800", "?"), destination.toString());
         assertFalse(buffer.getBufferUnsafe().hasRemaining());
      }
   }
}
