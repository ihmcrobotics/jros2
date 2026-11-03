/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
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

import us.ihmc.jros2.parser.MsgContext;
import us.ihmc.jros2.parser.MsgParser;
import us.ihmc.jros2.parser.field.InterfaceFieldParsingException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Generates Java message classes from ROS 2 packages, independent of Gradle (used by {@link jros2GenTask} and
 * {@link jros2GeneratorMain}).
 * <p>
 * Generation runs in two passes. The first pass parses every .msg file of every package. The second pass computes the
 * RIHS01 type hash of each message ({@link TypeDescriptionHasher}) and renders the classes. Referenced types are
 * resolved, in this order, from the generated packages, from the dependency packages, and from the definitions of the
 * interfaces bundled with jros2 (a generator resource), so user messages that reference e.g. {@code std_msgs/Header} get
 * a correct hash without the std_msgs sources.
 * </p>
 */
public final class jros2InterfaceGenerator
{
   /** Generator resource holding the (comment-free) .msg definitions of the interfaces bundled with jros2. */
   public static final String BUNDLED_DEFINITIONS_RESOURCE = "jros2_bundled_interfaces.txt";
   static final String DEFINITION_SEPARATOR = "@@@ ";

   private final List<Path> packagePaths = new ArrayList<>();
   private final List<Path> dependencyPaths = new ArrayList<>();
   private Path outputDir;
   private Map<String, String> typeToClass = new HashMap<>();
   private boolean cleanOutputDir;
   private boolean useBundledDefinitions = true;
   private Path bundledDefinitionsOutput;

   /** Adds a ROS 2 package directory (containing package.xml and msg/) to generate classes for. */
   public jros2InterfaceGenerator addPackagePath(Path packagePath)
   {
      packagePaths.add(packagePath);
      return this;
   }

   /**
    * Adds a directory used only to resolve referenced types: either a ROS 2 package directory (containing msg/) or a
    * directory of packages (such as {@code /opt/ros/jazzy/share}).
    */
   public jros2InterfaceGenerator addDependencyPath(Path dependencyPath)
   {
      dependencyPaths.add(dependencyPath);
      return this;
   }

   public jros2InterfaceGenerator setOutputDir(Path outputDir)
   {
      this.outputDir = outputDir;
      return this;
   }

   /**
    * @param typeToClass map of {@code pkg/Type} to a hand-written Java class; such types are not generated and fields of
    *                    that type use the given class
    */
   public jros2InterfaceGenerator setTypeToClass(Map<String, String> typeToClass)
   {
      this.typeToClass = typeToClass == null ? new HashMap<>() : typeToClass;
      return this;
   }

   /** Delete the entire output directory before generation. */
   public jros2InterfaceGenerator setCleanOutputDir(boolean cleanOutputDir)
   {
      this.cleanOutputDir = cleanOutputDir;
      return this;
   }

   /** Whether to fall back to the definitions bundled with jros2 to resolve referenced types (default true). */
   public jros2InterfaceGenerator setUseBundledDefinitions(boolean useBundledDefinitions)
   {
      this.useBundledDefinitions = useBundledDefinitions;
      return this;
   }

   /** If set, the definitions of all generated packages are also written to this file, in the bundled resource format. */
   public jros2InterfaceGenerator setBundledDefinitionsOutput(Path bundledDefinitionsOutput)
   {
      this.bundledDefinitionsOutput = bundledDefinitionsOutput;
      return this;
   }

   /**
    * Runs the generation.
    *
    * @return the number of generated classes
    */
   public int generate() throws IOException, InterfaceFieldParsingException
   {
      if (outputDir == null)
      {
         throw new IllegalStateException("No output directory set");
      }

      long startTime = System.currentTimeMillis();
      Path outputDirPath = outputDir.toAbsolutePath().normalize();

      // Pass 1: parse every message of every package
      Map<String, MsgContext> parsed = new LinkedHashMap<>();
      Map<String, String> normalizedDefinitions = new TreeMap<>();
      for (Path packagePath : packagePaths)
      {
         if (!Files.exists(packagePath.resolve("package.xml")))
         {
            System.err.println("No package.xml found in package path: " + packagePath);
            continue;
         }

         for (Path msgFile : listMsgFiles(packagePath))
         {
            String packageResourceName = packageResourceName(packagePath, msgFile);
            String msgFileContent = Files.readString(msgFile);
            MsgContext context;
            try
            {
               context = parseMsg(msgFileContent, packageResourceName);
            }
            catch (InterfaceFieldParsingException e)
            {
               System.err.println(msgFile + ": " + e.getMessage());
               throw e;
            }
            parsed.put(context.getRosTypeName(), context);
            normalizedDefinitions.put(context.getRosTypeName(), normalizeDefinition(msgFileContent));
         }
      }

      // Resolution order for referenced types: generated packages, dependency packages, bundled definitions
      Map<String, Path> dependencyFiles = indexDependencyPaths();
      Map<String, String> bundledDefinitions = useBundledDefinitions ? loadBundledDefinitions() : Collections.emptyMap();
      Map<String, MsgContext> resolvedDependencies = new HashMap<>();
      TypeDescriptionHasher hasher = new TypeDescriptionHasher(typeName ->
      {
         MsgContext context = parsed.get(typeName);
         if (context == null)
         {
            context = resolvedDependencies.computeIfAbsent(typeName, name -> parseDependency(name, dependencyFiles, bundledDefinitions));
         }
         return context;
      });

      if (cleanOutputDir)
      {
         GeneratedMessageFileCleanup.cleanOutputDirectory(outputDirPath);
      }

      // Pass 2: hash and generate
      int totalInterfacesGenerated = 0;
      Set<Path> generatedPaths = new HashSet<>();
      for (MsgContext context : parsed.values())
      {
         try
         {
            context.setTypeHash(hasher.hash(context));
         }
         catch (TypeDescriptionHasher.UnresolvedTypeException e)
         {
            System.err.println("WARNING: no type hash for " + context.getRosTypeName() + ": " + e.getMessage()
                               + ". Add the package that defines it to the packagePaths or dependencyPaths.");
            context.setTypeHash(null);
         }

         String packageResourceName = context.getPackageResourceName();
         if (typeToClass.containsKey(packageResourceName))
         {
            // Do not generate files for package-resources defined in typeToClass map
            System.out.println("Not generating for " + packageResourceName + ". It is mapped to: " + typeToClass.get(packageResourceName));
            continue;
         }

         String classContent = ROS2MessageGenerator.generateJavaClassContents(context, typeToClass);
         Path outputFilePath = outputDirPath.resolve(context.getJavaPackageName().replace(".", "/") + "/" + context.getJavaClassName() + ".java");
         Files.createDirectories(outputFilePath.getParent());
         Files.writeString(outputFilePath, classContent, StandardCharsets.UTF_8);
         generatedPaths.add(outputFilePath.toAbsolutePath().normalize());
         System.out.printf("%-40s | %s%n", packageResourceName, outputFilePath.toAbsolutePath());
         totalInterfacesGenerated++;
      }

      int removedOrphans = GeneratedMessageFileCleanup.removeOrphanedGeneratedFiles(outputDirPath, generatedPaths);

      if (bundledDefinitionsOutput != null)
      {
         writeBundledDefinitions(bundledDefinitionsOutput, normalizedDefinitions);
         System.out.println("Wrote " + normalizedDefinitions.size() + " definitions to " + bundledDefinitionsOutput.toAbsolutePath());
      }

      double runTimeSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
      System.out.println();
      System.out.println(totalInterfacesGenerated + " interfaces generated in " + runTimeSeconds + "s");
      if (removedOrphans > 0)
         System.out.println(removedOrphans + " orphaned generated files removed");

      return totalInterfacesGenerated;
   }

   /**
    * Parses a .msg file the way the generator does (Javadoc comment terminators are removed from the schema).
    */
   static MsgContext parseMsg(String msgFileContent, String packageResourceName) throws InterfaceFieldParsingException
   {
      return MsgParser.parseMsg(msgFileContent.replaceAll("\\*+/", ""), packageResourceName); // Replace any comment enders in the file
   }

   /**
    * Removes comments and blank lines: what remains is all that matters for the type hash.
    */
   static String normalizeDefinition(String msgFileContent)
   {
      StringBuilder normalized = new StringBuilder();
      for (String line : msgFileContent.split("\\R"))
      {
         int comment = line.indexOf('#');
         String content = (comment >= 0 ? line.substring(0, comment) : line).replace('\t', ' ').trim();
         if (!content.isEmpty())
         {
            normalized.append(content).append('\n');
         }
      }
      return normalized.toString();
   }

   /**
    * Reads the definitions bundled with jros2: a map of ROS type name ({@code pkg/msg/Type}) to normalized .msg text.
    */
   public static Map<String, String> loadBundledDefinitions()
   {
      try (InputStream stream = jros2InterfaceGenerator.class.getClassLoader().getResourceAsStream(BUNDLED_DEFINITIONS_RESOURCE))
      {
         if (stream == null)
         {
            return Collections.emptyMap();
         }
         return parseBundledDefinitions(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      }
      catch (IOException e)
      {
         throw new UncheckedIOException(e);
      }
   }

   static Map<String, String> parseBundledDefinitions(String content)
   {
      Map<String, String> definitions = new TreeMap<>();
      String currentType = null;
      StringBuilder currentDefinition = new StringBuilder();
      for (String line : content.split("\\R"))
      {
         if (line.startsWith(DEFINITION_SEPARATOR))
         {
            if (currentType != null)
               definitions.put(currentType, currentDefinition.toString());
            currentType = line.substring(DEFINITION_SEPARATOR.length()).trim();
            currentDefinition.setLength(0);
         }
         else if (currentType != null && !line.isEmpty())
         {
            currentDefinition.append(line).append('\n');
         }
      }
      if (currentType != null)
         definitions.put(currentType, currentDefinition.toString());
      return definitions;
   }

   static void writeBundledDefinitions(Path file, Map<String, String> definitions) throws IOException
   {
      StringBuilder content = new StringBuilder();
      content.append("# ROS 2 message definitions of the interfaces bundled with jros2, without comments.\n");
      content.append("# The jros2 generator uses them to compute the RIHS01 type hashes of messages that reference these types.\n");
      content.append("# Generated by the jros2GenerateDefaultInterfacesLocal Gradle task. Do not edit.\n");
      for (Map.Entry<String, String> entry : new TreeMap<>(definitions).entrySet())
      {
         content.append(DEFINITION_SEPARATOR).append(entry.getKey()).append('\n');
         content.append(entry.getValue());
      }
      Path parent = file.toAbsolutePath().getParent();
      if (parent != null)
         Files.createDirectories(parent);
      Files.writeString(file, content.toString(), StandardCharsets.UTF_8);
   }

   private Map<String, Path> indexDependencyPaths() throws IOException
   {
      Map<String, Path> files = new HashMap<>();
      for (Path dependencyPath : dependencyPaths)
      {
         List<Path> packages = new ArrayList<>();
         if (Files.isDirectory(dependencyPath.resolve("msg")))
         {
            packages.add(dependencyPath);
         }
         else if (Files.isDirectory(dependencyPath))
         {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dependencyPath, path -> Files.isDirectory(path.resolve("msg"))))
            {
               stream.forEach(packages::add);
            }
         }
         else
         {
            System.err.println("Dependency path is not a directory: " + dependencyPath);
         }

         for (Path packagePath : packages)
         {
            for (Path msgFile : listMsgFiles(packagePath))
            {
               String packageResourceName = packageResourceName(packagePath, msgFile);
               String[] packageAndType = packageResourceName.split("/");
               files.putIfAbsent(packageAndType[0] + "/msg/" + packageAndType[1], msgFile);
            }
         }
      }
      return files;
   }

   private static MsgContext parseDependency(String typeName, Map<String, Path> dependencyFiles, Map<String, String> bundledDefinitions)
   {
      String[] parts = typeName.split("/");
      if (parts.length != 3)
      {
         return null;
      }
      String packageResourceName = parts[0] + "/" + parts[2];

      try
      {
         Path file = dependencyFiles.get(typeName);
         if (file != null)
         {
            return parseMsg(Files.readString(file), packageResourceName);
         }

         String definition = bundledDefinitions.get(typeName);
         if (definition != null)
         {
            return parseMsg(definition, packageResourceName);
         }
      }
      catch (IOException e)
      {
         throw new UncheckedIOException(e);
      }
      catch (InterfaceFieldParsingException e)
      {
         throw new TypeDescriptionHasher.UnresolvedTypeException("Cannot parse the definition of " + typeName + ": " + e.getMessage());
      }

      return null;
   }

   private static List<Path> listMsgFiles(Path packagePath) throws IOException
   {
      Path msgDirPath = packagePath.resolve("msg");
      List<Path> msgFiles = new ArrayList<>();
      if (Files.isDirectory(msgDirPath))
      {
         DirectoryStream.Filter<Path> msgFileFilter = path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".msg");
         try (DirectoryStream<Path> stream = Files.newDirectoryStream(msgDirPath, msgFileFilter))
         {
            stream.forEach(msgFiles::add);
         }
      }
      msgFiles.sort(null);
      return msgFiles;
   }

   private static String packageResourceName(Path packagePath, Path msgFile)
   {
      String fileName = msgFile.getFileName().toString();
      return packagePath.toAbsolutePath().normalize().getFileName().toString() + "/" + fileName.substring(0, fileName.length() - ".msg".length());
   }
}
