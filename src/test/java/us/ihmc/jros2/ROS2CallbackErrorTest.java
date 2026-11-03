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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.jros2.PubSubTestTools.*;

/**
 * Exceptions thrown by user callbacks must never propagate into Fast-DDS (or the callback executor): they are logged at
 * SEVERE with the topic, message type and throw site, and the subscription keeps delivering later samples.
 */
public class ROS2CallbackErrorTest
{
   private final List<LogRecord> severe = new ArrayList<>();
   private final Handler handler = new Handler()
   {
      @Override
      public void publish(LogRecord record)
      {
         if (record.getLevel() == Level.SEVERE)
         {
            synchronized (severe)
            {
               severe.add(record);
            }
         }
      }

      @Override
      public void flush()
      {
      }

      @Override
      public void close()
      {
      }
   };

   @BeforeEach
   public void addHandler()
   {
      jros2.load();
      jros2.getLogger().addHandler(handler);
   }

   @AfterEach
   public void removeHandler()
   {
      jros2.getLogger().removeHandler(handler);
   }

   private LogRecord awaitSevere(String messagePart)
   {
      LogRecord[] found = new LogRecord[1];
      awaitCondition(() ->
                     {
                        synchronized (severe)
                        {
                           for (LogRecord record : severe)
                           {
                              if (record.getMessage() != null && record.getMessage().contains(messagePart))
                              {
                                 found[0] = record;
                                 return true;
                              }
                           }
                           return false;
                        }
                     }, 30_000, "no SEVERE log record containing '" + messagePart + "'");
      return found[0];
   }

   private static example_interfaces.Int64 int64(long value)
   {
      example_interfaces.Int64 message = new example_interfaces.Int64();
      message.setData(value);
      return message;
   }

   @Test
   @Timeout(60)
   public void testThrowingCallbackIsLoggedAndDeliveryContinuesInline()
   {
      testThrowingCallbackIsLoggedAndDeliveryContinues(null);
   }

   @Test
   @Timeout(60)
   public void testThrowingCallbackIsLoggedAndDeliveryContinuesOnAnExecutor()
   {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try
      {
         testThrowingCallbackIsLoggedAndDeliveryContinues(executor);
      }
      finally
      {
         executor.shutdownNow();
      }
   }

   private void testThrowingCallbackIsLoggedAndDeliveryContinues(ExecutorService executor)
   {
      ROS2NodeOptions options = ROS2NodeOptions.defaults();
      if (executor != null)
      {
         options.callbackExecutor(executor);
      }
      ROS2Node node = new ROS2Node("callback_error", options);
      try
      {
         String topicName = uniqueTopicName("callback_error");
         ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(topicName, example_interfaces.Int64.class);
         IllegalStateException withoutStackTrace = new IllegalStateException("no stack trace");
         withoutStackTrace.setStackTrace(new StackTraceElement[0]);
         List<Long> received = new ArrayList<>();
         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(topic, reader ->
         {
            example_interfaces.Int64 data = new example_interfaces.Int64();
            assertTrue(reader.read(data));
            synchronized (received)
            {
               received.add(data.getData());
            }
            if (data.getData() == 1)
            {
               throw new IllegalArgumentException("boom from callback");
            }
            if (data.getData() == 2)
            {
               throw new AssertionError("an Error is caught as well");
            }
            if (data.getData() == 3)
            {
               throw withoutStackTrace;
            }
         }, reliableKeepAll());
         ROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic, reliableKeepAll());
         awaitMatched(publisher, subscription);

         for (long value = 1; value <= 4; ++value)
         {
            publisher.publish(int64(value));
         }
         awaitCondition(() ->
                        {
                           synchronized (received)
                           {
                              return received.size() == 4;
                           }
                        }, 30_000, "the subscription must keep delivering after a callback threw");
         synchronized (received)
         {
            assertEquals(List.of(1L, 2L, 3L, 4L), received);
         }

         LogRecord exception = awaitSevere("boom from callback");
         String expectedPrefix = "Exception in ROS2Subscription callback on topic '" + topicName + "' (Int64) at " + getClass().getName();
         assertTrue(exception.getMessage().startsWith(expectedPrefix), exception.getMessage());
         assertTrue(exception.getMessage().endsWith(": boom from callback"), exception.getMessage());
         assertInstanceOf(IllegalArgumentException.class, exception.getThrown());

         LogRecord error = awaitSevere("an Error is caught as well");
         assertInstanceOf(AssertionError.class, error.getThrown());

         // Without a stack trace the throw site is the exception class
         LogRecord noStackTrace = awaitSevere("no stack trace");
         assertTrue(noStackTrace.getMessage().contains(" at java.lang.IllegalStateException: no stack trace"), noStackTrace.getMessage());
         assertSame(withoutStackTrace, noStackTrace.getThrown());

         assertFalse(subscription.isClosed());
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(60)
   public void testThrowingMatchedCallbackIsLogged() throws InterruptedException
   {
      ROS2Node node = new ROS2Node("matched_callback_error");
      try
      {
         ROS2Topic<example_interfaces.Int64> topic = new ROS2Topic<>(uniqueTopicName("matched_callback_error"), example_interfaces.Int64.class);
         List<Boolean> matchedEvents = new ArrayList<>();
         List<Long> received = new ArrayList<>();
         ROS2Subscription<example_interfaces.Int64> subscription = node.createSubscription(topic, reader ->
         {
            example_interfaces.Int64 data = new example_interfaces.Int64();
            if (reader.read(data))
            {
               synchronized (received)
               {
                  received.add(data.getData());
               }
            }
         }, (sub, publicationGuid, matched) ->
         {
            synchronized (matchedEvents)
            {
               matchedEvents.add(matched);
            }
            throw new IllegalStateException("boom from matched callback " + matched);
         }, reliableKeepAll());
         ROS2Publisher<example_interfaces.Int64> publisher = node.createPublisher(topic, reliableKeepAll());
         awaitMatched(publisher, subscription);

         LogRecord record = awaitSevere("boom from matched callback true");
         assertInstanceOf(IllegalStateException.class, record.getThrown());

         // The subscription still works
         publisher.publish(int64(7));
         awaitCondition(() ->
                        {
                           synchronized (received)
                           {
                              return received.contains(7L);
                           }
                        }, 30_000, "sample not received after the matched callback threw");

         // The unmatch is reported (and its exception logged) as well
         assertTrue(node.destroyPublisher(publisher));
         awaitSevere("boom from matched callback false");
         synchronized (matchedEvents)
         {
            assertEquals(List.of(true, false), matchedEvents);
         }
      }
      finally
      {
         node.close();
      }
   }
}
