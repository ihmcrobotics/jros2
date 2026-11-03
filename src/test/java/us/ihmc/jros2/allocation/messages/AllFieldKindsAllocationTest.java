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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.ihmc.fastddsjava.cdr.idl.IDLBoolSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLByteSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLCharSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLDoubleSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLFloatSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLIntSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLLongSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLObjectSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLShortSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLStringSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLWStringSequence;
import us.ihmc.jros2.ROS2Message;
import us.ihmc.jros2.generator.jros2InterfaceGenerator;
import us.ihmc.jros2.allocation.ClassFileStrings;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generates (with the generator and template of this repository), compiles and loads test messages that use every field
 * kind jros2 supports, then runs the steady-state allocation gate of {@link MessageAllocationHarness} on them. Because the
 * classes are generated when the test runs, an allocation added to the template fails this test without regenerating the
 * bundled classes.
 */
@Tag("allocation")
public class AllFieldKindsAllocationTest
{
   private static final String[] PRIMITIVES = {"bool", "byte", "char", "int8", "uint8", "int16", "uint16", "int32", "uint32", "int64", "uint64", "float32", "float64"};

   private static String allKindsMsg()
   {
      StringBuilder msg = new StringBuilder("# Every field kind jros2 supports (wchar cannot be declared in .msg files)\n");
      msg.append("uint8 BOX=1\nstring NAME=\"näme 𝄞\"\nfloat64 RATIO=0.5\n");
      for (String primitive : PRIMITIVES)
      {
         msg.append(primitive).append(" s_").append(primitive).append('\n');
         msg.append(primitive).append("[3] a_").append(primitive).append('\n');
         msg.append(primitive).append("[] q_").append(primitive).append('\n');
         msg.append(primitive).append("[<=7] bq_").append(primitive).append('\n');
      }
      msg.append("""
                 int32 with_default 42
                 string s
                 wstring ws
                 string<=10 bs
                 wstring<=10 bws
                 string with_default_string "défaut 😀"
                 string[2] a_s
                 wstring[2] a_ws
                 string<=5[2] a_bs
                 string[] q_s
                 wstring[] q_ws
                 string<=6[] q_bs
                 wstring<=6[] q_bws
                 string[<=3] bq_s
                 wstring[<=3] bq_ws
                 string<=4[<=3] bq_bs
                 int32[] q_default [1, 2, 3]
                 string[] q_string_default ["a", "é"]
                 Nested n
                 Nested[2] a_n
                 Nested[] q_n
                 Nested[<=3] bq_n
                 OnlyConstants c
                 OnlyConstants[2] a_c
                 OnlyConstants[] q_c
                 Blank blank
                 Blank[<=2] bq_blank
                 geometry_msgs/Pose pose
                 std_msgs/Header header
                 geometry_msgs/PoseStamped[] poses
                 """);
      return msg.toString();
   }

   private static final String NESTED_MSG = """
                                            int32 id
                                            string label
                                            float64[] values
                                            Inner[] inners
                                            Inner inner
                                            Inner[2] fixed_inners
                                            wstring w
                                            """;

   private static final String INNER_MSG = """
                                           uint16[<=4] ports
                                           string[] names
                                           bool flag
                                           char[] chars
                                           """;

   private static final String ONLY_CONSTANTS_MSG = "uint8 A=1\nstring B=\"b\"\n";
   private static final String BLANK_MSG = "# no fields, no constants\n";

   @TempDir
   static Path tmp;
   private static URLClassLoader classLoader;
   private static CallingThreadAllocationGate gate;

   @BeforeAll
   public static void generateAndCompile() throws Exception
   {
      Path packagePath = tmp.resolve("pkgs").resolve("jros2_alloc_kinds");
      Map<String, String> messages = new LinkedHashMap<>();
      messages.put("AllKinds.msg", allKindsMsg());
      messages.put("Nested.msg", NESTED_MSG);
      messages.put("Inner.msg", INNER_MSG);
      messages.put("OnlyConstants.msg", ONLY_CONSTANTS_MSG);
      messages.put("Blank.msg", BLANK_MSG);
      Files.createDirectories(packagePath.resolve("msg"));
      Files.writeString(packagePath.resolve("package.xml"), "<package format=\"3\"><name>jros2_alloc_kinds</name></package>\n");
      for (Map.Entry<String, String> message : messages.entrySet())
         Files.writeString(packagePath.resolve("msg").resolve(message.getKey()), message.getValue(), StandardCharsets.UTF_8);

      Path generated = tmp.resolve("generated");
      int count = new jros2InterfaceGenerator().addPackagePath(packagePath).setOutputDir(generated).generate();
      assertEquals(messages.size(), count);

      classLoader = TestSourceCompiler.compileAndLoad(generated, tmp.resolve("classes"));
   }

   @AfterAll
   public static void cleanUp() throws Exception
   {
      if (classLoader != null)
         classLoader.close();
   }

   /** The sampler is registered only while a test runs (never between tests). */
   @BeforeEach
   public void installGate()
   {
      gate = CallingThreadAllocationGate.create();
   }

   @AfterEach
   public void removeGate()
   {
      gate.close();
      gate = null;
   }

   @SuppressWarnings({"rawtypes", "unchecked"})
   private static Class<? extends ROS2Message> load(String simpleName) throws ClassNotFoundException
   {
      return (Class<? extends ROS2Message>) classLoader.loadClass("jros2_alloc_kinds." + simpleName);
   }

   @Test
   public void testEveryFieldKindIsPresent() throws Exception
   {
      Set<Class<?>> fieldTypes = new HashSet<>();
      for (Field field : load("AllKinds").getDeclaredFields())
      {
         if (!Modifier.isStatic(field.getModifiers()))
            fieldTypes.add(field.getType());
      }
      Class<?>[] required = {boolean.class, byte.class, char.class, short.class, int.class, long.class, float.class, double.class, boolean[].class, byte[].class,
                             char[].class, short[].class, int[].class, long[].class, float[].class, double[].class, StringBuilder.class, StringBuilder[].class,
                             IDLBoolSequence.class, IDLByteSequence.class, IDLCharSequence.class, IDLShortSequence.class, IDLIntSequence.class,
                             IDLLongSequence.class, IDLFloatSequence.class, IDLDoubleSequence.class, IDLStringSequence.class, IDLWStringSequence.class,
                             IDLObjectSequence.class, load("Nested"), load("OnlyConstants"), load("Blank"), geometry_msgs.Pose.class, std_msgs.Header.class};
      for (Class<?> type : required)
         assertTrue(fieldTypes.contains(type), "AllKinds has no field of type " + type.getName() + ": " + fieldTypes);

      // Bounded and unbounded variants are both present
      Object allKinds = load("AllKinds").getDeclaredConstructor().newInstance();
      int bounded = 0, unbounded = 0;
      for (Field field : load("AllKinds").getDeclaredFields())
      {
         if (IDLSequence.class.isAssignableFrom(field.getType()))
         {
            field.setAccessible(true);
            if (((IDLSequence<?>) field.get(allKinds)).isUnbounded())
               unbounded++;
            else
               bounded++;
         }
      }
      assertEquals(PRIMITIVES.length + 5, bounded);
      assertEquals(PRIMITIVES.length + 9, unbounded);
   }

   @SuppressWarnings("rawtypes")
   @Test
   public void testEveryFieldKindIsAllocationFree() throws Exception
   {
      String[] types = {"AllKinds", "Nested", "Inner", "OnlyConstants", "Blank"};
      for (String simpleName : types)
      {
         Class<? extends ROS2Message> type = load(simpleName);
         // Standard sizes, then sequences up to 64 primitives / 7 strings / 4 nested messages with 24-character strings
         MessageFiller standard = MessageFiller.standard(simpleName.hashCode());
         MessageAllocationHarness.assertSteadyStateAllocationFree(gate, type, standard::fillHighWaterMark, standard::fillRandom);
         MessageFiller larger = new MessageFiller(31L * simpleName.hashCode(), 64, 7, 4, 24);
         MessageAllocationHarness.assertSteadyStateAllocationFree(gate, type, larger::fillHighWaterMark, larger::fillRandom);
         // Non-Latin-1 strings after an ASCII-only warm-up
         MessageAllocationHarness.assertNonLatin1AfterAsciiAllocationFree(gate,
                                                                        type,
                                                                        MessageFiller.standard(simpleName.hashCode())::fillHighWaterMarkAscii,
                                                                        MessageFiller.standard(simpleName.hashCode())::fillHighWaterMark);
      }
   }

   /**
    * Every field kind in a long steady state (see {@link LongSteadyStateAllocationTest}): the methods of the generated
    * classes, loaded fresh by this test, cross their JIT compilation thresholds inside the measured windows.
    */
   @SuppressWarnings("rawtypes")
   @Test
   public void testEveryFieldKindInALongSteadyState() throws Exception
   {
      for (String simpleName : new String[] {"AllKinds", "Nested", "Inner", "OnlyConstants", "Blank"})
      {
         Class<? extends ROS2Message> type = load(simpleName);
         MessageFiller filler = MessageFiller.standard(17L * simpleName.hashCode());
         MessageAllocationHarness.assertLongSteadyStateAllocationFree(gate, type, filler::fillHighWaterMark, filler::fillRandom, LongSteadyStateAllocationTest.rounds());
      }
   }

   /**
    * The string accessors of the generated test messages (scalar string, wstring, bounded strings, default values):
    * setX(String), setX(CharSequence), getX() allocate nothing; getXAsString() allocates by nature.
    */
   @Test
   public void testStringAccessors() throws Exception
   {
      List<Class<?>> types = new ArrayList<>();
      for (String simpleName : new String[] {"AllKinds", "Nested", "Inner", "OnlyConstants", "Blank"})
         types.add(load(simpleName));
      StringAccessorDrivers drivers = new StringAccessorDrivers(types, tmp.resolve("accessors"), classLoader, tmp.resolve("classes"));
      try
      {
         // AllKinds: s, ws, bs, bws, with_default_string; Nested: label, w
         assertEquals(2, drivers.getTypeCount());
         assertEquals(7, drivers.getFieldCount());
         StringAccessorsAllocationTest.assertAccessors(gate, drivers, "generated test types");
      }
      finally
      {
         drivers.close();
      }
   }

   /**
    * The classes generated from the template have no string literal that HotSpot would have to create on their first C2
    * compilation (see {@link StringConstantsAllocationTest}).
    */
   @Test
   public void testNoUnresolvedStringLiterals() throws Exception
   {
      List<String> violations = new ArrayList<>();
      int strings = 0;
      for (String simpleName : new String[] {"AllKinds", "Nested", "Inner", "OnlyConstants", "Blank"})
      {
         ClassFileStrings classFile = new ClassFileStrings(load(simpleName));
         strings += classFile.getStringCount();
         StringConstantsAllocationTest.assertNoUnresolvedStrings(classFile, violations);
      }
      assertTrue(violations.isEmpty(), "string literals that HotSpot would create on the first C2 compilation:\n" + String.join("\n", violations));
      assertTrue(strings > 20, "the generated classes have string constants (names, defaults, labels): " + strings);
   }
}
