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

      plugin.getTasks().register("jros2gen", task ->
      {
         task.setGroup(TASK_GROUP_NAME);
         task.setDescription("desc");
      });
   }
}
