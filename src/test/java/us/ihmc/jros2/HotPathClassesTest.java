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
package us.ihmc.jros2;

import org.junit.jupiter.api.Test;
import us.ihmc.jros2.allocation.ClassFileStrings;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The classes whose methods run on the publish and receive paths (the jros2 runtime, the JNI wrapper, the CDR buffer and
 * the IDL sequences; the generated messages are checked by StringConstantsAllocationTest) contain no string literals (see
 * {@link HotPathText}):
 * HotSpot creates the unused literals of a class on the thread that triggers the class's first C2 compilation, which for
 * these classes is a publishing, polling or callback thread, at a time that depends on the JIT. This is the one
 * allocation of the hot path that the allocation gates can only observe by chance (the gates warm up past the compile
 * thresholds), so it is checked here, on the class files, deterministically.
 */
public class HotPathClassesTest
{
   /** Classes with methods on the hot path, each with all its nested, local and anonymous classes. */
   static final String[] HOT_PATH_CLASSES = {"us.ihmc.jros2.ROS2Publisher",
                                             "us.ihmc.jros2.AsyncROS2Publisher",
                                             "us.ihmc.jros2.AsyncROS2Node",
                                             "us.ihmc.jros2.ROS2Subscription",
                                             "us.ihmc.jros2.CallbackContext",
                                             "us.ihmc.jros2.SampleInfo",
                                             "us.ihmc.jros2.Guid",
                                             "us.ihmc.jros2.Statistics",
                                             "us.ihmc.jros2.StatisticsCalculator",
                                             "us.ihmc.jros2.MessageStatisticsProvider",
                                             "us.ihmc.jros2.AllocationFreeLock",
                                             "us.ihmc.jros2.ROS2CallbackExecutor",
                                             "us.ihmc.jros2.TopicData",
                                             "us.ihmc.jros2.ROS2Topic",
                                             "us.ihmc.fastddsjava.natives.fastddsjava",
                                             "us.ihmc.fastddsjava.natives.fastddsjavaCallback",
                                             "us.ihmc.fastddsjava.cdr.CDRBuffer",
                                             "us.ihmc.fastddsjava.cdr.CDRSerializable",
                                             "us.ihmc.fastddsjava.cdr.idl.IDLSequence",
                                             "us.ihmc.fastddsjava.cdr.idl.IDLBoolSequence",
                                             "us.ihmc.fastddsjava.cdr.idl.IDLByteSequence",
                                             "us.ihmc.fastddsjava.cdr.idl.IDLCharSequence",
                                             "us.ihmc.fastddsjava.cdr.idl.IDLShortSequence",
                                             "us.ihmc.fastddsjava.cdr.idl.IDLIntSequence",
                                             "us.ihmc.fastddsjava.cdr.idl.IDLLongSequence",
                                             "us.ihmc.fastddsjava.cdr.idl.IDLFloatSequence",
                                             "us.ihmc.fastddsjava.cdr.idl.IDLDoubleSequence",
                                             "us.ihmc.fastddsjava.cdr.idl.IDLObjectSequence",
                                             "us.ihmc.fastddsjava.cdr.idl.IDLStringSequence",
                                             "us.ihmc.fastddsjava.cdr.idl.IDLWStringSequence"};
   /**
    * The classes of the CDR packages that only error paths and setup use (they may hold any literal). Every other class of
    * those packages must be in {@link #HOT_PATH_CLASSES}: checked, so that a new CDR class cannot be forgotten.
    */
   static final Set<String> CDR_COLD_CLASSES = Set.of("us.ihmc.fastddsjava.cdr.CDRBufferErrors",
                                                      "us.ihmc.fastddsjava.cdr.CDRDeserializationException",
                                                      "us.ihmc.fastddsjava.cdr.JdkPreparation",
                                                      "us.ihmc.fastddsjava.cdr.idl.IDLSequenceErrors");
   /**
    * Hot-path classes nested in classes that are not on the hot path themselves (their other nested classes are not
    * either).
    */
   static final String[] HOT_PATH_NESTED_CLASSES = {"us.ihmc.jros2.EndpointSupport$Gate",
                                                    "us.ihmc.jros2.EndpointSupport$Trampoline",
                                                    "us.ihmc.jros2.EndpointSupport$RateLimiter",
                                                    "us.ihmc.jros2.EndpointSupport$BoundedMpscSequencer",
                                                    "us.ihmc.jros2.ROS2Participant$CallbackStack",
                                                    "us.ihmc.jros2.ROS2Node$TrackedCallback",
                                                    "us.ihmc.jros2.ROS2Node$SamplerCallback"};
   /**
    * Test code that runs inside the windows of the allocation gates: a literal there would make a gate fail by chance.
    * (The message kinds of {@code HotPathMessages} have literals, but only in {@code create()}, which runs, and so creates
    * them, before any window.)
    */
   static final String[] GATE_WINDOW_CLASSES = {"us.ihmc.jros2.allocation.SpinWait",
                                                "us.ihmc.jros2.allocation.AllocationGate$Builder",
                                                "us.ihmc.jros2.allocation.AllocationGate$Observer",
                                                "us.ihmc.jros2.HotPathPubSub",
                                                "us.ihmc.jros2.HotPathMessages$MessageKind"};

   /** @return {@code binaryName} and its nested, local and anonymous classes, recursively */
   static Set<String> withNestedClasses(String binaryName) throws ClassNotFoundException
   {
      Set<String> names = new LinkedHashSet<>();
      addWithNestedClasses(Class.forName(binaryName, false, HotPathClassesTest.class.getClassLoader()), names);
      return names;
   }

   private static void addWithNestedClasses(Class<?> type, Set<String> names) throws ClassNotFoundException
   {
      if (!names.add(type.getName()))
      {
         return;
      }
      for (Class<?> nested : type.getDeclaredClasses())
      {
         addWithNestedClasses(nested, names);
      }
      // Anonymous classes (and javac's enum switch maps) are not declared classes: javac numbers them Outer$1, Outer$2, ...
      ClassLoader loader = HotPathClassesTest.class.getClassLoader();
      for (int i = 1; ; ++i)
      {
         String anonymous = type.getName() + "$" + i;
         if (loader.getResource(anonymous.replace('.', '/') + ".class") == null)
         {
            break;
         }
         addWithNestedClasses(Class.forName(anonymous, false, loader), names);
      }
   }

   private static List<String> violations(Iterable<String> classNames) throws IOException, ClassNotFoundException
   {
      List<String> violations = new ArrayList<>();
      for (String className : classNames)
      {
         List<String> literals = new ArrayList<>(ClassFileStrings.of(className));
         // The names of enum constants are created when the enum class is initialized, before any use.
         Class<?> type = Class.forName(className, false, HotPathClassesTest.class.getClassLoader());
         if (type.isEnum())
         {
            for (Field field : type.getDeclaredFields())
            {
               if (field.isEnumConstant())
               {
                  literals.remove(field.getName());
               }
            }
         }
         if (!literals.isEmpty())
         {
            violations.add(className + " " + literals);
         }
      }
      return violations;
   }

   @Test
   public void testHotPathClassesHaveNoStringLiterals() throws Exception
   {
      Set<String> classes = new LinkedHashSet<>();
      for (String name : HOT_PATH_CLASSES)
      {
         classes.addAll(withNestedClasses(name));
      }
      for (String name : HOT_PATH_NESTED_CLASSES)
      {
         classes.add(Class.forName(name, false, HotPathClassesTest.class.getClassLoader()).getName());
      }
      assertTrue(classes.contains("us.ihmc.jros2.Statistics$StatisticDataType"), "nested classes are included: " + classes);
      assertTrue(classes.contains("us.ihmc.fastddsjava.cdr.idl.IDLBoolSequence$BooleanBufferWrapper"), "nested classes are included: " + classes);
      List<String> violations = violations(classes);
      assertTrue(violations.isEmpty(), "String literals in hot-path classes (move them to HotPathText): " + String.join("\n", violations));
   }

   /** Every class of the CDR packages is either on the hot path (and checked above) or a known cold class. */
   @Test
   public void testEveryCDRClassIsClassified() throws Exception
   {
      Set<String> hotPath = new LinkedHashSet<>();
      for (String name : HOT_PATH_CLASSES)
      {
         hotPath.addAll(withNestedClasses(name));
      }
      List<String> unclassified = new ArrayList<>();
      int found = 0;
      for (String packageName : new String[] {"us.ihmc.fastddsjava.cdr", "us.ihmc.fastddsjava.cdr.idl"})
      {
         for (String className : classesOfPackage(packageName))
         {
            found++;
            if (!hotPath.contains(className) && !CDR_COLD_CLASSES.contains(className))
            {
               unclassified.add(className);
            }
         }
      }
      assertTrue(found >= 25, "CDR classes found: " + found);
      assertTrue(unclassified.isEmpty(), "CDR classes neither in HOT_PATH_CLASSES nor in CDR_COLD_CLASSES: " + unclassified);
   }

   /** @return the binary names of the classes (incl. nested) of a CDR package in the jros2 jar or classes directory */
   private static List<String> classesOfPackage(String packageName) throws Exception
   {
      String path = packageName.replace('.', '/');
      java.nio.file.Path location = java.nio.file.Path.of(us.ihmc.fastddsjava.cdr.CDRBuffer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      List<String> names = new ArrayList<>();
      if (java.nio.file.Files.isDirectory(location))
      {
         addClassFiles(location.resolve(path), packageName, names);
      }
      else
      {
         try (java.nio.file.FileSystem jar = java.nio.file.FileSystems.newFileSystem(location))
         {
            addClassFiles(jar.getPath(path), packageName, names);
         }
      }
      return names;
   }

   private static void addClassFiles(java.nio.file.Path directory, String packageName, List<String> names) throws IOException
   {
      try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(directory))
      {
         files.map(file -> file.getFileName().toString())
              .filter(file -> file.endsWith(".class"))
              .forEach(file -> names.add(packageName + "." + file.substring(0, file.length() - ".class".length())));
      }
   }

   @Test
   public void testGateWindowCodeHasNoStringLiterals() throws Exception
   {
      List<String> classes = new ArrayList<>();
      for (String name : GATE_WINDOW_CLASSES)
      {
         classes.add(Class.forName(name, false, HotPathClassesTest.class.getClassLoader()).getName());
      }
      List<String> violations = violations(classes);
      assertTrue(violations.isEmpty(), "String literals in code that runs inside allocation gate windows: " + String.join("\n", violations));
   }

   /** The scanner finds literals, concatenation recipes and literals inside lambdas (positive controls). */
   @Test
   public void testScannerFindsLiterals() throws Exception
   {
      List<String> literals = ClassFileStrings.of(HotPathText.class.getName());
      assertTrue(literals.contains("SampleInfo has been closed"), literals.toString());
      assertTrue(literals.contains("capacity must be at least 1: \u0001"), "concatenation recipe: " + literals);
      List<String> lambdaLiterals = ClassFileStrings.of(LiteralInLambda.class.getName());
      assertEquals(List.of("in a lambda"), lambdaLiterals);
      Set<String> nested = withNestedClasses(LiteralInLambda.class.getName());
      assertTrue(nested.contains(LiteralInLambda.class.getName() + "$1"), nested.toString());
      assertFalse(violations(nested).isEmpty());
      assertThrows(IllegalArgumentException.class, () -> ClassFileStrings.of("us.ihmc.jros2.NoSuchClass"));
   }

   private static final class LiteralInLambda
   {
      static final Runnable LAMBDA = () -> System.getProperty("in a lambda");
      static final Object ANONYMOUS = new Object()
      {
         @Override
         public String toString()
         {
            return "in an anonymous class";
         }
      };
   }
}
