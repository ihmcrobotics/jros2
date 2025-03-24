package us.ihmc.fastddsjava;

import org.bytedeco.javacpp.Pointer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.pointers.SampleInfo;
import us.ihmc.fastddsjava.pointers.SubscriptionMatchedStatus;
import us.ihmc.fastddsjava.pointers.fastddsjava_DataReaderListener;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapperType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static us.ihmc.fastddsjava.pointers.fastddsjava.*;

public class fastddsjavaTest
{
   static
   {
      fastddsjavaNativeLibrary.load();

      Path xmlPath = Path.of("test_profile.xml");
      try
      {
         String xmlContent = Files.readString(xmlPath);

         fastddsjava_load_xml_profiles_string(xmlContent);
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
   }

   private static byte[] generateRandomBytes(int length)
   {
      byte[] randomBytes = new byte[length];
      new Random(621654).nextBytes(randomBytes);
      return randomBytes;
   }

   @Test
   public void createAndDeleteTopicDataWrapperTest()
   {
      int megabytes = 1;
      int dataLength = 1000000 * megabytes;

      byte[] sampleData = generateRandomBytes(dataLength);

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", (short) 0x0001);
      fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(topicDataWrapperType.create_data());

      topicDataWrapper.data_vector().put(sampleData);

      topicDataWrapperType.delete_data(topicDataWrapper);
   }

   @RepeatedTest(1000)
   public void publishAndSubscribeTest() throws InterruptedException
   {
      int megabytes = 1;
      int dataLength = 1000000 * megabytes;

      byte[] sampleData = generateRandomBytes(dataLength);

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", (short) 0x0001);
      topicDataWrapperType.deallocate(false); // TODO: FIX
      fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(topicDataWrapperType.create_data());

      topicDataWrapper.data_vector().put(sampleData);

      Pointer participant = fastddsjava_create_participant("example_participant");
      fastddsjava_register_type(participant, topicDataWrapperType);

      Pointer topic = fastddsjava_create_topic(participant, topicDataWrapperType, "example_topic", "example_topic");

      // Publisher
      Pointer publisher = fastddsjava_create_publisher(participant, "example_publisher");
      Pointer dataWriter = fastddsjava_create_datawriter(publisher, topic, "example_datawriter");

      // Subscriber
      Pointer subscriber = fastddsjava_create_subscriber(participant, "example_subscriber");
      fastddsjava_DataReaderListener listener = new fastddsjava_DataReaderListener();
      final AtomicBoolean received = new AtomicBoolean(false);
      listener.set_on_data_available_callback(new fastddsjava_OnDataCallback() {
         @Override
         public void call(Pointer dataReader)
         {
            SampleInfo sampleInfo = new SampleInfo();
            fastddsjava_datareader_read_next_sample(dataReader, topicDataWrapper, sampleInfo);

            Assertions.assertArrayEquals(sampleData, topicDataWrapper.data_vector().get());

            synchronized (received)
            {
               received.set(true);
               received.notify();
            }
         }
      });
      listener.set_on_subscription_callback(new fastddsjava_OnSubscriptionCallback() {
         @Override
         public void call(Pointer dataReader, SubscriptionMatchedStatus info)
         {
            // Assert there is 1 match
            Assertions.assertEquals(1, info.total_count());
         }
      });
      Pointer dataReader = fastddsjava_create_datareader(subscriber, topic, listener, "example_datareader");
      fastddsjava_datawriter_write(dataWriter, topicDataWrapper);

      if (!received.get())
      {
         synchronized (received)
         {
            received.wait();
         }
      }

      // Assert that internally the data reader does not have any more history to be read
      Assertions.assertEquals(RETCODE_NO_DATA(), fastddsjava_datareader_read_next_sample(dataReader, topicDataWrapper, new SampleInfo()));

      topicDataWrapperType.delete_data(topicDataWrapper);
      fastddsjava_delete_datawriter(publisher, dataWriter);
      fastddsjava_delete_datareader(subscriber, dataReader);
      fastddsjava_delete_publisher(participant, publisher);
      fastddsjava_delete_subscriber(participant, subscriber);
      fastddsjava_delete_topic(participant, topic);
      fastddsjava_unregister_type(participant, topicDataWrapperType.get_name());
      fastddsjava_delete_participant(participant);
   }

   @Test
   public void growingDataTest() throws InterruptedException
   {
      final int initialSize = 1;
      final int finalSize = 1000;
      final AtomicInteger currentSize = new AtomicInteger(initialSize);

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", (short) 0x0001);
      topicDataWrapperType.deallocate(false); // TODO: FIX

      Pointer participant = fastddsjava_create_participant("example_participant");
      fastddsjava_register_type(participant, topicDataWrapperType);

      Pointer topic = fastddsjava_create_topic(participant, topicDataWrapperType, "example_topic", "example_topic");

      // Publisher
      Pointer publisher = fastddsjava_create_publisher(participant, "example_publisher");
      Pointer dataWriter = fastddsjava_create_datawriter(publisher, topic, "example_datawriter");

      // Subscriber
      Pointer subscriber = fastddsjava_create_subscriber(participant, "example_subscriber");
      fastddsjava_DataReaderListener listener = new fastddsjava_DataReaderListener();
      final AtomicBoolean finished = new AtomicBoolean(false);
      listener.set_on_data_available_callback(new fastddsjava_OnDataCallback() {
         @Override
         public void call(Pointer dataReader)
         {
            fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(topicDataWrapperType.create_data());
            SampleInfo sampleInfo = new SampleInfo();
            fastddsjava_datareader_read_next_sample(dataReader, topicDataWrapper, sampleInfo);

            // Wait to notify until the size is finalSize
            if (topicDataWrapper.data_vector().size() > finalSize)
            {
               synchronized (finished)
               {
                  finished.set(true);
                  finished.notify();
               }
            }

            topicDataWrapperType.delete_data(topicDataWrapper);
         }
      });

      Pointer dataReader = fastddsjava_create_datareader(subscriber, topic, listener, "example_datareader");

      while (!finished.get())
      {
         if (currentSize.get() >= finalSize)
         {
            synchronized (finished)
            {
               finished.wait();
            }
         }
         else
         {
            // Grow the currentSize
            currentSize.set(currentSize.get() * 2);

            fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(topicDataWrapperType.create_data());
            topicDataWrapper.data_vector().put(generateRandomBytes(currentSize.get()));

            fastddsjava_datawriter_write(dataWriter, topicDataWrapper);

            topicDataWrapperType.delete_data(topicDataWrapper);
         }
      }

      fastddsjava_delete_datawriter(publisher, dataWriter);
      fastddsjava_delete_datareader(subscriber, dataReader);
      fastddsjava_delete_publisher(participant, publisher);
      fastddsjava_delete_subscriber(participant, subscriber);
      fastddsjava_delete_topic(participant, topic);
      fastddsjava_unregister_type(participant, topicDataWrapperType.get_name());
      fastddsjava_delete_participant(participant);
   }
}
