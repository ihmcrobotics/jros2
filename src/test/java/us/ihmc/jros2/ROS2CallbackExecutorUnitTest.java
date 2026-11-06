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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Functional tests of {@link ROS2CallbackExecutor}; its allocation behavior is checked by
 * {@code CallbackExecutorAllocationTest}.
 */
public class ROS2CallbackExecutorUnitTest
{
   @Test
   @Timeout(30)
   public void testRunsTasksInOrderOnTheWorker() throws InterruptedException
   {
      try (ROS2CallbackExecutor executor = new ROS2CallbackExecutor("executor-test"))
      {
         assertEquals(ROS2CallbackExecutor.DEFAULT_CAPACITY, executor.getCapacity());
         assertEquals(1, executor.getWorkerThreads().length);
         assertTrue(executor.getWorkerThreads()[0].isDaemon());
         assertEquals("executor-test-0", executor.getWorkerThreads()[0].getName());
         List<Integer> order = Collections.synchronizedList(new ArrayList<>());
         List<String> threads = Collections.synchronizedList(new ArrayList<>());
         CountDownLatch done = new CountDownLatch(500);
         for (int i = 0; i < 500; ++i)
         {
            int value = i;
            executor.execute(() ->
            {
               order.add(value);
               threads.add(Thread.currentThread().getName());
               done.countDown();
            });
            if ((i & 31) == 0)
               Thread.sleep(1); // the worker goes idle in between
         }
         assertTrue(done.await(10, TimeUnit.SECONDS));
         for (int i = 0; i < 500; ++i)
            assertEquals(i, order.get(i));
         for (String thread : threads)
            assertEquals("executor-test-0", thread);
      }
   }

   @Test
   @Timeout(30)
   public void testSeveralWorkersAndProducers() throws InterruptedException
   {
      AtomicInteger created = new AtomicInteger();
      try (ROS2CallbackExecutor executor = new ROS2CallbackExecutor("executor-test-multi", 3, 8, runnable ->
      {
         Thread thread = new Thread(runnable, "custom-worker-" + created.getAndIncrement());
         thread.setDaemon(true);
         return thread;
      }))
      {
         assertEquals(3, created.get());
         int producers = 4;
         int tasksPerProducer = 5_000;
         AtomicInteger ran = new AtomicInteger();
         AtomicInteger rejected = new AtomicInteger();
         Thread[] threads = new Thread[producers];
         for (int p = 0; p < producers; ++p)
         {
            threads[p] = new Thread(() ->
            {
               for (int i = 0; i < tasksPerProducer; ++i)
               {
                  for (;;)
                  {
                     try
                     {
                        executor.execute(ran::incrementAndGet);
                        break;
                     }
                     catch (RejectedExecutionException full)
                     {
                        rejected.incrementAndGet();
                        Thread.onSpinWait();
                     }
                  }
               }
            });
            threads[p].start();
         }
         for (Thread thread : threads)
            thread.join();
         PubSubTestTools.awaitCondition(() -> ran.get() == producers * tasksPerProducer, 10_000, "all tasks ran");
         assertTrue(executor.getQueuedTaskCount() == 0);
         assertTrue(rejected.get() >= 0);
      }
   }

   @Test
   @Timeout(30)
   public void testFullQueueRejectsAndFailingTaskIsLogged() throws InterruptedException
   {
      try (ROS2CallbackExecutor executor = new ROS2CallbackExecutor("executor-test-full", 1, 2, null))
      {
         CountDownLatch release = new CountDownLatch(1);
         CountDownLatch started = new CountDownLatch(1);
         executor.execute(() ->
         {
            started.countDown();
            try
            {
               release.await();
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
         });
         assertTrue(started.await(10, TimeUnit.SECONDS));
         executor.execute(() ->
         {
            throw new IllegalStateException("expected test failure, logged by the executor");
         });
         CountDownLatch afterFailure = new CountDownLatch(1);
         executor.execute(afterFailure::countDown);
         assertEquals(2, executor.getQueuedTaskCount());
         RejectedExecutionException full = assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> { }));
         assertTrue(full.getMessage().contains("full"), full.getMessage());
         assertThrows(NullPointerException.class, () -> executor.execute(null));
         release.countDown();
         assertTrue(afterFailure.await(10, TimeUnit.SECONDS), "the worker survives a failing task");
      }
   }

   @Test
   @Timeout(30)
   public void testCloseRunsQueuedTasksAndRejectsNewOnes() throws InterruptedException
   {
      ROS2CallbackExecutor executor = new ROS2CallbackExecutor("executor-test-close", 2, 16, null);
      CountDownLatch release = new CountDownLatch(1);
      AtomicInteger ran = new AtomicInteger();
      for (int i = 0; i < 2; ++i)
      {
         executor.execute(() ->
         {
            try
            {
               release.await();
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
            ran.incrementAndGet();
         });
      }
      for (int i = 0; i < 5; ++i)
         executor.execute(ran::incrementAndGet);
      Thread closer = new Thread(executor::close);
      closer.start();
      PubSubTestTools.awaitCondition(executor::isClosed, 10_000, "closed");
      RejectedExecutionException closed = assertThrows(RejectedExecutionException.class, () -> executor.execute(ran::incrementAndGet));
      assertTrue(closed.getMessage().contains("closed"), closed.getMessage());
      release.countDown();
      closer.join(10_000);
      assertFalse(closer.isAlive());
      assertEquals(7, ran.get(), "queued tasks ran before the workers ended");
      for (Thread worker : executor.getWorkerThreads())
         assertFalse(worker.isAlive());
      executor.close(); // idempotent
   }

   @Test
   @Timeout(30)
   public void testCloseFromAWorkerDoesNotWaitForItself() throws InterruptedException
   {
      ROS2CallbackExecutor executor = new ROS2CallbackExecutor("executor-test-self-close");
      CountDownLatch closed = new CountDownLatch(1);
      executor.execute(() ->
      {
         executor.close();
         closed.countDown();
      });
      assertTrue(closed.await(10, TimeUnit.SECONDS));
      executor.getWorkerThreads()[0].join(10_000);
      assertFalse(executor.getWorkerThreads()[0].isAlive());
   }

   @Test
   public void testInvalidArguments()
   {
      assertThrows(IllegalArgumentException.class, () -> new ROS2CallbackExecutor("bad", 0, 1, null));
      assertThrows(IllegalArgumentException.class, () -> new ROS2CallbackExecutor("bad", 1, 0, null));
      assertThrows(IllegalStateException.class, () -> new ROS2CallbackExecutor("bad", 1, 1, runnable -> null));
   }

   /** Subscription callbacks of a node run on the executor's worker. */
   @Test
   @Timeout(60)
   public void testSubscriptionCallbacksRunOnTheExecutor()
   {
      try (ROS2CallbackExecutor executor = new ROS2CallbackExecutor("executor-test-node"))
      {
         ROS2Node node = new ROS2Node("executor_test_node", ROS2NodeOptions.defaults().callbackExecutor(executor));
         try
         {
            ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(PubSubTestTools.uniqueTopicName("executor_test"), std_msgs.String_.class);
            List<String> threads = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger received = new AtomicInteger();
            std_msgs.String_ message = new std_msgs.String_();
            ROS2Subscription<std_msgs.String_> subscription = node.createSubscription(topic, reader ->
            {
               if (reader.read(message))
               {
                  threads.add(Thread.currentThread().getName());
                  received.incrementAndGet();
               }
            }, PubSubTestTools.reliableKeepAll());
            ROS2Publisher<std_msgs.String_> publisher = node.createPublisher(topic, PubSubTestTools.reliableKeepAll());
            PubSubTestTools.awaitMatched(publisher, subscription);
            std_msgs.String_ sent = new std_msgs.String_();
            sent.getData().append("hello");
            for (int i = 0; i < 100; ++i)
               publisher.publish(sent);
            PubSubTestTools.awaitCondition(() -> received.get() == 100, 15_000, "delivery");
            assertNotNull(threads.get(0));
            for (String thread : threads)
               assertEquals("executor-test-node-0", thread);
         }
         finally
         {
            node.close();
         }
      }
   }
}
