package us.ihmc.jros2;

import us.ihmc.jros2.Statistics.StatisticDataType;

import java.util.EnumSet;
import java.util.Set;

interface MessageStatisticsProvider
{
   /**
    * Defines the types of data about messages collected.
    */
   enum MessageData
   {
      SIZE, PERIOD, AGE;

      // Static set and array for garbage free usage. Treat as read only.
      public static final Set<MessageData> set = EnumSet.allOf(MessageData.class);
      public static final MessageData[] values = values();
   }

   /**
    * Reset all statistics gathered.
    */
   void resetStatistics();

   /**
    * Pack the desired {@link MessageData} into the provided {@link Statistics} object.
    * <p>
    * This method is garbage free.
    *
    * @param messageData     The desired message data.
    * @param statisticToPack Object to pack statistics into.
    */
   void readStatistics(MessageData messageData, Statistics statisticToPack);

   /**
    * Get the {@link StatisticDataType} of the desired {@link MessageData} statistics.
    * <p>
    * This method is not garbage free. For garbage free access to the message data statistics,
    * use {@link #readStatistics(MessageData, Statistics)}.
    *
    * @param messageData   The desired message data.
    * @param statisticType The desired statistic type.
    * @return Value of the desired statistic type of message data statistics.
    */
   default double getStatistic(MessageData messageData, StatisticDataType statisticType)
   {
      Statistics statistics = new Statistics();
      readStatistics(messageData, statistics);
      return statistics.get(statisticType);
   }

   /**
    * Packs the message size statistics into the provided {@link Statistics} object.
    * <p>
    * This method is garbage free.
    *
    * @param statisticsToPack Object to pack message size statistics into.
    */
   default void readMessageSizeStatistics(Statistics statisticsToPack)
   {
      readStatistics(MessageData.SIZE, statisticsToPack);
   }

   /**
    * Get the {@link StatisticDataType} of the message size statistics.
    * <p>
    * This method is not garbage free. For garbage free access to the message size statistics,
    * use {@link #readMessageSizeStatistics(Statistics)}.
    *
    * @param statisticType The desired statistic type.
    * @return Value of the desired statistic type of message size statistics.
    */
   default double getMessageSize(StatisticDataType statisticType)
   {
      return getStatistic(MessageData.SIZE, statisticType);
   }

   /**
    * Packs the message period statistics into the provided {@link Statistics} object.
    * <p>
    * This method is garbage free.
    *
    * @param statisticsToPack Object to pack message period statistics into.
    */
   default void readMessagePeriodStatistics(Statistics statisticsToPack)
   {
      readStatistics(MessageData.PERIOD, statisticsToPack);
   }

   /**
    * Get the {@link StatisticDataType} of the message size statistics.
    * <p>
    * This method is not garbage free. For garbage free access to the message size statistics,
    * use {@link #readMessageSizeStatistics(Statistics)}.
    *
    * @param statisticType The desired statistic type.
    * @return Value of the desired statistic type of message size statistics.
    */
   default double getMessagePeriod(StatisticDataType statisticType)
   {
      return getStatistic(MessageData.PERIOD, statisticType);
   }

   /**
    * Packs the message age statistics into the provided {@link Statistics} object.
    * <p>
    * This method is garbage free.
    *
    * @param statisticsToPack Object to pack message age statistics into.
    */
   default void readMessageAgeStatistics(Statistics statisticsToPack)
   {
      readStatistics(MessageData.AGE, statisticsToPack);
   }

   /**
    * Get the {@link StatisticDataType} of the message size statistics.
    * <p>
    * This method is not garbage free. For garbage free access to the message size statistics,
    * use {@link #readMessageSizeStatistics(Statistics)}.
    *
    * @param statisticType The desired statistic type.
    * @return Value of the desired statistic type of message size statistics.
    */
   default double getMessageAge(StatisticDataType statisticType)
   {
      return getStatistic(MessageData.AGE, statisticType);
   }
}
