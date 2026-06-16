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
 * Callback invoked when a remote publisher is matched to or removed from a subscription.
 */
@FunctionalInterface
public interface ROS2SubscriptionMatchedCallback
{
   /**
    * @param subscription the subscription that was matched or unmatched
    * @param publicationGuid GUID of the remote publisher; owned by the subscription and refreshed on each call — copy with {@link Guid#set(Guid)} if you need to store it
    * @param matched {@code true} when a publisher was matched, {@code false} when one was removed
    */
   void onSubscriptionMatched(ROS2Subscription<?> subscription, Guid publicationGuid, boolean matched);
}
