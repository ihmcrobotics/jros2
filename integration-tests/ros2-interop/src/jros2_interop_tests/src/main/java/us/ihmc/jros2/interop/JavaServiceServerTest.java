package us.ihmc.jros2.interop;

import example_interfaces.AddTwoInts_Request;
import example_interfaces.AddTwoInts_Response;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2ServiceServer;

public class JavaServiceServerTest
{
   public static void main(java.lang.String[] args) throws InterruptedException
   {
      int domainId = Integer.parseInt(System.getenv().getOrDefault("ROS_DOMAIN_ID", "200"));
      System.out.println("Starting Java Service Server Test (Domain: " + domainId + ")");

      ROS2Node node = new ROS2Node("java_service_server_test", domainId);

      ROS2ServiceServer<AddTwoInts_Request, AddTwoInts_Response> serviceServer =
         node.createServiceServer(
            "/test/add_two_ints",
            AddTwoInts_Request.class,
            AddTwoInts_Response.class,
            (request, response) -> {
               long sum = request.getA() + request.getB();
               response.setSum(sum);
               System.out.println("Service request: " + request.getA() + " + " + request.getB() + " = " + sum);
            }
         );

      System.out.println("Service server ready, waiting for requests...");
      Thread.sleep(5000);

      node.close();
      System.out.println("Java Service Server Test Complete");
   }
}
