package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerScope;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.pointers.fastddsjava;

import java.io.Closeable;

public class ROS2Node implements Closeable
{
   static
   {
      fastddsjavaNativeLibrary.load();
   }

   private PointerScope pointerScope;
   private Pointer participant;

   public ROS2Node()
   {
      pointerScope = new PointerScope();

      participant = fastddsjava.fastddsjava_create_participant("");
   }

   public ROS2Publisher createPublisher(Class<?> topicType, String topicName, ROS2QoSProfile qosProfile)
   {
      return new ROS2Publisher(topicType, topicName, qosProfile);
   }

   public ROS2Subscriber createSubscription(Class<?> topicType, String topicName, ROS2SubscriberCallback callback, ROS2QoSProfile qosProfile)
   {
      // TODO:
      return null;
   }

   public ROS2Service createService(Class<?> serviceType, String serviceName, Object callback)
   {
      // TODO:
      return null;
   }

   public void declareParameter(String name, Object value)
   {
      // TODO:
   }

   public Object getParameter(String name)
   {
      // TODO:
      return null;
   }

   @Override
   public void close()
   {

      pointerScope.close();
   }
}
