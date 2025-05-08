package us.ihmc.jros2.generator.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import us.ihmc.jros2.generator.ROS2MessageGenerator;

import java.nio.file.Path;
import java.util.Map;

public class jros2GenTask extends DefaultTask
{
   private String packagePath;
   private String outputDir;
   private Map<String, String> typeToClass;

   @Input
   public String getPackagePath()
   {
      return packagePath;
   }

   @Option(option = "packagePath", description = "TODO")
   public void setPackagePath(String packagePath)
   {
      this.packagePath = packagePath;
   }

   @Input
   public String getOutputDir()
   {
      return outputDir;
   }

   @Option(option = "outputDir", description = "Directory to copy generated interface files")
   public void setOutputDir(String outputDir)
   {
      this.outputDir = outputDir;
   }

   @Input
   @Optional
   public Map<String, String> getTypeToClass()
   {
      return typeToClass;
   }

   @Option(option = "typeToClass", description = "TODO")
   public void setTypeToClass(Map<String, String> typeToClass)
   {
      this.typeToClass = typeToClass;
   }

   @TaskAction
   public void run()
   {
      Path packagePath = Path.of(this.packagePath);
      Path outputDir = Path.of(this.outputDir);

      ROS2MessageGenerator generator = new ROS2MessageGenerator(packagePath, outputDir);

      generator.generate();
   }
}
