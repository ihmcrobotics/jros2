package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapperType;

/**
 * An internal class for maintaining memory relating to Fast-DDS topic types.
 */
class TopicData
{
   static
   {
      jros2.load();
   }

   final fastddsjava_TopicDataWrapperType topicDataWrapperType;
   final Pointer fastddsTypeSupport;
   final Pointer fastddsTopic;

   TopicData(fastddsjava_TopicDataWrapperType topicDataWrapperType, Pointer fastddsTypeSupport, Pointer fastddsTopic)
   {
      this.topicDataWrapperType = topicDataWrapperType;
      this.fastddsTypeSupport = fastddsTypeSupport;
      this.fastddsTopic = fastddsTopic;
   }
}
