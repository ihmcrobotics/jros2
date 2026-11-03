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
package us.ihmc.jros2.allocation.messages;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.jros2.ROS2Message;
import us.ihmc.jros2.allocation.ClassFileStrings;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Steady-state allocation gate for one generated message type: the exact per-message calls of the jros2 runtime
 * (ROS2Publisher.writeAndPublish, ROS2Subscription.deserialize, AsyncROS2Publisher.publish), on a set of instances with
 * different contents and sizes, all at or below a high-water-mark instance.
 * <p>
 * Measured, after one warm-up round over the same instances (so every pooled element, StringBuilder and buffer has
 * reached its working size):
 * <ul>
 *    <li>calculateSizeBytes(0) and serialization into a reused CDRBuffer exactly as ROS2Publisher does it (rewind,
 *    estimate, grow only if needed, payload header, serialize), plus serialization into a big-endian stream;</li>
 *    <li>deserialization of little- and big-endian payloads into ONE reused instance, cycling through instances whose
 *    sequence lengths and string lengths are equal to or smaller than the high-water mark, alternating up and down,
 *    exactly as ROS2Subscription does it (beginRead, readPayloadHeader, deserialize, rewind), also from ONE shared read
 *    buffer into which payloads of alternating byte orders are copied, like the subscription's read buffer;</li>
 *    <li>set(other) into one reused instance from every instance (AsyncROS2Publisher's snapshot), set(this), and
 *    calculateSizeBytes/serialize of the copy (the async publish thread).</li>
 * </ul>
 * Correctness is checked outside the measured region: the reused instances must re-serialize to exactly the bytes of the
 * instance they were last deserialized/set from (stale elements or lengths would show), both byte orders decode to the
 * same message, and calculateSizeBytes is exact.
 */
public final class MessageAllocationHarness
{
   private static final int PAYLOAD_HEADER_SIZE = CDRBuffer.PAYLOAD_HEADER.length;
   /** Order in which the instances are cycled: 0 is the high-water mark; sizes go up and down below it. */
   private static final int[] ORDER = {0, 1, 2, 0, 3, 4, 2, 1, 4, 3, 0, 4};
   private static final int INSTANCES = 5;

   private static volatile long sink;

   private MessageAllocationHarness()
   {
   }

   @SuppressWarnings({"rawtypes", "unchecked"})
   public static void assertSteadyStateAllocationFree(CallingThreadAllocationGate gate, Class<? extends ROS2Message> type, Consumer<ROS2Message> highWaterMarkFiller,
                                                      Consumer<ROS2Message> randomFiller)
   {
      String name = type.getName();
      Round round = prepare(type, highWaterMarkFiller, randomFiller);

      // Warm-up round: same instances, same sizes
      round.run();

      gate.assertNoAllocation(name + " serialize/deserialize(LE+BE)/set/calculateSizeBytes", round::runTwice);

      assertReusedInstancesCorrect(name, round);
   }

   /**
    * Like {@link #assertSteadyStateAllocationFree}, but the measured window is {@code rounds} rounds long, so that the
    * methods of the type cross the JIT compilation thresholds (C1, C2, on-stack replacement) inside the window: whatever
    * the JVM does on the calling thread when it requests these compilations, and any allocation that happens only every
    * so many calls, is measured too.
    */
   @SuppressWarnings({"rawtypes", "unchecked"})
   public static void assertLongSteadyStateAllocationFree(CallingThreadAllocationGate gate, Class<? extends ROS2Message> type, Consumer<ROS2Message> highWaterMarkFiller,
                                                          Consumer<ROS2Message> randomFiller, int rounds)
   {
      String name = type.getName();
      Round round = prepare(type, highWaterMarkFiller, randomFiller);
      round.run();
      round.rounds = rounds;
      gate.assertNoAllocation(name + " long steady state", round::runRounds);
      assertReusedInstancesCorrect(name, round);
   }

   /**
    * Creates the instances (instance 0 filled by {@code highWaterMarkFiller}, the others by {@code randomFiller}), their
    * little- and big-endian payloads, and the reused objects of the runtime, and checks the payload sizes.
    */
   @SuppressWarnings({"rawtypes", "unchecked"})
   static Round prepare(Class<? extends ROS2Message> type, Consumer<ROS2Message> highWaterMarkFiller, Consumer<ROS2Message> randomFiller)
   {
      String name = type.getName();
      ROS2Message[] instances = new ROS2Message[INSTANCES];
      for (int i = 0; i < INSTANCES; i++)
      {
         instances[i] = newInstance(type);
         if (i == 0)
            highWaterMarkFiller.accept(instances[i]);
         else
            randomFiller.accept(instances[i]);
      }

      byte[][] expected = new byte[INSTANCES][];
      CDRBuffer[] littleEndian = new CDRBuffer[INSTANCES];
      CDRBuffer[] bigEndian = new CDRBuffer[INSTANCES];
      int[] littleEndianSize = new int[INSTANCES];
      int[] bigEndianSize = new int[INSTANCES];
      int maxSize = 0;
      for (int i = 0; i < INSTANCES; i++)
      {
         littleEndian[i] = new CDRBuffer();
         littleEndianSize[i] = serialize(instances[i], littleEndian[i], ByteOrder.LITTLE_ENDIAN);
         expected[i] = bytes(littleEndian[i], littleEndianSize[i]);
         assertEquals(littleEndianSize[i] - PAYLOAD_HEADER_SIZE, instances[i].calculateSizeBytes(0), name + ": calculateSizeBytes must be exact");
         bigEndian[i] = new CDRBuffer();
         bigEndianSize[i] = serialize(instances[i], bigEndian[i], ByteOrder.BIG_ENDIAN);
         assertEquals(littleEndianSize[i], bigEndianSize[i], name + ": big-endian payload size");
         maxSize = Math.max(maxSize, littleEndianSize[i]);
      }

      // The subscription's read buffer: one direct buffer, sized once, that receives every payload of either byte order
      CDRBuffer receiveBuffer = new CDRBuffer();
      receiveBuffer.ensureRemainingCapacity(maxSize);

      ROS2Message received = newInstance(type);
      ROS2Message copy = newInstance(type);
      Round round = new Round(instances, littleEndian, littleEndianSize, bigEndian, bigEndianSize, received, copy, receiveBuffer);
      round.expected = expected;

      // The instances differ (a filler that produced identical instances would not test anything)
      if (expected[0].length > PAYLOAD_HEADER_SIZE + 1)
      {
         assertNotEquals(Arrays.toString(expected[0]), Arrays.toString(expected[1]), name + ": instances should differ");
      }
      return round;
   }

   /** Correctness of the reused instances, cycling in the same order (stale lengths or elements would show here). */
   @SuppressWarnings({"rawtypes", "unchecked"})
   static void assertReusedInstancesCorrect(String name, Round round)
   {
      ROS2Message received = round.received;
      ROS2Message copy = round.copy;
      for (int k = 0; k < ORDER.length; k++)
      {
         int i = ORDER[k];
         Round.deserialize(received, round.littleEndian[i], round.littleEndianSize[i]);
         assertArrayEquals(round.expected[i], serializeToBytes(received), name + ": reused instance after little-endian deserialize of instance " + i);

         Round.deserialize(received, round.bigEndian[i], round.bigEndianSize[i]);
         assertArrayEquals(round.expected[i], serializeToBytes(received), name + ": reused instance after big-endian deserialize of instance " + i);

         round.receive(round.bigEndian[i], round.bigEndianSize[i]);
         assertArrayEquals(round.expected[i], serializeToBytes(received), name + ": reused instance after receiving the big-endian payload of instance " + i);

         copy.set(round.instances[i]);
         assertArrayEquals(round.expected[i], serializeToBytes(copy), name + ": reused instance after set from instance " + i);
      }
   }

   /**
    * A subscription that received ASCII-only strings for a long time, then strings with non-Latin-1 characters (no longer
    * than before): deserializing them (both byte orders) into the reused instance and copying them with set() allocates
    * nothing. A JDK StringBuilder that only ever held Latin-1 characters reallocates its storage for the first character
    * above U+00FF; the builders of generated messages and of IDL string sequences are created so that this never happens
    * (see {@link CDRBuffer#newStringBuilder(int)}).
    *
    * @param asciiFiller   fills an instance with ASCII-only strings
    * @param unicodeFiller fills an instance with the same structure and string lengths, with non-Latin-1 characters
    */
   @SuppressWarnings({"rawtypes", "unchecked"})
   public static void assertNonLatin1AfterAsciiAllocationFree(CallingThreadAllocationGate gate, Class<? extends ROS2Message> type, Consumer<ROS2Message> asciiFiller,
                                                              Consumer<ROS2Message> unicodeFiller)
   {
      String name = type.getName();
      ROS2Message ascii = newInstance(type);
      asciiFiller.accept(ascii);
      ROS2Message unicode = newInstance(type);
      unicodeFiller.accept(unicode);

      CDRBuffer asciiPayload = new CDRBuffer();
      int asciiSize = serialize(ascii, asciiPayload, ByteOrder.LITTLE_ENDIAN);
      CDRBuffer unicodeLittleEndian = new CDRBuffer();
      int unicodeLittleEndianSize = serialize(unicode, unicodeLittleEndian, ByteOrder.LITTLE_ENDIAN);
      byte[] expected = bytes(unicodeLittleEndian, unicodeLittleEndianSize);
      CDRBuffer unicodeBigEndian = new CDRBuffer();
      int unicodeBigEndianSize = serialize(unicode, unicodeBigEndian, ByteOrder.BIG_ENDIAN);

      // Warm-up with ASCII content only, twice: every sequence, pooled element and builder reaches its working size
      ROS2Message received = newInstance(type);
      ROS2Message copy = newInstance(type);
      for (int i = 0; i < 2; i++)
      {
         Round.deserialize(received, asciiPayload, asciiSize);
         Round.set(copy, ascii);
      }

      gate.assertNoAllocation(name + ": non-Latin-1 strings after an ASCII-only warm-up", () ->
      {
         Round.deserialize(received, unicodeLittleEndian, unicodeLittleEndianSize);
         Round.set(copy, unicode);
         Round.deserialize(received, asciiPayload, asciiSize);
         Round.deserialize(received, unicodeBigEndian, unicodeBigEndianSize);
      });

      assertArrayEquals(expected, serializeToBytes(received), name + ": reused instance after deserializing non-Latin-1 strings");
      assertArrayEquals(expected, serializeToBytes(copy), name + ": reused instance after set() with non-Latin-1 strings");
   }

   /**
    * The runtime's per-message calls, on pre-built objects only: the code that runs in the measured regions. It has no
    * string literal (see {@link ClassFileStrings}).
    */
   @SuppressWarnings({"rawtypes", "unchecked"})
   static final class Round
   {
      private final ROS2Message[] instances;
      private final CDRBuffer[] littleEndian;
      private final int[] littleEndianSize;
      private final CDRBuffer[] bigEndian;
      private final int[] bigEndianSize;
      private final ROS2Message received;
      private final ROS2Message copy;
      private final CDRBuffer writeBuffer = new CDRBuffer();
      private final CDRBuffer writeBufferBigEndian = new CDRBuffer();
      private final CDRBuffer receiveBuffer;
      private byte[][] expected;
      private int rounds;

      Round(ROS2Message[] instances, CDRBuffer[] littleEndian, int[] littleEndianSize, CDRBuffer[] bigEndian, int[] bigEndianSize, ROS2Message received,
            ROS2Message copy, CDRBuffer receiveBuffer)
      {
         this.receiveBuffer = receiveBuffer;
         this.instances = instances;
         this.littleEndian = littleEndian;
         this.littleEndianSize = littleEndianSize;
         this.bigEndian = bigEndian;
         this.bigEndianSize = bigEndianSize;
         this.received = received;
         this.copy = copy;
      }

      void runTwice()
      {
         run();
         run();
      }

      void runRounds()
      {
         for (int r = 0; r < rounds; r++)
            run();
      }

      void run()
      {
         long checksum = 0;
         for (int k = 0; k < ORDER.length; k++)
         {
            int i = ORDER[k];
            ROS2Message message = instances[i];

            // ROS2Publisher.writeAndPublish
            writeBuffer.rewind();
            int estimatedSizeBytes = PAYLOAD_HEADER_SIZE + message.calculateSizeBytes(0);
            if (estimatedSizeBytes > writeBuffer.getBufferUnsafe().capacity())
               writeBuffer.ensureRemainingCapacity(estimatedSizeBytes);
            writeBuffer.writePayloadHeader();
            message.serialize(writeBuffer);
            checksum += writeBuffer.getBufferUnsafe().position();

            // Big-endian stream (exercises the big-endian bulk views)
            writeBufferBigEndian.rewind();
            writeBufferBigEndian.ensureRemainingCapacity(estimatedSizeBytes);
            writeBufferBigEndian.writePayloadHeader();
            writeBufferBigEndian.getBufferUnsafe().order(ByteOrder.BIG_ENDIAN);
            message.serialize(writeBufferBigEndian);
            checksum += writeBufferBigEndian.getBufferUnsafe().position();

            // ROS2Subscription.deserialize, both byte orders, into one reused instance
            deserialize(received, littleEndian[i], littleEndianSize[i]);
            int next = ORDER[(k + 1) % ORDER.length];
            deserialize(received, bigEndian[next], bigEndianSize[next]);

            // ROS2Subscription's read buffer: payloads of both byte orders copied into one buffer (as the JNI take does)
            receive(bigEndian[i], bigEndianSize[i]);
            receive(littleEndian[next], littleEndianSize[next]);

            // AsyncROS2Publisher.publish (caller thread) and its publish thread
            copy.set(message);
            copy.set(copy);
            checksum += copy.calculateSizeBytes(0);
            writeBuffer.rewind();
            writeBuffer.writePayloadHeader();
            copy.serialize(writeBuffer);
            checksum += writeBuffer.getBufferUnsafe().position();
         }
         sink += checksum;
      }

      /**
       * Copies a payload into the shared read buffer, as the native take does, and deserializes it into the reused instance
       * (ROS2Subscription.takeNextValidSample + deserialize).
       */
      void receive(CDRBuffer payload, int size)
      {
         receiveBuffer.getBufferUnsafe().put(0, payload.getBufferUnsafe(), 0, size);
         deserialize(received, receiveBuffer, size);
      }

      /** ROS2Subscription.deserialize */
      static void deserialize(ROS2Message message, CDRBuffer payload, int size)
      {
         payload.beginRead(size);
         payload.readPayloadHeader();
         message.deserialize(payload);
         payload.rewind();
      }

      static void set(ROS2Message destination, ROS2Message source)
      {
         destination.set(source);
      }
   }

   @SuppressWarnings("rawtypes")
   public static ROS2Message newInstance(Class<? extends ROS2Message> type)
   {
      try
      {
         return type.getDeclaredConstructor().newInstance();
      }
      catch (ReflectiveOperationException e)
      {
         throw new RuntimeException(e);
      }
   }

   /** Serializes with a payload header for {@code order} (CDR_LE or CDR_BE) and returns the payload size. */
   @SuppressWarnings("rawtypes")
   public static int serialize(ROS2Message message, CDRBuffer buffer, ByteOrder order)
   {
      buffer.rewind();
      buffer.ensureRemainingCapacity(PAYLOAD_HEADER_SIZE + message.calculateSizeBytes(0));
      buffer.writePayloadHeader();
      if (order == ByteOrder.BIG_ENDIAN)
      {
         ByteBuffer byteBuffer = buffer.getBufferUnsafe();
         byteBuffer.put(0, (byte) 0); // CDR_BE = 0x0000
         byteBuffer.put(1, (byte) 0);
         byteBuffer.order(ByteOrder.BIG_ENDIAN);
      }
      message.serialize(buffer);
      return buffer.getBufferUnsafe().position();
   }

   public static byte[] bytes(CDRBuffer buffer, int size)
   {
      byte[] bytes = new byte[size];
      ByteBuffer byteBuffer = buffer.getBufferUnsafe();
      for (int i = 0; i < size; i++)
         bytes[i] = byteBuffer.get(i);
      return bytes;
   }

   @SuppressWarnings("rawtypes")
   public static byte[] serializeToBytes(ROS2Message message)
   {
      CDRBuffer buffer = new CDRBuffer();
      int size = serialize(message, buffer, ByteOrder.LITTLE_ENDIAN);
      return bytes(buffer, size);
   }
}
