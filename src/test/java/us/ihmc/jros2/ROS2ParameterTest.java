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

import java.util.List;
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

   @Test
   public void testByteArrayParameter()
   {
      byte[] data = {1, 2, 3, 4, 5};
      ROS2Parameter param = new ROS2Parameter("byte_data", data);
      node.declareParameter(param);

      ROS2Parameter retrieved = node.getParameter("byte_data");
      assertNotNull(retrieved);
      assertArrayEquals(data, retrieved.asByteArray());
      assertEquals(ROS2Parameter.ParameterType.PARAMETER_BYTE_ARRAY, retrieved.getType());
   }

   @Test
   public void testBoolArrayParameter()
   {
      boolean[] flags = {true, false, true, true};
      ROS2Parameter param = new ROS2Parameter("flags", flags);
      node.declareParameter(param);

      ROS2Parameter retrieved = node.getParameter("flags");
      assertNotNull(retrieved);
      assertArrayEquals(flags, retrieved.asBoolArray());
      assertEquals(ROS2Parameter.ParameterType.PARAMETER_BOOL_ARRAY, retrieved.getType());
   }

   @Test
   public void testLongArrayParameter()
   {
      long[] numbers = {100L, 200L, 300L};
      ROS2Parameter param = new ROS2Parameter("numbers", numbers);
      node.declareParameter(param);

      ROS2Parameter retrieved = node.getParameter("numbers");
      assertNotNull(retrieved);
      assertArrayEquals(numbers, retrieved.asLongArray());
      assertEquals(ROS2Parameter.ParameterType.PARAMETER_INTEGER_ARRAY, retrieved.getType());
   }

   @Test
   public void testDoubleArrayParameter()
   {
      double[] values = {1.1, 2.2, 3.3};
      ROS2Parameter param = new ROS2Parameter("values", values);
      node.declareParameter(param);

      ROS2Parameter retrieved = node.getParameter("values");
      assertNotNull(retrieved);
      assertArrayEquals(values, retrieved.asDoubleArray(), 0.0001);
      assertEquals(ROS2Parameter.ParameterType.PARAMETER_DOUBLE_ARRAY, retrieved.getType());
   }

   @Test
   public void testStringArrayParameter()
   {
      String[] names = {"Alice", "Bob", "Charlie"};
      ROS2Parameter param = new ROS2Parameter("names", names);
      node.declareParameter(param);

      ROS2Parameter retrieved = node.getParameter("names");
      assertNotNull(retrieved);
      assertArrayEquals(names, retrieved.asStringArray());
      assertEquals(ROS2Parameter.ParameterType.PARAMETER_STRING_ARRAY, retrieved.getType());
   }

   @Test
   public void testNullParameterNameThrows()
   {
      assertThrows(IllegalArgumentException.class, () -> node.declareParameter(null, true));
      assertThrows(IllegalArgumentException.class, () -> node.declareParameter(null, 123L));
      assertThrows(IllegalArgumentException.class, () -> node.declareParameter(null, 3.14));
      assertThrows(IllegalArgumentException.class, () -> node.declareParameter(null, "value"));
   }

   @Test
   public void testEmptyParameterNameThrows()
   {
      assertThrows(IllegalArgumentException.class, () -> node.declareParameter("", true));
      assertThrows(IllegalArgumentException.class, () -> node.declareParameter("", 123L));
      assertThrows(IllegalArgumentException.class, () -> node.declareParameter("", 3.14));
      assertThrows(IllegalArgumentException.class, () -> node.declareParameter("", "value"));
   }

   @Test
   public void testNullParameterObjectThrows()
   {
      assertThrows(IllegalArgumentException.class, () -> node.declareParameter(null));
   }

   @Test
   public void testParameterUpdateEvent() throws InterruptedException
   {
      ROS2Node subscriber = new ROS2Node("subscriber_node");
      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<ParameterEvent> receivedEvent = new AtomicReference<>();

      // Subscribe to parameter events BEFORE declaring the parameter
      subscriber.createSubscription(new ROS2Topic<>("/parameter_events", ParameterEvent.class), (eventReader) ->
      {
         ParameterEvent event = new ParameterEvent();
         eventReader.read(event);

         if (event.getNodeAsString().equals("test_node") && event.getChangedParameters().size() > 0)
         {
            receivedEvent.set(event);
            latch.countDown();
         }
      });

      Thread.sleep(500); // Give more time for subscription to establish

      // Declare initial parameter
      node.declareParameter("counter", 0L);

      // Update the parameter (should trigger changed event)
      node.setParameter(new ROS2Parameter("counter", 10L));

      // Wait for event with longer timeout
      boolean received = latch.await(3, TimeUnit.SECONDS);
      assertTrue(received, "Should receive parameter changed event");

      ParameterEvent event = receivedEvent.get();
      assertNotNull(event);
      assertEquals(1, event.getChangedParameters().size());
      assertEquals("counter", event.getChangedParameters().get(0).getNameAsString());

      subscriber.close();
   }

   @Test
   public void testHasParameter()
   {
      assertFalse(node.hasParameter("nonexistent"));

      node.declareParameter("exists", 42L);
      assertTrue(node.hasParameter("exists"));
   }

   @Test
   public void testGetAllParameters()
   {
      node.declareParameter("param1", true);
      node.declareParameter("param2", 123L);
      node.declareParameter("param3", "test");

      var allParams = node.getParameters();
      assertEquals(3, allParams.size());
      assertTrue(allParams.containsKey("param1"));
      assertTrue(allParams.containsKey("param2"));
      assertTrue(allParams.containsKey("param3"));
   }

   @Test
   public void testParameterClientGetParameter() throws InterruptedException
   {
      // Create a separate node for the client
      ROS2Node clientNode = new ROS2Node("client_node");

      // Main node declares parameters
      node.declareParameter("target_speed", 5.0);
      node.declareParameter("max_accel", 2.5);

      // Client reads parameters from the main node
      ROS2ParameterClient client = clientNode.createParameterClient("test_node");

      // Allow time for service discovery
      Thread.sleep(500);

      ROS2Parameter speedParam = client.getParameter("target_speed", 1000);
      assertNotNull(speedParam, "Should be able to read parameter via client");
      assertEquals(5.0, speedParam.asDouble(), 0.001);

      ROS2Parameter accelParam = client.getParameter("max_accel", 1000);
      assertNotNull(accelParam);
      assertEquals(2.5, accelParam.asDouble(), 0.001);

      clientNode.destroyParameterClient(client);
      clientNode.close();
   }

   @Test
   public void testParameterClientSetParameter() throws InterruptedException
   {
      // Create a separate node for the client
      ROS2Node clientNode = new ROS2Node("client_node");

      // Main node declares a parameter
      node.declareParameter("mode", "auto");

      // Client modifies the parameter remotely
      ROS2ParameterClient client = clientNode.createParameterClient("test_node");

      // Allow time for service discovery
      Thread.sleep(500);

      ROS2Parameter newMode = new ROS2Parameter("mode", "manual");
      boolean success = client.setParameter(newMode, 1000);
      assertTrue(success, "Client should be able to set parameter");

      // Verify the node's parameter was actually changed
      ROS2Parameter nodeParam = node.getParameter("mode");
      assertEquals("manual", nodeParam.asString());

      clientNode.destroyParameterClient(client);
      clientNode.close();
   }

   @Test
   public void testParameterClientGetMultipleParameters() throws InterruptedException
   {
      // Create a separate node for the client
      ROS2Node clientNode = new ROS2Node("client_node");

      // Main node declares multiple parameters
      node.declareParameter("enabled", true);
      node.declareParameter("count", 10L);
      node.declareParameter("name", "robot1");

      // Client reads multiple parameters at once
      ROS2ParameterClient client = clientNode.createParameterClient("test_node");

      // Allow time for service discovery
      Thread.sleep(500);

      String[] paramNames = {"enabled", "count", "name"};
      List<ROS2Parameter> params = client.getParameters(paramNames, 1000);

      assertNotNull(params);
      assertEquals(3, params.size());
      assertTrue(params.get(0).asBool());
      assertEquals(10L, params.get(1).asLong());
      assertEquals("robot1", params.get(2).asString());

      clientNode.destroyParameterClient(client);
      clientNode.close();
   }
}
