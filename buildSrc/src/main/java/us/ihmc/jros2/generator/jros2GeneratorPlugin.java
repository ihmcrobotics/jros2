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
package us.ihmc.jros2.generator;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class jros2GeneratorPlugin implements Plugin<Project>
{
   private static final String TASK_GROUP_NAME = "\0jros2";

   @Override
   public void apply(Project plugin)
   {
      plugin.getTasks().register("jros2ls", task ->
      {
         task.setGroup(TASK_GROUP_NAME);
         task.setDescription("desc");
      });
   }
}
