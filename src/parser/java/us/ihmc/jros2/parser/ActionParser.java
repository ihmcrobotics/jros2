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

/**
 * Parser for ROS 2 action (.action) definition files.
 * Action files are divided into three sections separated by '---':
 * 1. Goal definition
 * 2. Result definition
 * 3. Feedback definition
 */
public final class ActionParser
{
   public static ActionContext parseAction(String schema, String packageResourceName) throws InterfaceFieldParsingException
   {
      // Split the schema into goal, result, and feedback sections
      String[] sections = schema.split("---", 3);

      if (sections.length != 3)
      {
         throw new InterfaceFieldParsingException("action_definition", schema,
            new RuntimeException("Invalid action definition: must contain exactly two '---' separators (goal, result, feedback)"));
      }

      String goalSchema = sections[0].trim();
      String resultSchema = sections[1].trim();
      String feedbackSchema = sections[2].trim();

      // Parse goal, result, and feedback as messages
      MsgContext goalContext = MsgParser.parseMsg(goalSchema, packageResourceName + "_Goal");
      MsgContext resultContext = MsgParser.parseMsg(resultSchema, packageResourceName + "_Result");
      MsgContext feedbackContext = MsgParser.parseMsg(feedbackSchema, packageResourceName + "_Feedback");

      // Create the action context
      ActionContext actionContext = new ActionContext(schema, goalContext, resultContext, feedbackContext);
      actionContext.setPackageResourceName(packageResourceName);

      return actionContext;
   }
}
