/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.fastddsjava.natives;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.natives.NativeDdsFixture.Topic;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static us.ihmc.fastddsjava.fastddsjavaTestTools.waitFor;
import static us.ihmc.fastddsjava.natives.NativeDdsFixture.*;
import static us.ihmc.fastddsjava.natives.fastddsjava.*;

/**
 * Native publish / take paths: the ABI v2 zero-copy write and take-into-buffer functions, the ABI v1 functions they
 * replace, payload bounds, samples without data, profile QoS and argument validation.
 */
public class NativeDataPathTest
{
   /** fastddsjava.h: InstanceStateKind NOT_ALIVE_NO_WRITERS. */
   private static final short NOT_ALIVE_NO_WRITERS_INSTANCE_STATE = 4;

   @BeforeAll
   public static void loadNatives()
   {
      assertTrue(fastddsjavaNativeLibrary.load(), "native libraries must load");
   }

   private static void assumeAbiV2()
   {
      assumeTrue(abiVersion() >= ABI_V2, "requires ABI v2 natives (run with JROS2_NATIVE_PATH)");
   }

   /** Takes one sample with the API of the loaded ABI. Returns its bytes, or null if none arrived in time. */
   private static byte[] takeOne(long reader, long topicData, long sampleInfo, long timeoutMillis) throws InterruptedException
   {
      AtomicReference<byte[]> sample = new AtomicReference<>();
      ByteBuffer dst = ByteBuffer.allocateDirect(256);
      waitFor(() ->
              {
                 if (abiVersion() >= ABI_V2)
                 {
                    int result = dataReaderTakeNextToBuffer(reader, topicData, sampleInfo, dst, dst.capacity());
                    if (result >= 0)
                    {
                       if (result <= dst.capacity())
                       {
                          sample.set(bytes(dst, result));
                       }
                       else
                       {
                          ByteBuffer large = ByteBuffer.allocateDirect(result);
                          topicDataReadBuffer(topicData, large, 0, result);
                          sample.set(bytes(large, result));
                       }
                    }
                    else
                    {
                       assertTrue(result == TAKE_NO_DATA || result == TAKE_INVALID_SAMPLE, "take failed: " + result);
                    }
                 }
                 else if (dataReaderTakeNextCustom(reader, topicData, sampleInfo) == RETCODE_OK && sampleInfoValidData(sampleInfo))
                 {
                    byte[] bytes = new byte[topicDataSize(topicData)];
                    topicDataRead(topicData, bytes, 0, bytes.length);
                    sample.set(bytes);
                 }
                 return sample.get() != null;
              }, timeoutMillis);
      return sample.get();
   }

   /** Writes with the API of the loaded ABI. */
   private static int writeOne(long writer, long topicData, byte[] content)
   {
      if (abiVersion() >= ABI_V2)
         return writeBuffer(writer, direct(content, content.length), content.length);
      topicDataResize(topicData, content.length);
      topicDataWrite(topicData, content, 0, content.length);
      return dataWriterWrite(writer, topicData);
   }

   @Test
   @Timeout(60)
   public void zeroCopyWriteAndTakeIntoDirectBuffer() throws Exception
   {
      assumeAbiV2();
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_zero_copy");
         long writer = fixture.writer(topic, RELIABLE);
         long reader = fixture.reader(topic, RELIABLE, 0);
         assertTrue(waitForMatch(writer, reader));
         long topicData = createData(topic.type);
         long sampleInfo = createSampleInfo();
         try
         {
            ByteBuffer dst = ByteBuffer.allocateDirect(4096);
            int[] sizes = {1, 5, 63, 64, 65, 67, 1000, 4096, 4097, 100_000};
            for (int i = 0; i < sizes.length; i++)
            {
               int size = sizes[i];
               byte[] content = payload(size, i);
               ByteBuffer src = direct(content, size + 32); // only the first size bytes are written
               assertEquals(RETCODE_OK, writeBuffer(writer, src, size), "write " + size);
               // The sample is serialized inside write(): scribbling the source afterwards must not matter.
               for (int j = 0; j < src.capacity(); j++)
                  src.put(j, (byte) 0xEE);

               int[] result = {TAKE_NO_DATA};
               assertTrue(waitFor(() -> (result[0] = dataReaderTakeNextToBuffer(reader, topicData, sampleInfo, dst, dst.capacity())) != TAKE_NO_DATA, 10000));
               int received = result[0];
               assertTrue(isPaddedSize(received, size), "returned payload size " + received + " for " + size);
               assertTrue(sampleInfoValidData(sampleInfo));
               assertTrue(sampleInfoSourceTimestampNanos(sampleInfo) > 0);

               byte[] receivedBytes;
               if (received <= dst.capacity())
               {
                  receivedBytes = bytes(dst, received);
               }
               else
               {
                  // Too large for dst: kept in the topic data for the caller to copy after growing its buffer.
                  assertEquals(received, topicDataSize(topicData));
                  ByteBuffer large = ByteBuffer.allocateDirect(received);
                  topicDataReadBuffer(topicData, large, 0, received);
                  receivedBytes = bytes(large, received);
               }
               assertPayload(content, receivedBytes, "payload of size " + size);
            }
            assertEquals(TAKE_NO_DATA, dataReaderTakeNextToBuffer(reader, topicData, sampleInfo, dst, dst.capacity()));
         }
         finally
         {
            deleteSampleInfo(sampleInfo);
            deleteData(topic.type, topicData);
         }
      }
   }

   @Test
   @Timeout(60)
   public void takeFallsBackToTopicDataWhenDestinationIsUnusable() throws Exception
   {
      assumeAbiV2();
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_take_fallback");
         long writer = fixture.writer(topic, RELIABLE);
         long reader = fixture.reader(topic, RELIABLE, 0);
         assertTrue(waitForMatch(writer, reader));
         long topicData = createData(topic.type);
         try
         {
            ByteBuffer dst = ByteBuffer.allocateDirect(1024);
            // Too small for the payload, no destination, a heap buffer with capacity 0, a direct buffer with capacity 0.
            ByteBuffer[] destinations = {dst, null, ByteBuffer.allocate(1024), dst};
            int[] capacities = {16, 1024, 0, 0};
            for (int i = 0; i < destinations.length; i++)
            {
               byte[] content = payload(100, 40 + i);
               for (int j = 0; j < dst.capacity(); j++)
                  dst.put(j, (byte) 0x5A);
               assertEquals(RETCODE_OK, writeBuffer(writer, direct(content, content.length), content.length));
               ByteBuffer destination = destinations[i];
               int capacity = capacities[i];
               int[] result = {TAKE_NO_DATA};
               // sample info 0: the caller does not need it
               assertTrue(waitFor(() -> (result[0] = dataReaderTakeNextToBuffer(reader, topicData, 0, destination, capacity)) != TAKE_NO_DATA, 10000));
               assertEquals(100, result[0]);
               assertEquals(100, topicDataSize(topicData), "payload kept in the topic data (case " + i + ")");
               byte[] received = new byte[100];
               topicDataRead(topicData, received, 0, received.length);
               assertArrayEquals(content, received);
               for (int j = 0; j < dst.capacity(); j++)
                  assertEquals((byte) 0x5A, dst.get(j), "dst must not be written when the payload does not fit (case " + i + ")");
            }
         }
         finally
         {
            deleteData(topic.type, topicData);
         }
      }
   }

   @Test
   @Timeout(60)
   public void zeroCopyWriteRejectsInvalidArguments() throws Exception
   {
      assumeAbiV2();
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_write_args");
         long writer = fixture.writer(topic, RELIABLE);
         ByteBuffer direct = direct(payload(32, 1), 32);

         assertEquals(RETCODE_BAD_PARAMETER, dataWriterWriteBuffer(0, direct, 32), "null writer");
         assertEquals(RETCODE_BAD_PARAMETER, dataWriterWriteBuffer(writer, null, 32), "null buffer");
         assertEquals(RETCODE_BAD_PARAMETER, dataWriterWriteBuffer(writer, ByteBuffer.allocate(32), 32), "heap buffer has no address");
         assertEquals(RETCODE_BAD_PARAMETER, dataWriterWriteBuffer(writer, direct, 0), "empty payload");
         assertEquals(RETCODE_BAD_PARAMETER, dataWriterWriteBuffer(writer, direct, -1), "negative length");
         assertEquals(RETCODE_BAD_PARAMETER, dataWriterWriteBuffer(writer, direct, 33), "length beyond capacity");
         assertEquals(RETCODE_OK, dataWriterWriteBuffer(writer, direct, 32));

         // The legacy path refuses an empty payload instead of sending one.
         long topicData = createData(topic.type);
         topicDataResize(topicData, 0);
         assertEquals(RETCODE_ERROR, dataWriterWrite(writer, topicData));
         deleteData(topic.type, topicData);
      }
   }

   @Test
   @Timeout(60)
   public void takeToBufferReportsNoDataAndErrors() throws Exception
   {
      assumeAbiV2();
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_take_args");
         long reader = fixture.reader(topic, RELIABLE, 0);
         long topicData = createData(topic.type);
         ByteBuffer dst = ByteBuffer.allocateDirect(64);

         assertEquals(TAKE_NO_DATA, dataReaderTakeNextToBuffer(reader, topicData, 0, dst, 64));
         assertEquals(-(TAKE_ERROR_BASE + RETCODE_BAD_PARAMETER), dataReaderTakeNextToBuffer(0, topicData, 0, dst, 64));
         assertEquals(-(TAKE_ERROR_BASE + RETCODE_BAD_PARAMETER), dataReaderTakeNextToBuffer(reader, 0, 0, dst, 64));
         deleteData(topic.type, topicData);
      }
   }

   @Test
   @Timeout(60)
   public void takeToBufferRejectsDestinationsThatCannotHoldDstCapacity() throws Exception
   {
      // The caller learns from the result alone whether the payload is in dst (size <= dstCapacity), so a destination
      // that cannot hold dstCapacity bytes is refused instead of silently using the fallback, and nothing is taken.
      assumeAbiV2();
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_take_bad_dst");
         long writer = fixture.writer(topic, RELIABLE);
         long reader = fixture.reader(topic, RELIABLE, 0);
         assertTrue(waitForMatch(writer, reader));
         long topicData = createData(topic.type);
         try
         {
            byte[] content = payload(48, 5);
            assertEquals(RETCODE_OK, writeBuffer(writer, direct(content, content.length), content.length));
            assertTrue(waitFor(() -> dataReaderGetUnreadCount(reader) > 0, 10000));

            int badParameter = -(TAKE_ERROR_BASE + RETCODE_BAD_PARAMETER);
            assertEquals(badParameter, dataReaderTakeNextToBuffer(reader, topicData, 0, ByteBuffer.allocate(64), 64), "heap buffer");
            ByteBuffer small = ByteBuffer.allocateDirect(32);
            assertEquals(badParameter, dataReaderTakeNextToBuffer(reader, topicData, 0, small, 33), "dstCapacity beyond the buffer");
            assertEquals(badParameter, dataReaderTakeNextToBuffer(reader, topicData, 0, small, Integer.MAX_VALUE), "huge dstCapacity");

            // The sample is still there.
            ByteBuffer dst = ByteBuffer.allocateDirect(64);
            assertEquals(48, dataReaderTakeNextToBuffer(reader, topicData, 0, dst, dst.capacity()));
            assertArrayEquals(content, bytes(dst, 48));
            // A negative capacity means "no destination": the next sample goes to the topic data.
            assertEquals(RETCODE_OK, writeBuffer(writer, direct(content, content.length), content.length));
            int[] result = {TAKE_NO_DATA};
            assertTrue(waitFor(() -> (result[0] = dataReaderTakeNextToBuffer(reader, topicData, 0, dst, -5)) != TAKE_NO_DATA, 10000));
            assertEquals(48, result[0]);
            assertEquals(48, topicDataSize(topicData));
         }
         finally
         {
            deleteData(topic.type, topicData);
         }
      }
   }

   @Test
   @Timeout(120)
   public void concurrentZeroCopyWritesWithDistinctBuffers() throws Exception
   {
      assumeAbiV2();
      final int threads = 4;
      final int samplesPerThread = 500;
      final int total = threads * samplesPerThread;

      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_concurrent_write");
         long writer = fixture.writer(topic, RELIABLE);
         long reader = fixture.reader(topic, RELIABLE, 0);
         assertTrue(waitForMatch(writer, reader));

         Set<Long> received = ConcurrentHashMap.newKeySet();
         List<String> errors = Collections.synchronizedList(new ArrayList<>());
         long topicData = createData(topic.type);

         Thread consumer = new Thread(() ->
         {
            ByteBuffer dst = ByteBuffer.allocateDirect(4096).order(ByteOrder.LITTLE_ENDIAN);
            long deadline = System.currentTimeMillis() + 60000;
            while (received.size() < total && System.currentTimeMillis() < deadline)
            {
               int size = dataReaderTakeNextToBuffer(reader, topicData, 0, dst, dst.capacity());
               if (size == TAKE_NO_DATA)
               {
                  Thread.yield();
                  continue;
               }
               if (size < 0 || size > dst.capacity())
               {
                  errors.add("unexpected take result " + size);
                  continue;
               }
               int thread = dst.getInt(4);
               int sequence = dst.getInt(8);
               byte[] expected = concurrentPayload(thread, sequence);
               if (!isPaddedSize(size, expected.length) || !Arrays.equals(expected, Arrays.copyOf(bytes(dst, size), expected.length)))
                  errors.add("corrupt sample thread=" + thread + " seq=" + sequence + " size=" + size);
               else if (!received.add(((long) thread << 32) | sequence))
                  errors.add("duplicate sample thread=" + thread + " seq=" + sequence);
            }
         }, "n1-consumer");
         consumer.start();

         CountDownLatch start = new CountDownLatch(1);
         List<Thread> producers = new ArrayList<>();
         for (int t = 0; t < threads; t++)
         {
            final int thread = t;
            Thread producer = new Thread(() ->
            {
               // One direct buffer per thread, reused for every sample of that thread.
               ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
               try
               {
                  start.await();
               }
               catch (InterruptedException e)
               {
                  return;
               }
               for (int sequence = 0; sequence < samplesPerThread; sequence++)
               {
                  byte[] content = concurrentPayload(thread, sequence);
                  buffer.clear();
                  buffer.put(content);
                  int ret = writeBuffer(writer, buffer, content.length);
                  if (ret != RETCODE_OK)
                     errors.add("write failed thread=" + thread + " seq=" + sequence + " ret=" + ret);
               }
            }, "n1-producer-" + t);
            producers.add(producer);
            producer.start();
         }
         start.countDown();
         for (Thread producer : producers)
            producer.join();
         consumer.join(90000);
         deleteData(topic.type, topicData);

         synchronized (errors)
         {
            assertTrue(errors.isEmpty(), "errors: " + errors.subList(0, Math.min(10, errors.size())));
         }
         assertEquals(total, received.size(), "every sample received exactly once and intact");
      }
   }

   private static byte[] concurrentPayload(int thread, int sequence)
   {
      int size = 16 + (sequence * 37 + thread * 101) % 3000;
      byte[] payload = payload(size, thread * 100_000 + sequence);
      ByteBuffer header = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
      header.putInt(4, thread);
      header.putInt(8, sequence);
      return payload;
   }

   @Test
   @Timeout(60)
   public void legacyWriteAndTakeStillWork() throws Exception
   {
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_legacy");
         long writer = fixture.writer(topic, RELIABLE);
         long reader = fixture.reader(topic, RELIABLE, 0);
         assertTrue(waitForMatch(writer, reader));
         long publishData = createData(topic.type);
         long receiveData = createData(topic.type);
         long sampleInfo = createSampleInfo();
         try
         {
            assertEquals(1, topicDataSize(publishData), "new topic data holds one byte");
            for (int size : new int[] {1, 100, 5000, 70_000, 64})
            {
               byte[] content = payload(size, size);
               topicDataResize(publishData, size);
               topicDataWrite(publishData, content, 0, size);
               assertEquals(RETCODE_OK, dataWriterWrite(writer, publishData));
               assertTrue(waitFor(() -> dataReaderTakeNextCustom(reader, receiveData, sampleInfo) == RETCODE_OK, 10000));
               assertTrue(sampleInfoValidData(sampleInfo));
               byte[] received = new byte[topicDataSize(receiveData)];
               topicDataRead(receiveData, received, 0, received.length);
               assertPayload(content, received, "legacy payload of size " + size);
            }
            assertEquals(RETCODE_NO_DATA, dataReaderTakeNextCustom(reader, receiveData, sampleInfo));
         }
         finally
         {
            deleteSampleInfo(sampleInfo);
            deleteData(topic.type, receiveData);
            deleteData(topic.type, publishData);
         }
      }
   }

   @Test
   @Timeout(60)
   public void samplesAlreadyReadAreStillTaken() throws Exception
   {
      // jros2's callback mode reads a sample (marking it READ) and takes it later: the take must not filter on NOT_READ.
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_read_then_take");
         long writer = fixture.writer(topic, RELIABLE);
         long reader = fixture.reader(topic, RELIABLE, 0);
         assertTrue(waitForMatch(writer, reader));
         long readData = createData(topic.type);
         long takeData = createData(topic.type);
         long writerData = createData(topic.type);
         long sampleInfo = createSampleInfo();
         try
         {
            byte[] content = payload(40, 11);
            assertEquals(RETCODE_OK, writeOne(writer, writerData, content));
            assertTrue(waitFor(() -> dataReaderReadNextSample(reader, readData, sampleInfo) == RETCODE_OK, 10000));
            byte[] read = new byte[topicDataSize(readData)];
            topicDataRead(readData, read, 0, read.length);
            assertPayload(content, read, "read sample");
            assertEquals(0, dataReaderGetUnreadCount(reader), "the sample is now READ");

            assertPayload(content, takeOne(reader, takeData, sampleInfo, 10000), "the READ sample is taken");
            assertNull(takeOne(reader, takeData, sampleInfo, 200), "and only once");
         }
         finally
         {
            deleteSampleInfo(sampleInfo);
            deleteData(topic.type, writerData);
            deleteData(topic.type, takeData);
            deleteData(topic.type, readData);
         }
      }
   }

   @Test
   @Timeout(60)
   public void payloadLargerThanPreallocatedPoolFailsCleanly() throws Exception
   {
      // ABI v1 serialize() overflows the fixed-size payload here (heap corruption), so only v2 is exercised.
      assumeAbiV2();
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_preallocated");
         // Writer first: the PREALLOCATED payload pool of the topic gets the type's small initial payload size.
         long writer = fixture.writer(topic, PREALLOCATED);
         long reader = fixture.reader(topic, PREALLOCATED, 0);
         assertTrue(waitForMatch(writer, reader));
         long topicData = createData(topic.type);
         long sampleInfo = createSampleInfo();
         try
         {
            byte[] small = payload(40, 1);
            assertEquals(RETCODE_OK, writeBuffer(writer, direct(small, small.length), small.length));
            assertPayload(small, takeOne(reader, topicData, sampleInfo, 10000), "taken sample");

            byte[] large = payload(1000, 2);
            assertEquals(RETCODE_ERROR, dataWriterWriteBuffer(writer, direct(large, large.length), large.length), "zero-copy write of an oversized payload");
            topicDataResize(topicData, large.length);
            topicDataWrite(topicData, large, 0, large.length);
            assertEquals(RETCODE_ERROR, dataWriterWrite(writer, topicData), "legacy write of an oversized payload");

            // The writer is still healthy and nothing oversized was delivered.
            byte[] after = payload(48, 3);
            assertEquals(RETCODE_OK, writeBuffer(writer, direct(after, after.length), after.length));
            assertPayload(after, takeOne(reader, topicData, sampleInfo, 10000), "taken sample");
            assertNull(takeOne(reader, topicData, sampleInfo, 200));
         }
         finally
         {
            deleteSampleInfo(sampleInfo);
            deleteData(topic.type, topicData);
         }
      }
   }

   @Test
   @Timeout(60)
   public void sampleWithoutDataAfterWriterLossIsReportedOnce() throws Exception
   {
      String topicName = uniqueName("n1_invalid_sample");
      String typeName = uniqueName("n1_invalid_sample_type");
      try (NativeDdsFixture readerSide = new NativeDdsFixture(); NativeDdsFixture writerSide = new NativeDdsFixture())
      {
         Topic readerTopic = readerSide.topic(topicName, typeName);
         Topic writerTopic = writerSide.topic(topicName, typeName);
         long reader = readerSide.reader(readerTopic, RELIABLE, 0);
         long writer = writerSide.writer(writerTopic, RELIABLE);
         assertTrue(waitForMatch(writer, reader));

         long topicData = createData(readerTopic.type);
         long sampleInfo = createSampleInfo();
         long writerData = createData(writerTopic.type);
         try
         {
            byte[] content = payload(24, 7);
            assertEquals(RETCODE_OK, writeOne(writer, writerData, content));
            assertPayload(content, takeOne(reader, topicData, sampleInfo, 10000), "taken sample");

            // The last writer that delivered a sample goes away: the reader gets one sample without data.
            assertEquals(RETCODE_OK, writerSide.deleteWriter(writer));

            if (abiVersion() >= ABI_V2)
            {
               ByteBuffer dst = ByteBuffer.allocateDirect(64);
               for (int j = 0; j < dst.capacity(); j++)
                  dst.put(j, (byte) 0x33);
               int[] result = {TAKE_NO_DATA};
               assertTrue(waitFor(() -> (result[0] = dataReaderTakeNextToBuffer(reader, topicData, sampleInfo, dst, dst.capacity())) != TAKE_NO_DATA, 10000),
                          "the no-writers notification must be taken");
               assertEquals(TAKE_INVALID_SAMPLE, result[0]);
               for (int j = 0; j < dst.capacity(); j++)
                  assertEquals((byte) 0x33, dst.get(j), "an invalid sample writes no payload");
               assertFalse(sampleInfoValidData(sampleInfo));
               assertEquals(NOT_ALIVE_NO_WRITERS_INSTANCE_STATE, sampleInfoInstanceState(sampleInfo));
               assertEquals(TAKE_NO_DATA, dataReaderTakeNextToBuffer(reader, topicData, sampleInfo, dst, dst.capacity()), "taken only once");
            }
            else
            {
               // ABI v1: RETCODE_OK with valid_data == false; the topic data keeps the previous payload.
               assertTrue(waitFor(() -> dataReaderTakeNextCustom(reader, topicData, sampleInfo) == RETCODE_OK, 10000));
               assertFalse(sampleInfoValidData(sampleInfo));
               assertEquals(NOT_ALIVE_NO_WRITERS_INSTANCE_STATE, sampleInfoInstanceState(sampleInfo));
               assertEquals(content.length, topicDataSize(topicData));
               assertEquals(RETCODE_NO_DATA, dataReaderTakeNextCustom(reader, topicData, sampleInfo));
            }
         }
         finally
         {
            deleteData(writerTopic.type, writerData);
            deleteSampleInfo(sampleInfo);
            deleteData(readerTopic.type, topicData);
         }
      }
   }

   @Test
   @Timeout(60)
   public void profileQosStillAppliesToEndpoints() throws Exception
   {
      // ABI v2 builds the endpoint QoS from the profile and then sets the XCDR1 representation: durability and history
      // from the XML profile must still be in effect (writer TRANSIENT_LOCAL, KEEP_LAST 2).
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_profile_qos");
         long writer = fixture.writer(topic, TRANSIENT_DEPTH_2);
         long writerData = createData(topic.type);
         long topicData = createData(topic.type);
         long sampleInfo = createSampleInfo();
         try
         {
            for (int i = 0; i < 5; i++)
               assertEquals(RETCODE_OK, writeOne(writer, writerData, payload(32, i)));

            // Late joiner: only the last two samples are kept for it.
            long reader = fixture.reader(topic, TRANSIENT_READER, 0);
            assertTrue(waitForMatch(writer, reader));
            assertPayload(payload(32, 3), takeOne(reader, topicData, sampleInfo, 10000), "taken sample");
            assertPayload(payload(32, 4), takeOne(reader, topicData, sampleInfo, 10000), "taken sample");
            assertNull(takeOne(reader, topicData, sampleInfo, 500), "history depth 2 from the writer profile");
         }
         finally
         {
            deleteSampleInfo(sampleInfo);
            deleteData(topic.type, topicData);
            deleteData(topic.type, writerData);
         }
      }
   }

   @Test
   @Timeout(60)
   public void unknownProfilesYieldNullEntities() throws Exception
   {
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_unknown_profile");
         long publisher = fixture.publisher(RELIABLE);
         long subscriber = fixture.subscriber(RELIABLE);
         assertEquals(0L, createDataWriter(publisher, topic.topic, "n1_no_such_profile"));
         assertEquals(0L, createDataReader(subscriber, topic.topic, 0, "n1_no_such_profile"));
      }
   }

   @Test
   @Timeout(60)
   public void nullAndMismatchedArgumentsAreRejected() throws Exception
   {
      // ABI v1 dereferences null pointers (SIGSEGV), so this only runs against v2.
      assumeAbiV2();
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_null_args");
         long publisher = fixture.publisher(RELIABLE);
         long subscriber = fixture.subscriber(RELIABLE);

         // Reusing a topic name with another type: Fast-DDS refuses the topic, and the null topic is refused downstream.
         String otherTypeName = uniqueName("n1_other_type");
         long otherType = createTopicDataWrapperType(otherTypeName, CDR_LE);
         long otherTypeSupport = createTypesupport(otherType);
         assertEquals(RETCODE_OK, registerType(fixture.participant, otherTypeSupport));
         long duplicateTopic = createTopic(fixture.participant, otherType, topic.name, TOPIC_PROFILE);
         assertEquals(0L, duplicateTopic, "a topic name can only be created once per participant");
         assertEquals(0L, createDataWriter(publisher, duplicateTopic, RELIABLE));
         assertEquals(0L, createDataReader(subscriber, duplicateTopic, 0, RELIABLE));
         assertEquals(RETCODE_OK, unregisterType(fixture.participant, otherTypeName));
         deleteTypesupport(otherTypeSupport);

         assertEquals(0L, createTopic(fixture.participant, 0, uniqueName("n1_null_type"), TOPIC_PROFILE));
         assertEquals(0L, createTopic(0, topic.type, uniqueName("n1_null_participant"), TOPIC_PROFILE));
         assertEquals(0L, createDataWriter(0, topic.topic, RELIABLE));
         assertEquals(0L, createDataReader(0, topic.topic, 0, RELIABLE));
         assertEquals(0L, createPublisher(0, RELIABLE));
         assertEquals(0L, createSubscriber(0, RELIABLE));
         assertEquals(0L, createTypesupport(0));
         assertEquals(RETCODE_BAD_PARAMETER, registerType(0, topic.typeSupport));
         assertEquals(RETCODE_BAD_PARAMETER, registerType(fixture.participant, 0));
         assertEquals(RETCODE_BAD_PARAMETER, deleteParticipant(0));
         assertEquals(RETCODE_BAD_PARAMETER, deletePublisher(fixture.participant, 0));
         assertEquals(RETCODE_BAD_PARAMETER, deleteSubscriber(0, subscriber));
         assertEquals(RETCODE_BAD_PARAMETER, deleteTopic(fixture.participant, 0));
         assertEquals(RETCODE_BAD_PARAMETER, deleteDataWriter(publisher, 0));
         assertEquals(RETCODE_BAD_PARAMETER, deleteDataReader(0, 0));
         assertEquals(RETCODE_BAD_PARAMETER, dataWriterWrite(0, 0));
         assertEquals(RETCODE_BAD_PARAMETER, dataWriterSetListener(0, 0));
         assertEquals(RETCODE_BAD_PARAMETER, dataReaderSetListener(0, 0));
         assertEquals(RETCODE_BAD_PARAMETER, dataReaderTakeNextCustom(0, 0, 0));
         assertEquals(RETCODE_BAD_PARAMETER, dataReaderReadNextSample(0, 0, 0));
         assertEquals(0, dataReaderGetUnreadCount(0));
         assertEquals(0, dataWriterGetPublicationMatchedCount(0));
         assertEquals(0, dataReaderGetSubscriptionMatchedCount(0));
         assertEquals("", topicDataWrapperTypeGetName(0));

         byte[] guid = new byte[16];
         Arrays.fill(guid, (byte) 1);
         getWriterGuid(0, guid);
         assertArrayEquals(new byte[16], guid);

         assertFalse(sampleInfoValidData(0));
         assertEquals(0L, sampleInfoSourceTimestampNanos(0));
         assertEquals(0, topicDataSize(0));
         deleteData(topic.type, 0);
         deleteSampleInfo(0);
         deleteTypesupport(0);
         deleteDataReaderListener(0);
         deleteDataWriterListener(0);
      }
   }

   @Test
   public void topicDataAccessIsBoundsChecked()
   {
      // No DDS entities needed. Out-of-range accesses are ignored instead of reading or writing out of bounds
      // (ABI v1 copies out of bounds of the Java array, so only v2 is exercised).
      assumeAbiV2();
      long type = createTopicDataWrapperType(uniqueName("n1_bounds_type"), CDR_LE);
      long typeSupport = createTypesupport(type);
      long topicData = createData(type);
      try
      {
         byte[] content = payload(16, 9);
         topicDataResize(topicData, 16);
         topicDataWrite(topicData, content, 0, 16);

         topicDataWrite(topicData, new byte[4], 2, 4);      // offset + length beyond the array
         topicDataWrite(topicData, new byte[4], -1, 2);     // negative offset
         byte[] small = new byte[8];
         topicDataRead(topicData, small, 4, 8);             // beyond the destination array
         assertArrayEquals(new byte[8], small);
         ByteBuffer directSmall = ByteBuffer.allocateDirect(8);
         topicDataReadBuffer(topicData, directSmall, 4, 8); // beyond the destination buffer
         topicDataWriteBuffer(topicData, directSmall, 1, 8); // beyond the source buffer

         byte[] received = new byte[16];
         topicDataRead(topicData, received, 0, 16);
         assertArrayEquals(content, received, "rejected accesses leave the topic data untouched");

         // A read longer than the payload copies only the payload.
         byte[] longer = new byte[32];
         topicDataRead(topicData, longer, 0, 32);
         assertArrayEquals(content, Arrays.copyOf(longer, 16));
         assertArrayEquals(new byte[16], Arrays.copyOfRange(longer, 16, 32));
      }
      finally
      {
         deleteData(type, topicData);
         deleteTypesupport(typeSupport);
      }
   }

   @Test
   public void topicDataOutlivesItsType()
   {
      assumeAbiV2();
      long type = createTopicDataWrapperType(uniqueName("n1_lifetime_type"), CDR_LE);
      long typeSupport = createTypesupport(type);
      long topicData = createData(type);
      deleteTypesupport(typeSupport); // the type support owned (and now freed) the type
      topicDataResize(topicData, 8);
      assertEquals(8, topicDataSize(topicData));
      deleteData(type, topicData);    // must not touch the freed type
   }

   @Test
   @Timeout(120)
   public void legacyWritesOfVaryingPayloadSizesFromSeveralThreads() throws Exception
   {
      // ABI v1 contract: callers serialize fill + write of a shared topic data (the dataWriterWriteBuffer path has no
      // such requirement). Payloads grow beyond and shrink below the initial pool size between writes.
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_shared_legacy");
         long writer = fixture.writer(topic, RELIABLE);
         long topicData = createData(topic.type);
         Object lock = new Object();
         AtomicInteger failures = new AtomicInteger();
         List<Thread> threads = new ArrayList<>();
         for (int t = 0; t < 4; t++)
         {
            final int thread = t;
            Thread worker = new Thread(() ->
            {
               for (int i = 0; i < 200; i++)
               {
                  byte[] content = payload(8 + ((i * 131 + thread * 977) % 20000), i);
                  synchronized (lock)
                  {
                     topicDataResize(topicData, content.length);
                     topicDataWrite(topicData, content, 0, content.length);
                     int ret = dataWriterWrite(writer, topicData);
                     if (ret != RETCODE_OK && ret != RETCODE_TIMEOUT)
                        failures.incrementAndGet();
                  }
               }
            });
            threads.add(worker);
            worker.start();
         }
         for (Thread worker : threads)
            worker.join();
         deleteData(topic.type, topicData);
         assertEquals(0, failures.get());
      }
   }
}
