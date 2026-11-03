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

import us.ihmc.fastddsjava.profiles.gen.DurationType;

import java.util.List;

/**
 * Helpers for Fast-DDS XML durations ({@link DurationType}).
 * <p>
 * The generated {@link DurationType} keeps its values in a positional list ({@link DurationType#getSecOrNanosec()}):
 * {@code [nanosec, sec]}, or {@code [sec]} with a single entry. Values are decimal integers or one of the Fast-DDS infinity
 * literals ({@value #DURATION_INFINITY}, {@code DURATION_INFINITE_SEC}, {@code DURATION_INFINITE_NSEC}).
 * <p>
 * DDS durations have int32 seconds and a nanosecond part below one second. Fast-DDS silently wraps larger seconds into negative
 * values (entity creation then fails) and treats {@code sec == 2^31 - 1} as infinite, so {@link #normalize(DurationType)} maps
 * every duration of {@code 2^31 - 1} seconds or more to infinity and carries nanoseconds above one second into the seconds.
 */
public final class DurationTypeTools
{
   /** The Fast-DDS XML literal for an infinite duration. */
   public static final String DURATION_INFINITY = "DURATION_INFINITY";
   /** Durations of this many seconds or more are infinite for Fast-DDS ({@code Time_t::is_infinite}). */
   public static final long INFINITE_SECONDS = Integer.MAX_VALUE;
   /** Largest finite seconds value a DDS duration can hold. */
   public static final long MAX_FINITE_SECONDS = Integer.MAX_VALUE - 1L;
   /** Largest nanoseconds value of a normalized DDS duration. */
   public static final long MAX_NANOSECONDS = 999_999_999L;

   private static final long NANOS_PER_SECOND = 1_000_000_000L;

   private DurationTypeTools()
   {
   }

   /**
    * @return a new {@link DurationType} representing an infinite duration ({@code <sec>DURATION_INFINITY</sec>})
    */
   public static DurationType infinite()
   {
      DurationType durationType = new DurationType();
      durationType.getSecOrNanosec().add(DURATION_INFINITY);
      return durationType;
   }

   /**
    * @return the largest finite DDS duration ({@code 2^31 - 2} seconds and 999999999 nanoseconds, about 68 years)
    */
   public static DurationType maxFinite()
   {
      return of(MAX_FINITE_SECONDS, MAX_NANOSECONDS);
   }

   /**
    * Creates a normalized duration. Durations of {@code 2^31 - 1} seconds or more become {@link #infinite()}.
    *
    * @param seconds     whole seconds, not negative
    * @param nanoseconds nanoseconds, not negative; values of one second or more are carried into {@code seconds}
    * @return the duration in the {@code [nanosec, sec]} list convention, or an infinite duration
    * @throws IllegalArgumentException if {@code seconds} or {@code nanoseconds} is negative
    */
   public static DurationType of(long seconds, long nanoseconds)
   {
      if (seconds < 0 || nanoseconds < 0)
      {
         throw new IllegalArgumentException("DDS durations must not be negative (" + seconds + " s, " + nanoseconds + " ns)");
      }
      long carry = nanoseconds / NANOS_PER_SECOND;
      long nanos = nanoseconds % NANOS_PER_SECOND;
      if (seconds >= INFINITE_SECONDS || carry >= INFINITE_SECONDS - seconds)
      {
         return infinite();
      }
      DurationType durationType = new DurationType();
      durationType.getSecOrNanosec().add(Long.toString(nanos));
      durationType.getSecOrNanosec().add(Long.toString(seconds + carry));
      return durationType;
   }

   /**
    * @param durationType a duration, may be null
    * @return true if the duration is infinite once normalized
    * @throws IllegalArgumentException if the duration is malformed or negative
    */
   public static boolean isInfinite(DurationType durationType)
   {
      return durationType != null && normalize(durationType) == null;
   }

   /**
    * Parses and normalizes a duration.
    *
    * @param durationType a non-null duration
    * @return {@code {seconds, nanoseconds}} with {@code 0 <= seconds < 2^31 - 1} and {@code 0 <= nanoseconds < 10^9}, or
    *       null if the duration is infinite
    * @throws IllegalArgumentException if the list is empty, a value is not a number or an infinity literal, or a value is
    *                                  negative
    */
   public static long[] normalize(DurationType durationType)
   {
      List<String> values = durationType.getSecOrNanosec();
      if (values == null || values.isEmpty())
      {
         throw new IllegalArgumentException("A DDS duration must have at least a seconds or a nanoseconds value");
      }

      String secondsText;
      String nanosText;
      if (values.size() >= 2)
      {
         nanosText = values.get(0);
         secondsText = values.get(1);
      }
      else
      {
         nanosText = null;
         secondsText = values.get(0);
      }

      if (isInfinityLiteral(secondsText) || isInfinityLiteral(nanosText))
      {
         return null;
      }

      long seconds = parseNonNegative(secondsText, "sec");
      long nanos = nanosText == null ? 0L : parseNonNegative(nanosText, "nanosec");
      long carry = nanos / NANOS_PER_SECOND;
      nanos = nanos % NANOS_PER_SECOND;
      if (seconds >= INFINITE_SECONDS || carry >= INFINITE_SECONDS - seconds)
      {
         return null;
      }
      return new long[] {seconds + carry, nanos};
   }

   private static boolean isInfinityLiteral(String text)
   {
      if (text == null)
      {
         return false;
      }
      String trimmed = text.trim();
      return trimmed.equals(DURATION_INFINITY) || trimmed.equals("DURATION_INFINITE_SEC") || trimmed.equals("DURATION_INFINITE_NSEC");
   }

   private static long parseNonNegative(String text, String name)
   {
      if (text == null)
      {
         throw new IllegalArgumentException("Missing DDS duration " + name);
      }
      long value;
      try
      {
         value = Long.parseLong(text.trim());
      }
      catch (NumberFormatException e)
      {
         // Larger than a long: certainly infinite for DDS
         if (text.trim().matches("[0-9]+"))
         {
            return Long.MAX_VALUE;
         }
         throw new IllegalArgumentException("Invalid DDS duration " + name + ": '" + text + "'", e);
      }
      if (value < 0)
      {
         throw new IllegalArgumentException("DDS duration " + name + " must not be negative: " + value);
      }
      return value;
   }
}
