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
package us.ihmc.jros2.allocation.messages;

import us.ihmc.fastddsjava.cdr.idl.IDLBoolSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLByteSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLCharSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLDoubleSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLFloatSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLIntSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLLongSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLObjectSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLShortSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLStringSequence;
import us.ihmc.fastddsjava.cdr.CDRSerializable;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Random;

/**
 * Fills generated messages (any type produced by the jros2 generator) with deterministic random contents through
 * reflection: every primitive, string, fixed array, bounded/unbounded sequence and nested message. Strings mix ASCII,
 * Latin-1, 2- and 3-byte UTF-8 characters and supplementary characters (4-byte UTF-8, surrogate pairs in UTF-16).
 * <p>
 * In {@link #fillHighWaterMark} mode every sequence gets its largest test length and every string its largest test
 * length; {@link #fillRandom} picks lengths between 0 and those maxima, so the instances it produces never exceed the
 * high-water mark of a high-water-mark instance filled with the same limits.
 * </p>
 */
public final class MessageFiller
{
   /** Characters used in strings: ASCII, Latin-1, 2-byte and 3-byte UTF-8. Never NUL. */
   private static final String BMP_CHARACTERS = "abcXYZ019 _-/.éßçÿΩжא€中ह✓";
   /** Supplementary characters: 4-byte UTF-8, surrogate pairs in UTF-16. */
   private static final int[] SUPPLEMENTARY = {0x1D11E, 0x1F600, 0x20BB7, 0x10348};

   private final Random random;
   private final int maxPrimitiveSequenceLength;
   private final int maxStringSequenceLength;
   private final int maxObjectSequenceLength;
   private final int maxStringLength;
   private boolean highWaterMark;
   private boolean asciiOnly;

   public MessageFiller(long seed, int maxPrimitiveSequenceLength, int maxStringSequenceLength, int maxObjectSequenceLength, int maxStringLength)
   {
      this.random = new Random(seed);
      this.maxPrimitiveSequenceLength = maxPrimitiveSequenceLength;
      this.maxStringSequenceLength = maxStringSequenceLength;
      this.maxObjectSequenceLength = maxObjectSequenceLength;
      this.maxStringLength = maxStringLength;
   }

   public static MessageFiller standard(long seed)
   {
      return new MessageFiller(seed, 37, 5, 3, 12);
   }

   public void fillHighWaterMark(Object message)
   {
      highWaterMark = true;
      fillMessage(message, 0);
   }

   public void fillRandom(Object message)
   {
      highWaterMark = false;
      fillMessage(message, 0);
   }

   /**
    * Like {@link #fillHighWaterMark}, but every string holds ASCII characters only (same lengths in UTF-16 code units).
    */
   public void fillHighWaterMarkAscii(Object message)
   {
      asciiOnly = true;
      try
      {
         fillHighWaterMark(message);
      }
      finally
      {
         asciiOnly = false;
      }
   }

   private int length(int max)
   {
      if (max <= 0)
         return 0;
      if (highWaterMark)
         return max;
      // Mostly non-empty, sometimes empty, sometimes equal to the maximum
      int pick = random.nextInt(8);
      if (pick == 0)
         return 0;
      if (pick == 1)
         return max;
      return 1 + random.nextInt(max);
   }

   private static int bounded(IDLSequence<?> sequence, int max)
   {
      return (int) Math.min(sequence.getMaxSize(), max);
   }

   private void fillMessage(Object message, int depth)
   {
      if (depth > 12)
         throw new IllegalStateException("message nesting too deep: " + message.getClass());

      for (Class<?> type = message.getClass(); type != null && type != Object.class; type = type.getSuperclass())
      {
         for (Field field : type.getDeclaredFields())
         {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic())
               continue;
            field.setAccessible(true);
            try
            {
               fillField(message, field, depth);
            }
            catch (IllegalAccessException e)
            {
               throw new RuntimeException(e);
            }
         }
      }
   }

   private void fillField(Object message, Field field, int depth) throws IllegalAccessException
   {
      Class<?> type = field.getType();
      if (type.isPrimitive())
      {
         field.set(message, randomPrimitive(type));
         return;
      }

      Object value = field.get(message);
      if (value == null)
         throw new IllegalStateException("null field " + field);

      if (type.isArray())
      {
         int length = Array.getLength(value);
         Class<?> componentType = type.getComponentType();
         for (int i = 0; i < length; i++)
         {
            if (componentType.isPrimitive())
               Array.set(value, i, randomPrimitive(componentType));
            else if (componentType == StringBuilder.class)
               fillString((StringBuilder) Array.get(value, i));
            else
               fillMessage(Array.get(value, i), depth + 1);
         }
      }
      else if (value instanceof StringBuilder builder)
      {
         fillString(builder);
      }
      else if (value instanceof IDLSequence<?> sequence)
      {
         fillSequence(sequence, depth);
      }
      else if (value instanceof CDRSerializable)
      {
         fillMessage(value, depth + 1);
      }
      else
      {
         throw new IllegalStateException("unsupported field " + field);
      }
   }

   private void fillSequence(IDLSequence<?> sequence, int depth)
   {
      sequence.clear();
      if (sequence instanceof IDLObjectSequence<?> objects)
      {
         int n = length(bounded(sequence, Math.max(1, maxObjectSequenceLength - depth / 2)));
         for (int i = 0; i < n; i++)
            fillMessage(objects.add(), depth + 1);
      }
      else if (sequence instanceof IDLStringSequence strings) // IDLWStringSequence included
      {
         int n = length(bounded(sequence, maxStringSequenceLength));
         for (int i = 0; i < n; i++)
         {
            StringBuilder element = strings.add();
            fillString(element);
         }
      }
      else
      {
         int n = length(bounded(sequence, maxPrimitiveSequenceLength));
         for (int i = 0; i < n; i++)
         {
            if (sequence instanceof IDLBoolSequence s)
               s.add(random.nextBoolean());
            else if (sequence instanceof IDLByteSequence s)
               s.add((byte) random.nextInt());
            else if (sequence instanceof IDLCharSequence s)
               s.add((char) random.nextInt(256));
            else if (sequence instanceof IDLShortSequence s)
               s.add((short) random.nextInt());
            else if (sequence instanceof IDLIntSequence s)
               s.add(random.nextInt());
            else if (sequence instanceof IDLLongSequence s)
               s.add(random.nextLong());
            else if (sequence instanceof IDLFloatSequence s)
               s.add(random.nextFloat() * 2000.0f - 1000.0f);
            else if (sequence instanceof IDLDoubleSequence s)
               s.add(random.nextDouble() * 2.0e6 - 1.0e6);
            else
               throw new IllegalStateException("unsupported sequence " + sequence.getClass());
         }
      }
   }

   private Object randomPrimitive(Class<?> type)
   {
      if (type == boolean.class)
         return random.nextBoolean();
      if (type == byte.class)
         return (byte) random.nextInt();
      if (type == char.class)
         return (char) random.nextInt(256); // ROS 2 char is uint8
      if (type == short.class)
         return (short) random.nextInt();
      if (type == int.class)
         return random.nextInt();
      if (type == long.class)
         return random.nextLong();
      if (type == float.class)
         return random.nextFloat() * 2000.0f - 1000.0f;
      if (type == double.class)
         return random.nextDouble() * 2.0e6 - 1.0e6;
      throw new IllegalStateException("unsupported primitive " + type);
   }

   /**
    * Replaces the content with a random string of {@link #length} UTF-16 code units. A high-water-mark string always
    * contains non-Latin-1 characters and a surrogate pair.
    */
   private void fillString(StringBuilder builder)
   {
      builder.setLength(0);
      int length = length(maxStringLength);
      if (asciiOnly)
      {
         while (builder.length() < length)
            builder.append((char) ('a' + random.nextInt(26)));
         return;
      }
      if (highWaterMark && length >= 3)
      {
         builder.append('€');
         builder.appendCodePoint(SUPPLEMENTARY[random.nextInt(SUPPLEMENTARY.length)]);
      }
      while (builder.length() < length)
      {
         if (length - builder.length() >= 2 && random.nextInt(5) == 0)
            builder.appendCodePoint(SUPPLEMENTARY[random.nextInt(SUPPLEMENTARY.length)]);
         else
            builder.append(BMP_CHARACTERS.charAt(random.nextInt(BMP_CHARACTERS.length())));
      }
   }
}
