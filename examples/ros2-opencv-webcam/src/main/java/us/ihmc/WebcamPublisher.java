package us.ihmc;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import sensor_msgs.Image;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2Publisher;
import us.ihmc.jros2.ROS2QoSProfile;
import us.ihmc.jros2.ROS2Topic;

import java.nio.ByteBuffer;

public class WebcamPublisher
{
   public static void main(String[] args)
   {
      ROS2Node ros2Node = new ROS2Node("image_publisher_node", 0);
      Runtime.getRuntime().addShutdownHook(new Thread(ros2Node::close, "WebcamShutdown"));
      ROS2Topic<Image> imageTopic = new ROS2Topic<>("/test/image", Image.class);
      // Camera streams use the ROS 2 sensor data profile (BEST_EFFORT, VOLATILE, KEEP_LAST 5), like rclcpp::SensorDataQoS().
      // A subscriber with the default profile (RELIABLE) does not match it; use SENSOR_DATA (or BEST_EFFORT) there too,
      // e.g. "ros2 topic echo --qos-profile sensor_data /test/image".
      ROS2Publisher<Image> imagePublisher = ros2Node.createPublisher(imageTopic, ROS2QoSProfile.SENSOR_DATA);

      Image msg = new Image();
      int frameCount = 0;
      OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(0);

      try
      {
         grabber.start();

         while (true)
         {
            Frame frame = grabber.grab();
            ByteBuffer frameBuffer = (ByteBuffer) frame.image[0];

            if (frameCount == 0)
            {
               msg.setEncoding("bgr8");
               msg.getHeader().setFrameId("webcam");
               msg.setWidth(frame.imageWidth);
               msg.setHeight(frame.imageHeight);
               msg.setStep(frame.imageChannels * frame.imageWidth);
               msg.getData().ensureMinCapacity(frame.imageChannels * (frame.imageWidth * frame.imageHeight));
            }

            long now = System.currentTimeMillis();
            msg.getHeader().getStamp().setSec((int) (now / 1000));
            msg.getHeader().getStamp().setNanosec((int) ((now % 1000) * 1_000_000));

            // The sequence size is the buffer position, so rewind and copy the whole frame every time.
            msg.getData().getBuffer().rewind();
            msg.getData().getBuffer().put(frameBuffer);

            imagePublisher.publish(msg);
            System.out.println("Published frame " + frameCount);

            frame.close();

            frameCount++;
         }
      }
      catch (Exception e)
      {
         e.printStackTrace();
      }
   }
}
