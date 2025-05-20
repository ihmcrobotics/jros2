/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapperType;

/**
 * An internal class for maintaining memory relating to Fast-DDS topic types.
 */
class TopicData
{
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
