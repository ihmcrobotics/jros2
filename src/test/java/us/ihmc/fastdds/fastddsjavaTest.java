package us.ihmc.fastdds;

import org.bytedeco.javacpp.Pointer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import us.ihmc.fastdds.library.fastddsNativeLibrary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static us.ihmc.fastdds.global.fastdds.*;

public class fastddsjavaTest
{
   static
   {
      fastddsNativeLibrary.load();

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
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", (short) 0x0001, dataLength);
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
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", (short) 0x0001, dataLength);
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
      listener.set_on_data_available(new fastddsjava_OnDataCallback() {
         @Override
         public void call(fastddsjava_TopicDataWrapper readData, SampleInfo sampleInfo)
         {
            Assertions.assertArrayEquals(sampleData, readData.data_vector().get());

            synchronized (received)
            {
               received.set(true);
               received.notify();
            }
         }
      });
      listener.set_on_subscription_callback(new fastddsjava_OnSubscriptionCallback() {
         @Override
         public void call(SubscriptionMatchedStatus info)
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

      topicDataWrapperType.delete_data(topicDataWrapper);
      fastddsjava_delete_datawriter(publisher, dataWriter);
      fastddsjava_delete_datareader(subscriber, dataReader);
      fastddsjava_delete_publisher(participant, publisher);
      fastddsjava_delete_subscriber(participant, subscriber);
      fastddsjava_delete_topic(participant, topic);
      fastddsjava_unregister_type(participant, topicDataWrapperType.get_name());
      fastddsjava_delete_participant(participant);
   }
}
