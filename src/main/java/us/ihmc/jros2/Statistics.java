package us.ihmc.jros2;

import java.util.EnumSet;
import java.util.Set;

public class Statistics
{
   /**
    * Includes data types defined in
    * <a href="https://github.com/ros2/rcl_interfaces/blob/humble/statistics_msgs/msg/StatisticDataType.msg">StatisticDataType.msg</a>
    * and a few extra useful data types.
    */
   public enum StatisticDataType
   {
      // Types contained in StatisticDataType.msg
      AVERAGE, MINIMUM, MAXIMUM, STDDEV, SAMPLE_COUNT,
      // Extra types we find useful
      TOTAL, LATEST;

      // Static set and array for garbage-free usage. Treat as read only.
      public static final Set<StatisticDataType> set = EnumSet.allOf(StatisticDataType.class);
      public static final StatisticDataType[] values = values();
   }

   private double average;
   private double min;
   private double max;
   private double standardDeviation;
   private long sampleCount;
   private double total;
   private double latest;

   public Statistics()
   {
      reset();
   }

   /**
    * Resets the statistics object to its default values.
    */
   public void reset()
   {
      average = Double.NaN;
      min = Double.NaN;
      max = Double.NaN;
      standardDeviation = Double.NaN;
      sampleCount = 0;
      total = 0.0;
      latest = Double.NaN;
   }

   /**
    * Getter for statistic values.
    *
    * @param statisticType The statistic type to get.
    * @return The value of the desired statistic type.
    * @implNote The switch statement is used so that future additions of statistic types
    *       are guaranteed to have a getter, as a missing case will cause compilation errors.
    */
   public double get(StatisticDataType statisticType)
   {
      return switch (statisticType)
      {
         case AVERAGE -> average;
         case MINIMUM -> min;
         case MAXIMUM -> max;
         case STDDEV -> standardDeviation;
         case SAMPLE_COUNT -> sampleCount;
         case TOTAL -> total;
         case LATEST -> latest;
      };
   }

   /**
    * Setter for statistic values.
    *
    * @param statisticType The statistic type to set.
    * @param value         The value to set the statistic type.
    * @implNote The switch statement is used so that future additions of statistic types
    *       are guaranteed to have a setter, as a missing case will cause compilation errors.
    */
   void set(StatisticDataType statisticType, double value)
   {
      switch (statisticType)
      {
         case AVERAGE -> average = value;
         case MINIMUM -> min = value;
         case MAXIMUM -> max = value;
         case STDDEV -> standardDeviation = value;
         case SAMPLE_COUNT -> sampleCount = (long) value;
         case TOTAL -> total = value;
         case LATEST -> latest = value;
      }
   }

   @Override
   public String toString()
   {
      StringBuilder builder = new StringBuilder(getClass().getSimpleName());
      builder.append(": {");

      for (int i = 0; i < StatisticDataType.values.length; ++i)
      {
         StatisticDataType statisticType = StatisticDataType.values[i];
         builder.append(statisticType.name()).append("=").append(get(statisticType)).append(", ");
      }
      int lastCommaIndex = builder.lastIndexOf(", ");
      builder.replace(lastCommaIndex, lastCommaIndex + 2, "}");

      return builder.toString();
   }
}
