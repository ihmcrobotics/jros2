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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.jros2.ROS2Message;
import us.ihmc.jros2.allocation.ClassFileStrings;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The classes on the message hot path (CDRBuffer, the IDL sequences and every bundled message class) contain no string
 * literal that HotSpot would have to create when it first compiles one of their methods with C2: that happens on the
 * thread that triggers the compilation, so it would allocate on a control thread in the middle of its steady state, once
 * per class, invisibly to bytecode instrumentation (see {@link ClassFileStrings}). Error messages are built in classes
 * used only by error paths, labels are compile-time constants, and the toString() labels of generated messages are
 * created by the static initializer.
 */
@Tag("allocation")
public class StringConstantsAllocationTest
{
   /** Classes used only on error paths, which may hold any string literal. */
   private static final Set<String> COLD_CLASSES = Set.of("us.ihmc.fastddsjava.cdr.CDRBufferErrors",
                                                          "us.ihmc.fastddsjava.cdr.idl.IDLSequenceErrors",
                                                          "us.ihmc.fastddsjava.cdr.CDRDeserializationException");
   /** The CDR classes when this gate was written (top-level, nested and anonymous); may grow, never shrink. */
   private static final int MINIMUM_CDR_CLASSES = 25;

   @Test
   public void testCDRClassesHaveNoUnresolvedStringLiterals() throws Exception
   {
      Map<String, byte[]> classFiles = cdrClassFiles();
      assertTrue(classFiles.keySet().containsAll(COLD_CLASSES), "the cold classes exist: " + classFiles.keySet());
      List<String> violations = new ArrayList<>();
      int checked = 0;
      for (Map.Entry<String, byte[]> classFile : classFiles.entrySet())
      {
         if (COLD_CLASSES.contains(classFile.getKey()))
            continue;
         ClassFileStrings strings = new ClassFileStrings(classFile.getKey(), classFile.getValue());
         List<String> unresolved = strings.unresolvedStrings(false);
         if (!unresolved.isEmpty())
            violations.add(classFile.getKey() + ": " + unresolved);
         checked++;
      }
      if (!violations.isEmpty())
         fail("string literals that HotSpot would create on the first C2 compilation (make them compile-time constants or move them to a cold class):\n"
              + String.join("\n", violations));
      assertTrue(checked >= MINIMUM_CDR_CLASSES, "only " + checked + " CDR classes checked");
   }

   @SuppressWarnings("rawtypes")
   @Test
   public void testBundledMessagesHaveNoUnresolvedStringLiterals() throws Exception
   {
      List<Class<? extends ROS2Message>> types = BundledMessageCatalog.bundledMessageClasses();
      List<String> violations = new ArrayList<>();
      int checked = 0;
      for (Class<? extends ROS2Message> type : types)
      {
         checked += assertNoUnresolvedStrings(new ClassFileStrings(type), violations);
      }
      if (!violations.isEmpty())
         fail("string literals that HotSpot would create on the first C2 compilation:\n" + String.join("\n", violations));
      assertEquals(types.size(), checked);
   }

   /**
    * The test code that runs inside the measured regions (the gate's begin/end and the harness rounds) follows the same
    * rule, so that the JIT compilation of the test code itself cannot show up as an allocation.
    */
   @Test
   public void testMeasuredTestCodeHasNoUnresolvedStringLiterals() throws Exception
   {
      for (Class<?> type : new Class<?>[] {SingleThreadAllocationGate.class, ThreadAllocatedBytesGate.class, MessageAllocationHarness.Round.class})
         assertEquals(List.of(), new ClassFileStrings(type).unresolvedStrings(false), type.getName());
   }

   /**
    * Adds a violation if a generated message class has a string literal that is not resolved when the class is initialized
    * or constructed (a generated message has no static method, so its constructor runs before any other method).
    *
    * @return 1
    */
   public static int assertNoUnresolvedStrings(ClassFileStrings strings, List<String> violations)
   {
      List<String> unresolved = strings.unresolvedStrings(true);
      if (!unresolved.isEmpty())
         violations.add(strings.getClassName() + ": " + unresolved);
      return 1;
   }

   /** The class files of the packages us.ihmc.fastddsjava.cdr and us.ihmc.fastddsjava.cdr.idl, by class name. */
   private static Map<String, byte[]> cdrClassFiles() throws IOException
   {
      URI location;
      try
      {
         location = CDRBuffer.class.getProtectionDomain().getCodeSource().getLocation().toURI();
      }
      catch (java.net.URISyntaxException e)
      {
         throw new IOException(e);
      }

      Map<String, byte[]> classFiles = new java.util.TreeMap<>();
      Path root = Path.of(location);
      if (Files.isDirectory(root))
      {
         collect(root, classFiles);
      }
      else
      {
         try (FileSystem jar = FileSystems.newFileSystem(root))
         {
            collect(jar.getPath("/"), classFiles);
         }
      }
      return classFiles;
   }

   private static void collect(Path root, Map<String, byte[]> classFiles) throws IOException
   {
      for (String packagePath : new String[] {"us/ihmc/fastddsjava/cdr", "us/ihmc/fastddsjava/cdr/idl"})
      {
         Path directory = root.resolve(packagePath);
         try (Stream<Path> files = Files.list(directory))
         {
            for (Path file : (Iterable<Path>) files::iterator)
            {
               String fileName = file.getFileName().toString();
               if (fileName.endsWith(".class"))
                  classFiles.put(packagePath.replace('/', '.') + "." + fileName.substring(0, fileName.length() - ".class".length()), Files.readAllBytes(file));
            }
         }
      }
   }
}
