package us.ihmc.jros2.generator.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import us.ihmc.jros2.generator.ROS2MessageGenerator;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class jros2GenTask extends DefaultTask
{
   private List<String> packagePaths;
   private String outputDir;
   private Map<String, String> typeToClass;

   @Input
   public List<String> getPackagePaths()
   {
      return packagePaths;
   }

   @Option(option = "packagePaths", description = "TODO")
   public void setPackagePaths(List<String> packagePaths)
   {
      this.packagePaths = packagePaths;
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
      Path outputDir = Path.of(this.outputDir);

      for (String packagePathStr : packagePaths)
      {
         Path packagePath = Path.of(packagePathStr);

         ROS2MessageGenerator generator = new ROS2MessageGenerator(packagePath, outputDir, packagePaths);

         generator.generate();
      }
   }
}
