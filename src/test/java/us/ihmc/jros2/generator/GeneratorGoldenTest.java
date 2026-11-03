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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.IDLBoolSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLObjectSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLShortSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLStringSequence;
import us.ihmc.jros2.ROS2Message;
import us.ihmc.jros2.parser.field.InterfaceFieldParsingException;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generates classes from the RIHS01 edge-case packages and a golden package, compiles them with the system Java
 * compiler, and checks type hashes, defaults, bounds and CDR round trips of the compiled classes.
 */
public class GeneratorGoldenTest
{
   private static final String GOLDEN_MSG = """
                                            # Every container and default kind jros2 supports
                                            std_msgs/Header header
                                            uint8 BOX=1
                                            uint8 LARGE=200
                                            string NAME="golden"
                                            float32 RATIO=0.5
                                            int64 HUGE=5000000000
                                            bool flag True
                                            uint16 port 65535
                                            uint64 big 18446744073709551615
                                            float32[3] f3 [1, 2.5, -3]
                                            uint16[<=4] ports [1, 65535]
                                            string<=8[] labels ["a,b", 'c']
                                            wstring wide "wïde"
                                            string<=5 bounded "abc"
                                            char[2] chars [65, 200]
                                            geometry_msgs/Point[2] points
                                            geometry_msgs/Point[<=2] bounded_points
                                            int8[] bytes [-1, 1]
                                            string[2] pair ["x", "y"]
                                            bool[] bools [true, FALSE]
                                            EmptyMsg empty
                                            uint8[] data
                                            """;

   /**
    * Backslashes followed by 'u' in comments (javac translates unicode escapes in comments too) and in defaults. Every
    * backslash is doubled in this text block.
    */
   private static final String PATHS_MSG = """
                                           # Files are stored under C:\\users\\data, see \\\\server\\unc and \\u000a
                                           string path "C:\\users" # default C:\\users\\x
                                           # \\\\\\uabcd
                                           string<=8 escaped "\\u0041"
                                           """;

   @TempDir
   static Path tmp;
   private static Path generated;
   private static URLClassLoader classLoader;

   @BeforeAll
   public static void generateAndCompile() throws Exception
   {
      Path packages = tmp.resolve("pkgs");
      copyPackage(TypeDescriptionHasherTest.edgePackages().resolve("rihs_edge"), packages.resolve("rihs_edge"));
      copyPackage(TypeDescriptionHasherTest.edgePackages().resolve("rihs_gaps"), packages.resolve("rihs_gaps"));
      writePackage(packages.resolve("jros2_golden"), Map.of("Golden.msg", GOLDEN_MSG, "EmptyMsg.msg", "# constants only\nuint8 A=1\n", "Paths.msg", PATHS_MSG));

      generated = tmp.resolve("generated");
      int count = new jros2InterfaceGenerator().addPackagePath(packages.resolve("rihs_edge"))
                                               .addPackagePath(packages.resolve("rihs_gaps"))
                                               .addPackagePath(packages.resolve("jros2_golden"))
                                               .setOutputDir(generated)
                                               .generate();
      assertEquals(6 + 8 + 3, count);

      JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
      Assumptions.assumeTrue(compiler != null, "No system Java compiler");
      List<String> arguments = new ArrayList<>(List.of("-encoding", "UTF-8", "-proc:none", "-d", tmp.resolve("classes").toString(), "-classpath", jros2ClassPath()));
      try (Stream<Path> files = Files.walk(generated))
      {
         files.filter(path -> path.toString().endsWith(".java")).forEach(path -> arguments.add(path.toString()));
      }
      ByteArrayOutputStream errors = new ByteArrayOutputStream();
      int result = compiler.run(null, null, errors, arguments.toArray(new String[0]));
      assertEquals(0, result, "Generated sources must compile:\n" + errors.toString(StandardCharsets.UTF_8));

      classLoader = new URLClassLoader(new URL[] {tmp.resolve("classes").toUri().toURL()}, GeneratorGoldenTest.class.getClassLoader());
   }

   private static String jros2ClassPath() throws Exception
   {
      // The generated code needs the jros2 runtime and the bundled messages (std_msgs, geometry_msgs, ...)
      return Path.of(ROS2Message.class.getProtectionDomain().getCodeSource().getLocation().toURI()) + java.io.File.pathSeparator + Path.of(
            CDRBuffer.class.getProtectionDomain().getCodeSource().getLocation().toURI()) + java.io.File.pathSeparator + Path.of(std_msgs.Header.class.getProtectionDomain()
                                                                                                                                                      .getCodeSource()
                                                                                                                                                      .getLocation()
                                                                                                                                                      .toURI());
   }

   private static void copyPackage(Path source, Path destination) throws IOException
   {
      Files.createDirectories(destination.resolve("msg"));
      Files.copy(source.resolve("package.xml"), destination.resolve("package.xml"));
      try (Stream<Path> files = Files.list(source.resolve("msg")))
      {
         for (Path file : files.toList())
            Files.copy(file, destination.resolve("msg").resolve(file.getFileName()));
      }
   }

   static void writePackage(Path packagePath, Map<String, String> messages) throws IOException
   {
      Files.createDirectories(packagePath.resolve("msg"));
      Files.writeString(packagePath.resolve("package.xml"), "<package format=\"3\"><name>" + packagePath.getFileName() + "</name></package>\n");
      for (Map.Entry<String, String> message : messages.entrySet())
         Files.writeString(packagePath.resolve("msg").resolve(message.getKey()), message.getValue(), StandardCharsets.UTF_8);
   }

   private static Object newMessage(String className) throws Exception
   {
      return classLoader.loadClass(className).getDeclaredConstructor().newInstance();
   }

   private static Object call(Object target, String method, Object... arguments) throws Exception
   {
      for (Method candidate : target.getClass().getMethods())
      {
         if (candidate.getName().equals(method) && candidate.getParameterCount() == arguments.length)
         {
            try
            {
               return candidate.invoke(target, arguments);
            }
            catch (InvocationTargetException e)
            {
               if (e.getCause() instanceof Exception cause)
                  throw cause;
               throw e;
            }
         }
      }
      throw new NoSuchMethodException(method);
   }

   private static byte[] serialize(ROS2Message<?> message)
   {
      CDRBuffer buffer = new CDRBuffer();
      buffer.writePayloadHeader();
      message.serialize(buffer);
      byte[] bytes = new byte[buffer.getBufferUnsafe().position()];
      for (int i = 0; i < bytes.length; i++)
         bytes[i] = buffer.getBufferUnsafe().get(i);
      assertEquals(bytes.length - 4, message.calculateSizeBytes(0), "calculateSizeBytes of " + message.getClass().getName());
      return bytes;
   }

   private static void deserialize(byte[] payload, ROS2Message<?> message)
   {
      CDRBuffer buffer = new CDRBuffer();
      buffer.ensureRemainingCapacity(payload.length);
      buffer.getBufferUnsafe().put(payload);
      buffer.beginRead(payload.length);
      buffer.readPayloadHeader();
      message.deserialize(buffer);
      assertEquals(payload.length, buffer.getBufferUnsafe().position());
   }

   @Test
   public void testGeneratedTypeHashesMatchTheOfficialPipeline() throws Exception
   {
      int checked = 0;
      for (Map.Entry<String, String> vector : TypeDescriptionHasherTest.loadVectors().get("edge").entrySet())
      {
         if (!vector.getKey().startsWith("rihs_"))
            continue;
         String[] parts = vector.getKey().split("/");
         Class<?> messageClass = classLoader.loadClass(parts[0] + "." + parts[2]);
         assertEquals(vector.getValue(), ROS2Message.getTypeHashFromMessageClass(messageClass), vector.getKey());
         checked++;
      }
      assertEquals(14, checked);

      String goldenHash = ROS2Message.getTypeHashFromMessageClass(classLoader.loadClass("jros2_golden.Golden"));
      assertNotNull(goldenHash);
      assertTrue(goldenHash.matches("RIHS01_[0-9a-f]{64}"));
   }

   @Test
   @SuppressWarnings({"unchecked", "rawtypes"})
   public void testGoldenDefaultsBoundsAndRoundTrip() throws Exception
   {
      ROS2Message golden = (ROS2Message) newMessage("jros2_golden.Golden");
      Class<?> goldenClass = golden.getClass();

      // Constants
      assertEquals((byte) 1, goldenClass.getField("BOX").get(null));
      assertEquals((byte) 200, goldenClass.getField("LARGE").get(null));
      assertEquals("golden", goldenClass.getField("NAME").get(null));
      assertEquals(0.5f, goldenClass.getField("RATIO").get(null));
      assertEquals(5000000000L, goldenClass.getField("HUGE").get(null));

      // Defaults
      assertEquals(true, call(golden, "getFlag"));
      assertEquals(65535, call(golden, "getPort"));
      assertEquals(-1L, call(golden, "getBig"));
      assertArrayEquals(new float[] {1, 2.5f, -3}, (float[]) call(golden, "getF3"));
      IDLShortSequence ports = (IDLShortSequence) call(golden, "getPorts");
      assertEquals(2, ports.size());
      assertEquals(65535, Short.toUnsignedInt(ports.get(1)));
      assertEquals(4, ports.getMaxSize());
      IDLStringSequence labels = (IDLStringSequence) call(golden, "getLabels");
      assertEquals("[a,b, c]", labels.toString());
      assertTrue(labels.isUnbounded());
      assertEquals("wïde", call(golden, "getWideAsString"));
      assertEquals("abc", call(golden, "getBoundedAsString"));
      assertArrayEquals(new char[] {'A', (char) 200}, (char[]) call(golden, "getChars"));
      StringBuilder[] pair = (StringBuilder[]) call(golden, "getPair");
      assertEquals("x", pair[0].toString());
      assertEquals("y", pair[1].toString());
      assertEquals("[true, false]", call(golden, "getBools").toString());
      assertEquals("[-1, 1]", call(golden, "getBytes").toString());

      // Bounded sequence of nested messages
      IDLObjectSequence<?> boundedPoints = (IDLObjectSequence<?>) call(golden, "getBoundedPoints");
      boundedPoints.add();
      boundedPoints.add();
      assertThrows(IllegalArgumentException.class, boundedPoints::add);
      assertEquals(2, boundedPoints.size());

      // Populate and round trip
      call(call(golden, "getHeader"), "setFrameId", "främe");
      ((geometry_msgs.Point[]) call(golden, "getPoints"))[1].setZ(-7.5);
      ((IDLBoolSequence) call(golden, "getBools")).add(true);
      ((us.ihmc.fastddsjava.cdr.idl.IDLByteSequence) call(golden, "getData")).addAll(new byte[] {9, 8, 7});
      call(golden, "setWide", "😀");

      byte[] payload = serialize(golden);
      ROS2Message received = (ROS2Message) newMessage("jros2_golden.Golden");
      ((IDLStringSequence) call(received, "getLabels")).clear();
      deserialize(payload, received);
      assertEquals(golden.toString(), received.toString());

      ROS2Message copy = (ROS2Message) newMessage("jros2_golden.Golden");
      copy.set(received);
      assertEquals(golden.toString(), copy.toString());
      ((StringBuilder[]) call(received, "getPair"))[0].append("changed");
      assertEquals("x", ((StringBuilder[]) call(copy, "getPair"))[0].toString(), "set() must not alias string arrays");
      IDLObjectSequence<?> receivedPoints = (IDLObjectSequence<?>) call(received, "getBoundedPoints");
      IDLObjectSequence<?> copiedPoints = (IDLObjectSequence<?>) call(copy, "getBoundedPoints");
      assertEquals(2, copiedPoints.size());
      assertNotSame(receivedPoints.get(0), copiedPoints.get(0), "set() must not alias sequences of messages");
      ((geometry_msgs.Point) receivedPoints.get(0)).setX(99.0);
      assertEquals(0.0, ((geometry_msgs.Point) copiedPoints.get(0)).getX());
   }

   @Test
   public void testBackslashUInCommentsAndDefaults() throws Exception
   {
      // The generated sources compiled in generateAndCompile(); the default values keep their backslashes
      Object paths = newMessage("jros2_golden.Paths");
      assertEquals("C:\\users", call(paths, "getPathAsString"));
      assertEquals("\\u0041", call(paths, "getEscapedAsString"));

      String source = Files.readString(generated.resolve("jros2_golden/Paths.java"));
      assertTrue(source.contains("C:\\\\users\\data"), "an odd run of backslashes before 'u' is made even in comments");
      assertTrue(source.contains("see \\\\server\\\\unc"), "an even run is kept");
      assertTrue(source.contains("# \\\\\\\\uabcd"));
      assertTrue(source.contains("// default C:\\\\users\\x"), "trailing comments are escaped too");
   }

   @Test
   @SuppressWarnings({"unchecked", "rawtypes"})
   public void testEmptyAndConstantOnlyMessages() throws Exception
   {
      for (String className : new String[] {"jros2_golden.EmptyMsg", "rihs_edge.OnlyConstants"})
      {
         ROS2Message message = (ROS2Message) newMessage(className);
         assertArrayEquals(new byte[] {0, 1, 0, 0, 0}, serialize(message), className);
         deserialize(new byte[] {0, 1, 0, 0, 0}, message);
      }
   }

   @Test
   public void testGeneratedSourceUsesBulkAndUtf8Operations() throws IOException
   {
      String golden = Files.readString(generated.resolve("jros2_golden/Golden.java"));
      assertTrue(golden.contains("buffer.writeFloatArray(f3_);"));
      assertTrue(golden.contains("buffer.readCharArray(chars_);"));
      assertTrue(golden.contains("CDRBuffer.utf8Length(bounded_)"));
      assertTrue(golden.contains("System.arraycopy(from.f3_, 0, f3_, 0, f3_.length);"));
      assertTrue(golden.contains("bounded_points_.set(from.bounded_points_, geometry_msgs.Point::set);"), "sequences of messages are deep-copied");
      assertTrue(golden.contains("ports_ = new IDLShortSequence(0, 4);"));
      assertTrue(golden.contains("labels_ = new IDLStringSequence(0, IDLSequence.UNBOUNDED_MAX_SIZE, 8);"));
      assertTrue(golden.contains("public static final java.lang.String typeHash = \"RIHS01_"));
      assertTrue(golden.contains("does not enforce string bounds"));

      String empty = Files.readString(generated.resolve("jros2_golden/EmptyMsg.java"));
      assertTrue(empty.contains("buffer.writeStructureDummyMember();"));
      assertTrue(empty.contains("buffer.readStructureDummyMember();"));
      assertTrue(empty.contains("currentAlignment += 1; // uint8 structure_needs_at_least_one_member"));
   }

   @Test
   public void testTimeAndDurationAreRejected(@TempDir Path output)
   {
      Path packagePath = TypeDescriptionHasherTest.edgePackages().resolve("rihs_time");
      InterfaceFieldParsingException exception = assertThrows(InterfaceFieldParsingException.class,
                                                              () -> new jros2InterfaceGenerator().addPackagePath(packagePath).setOutputDir(output).generate());
      assertTrue(exception.getMessage().contains("builtin_interfaces/Time"), exception.getMessage());
   }

   @Test
   public void testUnresolvedReferenceGeneratesNullHash(@TempDir Path directory) throws Exception
   {
      writePackage(directory.resolve("user_pkg"), Map.of("UsesUnknown.msg", "unknown_pkg/Thing thing\n"));
      new jros2InterfaceGenerator().addPackagePath(directory.resolve("user_pkg")).setOutputDir(directory.resolve("out")).generate();
      String source = Files.readString(directory.resolve("out/user_pkg/UsesUnknown.java"));
      assertTrue(source.contains("public static final java.lang.String typeHash = null;"), source);
   }

   @Test
   public void testReferencesResolveFromDependencyPathsAndBundledDefinitions(@TempDir Path directory) throws Exception
   {
      writePackage(directory.resolve("deps").resolve("dep_pkg"), Map.of("Dep.msg", "string label\nstd_msgs/Header header\n"));
      writePackage(directory.resolve("user_pkg"), Map.of("User.msg", "dep_pkg/Dep dep\ngeometry_msgs/Pose pose\n"));

      // dependency path given as a directory of packages
      new jros2InterfaceGenerator().addPackagePath(directory.resolve("user_pkg"))
                                   .addDependencyPath(directory.resolve("deps"))
                                   .setOutputDir(directory.resolve("out1"))
                                   .generate();
      // dependency path given as the package itself
      new jros2InterfaceGenerator().addPackagePath(directory.resolve("user_pkg"))
                                   .addDependencyPath(directory.resolve("deps").resolve("dep_pkg"))
                                   .setOutputDir(directory.resolve("out2"))
                                   .generate();

      TypeDescriptionHasher reference = new TypeDescriptionHasher(TypeDescriptionHasherTest.resolver(true, directory.resolve("deps"), directory));
      String expected = reference.hash("user_pkg/msg/User");
      for (String out : new String[] {"out1", "out2"})
      {
         String source = Files.readString(directory.resolve(out).resolve("user_pkg/User.java"));
         assertTrue(source.contains("typeHash = \"" + expected + "\""), out);
         assertFalse(Files.exists(directory.resolve(out).resolve("dep_pkg")), "dependencies must not be generated");
      }

      // Without the dependency the hash cannot be computed, but generation still succeeds
      new jros2InterfaceGenerator().addPackagePath(directory.resolve("user_pkg")).setOutputDir(directory.resolve("out3")).generate();
      assertTrue(Files.readString(directory.resolve("out3/user_pkg/User.java")).contains("typeHash = null;"));
   }

   @Test
   public void testTypeToClassTypesAreHashedButNotGenerated(@TempDir Path directory) throws Exception
   {
      writePackage(directory.resolve("user_pkg"), Map.of("Mapped.msg", "int32 x\n", "User.msg", "Mapped mapped\n"));
      new jros2InterfaceGenerator().addPackagePath(directory.resolve("user_pkg"))
                                   .setTypeToClass(Map.of("user_pkg/Mapped", "com.example.CustomMapped"))
                                   .setOutputDir(directory.resolve("out"))
                                   .generate();
      assertFalse(Files.exists(directory.resolve("out/user_pkg/Mapped.java")));
      String user = Files.readString(directory.resolve("out/user_pkg/User.java"));
      assertTrue(user.contains("com.example.CustomMapped mapped_"));
      assertFalse(user.contains("typeHash = null"));
   }

   @Test
   public void testBundledDefinitionsFormatRoundTrip(@TempDir Path directory) throws IOException
   {
      Map<String, String> definitions = Map.of("a_pkg/msg/A", "int32 x\n", "b_pkg/msg/B", "a_pkg/A a\nstring s \"x\"\n");
      Path file = directory.resolve("defs.txt");
      jros2InterfaceGenerator.writeBundledDefinitions(file, definitions);
      assertEquals(definitions, jros2InterfaceGenerator.parseBundledDefinitions(Files.readString(file)));
      assertEquals("int32 x\nstring s \"\n", jros2InterfaceGenerator.normalizeDefinition("# c\n\nint32\tx # comment\nstring s \"#y\"\n"));
   }

   @Test
   public void testCommandLine(@TempDir Path directory) throws Exception
   {
      writePackage(directory.resolve("cli_pkg"), Map.of("Cli.msg", "std_msgs/Header header\n"));
      jros2GeneratorMain.main(new String[] {"--output-dir", directory.resolve("out").toString(), "--package", directory.resolve("cli_pkg").toString(),
                                            "--bundled-definitions-output", directory.resolve("defs.txt").toString()});
      assertTrue(Files.readString(directory.resolve("out/cli_pkg/Cli.java")).contains("typeHash = \"RIHS01_"));
      assertTrue(Files.readString(directory.resolve("defs.txt")).contains("@@@ cli_pkg/msg/Cli\nstd_msgs/Header header\n"));

      assertThrows(IllegalArgumentException.class, () -> jros2GeneratorMain.main(new String[] {"--bogus"}));
      assertThrows(IllegalArgumentException.class, () -> jros2GeneratorMain.main(new String[] {"--package", "x"}));
      assertThrows(IllegalArgumentException.class, () -> jros2GeneratorMain.main(new String[] {"--output-dir"}));
   }
}
