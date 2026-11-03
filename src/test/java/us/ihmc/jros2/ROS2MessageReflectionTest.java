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
import us.ihmc.fastddsjava.cdr.CDRBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The reflection helpers of {@link ROS2Message} that endpoints use on message classes: the DDS type name, a new instance
 * (for {@code read()} and the async publisher queue) and the header getter (for age statistics). A class that does not
 * have what they look for yields null and a logged error, not an exception on a publish or receive path.
 */
public class ROS2MessageReflectionTest
{
   private final List<LogRecord> severe = new ArrayList<>();
   /** The helpers log on the calling thread; records of other threads (e.g. endpoints left by other tests) are ignored. */
   private volatile long testThreadId;
   private final Handler handler = new Handler()
   {
      @Override
      public void publish(LogRecord record)
      {
         if (record.getLevel() == Level.SEVERE && record.getLongThreadID() == testThreadId)
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
      testThreadId = Thread.currentThread().getId();
      jros2.getLogger().addHandler(handler);
   }

   @AfterEach
   public void removeHandler()
   {
      jros2.getLogger().removeHandler(handler);
   }

   /** A message class without a {@code name} field and without a no-argument constructor. */
   static final class Incomplete implements ROS2Message<Incomplete>
   {
      Incomplete(int ignored)
      {
      }

      @Override
      public void set(Incomplete from)
      {
      }

      @Override
      public int calculateSizeBytes(int currentAlignment)
      {
         return 0;
      }

      @Override
      public void serialize(CDRBuffer buffer)
      {
      }

      @Override
      public void deserialize(CDRBuffer buffer)
      {
      }
   }

   @Test
   public void testGeneratedClasses()
   {
      assertEquals("std_msgs::msg::dds_::String_", ROS2Message.getNameFromMessageClass(std_msgs.String_.class));
      assertNotNull(ROS2Message.createInstance(geometry_msgs.PoseStamped.class));
      assertEquals("getHeader", ROS2Message.getHeaderMethod(geometry_msgs.PoseStamped.class).getName());
      assertNull(ROS2Message.getHeaderMethod(std_msgs.String_.class), "a message without a header has no header getter");
      synchronized (severe)
      {
         assertTrue(severe.isEmpty(), severe.toString());
      }
   }

   @Test
   public void testIncompleteClassesYieldNullAndAnError()
   {
      assertNull(ROS2Message.getNameFromMessageClass(Incomplete.class));
      assertNull(ROS2Message.createInstance(Incomplete.class));
      assertNull(ROS2Message.getTypeHashFromMessageClass(Incomplete.class));
      synchronized (severe)
      {
         assertEquals(2, severe.size(), severe.toString());
         assertInstanceOf(NoSuchFieldException.class, severe.get(0).getThrown());
         assertInstanceOf(NoSuchMethodException.class, severe.get(1).getThrown());
      }
   }

   @Test
   public void testTypeHashAcceptsUpperCaseHex()
   {
      assertEquals(UpperCaseHash.typeHash, ROS2Message.getTypeHashFromMessageClass(UpperCaseHash.class));
   }

   public static final class UpperCaseHash
   {
      public static final String typeHash = "RIHS01_DF668C740482BBD48FB39D76A70DFD4BD59DB1288021743503259E948F6B1A18";
   }
}
