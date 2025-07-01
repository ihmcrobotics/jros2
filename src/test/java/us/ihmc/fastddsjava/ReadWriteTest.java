package us.ihmc.fastddsjava;

import org.bytedeco.javacpp.Pointer;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.pointers.SampleInfo;
import us.ihmc.fastddsjava.pointers.fastddsjava_DataReaderListener;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapperType;
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.TransportDescriptorTypeTools;
import us.ihmc.fastddsjava.profiles.gen.DataReaderQosPoliciesType;
import us.ihmc.fastddsjava.profiles.gen.DataWriterQosPoliciesType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps.UserTransports;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.ReliabilityQosPolicyType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;
import us.ihmc.fastddsjava.profiles.gen.TopicProfileType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorListType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.fastddsjava.fastddsjavaTestTools.generateRandomBytes;
import static us.ihmc.fastddsjava.fastddsjavaTools.retcodeThrowOnError;
import static us.ihmc.fastddsjava.pointers.fastddsjava.*;

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
      ProfilesXML profilesXML = new ProfilesXML();

      final TransportDescriptorType transportDescriptorType;

      // UDP-only on Linux GitHub CI
      boolean udp4Only = System.getProperty("os.name").contains("Linux") && System.getenv().containsKey("GITHUB_ACTIONS");
      // SHM-only on Windows GitHub CI
      boolean shmOnly = System.getProperty("os.name").contains("Windows") && System.getenv().containsKey("GITHUB_ACTIONS");

      // Add participant profile
      ParticipantProfileType participantProfileType = new ParticipantProfileType();

      Rtps rtps = new Rtps();

      boolean builtinTransports = !(udp4Only || shmOnly);

      System.out.println("Builtin transports: " + builtinTransports);
      System.out.println("UDPv4 only: " + udp4Only);
      System.out.println("SHM only: " + shmOnly);
      rtps.setUseBuiltinTransports(builtinTransports);

      if (udp4Only)
      {
         ParticipantProfileType.Rtps.UserTransports userTransports = new UserTransports();
         TransportDescriptorListType transportDescriptorListType = new TransportDescriptorListType();
         transportDescriptorType = TransportDescriptorTypeTools.createUDPv4Descriptor();
         TransportDescriptorTypeTools.setInterfacesWhitelist(transportDescriptorType, "127.0.0.1");
         transportDescriptorListType.getTransportDescriptor().add(transportDescriptorType);
         profilesXML.addTransportDescriptorsProfile(transportDescriptorListType);
         userTransports.getTransportId().add(transportDescriptorType.getTransportId());
         rtps.setUserTransports(userTransports);
      }
      else if (shmOnly)
      {
         ParticipantProfileType.Rtps.UserTransports userTransports = new UserTransports();
         TransportDescriptorListType transportDescriptorListType = new TransportDescriptorListType();
         transportDescriptorType = TransportDescriptorTypeTools.createSHMDescriptor();
         transportDescriptorListType.getTransportDescriptor().add(transportDescriptorType);
         profilesXML.addTransportDescriptorsProfile(transportDescriptorListType);
         userTransports.getTransportId().add(transportDescriptorType.getTransportId());
         rtps.setUserTransports(userTransports);
      }

      participantProfileType.setRtps(rtps);

      participantProfileType.setProfileName("unit_test_participant");
      participantProfileType.setDomainId(100);
      profilesXML.addParticipantProfile(participantProfileType);

      // Add topic profile
      TopicProfileType topicProfileType = new TopicProfileType();
      topicProfileType.setProfileName("unit_test_topic");
      profilesXML.addTopicProfile(topicProfileType);

      // RELIABLE QoS
      ReliabilityQosPolicyType reliabilityQosPolicyType = new ReliabilityQosPolicyType();
      reliabilityQosPolicyType.setKind("RELIABLE");

      // Add publisher profile / AKA data writer profile
      PublisherProfileType publisherProfileType = new PublisherProfileType();
      publisherProfileType.setProfileName("unit_test_publisher");
      DataWriterQosPoliciesType dataWriterQosPoliciesType = new DataWriterQosPoliciesType();
      dataWriterQosPoliciesType.setReliability(reliabilityQosPolicyType);
      publisherProfileType.setQos(dataWriterQosPoliciesType);
      profilesXML.addPublisherProfile(publisherProfileType);

      // Add subscriber profile / AKA data reader profile
      SubscriberProfileType subscriberProfileType = new SubscriberProfileType();
      subscriberProfileType.setProfileName("unit_test_subscriber");
      DataReaderQosPoliciesType dataReaderQosPoliciesType = new DataReaderQosPoliciesType();
      dataReaderQosPoliciesType.setReliability(reliabilityQosPolicyType);
      subscriberProfileType.setQos(dataReaderQosPoliciesType);
      profilesXML.addSubscriberProfile(subscriberProfileType);

      return profilesXML;
   }

   @RepeatedTest(100)
   @Timeout(30)
   public void readWriteTestWriteOnce() throws InterruptedException, fastddsjavaException
   {
      int retCode;
      final byte[] sampleData = generateRandomBytes(100000);

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", CDR_LE);
      Pointer typeSupport = fastddsjava_create_typesupport(topicDataWrapperType);

      Pointer participant = fastddsjava_create_participant("unit_test_participant");

      retCode = fastddsjava_register_type(participant, typeSupport);
      retcodeThrowOnError(retCode);

      Pointer topic = fastddsjava_create_topic(participant, topicDataWrapperType, "unit_test_topic", "unit_test_topic");

      // Publisher
      Pointer publisher = fastddsjava_create_publisher(participant, "unit_test_publisher");
      Pointer dataWriter = fastddsjava_create_datawriter(publisher, topic, "unit_test_publisher");

      // Subscriber
      Pointer subscriber = fastddsjava_create_subscriber(participant, "unit_test_subscriber");
      Pointer dataReader = fastddsjava_create_datareader(subscriber, topic, null, "unit_test_subscriber");

      fastddsjava_DataReaderListener listener = new fastddsjava_DataReaderListener();

      final AtomicBoolean received = new AtomicBoolean(false);
      final AtomicBoolean dataCorrect = new AtomicBoolean(false);

      // Add callback to listener
      Pointer dataReceive = topicDataWrapperType.create_data();
      fastddsjava_TopicDataWrapper topicDataWrapperReceive = new fastddsjava_TopicDataWrapper(dataReceive);
      SampleInfo sampleInfo = new SampleInfo();
      fastddsjava_OnDataCallback onDataCallback = new fastddsjava_OnDataCallback()
      {
         public void call()
         {
            fastddsjava_datareader_take_next_sample(dataReader, topicDataWrapperReceive, sampleInfo);

            dataCorrect.set(Arrays.equals(sampleData, topicDataWrapperReceive.data_vector().get()));
            received.set(true);

            synchronized (received)
            {
               received.notify();
            }
         }
      };
      listener.set_on_data_available_callback(onDataCallback);
      fastddsjava_datareader_set_listener(dataReader, listener);

      Pointer data = topicDataWrapperType.create_data();
      fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(data);

      // Pack wrapper with data
      topicDataWrapper.data_vector().resize(sampleData.length);
      topicDataWrapper.data_ptr().put(sampleData);

      // Send the data
      retCode = fastddsjava_datawriter_write(dataWriter, topicDataWrapper);
      retcodeThrowOnError(retCode);

      if (!received.get())
      {
         // Wait to receive data
         synchronized (received)
         {
            received.wait();
         }
      }

      // Assert the data was received correctly
      assertTrue(dataCorrect.get());

      // Delete / release all references
      assertTrue(sampleInfo.releaseReference());
      topicDataWrapperType.delete_data(dataReceive);
      topicDataWrapperType.delete_data(data);
      retcodeThrowOnError(fastddsjava_delete_datareader(subscriber, dataReader));
      assertTrue(onDataCallback.releaseReference());
      assertTrue(listener.releaseReference());
      retcodeThrowOnError(fastddsjava_delete_subscriber(participant, subscriber));
      retcodeThrowOnError(fastddsjava_delete_datawriter(publisher, dataWriter));
      retcodeThrowOnError(fastddsjava_delete_publisher(participant, publisher));
      retcodeThrowOnError(fastddsjava_delete_topic(participant, topic));
      retcodeThrowOnError(fastddsjava_unregister_type(participant, topicDataWrapperType.get_name()));
      retcodeThrowOnError(fastddsjava_delete_participant(participant));
      assertTrue(topicDataWrapperType.releaseReference());
   }

   @Test
   @Timeout(30)
   public void readWriteTestWriteNTimes() throws InterruptedException, fastddsjavaException
   {
      final int n = 5000;

      int retCode;
      final byte[] sampleData = generateRandomBytes(100000);

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", CDR_LE);
      Pointer typeSupport = fastddsjava_create_typesupport(topicDataWrapperType);

      Pointer participant = fastddsjava_create_participant("unit_test_participant");

      retCode = fastddsjava_register_type(participant, typeSupport);
      retcodeThrowOnError(retCode);

      Pointer topic = fastddsjava_create_topic(participant, topicDataWrapperType, "unit_test_topic", "unit_test_topic");

      // Publisher
      Pointer publisher = fastddsjava_create_publisher(participant, "unit_test_publisher");
      Pointer dataWriter = fastddsjava_create_datawriter(publisher, topic, "unit_test_publisher");

      // Subscriber
      Pointer subscriber = fastddsjava_create_subscriber(participant, "unit_test_subscriber");
      Pointer dataReader = fastddsjava_create_datareader(subscriber, topic, null, "unit_test_subscriber");

      fastddsjava_DataReaderListener listener = new fastddsjava_DataReaderListener();

      final AtomicInteger received = new AtomicInteger(0);

      // Add callback to listener
      Pointer dataReceive = topicDataWrapperType.create_data();
      fastddsjava_TopicDataWrapper topicDataWrapperReceive = new fastddsjava_TopicDataWrapper(dataReceive);
      SampleInfo sampleInfo = new SampleInfo();
      fastddsjava_OnDataCallback onDataCallback = new fastddsjava_OnDataCallback()
      {
         public void call()
         {
            fastddsjava_datareader_read_next_sample(dataReader, topicDataWrapperReceive, sampleInfo);

            if (n == received.incrementAndGet())
            {
               synchronized (received)
               {
                  received.notify();
               }
            }
         }
      };
      listener.set_on_data_available_callback(onDataCallback);
      fastddsjava_datareader_set_listener(dataReader, listener);

      Pointer data = topicDataWrapperType.create_data();
      fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(data);

      // Pack wrapper with data
      topicDataWrapper.data_vector().resize(sampleData.length);
      topicDataWrapper.data_ptr().put(sampleData);

      // Send the data n times
      for (int i = 0; i < n; ++i)
      {
         retCode = fastddsjava_datawriter_write(dataWriter, topicDataWrapper);
         retcodeThrowOnError(retCode);
         // This makes the test more robust especially on Windows
         LockSupport.parkNanos(1);
      }

      if (n != received.get())
      {
         // Wait for the subscription to have received all n samples
         synchronized (received)
         {
            received.wait();
         }
      }

      assertEquals(n, received.get());

      // Delete / release all references
      assertTrue(sampleInfo.releaseReference());
      topicDataWrapperType.delete_data(dataReceive);
      topicDataWrapperType.delete_data(data);
      retcodeThrowOnError(fastddsjava_delete_datareader(subscriber, dataReader));
      assertTrue(onDataCallback.releaseReference());
      assertTrue(listener.releaseReference());
      retcodeThrowOnError(fastddsjava_delete_subscriber(participant, subscriber));
      retcodeThrowOnError(fastddsjava_delete_datawriter(publisher, dataWriter));
      retcodeThrowOnError(fastddsjava_delete_publisher(participant, publisher));
      retcodeThrowOnError(fastddsjava_delete_topic(participant, topic));
      retcodeThrowOnError(fastddsjava_unregister_type(participant, topicDataWrapperType.get_name()));
      retcodeThrowOnError(fastddsjava_delete_participant(participant));
      assertTrue(topicDataWrapperType.releaseReference());
   }

   @RepeatedTest(100)
   @Timeout(30)
   public void readWriteTestWithGrowingDataSize() throws InterruptedException, fastddsjavaException
   {
      int retCode;
      final int initialDataLength = 1;
      final int finalDataLength = 16384;

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", CDR_LE);
      Pointer typeSupport = fastddsjava_create_typesupport(topicDataWrapperType);

      Pointer participant = fastddsjava_create_participant("unit_test_participant");

      retCode = fastddsjava_register_type(participant, typeSupport);
      retcodeThrowOnError(retCode);

      Pointer topic = fastddsjava_create_topic(participant, topicDataWrapperType, "unit_test_topic", "unit_test_topic");

      // Publisher
      Pointer publisher = fastddsjava_create_publisher(participant, "unit_test_publisher");
      Pointer dataWriter = fastddsjava_create_datawriter(publisher, topic, "unit_test_publisher");

      // Subscriber
      Pointer subscriber = fastddsjava_create_subscriber(participant, "unit_test_subscriber");
      Pointer dataReader = fastddsjava_create_datareader(subscriber, topic, null, "unit_test_subscriber");
      fastddsjava_DataReaderListener listener = new fastddsjava_DataReaderListener();

      final AtomicBoolean received = new AtomicBoolean(false);
      final AtomicLong receivedDataLength = new AtomicLong(0L);

      // Add callback to listener
      Pointer dataReceive = topicDataWrapperType.create_data();
      fastddsjava_TopicDataWrapper topicDataWrapperReceive = new fastddsjava_TopicDataWrapper(dataReceive);
      SampleInfo sampleInfo = new SampleInfo();
      fastddsjava_OnDataCallback onDataCallback = new fastddsjava_OnDataCallback()
      {
         @Override
         public void call()
         {
            fastddsjava_datareader_read_next_sample(dataReader, topicDataWrapperReceive, sampleInfo);

            long dataLength = topicDataWrapperReceive.data_vector().size();

            receivedDataLength.set(dataLength);

            if (dataLength == finalDataLength)
            {
               received.set(true);

               synchronized (received)
               {
                  received.notify();
               }
            }
         }
      };
      listener.set_on_data_available_callback(onDataCallback);
      fastddsjava_datareader_set_listener(dataReader, listener);

      // Send the data
      Pointer dataWrite = topicDataWrapperType.create_data();
      fastddsjava_TopicDataWrapper topicDataWrapperWrite = new fastddsjava_TopicDataWrapper(dataWrite);
      Thread writerThread = new Thread(() ->
      {
         int currentDataLength = initialDataLength;

         do
         {
            byte[] sampleData = generateRandomBytes(currentDataLength);

            topicDataWrapperWrite.data_vector().resize(sampleData.length);
            topicDataWrapperWrite.data_vector().put(sampleData);
//            topicDataWrapperWrite.data_ptr().put(sampleData);

            int writerRetCode;
            writerRetCode = fastddsjava_datawriter_write(dataWriter, topicDataWrapperWrite);

            try
            {
               retcodeThrowOnError(writerRetCode);
            }
            catch (fastddsjavaException e)
            {
               throw new RuntimeException(e);
            }

            // Grow the data length
            currentDataLength = currentDataLength * 2;

            // This makes the test more robust especially on Windows
            LockSupport.parkNanos(1);
         }
         while (receivedDataLength.get() < finalDataLength);
      }, "WriterThread");
      writerThread.start();

      if (!received.get())
      {
         // Wait to receive data
         synchronized (received)
         {
            received.wait();
         }
      }

      // Assert that the received data length was equal to the expected final data length
      assertEquals(finalDataLength, receivedDataLength.get());

      // Delete / release all references
      assertTrue(sampleInfo.releaseReference());
      topicDataWrapperType.delete_data(dataReceive);
      topicDataWrapperType.delete_data(dataWrite);
      retcodeThrowOnError(fastddsjava_delete_datareader(subscriber, dataReader));
      assertTrue(onDataCallback.releaseReference());
      assertTrue(listener.releaseReference());
      retcodeThrowOnError(fastddsjava_delete_subscriber(participant, subscriber));
      retcodeThrowOnError(fastddsjava_delete_datawriter(publisher, dataWriter));
      retcodeThrowOnError(fastddsjava_delete_publisher(participant, publisher));
      retcodeThrowOnError(fastddsjava_delete_topic(participant, topic));
      retcodeThrowOnError(fastddsjava_unregister_type(participant, topicDataWrapperType.get_name()));
      retcodeThrowOnError(fastddsjava_delete_participant(participant));
      assertTrue(topicDataWrapperType.releaseReference());
   }

   @RepeatedTest(100)
   @Timeout(30)
   public void readWriteTestWithRandomDataSize() throws InterruptedException, fastddsjavaException
   {
      Random random = new Random();

      int retCode;
      final int minDataLength = 1;
      final int maxDataLength = 100000;
      final int messagesToSend = 32;

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", CDR_LE);
      Pointer typeSupport = fastddsjava_create_typesupport(topicDataWrapperType);

      Pointer participant = fastddsjava_create_participant("unit_test_participant");

      retCode = fastddsjava_register_type(participant, typeSupport);
      retcodeThrowOnError(retCode);

      Pointer topic = fastddsjava_create_topic(participant, topicDataWrapperType, "unit_test_topic", "unit_test_topic");

      // Publisher
      Pointer publisher = fastddsjava_create_publisher(participant, "unit_test_publisher");
      Pointer dataWriter = fastddsjava_create_datawriter(publisher, topic, "unit_test_publisher");

      // Subscriber
      Pointer subscriber = fastddsjava_create_subscriber(participant, "unit_test_subscriber");
      Pointer dataReader = fastddsjava_create_datareader(subscriber, topic, null, "unit_test_subscriber");
      fastddsjava_DataReaderListener listener = new fastddsjava_DataReaderListener();

      final AtomicInteger received = new AtomicInteger(0);

      // Add callback to listener
      Pointer dataReceive = topicDataWrapperType.create_data();
      fastddsjava_TopicDataWrapper topicDataWrapperReceive = new fastddsjava_TopicDataWrapper(dataReceive);
      SampleInfo sampleInfo = new SampleInfo();
      fastddsjava_OnDataCallback onDataCallback = new fastddsjava_OnDataCallback()
      {
         @Override
         public void call()
         {
            fastddsjava_datareader_read_next_sample(dataReader, topicDataWrapperReceive, sampleInfo);

            received.incrementAndGet();

            synchronized (received)
            {
               received.notify();
            }
         }
      };
      listener.set_on_data_available_callback(onDataCallback);
      fastddsjava_datareader_set_listener(dataReader, listener);

      // Send the data
      Pointer dataWrite = topicDataWrapperType.create_data();
      fastddsjava_TopicDataWrapper topicDataWrapperWrite = new fastddsjava_TopicDataWrapper(dataWrite);
      Thread writerThread = new Thread(() ->
      {
         for (int i = 0; i < messagesToSend; ++i)
         {
            byte[] sampleData = generateRandomBytes(random.nextInt(minDataLength, maxDataLength));
            topicDataWrapperWrite.data_vector().resize(sampleData.length);
            topicDataWrapperWrite.data_ptr().put(sampleData);

            int writerRetCode;
            writerRetCode = fastddsjava_datawriter_write(dataWriter, topicDataWrapperWrite);
            try
            {
               retcodeThrowOnError(writerRetCode);
            }
            catch (fastddsjavaException e)
            {
               throw new RuntimeException(e);
            }
         }
      }, "WriterThread");
      writerThread.start();

      while (received.get() < messagesToSend)
      {
         // Wait to receive data
         synchronized (received)
         {
            received.wait();
         }
      }

      // Assert that the received data length was equal to the expected final data length
      assertEquals(messagesToSend, received.get());

      // Delete / release all references
      assertTrue(sampleInfo.releaseReference());
      topicDataWrapperType.delete_data(dataReceive);
      topicDataWrapperType.delete_data(dataWrite);
      retcodeThrowOnError(fastddsjava_delete_datareader(subscriber, dataReader));
      assertTrue(onDataCallback.releaseReference());
      assertTrue(listener.releaseReference());
      retcodeThrowOnError(fastddsjava_delete_subscriber(participant, subscriber));
      retcodeThrowOnError(fastddsjava_delete_datawriter(publisher, dataWriter));
      retcodeThrowOnError(fastddsjava_delete_publisher(participant, publisher));
      retcodeThrowOnError(fastddsjava_delete_topic(participant, topic));
      retcodeThrowOnError(fastddsjava_unregister_type(participant, topicDataWrapperType.get_name()));
      retcodeThrowOnError(fastddsjava_delete_participant(participant));
      assertTrue(topicDataWrapperType.releaseReference());
   }
}
