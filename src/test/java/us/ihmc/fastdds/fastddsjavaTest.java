package us.ihmc.fastdds;

import org.bytedeco.javacpp.Pointer;
import org.junit.jupiter.api.Test;
import us.ihmc.fastdds.library.fastddsNativeLibrary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static us.ihmc.fastdds.global.fastdds.*;

public class fastddsjavaTest
{
   @Test
//   @RepeatedTest(50)
   public void createPublisherTest() throws IOException
   {
      fastddsNativeLibrary.load();

      Path xmlPath = Path.of("test_profile.xml");
      String xmlContent = Files.readString(xmlPath);

      int megabytes = 1;
      int size = 1000000 * megabytes;
      byte[] randomBytes = new byte[size];
      new Random().nextBytes(randomBytes);

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", (short) 0x0001, size);
      fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(topicDataWrapperType.create_data());

      System.out.println("topicDataWrapper size " + topicDataWrapper.data_vector().size());

      topicDataWrapper.data_vector().put(randomBytes);

      Pointer participant = fastddsjava_create_participant(xmlContent, "example_participant");
      fastddsjava_register_type(participant, topicDataWrapperType);

      Pointer topic = fastddsjava_create_topic(participant, topicDataWrapperType, "example_topic", "example_topic");

      // Publisher
      Pointer publisher = fastddsjava_create_publisher(participant, "example_publisher");
      Pointer dataWriter = fastddsjava_create_datawriter(publisher, topic, "example_datawriter");

      // Subscriber
      Pointer subscriber = fastddsjava_create_subscriber(participant, "example_subscriber");
      Pointer dataReader = fastddsjava_create_datareader(subscriber, topic, "example_datareader");

      fastddsjava_datawriter_write(dataWriter, topicDataWrapper);
   }
}
