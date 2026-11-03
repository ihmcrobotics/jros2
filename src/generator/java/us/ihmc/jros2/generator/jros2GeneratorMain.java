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

import java.nio.file.Path;

/**
 * Command line entry point of the jros2 message generator, used by the {@code jros2GenerateDefaultInterfacesLocal}
 * Gradle task to run the generator from source.
 * <pre>
 * jros2GeneratorMain --output-dir DIR [--package PACKAGE_DIR]... [--dependency DIR]... [--clean]
 *                    [--no-bundled-definitions] [--bundled-definitions-output FILE]
 * </pre>
 */
public final class jros2GeneratorMain
{
   private jros2GeneratorMain()
   {
   }

   public static void main(String[] args) throws Exception
   {
      jros2InterfaceGenerator generator = new jros2InterfaceGenerator();
      boolean hasOutputDir = false;

      for (int i = 0; i < args.length; i++)
      {
         switch (args[i])
         {
            case "--output-dir" ->
            {
               generator.setOutputDir(Path.of(requireValue(args, ++i)));
               hasOutputDir = true;
            }
            case "--package" -> generator.addPackagePath(Path.of(requireValue(args, ++i)));
            case "--dependency" -> generator.addDependencyPath(Path.of(requireValue(args, ++i)));
            case "--clean" -> generator.setCleanOutputDir(true);
            case "--no-bundled-definitions" -> generator.setUseBundledDefinitions(false);
            case "--bundled-definitions-output" -> generator.setBundledDefinitionsOutput(Path.of(requireValue(args, ++i)));
            default -> throw new IllegalArgumentException("Unknown argument: " + args[i] + "\n" + usage());
         }
      }

      if (!hasOutputDir)
      {
         throw new IllegalArgumentException("--output-dir is required\n" + usage());
      }

      generator.generate();
   }

   private static String requireValue(String[] args, int index)
   {
      if (index >= args.length)
      {
         throw new IllegalArgumentException("Missing value for " + args[index - 1] + "\n" + usage());
      }
      return args[index];
   }

   private static String usage()
   {
      return "Usage: jros2GeneratorMain --output-dir DIR [--package PACKAGE_DIR]... [--dependency DIR]... [--clean] [--no-bundled-definitions] "
             + "[--bundled-definitions-output FILE]";
   }
}
