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
package us.ihmc.fastddsjava.cdr.allocation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.IDLBoolSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLByteSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLCharSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLDoubleSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLFloatSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLIntSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLLongSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLObjectSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLShortSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLStringSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLWStringSequence;
import us.ihmc.jros2.allocation.messages.CallingThreadAllocationGate;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The IDL sequence API that user code calls every control tick to fill and read messages (clear, add, addAll, putAt,
 * get, remove, set, ...) and the CDR paths of every sequence type, in steady state, allocate nothing; pooled elements
 * (StringBuilders and nested messages) are reused, never reallocated.
 */
@Tag("allocation")
public class IDLSequenceAllocationTest
{
   private static final int N = 50;

   private static CallingThreadAllocationGate gate;
   private static volatile long sink;

   /** The sampler is registered only while a test runs (never between tests). */
   @BeforeEach
   public void installGate()
   {
      gate = CallingThreadAllocationGate.create();
   }

   @AfterEach
   public void removeGate()
   {
      gate.close();
      gate = null;
   }

   /** Runs {@code body} once to warm up, then twice under the gate. */
   private static void assertSteadyStateAllocationFree(String what, Runnable body)
   {
      assertSteadyStateAllocationFree(what, 1, body);
   }

   /** Runs {@code body} {@code warmUpRounds} times to warm up, then twice under the gate. */
   private static void assertSteadyStateAllocationFree(String what, int warmUpRounds, Runnable body)
   {
      for (int i = 0; i < warmUpRounds; i++)
         body.run();
      gate.assertNoAllocation(what, () ->
      {
         body.run();
         body.run();
      });
   }

   /** Serializes {@code sequence} with a header in {@code order} into {@code buffer} and deserializes it into {@code target}. */
   private static void roundTrip(IDLSequence<?> sequence, IDLSequence<?> target, CDRBuffer buffer, boolean bigEndian)
   {
      buffer.rewind();
      buffer.ensureRemainingCapacity(4 + sequence.calculateSizeBytes(0));
      buffer.writePayloadHeader();
      if (bigEndian)
      {
         buffer.getBufferUnsafe().put(1, (byte) 0);
         buffer.getBufferUnsafe().order(ByteOrder.BIG_ENDIAN);
      }
      sequence.serialize(buffer);
      int size = buffer.getBufferUnsafe().position();
      buffer.beginRead(size);
      buffer.readPayloadHeader();
      target.deserialize(buffer);
      buffer.rewind();
      sink += size + target.size();
   }

   @Test
   public void testPrimitiveSequences()
   {
      CDRBuffer buffer = new CDRBuffer();

      IDLBoolSequence bools = new IDLBoolSequence(), boolsCopy = new IDLBoolSequence(0, 2 * N);
      boolean[] boolArray = new boolean[N / 2];
      assertSteadyStateAllocationFree("IDLBoolSequence", () ->
      {
         bools.clear();
         for (int i = 0; i < N; i++)
            bools.add(i % 3 == 0);
         bools.addAll(boolArray);
         sink += bools.get(3) ? 1 : 0;
         sink += bools.remove() ? 1 : 0;
         sink += bools.remove(2) ? 1 : 0;
         bools.ensureMinCapacity(N);
         sink += bools.size() + bools.capacity() + bools.remainingCapacity() + (bools.isEmpty() ? 1 : 0) + bools.getBuffer().position();
         boolsCopy.set(bools);
         bools.clear();
         for (int i = 0; i < N / 3; i++)
            bools.add(true);
         roundTrip(bools, boolsCopy, buffer, false);
         roundTrip(boolsCopy, bools, buffer, true);
      });

      IDLByteSequence bytes = new IDLByteSequence(), bytesCopy = new IDLByteSequence(0, 2 * N);
      byte[] byteArray = new byte[N / 2];
      assertSteadyStateAllocationFree("IDLByteSequence", () ->
      {
         bytes.clear();
         for (int i = 0; i < N; i++)
            bytes.add((byte) i);
         bytes.addAll(byteArray);
         bytes.putAt(N + 3, (byte) 7);
         sink += bytes.get(3) + bytes.remove() + bytes.remove(2);
         sink += bytes.size() + bytes.capacity() + bytes.getBuffer().position();
         bytesCopy.set(bytes);
         bytes.clear();
         bytes.addAll(byteArray);
         roundTrip(bytes, bytesCopy, buffer, false);
         roundTrip(bytesCopy, bytes, buffer, true);
      });

      IDLCharSequence chars = new IDLCharSequence(), charsCopy = new IDLCharSequence(0, 2 * N);
      char[] charArray = new char[N / 2];
      assertSteadyStateAllocationFree("IDLCharSequence", () ->
      {
         chars.clear();
         for (int i = 0; i < N; i++)
            chars.add((char) (200 + i % 50));
         chars.addAll(charArray);
         chars.putAt(N + 3, 'x');
         sink += chars.get(3) + chars.remove() + chars.remove(2);
         sink += chars.size() + chars.getBuffer().position();
         charsCopy.set(chars);
         chars.clear();
         chars.addAll(charArray);
         roundTrip(chars, charsCopy, buffer, false);
         roundTrip(charsCopy, chars, buffer, true);
      });

      IDLShortSequence shorts = new IDLShortSequence(), shortsCopy = new IDLShortSequence(0, 2 * N);
      short[] shortArray = new short[N / 2];
      assertSteadyStateAllocationFree("IDLShortSequence", () ->
      {
         shorts.clear();
         for (int i = 0; i < N; i++)
            shorts.add((short) (i * 1000));
         shorts.addAll(shortArray);
         shorts.putAt(N + 3, (short) -7);
         sink += shorts.get(3) + shorts.remove() + shorts.remove(2);
         sink += shorts.size() + shorts.getBuffer().position();
         shortsCopy.set(shorts);
         shorts.clear();
         shorts.addAll(shortArray);
         roundTrip(shorts, shortsCopy, buffer, false);
         roundTrip(shortsCopy, shorts, buffer, true);
      });

      IDLIntSequence ints = new IDLIntSequence(), intsCopy = new IDLIntSequence(0, 2 * N);
      int[] intArray = new int[N / 2];
      assertSteadyStateAllocationFree("IDLIntSequence", () ->
      {
         ints.clear();
         for (int i = 0; i < N; i++)
            ints.add(i * 100000);
         ints.addAll(intArray);
         ints.putAt(N + 3, -7);
         sink += ints.get(3) + ints.remove() + ints.remove(2);
         sink += ints.size() + ints.getBuffer().position();
         intsCopy.set(ints);
         ints.clear();
         ints.addAll(intArray);
         roundTrip(ints, intsCopy, buffer, false);
         roundTrip(intsCopy, ints, buffer, true);
      });

      IDLLongSequence longs = new IDLLongSequence(), longsCopy = new IDLLongSequence(0, 2 * N);
      long[] longArray = new long[N / 2];
      assertSteadyStateAllocationFree("IDLLongSequence", () ->
      {
         longs.clear();
         for (int i = 0; i < N; i++)
            longs.add(i * 10000000000L);
         longs.addAll(longArray);
         longs.putAt(N + 3, -7L);
         sink += longs.get(3) + longs.remove() + longs.remove(2);
         sink += longs.size() + longs.getBuffer().position();
         longsCopy.set(longs);
         longs.clear();
         longs.addAll(longArray);
         roundTrip(longs, longsCopy, buffer, false);
         roundTrip(longsCopy, longs, buffer, true);
      });

      IDLFloatSequence floats = new IDLFloatSequence(), floatsCopy = new IDLFloatSequence(0, 2 * N);
      float[] floatArray = new float[N / 2];
      assertSteadyStateAllocationFree("IDLFloatSequence", () ->
      {
         floats.clear();
         for (int i = 0; i < N; i++)
            floats.add(i * 0.5f);
         floats.addAll(floatArray);
         floats.putAt(N + 3, -7.5f);
         sink += (long) (floats.get(3) + floats.remove() + floats.remove(2));
         sink += floats.size() + floats.getBuffer().position();
         floatsCopy.set(floats);
         floats.clear();
         floats.addAll(floatArray);
         roundTrip(floats, floatsCopy, buffer, false);
         roundTrip(floatsCopy, floats, buffer, true);
      });

      IDLDoubleSequence doubles = new IDLDoubleSequence(), doublesCopy = new IDLDoubleSequence(0, 2 * N);
      double[] doubleArray = new double[N / 2];
      assertSteadyStateAllocationFree("IDLDoubleSequence", () ->
      {
         doubles.clear();
         for (int i = 0; i < N; i++)
            doubles.add(i * 0.25);
         doubles.addAll(doubleArray);
         doubles.putAt(N + 3, -7.25);
         sink += (long) (doubles.get(3) + doubles.remove() + doubles.remove(2));
         sink += doubles.size() + doubles.getBuffer().position();
         doublesCopy.set(doubles);
         doubles.clear();
         doubles.addAll(doubleArray);
         roundTrip(doubles, doublesCopy, buffer, false);
         roundTrip(doublesCopy, doubles, buffer, true);
      });
   }

   private static final String[] NAMES = {"joint_1", "épaule", "coude_€", "poignet_😀", "pince"};

   private static void testStringSequence(String what, IDLStringSequence strings, IDLStringSequence copy, IDLStringSequence received)
   {
      CDRBuffer buffer = new CDRBuffer();
      StringBuilder dynamic = new StringBuilder(32);
      List<String> nameList = new ArrayList<>(List.of(NAMES));
      IDLStringSequence shared = new IDLStringSequence();
      StringBuilder[] donated = {new StringBuilder(NAMES[0]), new StringBuilder(NAMES[1])};
      // remove(int) rotates the pooled builders through the positions, so the warm-up lasts until every pooled builder
      // has been at every position once (at most 20 builders): add(16) grows each 12-character builder once.
      assertSteadyStateAllocationFree(what, 20, () ->
      {
         // Typical control-tick fill: clear, then add names (JointState.name)
         strings.clear();
         for (int i = 0; i < NAMES.length; i++)
            strings.add(NAMES[i]);
         // Fill from a CharSequence / formatted content without creating a String
         dynamic.setLength(0);
         dynamic.append("sensor_").append(strings.size()).append('_').append(-42L);
         strings.add().append(dynamic);
         strings.add(16).append("bounded");
         strings.addAll(NAMES);
         strings.addAll(nameList); // RandomAccess list: read by index
         sink += strings.get(2).length() + strings.size() + strings.capacity();
         strings.remove(1);
         strings.removeLast();
         copy.set(strings);
         roundTrip(strings, received, buffer, false);
         roundTrip(copy, received, buffer, true);
         sink += CDRBuffer.utf8Length(received.get(0)) + received.calculateSizeBytes(3) + received.elementSizeBytes(0, 0);
         // Smaller than the high-water mark
         strings.clear();
         strings.add(NAMES[3]);
         copy.set(strings);
         roundTrip(copy, received, buffer, false);
         // Sharing API (stores the builders themselves, no copy): allocation free too
         shared.clear();
         shared.add(dynamic);
         shared.addAll(donated);
         sink += shared.size() + shared.get(2).length();
      });

      // Reused builders hold the new content only (no stale characters from the previous tick)
      strings.clear();
      strings.add().append("x");
      strings.add("y");
      assertEquals("[x, y]", strings.toString());
      strings.clear();
      StringBuilder reused = strings.add();
      assertEquals(0, reused.length(), "add() must return an empty builder");
      strings.clear();
      assertSame(reused, strings.add(16), "add(int) reuses the pooled builder");
      assertEquals(0, strings.get(0).length());
   }

   @Test
   public void testStringSequences()
   {
      testStringSequence("IDLStringSequence", new IDLStringSequence(), new IDLStringSequence(), new IDLStringSequence());
      testStringSequence("IDLStringSequence (bounded)", new IDLStringSequence(0, 20, 12), new IDLStringSequence(0, 20, 12), new IDLStringSequence(0, 20));
      testStringSequence("IDLWStringSequence", new IDLWStringSequence(), new IDLWStringSequence(), new IDLWStringSequence());
      testStringSequence("IDLWStringSequence (bounded)", new IDLWStringSequence(0, 20, 12), new IDLWStringSequence(0, 20), new IDLWStringSequence(0, 20));
   }

   @Test
   public void testObjectSequence()
   {
      IDLObjectSequence<geometry_msgs.PoseStamped> poses = new IDLObjectSequence<>(geometry_msgs.PoseStamped.class);
      IDLObjectSequence<geometry_msgs.PoseStamped> copy = new IDLObjectSequence<>(0, 10, geometry_msgs.PoseStamped.class);
      IDLObjectSequence<geometry_msgs.PoseStamped> received = new IDLObjectSequence<>(geometry_msgs.PoseStamped.class);
      IDLObjectSequence<geometry_msgs.PoseStamped> shared = new IDLObjectSequence<>(geometry_msgs.PoseStamped.class);
      geometry_msgs.PoseStamped[] external = {new geometry_msgs.PoseStamped(), new geometry_msgs.PoseStamped()};
      List<geometry_msgs.PoseStamped> externalList = new ArrayList<>(List.of(external));
      CDRBuffer buffer = new CDRBuffer();

      assertSteadyStateAllocationFree("IDLObjectSequence", () ->
      {
         poses.clear();
         for (int i = 0; i < 6; i++)
         {
            geometry_msgs.PoseStamped pose = poses.add();
            pose.getHeader().setFrameId(NAMES[i % NAMES.length]);
            pose.getPose().getPosition().setX(i);
         }
         sink += (long) poses.get(2).getPose().getPosition().getX() + poses.getLast().getHeader().getFrameId().length();
         poses.remove(1);
         poses.removeLast();
         // Deep copy into reused elements (what generated set() does)
         copy.set(poses, geometry_msgs.PoseStamped::set);
         roundTrip(poses, received, buffer, false);
         roundTrip(copy, received, buffer, true);
         sink += poses.calculateSizeBytes(0) + poses.elementSizeBytes(0, 1);
         // Smaller than the high-water mark, alternating
         poses.clear();
         poses.add().getHeader().setFrameId(NAMES[4]);
         copy.set(poses, geometry_msgs.PoseStamped::set);
         roundTrip(copy, received, buffer, false);
         // Sharing API (stores references): allocation free too
         shared.clear();
         shared.addAll(external);
         shared.addAll(externalList); // RandomAccess list: read by index
         shared.add(external[0]);
         shared.set(copy);
      });

      // Elements are reused, never reallocated: the same instances before and after the steady-state rounds
      geometry_msgs.PoseStamped first = received.get(0);
      roundTrip(poses, received, buffer, false);
      assertSame(first, received.get(0));
      geometry_msgs.PoseStamped copied = copy.get(0);
      copy.set(poses, geometry_msgs.PoseStamped::set);
      assertSame(copied, copy.get(0));
      assertNotSame(poses.get(0), copy.get(0));
   }
}
