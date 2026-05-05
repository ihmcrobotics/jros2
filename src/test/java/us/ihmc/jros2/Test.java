package us.ihmc.jros2;

import std_msgs.String_;

public class Test
{
   public static void main(String[] args) throws InterruptedException
   {
      ROS2Node node = new ROS2Node("test_node", 200);

      Thread.sleep(1000000000);

      node.close();
   }
}
