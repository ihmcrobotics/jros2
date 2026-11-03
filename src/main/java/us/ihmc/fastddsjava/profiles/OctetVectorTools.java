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
package us.ihmc.fastddsjava.profiles;

import us.ihmc.fastddsjava.profiles.gen.OctectVectorQosPolicyType;

import java.nio.charset.StandardCharsets;

/**
 * Helpers for Fast-DDS octet vectors such as {@code <userData>}.
 * <p>
 * Fast-DDS parses an octet vector from exactly one {@code <value>} element whose text is a list of 1-2 digit hexadecimal octets
 * separated by '.', without whitespace (for example {@code 65.6e.63.6c.61.76.65.3d.2f.3b.00}).
 */
public final class OctetVectorTools
{
   private static final char[] HEX = "0123456789abcdef".toCharArray();

   private OctetVectorTools()
   {
   }

   /**
    * @param bytes the octets
    * @return the octets formatted as Fast-DDS expects them in a {@code <value>} element, e.g. {@code 2f.3b.00}
    */
   public static String toOctetString(byte[] bytes)
   {
      StringBuilder builder = new StringBuilder(bytes.length * 3);
      for (int i = 0; i < bytes.length; ++i)
      {
         if (i > 0)
         {
            builder.append('.');
         }
         builder.append(HEX[(bytes[i] >> 4) & 0xf]).append(HEX[bytes[i] & 0xf]);
      }
      return builder.toString();
   }

   /**
    * @param octets the octets
    * @return an octet vector with a single value holding {@code octets}
    * @throws IllegalArgumentException if {@code octets} is empty (Fast-DDS rejects an empty value)
    */
   public static OctectVectorQosPolicyType fromBytes(byte[] octets)
   {
      if (octets.length == 0)
      {
         throw new IllegalArgumentException("A Fast-DDS octet vector cannot be empty");
      }
      OctectVectorQosPolicyType octetVector = new OctectVectorQosPolicyType();
      octetVector.getValue().add(toOctetString(octets));
      return octetVector;
   }

   /**
    * @param text           ASCII text
    * @param terminatingNul whether to append a NUL octet (rmw_fastrtps does for the participant {@code enclave=...;} user data)
    * @return an octet vector holding the ASCII bytes of {@code text}
    * @throws IllegalArgumentException if {@code text} contains characters outside US-ASCII
    */
   public static OctectVectorQosPolicyType fromAscii(String text, boolean terminatingNul)
   {
      for (int i = 0; i < text.length(); ++i)
      {
         if (text.charAt(i) > 0x7f)
         {
            throw new IllegalArgumentException("Octet vector text must be US-ASCII: '" + text + "'");
         }
      }
      byte[] ascii = text.getBytes(StandardCharsets.US_ASCII);
      if (!terminatingNul)
      {
         return fromBytes(ascii);
      }
      byte[] withNul = new byte[ascii.length + 1];
      System.arraycopy(ascii, 0, withNul, 0, ascii.length);
      return fromBytes(withNul);
   }

   /**
    * Parses an octet string as written by {@link #toOctetString(byte[])}.
    *
    * @param octetString dot-separated hexadecimal octets
    * @return the octets
    * @throws IllegalArgumentException if the text is not a valid octet string
    */
   public static byte[] parseOctetString(String octetString)
   {
      if (octetString == null || octetString.isEmpty())
      {
         throw new IllegalArgumentException("Empty octet string");
      }
      String[] tokens = octetString.split("\\.", -1);
      byte[] bytes = new byte[tokens.length];
      for (int i = 0; i < tokens.length; ++i)
      {
         String token = tokens[i];
         if (token.isEmpty() || token.length() > 2)
         {
            throw new IllegalArgumentException("Invalid octet '" + token + "' in '" + octetString + "'");
         }
         int value = 0;
         for (int j = 0; j < token.length(); ++j)
         {
            int digit = Character.digit(token.charAt(j), 16);
            if (digit < 0)
            {
               throw new IllegalArgumentException("Invalid octet '" + token + "' in '" + octetString + "'");
            }
            value = value * 16 + digit;
         }
         bytes[i] = (byte) value;
      }
      return bytes;
   }
}
