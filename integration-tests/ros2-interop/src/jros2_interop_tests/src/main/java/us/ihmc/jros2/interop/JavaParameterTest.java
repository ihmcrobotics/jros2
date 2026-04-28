package us.ihmc.jros2.interop;

import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2Parameter;

public class JavaParameterTest
{
   public static void main(java.lang.String[] args) throws InterruptedException
   {
      int domainId = Integer.parseInt(System.getenv().getOrDefault("ROS_DOMAIN_ID", "200"));
      System.out.println("Starting Java Parameter Test (Domain: " + domainId + ")");

      ROS2Node node = new ROS2Node("java_parameter_test", domainId);

      // Declare various parameter types
      node.declareParameter("bool_param", true);
      node.declareParameter("int_param", 42L);
      node.declareParameter("double_param", 3.14);
      node.declareParameter("string_param", "hello from java");

      System.out.println("Declared parameters:");
      System.out.println("  bool_param: " + node.getParameter("bool_param").asBool());
      System.out.println("  int_param: " + node.getParameter("int_param").asLong());
      System.out.println("  double_param: " + node.getParameter("double_param").asDouble());
      System.out.println("  string_param: " + node.getParameter("string_param").asString());

      // Test modifying a parameter
      System.out.println("\nTesting parameter modification:");
      ROS2Parameter newParam = new ROS2Parameter("string_param", "modified value");
      boolean setResult = node.setParameter(newParam);
      System.out.println("setParameter returned: " + setResult);

      String modifiedValue = node.getParameter("string_param").asString();
      System.out.println("After modification: " + modifiedValue);

      if ("modified value".equals(modifiedValue))
      {
         System.out.println("SUCCESS: ROS2 parameter get/set works correctly");
      }
      else
      {
         System.out.println("FAILED: Parameter was not modified correctly");
      }

      System.out.println("\nParameter services are now available.");
      System.out.println("Waiting for Python to test interoperability (15 seconds)...");
      Thread.sleep(15000);

      // Check if Python modified the parameter
      String finalValue = node.getParameter("string_param").asString();
      System.out.println("\nFinal parameter value: " + finalValue);

      node.close();
      System.out.println("Java Parameter Test Complete");
   }
}
