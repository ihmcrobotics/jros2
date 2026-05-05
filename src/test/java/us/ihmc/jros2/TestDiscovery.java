package us.ihmc.jros2;

import rmw_dds_common.ParticipantEntitiesInfo;

public class TestDiscovery
{
   public static void main(String[] args) throws InterruptedException
   {
      System.out.println("Creating test node...");
      ROS2Node node = new ROS2Node("test_discovery_node");
      System.out.println("Node created: " + node.getName());

      // Subscribe to discovery info to see what's being published
      ROS2QoSProfile discoveryQos = new ROS2QoSProfile();
      discoveryQos.reliability(ROS2QoSProfile.Reliability.RELIABLE);
      discoveryQos.durability(ROS2QoSProfile.Durability.TRANSIENT_LOCAL);
      discoveryQos.history(ROS2QoSProfile.History.KEEP_LAST);
      discoveryQos.depth(1);

      ROS2Subscription<ParticipantEntitiesInfo> discoverySubscription = node.createSubscription(
            new ROS2Topic<>("ros_discovery_info", ParticipantEntitiesInfo.class),
            reader -> {
               ParticipantEntitiesInfo info = new ParticipantEntitiesInfo();
               reader.read(info);
               System.out.println("Received discovery info!");
               System.out.println("  Nodes count: " + info.getNodeEntitiesInfoSeq().size());
               if (info.getNodeEntitiesInfoSeq().size() > 0) {
                  System.out.println("  First node: " + info.getNodeEntitiesInfoSeq().get(0).getNodeNameAsString());
               }
            },
            discoveryQos
      );

      System.out.println("Waiting for discovery messages...");
      Thread.sleep(10000); // Wait 10 seconds

      node.close();
      System.out.println("Test complete");
   }
}
