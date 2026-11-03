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

import us.ihmc.jros2.ROS2Message;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enumerates every bundled message class from the sources in {@code src/main/java-interfaces}, so a newly bundled type
 * is covered automatically, and fails if any of them cannot be loaded or is not a concrete ROS2Message.
 */
public final class BundledMessageCatalog
{
   /** The number of bundled message types when this gate was written; the catalog may grow, never shrink. */
   public static final int MINIMUM_BUNDLED_TYPES = 179;

   private BundledMessageCatalog()
   {
   }

   public static Path repositoryRoot()
   {
      Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
      for (Path candidate = directory; candidate != null; candidate = candidate.getParent())
      {
         if (Files.isDirectory(candidate.resolve("src").resolve("main").resolve("java-interfaces")))
            return candidate;
      }
      throw new IllegalStateException("src/main/java-interfaces not found above " + directory);
   }

   @SuppressWarnings({"rawtypes", "unchecked"})
   public static List<Class<? extends ROS2Message>> bundledMessageClasses()
   {
      Path interfaces = repositoryRoot().resolve("src").resolve("main").resolve("java-interfaces");
      List<String> classNames = new ArrayList<>();
      try (Stream<Path> files = Files.walk(interfaces))
      {
         files.filter(path -> path.toString().endsWith(".java")).sorted().forEach(path ->
         {
            String relative = interfaces.relativize(path).toString();
            classNames.add(relative.substring(0, relative.length() - ".java".length()).replace(java.io.File.separatorChar, '.'));
         });
      }
      catch (IOException e)
      {
         throw new UncheckedIOException(e);
      }

      List<Class<? extends ROS2Message>> classes = new ArrayList<>();
      for (String className : classNames)
      {
         Class<?> type;
         try
         {
            type = Class.forName(className);
         }
         catch (ClassNotFoundException e)
         {
            throw new AssertionError("bundled message source without class on the test classpath: " + className, e);
         }
         assertTrue(ROS2Message.class.isAssignableFrom(type), className + " is not a ROS2Message");
         assertTrue(!Modifier.isAbstract(type.getModifiers()) && !type.isInterface(), className + " is not concrete");
         assertNotNull(ROS2Message.getNameFromMessageClass((Class) type), className + " has no name");
         classes.add((Class<? extends ROS2Message>) type);
      }

      assertEquals(classNames.size(), classes.size());
      assertTrue(classes.size() >= MINIMUM_BUNDLED_TYPES, "only " + classes.size() + " bundled message types found");
      return classes;
   }
}
