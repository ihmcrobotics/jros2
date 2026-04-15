/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
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
import rcl_interfaces.ParameterEvent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ROS2ParameterTest
{
   private ROS2Node node;

   @BeforeEach
   public void setUp()
   {
      node = new ROS2Node("test_node");
   }

   @AfterEach
   public void tearDown()
   {
      if (node != null)
      {
         node.close();
      }
   }

   @Test
   public void testDeclareAndGetBoolParameter()
   {
      ROS2Parameter param = node.declareParameter("enable_feature", true);
      assertNotNull(param);
      assertEquals("enable_feature", param.getName());
      assertTrue(param.asBool());

      ROS2Parameter retrieved = node.getParameter("enable_feature");
      assertNotNull(retrieved);
      assertTrue(retrieved.asBool());
   }

   @Test
   public void testDeclareAndGetIntegerParameter()
   {
      ROS2Parameter param = node.declareParameter("max_connections", 42L);
      assertNotNull(param);
      assertEquals(42L, param.asLong());

      ROS2Parameter retrieved = node.getParameter("max_connections");
      assertNotNull(retrieved);
      assertEquals(42L, retrieved.asLong());
   }

   @Test
   public void testDeclareAndGetDoubleParameter()
   {
      ROS2Parameter param = node.declareParameter("max_speed", 3.14);
      assertNotNull(param);
      assertEquals(3.14, param.asDouble(), 0.0001);

      ROS2Parameter retrieved = node.getParameter("max_speed");
      assertNotNull(retrieved);
      assertEquals(3.14, retrieved.asDouble(), 0.0001);
   }

   @Test
   public void testDeclareAndGetStringParameter()
   {
      ROS2Parameter param = node.declareParameter("robot_name", "Atlas");
      assertNotNull(param);
      assertEquals("Atlas", param.asString());

      ROS2Parameter retrieved = node.getParameter("robot_name");
      assertNotNull(retrieved);
      assertEquals("Atlas", retrieved.asString());
   }

   @Test
   public void testSetParameter()
   {
      node.declareParameter("count", 10L);

      ROS2Parameter newValue = new ROS2Parameter("count", 20L);
      boolean success = node.setParameter(newValue);
      assertTrue(success);

      ROS2Parameter retrieved = node.getParameter("count");
      assertEquals(20L, retrieved.asLong());
   }

   @Test
   public void testSetParameterRequiresDeclaration()
   {
      ROS2Parameter param = new ROS2Parameter("undeclared", true);
      boolean success = node.setParameter(param);
      assertFalse(success, "Setting undeclared parameter should fail");
   }

   @Test
   public void testParameterEventPublishing() throws InterruptedException
   {
      ROS2Node subscriber = new ROS2Node("subscriber_node");
      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<ParameterEvent> receivedEvent = new AtomicReference<>();

      // Subscribe to parameter events
      subscriber.createSubscription(new ROS2Topic<>("/parameter_events", ParameterEvent.class), (eventReader) ->
      {
         ParameterEvent event = new ParameterEvent();
         eventReader.read(event);

         if (event.getNodeAsString().equals("test_node"))
         {
            receivedEvent.set(event);
            latch.countDown();
         }
      });

      Thread.sleep(100); // Let subscription establish

      // Declare a parameter (should trigger event)
      node.declareParameter("test_param", 123L);

      // Wait for event
      boolean received = latch.await(2, TimeUnit.SECONDS);
      assertTrue(received, "Should receive parameter event");

      ParameterEvent event = receivedEvent.get();
      assertNotNull(event);
      assertEquals("test_node", event.getNodeAsString());
      assertEquals(1, event.getNewParameters().size());
      assertEquals("test_param", event.getNewParameters().get(0).getNameAsString());

      subscriber.close();
   }
}
