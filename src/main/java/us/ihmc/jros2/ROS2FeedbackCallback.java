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
 * Receives the feedback of a goal sent by a {@link ROS2ActionClient}. Called on a Fast-DDS listener thread.
 */
@FunctionalInterface
public interface ROS2FeedbackCallback<G extends ROS2Message<G>, R extends ROS2Message<R>, F extends ROS2Message<F>>
{
   void onFeedback(ROS2ClientGoalHandle<G, R, F> goalHandle, F feedback);
}
