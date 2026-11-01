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

import us.ihmc.jros2.parser.field.InterfaceFieldParsingException;

import java.util.List;

public final class ActionParser
{
   /**
    * Parse a ROS 2 action (.action) schema. The goal, result and feedback sections are separated by lines containing only
    * {@code ---}. Any section may be empty.
    *
    * @param schema              the contents of the .action file
    * @param packageResourceName e.g. {@code example_interfaces/Fibonacci.action}
    * @return the parsed action, with the three sections and the derived service and feedback messages
    * @throws IllegalArgumentException      if the schema does not contain exactly two separators
    * @throws InterfaceFieldParsingException if a field cannot be parsed
    */
   public static ActionContext parseAction(String schema, String packageResourceName) throws InterfaceFieldParsingException
   {
      List<String> sections = SrvParser.splitSections(schema, 3, packageResourceName);

      ActionSectionContext goal = parseSection(sections.get(0), packageResourceName, "_Goal");
      ActionSectionContext result = parseSection(sections.get(1), packageResourceName, "_Result");
      ActionSectionContext feedback = parseSection(sections.get(2), packageResourceName, "_Feedback");

      String actionName = goal.getActionName();
      ActionSectionContext sendGoalRequest = parseSection("unique_identifier_msgs/UUID goal_id\n" + actionName + "_Goal goal\n",
                                                          packageResourceName,
                                                          "_SendGoal_Request");
      ActionSectionContext sendGoalResponse = parseSection("bool accepted\nbuiltin_interfaces/Time stamp\n", packageResourceName, "_SendGoal_Response");
      ActionSectionContext getResultRequest = parseSection("unique_identifier_msgs/UUID goal_id\n", packageResourceName, "_GetResult_Request");
      ActionSectionContext getResultResponse = parseSection("int8 status\n" + actionName + "_Result result\n", packageResourceName, "_GetResult_Response");
      ActionSectionContext feedbackMessage = parseSection("unique_identifier_msgs/UUID goal_id\n" + actionName + "_Feedback feedback\n",
                                                          packageResourceName,
                                                          "_FeedbackMessage");

      return new ActionContext(schema, goal, result, feedback, sendGoalRequest, sendGoalResponse, getResultRequest, getResultResponse, feedbackMessage);
   }

   private static ActionSectionContext parseSection(String section, String packageResourceName, String suffix) throws InterfaceFieldParsingException
   {
      ActionSectionContext context = new ActionSectionContext(section, packageResourceName, suffix);
      MsgParser.parseFields(context, section);
      return context;
   }
}
