package us.ihmc.fastddsjava;

import org.bytedeco.javacpp.Pointer;
import org.junit.jupiter.api.RepeatedTest;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.pointers.SampleInfo;
import us.ihmc.fastddsjava.pointers.SubscriptionMatchedStatus;
import us.ihmc.fastddsjava.pointers.fastddsjava_DataReaderListener;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapperType;
import us.ihmc.fastddsjava.profiles.ProfilesHelper;
import us.ihmc.fastddsjava.profiles.ProfilesXML;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.fastddsjava.fastddsjavaTestTools.generateRandomBytes;
import static us.ihmc.fastddsjava.fastddsjavaTools.retcodeThrowOnError;
import static us.ihmc.fastddsjava.pointers.fastddsjava.*;

public class ReadWriteTest
{
   static
   {
      fastddsjavaNativeLibrary.load();

      ProfilesXML profilesXML = ProfilesHelper.unitTestProfile();

      System.out.println(profilesXML.marshall());

      try
      {
         profilesXML.load();
      }
      catch (fastddsjavaException e)
      {
         throw new RuntimeException(e);
      }
   }

   @RepeatedTest(5000)
   public void readWriteTest() throws InterruptedException, fastddsjavaException
   {
      int retCode;
      final byte[] sampleData = generateRandomBytes(100000);

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", CDR_LE);
      Pointer typeSupport = fastddsjava_create_typesupport(topicDataWrapperType);

      Pointer participant = fastddsjava_create_participant("example_participant");

      retCode = fastddsjava_register_type(participant, typeSupport);
      retcodeThrowOnError(retCode);

      Pointer topic = fastddsjava_create_topic(participant, topicDataWrapperType, "example_topic", "example_topic");

      // Publisher
      Pointer publisher = fastddsjava_create_publisher(participant, "example_publisher");
      Pointer dataWriter = fastddsjava_create_datawriter(publisher, topic, "example_publisher");

      // Subscriber
      Pointer subscriber = fastddsjava_create_subscriber(participant, "example_subscriber");
      fastddsjava_DataReaderListener listener = new fastddsjava_DataReaderListener();

      final AtomicBoolean received = new AtomicBoolean(false);
      final AtomicBoolean dataCorrect = new AtomicBoolean(false);

      // Add callback to listener
      Pointer dataReceive = topicDataWrapperType.create_data();
      fastddsjava_TopicDataWrapper topicDataWrapperReceive = new fastddsjava_TopicDataWrapper(dataReceive);
      SampleInfo sampleInfo = new SampleInfo();
      fastddsjava_OnDataCallback onDataCallback = new fastddsjava_OnDataCallback()
      {
         @Override
         public void call(Pointer dataReader)
         {
            synchronized (received)
            {
               fastddsjava_datareader_read_next_sample(dataReader, topicDataWrapperReceive, sampleInfo);
               dataCorrect.set(Arrays.equals(sampleData, topicDataWrapperReceive.data_vector().get()));

               received.set(true);
               received.notify();
            }
         }
      };
      listener.set_on_data_available_callback(onDataCallback);

      // Add subscription callback
      final AtomicInteger numberOfMatches = new AtomicInteger();
      fastddsjava_OnSubscriptionCallback onSubscriptionCallback = new fastddsjava_OnSubscriptionCallback() {
         @Override
         public void call(Pointer dataReader, SubscriptionMatchedStatus info)
         {
            // Record number of matches
            numberOfMatches.set(info.total_count());
         }
      };
      listener.set_on_subscription_callback(onSubscriptionCallback);

      Pointer data = topicDataWrapperType.create_data();
      fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(data);

      // pack wrapper with data
      topicDataWrapper.data_vector().put(sampleData);

      // Create reader with listener
      Pointer dataReader = fastddsjava_create_datareader(subscriber, topic, listener, "example_subscriber");

      // Send the data
      retCode = fastddsjava_datawriter_write(dataWriter, topicDataWrapper);
      retcodeThrowOnError(retCode);

      // Wait to receive data
      synchronized (received)
      {
         if (!received.get())
         {
            received.wait();
         }
      }

      // Assert there was only 1 match
      assertEquals(1, numberOfMatches.get());

      // Assert the data was received correctly
      assertTrue(dataCorrect.get());

      // Delete / release all references
      assertTrue(sampleInfo.releaseReference());
      topicDataWrapperType.delete_data(dataReceive);
      topicDataWrapperType.delete_data(data);
      retcodeThrowOnError(fastddsjava_delete_datareader(subscriber, dataReader));
      assertTrue(onSubscriptionCallback.releaseReference());
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

   @RepeatedTest(5000)
   public void readWriteTestWithGrowingData() throws InterruptedException, fastddsjavaException
   {
      int retCode;
      final int initialDataLength = 1;
      final int finalDataLength = 16384;

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", CDR_LE);
      Pointer typeSupport = fastddsjava_create_typesupport(topicDataWrapperType);

      Pointer participant = fastddsjava_create_participant("example_participant");

      retCode = fastddsjava_register_type(participant, typeSupport);
      retcodeThrowOnError(retCode);

      Pointer topic = fastddsjava_create_topic(participant, topicDataWrapperType, "example_topic", "example_topic");

      // Publisher
      Pointer publisher = fastddsjava_create_publisher(participant, "example_publisher");
      Pointer dataWriter = fastddsjava_create_datawriter(publisher, topic, "example_publisher");

      // Subscriber
      Pointer subscriber = fastddsjava_create_subscriber(participant, "example_subscriber");
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
         public void call(Pointer dataReader)
         {
            synchronized (received)
            {
               fastddsjava_datareader_read_next_sample(dataReader, topicDataWrapperReceive, sampleInfo);

               long dataLength = topicDataWrapperReceive.data_vector().size();

               receivedDataLength.set(dataLength);

               if (dataLength == finalDataLength)
               {
                  received.set(true);
                  received.notify();
               }
            }
         }
      };
      listener.set_on_data_available_callback(onDataCallback);

      // Add subscription callback
      final AtomicInteger numberOfMatches = new AtomicInteger();
      fastddsjava_OnSubscriptionCallback onSubscriptionCallback = new fastddsjava_OnSubscriptionCallback() {
         @Override
         public void call(Pointer dataReader, SubscriptionMatchedStatus info)
         {
            // Record number of matches
            numberOfMatches.set(info.total_count());
         }
      };
      listener.set_on_subscription_callback(onSubscriptionCallback);

      // Create reader with listener
      Pointer dataReader = fastddsjava_create_datareader(subscriber, topic, listener, "example_subscriber");

      // Send the data
      Pointer dataWrite = topicDataWrapperType.create_data();
      fastddsjava_TopicDataWrapper topicDataWrapperWrite = new fastddsjava_TopicDataWrapper(dataWrite);
      Thread writerThread = new Thread(() ->
      {
         int currentDataLength = initialDataLength;

         do
         {
            byte[] sampleData = generateRandomBytes(currentDataLength);
            topicDataWrapperWrite.data_vector().put(sampleData);

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
         }
         while (receivedDataLength.get() < finalDataLength);
      }, "WriterThread");
      writerThread.start();

      // Wait to receive data
      synchronized (received)
      {
         if (!received.get())
         {
            received.wait();
         }
      }

      // Assert there was only 1 match
      assertEquals(1, numberOfMatches.get());

      // Assert that the received data length was equal to the expected final data length
      assertEquals(finalDataLength, receivedDataLength.get());

      // Delete / release all references
      assertTrue(sampleInfo.releaseReference());
      topicDataWrapperType.delete_data(dataReceive);
      topicDataWrapperType.delete_data(dataWrite);
      retcodeThrowOnError(fastddsjava_delete_datareader(subscriber, dataReader));
      assertTrue(onSubscriptionCallback.releaseReference());
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
