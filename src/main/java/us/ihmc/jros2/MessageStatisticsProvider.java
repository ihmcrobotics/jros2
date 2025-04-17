package us.ihmc.jros2;

interface MessageStatisticsProvider
{
   /** @return Total number of received messages for statistic collection. */
   long getNumberOfReceivedMessages();

   /** @return Latest received message payload for statistic collection. */
   long getCurrentMessageSize();

   /** @return Largest ever received message payload for statistic collection. */
   long getLargestMessageSize();

   /** @return Total payload bytes over all received messages for statistic collection and bandwidth calculation. */
   long getCumulativePayloadBytes();
}
