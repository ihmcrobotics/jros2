package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapperType;

class ROS2TopicData
{
   static
   {
      fastddsjavaNativeLibrary.load();
   }

   protected fastddsjava_TopicDataWrapperType topicDataWrapperType;
   protected Pointer fastddsTypeSupport;
   protected Pointer fastddsTopic;

   protected ROS2TopicData(fastddsjava_TopicDataWrapperType topicDataWrapperType, Pointer fastddsTypeSupport, Pointer fastddsTopic)
   {
      this.topicDataWrapperType = topicDataWrapperType;
      this.fastddsTypeSupport = fastddsTypeSupport;
      this.fastddsTopic = fastddsTopic;
   }
}
