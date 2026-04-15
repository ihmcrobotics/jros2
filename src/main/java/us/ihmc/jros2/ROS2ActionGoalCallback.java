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

/**
 * Callback interface for ROS 2 action servers to handle goal execution.
 * Implement this interface to define action server behavior.
 *
 * @param <Goal>     The action goal message type
 * @param <Result>   The action result message type
 * @param <Feedback> The action feedback message type
 */
@FunctionalInterface
public interface ROS2ActionGoalCallback<Goal extends ROS2Message<Goal>, Result extends ROS2Message<Result>, Feedback extends ROS2Message<Feedback>>
{
   /**
    * Execute the action goal and generate a result.
    * Implementations can publish feedback during execution using the feedbackPublisher.
    *
    * @param goal              The goal to execute
    * @param result            The result object to be filled and returned to the client
    * @param feedbackPublisher Callback to publish periodic feedback during execution
    */
   void execute(Goal goal, Result result, FeedbackPublisher<Feedback> feedbackPublisher);

   /**
    * Interface for publishing feedback during action execution.
    *
    * @param <Feedback> The feedback message type
    */
   interface FeedbackPublisher<Feedback extends ROS2Message<Feedback>>
   {
      /**
       * Publish feedback to the action client.
       *
       * @param feedback The feedback message to send
       */
      void publishFeedback(Feedback feedback);

      /**
       * Create a new feedback message instance.
       *
       * @return A new feedback message instance
       */
      Feedback createFeedback();
   }
}
