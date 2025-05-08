package us.ihmc.jros2.generator;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class jros2GeneratorPlugin implements Plugin<Project>
{
   @Override
   public void apply(Project target)
   {
      System.out.println("Running plugin");
   }
}
