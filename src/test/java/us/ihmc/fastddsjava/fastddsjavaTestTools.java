package us.ihmc.fastddsjava;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

public final class fastddsjavaTestTools
{
   private static final Random RANDOM = new Random(1881108);

   /** Domain used when ROS_DOMAIN_ID is not set (the historical value of the fastddsjava tests). */
   public static final int DEFAULT_TEST_DOMAIN_ID = 100;

   private fastddsjavaTestTools()
   {
   }

   public static byte[] generateRandomBytes(int length)
   {
      byte[] randomBytes = new byte[length];
      synchronized (RANDOM)
      {
         RANDOM.nextBytes(randomBytes);
      }
      return randomBytes;
   }

   /**
    * @return ROS_DOMAIN_ID if it is set to a valid domain (0 to 232), otherwise {@link #DEFAULT_TEST_DOMAIN_ID}. Lets
    *       concurrent test runs on one machine stay on separate domains.
    */
   public static int testDomainId()
   {
      String value = System.getenv("ROS_DOMAIN_ID");
      if (value != null)
      {
         try
         {
            int domainId = Integer.parseInt(value.trim());
            if (domainId >= 0 && domainId <= 232)
               return domainId;
         }
         catch (NumberFormatException ignored)
         {
            // Fall through to the default.
         }
      }
      return DEFAULT_TEST_DOMAIN_ID;
   }

   /**
    * Polls {@code condition} until it is true or {@code timeoutMillis} elapsed.
    *
    * @return the final value of the condition
    */
   public static boolean waitFor(BooleanSupplier condition, long timeoutMillis) throws InterruptedException
   {
      long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
      while (!condition.getAsBoolean())
      {
         if (System.nanoTime() > deadline)
            return condition.getAsBoolean();
         Thread.sleep(2);
      }
      return true;
   }

   /**
    * @return the {@code -javaagent} option of the JaCoCo agent this JVM runs with, or an empty list outside a coverage run.
    *       Tests that start child JVMs pass it on, so that the code a child runs is part of the coverage report: the agent
    *       of every JVM appends to the same execution data file (it locks the file while writing). The option may use a
    *       path relative to the working directory, so the child must run in the working directory of this JVM.
    */
   public static List<String> coverageAgentArguments()
   {
      List<String> arguments = new ArrayList<>();
      for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments())
      {
         if (argument.startsWith("-javaagent:") && argument.contains("jacocoagent"))
            arguments.add(argument);
      }
      return arguments;
   }
}
