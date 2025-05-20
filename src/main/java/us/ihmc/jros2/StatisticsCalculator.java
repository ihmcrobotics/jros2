package us.ihmc.jros2;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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

   private final ReadWriteLock lock;

   StatisticsCalculator()
   {
      lock = new ReentrantReadWriteLock();
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

         if (sampleCount > 0)
         {
            lock.readLock().lock();
            statisticsToPack.set(AVERAGE, average);
            statisticsToPack.set(MINIMUM, min);
            statisticsToPack.set(MAXIMUM, max);
            statisticsToPack.set(STDDEV, Math.sqrt(m2 / sampleCount));
            statisticsToPack.set(SAMPLE_COUNT, sampleCount);
            statisticsToPack.set(TOTAL, total);
            statisticsToPack.set(LATEST, latest);
            lock.readLock().unlock();
         }
      }
   }

   /**
    * Reset the statistics.
    */
   void reset()
   {
      lock.writeLock().lock();
      average = 0.0;
      min = Double.MAX_VALUE;
      max = Double.MIN_VALUE;
      m2 = 0.0;
      sampleCount = 0;
      total = 0.0;
      latest = Double.NaN;
      lock.writeLock().unlock();
   }

   void record(double value)
   {
      if (Double.isFinite(value))
      {
         lock.writeLock().lock();
         ++sampleCount;
         double previousAverage = average;
         average = previousAverage + ((value - previousAverage) / sampleCount);
         min = Math.min(min, value);
         max = Math.max(max, value);
         m2 = m2 + (value - previousAverage) * (value - average);
         total += value;
         latest = value;
         lock.writeLock().unlock();
      }
   }

   @Override
   public String toString()
   {
      return "%s: {AVERAGE=%f, MINIMUM=%f, MAXIMUM=%f, STDDEV=%f, SAMPLE_COUNT=%f, TOTAL=%f, LATEST=%f}".formatted(getClass().getSimpleName(),
                                                                                                                   average,
                                                                                                                   min,
                                                                                                                   max,
                                                                                                                   Math.sqrt(m2 / sampleCount),
                                                                                                                   (double) sampleCount,
                                                                                                                   total,
                                                                                                                   latest);
   }
}
