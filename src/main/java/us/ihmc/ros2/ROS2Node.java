package us.ihmc.ros2;

import org.bytedeco.javacpp.Pointer;
import us.ihmc.fastddsjava.fastddsjavaException;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.pointers.fastddsjava;
import us.ihmc.fastddsjava.profiles.ProfilesXML;

public class ROS2Node
{
   static
   {
      fastddsjavaNativeLibrary.load();
   }

   private Pointer participant;

   public ROS2Node()
   {
      ProfilesXML profilesXML = new ProfilesXML();

      try
      {
         profilesXML.load();
      }
      catch (fastddsjavaException e)
      {
         e.printStackTrace();
      }



      fastddsjava.fastddsjava_create_participant("");
   }


}
