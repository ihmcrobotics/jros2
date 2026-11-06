/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.jros2;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.IDLBoolSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLByteSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLCharSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLDoubleSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLFloatSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLIntSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLLongSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLShortSequence;
import us.ihmc.fastddsjava.fastddsjavaTools;
import us.ihmc.fastddsjava.natives.fastddsjava;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Runs, without DDS, the jros2 code paths that used JDK APIs newer than Android API 26 and therefore failed with
 * {@link NoSuchMethodError} on older devices although they compile fine:
 * <ul>
 *    <li>absolute bulk {@code Buffer.put(int, Buffer, int, int)} (API 35) in every primitive IDL sequence: growing
 *    ({@code ensureMinCapacity}), {@code remove(int)} and {@code set(other)},</li>
 *    <li>{@code String.formatted} (API 34) in {@code fastddsjavaTools} and {@code StatisticsCalculator}.</li>
 * </ul>
 * Every failure is collected, so one run lists all affected classes. Android Lint (NewApi, see android/build.gradle.kts)
 * is the static gate for the same problem; this test proves the behavior on a real device.
 */
@RunWith(AndroidJUnit4.class)
public class ApiCompatibilityOnDeviceTest
{
   private static final int COUNT = 1000;

   private interface Check
   {
      void run();
   }

   private static void check(List<String> failures, String what, Check check)
   {
      try
      {
         check.run();
      }
      catch (Throwable t) // NoSuchMethodError is an Error, not an Exception
      {
         failures.add(what + ": " + t);
      }
   }

   private static void assertNoFailures(List<String> failures)
   {
      assertTrue(failures.size() + " failure(s):\n" + String.join("\n", failures), failures.isEmpty());
   }

   @Test
   public void primitiveSequencesGrowRemoveAndCopy()
   {
      List<String> failures = new ArrayList<>();

      check(failures, "IDLByteSequence", () ->
      {
         IDLByteSequence sequence = new IDLByteSequence();
         for (int i = 0; i < COUNT; ++i)
         {
            sequence.add((byte) i); // grows step by step
         }
         byte[] more = new byte[COUNT];
         for (int i = 0; i < COUNT; ++i)
         {
            more[i] = (byte) (COUNT + i);
         }
         sequence.addAll(more);
         assertEquals(2 * COUNT, sequence.size());
         assertEquals((byte) 0, sequence.remove(0));
         assertEquals((byte) 1, sequence.get(0));
         IDLByteSequence copy = new IDLByteSequence();
         copy.set(sequence);
         assertEquals(2 * COUNT - 1, copy.size());
         assertEquals((byte) (2 * COUNT - 1), copy.get(copy.size() - 1));
      });

      check(failures, "IDLBoolSequence", () ->
      {
         IDLBoolSequence sequence = new IDLBoolSequence();
         for (int i = 0; i < COUNT; ++i)
         {
            sequence.add(i % 3 == 0);
         }
         sequence.addAll(new boolean[] {true, false, true});
         assertEquals(COUNT + 3, sequence.size());
         assertTrue(sequence.remove(0));
         assertEquals(false, sequence.get(0));
         IDLBoolSequence copy = new IDLBoolSequence();
         copy.set(sequence);
         assertEquals(COUNT + 2, copy.size());
         assertTrue(copy.get(copy.size() - 1));
      });

      check(failures, "IDLCharSequence", () ->
      {
         IDLCharSequence sequence = new IDLCharSequence();
         for (int i = 0; i < COUNT; ++i)
         {
            sequence.add((char) ('a' + i % 26));
         }
         sequence.addAll(new char[] {'x', 'y', 'z'});
         assertEquals(COUNT + 3, sequence.size());
         assertEquals('a', sequence.remove(0));
         assertEquals('b', sequence.get(0));
         IDLCharSequence copy = new IDLCharSequence();
         copy.set(sequence);
         assertEquals(COUNT + 2, copy.size());
         assertEquals('z', copy.get(copy.size() - 1));
      });

      check(failures, "IDLShortSequence", () ->
      {
         IDLShortSequence sequence = new IDLShortSequence();
         for (int i = 0; i < COUNT; ++i)
         {
            sequence.add((short) i);
         }
         sequence.addAll(new short[] {-1, -2, -3});
         assertEquals(COUNT + 3, sequence.size());
         assertEquals((short) 0, sequence.remove(0));
         assertEquals((short) 1, sequence.get(0));
         IDLShortSequence copy = new IDLShortSequence();
         copy.set(sequence);
         assertEquals(COUNT + 2, copy.size());
         assertEquals((short) -3, copy.get(copy.size() - 1));
      });

      check(failures, "IDLIntSequence", () ->
      {
         IDLIntSequence sequence = new IDLIntSequence();
         for (int i = 0; i < COUNT; ++i)
         {
            sequence.add(i * 1000);
         }
         sequence.addAll(new int[] {-1, -2, Integer.MAX_VALUE});
         assertEquals(COUNT + 3, sequence.size());
         assertEquals(0, sequence.remove(0));
         assertEquals(1000, sequence.get(0));
         IDLIntSequence copy = new IDLIntSequence();
         copy.set(sequence);
         assertEquals(COUNT + 2, copy.size());
         assertEquals(Integer.MAX_VALUE, copy.get(copy.size() - 1));
      });

      check(failures, "IDLLongSequence", () ->
      {
         IDLLongSequence sequence = new IDLLongSequence();
         for (int i = 0; i < COUNT; ++i)
         {
            sequence.add(i * 10_000_000_000L);
         }
         sequence.addAll(new long[] {-1L, Long.MIN_VALUE, Long.MAX_VALUE});
         assertEquals(COUNT + 3, sequence.size());
         assertEquals(0L, sequence.remove(0));
         assertEquals(10_000_000_000L, sequence.get(0));
         IDLLongSequence copy = new IDLLongSequence();
         copy.set(sequence);
         assertEquals(COUNT + 2, copy.size());
         assertEquals(Long.MAX_VALUE, copy.get(copy.size() - 1));
      });

      check(failures, "IDLFloatSequence", () ->
      {
         IDLFloatSequence sequence = new IDLFloatSequence();
         for (int i = 0; i < COUNT; ++i)
         {
            sequence.add(i * 0.5f);
         }
         sequence.addAll(new float[] {-1.5f, Float.MIN_VALUE, Float.MAX_VALUE});
         assertEquals(COUNT + 3, sequence.size());
         assertEquals(0.0f, sequence.remove(0), 0.0f);
         assertEquals(0.5f, sequence.get(0), 0.0f);
         IDLFloatSequence copy = new IDLFloatSequence();
         copy.set(sequence);
         assertEquals(COUNT + 2, copy.size());
         assertEquals(Float.MAX_VALUE, copy.get(copy.size() - 1), 0.0f);
      });

      check(failures, "IDLDoubleSequence", () ->
      {
         IDLDoubleSequence sequence = new IDLDoubleSequence();
         for (int i = 0; i < COUNT; ++i)
         {
            sequence.add(i * 0.25);
         }
         sequence.addAll(new double[] {-1.5, Double.MIN_VALUE, Double.MAX_VALUE});
         assertEquals(COUNT + 3, sequence.size());
         assertEquals(0.0, sequence.remove(0), 0.0);
         assertEquals(0.25, sequence.get(0), 0.0);
         IDLDoubleSequence copy = new IDLDoubleSequence();
         copy.set(sequence);
         assertEquals(COUNT + 2, copy.size());
         assertEquals(Double.MAX_VALUE, copy.get(copy.size() - 1), 0.0);
      });

      assertNoFailures(failures);
   }

   /**
    * sensor_msgs/JointState (a string sequence and three float64 sequences) serialized into a {@link CDRBuffer} and read
    * back into a reused message, which grows the receiving sequences.
    */
   @Test
   public void cdrRoundTripOfSequenceMessage()
   {
      sensor_msgs.JointState message = new sensor_msgs.JointState();
      message.getHeader().getFrameId().append("base_link");
      int joints = 64;
      for (int i = 0; i < joints; ++i)
      {
         message.getName().add("joint_" + i);
         message.getPosition().add(i * 0.1);
         message.getVelocity().add(-i * 0.01);
         message.getEffort().add(i * 2.0);
      }

      CDRBuffer buffer = new CDRBuffer();
      buffer.ensureRemainingCapacity(CDRBuffer.PAYLOAD_HEADER.length + message.calculateSizeBytes(0));
      buffer.writePayloadHeader();
      message.serialize(buffer);
      buffer.rewind();
      buffer.readPayloadHeader();

      sensor_msgs.JointState copy = new sensor_msgs.JointState();
      copy.deserialize(buffer);

      assertEquals("base_link", copy.getHeader().getFrameId().toString());
      assertEquals(joints, copy.getName().size());
      assertEquals(joints, copy.getPosition().size());
      assertEquals(joints, copy.getVelocity().size());
      assertEquals(joints, copy.getEffort().size());
      for (int i = 0; i < joints; ++i)
      {
         assertEquals("joint_" + i, copy.getName().getAsString(i));
         assertEquals(i * 0.1, copy.getPosition().get(i), 0.0);
         assertEquals(-i * 0.01, copy.getVelocity().get(i), 0.0);
         assertEquals(i * 2.0, copy.getEffort().get(i), 0.0);
      }
   }

   @Test
   public void diagnosticMessagesAreFormatted()
   {
      List<String> failures = new ArrayList<>();

      check(failures, "fastddsjavaTools.retcodeMessage", () ->
      {
         String message = fastddsjavaTools.retcodeMessage(fastddsjava.RETCODE_ERROR);
         assertTrue(message, message.contains("(1)"));
      });

      check(failures, "StatisticsCalculator.toString", () ->
      {
         StatisticsCalculator calculator = new StatisticsCalculator();
         calculator.record(1.0);
         calculator.record(3.0);
         String text = calculator.toString();
         assertTrue(text, text.contains("AVERAGE=") && text.contains("SAMPLE_COUNT="));
      });

      assertNoFailures(failures);
   }
}
