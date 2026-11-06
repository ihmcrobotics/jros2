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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The layout of generated classes: no blank line before a closing brace or after another blank line, in the default IHMC
 * format and with {@link GeneratedCodeFormat#GOOGLE}, and classes that compile and stay marked as generated.
 */
public class GeneratedCodeFormatTest
{
   private static final String MSG = """
                                     # A comment


                                     # after two blank lines
                                     std_msgs/Header header
                                     uint8 CONSTANT=1
                                     string name "default"
                                     float64[] values
                                     float64[3] fixed
                                     """;

   private static Path writePackage(Path directory) throws IOException
   {
      Path packagePath = Files.createDirectories(directory.resolve("format_pkg"));
      Files.writeString(packagePath.resolve("package.xml"), "<package format=\"3\"><name>format_pkg</name></package>\n");
      Path msg = Files.createDirectories(packagePath.resolve("msg"));
      Files.writeString(msg.resolve("Full.msg"), MSG);
      Files.writeString(msg.resolve("Empty.msg"), "");
      return packagePath;
   }

   private static jros2InterfaceGenerator generator(Path packagePath, Path output)
   {
      return new jros2InterfaceGenerator().addPackagePath(packagePath).setOutputDir(output);
   }

   private static void assertCompiles(Path source)
   {
      ByteArrayOutputStream errors = new ByteArrayOutputStream();
      Path classes = source.resolveSibling("classes");
      int result = ToolProvider.getSystemJavaCompiler().run(null, null, errors, "-cp", System.getProperty("java.class.path"), "-d", classes.toString(), source.toString());
      assertEquals(0, result, errors::toString);
   }

   @Test
   public void testRemoveExtraBlankLines()
   {
      assertEquals("void a()\n{\n   b();\n}\n", ROS2MessageGenerator.removeExtraBlankLines("void a()\n{\n   b();\n\n}\n"));
      assertEquals("a();\n\nb();\n", ROS2MessageGenerator.removeExtraBlankLines("a();\n\n   \n\nb();\n"));
      assertEquals("a();\n\nb();\n", ROS2MessageGenerator.removeExtraBlankLines("a();\n\nb();\n"));
      // A closing brace with something after it is not the end of a block
      assertEquals("a\n\n}</pre>\n", ROS2MessageGenerator.removeExtraBlankLines("a\n\n}</pre>\n"));
      // Line separators of the platform that generated the source stay
      assertEquals("{\r\n}\r\n", ROS2MessageGenerator.removeExtraBlankLines("{\r\n\r\n}\r\n"));
      // Comments quote the .msg definition
      String comment = "/**\n * a\n\n\n * b\n\n}\n*/\n";
      assertEquals(comment, ROS2MessageGenerator.removeExtraBlankLines(comment));
      assertEquals("/* a */\nb\n}\n", ROS2MessageGenerator.removeExtraBlankLines("/* a */\nb\n\n}\n"));
   }

   private static void assertNoExtraBlankLines(String source)
   {
      String code = source.replaceAll("(?s)/\\*.*?\\*/", "");
      assertFalse(code.matches("(?s).*\\n[ \\t]*\\n[ \\t]*\\}[ \\t]*\\n.*"), () -> "blank line before a closing brace:\n" + source);
      assertFalse(source.replaceAll("(?s)/\\*.*?\\*/", "/**/").matches("(?s).*\\n[ \\t]*\\n[ \\t]*\\n.*"), () -> "consecutive blank lines:\n" + source);
   }

   @Test
   public void testDefaultFormatIsIhmcWithoutExtraBlankLines(@TempDir Path directory) throws Exception
   {
      Path output = directory.resolve("out");
      assertEquals(2, generator(writePackage(directory), output).generate());
      for (String name : new String[] {"Full", "Empty"})
      {
         Path file = output.resolve("format_pkg/" + name + ".java");
         String source = Files.readString(file);
         assertNoExtraBlankLines(source);
         assertTrue(source.contains("public " + name + "()\n   {\n"), source);
         assertCompiles(file);
      }
      // The comment in the class Javadoc still shows the .msg definition with its blank lines
      assertTrue(Files.readString(output.resolve("format_pkg/Full.java")).contains("# A comment\n\n\n# after two blank lines"));
   }

   @Test
   public void testGoogleFormat(@TempDir Path directory) throws Exception
   {
      // A space in the path has to survive the formatter's argument file
      Path output = directory.resolve("out dir");
      Path packagePath = writePackage(directory);
      assertEquals(2, generator(packagePath, output).setFormat(GeneratedCodeFormat.GOOGLE).generate());

      Path file = output.resolve("format_pkg/Full.java");
      String source = Files.readString(file);
      assertNoExtraBlankLines(source);
      assertTrue(source.contains("public Full() {\n    "), source);
      assertTrue(source.contains("public class Full implements ROS2Message<Full>"), source);
      assertFalse(source.contains("\n   public"), source);
      assertCompiles(file);

      // Still recognized as a generated file, so that a later run can remove it
      assertTrue(source.contains("automatically generated by jros2"), source);
      Path orphan = output.resolve("format_pkg/Orphan.java");
      Files.writeString(orphan, source);
      generator(packagePath, output).setFormat(GeneratedCodeFormat.GOOGLE).generate();
      assertFalse(Files.exists(orphan));

      // Generating twice gives the same files
      assertEquals(source, Files.readString(file));
   }

   @Test
   public void testGoogleFormatAndIhmcFormatHaveTheSameCode(@TempDir Path directory) throws Exception
   {
      Path packagePath = writePackage(directory);
      Path ihmc = directory.resolve("ihmc");
      Path google = directory.resolve("google");
      generator(packagePath, ihmc).generate();
      generator(packagePath, google).setFormat(GeneratedCodeFormat.GOOGLE).generate();

      try (Stream<Path> files = Files.list(ihmc.resolve("format_pkg")))
      {
         for (Path file : (Iterable<Path>) files::iterator)
         {
            assertEquals(tokens(Files.readString(file)), tokens(Files.readString(google.resolve("format_pkg").resolve(file.getFileName()))), file.toString());
         }
      }
   }

   /** The source without comments and any whitespace, so that only a change of the code itself shows. */
   private static String tokens(String source)
   {
      return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)^\\s*//.*$", "").replaceAll("\\s+", "");
   }

   @Test
   public void testGoogleFormatWithoutTheFormatterFails(@TempDir Path directory) throws Exception
   {
      jros2InterfaceGenerator generator = generator(writePackage(directory), directory.resolve("out")).setFormat(GeneratedCodeFormat.GOOGLE)
                                                                                                       .setGoogleJavaFormatClasspath(directory.toString());
      IOException exception = assertThrows(IOException.class, generator::generate);
      assertTrue(exception.getMessage().contains("google-java-format failed"), exception::getMessage);
   }

   @Test
   public void testParse()
   {
      assertEquals(GeneratedCodeFormat.GOOGLE, GeneratedCodeFormat.parse("google"));
      assertEquals(GeneratedCodeFormat.IHMC, GeneratedCodeFormat.parse(" IHMC "));
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> GeneratedCodeFormat.parse("tabs"));
      assertTrue(exception.getMessage().contains("GOOGLE"), exception::getMessage);
      assertThrows(IllegalArgumentException.class, () -> GeneratedCodeFormat.parse(null));
   }
}
