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

/**
 * One of the messages of a ROS 2 action (.action): the goal, result and feedback sections, and the messages ROS 2 derives
 * from them for the action's services and feedback topic. Its Java class name and DDS type name follow the rosidl
 * conventions, e.g. {@code Fibonacci_SendGoal_Request} and {@code pkg::action::dds_::Fibonacci_SendGoal_Request_}.
 */
public class ActionSectionContext extends MsgContext
{
   private final String actionName;
   private final String suffix;

   /**
    * @param suffix appended to the action name, e.g. {@code _Goal} or {@code _SendGoal_Request}
    */
   protected ActionSectionContext(String schema, String packageResourceName, String suffix)
   {
      super(schema);
      this.suffix = suffix;
      setPackageResourceName(packageResourceName);
      actionName = getResourceName().split("\\.")[0];
      setJavaClassName(actionName + suffix);
   }

   public String getActionName()
   {
      return actionName;
   }

   public String getSuffix()
   {
      return suffix;
   }

   @Override
   public String getDDSName()
   {
      String actionNameSanitized = actionName.replace("_", "");
      return getPackageName() + "::action::dds_::" + actionNameSanitized + suffix + "_";
   }
}
