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

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmEnvironment;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import us.ihmc.jros2.parser.field.InterfaceFieldParsingException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gradle task that generates Java message classes (including their RIHS01 type hashes) from ROS 2 packages.
 *
 * @see jros2InterfaceGenerator
 */
public class jros2GenTask extends DefaultTask
{
   private List<String> packagePaths;
   private List<String> dependencyPaths;
   private String outputDir;
   private Map<String, String> typeToClass;
   private boolean cleanOutputDir;
   private GeneratedCodeFormat format = GeneratedCodeFormat.IHMC;
   private String googleJavaFormatVersion = GoogleJavaFormatRunner.DEFAULT_VERSION;
   // Resolved only for the GOOGLE format, so that builds with the default format do not download or depend on the formatter
   private final Provider<String> googleJavaFormatClasspath;

   public jros2GenTask()
   {
      typeToClass = new HashMap<>();
      dependencyPaths = new ArrayList<>();
      getOutputs().cacheIf(task -> false);

      Project project = getProject();
      googleJavaFormatClasspath = project.getProviders().provider(() ->
      {
         if (format != GeneratedCodeFormat.GOOGLE)
         {
            return "";
         }
         String module = GoogleJavaFormatRunner.MODULE + ":" + googleJavaFormatVersion;
         Configuration configuration = project.getConfigurations().detachedConfiguration(project.getDependencies().create(module));
         // Without these Gradle cannot choose between the JVM and Android variants of the formatter's Guava dependency
         configuration.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
         configuration.getAttributes().attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                                                 project.getObjects().named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM));
         return configuration.getAsPath();
      });
   }

   @Input
   public List<String> getPackagePaths()
   {
      return packagePaths;
   }

   @Option(option = "packagePaths", description = "ROS 2 package directories (containing package.xml and msg/) to generate classes for")
   public void setPackagePaths(List<String> packagePaths)
   {
      this.packagePaths = packagePaths;
   }

   @Input
   @Optional
   public List<String> getDependencyPaths()
   {
      return dependencyPaths;
   }

   @Option(option = "dependencyPaths",
           description = "ROS 2 package directories, or directories of packages (e.g. /opt/ros/jazzy/share), used only to resolve the types referenced by "
                         + "the generated messages when computing type hashes. The interfaces bundled with jros2 are always available.")
   public void setDependencyPaths(List<String> dependencyPaths)
   {
      this.dependencyPaths = dependencyPaths;
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

   /**
    * @param typeToClass map of pkg/Type to a hand-written Java class; such types are not generated. Set in the build script:
    *                    a Map cannot be a Gradle command line option, and declaring one breaks any command line with an
    *                    argument after this task.
    */
   public void setTypeToClass(Map<String, String> typeToClass)
   {
      this.typeToClass = typeToClass;
   }

   @Input
   public boolean isCleanOutputDir()
   {
      return cleanOutputDir;
   }

   @Option(option = "cleanOutputDir", description = "Delete the entire output directory before generation. Use only when outputDir contains generated files exclusively.")
   public void setCleanOutputDir(boolean cleanOutputDir)
   {
      this.cleanOutputDir = cleanOutputDir;
   }

   @Input
   public String getFormat()
   {
      return format.name();
   }

   @Option(option = "format",
           description = "Source code format of the generated classes: IHMC (default) or GOOGLE (Google Java Style, applied by google-java-format, "
                         + "which is resolved from the repositories of the project)")
   public void setFormat(String format)
   {
      this.format = GeneratedCodeFormat.parse(format);
   }

   @Input
   public String getGoogleJavaFormatVersion()
   {
      return googleJavaFormatVersion;
   }

   @Option(option = "googleJavaFormatVersion", description = "Version of google-java-format for the GOOGLE format. It has to run on the JVM of this build")
   public void setGoogleJavaFormatVersion(String googleJavaFormatVersion)
   {
      this.googleJavaFormatVersion = googleJavaFormatVersion;
   }

   @TaskAction
   public void run() throws IOException
   {
      jros2InterfaceGenerator generator = new jros2InterfaceGenerator();
      for (String packagePath : packagePaths)
      {
         generator.addPackagePath(Path.of(packagePath));
      }
      if (dependencyPaths != null)
      {
         for (String dependencyPath : dependencyPaths)
         {
            generator.addDependencyPath(Path.of(dependencyPath));
         }
      }
      generator.setOutputDir(Path.of(outputDir));
      generator.setTypeToClass(typeToClass);
      generator.setCleanOutputDir(cleanOutputDir);
      generator.setFormat(format);
      generator.setGoogleJavaFormatClasspath(googleJavaFormatClasspath.get());

      try
      {
         generator.generate();
      }
      catch (InterfaceFieldParsingException e)
      {
         throw new RuntimeException(e.getMessage(), e);
      }
   }
}
