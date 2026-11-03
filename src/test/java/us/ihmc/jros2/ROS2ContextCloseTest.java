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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.jros2.PubSubTestTools.*;

/**
 * {@link ROS2Context#close()} called by two threads at once: the context is marked closed at once, and the second call does
 * not return before the first one has deleted the participant (here held up by a subscription callback that is still running
 * on a callback executor).
 */
public class ROS2ContextCloseTest
{
   @Test
   @Timeout(60)
   public void testConcurrentCloseWaitsForTheFirstClose() throws InterruptedException
   {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      ROS2Node publisherNode = new ROS2Node("context_close_publisher");
      ROS2Context context = ROS2Context.create();
      CountDownLatch callbackRunning = new CountDownLatch(1);
      CountDownLatch releaseCallback = new CountDownLatch(1);
      try
      {
         ROS2Node node = new ROS2Node("context_close_node", ROS2NodeOptions.defaults().context(context).callbackExecutor(executor));
         ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("context_close"), example_interfaces.Int64.class);
         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(topic, reader ->
         {
            reader.read();
            callbackRunning.countDown();
            try
            {
               releaseCallback.await(30, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
         }, reliableKeepAll());
         ROS2Publisher<example_interfaces.Int64> publisher = publisherNode.createPublisher(topic, reliableKeepAll());
         awaitMatched(publisher, subscription);
         publisher.publish(new example_interfaces.Int64());
         assertTrue(callbackRunning.await(30, TimeUnit.SECONDS), "callback did not start");
         assertTrue(context.toString().startsWith("ROS2Context[domain=" + context.getDomainId() + ", nodes=1"), context.toString());

         // The first close waits for the running callback before it deletes the participant
         Thread firstCloser = new Thread(context::close, "first-closer");
         firstCloser.start();
         awaitCondition(context::isClosed, 10_000, "close() must mark the context closed at once");

         AtomicBoolean participantClosedWhenSecondReturned = new AtomicBoolean();
         Thread secondCloser = new Thread(() ->
         {
            context.close();
            participantClosedWhenSecondReturned.set(context.getParticipant().isClosed());
         }, "second-closer");
         secondCloser.start();
         secondCloser.join(500);
         assertTrue(secondCloser.isAlive(), "a second close() must wait until the first one completed");
         assertTrue(firstCloser.isAlive());
         assertFalse(context.getParticipant().isClosed());

         releaseCallback.countDown();
         firstCloser.join(30_000);
         secondCloser.join(30_000);
         assertFalse(firstCloser.isAlive());
         assertFalse(secondCloser.isAlive());
         assertTrue(participantClosedWhenSecondReturned.get(), "the second close() returned before the participant was deleted");
         assertTrue(context.awaitClosed(1, TimeUnit.SECONDS));
         assertTrue(context.getNodes().isEmpty());
         assertTrue(node.isClosed());
         assertTrue(context.toString().endsWith(", nodes=0, closed]"), context.toString());

         // Closing again returns at once
         long start = System.nanoTime();
         context.close();
         assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 5_000);
      }
      finally
      {
         releaseCallback.countDown();
         context.close();
         publisherNode.close();
         executor.shutdownNow();
      }
   }
}
