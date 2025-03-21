package us.ihmc.fastdds;

import org.bytedeco.javacpp.Pointer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import us.ihmc.fastdds.library.fastddsNativeLibrary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static us.ihmc.fastdds.global.fastdds.*;

public class fastddsjavaTest
{
   private static byte[] generateRandomBytes(int length)
   {
      byte[] randomBytes = new byte[length];
      new Random().nextBytes(randomBytes);
      return randomBytes;
   }

   @Test
//   @RepeatedTest(50)
   public void createPublisherTest() throws IOException
   {
      fastddsNativeLibrary.load();

      Path xmlPath = Path.of("test_profile.xml");
      String xmlContent = Files.readString(xmlPath);

      int megabytes = 1;
      int dataLength = 1000000 * megabytes;

      byte[] sampleData = generateRandomBytes(dataLength);

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", (short) 0x0001, dataLength);
      fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(topicDataWrapperType.create_data());

      topicDataWrapper.data_vector().put(sampleData);

      Pointer participant = fastddsjava_create_participant(xmlContent, "example_participant");
      fastddsjava_register_type(participant, topicDataWrapperType);

      Pointer topic = fastddsjava_create_topic(participant, topicDataWrapperType, "example_topic", "example_topic");

      // Publisher
      Pointer publisher = fastddsjava_create_publisher(participant, "example_publisher");
      Pointer dataWriter = fastddsjava_create_datawriter(publisher, topic, "example_datawriter");

      // Subscriber
      Pointer subscriber = fastddsjava_create_subscriber(participant, "example_subscriber");
      fastddsjava_DataReaderListener listener = new fastddsjava_DataReaderListener();
      listener.set_callback(new fastddsjava_DataReaderListenerCallback() {
         @Override
         public void call(fastddsjava_TopicDataWrapper readData, SampleInfo sampleInfo)
         {
            Assertions.assertArrayEquals(sampleData, readData.data_vector().get());
         }
      });
      Pointer dataReader = fastddsjava_create_datareader(subscriber, topic, listener, "example_datareader");

      fastddsjava_datawriter_write(dataWriter, topicDataWrapper);
   }
}
