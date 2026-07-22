package us.ihmc.fastddsjava;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.natives.fastddsjava;

import java.nio.ByteBuffer;

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

      long fastddsTopicDataWrapperType = fastddsjava.createTopicDataWrapperType("test_type", (short) 0x0001);
      long fastddsTypeSupport = fastddsjava.createTypesupport(fastddsTopicDataWrapperType);
      long fastddsTopicData = fastddsjava.createData(fastddsTopicDataWrapperType);
      fastddsjava.topicDataResize(fastddsTopicData, sampleData.length);
      fastddsjava.topicDataWrite(fastddsTopicData, sampleData, 0, sampleData.length);

      byte[] got = new byte[fastddsjava.topicDataSize(fastddsTopicData)];
      fastddsjava.topicDataRead(fastddsTopicData, got, 0, got.length);
      Assertions.assertArrayEquals(sampleData, got);

      fastddsjava.deleteData(fastddsTopicDataWrapperType, fastddsTopicData);
      fastddsjava.deleteTypesupport(fastddsTypeSupport);
   }

   @Test
   public void topicDataDirectBufferRoundTripTest()
   {
      final byte[] sampleData = generateRandomBytes(1024);
      ByteBuffer writeBuffer = ByteBuffer.allocateDirect(sampleData.length);
      writeBuffer.put(sampleData);
      writeBuffer.flip();

      long fastddsTopicDataWrapperType = fastddsjava.createTopicDataWrapperType("test_type_direct", (short) 0x0001);
      long fastddsTypeSupport = fastddsjava.createTypesupport(fastddsTopicDataWrapperType);
      long fastddsTopicData = fastddsjava.createData(fastddsTopicDataWrapperType);
      fastddsjava.topicDataResize(fastddsTopicData, sampleData.length);
      fastddsjava.topicDataWriteBuffer(fastddsTopicData, writeBuffer, 0, sampleData.length);

      ByteBuffer readBuffer = ByteBuffer.allocateDirect(sampleData.length);
      fastddsjava.topicDataReadBuffer(fastddsTopicData, readBuffer, 0, sampleData.length);

      byte[] got = new byte[sampleData.length];
      readBuffer.get(got);
      Assertions.assertArrayEquals(sampleData, got);

      fastddsjava.deleteData(fastddsTopicDataWrapperType, fastddsTopicData);
      fastddsjava.deleteTypesupport(fastddsTypeSupport);
   }
}
