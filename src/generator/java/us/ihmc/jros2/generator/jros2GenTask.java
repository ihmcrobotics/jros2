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
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import us.ihmc.jros2.parser.MsgContext;
import us.ihmc.jros2.parser.MsgParser;
import us.ihmc.jros2.parser.field.InterfaceFieldParsingException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class jros2GenTask extends DefaultTask
{
   private List<String> packagePaths;
   private String outputDir;
   private Map<String, String> typeToClass;
   private boolean cleanOutputDir;

   public jros2GenTask()
   {
      typeToClass = new HashMap<>();
      getOutputs().cacheIf(task -> false);
   }

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

   @TaskAction
   public void run() throws IOException
   {
      long startTime = System.currentTimeMillis();
      int totalInterfacesGenerated = 0;

      Path outputDirPath = Path.of(outputDir).toAbsolutePath().normalize();
      Set<Path> generatedPaths = new HashSet<>();

      if (cleanOutputDir)
      {
         GeneratedMessageFileCleanup.cleanOutputDirectory(outputDirPath);
      }

      for (String packagePathStr : packagePaths)
      {
         Path packagePath = Path.of(packagePathStr);
         Path packageXmlPath = packagePath.resolve("package.xml");
         Path msgDirPath = packagePath.resolve("msg");

         if (!Files.exists(packageXmlPath))
         {
            System.err.println("No package.xml found in package path: " + packagePathStr);
            continue;
         }

         if (Files.exists(msgDirPath) && Files.isDirectory(msgDirPath))
         {
            DirectoryStream.Filter<Path> msgFileFilter = path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".msg");

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(msgDirPath, msgFileFilter))
            {
               for (Path msgFile : stream)
               {
                  try
                  {
                     String packageResourceName = packagePath.getFileName().toString() + "/" + msgFile.getFileName().toString().replace(".msg", "");
                     if (typeToClass.containsKey(packageResourceName))
                     {
                        // Do not generate files for package-resources defined in typeToClass map
                        System.out.println("Not generating for " + packageResourceName + ". It is mapped to: " + typeToClass.get(packageResourceName));
                        continue;
                     }
                     String msgFileContent = Files.readString(msgFile).replaceAll("\\*+/", ""); // Replace any comment enders in the file
                     MsgContext context = MsgParser.parseMsg(msgFileContent, packageResourceName);
                     String classContent = ROS2MessageGenerator.generateJavaClassContents(context, typeToClass);
                     Path outputFilePath = outputDirPath.resolve(context.getJavaPackageName().replace(".", "/") + "/" + context.getJavaClassName() + ".java");
                     Files.createDirectories(outputFilePath.getParent());
                     Files.writeString(outputFilePath, classContent, StandardCharsets.UTF_8);
                     generatedPaths.add(outputFilePath.toAbsolutePath().normalize());
                     System.out.printf("%-40s | %s%n", packageResourceName, outputFilePath.toAbsolutePath());
                     totalInterfacesGenerated++;
                  }
                  catch (InterfaceFieldParsingException e)
                  {
                     System.err.println(e.getMessage());
                     throw new RuntimeException(e);
                  }
               }
            }
         }
      }

      int removedOrphans = GeneratedMessageFileCleanup.removeOrphanedGeneratedFiles(outputDirPath, generatedPaths);

      long endTime = System.currentTimeMillis();
      double runTimeSeconds = (endTime - startTime) / 1000.0;

      System.out.println();
      System.out.println(totalInterfacesGenerated + " interfaces generated in " + runTimeSeconds + "s");
      if (removedOrphans > 0)
         System.out.println(removedOrphans + " orphaned generated files removed");
   }
}
