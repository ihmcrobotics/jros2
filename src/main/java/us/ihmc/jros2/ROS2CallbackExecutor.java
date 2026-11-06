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

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;

/**
 * An allocation-free {@link Executor} for subscription callbacks (see {@link ROS2NodeOptions#callbackExecutor(Executor)}).
 * <p>
 * The JDK's executors allocate for every task or every idle wait (a {@code LinkedBlockingQueue} node per task, a
 * condition node whenever a {@code ThreadPoolExecutor} worker waits for work), so a callback executor built from them is
 * not allocation-free even though jros2's dispatch is. This executor keeps its tasks in a preallocated lock-free ring
 * and its worker threads park and unpark without allocating: {@link #execute(Runnable)} and the workers allocate
 * nothing.
 * <p>
 * The queue is bounded: {@link #execute(Runnable)} throws {@link RejectedExecutionException} when it is full. A jros2
 * subscription has at most one task pending at a time, so a capacity of at least the number of subscriptions that use
 * this executor never rejects their tasks. Tasks run in submission order on one worker thread; with several worker
 * threads they start in submission order. A task that throws is logged and the worker continues.
 * <p>
 * The workers are created by the {@link ThreadFactory} given to the constructor, which is the place to set real-time
 * scheduling, priority or CPU affinity; by default they are daemon threads named {@code <name>-<index>}. {@link #close()}
 * stops accepting tasks, lets the workers run the tasks already queued and waits for them to end.
 */
public class ROS2CallbackExecutor implements Executor, AutoCloseable
{
   /** Queue capacity of executors created without an explicit capacity. */
   public static final int DEFAULT_CAPACITY = 1024;

   private final String name;
   private final int capacity;
   private final AtomicLongArray sequences;
   private final AtomicReferenceArray<Runnable> tasks;
   private final AtomicLong producerPosition = new AtomicLong();
   private final AtomicLong consumerPosition = new AtomicLong();

   private final Thread[] workers;
   /** 1 while the worker with that index is (about to be) parked waiting for work. */
   private final AtomicIntegerArray idle;
   private volatile boolean closed;
   private final EndpointSupport.RateLimiter taskErrorLog = new EndpointSupport.RateLimiter(5, TimeUnit.SECONDS);

   /**
    * Creates an executor with one daemon worker thread named {@code <name>-0} and a queue of {@value #DEFAULT_CAPACITY}
    * tasks.
    */
   public ROS2CallbackExecutor(String name)
   {
      this(name, 1, DEFAULT_CAPACITY, null);
   }

   /**
    * @param name          prefix of the worker thread names (used when {@code threadFactory} is null) and of log messages
    * @param threadCount   number of worker threads, at least 1
    * @param capacity      maximum number of tasks waiting to run, at least 1
    * @param threadFactory creates the worker threads, e.g. real-time threads; null for daemon threads named
    *                      {@code <name>-<index>}
    */
   public ROS2CallbackExecutor(String name, int threadCount, int capacity, ThreadFactory threadFactory)
   {
      if (threadCount < 1)
      {
         throw new IllegalArgumentException(HotPathText.executorThreadCountTooSmall(threadCount));
      }
      if (capacity < 1)
      {
         throw new IllegalArgumentException(HotPathText.capacityTooSmall(capacity));
      }
      this.name = name;
      this.capacity = capacity;
      sequences = new AtomicLongArray(capacity);
      for (int i = 0; i < capacity; ++i)
      {
         sequences.set(i, i);
      }
      tasks = new AtomicReferenceArray<>(capacity);
      idle = new AtomicIntegerArray(threadCount);

      workers = new Thread[threadCount];
      try
      {
         for (int i = 0; i < threadCount; ++i)
         {
            int index = i;
            Runnable loop = () -> workLoop(index);
            Thread worker;
            if (threadFactory == null)
            {
               worker = new Thread(loop, HotPathText.executorWorkerName(name, i));
               worker.setDaemon(true);
            }
            else
            {
               worker = threadFactory.newThread(loop);
               if (worker == null)
               {
                  throw new IllegalStateException(HotPathText.executorThreadFactoryReturnedNull(name));
               }
            }
            workers[i] = worker;
         }
      }
      catch (RuntimeException | Error e)
      {
         closed = true;
         throw e;
      }
      for (Thread worker : workers)
      {
         worker.start();
      }
   }

   /**
    * Queues {@code task} to run on a worker thread. Thread-safe, lock-free and allocation-free.
    *
    * @throws RejectedExecutionException if the queue is full or the executor is closed
    * @throws NullPointerException       if {@code task} is null
    */
   @Override
   public void execute(Runnable task)
   {
      if (task == null)
      {
         throw new NullPointerException(HotPathText.executorTaskParameter());
      }
      if (closed || !offer(task))
      {
         throw new RejectedExecutionException(closed ? HotPathText.executorClosed(name) : HotPathText.executorFull(name, capacity));
      }
      wakeIdleWorker();
   }

   /**
    * D. Vyukov's bounded MPMC queue: the sequence of a slot is its position while free and position + 1 while it holds a
    * task.
    */
   private boolean offer(Runnable task)
   {
      long position = producerPosition.get();
      for (;;)
      {
         int index = (int) (position % capacity);
         long difference = sequences.get(index) - position;
         if (difference == 0)
         {
            if (producerPosition.compareAndSet(position, position + 1))
            {
               tasks.set(index, task);
               sequences.set(index, position + 1);
               return true;
            }
            position = producerPosition.get();
         }
         else if (difference < 0)
         {
            return false;
         }
         else
         {
            position = producerPosition.get();
         }
      }
   }

   private Runnable poll()
   {
      long position = consumerPosition.get();
      for (;;)
      {
         int index = (int) (position % capacity);
         long difference = sequences.get(index) - (position + 1);
         if (difference == 0)
         {
            if (consumerPosition.compareAndSet(position, position + 1))
            {
               Runnable task = tasks.get(index);
               tasks.set(index, null);
               sequences.set(index, position + capacity);
               return task;
            }
            position = consumerPosition.get();
         }
         else if (difference < 0)
         {
            return null;
         }
         else
         {
            position = consumerPosition.get();
         }
      }
   }

   private void wakeIdleWorker()
   {
      for (int i = 0; i < workers.length; ++i)
      {
         if (idle.get(i) != 0 && idle.compareAndSet(i, 1, 0))
         {
            LockSupport.unpark(workers[i]);
            return;
         }
      }
   }

   private void workLoop(int index)
   {
      CallbackContext.prepareThread();
      for (;;)
      {
         Runnable task = poll();
         if (task != null)
         {
            run(task);
            continue;
         }
         if (closed)
         {
            // Tasks queued before close() have run.
            return;
         }
         // Announce the wait before looking at the queue a last time: a producer that commits a task afterward sees the
         // flag and unparks us.
         idle.set(index, 1);
         if (isEmpty() && !closed)
         {
            LockSupport.park(this);
         }
         idle.set(index, 0);
         Thread.interrupted(); // A stray interrupt must not turn park() into a busy loop.
      }
   }

   private boolean isEmpty()
   {
      long position = consumerPosition.get();
      return sequences.get((int) (position % capacity)) != position + 1;
   }

   private void run(Runnable task)
   {
      try
      {
         task.run();
      }
      catch (Throwable throwable)
      {
         EndpointSupport.logRateLimited(taskErrorLog, Level.SEVERE, HotPathText.executorTaskFailed(name), throwable);
      }
   }

   /**
    * @return the maximum number of tasks that can wait to run
    */
   public int getCapacity()
   {
      return capacity;
   }

   /**
    * @return an estimate of the number of tasks waiting to run
    */
   public int getQueuedTaskCount()
   {
      long size = producerPosition.get() - consumerPosition.get();
      return (int) Math.max(0, Math.min(size, capacity));
   }

   /**
    * @return the worker threads (a copy of the array)
    */
   public Thread[] getWorkerThreads()
   {
      return workers.clone();
   }

   /**
    * @return true once {@link #close()} has been called
    */
   public boolean isClosed()
   {
      return closed;
   }

   /**
    * Stops accepting tasks, lets the workers run the tasks already queued and waits up to 5 seconds for them to end. Close
    * the nodes that use this executor first. Called from a worker thread it does not wait for that worker.
    */
   @Override
   public void close()
   {
      closed = true;
      for (Thread worker : workers)
      {
         LockSupport.unpark(worker);
      }
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      boolean interrupted = false;
      for (Thread worker : workers)
      {
         if (worker == Thread.currentThread())
         {
            continue;
         }
         long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
         try
         {
            if (remainingMillis > 0)
            {
               worker.join(remainingMillis);
            }
         }
         catch (InterruptedException e)
         {
            interrupted = true;
         }
         if (worker.isAlive())
         {
            jros2.getLogger().warning(HotPathText.executorWorkerDidNotEnd(name));
         }
      }
      if (interrupted)
      {
         Thread.currentThread().interrupt();
      }
   }
}
