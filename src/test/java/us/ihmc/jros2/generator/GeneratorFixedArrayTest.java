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
import org.junit.jupiter.api.io.TempDir;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.jros2.ROS2Message;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fixed-size arrays of every primitive built-in type, which the generated code writes and reads with the bulk
 * {@link CDRBuffer} array methods (the bundled interfaces and the other generator tests do not use all of them, e.g.
 * {@code uint16[N]}): the generated class must compile, encode exactly like the element-wise CDR methods, and decode its own
 * encoding.
 */
public class GeneratorFixedArrayTest
{
   private static final String ALL_FIXED_MSG = """
                                               bool[2] b [true, false]
                                               byte[3] y [1, 2, 255]
                                               char[2] c [65, 200]
                                               int8[2] i8 [-1, 127]
                                               uint8[3] u8 [0, 128, 255]
                                               int16[2] i16 [-2, 300]
                                               uint16[3] u16 [1, 2, 65535]
                                               int32[2] i32 [-3, 70000]
                                               uint32[2] u32 [1, 4294967295]
                                               int64[2] i64 [-4, 5000000000]
                                               uint64[2] u64 [1, 18446744073709551615]
                                               float32[2] f32 [1.5, -2.25]
                                               float64[2] f64 [0.125, -1e300]
                                               """;

   private static String classPath() throws Exception
   {
      return Path.of(ROS2Message.class.getProtectionDomain().getCodeSource().getLocation().toURI()) + File.pathSeparator
             + Path.of(CDRBuffer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
   }

   /** The encoding of the default values above, written element by element. */
   private static byte[] expectedEncoding()
   {
      CDRBuffer buffer = new CDRBuffer();
      buffer.writePayloadHeader();
      buffer.writeBoolean(true);
      buffer.writeBoolean(false);
      for (int value : new int[] {1, 2, 255})
         buffer.writeByte((byte) value);
      buffer.writeChar((char) 65);
      buffer.writeChar((char) 200);
      buffer.writeByte((byte) -1);
      buffer.writeByte((byte) 127);
      for (int value : new int[] {0, 128, 255})
         buffer.writeByte((byte) value);
      buffer.writeShort((short) -2);
      buffer.writeShort((short) 300);
      for (int value : new int[] {1, 2, 65535})
         buffer.writeUInt16(value);
      buffer.writeInt(-3);
      buffer.writeInt(70000);
      buffer.writeInt(1);
      buffer.writeInt(-1);
      buffer.writeLong(-4);
      buffer.writeLong(5000000000L);
      buffer.writeLong(1);
      buffer.writeLong(-1);
      buffer.writeFloat(1.5f);
      buffer.writeFloat(-2.25f);
      buffer.writeDouble(0.125);
      buffer.writeDouble(-1e300);
      return bytes(buffer);
   }

   private static byte[] bytes(CDRBuffer buffer)
   {
      byte[] bytes = new byte[buffer.getBufferUnsafe().position()];
      for (int i = 0; i < bytes.length; ++i)
         bytes[i] = buffer.getBufferUnsafe().get(i);
      return bytes;
   }

   private static byte[] serialize(ROS2Message<?> message)
   {
      CDRBuffer buffer = new CDRBuffer();
      buffer.writePayloadHeader();
      message.serialize(buffer);
      byte[] bytes = bytes(buffer);
      assertEquals(bytes.length - 4, message.calculateSizeBytes(0));
      return bytes;
   }

   @Test
   public void testFixedArraysOfEveryPrimitiveType(@TempDir Path directory) throws Exception
   {
      GeneratorGoldenTest.writePackage(directory.resolve("fixed_pkg"), Map.of("AllFixed.msg", ALL_FIXED_MSG));
      Path generated = directory.resolve("generated");
      assertEquals(1, new jros2InterfaceGenerator().addPackagePath(directory.resolve("fixed_pkg")).setOutputDir(generated).generate());

      JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
      Assumptions.assumeTrue(compiler != null, "No system Java compiler");
      List<String> arguments = new ArrayList<>(List.of("-encoding", "UTF-8", "-proc:none", "-d", directory.resolve("classes").toString(), "-classpath", classPath()));
      arguments.add(generated.resolve("fixed_pkg/AllFixed.java").toString());
      ByteArrayOutputStream errors = new ByteArrayOutputStream();
      assertEquals(0, compiler.run(null, null, errors, arguments.toArray(new String[0])), "Generated sources must compile:\n" + errors.toString(StandardCharsets.UTF_8));

      try (URLClassLoader classLoader = new URLClassLoader(new URL[] {directory.resolve("classes").toUri().toURL()}, getClass().getClassLoader()))
      {
         Class<?> type = classLoader.loadClass("fixed_pkg.AllFixed");
         ROS2Message<?> message = (ROS2Message<?>) type.getDeclaredConstructor().newInstance();
         byte[] encoded = serialize(message);
         assertArrayEquals(expectedEncoding(), encoded, "the default values, encoded like the element-wise CDR methods do");

         ROS2Message<?> decoded = (ROS2Message<?>) type.getDeclaredConstructor().newInstance();
         CDRBuffer buffer = new CDRBuffer();
         buffer.ensureRemainingCapacity(encoded.length);
         buffer.getBufferUnsafe().put(encoded);
         buffer.beginRead(encoded.length);
         buffer.readPayloadHeader();
         decoded.deserialize(buffer);
         assertEquals(encoded.length, buffer.getBufferUnsafe().position());
         assertArrayEquals(encoded, serialize(decoded));
         assertArrayEquals(new int[] {1, 2, 65535}, (int[]) type.getMethod("getU16").invoke(decoded), "uint16 values are unsigned");
      }
   }
}
