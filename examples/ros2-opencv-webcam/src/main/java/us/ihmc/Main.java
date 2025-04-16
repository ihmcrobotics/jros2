package us.ihmc;

import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber.Exception;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import java.nio.Buffer;

public class Main
{
   public static void main(String[] args) throws Exception
   {
      // Initialize the frame grabber for the default webcam (index 0)
      OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(4);
      grabber.start();

      // Create a canvas to display the video
      CanvasFrame canvas = new CanvasFrame("Webcam");
      canvas.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
      // Set the canvas size based on the webcam resolution
      canvas.setCanvasSize(grabber.getImageWidth(), grabber.getImageHeight());

      // Continuously grab frames and display
      while (true)
      {
         Frame frame = grabber.grab();
         if (frame == null)
         {
            break;
         }

         Buffer imageData = frame.image[0];

         // TODO:

         // Show the frame in the canvas
         canvas.showImage(frame);
      }

      // Release resources
      grabber.stop();
      canvas.dispose();
   }
}
