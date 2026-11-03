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
import us.ihmc.jros2.MessageStatisticsProvider.MessageMetadataType;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.jros2.PubSubTestTools.*;
import static us.ihmc.jros2.Statistics.StatisticDataType.*;

/**
 * Opt-in message statistics of publishers and subscriptions ({@link MessageStatisticsProvider}), checked with values that do
 * not depend on timing: the sizes are the exact CDR payload sizes, the counts follow the published samples, periods start
 * with the second sample (also after a reset), and the age of a message with a header is measured from its stamp.
 */
public class ROS2EndpointStatisticsTest
{
   /** CDR payload of a std_msgs/String: 4-byte encapsulation header, 4-byte length, the characters and a NUL. */
   private static int stringPayloadSize(String data)
   {
      return 4 + 4 + data.length() + 1;
   }

   /** A received payload is {@code serializedSize} bytes, padded by the transport to a multiple of 4 or not. */
   private static void assertPayloadSize(int serializedSize, double receivedSize)
   {
      int padded = (serializedSize + 3) & ~3;
      assertTrue(receivedSize == serializedSize || receivedSize == padded, "received " + receivedSize + " for a payload of " + serializedSize + " bytes");
   }

   private static std_msgs.String_ string(String data)
   {
      std_msgs.String_ message = new std_msgs.String_();
      message.setData(data);
      return message;
   }

   private static double count(MessageStatisticsProvider provider, MessageMetadataType type)
   {
      Statistics statistics = new Statistics();
      provider.readStatistics(type, statistics);
      return statistics.get(SAMPLE_COUNT);
   }

   @Test
   @Timeout(60)
   public void testPublisherAndSubscriptionStatistics()
   {
      ROS2Node node = new ROS2Node("endpoint_statistics");
      try
      {
         ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(uniqueTopicName("endpoint_statistics"), std_msgs.String_.class);
         ROS2Subscription<std_msgs.String_> subscription = node.createSubscription(topic, reliableKeepAll());
         ROS2Publisher<std_msgs.String_> publisher = node.createPublisher(topic, reliableKeepAll());
         awaitMatched(publisher, subscription);

         // Disabled by default: nothing is recorded
         assertFalse(publisher.isStatisticsEnabled());
         assertFalse(subscription.isStatisticsEnabled());
         publisher.publish(string("ignored"));
         awaitCondition(() -> subscription.read() != null, 30_000, "first sample not received");
         for (MessageMetadataType type : MessageMetadataType.values)
         {
            assertEquals(0.0, count(publisher, type), type.name());
            assertEquals(0.0, count(subscription, type), type.name());
         }

         publisher.setStatisticsEnabled(true);
         subscription.setStatisticsEnabled(true);
         assertTrue(publisher.isStatisticsEnabled());
         assertTrue(subscription.isStatisticsEnabled());

         String[] data = {"a", "hello", "xyz"};
         for (String value : data)
         {
            publisher.publish(string(value));
         }
         std_msgs.String_ received = new std_msgs.String_();
         for (String value : data)
         {
            awaitCondition(() -> subscription.read(received), 30_000, "sample '" + value + "' not received");
            assertEquals(value, received.getData().toString());
         }

         for (MessageStatisticsProvider provider : new MessageStatisticsProvider[] {publisher, subscription})
         {
            String name = provider == publisher ? "publisher" : "subscription";
            Statistics size = new Statistics();
            provider.readMessageSizeStatistics(size);
            assertEquals(3.0, size.get(SAMPLE_COUNT), name);
            if (provider == publisher)
            {
               // The serialized payload
               assertEquals(stringPayloadSize("a"), size.get(MINIMUM), name);
               assertEquals(stringPayloadSize("hello"), size.get(MAXIMUM), name);
               assertEquals(stringPayloadSize("xyz"), size.get(LATEST), name);
               assertEquals(stringPayloadSize("a") + stringPayloadSize("hello") + stringPayloadSize("xyz"), size.get(TOTAL), name);
            }
            else
            {
               // The received payload: the same bytes, which the RTPS transports pad to a multiple of 4 (intraprocess
               // delivery hands over the unpadded payload)
               assertPayloadSize(stringPayloadSize("a"), size.get(MINIMUM));
               assertPayloadSize(stringPayloadSize("hello"), size.get(MAXIMUM));
               assertPayloadSize(stringPayloadSize("xyz"), size.get(LATEST));
               double total = stringPayloadSize("a") + stringPayloadSize("hello") + stringPayloadSize("xyz");
               assertTrue(size.get(TOTAL) >= total && size.get(TOTAL) <= total + 6, size.toString());
            }
            assertEquals(size.get(AVERAGE), provider.getMessageSize(AVERAGE), name);

            // Periods start with the second sample
            Statistics period = new Statistics();
            provider.readMessagePeriodStatistics(period);
            assertEquals(2.0, period.get(SAMPLE_COUNT), name);
            assertTrue(period.get(MINIMUM) >= 0.0, name + " " + period);
            assertEquals(period.get(MAXIMUM), provider.getMessagePeriod(MAXIMUM), name);
         }

         // std_msgs/String has no header: publishers record no age, subscriptions measure reception - source
         assertEquals(0.0, count(publisher, MessageMetadataType.AGE));
         Statistics age = new Statistics();
         subscription.readMessageAgeStatistics(age);
         assertEquals(3.0, age.get(SAMPLE_COUNT));
         assertTrue(age.get(MINIMUM) >= 0.0 && age.get(MAXIMUM) < TimeUnit.SECONDS.toMillis(30), age.toString());
         assertEquals(age.get(LATEST), subscription.getMessageAge(LATEST));

         // After a reset the next sample starts over: one size, no period
         publisher.resetStatistics();
         subscription.resetStatistics();
         for (MessageMetadataType type : MessageMetadataType.values)
         {
            assertEquals(0.0, count(publisher, type), type.name());
            assertEquals(0.0, count(subscription, type), type.name());
         }
         publisher.publish(string("after reset"));
         awaitCondition(() -> subscription.read(received), 30_000, "sample after reset not received");
         for (MessageStatisticsProvider provider : new MessageStatisticsProvider[] {publisher, subscription})
         {
            assertEquals(1.0, count(provider, MessageMetadataType.SIZE));
            if (provider == publisher)
               assertEquals(stringPayloadSize("after reset"), provider.getMessageSize(LATEST));
            else
               assertPayloadSize(stringPayloadSize("after reset"), provider.getMessageSize(LATEST));
            assertEquals(0.0, count(provider, MessageMetadataType.PERIOD), "the period restarts after a reset");
         }

         // Disabling keeps the recorded values
         publisher.setStatisticsEnabled(false);
         subscription.setStatisticsEnabled(false);
         publisher.publish(string("not recorded"));
         awaitCondition(() -> subscription.read(received), 30_000, "last sample not received");
         assertEquals(1.0, count(publisher, MessageMetadataType.SIZE));
         assertEquals(1.0, count(subscription, MessageMetadataType.SIZE));
         assertEquals(stringPayloadSize("after reset"), publisher.getStatistic(MessageMetadataType.SIZE, LATEST));
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(60)
   public void testPublisherAgeIsMeasuredFromTheHeaderStamp()
   {
      ROS2Node node = new ROS2Node("endpoint_statistics_age");
      try
      {
         ROS2Topic<geometry_msgs.PoseStamped> topic = new ROS2Topic<>(uniqueTopicName("endpoint_statistics_age"), geometry_msgs.PoseStamped.class);
         ROS2Publisher<geometry_msgs.PoseStamped> publisher = node.createPublisher(topic, reliableKeepAll());
         publisher.setStatisticsEnabled(true);

         geometry_msgs.PoseStamped message = new geometry_msgs.PoseStamped();
         long ageMillis = 5_000;
         for (int i = 0; i < 2; ++i)
         {
            long stampMillis = System.currentTimeMillis() - ageMillis;
            message.getHeader().getStamp().setSec((int) (stampMillis / 1000));
            message.getHeader().getStamp().setNanosec((int) ((stampMillis % 1000) * 1_000_000));
            publisher.publish(message);
         }

         Statistics age = new Statistics();
         publisher.readMessageAgeStatistics(age);
         assertEquals(2.0, age.get(SAMPLE_COUNT));
         // The stamp is truncated to whole milliseconds, so the age is at least the 5 s it was set back
         assertTrue(age.get(MINIMUM) >= ageMillis, age.toString());
         assertTrue(age.get(MAXIMUM) < ageMillis + TimeUnit.SECONDS.toMillis(30), age.toString());
      }
      finally
      {
         node.close();
      }
   }

   /**
    * A hand-written stamped message class that does not implement {@link ROS2Message.WithHeader}: publisher age statistics
    * read its header through reflection (generated stamped messages implement the interface).
    */
   public static final class ReflectiveStamped implements ROS2Message<ReflectiveStamped>
   {
      public static final java.lang.String name = "jros2_test::msg::dds_::ReflectiveStamped_";
      private final std_msgs.Header header = new std_msgs.Header();

      public std_msgs.Header getStampHeader()
      {
         return header;
      }

      @Override
      public int calculateSizeBytes(int currentAlignment)
      {
         return header.calculateSizeBytes(currentAlignment);
      }

      @Override
      public void serialize(us.ihmc.fastddsjava.cdr.CDRBuffer buffer)
      {
         header.serialize(buffer);
      }

      @Override
      public void deserialize(us.ihmc.fastddsjava.cdr.CDRBuffer buffer)
      {
         header.deserialize(buffer);
      }

      @Override
      public void set(ReflectiveStamped from)
      {
         header.set(from.header);
      }
   }

   @Test
   @Timeout(60)
   public void testPublisherAgeOfAMessageClassWithoutWithHeader()
   {
      assertTrue(ROS2Message.WithHeader.class.isAssignableFrom(geometry_msgs.PoseStamped.class), "generated stamped messages implement WithHeader");
      assertFalse(ROS2Message.WithHeader.class.isAssignableFrom(ReflectiveStamped.class));
      ROS2Node node = new ROS2Node("endpoint_statistics_reflective_age");
      try
      {
         ROS2Topic<ReflectiveStamped> topic = new ROS2Topic<>(uniqueTopicName("endpoint_statistics_reflective_age"), ReflectiveStamped.class);
         ROS2Publisher<ReflectiveStamped> publisher = node.createPublisher(topic, reliableKeepAll());
         publisher.setStatisticsEnabled(true);

         ReflectiveStamped message = new ReflectiveStamped();
         long ageMillis = 5_000;
         for (int i = 0; i < 3; ++i)
         {
            long stampMillis = System.currentTimeMillis() - ageMillis;
            message.getStampHeader().getStamp().setSec((int) (stampMillis / 1000));
            message.getStampHeader().getStamp().setNanosec((int) ((stampMillis % 1000) * 1_000_000));
            publisher.publish(message);
         }

         Statistics age = new Statistics();
         publisher.readMessageAgeStatistics(age);
         assertEquals(3.0, age.get(SAMPLE_COUNT));
         assertTrue(age.get(MINIMUM) >= ageMillis, age.toString());
         assertTrue(age.get(MAXIMUM) < ageMillis + TimeUnit.SECONDS.toMillis(30), age.toString());
      }
      finally
      {
         node.close();
      }
   }
}
