package us.ihmc.jros2.interop;

import example_interfaces.Fibonacci_Feedback;
import example_interfaces.Fibonacci_Goal;
import example_interfaces.Fibonacci_Result;
import us.ihmc.jros2.ROS2ActionGoalCallback;
import us.ihmc.jros2.ROS2ActionServer;
import us.ihmc.jros2.ROS2Node;

public class JavaActionServerTest
{
   public static void main(java.lang.String[] args) throws InterruptedException
   {
      int domainId = Integer.parseInt(System.getenv().getOrDefault("ROS_DOMAIN_ID", "200"));
      System.out.println("Starting Java Action Server Test (Domain: " + domainId + ")");

      ROS2Node node = new ROS2Node("java_action_server_test", domainId);

      ROS2ActionGoalCallback<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> callback = (goal, result, feedbackPublisher) -> {
         System.out.println("Received Fibonacci goal: order = " + goal.getOrder());
         int order = goal.getOrder();
         int prev = 0;
         int curr = 1;
         result.getSequence().add(prev);
         if (order > 0)
            result.getSequence().add(curr);

         for (int i = 2; i <= order; i++)
         {
            int next = prev + curr;
            result.getSequence().add(next);
            prev = curr;
            curr = next;

            if (i % 3 == 0 || i == order)
            {
               Fibonacci_Feedback feedback = feedbackPublisher.createFeedback();
               for (int j = 0; j <= i; j++)
               {
                  feedback.getSequence().add(result.getSequence().get(j));
               }
               feedbackPublisher.publishFeedback(feedback);
            }

            try
            {
               Thread.sleep(100);
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
               return;
            }
         }

         System.out.println("Fibonacci action complete");
      };

      ROS2ActionServer<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> actionServer =
         node.createActionServer("/test/fibonacci", Fibonacci_Goal.class, Fibonacci_Result.class, Fibonacci_Feedback.class, callback);

      System.out.println("Action server ready, waiting for goals...");
      Thread.sleep(5000);

      node.close();
      System.out.println("Java Action Server Test Complete");
   }
}
