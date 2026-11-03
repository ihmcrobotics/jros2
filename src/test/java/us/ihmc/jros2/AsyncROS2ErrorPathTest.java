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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.jros2.PubSubTestTools.*;

/**
 * Failures on the publish thread of an {@link AsyncROS2Node}: a queued message that cannot be serialized (a string with an
 * embedded NUL) is dropped, counted and logged, and a failing task is logged; in both cases the thread keeps publishing.
 */
public class AsyncROS2ErrorPathTest
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

   private static std_msgs.String_ string(String data)
   {
      std_msgs.String_ message = new std_msgs.String_();
      message.setData(data);
      return message;
   }

   @Test
   @Timeout(60)
   public void testUnserializableMessageIsDroppedAndPublishingContinues()
   {
      AsyncROS2Node node = new AsyncROS2Node("async_unserializable");
      try
      {
         ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(uniqueTopicName("async_unserializable"), std_msgs.String_.class);
         List<String> received = new ArrayList<>();
         ROS2Subscription<std_msgs.String_> subscription = node.createSubscription(topic, reader ->
         {
            std_msgs.String_ message = reader.read();
            if (message != null)
            {
               synchronized (received)
               {
                  received.add(message.getData().toString());
               }
            }
         }, reliableKeepAll());
         AsyncROS2Publisher<std_msgs.String_> publisher = node.createPublisher(topic, reliableKeepAll(), 8);
         awaitMatched(publisher, subscription);

         assertTrue(publisher.offer(string("before")));
         assertTrue(publisher.offer(string("embedded\0NUL")), "the message is queued; it fails on the publish thread");
         assertTrue(publisher.offer(string("after")));

         awaitCondition(() ->
                        {
                           synchronized (received)
                           {
                              return received.size() >= 2;
                           }
                        }, 30_000, "the messages around the failing one were not published");
         LogRecord record = awaitSevere("Failed to publish a queued message on topic '" + topic.getName() + "'");
         assertInstanceOf(IllegalArgumentException.class, record.getThrown());
         assertEquals(1, publisher.getDroppedMessageCount(), "the failing message is counted as dropped");
         synchronized (received)
         {
            assertEquals(List.of("before", "after"), received);
         }
         assertFalse(publisher.isClosed());
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(60)
   public void testFailingTaskIsLoggedAndLaterTasksRun() throws InterruptedException
   {
      AsyncROS2Node node = new AsyncROS2Node("async_failing_task");
      try
      {
         assertFalse(node.addTask(null));
         assertTrue(node.addTask(() ->
         {
            throw new IllegalStateException("boom from a publish thread task");
         }));
         CountDownLatch ran = new CountDownLatch(1);
         assertTrue(node.addTask(ran::countDown));
         assertTrue(ran.await(30, TimeUnit.SECONDS), "a task after the failing one did not run");
         LogRecord record = awaitSevere("A task on ");
         assertSame(IllegalStateException.class, record.getThrown().getClass());
         assertEquals("boom from a publish thread task", record.getThrown().getMessage());
      }
      finally
      {
         node.close();
      }
      assertFalse(node.addTask(() ->
      {
      }), "a closed node rejects tasks");
   }

   @Test
   @Timeout(60)
   public void testDomainConstructors()
   {
      int domainId = jros2.get().rosDomainId();
      AsyncROS2Node withDomain = new AsyncROS2Node("async_domain", domainId);
      AsyncROS2Node withTransports = new AsyncROS2Node("async_domain_transports", domainId, us.ihmc.fastddsjava.profiles.TransportDescriptorTypeTools.createUDPv4Descriptor());
      try
      {
         assertEquals(domainId, withDomain.getDomainId());
         assertEquals(domainId, withTransports.getDomainId());
      }
      finally
      {
         withDomain.close();
         withTransports.close();
      }
   }
}
