package us.ihmc.jros2;

import us.ihmc.jros2.Statistics.StatisticDataType;

import java.util.EnumSet;
import java.util.Set;

interface MessageStatisticsProvider
{
   /**
    * Defines the types of data about messages collected.
    */
   enum MessageMetadataType
   {
      SIZE, PERIOD, AGE;

      // Static set and array for garbage-free usage. Treat as read only.
      public static final Set<MessageMetadataType> set = EnumSet.allOf(MessageMetadataType.class);
      public static final MessageMetadataType[] values = values();
   }

   /**
    * Reset all statistics gathered.
    */
   void resetStatistics();

   /**
    * Pack the desired {@link MessageMetadataType} into the provided {@link Statistics} object.
    * <p>
    * This method does not generate garbage.
    *
    * @param messageMetadataType The desired message metadata.
    * @param statisticToPack     Object to pack statistics into.
    */
   void readStatistics(MessageMetadataType messageMetadataType, Statistics statisticToPack);

   /**
    * Get the {@link StatisticDataType} of the desired {@link MessageMetadataType} statistics.
    * <p>
    * This method will generate heap garbage. For garbage-free access to the message metadata statistics,
    * use {@link #readStatistics(MessageMetadataType, Statistics)}.
    *
    * @param messageMetadataType The desired message metadata.
    * @param statisticType       The desired statistic type.
    * @return Value of the desired statistic type of message metadata statistics.
    */
   default double getStatistic(MessageMetadataType messageMetadataType, StatisticDataType statisticType)
   {
      Statistics statistics = new Statistics();
      readStatistics(messageMetadataType, statistics);
      return statistics.get(statisticType);
   }

   /**
    * Packs the message size statistics into the provided {@link Statistics} object.
    * <p>
    * This method does not generate garbage.
    *
    * @param statisticsToPack Object to pack message size statistics into.
    */
   default void readMessageSizeStatistics(Statistics statisticsToPack)
   {
      readStatistics(MessageMetadataType.SIZE, statisticsToPack);
   }

   /**
    * Get the {@link StatisticDataType} of the message size statistics.
    * <p>
    * This method will generate heap garbage. For garbage-free access to the message size statistics,
    * use {@link #readMessageSizeStatistics(Statistics)}.
    *
    * @param statisticType The desired statistic type.
    * @return Value of the desired statistic type of message size statistics.
    */
   default double getMessageSize(StatisticDataType statisticType)
   {
      return getStatistic(MessageMetadataType.SIZE, statisticType);
   }

   /**
    * Packs the message period statistics into the provided {@link Statistics} object.
    * <p>
    * This method does not generate garbage.
    *
    * @param statisticsToPack Object to pack message period statistics into.
    */
   default void readMessagePeriodStatistics(Statistics statisticsToPack)
   {
      readStatistics(MessageMetadataType.PERIOD, statisticsToPack);
   }

   /**
    * Get the {@link StatisticDataType} of the message size statistics.
    * <p>
    * This method will generate heap garbage. For garbage-free access to the message size statistics,
    * use {@link #readMessageSizeStatistics(Statistics)}.
    *
    * @param statisticType The desired statistic type.
    * @return Value of the desired statistic type of message size statistics.
    */
   default double getMessagePeriod(StatisticDataType statisticType)
   {
      return getStatistic(MessageMetadataType.PERIOD, statisticType);
   }

   /**
    * Packs the message age statistics into the provided {@link Statistics} object.
    * <p>
    * This method does not generate garbage.
    *
    * @param statisticsToPack Object to pack message age statistics into.
    */
   default void readMessageAgeStatistics(Statistics statisticsToPack)
   {
      readStatistics(MessageMetadataType.AGE, statisticsToPack);
   }

   /**
    * Get the {@link StatisticDataType} of the message size statistics.
    * <p>
    * This method will generate heap garbage. For garbage-free access to the message size statistics,
    * use {@link #readMessageSizeStatistics(Statistics)}.
    *
    * @param statisticType The desired statistic type.
    * @return Value of the desired statistic type of message size statistics.
    */
   default double getMessageAge(StatisticDataType statisticType)
   {
      return getStatistic(MessageMetadataType.AGE, statisticType);
   }
}
