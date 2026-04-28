package us.ihmc.jros2.interop;

import example_interfaces.Fibonacci_Feedback;
import example_interfaces.Fibonacci_Goal;
import example_interfaces.Fibonacci_Result;
import us.ihmc.jros2.ROS2ActionClient;
import us.ihmc.jros2.ROS2Node;

import java.util.concurrent.CompletableFuture;

public class JavaActionClientTest
{
   public static void main(java.lang.String[] args) throws Exception
   {
      int domainId = Integer.parseInt(System.getenv().getOrDefault("ROS_DOMAIN_ID", "200"));
      System.out.println("Starting Java Action Client Test (Domain: " + domainId + ")");

      ROS2Node node = new ROS2Node("java_action_client_test", domainId);

      ROS2ActionClient<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> actionClient =
         node.createActionClient(
            "/test/fibonacci",
            Fibonacci_Goal.class,
            Fibonacci_Result.class,
            Fibonacci_Feedback.class
         );

      System.out.println("Action client created, waiting for server...");
      Thread.sleep(2000);

      System.out.println("Sending action goal...");

      Fibonacci_Goal goal = new Fibonacci_Goal();
      goal.setOrder(10);

      CompletableFuture<Fibonacci_Result> future = actionClient.sendGoalAsync(goal);

      Fibonacci_Result result = future.get();

      if (result != null)
      {
         System.out.println("Received result with sequence length: " + result.getSequence().size());
         if (result.getSequence().size() > 0)
         {
            System.out.print("Fibonacci sequence: ");
            for (int i = 0; i < result.getSequence().size(); ++i)
            {
               System.out.print(result.getSequence().get(i));
               if (i < result.getSequence().size() - 1)
                  System.out.print(", ");
            }
            System.out.println();
         }
         System.out.println("Action completed successfully");
      }
      else
      {
         System.err.println("ERROR: Did not receive action result");
      }

      node.close();
      System.out.println("Java Action Client Test Complete");
   }
}
