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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Runs google-java-format on generated files, in a child JVM. The formatter uses the internal javac API, which a JVM only
 * exposes when started with the {@code --add-exports} options below; the JVM that runs a Gradle task is not.
 */
final class GoogleJavaFormatRunner
{
   /** Group and artifact of google-java-format. */
   static final String MODULE = "com.google.googlejavaformat:google-java-format";
   /**
    * The default version, which runs on Java 17, 21 and 25. The class file version does not show this: 1.30 to 1.35 are Java 17
    * class files but fail on Java 17 with a NoClassDefFoundError for a javac class, and 1.36 and later need Java 21.
    */
   static final String DEFAULT_VERSION = "1.28.0";

   private static final String MAIN_CLASS = "com.google.googlejavaformat.java.Main";
   private static final String[] JAVAC_PACKAGES = {"api", "code", "file", "parser", "tree", "util"};

   private GoogleJavaFormatRunner()
   {
   }

   /**
    * Formats {@code files} in place.
    *
    * @param files     the files to format
    * @param classpath the classpath with google-java-format and its dependencies
    * @throws IOException if the formatter could not be started or reported a failure
    */
   static void format(Collection<Path> files, String classpath) throws IOException
   {
      if (files.isEmpty())
      {
         return;
      }

      // A Java launcher argument file, because a large package set exceeds the command line limit of some platforms. The
      // formatter's own @file is no use here: it splits at whitespace and skips files it does not recognize with exit value 0.
      Path argumentFile = Files.createTempFile("jros2-google-java-format", ".args");
      try
      {
         List<String> arguments = new ArrayList<>();
         for (String javacPackage : JAVAC_PACKAGES)
         {
            arguments.add("--add-exports=jdk.compiler/com.sun.tools.javac." + javacPackage + "=ALL-UNNAMED");
         }
         arguments.add("-cp");
         arguments.add(classpath);
         arguments.add(MAIN_CLASS);
         arguments.add("--replace");
         for (Path file : files)
         {
            arguments.add(file.toString());
         }
         StringBuilder argumentFileContent = new StringBuilder();
         for (String argument : arguments)
         {
            argumentFileContent.append('"').append(argument.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"\n");
         }
         Files.writeString(argumentFile, argumentFileContent.toString(), StandardCharsets.UTF_8);

         String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
         Process process = new ProcessBuilder(java, "@" + argumentFile).redirectErrorStream(true).start();
         String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
         int exitValue = process.waitFor();
         if (exitValue != 0 || output.contains("Skipping"))
         {
            throw new IOException("google-java-format failed with exit value " + exitValue + ":\n" + output);
         }
      }
      catch (InterruptedException e)
      {
         Thread.currentThread().interrupt();
         throw new IOException("Interrupted while running google-java-format", e);
      }
      finally
      {
         Files.deleteIfExists(argumentFile);
      }
   }
}
