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
package us.ihmc.jros2.allocation.messages;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.jros2.ROS2Message;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Compiles Java sources written by a test (generated messages, call drivers) against the jros2 classes and loads them, so
 * that the code under test is called directly (no reflection, which allocates, in the measured region).
 */
public final class TestSourceCompiler
{
   private TestSourceCompiler()
   {
   }

   /**
    * Compiles every .java file under {@code sources} into {@code classes} and returns a class loader for them whose
    * parent is the test class loader.
    */
   public static URLClassLoader compileAndLoad(Path sources, Path classes) throws IOException, URISyntaxException
   {
      return compileAndLoad(sources, classes, TestSourceCompiler.class.getClassLoader());
   }

   /**
    * Compiles every .java file under {@code sources} into {@code classes}, with the directories in {@code extraClassPath}
    * on the class path, and returns a class loader for them with the given parent (which must see the extra classes).
    */
   public static URLClassLoader compileAndLoad(Path sources, Path classes, ClassLoader parent, Path... extraClassPath) throws IOException, URISyntaxException
   {
      JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
      assertNotNull(compiler, "a JDK (system Java compiler) is required");

      Set<String> classPath = new LinkedHashSet<>();
      for (Class<?> type : new Class<?>[] {ROS2Message.class, CDRBuffer.class, std_msgs.Header.class, TestSourceCompiler.class})
         classPath.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
      for (Path path : extraClassPath)
         classPath.add(path.toString());

      Files.createDirectories(classes);
      List<String> arguments = new ArrayList<>(List.of("-encoding", "UTF-8", "-proc:none", "-d", classes.toString(), "-classpath", String.join(File.pathSeparator, classPath)));
      try (Stream<Path> files = Files.walk(sources))
      {
         files.filter(path -> path.toString().endsWith(".java")).forEach(path -> arguments.add(path.toString()));
      }
      ByteArrayOutputStream errors = new ByteArrayOutputStream();
      int result = compiler.run(null, null, errors, arguments.toArray(new String[0]));
      assertEquals(0, result, "Test sources must compile:\n" + errors.toString(StandardCharsets.UTF_8));

      return new URLClassLoader(new URL[] {classes.toUri().toURL()}, parent);
   }
}
