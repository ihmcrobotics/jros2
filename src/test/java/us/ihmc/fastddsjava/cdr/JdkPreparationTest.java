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
package us.ihmc.fastddsjava.cdr;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JdkPreparation}: what it does when {@link CDRBuffer} is initialized, and its class file reader. That it removes
 * the allocations it is there for is proven by the allocation gates (us.ihmc.jros2.allocation.messages.HotSpotAllocationTest
 * runs them in JVMs without the archived heap and without the JUnit engine).
 */
public class JdkPreparationTest
{
   @Test
   public void testPreparedWhenCDRBufferIsInitialized()
   {
      new CDRBuffer();
      // The JDK classes under the CDR code have hundreds of string constants (java.nio.Buffer alone has a dozen)
      assertTrue(JdkPreparation.getInternedConstantCount() > 100, "interned " + JdkPreparation.getInternedConstantCount());
   }

   @Test
   public void testEveryConstantKind() throws IOException
   {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(0xCAFEBABE);
      out.writeShort(0);
      out.writeShort(61);
      out.writeShort(22); // constant pool count: entries 1..21
      out.writeByte(1); // 1: Utf8
      out.writeUTF("jros2 € constant");
      out.writeByte(8); // 2: String -> 1
      out.writeShort(1);
      out.writeByte(3); // 3: Integer
      out.writeInt(42);
      out.writeByte(4); // 4: Float
      out.writeFloat(1.5f);
      out.writeByte(5); // 5, 6: Long
      out.writeLong(7L);
      out.writeByte(6); // 7, 8: Double
      out.writeDouble(2.5);
      out.writeByte(7); // 9: Class
      out.writeShort(1);
      out.writeByte(9); // 10: Fieldref
      out.writeInt(0x00090010);
      out.writeByte(10); // 11: Methodref
      out.writeInt(0x00090010);
      out.writeByte(11); // 12: InterfaceMethodref
      out.writeInt(0x00090010);
      out.writeByte(12); // 13: NameAndType
      out.writeInt(0x00010001);
      out.writeByte(15); // 14: MethodHandle
      out.writeByte(6);
      out.writeShort(11);
      out.writeByte(16); // 15: MethodType
      out.writeShort(1);
      out.writeByte(17); // 16: Dynamic
      out.writeInt(0x00000013);
      out.writeByte(18); // 17: InvokeDynamic
      out.writeInt(0x00000013);
      out.writeByte(19); // 18: Module
      out.writeShort(1);
      out.writeByte(20); // 19: Package
      out.writeShort(1);
      out.writeByte(8); // 20: String -> 21 (a forward reference)
      out.writeShort(21);
      out.writeByte(1); // 21: Utf8
      out.writeUTF("forward");
      out.flush();

      List<String> constants = read(bytes.toByteArray());
      assertEquals(List.of("jros2 € constant", "forward"), constants);
      // Interned: a literal with the same content is the same object
      assertSame("forward", constants.get(1));
   }

   @Test
   public void testUnknownConstantAndWrongMagic() throws IOException
   {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(0xCAFEBABE);
      out.writeShort(0);
      out.writeShort(61);
      out.writeShort(5);
      out.writeByte(1); // 1: Utf8
      out.writeUTF("before");
      out.writeByte(8); // 2: String -> 1
      out.writeShort(1);
      out.writeByte(99); // 3: unknown: reading stops, what was read is kept
      out.writeByte(8); // 4: never read
      out.writeShort(1);
      out.flush();
      assertEquals(List.of("before"), read(bytes.toByteArray()));

      byte[] notAClassFile = {1, 2, 3, 4, 5, 6, 7, 8};
      assertEquals(List.of(), read(notAClassFile));
   }

   private static List<String> read(byte[] classFile) throws IOException
   {
      List<String> constants = new ArrayList<>();
      JdkPreparation.readStringConstants(new DataInputStream(new ByteArrayInputStream(classFile)), constants);
      return constants;
   }
}
