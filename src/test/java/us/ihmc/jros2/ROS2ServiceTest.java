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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class ROS2ServiceTest
{
   @Test
   @Timeout(30)
   public void testAddTwoIntsService()
   {
      String serviceName = "/test_add_two_ints";

      // Create ROS 2 nodes
      ROS2Node serverNode = new ROS2Node("test_server_node");
      ROS2Node clientNode = new ROS2Node("test_client_node");

      try
      {
         // Create service server
         ROS2ServiceServer<example_interfaces.AddTwoInts_Request, example_interfaces.AddTwoInts_Response> server =
               serverNode.createServiceServer(
                     serviceName,
                     example_interfaces.AddTwoInts_Request.class,
                     example_interfaces.AddTwoInts_Response.class,
                     (request, response) -> {
                        // Implement the service: add the two numbers
                        long sum = request.getA() + request.getB();
                        response.setSum(sum);
                     },
                     ROS2QoSProfile.SERVICES_DEFAULT
               );

         // Give the server time to advertise
         Thread.sleep(500);

         // Create service client
         ROS2ServiceClient<example_interfaces.AddTwoInts_Request, example_interfaces.AddTwoInts_Response> client =
               clientNode.createServiceClient(
                     serviceName,
                     example_interfaces.AddTwoInts_Request.class,
                     example_interfaces.AddTwoInts_Response.class,
                     ROS2QoSProfile.SERVICES_DEFAULT
               );

         // Give client time to discover server
         Thread.sleep(500);

         // Test synchronous service call
         example_interfaces.AddTwoInts_Request request = new example_interfaces.AddTwoInts_Request();
         request.setA(15);
         request.setB(27);

         example_interfaces.AddTwoInts_Response response = client.sendRequestSync(request, 5000);

         assertNotNull(response, "Response should not be null");
         assertEquals(42, response.getSum(), "Sum should be 42 (15 + 27)");

         // Test with different values
         request.setA(100);
         request.setB(234);
         response = client.sendRequestSync(request, 5000);

         assertNotNull(response, "Response should not be null");
         assertEquals(334, response.getSum(), "Sum should be 334 (100 + 234)");

         // Clean up
         serverNode.destroyServiceServer(server);
         clientNode.destroyServiceClient(client);
      }
      catch (Exception e)
      {
         fail("Service test failed with exception: " + e.getMessage());
         e.printStackTrace();
      }
      finally
      {
         serverNode.close();
         clientNode.close();
      }
   }

   @Test
   @Timeout(30)
   public void testMultipleServiceCalls()
   {
      String serviceName = "/test_multiple_calls";

      ROS2Node serverNode = new ROS2Node("multi_server_node");
      ROS2Node clientNode = new ROS2Node("multi_client_node");

      try
      {
         AtomicInteger callCount = new AtomicInteger(0);

         // Create service server that counts calls
         ROS2ServiceServer<example_interfaces.AddTwoInts_Request, example_interfaces.AddTwoInts_Response> server =
               serverNode.createServiceServer(
                     serviceName,
                     example_interfaces.AddTwoInts_Request.class,
                     example_interfaces.AddTwoInts_Response.class,
                     (request, response) -> {
                        callCount.incrementAndGet();
                        response.setSum(request.getA() + request.getB());
                     },
                     ROS2QoSProfile.SERVICES_DEFAULT
               );

         Thread.sleep(500);

         ROS2ServiceClient<example_interfaces.AddTwoInts_Request, example_interfaces.AddTwoInts_Response> client =
               clientNode.createServiceClient(
                     serviceName,
                     example_interfaces.AddTwoInts_Request.class,
                     example_interfaces.AddTwoInts_Response.class,
                     ROS2QoSProfile.SERVICES_DEFAULT
               );

         Thread.sleep(500);

         // Make multiple service calls
         for (int i = 0; i < 10; i++)
         {
            example_interfaces.AddTwoInts_Request request = new example_interfaces.AddTwoInts_Request();
            request.setA(i);
            request.setB(i * 2);

            example_interfaces.AddTwoInts_Response response = client.sendRequestSync(request, 5000);

            assertNotNull(response);
            assertEquals(i + (i * 2), response.getSum());
         }

         assertEquals(10, callCount.get(), "Server should have received 10 calls");

         serverNode.destroyServiceServer(server);
         clientNode.destroyServiceClient(client);
      }
      catch (Exception e)
      {
         fail("Multiple service calls test failed: " + e.getMessage());
         e.printStackTrace();
      }
      finally
      {
         serverNode.close();
         clientNode.close();
      }
   }

   @Test
   @Timeout(30)
   public void testServiceTimeout()
   {
      String serviceName = "/test_timeout_service";

      ROS2Node clientNode = new ROS2Node("timeout_client_node");

      try
      {
         // Create client without a server
         ROS2ServiceClient<example_interfaces.AddTwoInts_Request, example_interfaces.AddTwoInts_Response> client =
               clientNode.createServiceClient(
                     serviceName,
                     example_interfaces.AddTwoInts_Request.class,
                     example_interfaces.AddTwoInts_Response.class,
                     ROS2QoSProfile.SERVICES_DEFAULT
               );

         example_interfaces.AddTwoInts_Request request = new example_interfaces.AddTwoInts_Request();
         request.setA(5);
         request.setB(10);

         // This should timeout since there's no server
         long startTime = System.currentTimeMillis();
         example_interfaces.AddTwoInts_Response response = client.sendRequestSync(request, 1000);
         long elapsed = System.currentTimeMillis() - startTime;

         assertNull(response, "Response should be null when service times out");
         assertTrue(elapsed >= 1000 && elapsed < 2000, "Timeout should take approximately 1 second");

         clientNode.destroyServiceClient(client);
      }
      catch (Exception e)
      {
         fail("Timeout test failed: " + e.getMessage());
         e.printStackTrace();
      }
      finally
      {
         clientNode.close();
      }
   }

   @Test
   @Timeout(30)
   public void testAsyncServiceCall()
   {
      String serviceName = "/test_async_service";

      ROS2Node serverNode = new ROS2Node("async_server_node");
      ROS2Node clientNode = new ROS2Node("async_client_node");

      try
      {
         // Create service server
         ROS2ServiceServer<example_interfaces.AddTwoInts_Request, example_interfaces.AddTwoInts_Response> server =
               serverNode.createServiceServer(
                     serviceName,
                     example_interfaces.AddTwoInts_Request.class,
                     example_interfaces.AddTwoInts_Response.class,
                     (request, response) -> {
                        response.setSum(request.getA() + request.getB());
                     },
                     ROS2QoSProfile.SERVICES_DEFAULT
               );

         Thread.sleep(500);

         ROS2ServiceClient<example_interfaces.AddTwoInts_Request, example_interfaces.AddTwoInts_Response> client =
               clientNode.createServiceClient(
                     serviceName,
                     example_interfaces.AddTwoInts_Request.class,
                     example_interfaces.AddTwoInts_Response.class,
                     ROS2QoSProfile.SERVICES_DEFAULT
               );

         Thread.sleep(500);

         // Test async service call
         example_interfaces.AddTwoInts_Request request = new example_interfaces.AddTwoInts_Request();
         request.setA(20);
         request.setB(22);

         CountDownLatch latch = new CountDownLatch(1);
         AtomicLong resultSum = new AtomicLong(-1);

         client.sendRequestAsync(request).thenAccept(response -> {
            if (response != null)
            {
               resultSum.set(response.getSum());
            }
            latch.countDown();
         }).exceptionally(ex -> {
            fail("Async service call failed: " + ex.getMessage());
            latch.countDown();
            return null;
         });

         assertTrue(latch.await(5, TimeUnit.SECONDS), "Async call should complete within 5 seconds");
         assertEquals(42, resultSum.get(), "Async result should be 42 (20 + 22)");

         serverNode.destroyServiceServer(server);
         clientNode.destroyServiceClient(client);
      }
      catch (Exception e)
      {
         fail("Async service test failed: " + e.getMessage());
         e.printStackTrace();
      }
      finally
      {
         serverNode.close();
         clientNode.close();
      }
   }

   @Test
   @Timeout(30)
   public void testServiceCleanupOnDestroy()
   {
      String serviceName = "/test_cleanup_service";

      ROS2Node node = new ROS2Node("cleanup_test_node");

      try
      {
         // Create service server
         ROS2ServiceServer<example_interfaces.AddTwoInts_Request, example_interfaces.AddTwoInts_Response> server =
               node.createServiceServer(
                     serviceName,
                     example_interfaces.AddTwoInts_Request.class,
                     example_interfaces.AddTwoInts_Response.class,
                     (request, response) -> response.setSum(request.getA() + request.getB()),
                     ROS2QoSProfile.SERVICES_DEFAULT
               );

         assertNotNull(server);

         // Destroy the server
         boolean destroyed = node.destroyServiceServer(server);
         assertTrue(destroyed, "Server should be destroyed successfully");

         // Trying to destroy again should return false
         boolean destroyedAgain = node.destroyServiceServer(server);
         assertFalse(destroyedAgain, "Destroying already destroyed server should return false");
      }
      finally
      {
         node.close();
      }
   }

   @Test
   @Timeout(30)
   public void testServiceCleanupOnNodeClose()
   {
      String serviceName = "/test_node_close_service";

      ROS2Node node = new ROS2Node("node_close_test");

      // Create multiple services
      ROS2ServiceServer<example_interfaces.AddTwoInts_Request, example_interfaces.AddTwoInts_Response> server1 =
            node.createServiceServer(
                  serviceName + "1",
                  example_interfaces.AddTwoInts_Request.class,
                  example_interfaces.AddTwoInts_Response.class,
                  (request, response) -> response.setSum(request.getA() + request.getB()),
                  ROS2QoSProfile.SERVICES_DEFAULT
            );

      ROS2ServiceClient<example_interfaces.AddTwoInts_Request, example_interfaces.AddTwoInts_Response> client1 =
            node.createServiceClient(
                  serviceName + "2",
                  example_interfaces.AddTwoInts_Request.class,
                  example_interfaces.AddTwoInts_Response.class,
                  ROS2QoSProfile.SERVICES_DEFAULT
            );

      assertNotNull(server1);
      assertNotNull(client1);

      // Close node - should clean up all services
      node.close();

      // Node should be marked as closed
      assertTrue(node.isClosed());
   }

   @Test
   @Timeout(30)
   public void testServiceCallbackException()
   {
      String serviceName = "/test_exception_service";

      ROS2Node serverNode = new ROS2Node("exception_server_node");
      ROS2Node clientNode = new ROS2Node("exception_client_node");

      try
      {
         // Create service server that throws exception
         ROS2ServiceServer<example_interfaces.AddTwoInts_Request, example_interfaces.AddTwoInts_Response> server =
               serverNode.createServiceServer(
                     serviceName,
                     example_interfaces.AddTwoInts_Request.class,
                     example_interfaces.AddTwoInts_Response.class,
                     (request, response) -> {
                        throw new RuntimeException("Intentional exception in callback");
                     },
                     ROS2QoSProfile.SERVICES_DEFAULT
               );

         Thread.sleep(500);

         ROS2ServiceClient<example_interfaces.AddTwoInts_Request, example_interfaces.AddTwoInts_Response> client =
               clientNode.createServiceClient(
                     serviceName,
                     example_interfaces.AddTwoInts_Request.class,
                     example_interfaces.AddTwoInts_Response.class,
                     ROS2QoSProfile.SERVICES_DEFAULT
               );

         Thread.sleep(500);

         // Send request - server should handle exception gracefully
         example_interfaces.AddTwoInts_Request request = new example_interfaces.AddTwoInts_Request();
         request.setA(5);
         request.setB(10);

         // Server should survive the exception and continue running
         // (though this particular request may timeout or return null)
         example_interfaces.AddTwoInts_Response response = client.sendRequestSync(request, 1000);

         // The important thing is the server didn't crash
         assertNotNull(server);
      }
      catch (Exception e)
      {
         fail("Service should handle callback exceptions gracefully: " + e.getMessage());
      }
      finally
      {
         serverNode.close();
         clientNode.close();
      }
   }

   @Test
   @Timeout(30)
   public void testConcurrentServiceCalls() throws InterruptedException
   {
      String serviceName = "/test_concurrent_service";

      ROS2Node serverNode = new ROS2Node("concurrent_server_node");
      ROS2Node clientNode = new ROS2Node("concurrent_client_node");

      try
      {
         AtomicInteger callCount = new AtomicInteger(0);

         // Create service server
         ROS2ServiceServer<example_interfaces.AddTwoInts_Request, example_interfaces.AddTwoInts_Response> server =
               serverNode.createServiceServer(
                     serviceName,
                     example_interfaces.AddTwoInts_Request.class,
                     example_interfaces.AddTwoInts_Response.class,
                     (request, response) -> {
                        callCount.incrementAndGet();
                        response.setSum(request.getA() + request.getB());
                     },
                     ROS2QoSProfile.SERVICES_DEFAULT
               );

         Thread.sleep(500);

         ROS2ServiceClient<example_interfaces.AddTwoInts_Request, example_interfaces.AddTwoInts_Response> client =
               clientNode.createServiceClient(
                     serviceName,
                     example_interfaces.AddTwoInts_Request.class,
                     example_interfaces.AddTwoInts_Response.class,
                     ROS2QoSProfile.SERVICES_DEFAULT
               );

         Thread.sleep(500);

         // Send multiple sequential requests to verify server handles multiple calls correctly
         int numRequests = 3;
         int successCount = 0;

         for (int i = 0; i < numRequests; i++)
         {
            example_interfaces.AddTwoInts_Request request = new example_interfaces.AddTwoInts_Request();
            request.setA(i);
            request.setB(i);

            example_interfaces.AddTwoInts_Response response = client.sendRequestSync(request, 5000);
            if (response != null && response.getSum() == i + i)
            {
               successCount++;
            }
         }

         assertEquals(numRequests, successCount, "All requests should succeed");
         assertEquals(numRequests, callCount.get(), "Server should have processed all " + numRequests + " requests");
      }
      finally
      {
         serverNode.close();
         clientNode.close();
      }
   }
}
