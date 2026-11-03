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
package us.ihmc.jros2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.jros2.Statistics.StatisticDataType.*;

/**
 * {@link Statistics} and {@link StatisticsCalculator}, without timing: Welford's mean and population standard deviation,
 * minimum and maximum (also of negative values), ignored non-finite values, reset, and consistent reads while values are
 * being recorded. (The timing-dependent {@code StatisticsTest} is disabled.)
 */
public class StatisticsCalculatorTest
{
   private static void assertDefaults(Statistics statistics)
   {
      assertTrue(Double.isNaN(statistics.get(AVERAGE)));
      assertTrue(Double.isNaN(statistics.get(MINIMUM)));
      assertTrue(Double.isNaN(statistics.get(MAXIMUM)));
      assertTrue(Double.isNaN(statistics.get(STDDEV)));
      assertEquals(0.0, statistics.get(SAMPLE_COUNT));
      assertEquals(0.0, statistics.get(TOTAL));
      assertTrue(Double.isNaN(statistics.get(LATEST)));
   }

   @Test
   public void testStatisticsValues()
   {
      Statistics statistics = new Statistics();
      assertDefaults(statistics);

      double value = 1.5;
      for (Statistics.StatisticDataType type : Statistics.StatisticDataType.values)
      {
         statistics.set(type, value);
         value += 1.0;
      }
      assertEquals(1.5, statistics.get(AVERAGE));
      assertEquals(2.5, statistics.get(MINIMUM));
      assertEquals(3.5, statistics.get(MAXIMUM));
      assertEquals(4.5, statistics.get(STDDEV));
      assertEquals(5.0, statistics.get(SAMPLE_COUNT), "the sample count is an integer");
      assertEquals(6.5, statistics.get(TOTAL));
      assertEquals(7.5, statistics.get(LATEST));
      assertEquals("Statistics: {AVERAGE=1.5, MINIMUM=2.5, MAXIMUM=3.5, STDDEV=4.5, SAMPLE_COUNT=5.0, TOTAL=6.5, LATEST=7.5}", statistics.toString());
      assertEquals(Statistics.StatisticDataType.values.length, Statistics.StatisticDataType.set.size());

      statistics.reset();
      assertDefaults(statistics);
   }

   @Test
   public void testCalculator()
   {
      StatisticsCalculator calculator = new StatisticsCalculator();
      Statistics statistics = new Statistics();
      statistics.set(AVERAGE, 42.0);
      calculator.read(statistics);
      assertDefaults(statistics);
      calculator.read(null); // ignored

      for (int i = 1; i <= 5; ++i)
      {
         calculator.record(i);
      }
      calculator.read(statistics);
      assertEquals(3.0, statistics.get(AVERAGE), 1e-12);
      assertEquals(1.0, statistics.get(MINIMUM));
      assertEquals(5.0, statistics.get(MAXIMUM));
      assertEquals(Math.sqrt(2.0), statistics.get(STDDEV), 1e-12, "population standard deviation");
      assertEquals(5.0, statistics.get(SAMPLE_COUNT));
      assertEquals(15.0, statistics.get(TOTAL));
      assertEquals(5.0, statistics.get(LATEST));
      assertTrue(calculator.toString().startsWith("StatisticsCalculator: {AVERAGE=3.0"), calculator.toString());

      // Values that are not finite are not recorded
      calculator.record(Double.NaN);
      calculator.record(Double.POSITIVE_INFINITY);
      calculator.record(Double.NEGATIVE_INFINITY);
      calculator.read(statistics);
      assertEquals(5.0, statistics.get(SAMPLE_COUNT));
      assertEquals(5.0, statistics.get(LATEST));
      assertEquals(5.0, statistics.get(MAXIMUM));

      calculator.reset();
      calculator.read(statistics);
      assertDefaults(statistics);
   }

   /** The allocation-free single-value read returns what read(Statistics) packs, with and without samples. */
   @Test
   public void testGetMatchesRead()
   {
      StatisticsCalculator calculator = new StatisticsCalculator();
      Statistics statistics = new Statistics();
      for (int round = 0; round < 3; ++round)
      {
         calculator.read(statistics);
         for (Statistics.StatisticDataType type : Statistics.StatisticDataType.values)
         {
            assertEquals(statistics.get(type), calculator.get(type), type + " after " + round + " rounds");
         }
         calculator.record(round * 1.5 - 2.0);
         calculator.record(round + 4.0);
      }
   }

   @Test
   public void testNegativeValues()
   {
      // Periods and ages can be negative when clocks step; the maximum of negative values must not be 0 or Double.MIN_VALUE
      StatisticsCalculator calculator = new StatisticsCalculator();
      calculator.record(-3.0);
      calculator.record(-1.0);
      calculator.record(-2.0);
      Statistics statistics = new Statistics();
      calculator.read(statistics);
      assertEquals(-1.0, statistics.get(MAXIMUM));
      assertEquals(-3.0, statistics.get(MINIMUM));
      assertEquals(-2.0, statistics.get(AVERAGE), 1e-12);
      assertEquals(Math.sqrt(2.0 / 3.0), statistics.get(STDDEV), 1e-12);
      assertEquals(-6.0, statistics.get(TOTAL));
      assertEquals(-2.0, statistics.get(LATEST));

      // A reset must also reset the maximum
      calculator.reset();
      calculator.record(-7.0);
      calculator.read(statistics);
      assertEquals(-7.0, statistics.get(MAXIMUM));
      assertEquals(-7.0, statistics.get(MINIMUM));
      assertEquals(0.0, statistics.get(STDDEV));
   }

   /**
    * A read while another thread records must see a consistent set of values: count, total and mean of the same samples.
    */
   @Test
   @Timeout(60)
   public void testReadsAreConsistentWhileRecording() throws InterruptedException
   {
      StatisticsCalculator calculator = new StatisticsCalculator();
      int samples = 200_000;
      Thread writer = new Thread(() ->
      {
         for (int i = 0; i < samples; ++i)
         {
            calculator.record(2.0);
         }
      }, "statistics-writer");
      AtomicReference<String> inconsistency = new AtomicReference<>();
      writer.start();
      Statistics statistics = new Statistics();
      while (writer.isAlive() && inconsistency.get() == null)
      {
         calculator.read(statistics);
         double count = statistics.get(SAMPLE_COUNT);
         if (count > 0 && (statistics.get(TOTAL) != 2.0 * count || statistics.get(AVERAGE) != 2.0 || statistics.get(STDDEV) != 0.0))
         {
            inconsistency.set(statistics.toString());
         }
      }
      writer.join();
      assertNull(inconsistency.get(), "inconsistent read: " + inconsistency.get());
      calculator.read(statistics);
      assertEquals(samples, statistics.get(SAMPLE_COUNT));
      assertEquals(2.0 * samples, statistics.get(TOTAL));
   }
}
