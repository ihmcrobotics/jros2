package us.ihmc;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import sensor_msgs.msg.dds.Image;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2Publisher;
import us.ihmc.jros2.ROS2Topic;

import java.nio.ByteBuffer;

public class WebcamPublisher
{
   public static void main(String[] args)
   {
      ROS2Node ros2Node = new ROS2Node("image_publisher_node", 0);
      ROS2Topic<Image> imageTopic = new ROS2Topic<>("/test/image", Image.class);
      ROS2Publisher<Image> imagePublisher = ros2Node.createPublisher(imageTopic);

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
               msg.getEncoding().append("bgr8");
               msg.setWidth(frame.imageWidth);
               msg.setHeight(frame.imageHeight);
               msg.getData().ensureMinCapacity(frame.imageChannels * (frame.imageWidth * frame.imageHeight));
            }
            else
            {
               msg.getData().getBuffer().rewind();
               msg.getData().getBuffer().put(frameBuffer);
            }

            imagePublisher.publish(msg);
            System.out.println("Published frame " + frameCount);

            frame.close();

            frameCount++;
         }
      }
      catch (Exception ignored)
      {
      }
   }
}
