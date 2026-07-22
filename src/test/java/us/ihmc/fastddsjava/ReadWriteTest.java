package us.ihmc.fastddsjava;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.natives.fastddsjava;
import us.ihmc.fastddsjava.natives.fastddsjavaCallback;
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.gen.DataReaderQosPoliciesType;
import us.ihmc.fastddsjava.profiles.gen.DataWriterQosPoliciesType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.ReliabilityQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;
import us.ihmc.fastddsjava.profiles.gen.TopicProfileType;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.fastddsjava.fastddsjavaTestTools.generateRandomBytes;
import static us.ihmc.fastddsjava.fastddsjavaTools.retcodeThrowOnError;
import static us.ihmc.fastddsjava.natives.fastddsjava.CDR_LE;

public class ReadWriteTest
{
   static
   {
      fastddsjavaNativeLibrary.load();

      try
      {
         profile().load();
      }
      catch (fastddsjavaException e)
      {
         throw new RuntimeException(e);
      }
   }

   private static ProfilesXML profile()
   {
      ProfilesXML.setIntraprocessDelivery("FULL");

      ProfilesXML profilesXML = new ProfilesXML();

      ParticipantProfileType participantProfileType = new ParticipantProfileType();
      Rtps rtps = new Rtps();
      rtps.setUseBuiltinTransports(true);
      participantProfileType.setRtps(rtps);
      participantProfileType.setProfileName("unit_test_participant");
      participantProfileType.setDomainId(100);
      profilesXML.addParticipantProfile(participantProfileType);

      TopicProfileType topicProfileType = new TopicProfileType();
      topicProfileType.setProfileName("unit_test_topic");
      profilesXML.addTopicProfile(topicProfileType);

      ReliabilityQosPolicyType reliabilityQosPolicyType = new ReliabilityQosPolicyType();
      reliabilityQosPolicyType.setKind("RELIABLE");

      PublisherProfileType publisherProfileType = new PublisherProfileType();
      publisherProfileType.setProfileName("unit_test_publisher");
      DataWriterQosPoliciesType dataWriterQosPoliciesType = new DataWriterQosPoliciesType();
      dataWriterQosPoliciesType.setReliability(reliabilityQosPolicyType);
      publisherProfileType.setQos(dataWriterQosPoliciesType);
      profilesXML.addPublisherProfile(publisherProfileType);

      SubscriberProfileType subscriberProfileType = new SubscriberProfileType();
      subscriberProfileType.setProfileName("unit_test_subscriber");
      DataReaderQosPoliciesType dataReaderQosPoliciesType = new DataReaderQosPoliciesType();
      dataReaderQosPoliciesType.setReliability(reliabilityQosPolicyType);
      subscriberProfileType.setQos(dataReaderQosPoliciesType);
      profilesXML.addSubscriberProfile(subscriberProfileType);

      return profilesXML;
   }

   private static void cleanupReader(long fastddsParticipant,
                                     long fastddsSubscriber,
                                     long fastddsDataReader,
                                     long fastddsDataReaderListener,
                                     long fastddsPublisher,
                                     long fastddsDataWriter,
                                     long fastddsTopic,
                                     long fastddsTypeSupport,
                                     long fastddsTopicDataWrapperType,
                                     long fastddsTopicDataPublish,
                                     long fastddsTopicDataReceive,
                                     long fastddsSampleInfo) throws fastddsjavaException
   {
      fastddsjava.deleteData(fastddsTopicDataWrapperType, fastddsTopicDataPublish);
      fastddsjava.deleteSampleInfo(fastddsSampleInfo);
      fastddsjava.deleteData(fastddsTopicDataWrapperType, fastddsTopicDataReceive);
      fastddsjava.dataReaderSetListener(fastddsDataReader, 0);
      retcodeThrowOnError(fastddsjava.deleteDataReader(fastddsSubscriber, fastddsDataReader));
      fastddsjava.deleteDataReaderListener(fastddsDataReaderListener);
      retcodeThrowOnError(fastddsjava.deleteSubscriber(fastddsParticipant, fastddsSubscriber));
      retcodeThrowOnError(fastddsjava.deleteDataWriter(fastddsPublisher, fastddsDataWriter));
      retcodeThrowOnError(fastddsjava.deletePublisher(fastddsParticipant, fastddsPublisher));
      retcodeThrowOnError(fastddsjava.deleteTopic(fastddsParticipant, fastddsTopic));
      retcodeThrowOnError(fastddsjava.unregisterType(fastddsParticipant, fastddsjava.topicDataWrapperTypeGetName(fastddsTopicDataWrapperType)));
      fastddsjava.deleteTypesupport(fastddsTypeSupport);
      retcodeThrowOnError(fastddsjava.deleteParticipant(fastddsParticipant));
   }

   @RepeatedTest(100)
   @Timeout(30)
   public void readWriteTestWriteOnce() throws InterruptedException, fastddsjavaException
   {
      final byte[] sampleData = generateRandomBytes(100000);

      long fastddsTopicDataWrapperType = fastddsjava.createTopicDataWrapperType("test_type", CDR_LE);
      long fastddsTypeSupport = fastddsjava.createTypesupport(fastddsTopicDataWrapperType);
      long fastddsParticipant = fastddsjava.createParticipant("unit_test_participant");
      retcodeThrowOnError(fastddsjava.registerType(fastddsParticipant, fastddsTypeSupport));
      long fastddsTopic = fastddsjava.createTopic(fastddsParticipant, fastddsTopicDataWrapperType, "unit_test_topic", "unit_test_topic");
      long fastddsPublisher = fastddsjava.createPublisher(fastddsParticipant, "unit_test_publisher");
      long fastddsDataWriter = fastddsjava.createDataWriter(fastddsPublisher, fastddsTopic, "unit_test_publisher");
      long fastddsSubscriber = fastddsjava.createSubscriber(fastddsParticipant, "unit_test_subscriber");
      long fastddsDataReader = fastddsjava.createDataReader(fastddsSubscriber, fastddsTopic, 0, "unit_test_subscriber");
      long fastddsDataReaderListener = fastddsjava.createDataReaderListener();

      final AtomicBoolean received = new AtomicBoolean(false);
      final AtomicBoolean dataCorrect = new AtomicBoolean(false);

      long fastddsTopicDataReceive = fastddsjava.createData(fastddsTopicDataWrapperType);
      long fastddsSampleInfo = fastddsjava.createSampleInfo();
      fastddsjavaCallback onDataCallback = () ->
      {
         fastddsjava.dataReaderReadNextSample(fastddsDataReader, fastddsTopicDataReceive, fastddsSampleInfo);
         byte[] got = new byte[fastddsjava.topicDataSize(fastddsTopicDataReceive)];
         fastddsjava.topicDataRead(fastddsTopicDataReceive, got, 0, got.length);
         dataCorrect.set(Arrays.equals(sampleData, got));
         received.set(true);
         synchronized (received)
         {
            received.notify();
         }
      };
      fastddsjava.dataReaderListenerSetOnDataAvailable(fastddsDataReaderListener, onDataCallback);
      fastddsjava.dataReaderSetListener(fastddsDataReader, fastddsDataReaderListener);

      long fastddsTopicDataPublish = fastddsjava.createData(fastddsTopicDataWrapperType);
      fastddsjava.topicDataResize(fastddsTopicDataPublish, sampleData.length);
      fastddsjava.topicDataWrite(fastddsTopicDataPublish, sampleData, 0, sampleData.length);

      retcodeThrowOnError(fastddsjava.dataWriterWrite(fastddsDataWriter, fastddsTopicDataPublish));

      if (!received.get())
      {
         synchronized (received)
         {
            received.wait();
         }
      }

      assertTrue(dataCorrect.get());

      cleanupReader(fastddsParticipant, fastddsSubscriber, fastddsDataReader, fastddsDataReaderListener, fastddsPublisher, fastddsDataWriter, fastddsTopic, fastddsTypeSupport, fastddsTopicDataWrapperType,
                    fastddsTopicDataPublish, fastddsTopicDataReceive, fastddsSampleInfo);
   }

   @Test
   @Timeout(30)
   public void readWriteTestWriteNTimes() throws InterruptedException, fastddsjavaException
   {
      final int n = 5000;
      final byte[] sampleData = generateRandomBytes(100000);

      long fastddsTopicDataWrapperType = fastddsjava.createTopicDataWrapperType("test_type", CDR_LE);
      long fastddsTypeSupport = fastddsjava.createTypesupport(fastddsTopicDataWrapperType);
      long fastddsParticipant = fastddsjava.createParticipant("unit_test_participant");
      retcodeThrowOnError(fastddsjava.registerType(fastddsParticipant, fastddsTypeSupport));
      long fastddsTopic = fastddsjava.createTopic(fastddsParticipant, fastddsTopicDataWrapperType, "unit_test_topic", "unit_test_topic");
      long fastddsPublisher = fastddsjava.createPublisher(fastddsParticipant, "unit_test_publisher");
      long fastddsDataWriter = fastddsjava.createDataWriter(fastddsPublisher, fastddsTopic, "unit_test_publisher");
      long fastddsSubscriber = fastddsjava.createSubscriber(fastddsParticipant, "unit_test_subscriber");
      long fastddsDataReader = fastddsjava.createDataReader(fastddsSubscriber, fastddsTopic, 0, "unit_test_subscriber");
      long fastddsDataReaderListener = fastddsjava.createDataReaderListener();

      final AtomicInteger received = new AtomicInteger(0);
      long fastddsTopicDataReceive = fastddsjava.createData(fastddsTopicDataWrapperType);
      long fastddsSampleInfo = fastddsjava.createSampleInfo();
      fastddsjavaCallback onDataCallback = () ->
      {
         fastddsjava.dataReaderReadNextSample(fastddsDataReader, fastddsTopicDataReceive, fastddsSampleInfo);
         if (n == received.incrementAndGet())
         {
            synchronized (received)
            {
               received.notify();
            }
         }
      };
      fastddsjava.dataReaderListenerSetOnDataAvailable(fastddsDataReaderListener, onDataCallback);
      fastddsjava.dataReaderSetListener(fastddsDataReader, fastddsDataReaderListener);

      long fastddsTopicDataPublish = fastddsjava.createData(fastddsTopicDataWrapperType);
      fastddsjava.topicDataResize(fastddsTopicDataPublish, sampleData.length);
      fastddsjava.topicDataWrite(fastddsTopicDataPublish, sampleData, 0, sampleData.length);
      for (int i = 0; i < n; ++i)
      {
         retcodeThrowOnError(fastddsjava.dataWriterWrite(fastddsDataWriter, fastddsTopicDataPublish));
      }

      if (n != received.get())
      {
         synchronized (received)
         {
            received.wait();
         }
      }

      assertEquals(n, received.get());

      cleanupReader(fastddsParticipant, fastddsSubscriber, fastddsDataReader, fastddsDataReaderListener, fastddsPublisher, fastddsDataWriter, fastddsTopic, fastddsTypeSupport, fastddsTopicDataWrapperType,
                    fastddsTopicDataPublish, fastddsTopicDataReceive, fastddsSampleInfo);
   }

   @RepeatedTest(100)
   @Timeout(30)
   public void readWriteTestWithGrowingDataSize() throws InterruptedException, fastddsjavaException
   {
      final int initialDataLength = 1;
      final int finalDataLength = 16384;

      long fastddsTopicDataWrapperType = fastddsjava.createTopicDataWrapperType("test_type", CDR_LE);
      long fastddsTypeSupport = fastddsjava.createTypesupport(fastddsTopicDataWrapperType);
      long fastddsParticipant = fastddsjava.createParticipant("unit_test_participant");
      retcodeThrowOnError(fastddsjava.registerType(fastddsParticipant, fastddsTypeSupport));
      long fastddsTopic = fastddsjava.createTopic(fastddsParticipant, fastddsTopicDataWrapperType, "unit_test_topic", "unit_test_topic");
      long fastddsPublisher = fastddsjava.createPublisher(fastddsParticipant, "unit_test_publisher");
      long fastddsDataWriter = fastddsjava.createDataWriter(fastddsPublisher, fastddsTopic, "unit_test_publisher");
      long fastddsSubscriber = fastddsjava.createSubscriber(fastddsParticipant, "unit_test_subscriber");
      long fastddsDataReader = fastddsjava.createDataReader(fastddsSubscriber, fastddsTopic, 0, "unit_test_subscriber");
      long fastddsDataReaderListener = fastddsjava.createDataReaderListener();

      final AtomicBoolean received = new AtomicBoolean(false);
      final AtomicLong receivedDataLength = new AtomicLong(0L);
      long fastddsTopicDataReceive = fastddsjava.createData(fastddsTopicDataWrapperType);
      long fastddsSampleInfo = fastddsjava.createSampleInfo();
      fastddsjavaCallback onDataCallback = () ->
      {
         fastddsjava.dataReaderTakeNextCustom(fastddsDataReader, fastddsTopicDataReceive, fastddsSampleInfo);
         long dataLength = fastddsjava.topicDataSize(fastddsTopicDataReceive);
         receivedDataLength.set(dataLength);
         if (dataLength == finalDataLength)
         {
            received.set(true);
            synchronized (received)
            {
               received.notify();
            }
         }
      };
      fastddsjava.dataReaderListenerSetOnDataAvailable(fastddsDataReaderListener, onDataCallback);
      fastddsjava.dataReaderSetListener(fastddsDataReader, fastddsDataReaderListener);

      final long fastddsTopicDataPublish = fastddsjava.createData(fastddsTopicDataWrapperType);
      int currentDataLength = initialDataLength;
      do
      {
         byte[] sampleData = generateRandomBytes(currentDataLength);
         synchronized (received)
         {
            fastddsjava.topicDataResize(fastddsTopicDataPublish, sampleData.length);
            fastddsjava.topicDataWrite(fastddsTopicDataPublish, sampleData, 0, sampleData.length);
         }
         retcodeThrowOnError(fastddsjava.dataWriterWrite(fastddsDataWriter, fastddsTopicDataPublish));
         currentDataLength = currentDataLength * 2;
      }
      while (receivedDataLength.get() < finalDataLength);

      if (!received.get())
      {
         synchronized (received)
         {
            received.wait();
         }
      }

      assertEquals(finalDataLength, receivedDataLength.get());

      cleanupReader(fastddsParticipant, fastddsSubscriber, fastddsDataReader, fastddsDataReaderListener, fastddsPublisher, fastddsDataWriter, fastddsTopic, fastddsTypeSupport, fastddsTopicDataWrapperType,
                    fastddsTopicDataPublish, fastddsTopicDataReceive, fastddsSampleInfo);
   }

   @RepeatedTest(100)
   @Timeout(30)
   public void readWriteTestWithRandomDataSize() throws InterruptedException, fastddsjavaException
   {
      Random random = new Random(1881108);
      final int minDataLength = 1;
      final int maxDataLength = 100000;
      final int messagesToSend = 32;

      long fastddsTopicDataWrapperType = fastddsjava.createTopicDataWrapperType("test_type", CDR_LE);
      long fastddsTypeSupport = fastddsjava.createTypesupport(fastddsTopicDataWrapperType);
      long fastddsParticipant = fastddsjava.createParticipant("unit_test_participant");
      retcodeThrowOnError(fastddsjava.registerType(fastddsParticipant, fastddsTypeSupport));
      long fastddsTopic = fastddsjava.createTopic(fastddsParticipant, fastddsTopicDataWrapperType, "unit_test_topic", "unit_test_topic");
      long fastddsPublisher = fastddsjava.createPublisher(fastddsParticipant, "unit_test_publisher");
      long fastddsDataWriter = fastddsjava.createDataWriter(fastddsPublisher, fastddsTopic, "unit_test_publisher");
      long fastddsSubscriber = fastddsjava.createSubscriber(fastddsParticipant, "unit_test_subscriber");
      long fastddsDataReader = fastddsjava.createDataReader(fastddsSubscriber, fastddsTopic, 0, "unit_test_subscriber");
      long fastddsDataReaderListener = fastddsjava.createDataReaderListener();

      final AtomicInteger received = new AtomicInteger(0);
      long fastddsTopicDataReceive = fastddsjava.createData(fastddsTopicDataWrapperType);
      long fastddsSampleInfo = fastddsjava.createSampleInfo();
      fastddsjavaCallback onDataCallback = () ->
      {
         fastddsjava.dataReaderReadNextSample(fastddsDataReader, fastddsTopicDataReceive, fastddsSampleInfo);
         received.incrementAndGet();
         synchronized (received)
         {
            received.notify();
         }
      };
      fastddsjava.dataReaderListenerSetOnDataAvailable(fastddsDataReaderListener, onDataCallback);
      fastddsjava.dataReaderSetListener(fastddsDataReader, fastddsDataReaderListener);

      long fastddsTopicDataWrite = fastddsjava.createData(fastddsTopicDataWrapperType);
      for (int i = 0; i < messagesToSend; ++i)
      {
         // Avoid Random#nextInt(origin, bound) (Java 17); not available on older Android ART builds.
         byte[] sampleData = generateRandomBytes(minDataLength + random.nextInt(maxDataLength - minDataLength));
         fastddsjava.topicDataResize(fastddsTopicDataWrite, sampleData.length);
         fastddsjava.topicDataWrite(fastddsTopicDataWrite, sampleData, 0, sampleData.length);
         retcodeThrowOnError(fastddsjava.dataWriterWrite(fastddsDataWriter, fastddsTopicDataWrite));
      }

      while (received.get() < messagesToSend)
      {
         synchronized (received)
         {
            received.wait();
         }
      }

      assertEquals(messagesToSend, received.get());

      cleanupReader(fastddsParticipant, fastddsSubscriber, fastddsDataReader, fastddsDataReaderListener, fastddsPublisher, fastddsDataWriter, fastddsTopic, fastddsTypeSupport, fastddsTopicDataWrapperType, fastddsTopicDataWrite,
                    fastddsTopicDataReceive, fastddsSampleInfo);
   }
}
