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
import org.junit.jupiter.api.Test;
import us.ihmc.jros2.ROS2Message;
import us.ihmc.jros2.parser.InterfaceContext;
import us.ihmc.jros2.parser.MsgContext;
import us.ihmc.jros2.parser.MsgParser;
import us.ihmc.jros2.parser.field.InterfaceFieldParsingException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RIHS01 type hashes (REP-2011) against ROS 2 Jazzy: canonical JSON, every bundled message class, the official-pipeline
 * edge cases (bounded/wide strings, the {@code wstring<=N[]} quirk, nested types, empty structures, comments, defaults)
 * and, when a Jazzy installation is present, all 190 message types of the required packages.
 */
public class TypeDescriptionHasherTest
{
   static final String CAPITALIZATION_TEST_HASH = "RIHS01_b8f4787bf859cbaa59b8930f2e859c6f87665636b241b0bdf52979a0192fd21a";

   /** section -> (pkg/msg/Type -> hash) from rihs01/test_vectors.txt */
   static Map<String, Map<String, String>> loadVectors()
   {
      Map<String, Map<String, String>> vectors = new HashMap<>();
      try (InputStream stream = TypeDescriptionHasherTest.class.getClassLoader().getResourceAsStream("rihs01/test_vectors.txt"))
      {
         assertNotNull(stream, "rihs01/test_vectors.txt");
         for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
         {
            if (line.isBlank() || line.startsWith("#"))
               continue;
            String[] parts = line.trim().split(" ");
            vectors.computeIfAbsent(parts[0], k -> new TreeMap<>()).put(parts[1], parts[2]);
         }
      }
      catch (IOException e)
      {
         throw new UncheckedIOException(e);
      }
      return vectors;
   }

   static Path edgePackages()
   {
      try
      {
         return Path.of(TypeDescriptionHasherTest.class.getClassLoader().getResource("rihs01/edge_pkgs").toURI());
      }
      catch (URISyntaxException e)
      {
         throw new RuntimeException(e);
      }
   }

   /** Resolves pkg/msg/Type from directories that contain pkg/msg/Type.msg, then from the bundled definitions. */
   static Function<String, InterfaceContext> resolver(boolean useBundled, Path... searchDirectories)
   {
      Map<String, String> bundled = useBundled ? jros2InterfaceGenerator.loadBundledDefinitions() : Map.of();
      Map<String, InterfaceContext> cache = new HashMap<>();
      return typeName -> cache.computeIfAbsent(typeName, name ->
      {
         String[] parts = name.split("/");
         try
         {
            for (Path directory : searchDirectories)
            {
               Path file = directory.resolve(parts[0]).resolve("msg").resolve(parts[2] + ".msg");
               if (Files.isRegularFile(file))
                  return jros2InterfaceGenerator.parseMsg(Files.readString(file), parts[0] + "/" + parts[2]);
            }
            String definition = bundled.get(name);
            return definition == null ? null : jros2InterfaceGenerator.parseMsg(definition, parts[0] + "/" + parts[2]);
         }
         catch (IOException e)
         {
            throw new UncheckedIOException(e);
         }
         catch (InterfaceFieldParsingException e)
         {
            throw new RuntimeException(e);
         }
      });
   }

   static Class<?> bundledClass(String typeName) throws ClassNotFoundException
   {
      String[] parts = typeName.split("/");
      try
      {
         return Class.forName(parts[0] + "." + parts[2] + "_"); // java.lang name clash, e.g. std_msgs.String_
      }
      catch (ClassNotFoundException e)
      {
         return Class.forName(parts[0] + "." + parts[2]);
      }
   }

   @Test
   public void testCanonicalJson()
   {
      TypeDescriptionHasher hasher = new TypeDescriptionHasher(resolver(true));

      assertEquals("{\"type_description\": {\"type_name\": \"std_msgs/msg/String\", \"fields\": [{\"name\": \"data\", \"type\": {\"type_id\": 17, "
                   + "\"capacity\": 0, \"string_capacity\": 0, \"nested_type_name\": \"\"}}]}, \"referenced_type_descriptions\": []}",
                   hasher.hashableJson("std_msgs/msg/String"));
      assertEquals("RIHS01_df668c740482bbd48fb39d76a70dfd4bd59db1288021743503259e948f6b1a18", hasher.hash("std_msgs/msg/String"));

      assertEquals("{\"type_description\": {\"type_name\": \"std_msgs/msg/Empty\", \"fields\": [{\"name\": \"structure_needs_at_least_one_member\", "
                   + "\"type\": {\"type_id\": 3, \"capacity\": 0, \"string_capacity\": 0, \"nested_type_name\": \"\"}}]}, "
                   + "\"referenced_type_descriptions\": []}", hasher.hashableJson("std_msgs/msg/Empty"));

      String pose = "{\"type_description\": {\"type_name\": \"geometry_msgs/msg/PoseWithCovariance\", \"fields\": [{\"name\": \"pose\", \"type\": {\"type_id\": "
                    + "1, \"capacity\": 0, \"string_capacity\": 0, \"nested_type_name\": \"geometry_msgs/msg/Pose\"}}, {\"name\": \"covariance\", \"type\": "
                    + "{\"type_id\": 59, \"capacity\": 36, \"string_capacity\": 0, \"nested_type_name\": \"\"}}]}, \"referenced_type_descriptions\": "
                    + "[{\"type_name\": \"geometry_msgs/msg/Point\", \"fields\": [{\"name\": \"x\", \"type\": {\"type_id\": 11, \"capacity\": 0, "
                    + "\"string_capacity\": 0, \"nested_type_name\": \"\"}}, {\"name\": \"y\", \"type\": {\"type_id\": 11, \"capacity\": 0, "
                    + "\"string_capacity\": 0, \"nested_type_name\": \"\"}}, {\"name\": \"z\", \"type\": {\"type_id\": 11, \"capacity\": 0, "
                    + "\"string_capacity\": 0, \"nested_type_name\": \"\"}}]}, {\"type_name\": \"geometry_msgs/msg/Pose\", \"fields\": [{\"name\": "
                    + "\"position\", \"type\": {\"type_id\": 1, \"capacity\": 0, \"string_capacity\": 0, \"nested_type_name\": "
                    + "\"geometry_msgs/msg/Point\"}}, {\"name\": \"orientation\", \"type\": {\"type_id\": 1, \"capacity\": 0, \"string_capacity\": 0, "
                    + "\"nested_type_name\": \"geometry_msgs/msg/Quaternion\"}}]}, {\"type_name\": \"geometry_msgs/msg/Quaternion\", \"fields\": "
                    + "[{\"name\": \"x\", \"type\": {\"type_id\": 11, \"capacity\": 0, \"string_capacity\": 0, \"nested_type_name\": \"\"}}, "
                    + "{\"name\": \"y\", \"type\": {\"type_id\": 11, \"capacity\": 0, \"string_capacity\": 0, \"nested_type_name\": \"\"}}, "
                    + "{\"name\": \"z\", \"type\": {\"type_id\": 11, \"capacity\": 0, \"string_capacity\": 0, \"nested_type_name\": \"\"}}, "
                    + "{\"name\": \"w\", \"type\": {\"type_id\": 11, \"capacity\": 0, \"string_capacity\": 0, \"nested_type_name\": \"\"}}]}]}";
      assertEquals(pose, hasher.hashableJson("geometry_msgs/msg/PoseWithCovariance"));
      assertEquals("RIHS01_9a7c0fd234b7f45c6098745ecccd773ca1085670e64107135397aee31c02e1bb", TypeDescriptionHasher.hashOfJson(pose));
   }

   @Test
   public void testEveryBundledClassCarriesTheExpectedHash() throws Exception
   {
      Map<String, Map<String, String>> vectors = loadVectors();
      Map<String, String> jazzy = vectors.get("jazzy");
      Map<String, String> edge = vectors.get("edge");
      Map<String, String> bundledDefinitions = jros2InterfaceGenerator.loadBundledDefinitions();
      TypeDescriptionHasher bundledHasher = new TypeDescriptionHasher(resolver(true));

      assertEquals(179, bundledDefinitions.size(), "bundled definitions resource");
      int matchedJazzy = 0;
      for (String typeName : bundledDefinitions.keySet())
      {
         String classHash = ROS2Message.getTypeHashFromMessageClass(bundledClass(typeName));
         assertNotNull(classHash, typeName);
         assertEquals(bundledHasher.hash(typeName), classHash, "bundled definitions and generated class disagree for " + typeName);

         String expected;
         if (typeName.equals("sensor_msgs/msg/Range"))
         {
            // jros2 bundles the humble Range (no 'variance'), so it advertises the hash of what it actually serializes
            expected = vectors.get("humble").get(typeName);
            assertNotEquals(jazzy.get(typeName), classHash);
         }
         else if (typeName.equals("jros2_example_interfaces/msg/CapitalizationTest"))
         {
            expected = CAPITALIZATION_TEST_HASH; // rejected by rosidl_adapter ('testTest2'), computed by the reference script
         }
         else
         {
            expected = jazzy.containsKey(typeName) ? jazzy.get(typeName) : edge.get(typeName);
            if (jazzy.containsKey(typeName))
               matchedJazzy++;
         }
         assertNotNull(expected, "no reference hash for " + typeName);
         assertEquals(expected, classHash, typeName);
      }
      assertEquals(173, matchedJazzy, "all bundled types except Range and jros2_example_interfaces equal Jazzy");
   }

   @Test
   public void testOfficialPipelineEdgeCases()
   {
      TypeDescriptionHasher hasher = new TypeDescriptionHasher(resolver(true, edgePackages()));
      int checked = 0;
      for (Map.Entry<String, String> entry : loadVectors().get("edge").entrySet())
      {
         if (entry.getKey().startsWith("rihs_"))
         {
            assertEquals(entry.getValue(), hasher.hash(entry.getKey()), entry.getKey());
            checked++;
         }
      }
      assertEquals(14, checked);
   }

   @Test
   public void testFieldTypeIdsAndWStringQuirk() throws Exception
   {
      MsgContext strings = jros2InterfaceGenerator.parseMsg(Files.readString(edgePackages().resolve("rihs_edge/msg/Strings.msg")), "rihs_edge/Strings");
      Map<String, TypeDescriptionHasher.FieldDescription> fields = new HashMap<>();
      for (TypeDescriptionHasher.FieldDescription field : TypeDescriptionHasher.describe(strings))
         fields.put(field.getName(), field);

      assertField(fields.get("n"), 118, 9, 9); // wstring<=9[]: rosidl_parser parses it as a bounded sequence of 9
      assertField(fields.get("j"), 118, 2, 3); // wstring<=3[<=2]
      assertField(fields.get("l"), 165, 0, 8); // string<=8[]
      assertField(fields.get("f"), 69, 3, 5); // string<=5[3]
      assertField(fields.get("m"), 162, 0, 0); // wstring[]

      MsgContext primitives = MsgParser.parseMsg("char c\nbyte b\nchar[3] ca\nbyte[] bs\nbool[<=2] bb\n", "vpkg/CB");
      List<TypeDescriptionHasher.FieldDescription> described = TypeDescriptionHasher.describe(primitives);
      assertField(described.get(0), 3, 0, 0); // char is uint8
      assertField(described.get(1), 16, 0, 0); // byte is octet
      assertField(described.get(2), 51, 3, 0);
      assertField(described.get(3), 160, 0, 0);
      assertField(described.get(4), 111, 2, 0);

      List<TypeDescriptionHasher.FieldDescription> empty = TypeDescriptionHasher.describe(MsgParser.parseMsg("int32 A=1\n", "vpkg/C"));
      assertEquals(1, empty.size());
      assertEquals(TypeDescriptionHasher.EMPTY_STRUCTURE_MEMBER_NAME, empty.get(0).getName());
      assertField(empty.get(0), 3, 0, 0);
   }

   private static void assertField(TypeDescriptionHasher.FieldDescription field, int typeId, long capacity, long stringCapacity)
   {
      assertEquals(typeId, field.getTypeId(), field.getName() + " type_id");
      assertEquals(capacity, field.getCapacity(), field.getName() + " capacity");
      assertEquals(stringCapacity, field.getStringCapacity(), field.getName() + " string_capacity");
   }

   @Test
   public void testUnresolvedReference() throws Exception
   {
      MsgContext context = MsgParser.parseMsg("std_msgs/Header header\nunknown_pkg/Thing thing\n", "user_pkg/UsesUnknown");
      TypeDescriptionHasher hasher = new TypeDescriptionHasher(resolver(true));
      TypeDescriptionHasher.UnresolvedTypeException exception = assertThrows(TypeDescriptionHasher.UnresolvedTypeException.class, () -> hasher.hash(context));
      assertTrue(exception.getMessage().contains("unknown_pkg/msg/Thing"), exception.getMessage());
      assertTrue(exception.getMessage().contains("user_pkg/msg/UsesUnknown"), exception.getMessage());

      // Transitive: a resolvable type that references an unresolvable one
      Map<String, InterfaceContext> types = new HashMap<>();
      types.put("a_pkg/msg/A", MsgParser.parseMsg("b_pkg/B b\n", "a_pkg/A"));
      TypeDescriptionHasher transitive = new TypeDescriptionHasher(types::get);
      exception = assertThrows(TypeDescriptionHasher.UnresolvedTypeException.class,
                               () -> transitive.hash(MsgParser.parseMsg("a_pkg/A a\n", "top_pkg/Top")));
      assertTrue(exception.getMessage().contains("b_pkg/msg/B (referenced by a_pkg/msg/A)"), exception.getMessage());
   }

   @Test
   public void testJazzyInstallationIfAvailable()
   {
      Path share = Path.of("/opt/ros/jazzy/share");
      Assumptions.assumeTrue(Files.isRegularFile(share.resolve("std_msgs/msg/String.msg")), "ROS 2 Jazzy is not installed");

      // No bundled definitions: everything is resolved from the Jazzy installation
      TypeDescriptionHasher hasher = new TypeDescriptionHasher(resolver(false, share));
      Map<String, String> jazzy = loadVectors().get("jazzy");
      for (Map.Entry<String, String> entry : jazzy.entrySet())
      {
         assertEquals(entry.getValue(), hasher.hash(entry.getKey()), entry.getKey());
      }
      assertEquals(190, jazzy.size());
   }
}
