package us.ihmc.fastddsjava;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapperType;

import static us.ihmc.fastddsjava.fastddsjavaTestTools.generateRandomBytes;

public class TopicDataWrapperTest
{
   static
   {
      fastddsjavaNativeLibrary.load();
   }

   @RepeatedTest(100)
   public void createAndDeleteTopicDataWrapperTest()
   {
      final int megabytes = 1;
      final int dataLength = 1000000 * megabytes;
      final byte[] sampleData = generateRandomBytes(dataLength);

      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", (short) 0x0001);
      fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(topicDataWrapperType.create_data());
      topicDataWrapper.data_vector().put(sampleData);

      Assertions.assertArrayEquals(sampleData, topicDataWrapper.data_vector().get());

      topicDataWrapperType.delete_data(topicDataWrapper);
      topicDataWrapperType.close();
   }
}
