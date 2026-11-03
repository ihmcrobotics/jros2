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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The texts that {@link HotPathText} builds for the hot-path classes are the messages those classes used to build
 * themselves (moving them out of the classes must not change what users see in exceptions and logs).
 */
public class HotPathTextTest
{
   @Test
   public void testSharedAndPublisherTexts()
   {
      assertEquals("capacity must be at least 1: 0", HotPathText.capacityTooSmall(0));
      assertEquals("queueCapacity must be at least 1: -1", HotPathText.queueCapacityTooSmall(-1));
      assertEquals("SampleInfo has been closed", HotPathText.sampleInfoClosed());
      assertEquals("Cannot create a publisher for topic '/t': the Fast-DDS topic does not exist", HotPathText.publisherTopicMissing("/t"));
      assertEquals("Failed to allocate the native write buffer for topic '/t'", HotPathText.writeBufferAllocationFailed("/t"));
      assertEquals("Failed to create a Fast-DDS DataWriterListener for topic '/t'", HotPathText.dataWriterListenerCreationFailed("/t"));
      assertEquals("Fast-DDS failed to create a Publisher for topic '/t' with profile 'p'", HotPathText.publisherCreationFailed("/t", "p"));
      assertEquals("Fast-DDS failed to create a DataWriter for topic '/t' (type a.B) with profile 'p'. Check the QoS settings.",
                   HotPathText.dataWriterCreationFailed("/t", "a.B", "p"));
      assertEquals("Failed to delete a partially created Fast-DDS Publisher (retcode 4)", HotPathText.partialPublisherDeletionFailed(4));
      assertEquals("Failed to attach the publication matched listener on topic '/t' (retcode 1)", HotPathText.publicationListenerAttachFailed("/t", 1));
      assertEquals("payload must be a direct ByteBuffer", HotPathText.payloadNotDirect());
      assertEquals("Failed to get the message header. Not recording message age statistics from now on.", HotPathText.headerLookupFailed());
      assertEquals("Fast-DDS failed to delete the DataWriter of topic '/t' (retcode 1); keeping its listener alive", HotPathText.dataWriterDeletionFailed("/t", 1));
      assertEquals("Fast-DDS failed to delete the Publisher of topic '/t' (retcode 1)", HotPathText.publisherDeletionFailed("/t", 1));
   }

   @Test
   public void testAsyncTexts()
   {
      assertEquals("Failed to publish a queued message on topic '/t'; dropped it", HotPathText.queuedPublishFailed("/t"));
      assertEquals("AsyncROS2NodePublishThread-n", HotPathText.publishThreadName("n"));
      assertEquals("The publish ThreadFactory of AsyncROS2Node 'n' returned null", HotPathText.publishThreadFactoryReturnedNull("n"));
      assertEquals("Publish thread x did not stop within 3 s; closing the node anyway", HotPathText.publishThreadDidNotStop("x"));
      assertEquals("A task on x failed", HotPathText.publishThreadTaskFailed("x"));
      assertEquals("Publishing on x failed", HotPathText.publishThreadPublishingFailed("x"));
   }

   @Test
   public void testSubscriptionTexts()
   {
      assertEquals("Cannot create a subscription for topic '/t': the Fast-DDS topic does not exist", HotPathText.subscriptionTopicMissing("/t"));
      assertEquals("Failed to create a Fast-DDS DataReaderListener for topic '/t'", HotPathText.dataReaderListenerCreationFailed("/t"));
      assertEquals("Fast-DDS failed to create a Subscriber for topic '/t' with profile 'p'", HotPathText.subscriberCreationFailed("/t", "p"));
      assertEquals("Fast-DDS failed to create a DataReader for topic '/t' (type a.B) with profile 'p'. Check the QoS settings.",
                   HotPathText.dataReaderCreationFailed("/t", "a.B", "p"));
      assertEquals("Failed to delete a partially created Fast-DDS Subscriber (retcode 2)", HotPathText.partialSubscriberDeletionFailed(2));
      assertEquals("Failed to attach the listener of the subscription on topic '/t' (retcode 2)", HotPathText.subscriptionListenerAttachFailed("/t", 2));
      assertEquals("Failed to allocate a native sample buffer for topic '/t'", HotPathText.sampleBufferAllocationFailed("/t"));
      assertEquals("The callback executor of the subscription on topic '/t' rejected a task; samples stay queued until the next notification",
                   HotPathText.callbackExecutorRejected("/t"));
      assertEquals("Dropped a malformed Int64 sample (12 bytes) on topic '/t'", HotPathText.malformedSample("Int64", 12, "/t"));
      assertEquals("Still waiting for a callback of the subscription on topic '/t' to return before closing it", HotPathText.stillWaitingForCallback("/t"));
      assertEquals("Fast-DDS failed to delete the DataReader of topic '/t' (retcode 1); keeping its listener alive", HotPathText.dataReaderDeletionFailed("/t", 1));
      assertEquals("Fast-DDS failed to delete the Subscriber of topic '/t' (retcode 1)", HotPathText.subscriberDeletionFailed("/t", 1));
      assertEquals("Still waiting for an in-flight native call to finish before releasing an endpoint", HotPathText.stillWaitingForNativeCall());
   }

   @Test
   public void testExecutorLockAndValueTexts()
   {
      assertEquals("task", HotPathText.executorTaskParameter());
      assertEquals("threadCount must be at least 1: 0", HotPathText.executorThreadCountTooSmall(0));
      assertEquals("e-2", HotPathText.executorWorkerName("e", 2));
      assertEquals("The ThreadFactory of ROS2CallbackExecutor 'e' returned null", HotPathText.executorThreadFactoryReturnedNull("e"));
      assertEquals("ROS2CallbackExecutor 'e' is closed", HotPathText.executorClosed("e"));
      assertEquals("The queue of ROS2CallbackExecutor 'e' is full (8 tasks)", HotPathText.executorFull("e", 8));
      assertEquals("A task of ROS2CallbackExecutor 'e' failed", HotPathText.executorTaskFailed("e"));
      assertEquals("A worker of ROS2CallbackExecutor 'e' did not end within 5 s", HotPathText.executorWorkerDidNotEnd("e"));
      assertEquals("The calling thread does not hold this lock", HotPathText.lockNotHeld());
      assertEquals("Failed to allocate a native Fast-DDS SampleInfo", HotPathText.sampleInfoAllocationFailed());
      assertEquals("GUID must be exactly 16 bytes, got 3", HotPathText.guidLengthInvalid(3));
      byte[] guid = new byte[16];
      guid[0] = (byte) 0xab;
      guid[15] = 0x01;
      assertEquals("ab0000000000000000000000.00000001", HotPathText.guidToString(guid));
      assertEquals("jros2-deferred-teardown", HotPathText.deferredThreadName());
      assertEquals("Deferred jros2 teardown task failed", HotPathText.deferredTaskFailed());
      assertEquals(String.format("%s: {AVERAGE=%f, MINIMUM=%f, MAXIMUM=%f, STDDEV=%f, SAMPLE_COUNT=%f, TOTAL=%f, LATEST=%f}", "S", 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0),
                   HotPathText.statisticsCalculatorToString("S", 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0));
      assertEquals("Statistics: {AVERAGE=NaN, MINIMUM=NaN, MAXIMUM=NaN, STDDEV=NaN, SAMPLE_COUNT=0.0, TOTAL=0.0, LATEST=NaN}",
                   HotPathText.statisticsToString(new Statistics()));
   }
}
