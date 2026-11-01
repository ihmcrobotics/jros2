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
package us.ihmc.jros2.parser;

import java.util.List;

/**
 * Representation of a ROS 2 action (.action): the goal, result and feedback messages defined in the file, plus the messages
 * ROS 2 derives from them (see rosidl_parser's {@code Action} definition):
 * <ul>
 *   <li>{@code <Action>_SendGoal_Request}: {@code unique_identifier_msgs/UUID goal_id}, {@code <Action>_Goal goal}</li>
 *   <li>{@code <Action>_SendGoal_Response}: {@code bool accepted}, {@code builtin_interfaces/Time stamp}</li>
 *   <li>{@code <Action>_GetResult_Request}: {@code unique_identifier_msgs/UUID goal_id}</li>
 *   <li>{@code <Action>_GetResult_Response}: {@code int8 status}, {@code <Action>_Result result}</li>
 *   <li>{@code <Action>_FeedbackMessage}: {@code unique_identifier_msgs/UUID goal_id}, {@code <Action>_Feedback feedback}</li>
 * </ul>
 */
public class ActionContext
{
   private final String schema;
   private final ActionSectionContext goalContext;
   private final ActionSectionContext resultContext;
   private final ActionSectionContext feedbackContext;
   private final ActionSectionContext sendGoalRequestContext;
   private final ActionSectionContext sendGoalResponseContext;
   private final ActionSectionContext getResultRequestContext;
   private final ActionSectionContext getResultResponseContext;
   private final ActionSectionContext feedbackMessageContext;

   protected ActionContext(String schema,
                           ActionSectionContext goalContext,
                           ActionSectionContext resultContext,
                           ActionSectionContext feedbackContext,
                           ActionSectionContext sendGoalRequestContext,
                           ActionSectionContext sendGoalResponseContext,
                           ActionSectionContext getResultRequestContext,
                           ActionSectionContext getResultResponseContext,
                           ActionSectionContext feedbackMessageContext)
   {
      this.schema = schema;
      this.goalContext = goalContext;
      this.resultContext = resultContext;
      this.feedbackContext = feedbackContext;
      this.sendGoalRequestContext = sendGoalRequestContext;
      this.sendGoalResponseContext = sendGoalResponseContext;
      this.getResultRequestContext = getResultRequestContext;
      this.getResultResponseContext = getResultResponseContext;
      this.feedbackMessageContext = feedbackMessageContext;
   }

   public String getSchema()
   {
      return schema;
   }

   public ActionSectionContext getGoalContext()
   {
      return goalContext;
   }

   public ActionSectionContext getResultContext()
   {
      return resultContext;
   }

   public ActionSectionContext getFeedbackContext()
   {
      return feedbackContext;
   }

   public ActionSectionContext getSendGoalRequestContext()
   {
      return sendGoalRequestContext;
   }

   public ActionSectionContext getSendGoalResponseContext()
   {
      return sendGoalResponseContext;
   }

   public ActionSectionContext getGetResultRequestContext()
   {
      return getResultRequestContext;
   }

   public ActionSectionContext getGetResultResponseContext()
   {
      return getResultResponseContext;
   }

   public ActionSectionContext getFeedbackMessageContext()
   {
      return feedbackMessageContext;
   }

   /**
    * @return the eight messages to generate for this action
    */
   public List<ActionSectionContext> getAllContexts()
   {
      return List.of(goalContext,
                     resultContext,
                     feedbackContext,
                     sendGoalRequestContext,
                     sendGoalResponseContext,
                     getResultRequestContext,
                     getResultResponseContext,
                     feedbackMessageContext);
   }

   public String getPackageName()
   {
      return goalContext.getPackageName();
   }

   public String getActionName()
   {
      return goalContext.getActionName();
   }
}
