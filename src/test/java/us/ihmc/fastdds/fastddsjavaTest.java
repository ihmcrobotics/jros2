package us.ihmc.fastdds;

import org.bytedeco.javacpp.Pointer;
import org.junit.jupiter.api.Test;
import us.ihmc.fastdds.library.fastddsNativeLibrary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static us.ihmc.fastdds.global.fastdds.*;

public class fastddsjavaTest
{
   @Test
   public void createPublisherTest() throws IOException, InterruptedException
   {
      fastddsNativeLibrary.load();

      Path xmlPath = Path.of("test_profile.xml");
      String xmlContent = Files.readString(xmlPath);

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", (short) 0x0001, 64);
      fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(topicDataWrapperType.create_data());
      topicDataWrapper.data_ptr().asByteBuffer().put((byte) 46);
      System.out.println("topicDataWrapper size " + topicDataWrapper.data_vector().size());

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

      Thread.sleep(1000);
   }
}
