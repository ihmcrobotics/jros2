package us.ihmc.jros2;

import geometry_msgs.msg.dds.Point;
import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.jros2.MessageStatisticsProvider.MessageMetadataType;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
      assertEquals(0, statistics.get(SAMPLE_COUNT));
      assertEquals(0.0, statistics.get(TOTAL));
      assertEquals(Double.NaN, statistics.get(LATEST));

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
      statistics.set(SAMPLE_COUNT, expectedCount);
      statistics.set(TOTAL, expectedTotal);
      statistics.set(LATEST, expectedLatest);

      // Assert that values were set correctly
      assertEquals(expectedAverage, statistics.get(AVERAGE));
      assertEquals(expectedMin, statistics.get(MINIMUM));
      assertEquals(expectedMax, statistics.get(MAXIMUM));
      assertEquals(expectedStddev, statistics.get(STDDEV));
      assertEquals(expectedCount, statistics.get(SAMPLE_COUNT));
      assertEquals(expectedTotal, statistics.get(TOTAL));
      assertEquals(expectedLatest, statistics.get(LATEST));

      // Reset the object
      statistics.reset();

      // Assert correct values upon reset
      assertEquals(Double.NaN, statistics.get(AVERAGE));
      assertEquals(Double.NaN, statistics.get(MINIMUM));
      assertEquals(Double.NaN, statistics.get(MAXIMUM));
      assertEquals(Double.NaN, statistics.get(STDDEV));
      assertEquals(0, statistics.get(SAMPLE_COUNT));
      assertEquals(0.0, statistics.get(TOTAL));
      assertEquals(Double.NaN, statistics.get(LATEST));
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
      assertEquals(5, statistics.get(SAMPLE_COUNT));
      assertEquals(15, statistics.get(TOTAL));
      assertEquals(5, statistics.get(LATEST));

      // Reset the calculator and read again. Should return default values
      calculator.reset();
      calculator.read(statistics);
      assertEquals(defaultStatisticsValues.toString(), statistics.toString());
   }

   @Test
   public void testPublisherSubscriptionStatistics() throws InterruptedException
   {
      // Create a publisher
      ROS2Node node = new ROS2Node("test_node");
      ROS2Topic<Point> topic = new ROS2Topic<>("test_topic", Point.class);
      ROS2Publisher<Point> publisher = node.createPublisher(topic);

      // Create the message to publish
      Point message = new Point();
      message.setx(1.0);
      message.sety(2.0);
      message.setz(3.0);

      // Create subscription
      AtomicInteger receivedCount = new AtomicInteger(0);
      ROS2Subscription<Point> subscription = node.createSubscription(topic, reader ->
      {
         Point received = reader.read();
         assertEquals(received.getx(), message.getx());
         assertEquals(received.gety(), message.gety());
         assertEquals(received.getz(), message.getz());
         receivedCount.incrementAndGet();
      });

      // Publish messages at a fixed rate for a few seconds
      AtomicInteger publishCount = new AtomicInteger(0);
      ScheduledExecutorService publishExecutor = Executors.newSingleThreadScheduledExecutor();
      publishExecutor.scheduleAtFixedRate(() ->
      {
         publisher.publish(message);
         publishCount.incrementAndGet();
      }, 0, 100, TimeUnit.MILLISECONDS);

      Thread.sleep(3000);
      publishExecutor.shutdown();

      Statistics statistics = new Statistics();

      // Read the message size statistics from the publisher, and assert they make sense
      double expectedSize = CDRBuffer.PAYLOAD_HEADER.length + message.calculateSizeBytes();
      publisher.readStatistics(MessageMetadataType.SIZE, statistics);
      assertEquals(expectedSize, statistics.get(AVERAGE), 1E-7);
      assertEquals(expectedSize, statistics.get(MINIMUM), 1E-7);
      assertEquals(expectedSize, statistics.get(MAXIMUM), 1E-7);
      assertEquals(0.0, statistics.get(STDDEV), 1E-7);
      assertEquals(publishCount.get(), statistics.get(SAMPLE_COUNT), 1E-7);
      assertEquals(publishCount.get() * expectedSize, statistics.get(TOTAL), 1E-4);
      assertEquals(expectedSize, statistics.get(LATEST), 1E-7);

      // Read the message publish period statistics from the publisher, and assert they make sense
      double expectedPeriod = 100.0;
      publisher.readStatistics(MessageMetadataType.PERIOD, statistics);
      assertEquals(expectedPeriod, statistics.get(AVERAGE), 5.0);
      assertEquals(expectedPeriod, statistics.get(MINIMUM), 2 * 5.0); // The minimum period can have quite substantial outliers
      assertEquals(expectedPeriod, statistics.get(MAXIMUM), 5.0);
      assertEquals(0.0, statistics.get(STDDEV), 1.0);
      assertEquals(publishCount.get() - 1, statistics.get(SAMPLE_COUNT), 1E-7);
      assertEquals((publishCount.get() - 1) * expectedPeriod, statistics.get(TOTAL), 5.0);
      assertEquals(expectedPeriod, statistics.get(LATEST), 5.0);

      // Read the message size statistics from the subscription, and assert they make sense
      subscription.readStatistics(MessageMetadataType.SIZE, statistics);
      assertEquals(expectedSize, statistics.get(AVERAGE), 1E-7);
      assertEquals(expectedSize, statistics.get(MINIMUM), 1E-7);
      assertEquals(expectedSize, statistics.get(MAXIMUM), 1E-7);
      assertEquals(0.0, statistics.get(STDDEV), 1E-7);
      assertEquals(receivedCount.get(), statistics.get(SAMPLE_COUNT), 1E-7);
      assertEquals(receivedCount.get() * expectedSize, statistics.get(TOTAL), 1E-4);
      assertEquals(expectedSize, statistics.get(LATEST), 1E-7);

      // Read the message publish period statistics from the subscription, and assert they make sense
      subscription.readStatistics(MessageMetadataType.PERIOD, statistics);
      assertEquals(expectedPeriod, statistics.get(AVERAGE), 5.0);
      assertEquals(expectedPeriod, statistics.get(MINIMUM), 5.0);
      assertEquals(expectedPeriod, statistics.get(MAXIMUM), 5.0);
      assertEquals(0.0, statistics.get(STDDEV), 1.0);
      assertEquals(receivedCount.get() - 1, statistics.get(SAMPLE_COUNT), 1E-7);
      assertEquals((receivedCount.get() - 1) * expectedPeriod, statistics.get(TOTAL), 5.0);
      assertEquals(expectedPeriod, statistics.get(LATEST), 5.0);

      node.destroyPublisher(publisher);
      node.destroySubscription(subscription);
      node.close();
   }
}
