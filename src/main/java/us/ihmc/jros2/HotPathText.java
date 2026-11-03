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

/**
 * The texts (exception and log messages, {@code toString()} output) of the classes on the publish and receive paths.
 * <p>
 * Those classes must not contain string literals, string concatenation or format strings: when HotSpot first queues a
 * method of a class for C2 compilation, it creates every string literal of that class that has not been used yet (error
 * messages, the recipes of string concatenations) on the thread that triggered the compilation. For a class on the hot
 * path that thread is a publishing, polling or callback thread, and the compilation can happen at any time, even long
 * after start-up for a method that runs rarely. Keeping the literals here, in a class that only error and diagnostic
 * paths use, keeps that allocation off the hot path. {@code HotPathClassesTest} checks the classes.
 */
final class HotPathText
{
   private HotPathText()
   {
   }

   /* Shared */

   static String capacityTooSmall(int capacity)
   {
      return "capacity must be at least 1: " + capacity;
   }

   static String queueCapacityTooSmall(int queueCapacity)
   {
      return "queueCapacity must be at least 1: " + queueCapacity;
   }

   static String sampleInfoClosed()
   {
      return "SampleInfo has been closed";
   }

   /* ROS2Publisher */

   static String publisherTopicMissing(String topicName)
   {
      return "Cannot create a publisher for topic '" + topicName + "': the Fast-DDS topic does not exist";
   }

   static String writeBufferAllocationFailed(String topicName)
   {
      return "Failed to allocate the native write buffer for topic '" + topicName + "'";
   }

   static String dataWriterListenerCreationFailed(String topicName)
   {
      return "Failed to create a Fast-DDS DataWriterListener for topic '" + topicName + "'";
   }

   static String publisherCreationFailed(String topicName, String profileName)
   {
      return "Fast-DDS failed to create a Publisher for topic '" + topicName + "' with profile '" + profileName + "'";
   }

   static String dataWriterCreationFailed(String topicName, String typeName, String profileName)
   {
      return "Fast-DDS failed to create a DataWriter for topic '" + topicName + "' (type " + typeName + ") with profile '" + profileName
             + "'. Check the QoS settings.";
   }

   static String partialPublisherDeletionFailed(int retcode)
   {
      return "Failed to delete a partially created Fast-DDS Publisher (retcode " + retcode + ")";
   }

   static String publicationListenerAttachFailed(String topicName, int retcode)
   {
      return "Failed to attach the publication matched listener on topic '" + topicName + "' (retcode " + retcode + ")";
   }

   static String payloadNotDirect()
   {
      return "payload must be a direct ByteBuffer";
   }

   static String headerLookupFailed()
   {
      return "Failed to get the message header. Not recording message age statistics from now on.";
   }

   static String dataWriterDeletionFailed(String topicName, int retcode)
   {
      return "Fast-DDS failed to delete the DataWriter of topic '" + topicName + "' (retcode " + retcode + "); keeping its listener alive";
   }

   static String publisherDeletionFailed(String topicName, int retcode)
   {
      return "Fast-DDS failed to delete the Publisher of topic '" + topicName + "' (retcode " + retcode + ")";
   }

   /* AsyncROS2Publisher and AsyncROS2Node */

   static String queuedPublishFailed(String topicName)
   {
      return "Failed to publish a queued message on topic '" + topicName + "'; dropped it";
   }

   static String publishThreadName(String nodeName)
   {
      return "AsyncROS2NodePublishThread-" + nodeName;
   }

   static String publishThreadFactoryReturnedNull(String nodeName)
   {
      return "The publish ThreadFactory of AsyncROS2Node '" + nodeName + "' returned null";
   }

   static String publishThreadDidNotStop(String threadName)
   {
      return "Publish thread " + threadName + " did not stop within 3 s; closing the node anyway";
   }

   static String publishThreadTaskFailed(String threadName)
   {
      return "A task on " + threadName + " failed";
   }

   static String publishThreadPublishingFailed(String threadName)
   {
      return "Publishing on " + threadName + " failed";
   }

   /* ROS2Subscription */

   static String subscriptionTopicMissing(String topicName)
   {
      return "Cannot create a subscription for topic '" + topicName + "': the Fast-DDS topic does not exist";
   }

   static String dataReaderListenerCreationFailed(String topicName)
   {
      return "Failed to create a Fast-DDS DataReaderListener for topic '" + topicName + "'";
   }

   static String subscriberCreationFailed(String topicName, String profileName)
   {
      return "Fast-DDS failed to create a Subscriber for topic '" + topicName + "' with profile '" + profileName + "'";
   }

   static String dataReaderCreationFailed(String topicName, String typeName, String profileName)
   {
      return "Fast-DDS failed to create a DataReader for topic '" + topicName + "' (type " + typeName + ") with profile '" + profileName
             + "'. Check the QoS settings.";
   }

   static String partialSubscriberDeletionFailed(int retcode)
   {
      return "Failed to delete a partially created Fast-DDS Subscriber (retcode " + retcode + ")";
   }

   static String subscriptionListenerAttachFailed(String topicName, int retcode)
   {
      return "Failed to attach the listener of the subscription on topic '" + topicName + "' (retcode " + retcode + ")";
   }

   static String sampleBufferAllocationFailed(String topicName)
   {
      return "Failed to allocate a native sample buffer for topic '" + topicName + "'";
   }

   static String callbackExecutorRejected(String topicName)
   {
      return "The callback executor of the subscription on topic '" + topicName + "' rejected a task; samples stay queued until the next notification";
   }

   static String malformedSample(String typeName, int sizeBytes, String topicName)
   {
      return "Dropped a malformed " + typeName + " sample (" + sizeBytes + " bytes) on topic '" + topicName + "'";
   }

   static String stillWaitingForCallback(String topicName)
   {
      return "Still waiting for a callback of the subscription on topic '" + topicName + "' to return before closing it";
   }

   static String dataReaderDeletionFailed(String topicName, int retcode)
   {
      return "Fast-DDS failed to delete the DataReader of topic '" + topicName + "' (retcode " + retcode + "); keeping its listener alive";
   }

   static String subscriberDeletionFailed(String topicName, int retcode)
   {
      return "Fast-DDS failed to delete the Subscriber of topic '" + topicName + "' (retcode " + retcode + ")";
   }

   /* EndpointSupport */

   static String stillWaitingForNativeCall()
   {
      return "Still waiting for an in-flight native call to finish before releasing an endpoint";
   }

   /* ROS2CallbackExecutor */

   static String executorTaskParameter()
   {
      return "task";
   }

   static String executorThreadCountTooSmall(int threadCount)
   {
      return "threadCount must be at least 1: " + threadCount;
   }

   static String executorWorkerName(String name, int index)
   {
      return name + "-" + index;
   }

   static String executorThreadFactoryReturnedNull(String name)
   {
      return "The ThreadFactory of ROS2CallbackExecutor '" + name + "' returned null";
   }

   static String executorClosed(String name)
   {
      return "ROS2CallbackExecutor '" + name + "' is closed";
   }

   static String executorFull(String name, int capacity)
   {
      return "The queue of ROS2CallbackExecutor '" + name + "' is full (" + capacity + " tasks)";
   }

   static String executorTaskFailed(String name)
   {
      return "A task of ROS2CallbackExecutor '" + name + "' failed";
   }

   static String executorWorkerDidNotEnd(String name)
   {
      return "A worker of ROS2CallbackExecutor '" + name + "' did not end within 5 s";
   }

   /* AllocationFreeLock */

   static String lockNotHeld()
   {
      return "The calling thread does not hold this lock";
   }

   /* SampleInfo */

   static String sampleInfoAllocationFailed()
   {
      return "Failed to allocate a native Fast-DDS SampleInfo";
   }

   /* Guid */

   static String guidLengthInvalid(int length)
   {
      return "GUID must be exactly 16 bytes, got " + length;
   }

   static String guidToString(byte[] guid)
   {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < guid.length; i++)
      {
         if (i == 12)
         {
            sb.append('.');
         }
         sb.append(String.format("%02x", guid[i]));
      }
      return sb.toString();
   }

   /* Statistics and StatisticsCalculator */

   static String statisticsToString(Statistics statistics)
   {
      StringBuilder builder = new StringBuilder(statistics.getClass().getSimpleName());
      builder.append(": {");

      for (int i = 0; i < Statistics.StatisticDataType.values.length; ++i)
      {
         Statistics.StatisticDataType statisticType = Statistics.StatisticDataType.values[i];
         builder.append(statisticType.name()).append("=").append(statistics.get(statisticType)).append(", ");
      }
      int lastCommaIndex = builder.lastIndexOf(", ");
      builder.replace(lastCommaIndex, lastCommaIndex + 2, "}");

      return builder.toString();
   }

   static String statisticsCalculatorToString(String simpleName,
                                              double average,
                                              double min,
                                              double max,
                                              double standardDeviation,
                                              double sampleCount,
                                              double total,
                                              double latest)
   {
      // String.format rather than String.formatted: the latter needs Android API 34.
      return String.format("%s: {AVERAGE=%f, MINIMUM=%f, MAXIMUM=%f, STDDEV=%f, SAMPLE_COUNT=%f, TOTAL=%f, LATEST=%f}",
                           simpleName,
                           average,
                           min,
                           max,
                           standardDeviation,
                           sampleCount,
                           total,
                           latest);
   }

   /* CallbackContext */

   static String deferredThreadName()
   {
      return "jros2-deferred-teardown";
   }

   static String deferredTaskFailed()
   {
      return "Deferred jros2 teardown task failed";
   }

   /* ROS2Topic: the name operations (setup), here because ROS2Topic's getters run on the hot path */

   static String topicNameOrEmpty(String topicName)
   {
      return topicName == null ? "" : topicName;
   }

   static String topicNameAppended(String topicName, String token)
   {
      return topicName + "/" + token;
   }

   static String topicNamePrepended(String topicName, String token)
   {
      if (topicName.isEmpty() || topicName.startsWith("/"))
      {
         return "/" + token + topicName;
      }
      // Relative and private names stay relative
      return token + "/" + topicName;
   }

   static String topicNameFromTokens(String originalTopicName, String[] tokens)
   {
      String prefix = originalTopicName.startsWith("/") ? "/" : "";
      return prefix + String.join("/", tokens);
   }

   /**
    * @return the tokens of a name: the parts between forward slashes, ignoring a leading slash
    */
   static String[] topicNameTokens(String name)
   {
      String withoutLeadingSlash = name.startsWith("/") ? name.substring(1) : name;
      if (withoutLeadingSlash.isEmpty())
      {
         return new String[0];
      }
      return withoutLeadingSlash.split("/");
   }

   static String topicInsertPositionOutOfRange()
   {
      return "Position must be in the range [0, numberOfTokens]";
   }
}
