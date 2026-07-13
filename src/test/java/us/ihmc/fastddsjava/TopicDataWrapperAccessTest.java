package us.ihmc.fastddsjava;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapperType;

import java.util.Arrays;

import static us.ihmc.fastddsjava.fastddsjavaTestTools.generateRandomBytes;

public class TopicDataWrapperAccessTest
{
   static
   {
      fastddsjavaNativeLibrary.load();
   }

   @Test
   public void sizeResizeAndCopyRoundTrip()
   {
      byte[] sampleData = generateRandomBytes(4096);

      fastddsjava_TopicDataWrapperType type = new fastddsjava_TopicDataWrapperType("access_test_type", (short) 0x0001);
      fastddsjava_TopicDataWrapper wrapper = new fastddsjava_TopicDataWrapper(type.create_data());
      TopicDataWrapperAccess access = new TopicDataWrapperAccess(wrapper);

      access.resize(sampleData.length);
      Assertions.assertEquals(sampleData.length, access.size());

      access.copyFromHeap(sampleData, 0, sampleData.length);

      byte[] viaLegacy = new byte[sampleData.length];
      wrapper.data_ptr().get(viaLegacy);
      Assertions.assertArrayEquals(sampleData, viaLegacy);

      byte[] viaAccess = new byte[sampleData.length];
      access.copyToHeap(viaAccess, 0, sampleData.length);
      Assertions.assertArrayEquals(sampleData, viaAccess);

      // Resize smaller then larger to ensure data pointer retargeting after reallocation
      access.resize(16);
      Assertions.assertEquals(16, access.size());
      byte[] small = generateRandomBytes(16);
      access.copyFromHeap(small, 0, 16);
      byte[] smallOut = new byte[16];
      access.copyToHeap(smallOut, 0, 16);
      Assertions.assertArrayEquals(small, smallOut);

      access.resize(sampleData.length);
      access.copyFromHeap(sampleData, 0, sampleData.length);
      Arrays.fill(viaAccess, (byte) 0);
      access.copyToHeap(viaAccess, 0, sampleData.length);
      Assertions.assertArrayEquals(sampleData, viaAccess);

      type.delete_data(wrapper);
      type.close();
   }

   @Test
   public void steadyStateCopyAllocatesFarLessThanLegacyWrappers()
   {
      byte[] sampleData = generateRandomBytes(1024);

      fastddsjava_TopicDataWrapperType type = new fastddsjava_TopicDataWrapperType("access_alloc_test_type", (short) 0x0001);
      fastddsjava_TopicDataWrapper wrapper = new fastddsjava_TopicDataWrapper(type.create_data());
      TopicDataWrapperAccess access = new TopicDataWrapperAccess(wrapper);
      access.resize(sampleData.length);
      access.copyFromHeap(sampleData, 0, sampleData.length);

      byte[] dst = new byte[sampleData.length];
      final int iterations = 10_000;

      // Warm up
      for (int i = 0; i < 1000; i++)
      {
         access.copyToHeap(dst, 0, (int) access.size());
         wrapper.data_ptr().get(dst, 0, sampleData.length);
      }

      long accessBytes = measureAllocatedBytes(() ->
      {
         for (int i = 0; i < iterations; i++)
         {
            long size = access.size();
            access.copyToHeap(dst, 0, (int) size);
         }
      });

      long legacyBytes = measureAllocatedBytes(() ->
      {
         for (int i = 0; i < iterations; i++)
         {
            long size = wrapper.data_vector().size();
            wrapper.data_ptr().get(dst, 0, (int) size);
         }
      });

      Assertions.assertTrue(legacyBytes > 100_000,
                            "Expected legacy data_vector/data_ptr path to allocate; got " + legacyBytes + " bytes");
      Assertions.assertTrue(accessBytes < legacyBytes / 50,
                            "Access path allocated " + accessBytes + " bytes vs legacy " + legacyBytes);
      Assertions.assertTrue(accessBytes < 4096,
                            "Access path should be essentially allocation-free; got " + accessBytes + " bytes");

      type.delete_data(wrapper);
      type.close();
   }

   private static long measureAllocatedBytes(Runnable runnable)
   {
      com.sun.management.ThreadMXBean sunBean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
      Assertions.assertTrue(sunBean.isThreadAllocatedMemorySupported());
      if (!sunBean.isThreadAllocatedMemoryEnabled())
         sunBean.setThreadAllocatedMemoryEnabled(true);

      long threadId = Thread.currentThread().getId();
      long before = sunBean.getThreadAllocatedBytes(threadId);
      runnable.run();
      long after = sunBean.getThreadAllocatedBytes(threadId);
      return Math.max(0L, after - before);
   }
}
