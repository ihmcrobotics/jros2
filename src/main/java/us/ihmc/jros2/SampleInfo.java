/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
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

import us.ihmc.fastddsjava.pointers.fastddsjava_SampleInfo;

/**
 * Metadata for a sample received by a {@link ROS2Subscription}.
 * Reuses a single native allocation for garbage-free reads.
 */
public class SampleInfo implements AutoCloseable
{
   final fastddsjava_SampleInfo nativeInfo;
   private boolean closed;

   public SampleInfo()
   {
      nativeInfo = new fastddsjava_SampleInfo();
   }

   public boolean hasValidData()
   {
      return nativeInfo.valid_data();
   }

   public long getSourceTimestampNanos()
   {
      return nativeInfo.source_timestamp().to_ns();
   }

   public long getReceptionTimestampNanos()
   {
      return nativeInfo.reception_timestamp().to_ns();
   }

   public short getSampleState()
   {
      return nativeInfo.sample_state();
   }

   public short getViewState()
   {
      return nativeInfo.view_state();
   }

   public short getInstanceState()
   {
      return nativeInfo.instance_state();
   }

   @Override
   public void close()
   {
      if (!closed)
      {
         nativeInfo.close();
         closed = true;
      }
   }
}
