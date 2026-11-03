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
package us.ihmc.jros2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import us.ihmc.fastddsjava.natives.fastddsjava;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests (no DDS) for {@link EndpointSupport} and {@link CallbackContext}.
 */
public class EndpointSupportTest
{
   /**
    * Reports which native ABI the endpoints use. Set {@code JROS2_EXPECT_NATIVE_ABI} (e.g. to 2 when running with
    * {@code JROS2_NATIVE_PATH} pointing at ABI v2 natives) to make sure the expected natives were really loaded, so that
    * the "DefaultNativeAbi" variants of the pub/sub tests exercise the ABI v2 paths.
    */
   @Test
   public void testNativeAbiSelection()
   {
      jros2.load();
      int abiVersion = fastddsjava.abiVersion();
      jros2.getLogger().info("Loaded fastddsjava native ABI v" + abiVersion + "; endpoints use the "
                             + (EndpointSupport.useNativeAbiV2() ? "ABI v2" : "ABI v1") + " write/take paths");
      assertTrue(abiVersion >= fastddsjava.ABI_V1);
      assertEquals(abiVersion >= fastddsjava.ABI_V2, EndpointSupport.useNativeAbiV2());

      String expected = System.getenv("JROS2_EXPECT_NATIVE_ABI");
      if (expected != null && !expected.isEmpty())
      {
         assertEquals(Integer.parseInt(expected.trim()), abiVersion, "JROS2_EXPECT_NATIVE_ABI");
      }

      try (PubSubTestTools.AbiScope ignored = PubSubTestTools.abi(true))
      {
         assertFalse(EndpointSupport.useNativeAbiV2(), "forcing ABI v1 selects the v1 paths");
      }
      assertEquals(abiVersion >= fastddsjava.ABI_V2, EndpointSupport.useNativeAbiV2(), "the scope restores the selection");
   }

   @Test
   public void testSequencerSingleProducerIsFifoAndBounded()
   {
      EndpointSupport.BoundedMpscSequencer sequencer = new EndpointSupport.BoundedMpscSequencer(4);
      long[] slots = new long[4];

      assertEquals(-1, sequencer.peek(), "empty");
      for (int i = 0; i < 4; ++i)
      {
         long position = sequencer.claim();
         assertEquals(i, position);
         slots[sequencer.index(position)] = 100 + i;
         sequencer.commit(position);
      }
      assertEquals(-1, sequencer.claim(), "full after capacity claims");
      assertEquals(4, sequencer.size());

      for (int i = 0; i < 4; ++i)
      {
         long position = sequencer.peek();
         assertEquals(i, position);
         assertEquals(100 + i, slots[sequencer.index(position)]);
         sequencer.release(position);
      }
      assertEquals(-1, sequencer.peek(), "empty again");
      assertEquals(0, sequencer.size());

      // Wraps around
      long position = sequencer.claim();
      assertEquals(4, position);
      assertEquals(0, sequencer.index(position));
   }

   @Test
   public void testSequencerCapacityOne()
   {
      EndpointSupport.BoundedMpscSequencer sequencer = new EndpointSupport.BoundedMpscSequencer(1);

      long first = sequencer.claim();
      assertEquals(0, first);
      assertEquals(-1, sequencer.claim(), "claimed but not committed slot is not free");
      assertEquals(-1, sequencer.peek(), "claimed but not committed slot is not visible");
      sequencer.commit(first);
      assertEquals(-1, sequencer.claim(), "committed but not consumed slot is not free");
      assertEquals(0, sequencer.peek());
      sequencer.release(0);
      assertEquals(1, sequencer.claim());
   }

   @Test
   public void testSequencerRejectsInvalidCapacity()
   {
      assertThrows(IllegalArgumentException.class, () -> new EndpointSupport.BoundedMpscSequencer(0));
   }

   @Test
   public void testSequencerSlotInvisibleUntilCommitted()
   {
      EndpointSupport.BoundedMpscSequencer sequencer = new EndpointSupport.BoundedMpscSequencer(8);
      long a = sequencer.claim();
      long b = sequencer.claim();
      sequencer.commit(b);
      assertEquals(-1, sequencer.peek(), "a later commit must not overtake an earlier claim");
      sequencer.commit(a);
      assertEquals(a, sequencer.peek());
      sequencer.release(a);
      assertEquals(b, sequencer.peek());
   }

   /**
    * Several producers race against one consumer. Every accepted item must be consumed exactly once, in per-producer order,
    * never torn, and accepted + rejected must equal offered.
    */
   @Test
   @Timeout(60)
   public void testSequencerMultiProducerExactlyOnce() throws Exception
   {
      final int producers = 4;
      final int perProducer = 200_000;
      final int capacity = 64;
      EndpointSupport.BoundedMpscSequencer sequencer = new EndpointSupport.BoundedMpscSequencer(capacity);
      long[] slotA = new long[capacity];
      long[] slotB = new long[capacity];

      AtomicLong accepted = new AtomicLong();
      AtomicLong rejected = new AtomicLong();
      AtomicBoolean producing = new AtomicBoolean(true);
      AtomicReference<String> error = new AtomicReference<>();
      long[] lastSequence = new long[producers];
      java.util.Arrays.fill(lastSequence, -1);
      AtomicLong consumed = new AtomicLong();

      Thread consumer = new Thread(() ->
      {
         while (producing.get() || sequencer.peek() >= 0)
         {
            long position = sequencer.peek();
            if (position < 0)
            {
               Thread.yield();
               continue;
            }
            int index = sequencer.index(position);
            long a = slotA[index];
            long b = slotB[index];
            sequencer.release(position);
            if (b != ~a)
            {
               error.compareAndSet(null, "torn slot: " + a + " / " + b);
            }
            int producer = (int) (a >>> 40);
            long sequence = a & ((1L << 40) - 1);
            if (sequence <= lastSequence[producer])
            {
               error.compareAndSet(null, "duplicate or reordered item from producer " + producer + ": " + sequence + " after " + lastSequence[producer]);
            }
            lastSequence[producer] = sequence;
            consumed.incrementAndGet();
         }
      }, "sequencer-consumer");
      consumer.start();

      CyclicBarrier start = new CyclicBarrier(producers);
      List<Thread> threads = new ArrayList<>();
      for (int p = 0; p < producers; ++p)
      {
         final long producerBits = ((long) p) << 40;
         Thread thread = new Thread(() ->
         {
            try
            {
               start.await();
            }
            catch (Exception e)
            {
               throw new RuntimeException(e);
            }
            for (long i = 0; i < perProducer; ++i)
            {
               long position = sequencer.claim();
               if (position < 0)
               {
                  rejected.incrementAndGet();
                  continue;
               }
               int index = sequencer.index(position);
               long value = producerBits | i;
               slotA[index] = value;
               slotB[index] = ~value;
               sequencer.commit(position);
               accepted.incrementAndGet();
            }
         }, "sequencer-producer-" + p);
         threads.add(thread);
         thread.start();
      }
      for (Thread thread : threads)
      {
         thread.join();
      }
      producing.set(false);
      consumer.join();

      assertNull(error.get(), error.get());
      assertEquals((long) producers * perProducer, accepted.get() + rejected.get());
      assertEquals(accepted.get(), consumed.get());
      assertTrue(accepted.get() > 0);
   }

   @Test
   @Timeout(30)
   public void testGateBlocksNewUsersAfterCloseAndWaitsForInFlightOnes() throws Exception
   {
      EndpointSupport.Gate gate = new EndpointSupport.Gate();
      assertTrue(gate.enter());
      gate.exit();

      // An in-flight user holds the gate while it is closed
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      Thread user = new Thread(() ->
      {
         assertTrue(gate.enter());
         entered.countDown();
         try
         {
            release.await();
         }
         catch (InterruptedException e)
         {
            Thread.currentThread().interrupt();
         }
         gate.exit();
      });
      user.start();
      assertTrue(entered.await(5, TimeUnit.SECONDS));

      assertTrue(gate.close(), "first close");
      assertFalse(gate.close(), "second close");
      assertTrue(gate.isClosed());
      assertFalse(gate.enter(), "no new users after close");

      AtomicBoolean idle = new AtomicBoolean();
      Thread closer = new Thread(() ->
      {
         gate.awaitIdle();
         idle.set(true);
      });
      closer.start();
      Thread.sleep(200);
      assertFalse(idle.get(), "awaitIdle must wait for the in-flight user");
      release.countDown();
      closer.join(5000);
      assertTrue(idle.get(), "awaitIdle returns once the in-flight user exited");
      user.join();
   }

   @Test
   public void testTrampolineForwardsUntilCleared()
   {
      AtomicInteger calls = new AtomicInteger();
      EndpointSupport.Trampoline trampoline = new EndpointSupport.Trampoline(calls::incrementAndGet);
      trampoline.call();
      assertEquals(1, calls.get());
      assertFalse(trampoline.isCleared());
      trampoline.clear();
      assertTrue(trampoline.isCleared());
      trampoline.call();
      assertEquals(1, calls.get());
   }

   @Test
   public void testRateLimiterAllowsOnePerIntervalAndCountsSuppressed() throws Exception
   {
      EndpointSupport.RateLimiter limiter = new EndpointSupport.RateLimiter(200, TimeUnit.MILLISECONDS);
      assertEquals(0, limiter.tryAcquire(), "first message is allowed");
      assertEquals(-1, limiter.tryAcquire());
      assertEquals(-1, limiter.tryAcquire());
      Thread.sleep(300);
      assertEquals(2, limiter.tryAcquire(), "reports the suppressed count");
      assertEquals(-1, limiter.tryAcquire());
   }

   @Test
   public void testCallbackContextDepth()
   {
      assertFalse(CallbackContext.isInCallback());
      CallbackContext.enter();
      CallbackContext.enter();
      assertTrue(CallbackContext.isInCallback());
      CallbackContext.exit();
      assertTrue(CallbackContext.isInCallback());
      CallbackContext.exit();
      assertFalse(CallbackContext.isInCallback());
   }

   @Test
   @Timeout(30)
   public void testRunDeferredRunsInOrderOnOneThreadAndAwaitDeferredWaits() throws Exception
   {
      List<Integer> order = new ArrayList<>();
      AtomicReference<Thread> deferredThread = new AtomicReference<>();
      CountDownLatch gate = new CountDownLatch(1);
      AtomicBoolean sawDeferredThread = new AtomicBoolean();

      CallbackContext.runDeferred(() ->
      {
         try
         {
            gate.await();
         }
         catch (InterruptedException e)
         {
            Thread.currentThread().interrupt();
         }
         deferredThread.set(Thread.currentThread());
         sawDeferredThread.set(CallbackContext.isDeferredThread());
         synchronized (order)
         {
            order.add(1);
         }
      });
      CallbackContext.runDeferred(() -> { throw new RuntimeException("deferred task failure is logged, not fatal"); });
      CallbackContext.runDeferred(() ->
      {
         // Awaiting from the deferred thread itself must not deadlock
         assertTrue(CallbackContext.awaitDeferred(1, TimeUnit.SECONDS));
         synchronized (order)
         {
            order.add(2);
         }
      });

      assertFalse(CallbackContext.awaitDeferred(100, TimeUnit.MILLISECONDS), "first task is still blocked");
      gate.countDown();
      assertTrue(CallbackContext.awaitDeferred(10, TimeUnit.SECONDS));
      synchronized (order)
      {
         assertEquals(java.util.Arrays.asList(1, 2), order);
      }
      assertTrue(sawDeferredThread.get());
      assertNotSame(Thread.currentThread(), deferredThread.get());
      assertFalse(CallbackContext.isDeferredThread());

      // From inside a callback awaitDeferred returns immediately instead of risking a deadlock
      CallbackContext.enter();
      try
      {
         assertTrue(CallbackContext.awaitDeferred(1, TimeUnit.MILLISECONDS));
      }
      finally
      {
         CallbackContext.exit();
      }
   }

   @Test
   public void testStatisticsCalculatorToStringAndRead()
   {
      StatisticsCalculator calculator = new StatisticsCalculator();
      calculator.record(1.0);
      calculator.record(3.0);
      String text = calculator.toString();
      assertTrue(text.startsWith("StatisticsCalculator: {AVERAGE="), text);
      assertTrue(text.contains("SAMPLE_COUNT="), text);

      Statistics statistics = new Statistics();
      calculator.read(statistics);
      assertEquals(2.0, statistics.get(Statistics.StatisticDataType.AVERAGE), 1e-12);
      assertEquals(2, statistics.get(Statistics.StatisticDataType.SAMPLE_COUNT));
      calculator.reset();
      calculator.read(statistics);
      assertEquals(0, statistics.get(Statistics.StatisticDataType.SAMPLE_COUNT));
   }

   /**
    * Message ages are negative when the publisher's clock is ahead of the subscriber's; the maximum used to start at
    * Double.MIN_VALUE (the smallest positive double) and was then reported as the maximum of all-negative samples.
    */
   @Test
   public void testStatisticsCalculatorMinimumAndMaximumOfNegativeValues()
   {
      StatisticsCalculator calculator = new StatisticsCalculator();
      calculator.record(-5.0);
      calculator.record(-2.0);
      calculator.record(Double.NaN); // ignored
      Statistics statistics = new Statistics();
      calculator.read(statistics);
      assertEquals(2, statistics.get(Statistics.StatisticDataType.SAMPLE_COUNT));
      assertEquals(-5.0, statistics.get(Statistics.StatisticDataType.MINIMUM), 0.0);
      assertEquals(-2.0, statistics.get(Statistics.StatisticDataType.MAXIMUM), 0.0);
      assertEquals(-2.0, statistics.get(Statistics.StatisticDataType.LATEST), 0.0);

      calculator.reset();
      calculator.record(-1.0);
      calculator.read(statistics);
      assertEquals(-1.0, statistics.get(Statistics.StatisticDataType.MAXIMUM), 0.0, "reset restores the initial extremes");
   }
}
