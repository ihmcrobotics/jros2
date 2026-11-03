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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the string constants ({@code CONSTANT_String} entries of the constant pool) of a class file, to check that HotSpot
 * never has to create one of them later: the literals of its {@code ldc} instructions and the recipes of its string
 * concatenations, also those inside its lambdas.
 * <p>
 * Two rules use it: {@code HotPathClassesTest} requires that the classes of jros2's publish/receive paths (runtime, CDR,
 * IDL sequences) have no string constant at all ({@link #of(String)}), and {@code StringConstantsAllocationTest} that
 * every constant of the generated message classes is resolved when the class is initialized or constructed
 * ({@link #unresolvedStrings(boolean)}).
 * </p>
 * <p>
 * Background: when a thread first requests a C2 compilation of a method (its invocation counter overflows, e.g. after
 * about 5000 calls), HotSpot resolves every string literal ({@code CONSTANT_String}) of the method's class that has not
 * been resolved yet ({@code CompileBroker::compile_method} calls {@code ConstantPool::resolve_string_constants}): it
 * creates and interns a {@link String} (and its byte array) for each one, on that thread. For a class whose error
 * messages or {@code toString()} labels are string literals, this is an allocation on a control thread in the middle of
 * its steady state, invisible to bytecode instrumentation. A literal costs nothing there when its string already exists
 * in the string table: compile-time constants ({@code static final String} fields with a {@code ConstantValue}
 * attribute) are created when the class is initialized, and literals loaded by {@code ldc} in the static initializer
 * (or, for generated messages, in the constructor, which always runs before any other method) are resolved then.
 * </p>
 */
public final class ClassFileStrings
{
   /** String constants of the class, by constant pool index. */
   private final Map<Integer, String> strings = new LinkedHashMap<>();
   /** Constant pool indices of the strings that are the ConstantValue of a static field. */
   private final Set<Integer> constantValues = new HashSet<>();
   /** Constant pool indices of the strings loaded by ldc in the static initializer. */
   private final Set<Integer> loadedInStaticInitializer = new HashSet<>();
   /** Constant pool indices of the strings loaded by ldc in a constructor. */
   private final Set<Integer> loadedInConstructors = new HashSet<>();
   private final String className;

   public ClassFileStrings(Class<?> type) throws IOException
   {
      this(type.getName(), classBytes(type));
   }

   public ClassFileStrings(String className, byte[] classBytes) throws IOException
   {
      this.className = className;
      parse(new DataInputStream(new ByteArrayInputStream(classBytes)));
   }

   private static byte[] classBytes(Class<?> type) throws IOException
   {
      String resource = type.getName().substring(type.getName().lastIndexOf('.') + 1) + ".class";
      try (InputStream stream = type.getResourceAsStream(resource))
      {
         if (stream == null)
            throw new IOException("class file of " + type.getName() + " not found");
         return stream.readAllBytes();
      }
   }

   /**
    * @param binaryName the binary name of a class on the class path, e.g. {@code us.ihmc.jros2.ROS2Node$TrackedCallback}
    * @return the values of all its {@code CONSTANT_String} entries, in constant pool order
    * @throws IllegalArgumentException if the class file is not on the class path
    */
   public static List<String> of(String binaryName) throws IOException
   {
      String resource = binaryName.replace('.', '/') + ".class";
      try (InputStream in = ClassFileStrings.class.getClassLoader().getResourceAsStream(resource))
      {
         if (in == null)
            throw new IllegalArgumentException("No class file " + resource + " on the class path");
         ByteArrayOutputStream bytes = new ByteArrayOutputStream();
         in.transferTo(bytes);
         return parse(bytes.toByteArray());
      }
   }

   /**
    * @param classFile the bytes of a class file
    * @return the values of all its {@code CONSTANT_String} entries, in constant pool order
    */
   public static List<String> parse(byte[] classFile) throws IOException
   {
      return new ClassFileStrings("", classFile).allStrings();
   }

   public String getClassName()
   {
      return className;
   }

   /** @return the values of all {@code CONSTANT_String} entries, in constant pool order */
   public List<String> allStrings()
   {
      return new ArrayList<>(strings.values());
   }

   /** @return the string literals that HotSpot may still have to create when it first compiles a method of the class */
   public List<String> unresolvedStrings(boolean constructorsResolve)
   {
      List<String> unresolved = new ArrayList<>();
      for (Map.Entry<Integer, String> string : strings.entrySet())
      {
         int index = string.getKey();
         if (constantValues.contains(index) || loadedInStaticInitializer.contains(index) || (constructorsResolve && loadedInConstructors.contains(index)))
            continue;
         unresolved.add(string.getValue());
      }
      return unresolved;
   }

   public int getStringCount()
   {
      return strings.size();
   }

   private void parse(DataInputStream in) throws IOException
   {
      if (in.readInt() != 0xCAFEBABE)
         throw new IOException("not a class file: " + className);
      in.readUnsignedShort(); // minor
      in.readUnsignedShort(); // major

      int constantPoolCount = in.readUnsignedShort();
      String[] utf8 = new String[constantPoolCount];
      int[] stringUtf8Index = new int[constantPoolCount];
      for (int i = 1; i < constantPoolCount; i++)
      {
         int tag = in.readUnsignedByte();
         switch (tag)
         {
            case 1 -> utf8[i] = in.readUTF();
            case 3, 4 -> in.readInt();
            case 5, 6 ->
            {
               in.readLong();
               i++;
            }
            case 7, 16, 19, 20 -> in.readUnsignedShort();
            case 8 -> stringUtf8Index[i] = in.readUnsignedShort();
            case 9, 10, 11, 12, 17, 18 -> in.readInt();
            case 15 ->
            {
               in.readUnsignedByte();
               in.readUnsignedShort();
            }
            default -> throw new IOException("unknown constant pool tag " + tag + " in " + className);
         }
      }
      for (int i = 1; i < constantPoolCount; i++)
      {
         if (stringUtf8Index[i] != 0)
            strings.put(i, utf8[stringUtf8Index[i]]);
      }

      in.readUnsignedShort(); // access flags
      in.readUnsignedShort(); // this class
      in.readUnsignedShort(); // super class
      int interfaces = in.readUnsignedShort();
      for (int i = 0; i < interfaces; i++)
         in.readUnsignedShort();

      int fields = in.readUnsignedShort();
      for (int i = 0; i < fields; i++)
      {
         int accessFlags = in.readUnsignedShort();
         in.readUnsignedShort(); // name
         in.readUnsignedShort(); // descriptor
         int attributes = in.readUnsignedShort();
         for (int a = 0; a < attributes; a++)
         {
            String name = utf8[in.readUnsignedShort()];
            int length = in.readInt();
            if ("ConstantValue".equals(name) && (accessFlags & 0x0008) != 0)
            {
               int index = in.readUnsignedShort();
               if (stringUtf8Index[index] != 0)
                  constantValues.add(index);
            }
            else
            {
               in.readFully(new byte[length]);
            }
         }
      }

      int methods = in.readUnsignedShort();
      for (int i = 0; i < methods; i++)
      {
         in.readUnsignedShort(); // access flags
         String name = utf8[in.readUnsignedShort()];
         in.readUnsignedShort(); // descriptor
         int attributes = in.readUnsignedShort();
         for (int a = 0; a < attributes; a++)
         {
            String attributeName = utf8[in.readUnsignedShort()];
            byte[] attribute = new byte[in.readInt()];
            in.readFully(attribute);
            if ("Code".equals(attributeName))
            {
               if ("<clinit>".equals(name))
                  collectLdc(attribute, stringUtf8Index, loadedInStaticInitializer);
               else if ("<init>".equals(name))
                  collectLdc(attribute, stringUtf8Index, loadedInConstructors);
            }
         }
      }
   }

   /** Adds the constant pool indices of the strings that the bytecode of a Code attribute loads with ldc or ldc_w. */
   private void collectLdc(byte[] codeAttribute, int[] stringUtf8Index, Set<Integer> loaded) throws IOException
   {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(codeAttribute));
      in.readUnsignedShort(); // max stack
      in.readUnsignedShort(); // max locals
      byte[] code = new byte[in.readInt()];
      in.readFully(code);

      int pc = 0;
      while (pc < code.length)
      {
         int opcode = code[pc] & 0xFF;
         if (opcode == 0x12) // ldc
         {
            int index = code[pc + 1] & 0xFF;
            if (stringUtf8Index[index] != 0)
               loaded.add(index);
         }
         else if (opcode == 0x13) // ldc_w
         {
            int index = ((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF);
            if (stringUtf8Index[index] != 0)
               loaded.add(index);
         }
         pc += instructionLength(code, pc);
      }
   }

   private int instructionLength(byte[] code, int pc) throws IOException
   {
      int opcode = code[pc] & 0xFF;
      if (opcode == 0xAA || opcode == 0xAB) // tableswitch, lookupswitch
      {
         int operands = (pc + 4) & ~3; // aligned to 4 bytes from the start of the code
         if (opcode == 0xAA)
         {
            int low = readInt(code, operands + 4);
            int high = readInt(code, operands + 8);
            return operands + 12 + 4 * (high - low + 1) - pc;
         }
         int pairs = readInt(code, operands + 4);
         return operands + 8 + 8 * pairs - pc;
      }
      if (opcode == 0xC4) // wide
         return (code[pc + 1] & 0xFF) == 0x84 ? 6 : 4;
      if (opcode <= 0x0F || (opcode >= 0x1A && opcode <= 0x35) || (opcode >= 0x3B && opcode <= 0x83) || (opcode >= 0x85 && opcode <= 0x98)
          || (opcode >= 0xAC && opcode <= 0xB1) || opcode == 0xBE || opcode == 0xBF || opcode == 0xC2 || opcode == 0xC3)
         return 1;
      if (opcode == 0x10 || opcode == 0x12 || (opcode >= 0x15 && opcode <= 0x19) || (opcode >= 0x36 && opcode <= 0x3A) || opcode == 0xA9 || opcode == 0xBC)
         return 2;
      if (opcode == 0x11 || opcode == 0x13 || opcode == 0x14 || opcode == 0x84 || (opcode >= 0x99 && opcode <= 0xA8) || (opcode >= 0xB2 && opcode <= 0xB8)
          || opcode == 0xBB || opcode == 0xBD || opcode == 0xC0 || opcode == 0xC1 || opcode == 0xC6 || opcode == 0xC7)
         return 3;
      if (opcode == 0xC5)
         return 4;
      if (opcode == 0xB9 || opcode == 0xBA || opcode == 0xC8 || opcode == 0xC9)
         return 5;
      throw new IOException("unknown opcode 0x" + Integer.toHexString(opcode) + " in " + className);
   }

   private static int readInt(byte[] code, int offset)
   {
      return ((code[offset] & 0xFF) << 24) | ((code[offset + 1] & 0xFF) << 16) | ((code[offset + 2] & 0xFF) << 8) | (code[offset + 3] & 0xFF);
   }
}
