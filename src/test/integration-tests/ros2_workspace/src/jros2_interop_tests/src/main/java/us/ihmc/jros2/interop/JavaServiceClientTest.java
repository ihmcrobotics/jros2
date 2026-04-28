package us.ihmc.jros2.interop;

import example_interfaces.AddTwoInts_Request;
import example_interfaces.AddTwoInts_Response;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2ServiceClient;

public class JavaServiceClientTest
{
   public static void main(java.lang.String[] args) throws InterruptedException
   {
      int domainId = Integer.parseInt(System.getenv().getOrDefault("ROS_DOMAIN_ID", "200"));
      System.out.println("Starting Java Service Client Test (Domain: " + domainId + ")");

      ROS2Node node = new ROS2Node("java_service_client_test", domainId);

      ROS2ServiceClient<AddTwoInts_Request, AddTwoInts_Response> serviceClient =
         node.createServiceClient(
            "/test/add_two_ints",
            AddTwoInts_Request.class,
            AddTwoInts_Response.class
         );

      System.out.println("Service client created, waiting for service...");
      Thread.sleep(2000);

      System.out.println("Making test calls...");

      for (int i = 0; i < 5; ++i)
      {
         AddTwoInts_Request request = new AddTwoInts_Request();
         request.setA(i * 10);
         request.setB(i * 5);

         AddTwoInts_Response response = serviceClient.sendRequestSync(request, 5000);

         if (response != null)
         {
            System.out.println("Service call " + i + ": " + request.getA() + " + " + request.getB() + " = " + response.getSum());
         }
         else
         {
            System.err.println("Service call " + i + " failed (timeout)");
         }

         Thread.sleep(500);
      }

      System.out.println("Service calls complete");

      node.close();
      System.out.println("Java Service Client Test Complete");
   }
}
