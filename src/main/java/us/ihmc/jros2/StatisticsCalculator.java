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
package us.ihmc.jros2;

import static us.ihmc.jros2.Statistics.StatisticDataType.*;

class StatisticsCalculator
{
   private double average;
   private double min;
   private double max;
   private double m2; // M2 from Welford's online algorithm (https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Welford's_online_algorithm)
   private long sampleCount;
   private double total;
   private double latest;

   /**
    * Readers (any thread) and the recording endpoint exclude each other. Not a ReadWriteLock: a contended
    * ReentrantReadWriteLock allocates queue nodes, and its read lock allocates a hold counter per reading thread whenever
    * two readers overlap.
    */
   private final AllocationFreeLock lock;

   StatisticsCalculator()
   {
      lock = new AllocationFreeLock();
      reset();
   }

   /**
    * Read the current values of statistics.
    * If there are no samples, the {@link Statistics} object will be set to its default values.
    *
    * @param statisticsToPack The {@link Statistics} object to pack current values into.
    */
   void read(Statistics statisticsToPack)
   {
      if (statisticsToPack != null)
      {
         statisticsToPack.reset();

         lock.lock();
         try
         {
            if (sampleCount > 0)
            {
               statisticsToPack.set(AVERAGE, average);
               statisticsToPack.set(MINIMUM, min);
               statisticsToPack.set(MAXIMUM, max);
               statisticsToPack.set(STDDEV, Math.sqrt(m2 / sampleCount));
               statisticsToPack.set(SAMPLE_COUNT, sampleCount);
               statisticsToPack.set(TOTAL, total);
               statisticsToPack.set(LATEST, latest);
            }
         }
         finally
         {
            lock.unlock();
         }
      }
   }

   /**
    * Reads one statistic without allocating: the value that {@link #read(Statistics)} would pack for
    * {@code statisticType}.
    */
   double get(Statistics.StatisticDataType statisticType)
   {
      lock.lock();
      try
      {
         if (sampleCount == 0)
         {
            // The defaults of Statistics.reset()
            return statisticType == SAMPLE_COUNT || statisticType == TOTAL ? 0.0 : Double.NaN;
         }
         return switch (statisticType)
         {
            case AVERAGE -> average;
            case MINIMUM -> min;
            case MAXIMUM -> max;
            case STDDEV -> Math.sqrt(m2 / sampleCount);
            case SAMPLE_COUNT -> sampleCount;
            case TOTAL -> total;
            case LATEST -> latest;
         };
      }
      finally
      {
         lock.unlock();
      }
   }

   /**
    * Reset the statistics.
    */
   void reset()
   {
      lock.lock();
      try
      {
         average = 0.0;
         min = Double.MAX_VALUE;
         max = -Double.MAX_VALUE; // not Double.MIN_VALUE, the smallest positive double: recorded values can be negative
         m2 = 0.0;
         sampleCount = 0;
         total = 0.0;
         latest = Double.NaN;
      }
      finally
      {
         lock.unlock();
      }
   }

   void record(double value)
   {
      if (Double.isFinite(value))
      {
         lock.lock();
         try
         {
            ++sampleCount;
            double previousAverage = average;
            average = previousAverage + ((value - previousAverage) / sampleCount);
            min = Math.min(min, value);
            max = Math.max(max, value);
            m2 = m2 + (value - previousAverage) * (value - average);
            total += value;
            latest = value;
         }
         finally
         {
            lock.unlock();
         }
      }
   }

   @Override
   public String toString()
   {
      lock.lock();
      try
      {
         return HotPathText.statisticsCalculatorToString(getClass().getSimpleName(),
                                                         average,
                                                         min,
                                                         max,
                                                         Math.sqrt(m2 / sampleCount),
                                                         (double) sampleCount,
                                                         total,
                                                         latest);
      }
      finally
      {
         lock.unlock();
      }
   }
}
