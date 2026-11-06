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

import us.ihmc.fastddsjava.fastddsjavaTools;
import us.ihmc.fastddsjava.natives.fastddsjava;
import us.ihmc.fastddsjava.natives.fastddsjavaCallback;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;

/**
 * Internal building blocks shared by {@link ROS2Publisher}, {@link ROS2Subscription} and {@link AsyncROS2Node}:
 * native ABI selection, a non-blocking gate that keeps native handles alive while they are in use, callback
 * trampolines that never pin their endpoint, a rate limiter for hot-path error logging, and a bounded
 * multi-producer/single-consumer sequencer.
 */
final class EndpointSupport
{
   /**
    * Forces the endpoints created afterward to use the ABI v1 native code paths even when ABI v2 natives are loaded.
    * Used by tests to exercise both paths; can also be set with the system property {@code jros2.native.forceAbiV1=true}
    * to work around a native problem in the field.
    */
   static volatile boolean forceNativeAbiV1 = Boolean.getBoolean("jros2.native.forceAbiV1");

   private EndpointSupport()
   {
   }

   /**
    * @return true if endpoints created now should use the ABI v2 single-call native paths
    *       ({@link fastddsjava#dataWriterWriteBuffer} and {@link fastddsjava#dataReaderTakeNextToBuffer}).
    */
   static boolean useNativeAbiV2()
   {
      return !forceNativeAbiV1 && fastddsjava.abiVersion() >= fastddsjava.ABI_V2;
   }

   /**
    * Keeps native handles alive while they are in use without ever blocking the user of the handles.
    * <p>
    * {@link #enter()} never blocks, so it can be called from Fast-DDS callback threads that hold Fast-DDS internal
    * locks. Teardown first {@link #close() closes} the gate and then {@link #awaitIdle() waits} for the in-flight users
    * to {@link #exit()}. Unlike a read/write lock, a waiting closer never blocks new users (they simply fail to enter),
    * which removes the lock-ordering deadlocks between Java locks and Fast-DDS locks.
    */
   static final class Gate
   {
      private final AtomicInteger users = new AtomicInteger();
      private volatile boolean closed;
      private volatile Thread waiter;

      /**
       * @return true if the caller may use the native handles; it must then call {@link #exit()} exactly once.
       */
      boolean enter()
      {
         users.incrementAndGet();
         if (closed)
         {
            exit();
            return false;
         }
         return true;
      }

      void exit()
      {
         if (users.decrementAndGet() == 0 && closed)
         {
            Thread thread = waiter;
            if (thread != null)
            {
               LockSupport.unpark(thread);
            }
         }
      }

      /**
       * Closes the gate. New {@link #enter()} calls fail from now on.
       *
       * @return true for the first call only
       */
      synchronized boolean close()
      {
         if (closed)
         {
            return false;
         }
         closed = true;
         return true;
      }

      boolean isClosed()
      {
         return closed;
      }

      /**
       * Waits until no caller is between {@link #enter()} and {@link #exit()}. Only valid after {@link #close()}.
       * Must not be called by a thread that is itself inside the gate.
       */
      void awaitIdle()
      {
         waiter = Thread.currentThread();
         try
         {
            long warnAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (users.get() != 0)
            {
               LockSupport.parkNanos(this, TimeUnit.MILLISECONDS.toNanos(1));
               if (System.nanoTime() - warnAt > 0)
               {
                  jros2.getLogger().warning(HotPathText.stillWaitingForNativeCall());
                  warnAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
               }
            }
         }
         finally
         {
            waiter = null;
         }
      }
   }

   /**
    * The object registered with the native listener (and therefore held by a JNI global reference). It only forwards to
    * its target, which {@link #clear()} drops, so a native listener that outlives its endpoint (for example because
    * Fast-DDS refused to delete the DataReader) cannot keep the endpoint or the user's callback reachable.
    */
   static final class Trampoline implements fastddsjavaCallback
   {
      private volatile Runnable target;

      Trampoline(Runnable target)
      {
         this.target = target;
      }

      @Override
      public void call()
      {
         Runnable runnable = target;
         if (runnable != null)
         {
            runnable.run();
         }
      }

      void clear()
      {
         target = null;
      }

      boolean isCleared()
      {
         return target == null;
      }
   }

   /**
    * Allows one log message per interval and counts the suppressed ones. Thread-safe and allocation-free when it
    * suppresses.
    */
   static final class RateLimiter
   {
      private final long intervalNanos;
      private final AtomicLong nextAllowedNanos;
      private final AtomicLong suppressed = new AtomicLong();

      RateLimiter(long interval, TimeUnit unit)
      {
         intervalNanos = unit.toNanos(interval);
         nextAllowedNanos = new AtomicLong(System.nanoTime() - intervalNanos);
      }

      /**
       * @return -1 if the message must be suppressed, otherwise the number of messages suppressed since the last allowed one
       */
      long tryAcquire()
      {
         long now = System.nanoTime();
         long next = nextAllowedNanos.get();
         if (now - next >= 0 && nextAllowedNanos.compareAndSet(next, now + intervalNanos))
         {
            return suppressed.getAndSet(0);
         }
         suppressed.incrementAndGet();
         return -1;
      }
   }

   /** {@link #logRetcode} operation: a DataWriter write. */
   static final int WRITE = 0;
   /** {@link #logRetcode} operation: a DataReader take. */
   static final int TAKE = 1;

   /**
    * Logs a failed Fast-DDS call at most once per rate-limiter interval. The operation is a constant rather than a text so
    * that the hot-path classes that call this contain no string literals (see {@link HotPathText}).
    *
    * @param operation {@link #WRITE} or {@link #TAKE}
    */
   static void logRetcode(RateLimiter limiter, int operation, String topicName, int retcode)
   {
      long suppressed = limiter.tryAcquire();
      if (suppressed >= 0)
      {
         String message = "Fast-DDS " + (operation == WRITE ? "write" : "take") + " failed on topic '" + topicName + "': "
                          + fastddsjavaTools.retcodeName(retcode) + " (" + retcode + ")";
         if (suppressed > 0)
         {
            message += " [" + suppressed + " similar messages suppressed]";
         }
         jros2.getLogger().log(Level.WARNING, message);
      }
   }

   /**
    * Logs a throwable at most once per rate-limiter interval.
    */
   static void logRateLimited(RateLimiter limiter, Level level, String message, Throwable throwable)
   {
      long suppressed = limiter.tryAcquire();
      if (suppressed >= 0)
      {
         String text = suppressed > 0 ? message + " [" + suppressed + " similar messages suppressed]" : message;
         jros2.getLogger().log(level, text, throwable);
      }
   }

   /**
    * Bounded multi-producer/single-consumer sequencer (D. Vyukov's bounded queue with per-slot sequence numbers).
    * It hands out slot indices; the caller owns the slot storage. A producer {@link #claim() claims} a position, fills
    * the slot and {@link #commit(long) commits} it; only then can the consumer {@link #peek() see} it. The consumer
    * {@link #release(long) releases} the slot when it is done with the contents, after which producers may reuse it.
    * <p>
    * Sequence encoding (supports a capacity of 1): slot free for position p: {@code 2p}; committed: {@code 2p + 1};
    * released: {@code 2(p + capacity)}. Allocation-free and lock-free for producers.
    */
   static final class BoundedMpscSequencer
   {
      private final int capacity;
      private final AtomicLongArray sequences;
      private final AtomicLong producerPosition = new AtomicLong();
      private final AtomicLong consumerPosition = new AtomicLong();

      BoundedMpscSequencer(int capacity)
      {
         if (capacity < 1)
         {
            throw new IllegalArgumentException(HotPathText.capacityTooSmall(capacity));
         }
         this.capacity = capacity;
         sequences = new AtomicLongArray(capacity);
         for (int i = 0; i < capacity; ++i)
         {
            sequences.set(i, 2L * i);
         }
      }

      int capacity()
      {
         return capacity;
      }

      int index(long position)
      {
         return (int) (position % capacity);
      }

      /**
       * Producer side: claims the next position.
       *
       * @return the claimed position, or -1 if the queue is full
       */
      long claim()
      {
         long position = producerPosition.get();
         for (;;)
         {
            long sequence = sequences.get(index(position));
            long difference = sequence - 2L * position;
            if (difference == 0)
            {
               if (producerPosition.compareAndSet(position, position + 1))
               {
                  return position;
               }
               position = producerPosition.get();
            }
            else if (difference < 0)
            {
               return -1;
            }
            else
            {
               position = producerPosition.get();
            }
         }
      }

      /**
       * Producer side: makes the claimed slot visible to the consumer. Must be called exactly once per claimed position.
       */
      void commit(long position)
      {
         sequences.set(index(position), 2L * position + 1);
      }

      /**
       * Consumer side.
       *
       * @return the oldest committed position, or -1 if there is none (empty, or the next slot is claimed but not committed yet)
       */
      long peek()
      {
         long position = consumerPosition.get();
         return sequences.get(index(position)) == 2L * position + 1 ? position : -1;
      }

      /**
       * Consumer side: frees the slot of a position returned by {@link #peek()}.
       */
      void release(long position)
      {
         sequences.set(index(position), 2L * (position + capacity));
         consumerPosition.set(position + 1);
      }

      /**
       * @return an estimate of the number of claimed but not yet released positions
       */
      int size()
      {
         long size = producerPosition.get() - consumerPosition.get();
         return (int) Math.max(0, Math.min(size, capacity));
      }
   }
}
