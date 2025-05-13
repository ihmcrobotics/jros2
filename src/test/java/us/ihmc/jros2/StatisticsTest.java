package us.ihmc.jros2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.jros2.Statistics.StatisticDataType.*;

public class StatisticsTest
{
   @Test
   public void testSimpleStatistics()
   {
      // Create new statistics object
      Statistics statistics = new Statistics();

      // Assert correct default values upon creation
      assertEquals(Double.NaN, statistics.get(AVERAGE));
      assertEquals(Double.NaN, statistics.get(MINIMUM));
      assertEquals(Double.NaN, statistics.get(MAXIMUM));
      assertEquals(Double.NaN, statistics.get(STDDEV));
      assertEquals(0.0, statistics.get(TOTAL));
      assertEquals(Double.NaN, statistics.get(LATEST));
      assertEquals(0, statistics.get(SAMPLE_COUNT));

      // Set the values
      final double expectedAverage = 5.0;
      final double expectedMin = 2.0;
      final double expectedMax = 7.0;
      final double expectedStddev = 1.0;
      final double expectedTotal = 10;
      final double expectedLatest = 3.0;
      final long expectedCount = 30L;

      statistics.set(AVERAGE, expectedAverage);
      statistics.set(MINIMUM, expectedMin);
      statistics.set(MAXIMUM, expectedMax);
      statistics.set(STDDEV, expectedStddev);
      statistics.set(TOTAL, expectedTotal);
      statistics.set(LATEST, expectedLatest);
      statistics.set(SAMPLE_COUNT, expectedCount);

      // Assert that values were set correctly
      assertEquals(expectedAverage, statistics.get(AVERAGE));
      assertEquals(expectedMin, statistics.get(MINIMUM));
      assertEquals(expectedMax, statistics.get(MAXIMUM));
      assertEquals(expectedStddev, statistics.get(STDDEV));
      assertEquals(expectedTotal, statistics.get(TOTAL));
      assertEquals(expectedLatest, statistics.get(LATEST));
      assertEquals(expectedCount, statistics.get(SAMPLE_COUNT));

      // Reset the object
      statistics.reset();

      // Assert correct values upon reset
      assertEquals(Double.NaN, statistics.get(AVERAGE));
      assertEquals(Double.NaN, statistics.get(MINIMUM));
      assertEquals(Double.NaN, statistics.get(MAXIMUM));
      assertEquals(Double.NaN, statistics.get(STDDEV));
      assertEquals(0.0, statistics.get(TOTAL));
      assertEquals(Double.NaN, statistics.get(LATEST));
      assertEquals(0, statistics.get(SAMPLE_COUNT));
   }

   @Test
   public void testStatisticsCalculator()
   {
      // Default statistics object for comparison
      Statistics defaultStatisticsValues = new Statistics();

      // Create a calculator
      StatisticsCalculator calculator = new StatisticsCalculator();

      // Create a statistics object to read data from the calculator
      Statistics statistics = new Statistics();

      // Read from an empty calculator (should leave the statistics as default)
      calculator.read(statistics);
      assertEquals(defaultStatisticsValues.toString(), statistics.toString());

      // Add some values to the calculator
      for (int i = 1; i <= 5; ++i)
      {
         calculator.record(i);
      }

      // Read from calculator with samples, and check that the values are correct
      calculator.read(statistics);

      assertEquals(3, statistics.get(AVERAGE));
      assertEquals(1, statistics.get(MINIMUM));
      assertEquals(5, statistics.get(MAXIMUM));
      assertEquals(Math.sqrt(2.0), statistics.get(STDDEV));
      assertEquals(15, statistics.get(TOTAL));
      assertEquals(5, statistics.get(LATEST));
      assertEquals(5, statistics.get(SAMPLE_COUNT));

      // Reset the calculator and read again. Should return default values
      calculator.reset();
      calculator.read(statistics);
      assertEquals(defaultStatisticsValues.toString(), statistics.toString());
   }
}
