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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Inputs of {@link jros2InterfaceGenerator} that are wrong or incomplete: generation must go on where it can (a package
 * without package.xml is skipped, a dependency path that is not a directory is ignored, a referenced type whose definition
 * cannot be parsed or found leaves the referencing class without a type hash) and fail clearly where it cannot.
 */
public class GeneratorInputEdgeCaseTest
{
   @Test
   public void testOutputDirectoryIsRequired()
   {
      IllegalStateException exception = assertThrows(IllegalStateException.class, () -> new jros2InterfaceGenerator().generate());
      assertTrue(exception.getMessage().contains("output directory"), exception.getMessage());
   }

   @Test
   public void testPackageWithoutPackageXmlIsSkipped(@TempDir Path directory) throws Exception
   {
      Path noManifest = Files.createDirectories(directory.resolve("no_manifest_pkg").resolve("msg"));
      Files.writeString(noManifest.resolve("Skipped.msg"), "int32 x\n");
      GeneratorGoldenTest.writePackage(directory.resolve("ok_pkg"), Map.of("Kept.msg", "int32 x\n"));

      int generated = new jros2InterfaceGenerator().addPackagePath(directory.resolve("no_manifest_pkg"))
                                                   .addPackagePath(directory.resolve("ok_pkg"))
                                                   .setOutputDir(directory.resolve("out"))
                                                   .generate();
      assertEquals(1, generated);
      assertTrue(Files.exists(directory.resolve("out/ok_pkg/Kept.java")));
      assertFalse(Files.exists(directory.resolve("out/no_manifest_pkg")));
   }

   @Test
   public void testUnusableDependenciesLeaveTheHashUnset(@TempDir Path directory) throws Exception
   {
      // dep_pkg/Broken cannot be parsed, a dependency path that is a file is ignored
      GeneratorGoldenTest.writePackage(directory.resolve("deps").resolve("dep_pkg"), Map.of("Broken.msg", "int32 x\nint32 x\n", "Fine.msg", "int32 y\n"));
      Path notADirectory = Files.writeString(directory.resolve("not_a_directory.txt"), "x");
      GeneratorGoldenTest.writePackage(directory.resolve("user_pkg"), Map.of("UsesBroken.msg", "dep_pkg/Broken broken\n", "UsesFine.msg", "dep_pkg/Fine fine\n"));

      int generated = new jros2InterfaceGenerator().addPackagePath(directory.resolve("user_pkg"))
                                                   .addDependencyPath(notADirectory)
                                                   .addDependencyPath(directory.resolve("deps"))
                                                   .setOutputDir(directory.resolve("out"))
                                                   .generate();
      assertEquals(2, generated, "classes are generated even when a hash cannot be computed");
      assertTrue(Files.readString(directory.resolve("out/user_pkg/UsesBroken.java")).contains("typeHash = null;"));
      assertTrue(Files.readString(directory.resolve("out/user_pkg/UsesFine.java")).contains("typeHash = \"RIHS01_"));
   }

   @Test
   public void testBundledDefinitionsCanBeTurnedOff(@TempDir Path directory) throws Exception
   {
      GeneratorGoldenTest.writePackage(directory.resolve("user_pkg"), Map.of("Stamped.msg", "std_msgs/Header header\n"));

      new jros2InterfaceGenerator().addPackagePath(directory.resolve("user_pkg")).setOutputDir(directory.resolve("with")).generate();
      String withBundled = Files.readString(directory.resolve("with/user_pkg/Stamped.java"));
      assertTrue(withBundled.contains("typeHash = \"RIHS01_"), "std_msgs/Header resolves from the bundled definitions");

      new jros2InterfaceGenerator().addPackagePath(directory.resolve("user_pkg"))
                                   .setUseBundledDefinitions(false)
                                   .setTypeToClass(null)
                                   .setOutputDir(directory.resolve("without"))
                                   .generate();
      assertTrue(Files.readString(directory.resolve("without/user_pkg/Stamped.java")).contains("typeHash = null;"),
                 "without the bundled definitions std_msgs/Header is unresolved");
   }

   @Test
   public void testBundledDefinitionsResourceIsComplete()
   {
      Map<String, String> bundled = jros2InterfaceGenerator.loadBundledDefinitions();
      assertTrue(bundled.containsKey("std_msgs/msg/Header"), bundled.keySet().toString());
      assertTrue(bundled.containsKey("geometry_msgs/msg/PoseStamped"));
      assertEquals("builtin_interfaces/Time stamp\nstring frame_id\n", bundled.get("std_msgs/msg/Header"));
      assertTrue(bundled.size() >= 179, "every bundled message type: " + bundled.size());
   }
}
