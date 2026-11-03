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

/**
 * The exceptions thrown by {@link CDRBuffer}, with their messages.
 * <p>
 * They are built here, in a class that only error paths use, so that the constant pool of {@link CDRBuffer} holds no
 * string constant at all: HotSpot resolves (creates) every not yet resolved string literal of a class on the thread that
 * first requests a C2 compilation of one of its methods, which would allocate on a control thread in the middle of a
 * steady state. What CDRBuffer was reading is passed as an int code; its name is looked up here.
 * </p>
 */
final class CDRBufferErrors
{
   // Codes of what CDRBuffer reads (compile-time constants: CDRBuffer holds no string constant), and their names
   static final int ENCAPSULATION_HEADER = 0;
   static final int OCTET = 1;
   static final int INT16 = 2;
   static final int INT32 = 3;
   static final int INT64 = 4;
   static final int FLOAT32 = 5;
   static final int FLOAT64 = 6;
   static final int BOOLEAN = 7;
   static final int STRING = 8;
   static final int WSTRING = 9;
   static final int SEQUENCE = 10;
   static final int OCTET_ARRAY = 11;
   static final int BOOLEAN_ARRAY = 12;
   static final int CHAR_ARRAY = 13;
   static final int INT16_ARRAY = 14;
   static final int UINT16_ARRAY = 15;
   static final int INT32_ARRAY = 16;
   static final int INT64_ARRAY = 17;
   static final int FLOAT32_ARRAY = 18;
   static final int FLOAT64_ARRAY = 19;
   static final int ALIGNMENT_PADDING = 20;
   private static final String[] NAMES = {"encapsulation header",
                                          "octet",
                                          "int16",
                                          "int32",
                                          "int64",
                                          "float32",
                                          "float64",
                                          "boolean",
                                          "string",
                                          "wstring",
                                          "sequence",
                                          "octet array",
                                          "boolean array",
                                          "char array",
                                          "int16 array",
                                          "uint16 array",
                                          "int32 array",
                                          "int64 array",
                                          "float32 array",
                                          "float64 array",
                                          "alignment padding"};

   /** @return the name of what CDRBuffer reads, for exception messages */
   static String name(int what)
   {
      return NAMES[what];
   }

   private CDRBufferErrors()
   {
   }

   static IllegalArgumentException negativeCapacity(int capacity)
   {
      return new IllegalArgumentException("CDRBuffer capacity cannot be negative: " + capacity);
   }

   static IllegalArgumentException cannotGrow(long requiredCapacity)
   {
      return new IllegalArgumentException("CDRBuffer cannot grow to " + requiredCapacity + " bytes");
   }

   static IllegalArgumentException payloadSizeOutsideCapacity(int payloadSizeBytes, int capacity)
   {
      return new IllegalArgumentException("Payload size " + payloadSizeBytes + " is outside the buffer capacity " + capacity);
   }

   static IllegalArgumentException nulCharacter(int index)
   {
      return new IllegalArgumentException("The string contains null characters (index " + index + "); ROS 2 strings cannot contain NUL");
   }

   static IllegalArgumentException stringTooLong(long utf8Length)
   {
      return new IllegalArgumentException("String is too long to be encoded: " + utf8Length + " UTF-8 bytes");
   }

   static IllegalArgumentException wideStringTooLong(int charLength)
   {
      return new IllegalArgumentException("Wide string is too long to be encoded: " + charLength + " code units");
   }

   static NullPointerException nullDestination(boolean wide)
   {
      return new NullPointerException(wide ? "CDRBuffer.readWString destination StringBuilder is null" : "CDRBuffer.readString destination StringBuilder is null");
   }

   static CDRDeserializationException wideStringExceedsPayload(int charLength, int remaining, int position)
   {
      return new CDRDeserializationException("wstring length " + charLength + " (x4 bytes) exceeds the " + remaining + " bytes remaining at position " + position);
   }

   static CDRDeserializationException wideCharacterTooLarge(int wchar)
   {
      return new CDRDeserializationException("wstring character value " + Integer.toUnsignedString(wchar) + " exceeds the maximum value 0xFFFF");
   }

   static IllegalArgumentException negativeSequenceLength(int length)
   {
      return new IllegalArgumentException("Sequence length cannot be negative: " + length);
   }

   static CDRDeserializationException sequenceExceedsBound(int length, int maxSize)
   {
      return new CDRDeserializationException("sequence length " + length + " exceeds its upper bound " + maxSize);
   }

   static CDRDeserializationException sequenceExceedsPayload(int length, long requiredBytes, int remaining, int position)
   {
      return new CDRDeserializationException("sequence length " + length + " needs at least " + requiredBytes + " bytes but only " + remaining + " remain at position "
                                             + position);
   }

   static CDRDeserializationException unsupportedEncapsulation(short encapsulation)
   {
      return new CDRDeserializationException("Unsupported encapsulation 0x" + Integer.toHexString(encapsulation & 0xFFFF));
   }

   static CDRDeserializationException unexpectedBoolean(byte value)
   {
      return new CDRDeserializationException("Unexpected boolean value " + (value & 0xFF) + ", expected 0 or 1");
   }

   static IndexOutOfBoundsException outOfRange(int offset, int length, int arrayLength)
   {
      return new IndexOutOfBoundsException("offset " + offset + ", length " + length + ", array length " + arrayLength);
   }

   static IllegalArgumentException arrayTooLarge(int length)
   {
      return new IllegalArgumentException("Array of " + length + " elements is too large to be encoded");
   }

   static CDRDeserializationException truncated(int what, long byteCount, int remaining, int position)
   {
      return new CDRDeserializationException("Payload truncated: reading " + name(what) + " needs " + byteCount + " bytes but only " + remaining + " remain at position "
                                             + position);
   }

   static CDRDeserializationException lengthTooLarge(int what, int length)
   {
      return new CDRDeserializationException(name(what) + " length " + Integer.toUnsignedString(length) + " is too large");
   }
}
