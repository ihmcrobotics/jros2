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
package us.ihmc.jros2.parser.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses default and constant values of .msg fields the way the ROS 2 reference implementation
 * ({@code rosidl_adapter/parser.py}, {@code parse_value_string}) does, and converts them to Java expressions.
 * <ul>
 *    <li>{@code bool}: {@code true}, {@code false}, {@code 1} or {@code 0}, case-insensitive.</li>
 *    <li>Integers: decimal (Python {@code int(s)}) or prefixed {@code 0x}, {@code 0o}, {@code 0b}; range checked per type.
 *    {@code byte} and {@code char} are in [0, 255].</li>
 *    <li>Floats: anything Python {@code float()} accepts, including {@code inf} and {@code nan}.</li>
 *    <li>Strings: if the value starts and ends with the same quote ({@code "} first, then {@code '}), the quotes are
 *    removed, an unescaped inner quote of that kind is an error, and {@code \"} (or {@code \'}) becomes the quote. Any
 *    other text, including backslashes, is taken literally. A bounded string default may not exceed the bound.</li>
 *    <li>Arrays: {@code [a, b, c]}; string array elements may be quoted and contain commas. Fixed-size arrays need exactly
 *    N elements, bounded sequences at most N.</li>
 * </ul>
 * Note that, like {@code rosidl_adapter}, the caller treats everything from the first {@code #} as a comment, even inside
 * quotes.
 */
public final class DefaultValueTools
{
   private DefaultValueTools()
   {
   }

   /**
    * Converts a scalar default or constant literal to a Java expression of the field's Java type.
    *
    * @param rosType         the ROS built-in type name, e.g. {@code uint8} or {@code string}
    * @param literal         the literal as written in the .msg file
    * @param stringBound     the bound of a bounded string ({@code <= 0} if unbounded)
    * @param sequenceElement true if the value is an element of an IDL sequence; {@code uint16} sequences store shorts
    * @return a Java expression, e.g. {@code (byte) 200}, {@code 5000000000L} or {@code "abc"}
    * @throws IllegalArgumentException if the literal is not valid for the type
    */
   public static String toJavaExpression(String rosType, String literal, int stringBound, boolean sequenceElement)
   {
      literal = literal.trim();

      switch (rosType)
      {
         case "bool":
            return parseBool(literal) ? "true" : "false";
         case "byte":
            return "(byte) " + parseInteger(rosType, literal, BigInteger.ZERO, BigInteger.valueOf(255));
         case "char":
            return "(char) " + parseInteger(rosType, literal, BigInteger.ZERO, BigInteger.valueOf(255));
         case "int8":
            return "(byte) " + parseInteger(rosType, literal, BigInteger.valueOf(Byte.MIN_VALUE), BigInteger.valueOf(Byte.MAX_VALUE));
         case "uint8":
            return "(byte) " + parseInteger(rosType, literal, BigInteger.ZERO, BigInteger.valueOf(255));
         case "int16":
            return "(short) " + parseInteger(rosType, literal, BigInteger.valueOf(Short.MIN_VALUE), BigInteger.valueOf(Short.MAX_VALUE));
         case "uint16":
         {
            BigInteger value = parseInteger(rosType, literal, BigInteger.ZERO, BigInteger.valueOf(65535));
            return sequenceElement ? "(short) " + value : value.toString();
         }
         case "int32":
            return parseInteger(rosType, literal, BigInteger.valueOf(Integer.MIN_VALUE), BigInteger.valueOf(Integer.MAX_VALUE)).toString();
         case "uint32":
         {
            BigInteger value = parseInteger(rosType, literal, BigInteger.ZERO, BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE));
            return value.bitLength() > 31 ? "0x" + value.toString(16).toUpperCase(Locale.ROOT) : value.toString();
         }
         case "int64":
            return parseInteger(rosType, literal, BigInteger.valueOf(Long.MIN_VALUE), BigInteger.valueOf(Long.MAX_VALUE)) + "L";
         case "uint64":
         {
            BigInteger value = parseInteger(rosType, literal, BigInteger.ZERO, BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE));
            return value.bitLength() > 63 ? "0x" + value.toString(16).toUpperCase(Locale.ROOT) + "L" : value + "L";
         }
         case "float32":
            return "(float) " + parseFloat(rosType, literal);
         case "float64":
            return parseFloat(rosType, literal);
         case "string":
         case "wstring":
            return toJavaStringLiteral(parseString(rosType, literal, stringBound));
         default:
            throw new IllegalArgumentException("Default values are only supported for built-in types, not '" + rosType + "'");
      }
   }

   /**
    * Splits an array literal {@code [a, b, c]} into its element literals and checks the element count.
    *
    * @param rosType    the ROS built-in element type
    * @param literal    the array literal as written in the .msg file
    * @param fixedSize  the array size of {@code T[N]}, or -1
    * @param upperBound the bound of {@code T[<=N]}, or -1
    * @return the element literals (trimmed)
    */
   public static List<String> splitArrayLiteral(String rosType, String literal, int fixedSize, int upperBound)
   {
      literal = literal.trim();
      if (!literal.startsWith("[") || !literal.endsWith("]"))
      {
         throw new IllegalArgumentException("Array value must start with '[' and end with ']': " + literal);
      }

      String elements = literal.substring(1, literal.length() - 1);
      List<String> values;

      if (rosType.equals("string") || rosType.equals("wstring"))
      {
         values = splitStringArrayElements(elements);
      }
      else
      {
         values = new ArrayList<>();
         if (!elements.isEmpty())
         {
            for (String element : elements.split(",", -1))
            {
               values.add(element.trim());
            }
         }
      }

      if (fixedSize > 0 && values.size() != fixedSize)
      {
         throw new IllegalArgumentException("Array must have exactly " + fixedSize + " elements, not " + values.size() + ": " + literal);
      }
      if (upperBound > 0 && values.size() > upperBound)
      {
         throw new IllegalArgumentException("Array must have not more than " + upperBound + " elements, not " + values.size() + ": " + literal);
      }

      return values;
   }

   /**
    * Parses a string default value the way {@code rosidl_adapter} does (see the class documentation).
    *
    * @return the string value
    */
   public static String parseString(String rosType, String literal, int stringBound)
   {
      String value = literal;

      for (char quote : new char[] {'"', '\''})
      {
         if (!value.isEmpty() && value.charAt(0) == quote && value.charAt(value.length() - 1) == quote)
         {
            // Like Python's value[1:-1], a single quote character yields an empty string
            value = value.length() >= 2 ? value.substring(1, value.length() - 1) : "";
            for (int i = 0; i < value.length(); ++i)
            {
               if (value.charAt(i) == quote && (i == 0 || value.charAt(i - 1) != '\\'))
               {
                  throw new IllegalArgumentException("String inner quotes not properly escaped: " + literal);
               }
            }
            value = value.replace("\\" + quote, String.valueOf(quote));
            break;
         }
      }

      if (stringBound > 0 && value.codePointCount(0, value.length()) > stringBound)
      {
         throw new IllegalArgumentException(rosType + " default value must not exceed the maximum length of " + stringBound + " characters: " + literal);
      }

      return value;
   }

   /**
    * Parses a {@code bool} literal: {@code true}/{@code 1} or {@code false}/{@code 0}, case-insensitive.
    */
   public static boolean parseBool(String literal)
   {
      String lower = literal.trim().toLowerCase(Locale.ROOT);
      if (lower.equals("true") || lower.equals("1"))
         return true;
      if (lower.equals("false") || lower.equals("0"))
         return false;
      throw new IllegalArgumentException("bool value must be either 'true' / '1' or 'false' / '0', not '" + literal + "'");
   }

   /**
    * Returns {@code value} as a Java string literal (with quotes); non-ASCII characters are written as unicode escapes so
    * the generated source does not depend on the compiler's source encoding.
    */
   public static String toJavaStringLiteral(String value)
   {
      StringBuilder builder = new StringBuilder(value.length() + 2);
      builder.append('"');
      for (int i = 0; i < value.length(); ++i)
      {
         char c = value.charAt(i);
         switch (c)
         {
            case '"' -> builder.append("\\\"");
            case '\\' -> builder.append("\\\\");
            case '\n' -> builder.append("\\n");
            case '\r' -> builder.append("\\r");
            case '\t' -> builder.append("\\t");
            default ->
            {
               if (c < 0x20 || c > 0x7E)
               {
                  builder.append(String.format("\\u%04x", (int) c));
               }
               else
               {
                  builder.append(c);
               }
            }
         }
      }
      builder.append('"');
      return builder.toString();
   }

   private static BigInteger parseInteger(String rosType, String literal, BigInteger min, BigInteger max)
   {
      BigInteger value;
      try
      {
         value = parsePythonInteger(literal);
      }
      catch (NumberFormatException e)
      {
         throw new IllegalArgumentException(rosType + " value must be a valid integer value >= " + min + " and <= " + max + ", not '" + literal + "'");
      }

      if (value.compareTo(min) < 0 || value.compareTo(max) > 0)
      {
         throw new IllegalArgumentException(rosType + " value must be a valid integer value >= " + min + " and <= " + max + ", not '" + literal + "'");
      }

      return value;
   }

   /** Python {@code int(s)}, falling back to {@code int(s, 0)} (prefixed binary, octal and hexadecimal). */
   private static BigInteger parsePythonInteger(String literal)
   {
      String s = literal.trim();
      boolean negative = false;
      if (s.startsWith("+") || s.startsWith("-"))
      {
         negative = s.startsWith("-");
         s = s.substring(1);
      }

      int radix = 10;
      String lower = s.toLowerCase(Locale.ROOT);
      if (lower.startsWith("0x"))
         radix = 16;
      else if (lower.startsWith("0o"))
         radix = 8;
      else if (lower.startsWith("0b"))
         radix = 2;
      if (radix != 10)
      {
         s = s.substring(2);
         if (s.startsWith("_"))
            s = s.substring(1);
      }

      if (s.isEmpty() || s.startsWith("_") || s.endsWith("_") || s.contains("__"))
      {
         throw new NumberFormatException(literal);
      }

      BigInteger value = new BigInteger(s.replace("_", ""), radix);
      return negative ? value.negate() : value;
   }

   /** Python {@code float(s)} as a Java double expression. */
   private static String parseFloat(String rosType, String literal)
   {
      String lower = literal.trim().toLowerCase(Locale.ROOT);
      boolean negative = lower.startsWith("-");
      String unsigned = lower.startsWith("-") || lower.startsWith("+") ? lower.substring(1) : lower;

      if (unsigned.equals("inf") || unsigned.equals("infinity"))
      {
         return negative ? "java.lang.Double.NEGATIVE_INFINITY" : "java.lang.Double.POSITIVE_INFINITY";
      }
      if (unsigned.equals("nan"))
      {
         return "java.lang.Double.NaN";
      }

      try
      {
         if (unsigned.isEmpty() || unsigned.startsWith("_") || unsigned.endsWith("_") || unsigned.contains("__") || !unsigned.matches("[0-9_.e+-]+"))
         {
            throw new NumberFormatException(literal);
         }
         double value = Double.parseDouble(lower.replace("_", ""));
         return Double.toString(value);
      }
      catch (NumberFormatException e)
      {
         throw new IllegalArgumentException(rosType + " value must be a floating point number using '.' as the separator, not '" + literal + "'");
      }
   }

   /** {@code rosidl_adapter.parser.parse_string_array_value_string}: quoted elements may contain commas. */
   private static List<String> splitStringArrayElements(String elements)
   {
      List<String> values = new ArrayList<>();
      String rest = elements;

      while (!rest.isEmpty())
      {
         rest = stripLeadingSpaces(rest);
         if (rest.isEmpty())
            break;
         if (rest.charAt(0) == ',')
         {
            throw new IllegalArgumentException("Unexpected ',' at beginning of [" + rest + "]");
         }

         char first = rest.charAt(0);
         if (first == '"' || first == '\'')
         {
            int endQuote = findMatchingEndQuote(rest, first);
            if (endQuote < 0)
            {
               throw new IllegalArgumentException("String [" + rest + "] incorrectly quoted");
            }
            String value = rest.substring(1, endQuote).replace("\\" + first, String.valueOf(first));
            values.add(value);
            rest = rest.substring(endQuote + 1);
         }
         else
         {
            int nextComma = rest.indexOf(',');
            if (nextComma < 0)
            {
               values.add(rest.trim());
               rest = "";
            }
            else
            {
               values.add(rest.substring(0, nextComma).trim());
               rest = rest.substring(nextComma);
            }
         }

         rest = stripLeadingSpaces(rest);
         if (!rest.isEmpty() && rest.charAt(0) == ',')
         {
            rest = rest.substring(1);
         }
      }

      return values;
   }

   /** Index of the next quote character after index 0 that is not preceded by a backslash, or -1. */
   private static int findMatchingEndQuote(String string, char quote)
   {
      for (int i = 1; i < string.length(); ++i)
      {
         if (string.charAt(i) == quote && string.charAt(i - 1) != '\\')
         {
            return i;
         }
      }
      return -1;
   }

   private static String stripLeadingSpaces(String string)
   {
      int i = 0;
      while (i < string.length() && string.charAt(i) == ' ')
         ++i;
      return string.substring(i);
   }
}
