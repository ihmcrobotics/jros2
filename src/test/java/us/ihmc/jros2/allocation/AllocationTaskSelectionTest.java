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
package us.ihmc.jros2.allocation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import us.ihmc.jros2.allocation.epsilon.EpsilonGcAllocationTest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The allocation test tasks only send classes named {@code *AllocationTest} or {@code *Test} in an {@code allocation}
 * package to their test JVMs (see build.gradle.kts): a class tagged "allocation" with another name would silently never run. This
 * normal-suite test fails if one exists.
 */
public class AllocationTaskSelectionTest
{
   /** The class file path (relative to the classes directory, with '/') matches the include patterns of the tasks. */
   static boolean isCandidate(String classFilePath)
   {
      return classFilePath.endsWith("AllocationTest.class") || (classFilePath.startsWith("allocation/") || classFilePath.contains("/allocation/"))
                                                                && classFilePath.endsWith("Test.class");
   }

   private static boolean isTaggedAllocation(Class<?> type)
   {
      List<Tag> tags = new ArrayList<>();
      Tag tag = type.getAnnotation(Tag.class);
      if (tag != null)
      {
         tags.add(tag);
      }
      Tags container = type.getAnnotation(Tags.class);
      if (container != null)
      {
         tags.addAll(List.of(container.value()));
      }
      for (Tag t : tags)
      {
         if (t.value().equals("allocation"))
         {
            return true;
         }
      }
      return false;
   }

   @Test
   public void testEveryAllocationTaggedClassIsACandidate() throws IOException, URISyntaxException
   {
      Path classes = Paths.get(AllocationTaskSelectionTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      List<String> tagged = new ArrayList<>();
      List<String> missed = new ArrayList<>();
      try (Stream<Path> files = Files.walk(classes))
      {
         for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".class"))::iterator)
         {
            String relative = classes.relativize(file).toString().replace('\\', '/');
            String className = relative.substring(0, relative.length() - ".class".length()).replace('/', '.');
            Class<?> type;
            try
            {
               type = Class.forName(className, false, AllocationTaskSelectionTest.class.getClassLoader());
            }
            catch (ClassNotFoundException | LinkageError e)
            {
               continue; // a class that cannot be loaded cannot hold runnable tests either
            }
            if (isTaggedAllocation(type))
            {
               tagged.add(className);
               if (!isCandidate(relative))
               {
                  missed.add(className);
               }
            }
         }
      }
      assertTrue(tagged.contains(AllocationGateMutationTest.class.getName()) && tagged.size() >= 10, "tagged classes found: " + tagged);
      // The GC-free (Epsilon) gate, in a subpackage of its own
      assertTrue(tagged.contains(EpsilonGcAllocationTest.class.getName()), "tagged classes found: " + tagged);
      assertTrue(isCandidate("us/ihmc/jros2/allocation/epsilon/EpsilonGcAllocationTest.class"));
      assertTrue(missed.isEmpty(), "classes tagged \"allocation\" that the allocation test tasks never run (rename them): " + missed);
      assertTrue(!isCandidate("us/ihmc/jros2/ROS2PublisherTest.class") && isCandidate("us/ihmc/jros2/allocation/x/YTest.class")
                 && !isCandidate("us/ihmc/jros2/allocation/SpinWait.class") && isCandidate("us/ihmc/jros2/FooAllocationTest.class")
                 && !isCandidate("us/ihmc/jros2/FooAllocationTest$Inner.class"));
   }
}
