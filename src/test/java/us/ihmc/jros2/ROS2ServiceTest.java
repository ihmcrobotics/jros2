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

import example_interfaces.AddTwoInts_Request;
import example_interfaces.AddTwoInts_Response;
import example_interfaces.Trigger_Request;
import example_interfaces.Trigger_Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ROS2ServiceTest
{
   private static final long WAIT_MS = 10000;

   private static AddTwoInts_Request addTwoInts(long a, long b)
   {
      AddTwoInts_Request request = new AddTwoInts_Request();
      request.setA(a);
      request.setB(b);
      return request;
   }

   private static ROS2ServiceCallback<AddTwoInts_Request, AddTwoInts_Response> addTwoIntsCallback()
   {
      return (request, response) -> response.setSum(request.getA() + request.getB());
   }

   @Test
   @Timeout(60)
   public void testAddTwoIntsSameNode() throws Exception
   {
      try (ROS2Node node = new ROS2Node("service_test_same_node"))
      {
         node.createService("/jros2_test/add_two_ints_same", AddTwoInts_Request.class, AddTwoInts_Response.class, addTwoIntsCallback());
         ROS2Client<AddTwoInts_Request, AddTwoInts_Response> client = node.createClient("/jros2_test/add_two_ints_same",
                                                                                        AddTwoInts_Request.class,
                                                                                        AddTwoInts_Response.class);

         assertTrue(client.waitForService(WAIT_MS));
         AddTwoInts_Response response = client.sendRequest(addTwoInts(2, 3)).get(WAIT_MS, TimeUnit.MILLISECONDS);
         assertEquals(5, response.getSum());
         assertEquals(0, client.getPendingRequestCount());
      }
   }

   @Test
   @Timeout(60)
   public void testAddTwoIntsSeparateNodes() throws Exception
   {
      try (ROS2Node serverNode = new ROS2Node("service_test_server"); ROS2Node clientNode = new ROS2Node("service_test_client"))
      {
         AtomicInteger requestsServed = new AtomicInteger();
         serverNode.createService("/jros2_test/add_two_ints", AddTwoInts_Request.class, AddTwoInts_Response.class, (request, response) ->
         {
            requestsServed.incrementAndGet();
            response.setSum(request.getA() + request.getB());
         });
         ROS2Client<AddTwoInts_Request, AddTwoInts_Response> client = clientNode.createClient("/jros2_test/add_two_ints",
                                                                                              AddTwoInts_Request.class,
                                                                                              AddTwoInts_Response.class);
         assertTrue(client.waitForService(WAIT_MS));

         // Many requests in flight at once, each response must go to its own future
         List<CompletableFuture<AddTwoInts_Response>> futures = new ArrayList<>();
         for (int i = 0; i < 50; i++)
         {
            futures.add(client.sendRequest(addTwoInts(i, 1000L * i)));
         }
         for (int i = 0; i < futures.size(); i++)
         {
            assertEquals(1001L * i, futures.get(i).get(WAIT_MS, TimeUnit.MILLISECONDS).getSum());
         }
         assertEquals(50, requestsServed.get());
         assertEquals(0, client.getPendingRequestCount());
      }
   }

   @Test
   @Timeout(60)
   public void testTwoClientsOnlyReceiveTheirOwnResponses() throws Exception
   {
      try (ROS2Node serverNode = new ROS2Node("service_test_server2");
           ROS2Node clientNodeA = new ROS2Node("service_test_client_a");
           ROS2Node clientNodeB = new ROS2Node("service_test_client_b"))
      {
         serverNode.createService("/jros2_test/add_two_ints_shared", AddTwoInts_Request.class, AddTwoInts_Response.class, addTwoIntsCallback());
         ROS2Client<AddTwoInts_Request, AddTwoInts_Response> clientA = clientNodeA.createClient("/jros2_test/add_two_ints_shared",
                                                                                                AddTwoInts_Request.class,
                                                                                                AddTwoInts_Response.class);
         ROS2Client<AddTwoInts_Request, AddTwoInts_Response> clientB = clientNodeB.createClient("/jros2_test/add_two_ints_shared",
                                                                                                AddTwoInts_Request.class,
                                                                                                AddTwoInts_Response.class);
         assertTrue(clientA.waitForService(WAIT_MS));
         assertTrue(clientB.waitForService(WAIT_MS));

         // Both clients' first request has the same sequence number, so the GUID check is what keeps them apart
         CompletableFuture<AddTwoInts_Response> futureA = clientA.sendRequest(addTwoInts(1, 1));
         CompletableFuture<AddTwoInts_Response> futureB = clientB.sendRequest(addTwoInts(100, 100));

         assertEquals(2, futureA.get(WAIT_MS, TimeUnit.MILLISECONDS).getSum());
         assertEquals(200, futureB.get(WAIT_MS, TimeUnit.MILLISECONDS).getSum());
      }
   }

   @Test
   @Timeout(60)
   public void testEmptyRequest() throws Exception
   {
      try (ROS2Node node = new ROS2Node("service_test_trigger"))
      {
         node.createService("/jros2_test/trigger", Trigger_Request.class, Trigger_Response.class, (request, response) ->
         {
            response.setSuccess(true);
            response.getMessage().append("triggered");
         });
         ROS2Client<Trigger_Request, Trigger_Response> client = node.createClient("/jros2_test/trigger", Trigger_Request.class, Trigger_Response.class);
         assertTrue(client.waitForService(WAIT_MS));

         for (int i = 0; i < 3; i++)
         {
            Trigger_Response response = client.sendRequest(new Trigger_Request()).get(WAIT_MS, TimeUnit.MILLISECONDS);
            assertTrue(response.getSuccess());
            // The response is reset before each callback, so the message does not accumulate
            assertEquals("triggered", response.getMessage().toString());
         }
      }
   }

   @Test
   @Timeout(60)
   public void testClosingClientCancelsPendingRequests() throws Exception
   {
      ROS2Node node = new ROS2Node("service_test_no_server");
      ROS2Client<AddTwoInts_Request, AddTwoInts_Response> client = node.createClient("/jros2_test/nobody_home",
                                                                                     AddTwoInts_Request.class,
                                                                                     AddTwoInts_Response.class);
      assertFalse(client.waitForService(500));
      assertFalse(client.isServiceAvailable());

      CompletableFuture<AddTwoInts_Response> future = client.sendRequest(addTwoInts(1, 2));
      assertEquals(1, client.getPendingRequestCount());

      assertTrue(node.destroyClient(client));
      assertTrue(future.isCancelled());
      assertThrows(CancellationException.class, () -> future.get(WAIT_MS, TimeUnit.MILLISECONDS));

      // A closed client refuses new requests
      assertThrows(ExecutionException.class, () -> client.sendRequest(addTwoInts(1, 2)).get(WAIT_MS, TimeUnit.MILLISECONDS));
      node.close();
   }

   @Test
   @Timeout(60)
   public void testServiceAndTopicWithSameName() throws Exception
   {
      // A topic and a service may share a ROS name: they map to different DDS topics (rt, rq/rr)
      try (ROS2Node node = new ROS2Node("service_test_same_name"))
      {
         ROS2Topic<AddTwoInts_Request> topic = new ROS2Topic<>("/jros2_test/shared_name", AddTwoInts_Request.class);
         ROS2Publisher<AddTwoInts_Request> publisher = node.createPublisher(topic);
         ROS2Subscription<AddTwoInts_Request> subscription = node.createSubscription(topic);

         node.createService("/jros2_test/shared_name", AddTwoInts_Request.class, AddTwoInts_Response.class, addTwoIntsCallback());
         ROS2Client<AddTwoInts_Request, AddTwoInts_Response> client = node.createClient("/jros2_test/shared_name",
                                                                                        AddTwoInts_Request.class,
                                                                                        AddTwoInts_Response.class);
         assertTrue(client.waitForService(WAIT_MS));
         assertEquals(7, client.sendRequest(addTwoInts(3, 4)).get(WAIT_MS, TimeUnit.MILLISECONDS).getSum());

         assertTrue(publisher.waitForSubscription(WAIT_MS));
         publisher.publish(addTwoInts(5, 6));
         AddTwoInts_Request received = new AddTwoInts_Request();
         long deadline = System.currentTimeMillis() + WAIT_MS;
         while (!subscription.read(received) && System.currentTimeMillis() < deadline)
         {
            Thread.sleep(10);
         }
         assertEquals(5, received.getA());
         assertEquals(6, received.getB());
         assertEquals(1, node.getServices().size());
         assertEquals(1, node.getClients().size());
      }
   }
}
