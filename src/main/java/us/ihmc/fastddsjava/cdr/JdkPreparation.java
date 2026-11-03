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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Does, once, when {@link CDRBuffer} is initialized (during setup), what HotSpot would otherwise do on a hot thread the
 * first time its C2 compiler is asked to compile a JDK method that the CDR code runs.
 * <p>
 * Before C2 compiles a method, the thread that requests the compilation (the thread that ran the method often enough:
 * a publishing or receiving thread) prepares the method's class (CompileBroker::compile_method):
 * </p>
 * <ul>
 *    <li>it loads every class of the method's signature (Method::load_signature_classes). A class that is not loaded yet
 *    and not in the class data sharing archive gets its {@code java.lang.Class} object allocated there: on JDK 17 the
 *    first C2 compilation of {@code ScopedMemoryAccess.putIntUnaligned(ScopedMemoryAccess.Scope, Object, long, int,
 *    boolean)}, on the path of every {@link CDRBuffer#writeInt(int)}, loads {@code ScopedMemoryAccess$Scope}: 128 bytes
 *    on the publishing thread, tens of thousands of writes into a fresh JVM. Listing the declared methods of a class makes
 *    the JVM load the parameter and return types of all of them; the buffer classes declare the accessor of that first
 *    parameter ({@code Buffer.scope()} on JDK 17, {@code Buffer.session()} on later JDKs), so no JDK-internal class is
 *    named for this.</li>
 *    <li>it creates the string literals of the class that are not resolved yet (ConstantPool::resolve_string_constants).
 *    With the JDK's class data sharing archive mapped with its heap objects (G1 with compressed oops, the defaults) the
 *    JDK classes come with their literals resolved; with any other collector, a heap of 32 GB or more, or -Xshare:off,
 *    the first C2 compilation of a method of, e.g., {@code java.nio.ByteBuffer} creates its unresolved literals (6 of 7:
 *    368 bytes) on the requesting thread. A literal is created only if the string table has no equal string yet: the string constants of
 *    the JDK classes under the CDR code are interned here, and kept, so that these resolutions find them.</li>
 * </ul>
 * <p>
 * jros2 prepares the JDK classes under its own publish and receive paths the same way, through
 * {@link #internStringConstants(String[])} (us.ihmc.jros2.JdkStringConstants, when the first participant is created).
 * Internal: not part of the API.
 * </p>
 * <p>
 * Best effort: whatever cannot be loaded or read is skipped (Android has no such compiler, and no class files).
 * </p>
 */
public final class JdkPreparation
{
   private static final String CLASS_FILE_EXTENSION = ".class";
   private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;
   private static final int CONSTANT_UTF8 = 1;
   private static final int CONSTANT_STRING = 8;

   /**
    * JDK classes whose methods run under the CDR code (measured on JDK 17 with -XX:+PrintCompilation), besides the buffer
    * classes and {@link StringBuilder}, which are found from the objects themselves. Names that do not exist are skipped.
    */
   private static final String[] CALLEES = {"java.nio.Bits",
                                            "java.nio.ByteOrder",
                                            "jdk.internal.misc.Unsafe",
                                            "jdk.internal.misc.ScopedMemoryAccess",
                                            "jdk.internal.util.Preconditions",
                                            "java.lang.Object",
                                            "java.lang.System",
                                            "java.lang.Math",
                                            "java.lang.StrictMath",
                                            "java.lang.String",
                                            "java.lang.StringLatin1",
                                            "java.lang.StringUTF16",
                                            "java.lang.Character",
                                            "java.lang.CharacterData",
                                            "java.lang.CharacterDataLatin1",
                                            "java.lang.Boolean",
                                            "java.lang.Byte",
                                            "java.lang.Short",
                                            "java.lang.Integer",
                                            "java.lang.Long",
                                            "java.lang.Float",
                                            "java.lang.Double",
                                            "java.util.Arrays",
                                            "java.util.Objects"};

   /** Strong references to the interned constants: the JVM's string table alone would let them be collected. */
   private static String[] internedConstants = new String[0];

   private JdkPreparation()
   {
   }

   static void prepare()
   {
      Set<Class<?>> classes = new LinkedHashSet<>();
      try
      {
         ByteBuffer buffer = ByteBuffer.allocateDirect(Long.BYTES);
         addWithSuperclasses(classes, buffer.getClass());
         for (ByteOrder order : new ByteOrder[] {ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN})
         {
            buffer.order(order);
            addWithSuperclasses(classes, buffer.asShortBuffer().getClass());
            addWithSuperclasses(classes, buffer.asIntBuffer().getClass());
            addWithSuperclasses(classes, buffer.asLongBuffer().getClass());
            addWithSuperclasses(classes, buffer.asFloatBuffer().getClass());
            addWithSuperclasses(classes, buffer.asDoubleBuffer().getClass());
         }
         addWithSuperclasses(classes, StringBuilder.class);

         for (Class<?> type : classes)
         {
            // The parameter and return types of every declared method
            type.getDeclaredMethods();
         }
      }
      catch (RuntimeException | LinkageError e)
      {
         // Only an optimization for HotSpot: the CDR code works the same without it
      }

      Set<String> classNames = new LinkedHashSet<>();
      for (Class<?> type : classes)
      {
         classNames.add(type.getName());
      }
      for (String callee : CALLEES)
      {
         classNames.add(callee);
      }
      internedConstants = internStringConstants(classNames.toArray(new String[0]));
   }

   /**
    * Interns the string constants ({@code CONSTANT_String} entries) of the class files of the given JDK classes. HotSpot
    * then finds them in its string table when it resolves the constants of these classes at their first C2 compilation,
    * instead of creating them on the thread that requested the compilation. Classes that do not exist or whose class file
    * cannot be read (Android) are skipped.
    *
    * @param classNames binary names of JDK classes (resources of java.base)
    * @return the interned constants: the caller must keep a strong reference to them, the JVM's string table alone would
    *       let them be collected
    */
   public static String[] internStringConstants(String[] classNames)
   {
      List<String> constants = new ArrayList<>();
      for (String className : classNames)
      {
         internStringConstants(className, constants);
      }
      return constants.toArray(new String[0]);
   }

   /** @return the number of string constants interned and kept */
   static int getInternedConstantCount()
   {
      return internedConstants.length;
   }

   private static void addWithSuperclasses(Set<Class<?>> classes, Class<?> type)
   {
      for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
      {
         classes.add(c);
      }
   }

   /** Reads the class file of a JDK class (a resource of java.base) and interns its CONSTANT_String entries. */
   private static void internStringConstants(String className, List<String> constants)
   {
      // No string concatenation: its recipe would be a string literal of this class (see CDRBuffer)
      String resource = new StringBuilder().append('/').append(className.replace('.', '/')).append(CLASS_FILE_EXTENSION).toString();
      try (InputStream classFile = Object.class.getResourceAsStream(resource))
      {
         if (classFile != null)
         {
            readStringConstants(new DataInputStream(classFile), constants);
         }
      }
      catch (IOException | RuntimeException e)
      {
         // Skipped: only an optimization for HotSpot
      }
   }

   static void readStringConstants(DataInputStream in, List<String> constants) throws IOException
   {
      if (in.readInt() != CLASS_FILE_MAGIC)
      {
         return;
      }
      in.readUnsignedShort(); // minor version
      in.readUnsignedShort(); // major version
      int count = in.readUnsignedShort();
      String[] utf8 = new String[count];
      int[] stringIndices = new int[count];
      int strings = 0;
      for (int i = 1; i < count; i++)
      {
         int tag = in.readUnsignedByte();
         switch (tag)
         {
            case CONSTANT_UTF8:
               utf8[i] = in.readUTF();
               break;
            case CONSTANT_STRING:
               stringIndices[strings++] = in.readUnsignedShort();
               break;
            case 7: // Class
            case 16: // MethodType
            case 19: // Module
            case 20: // Package
               in.readUnsignedShort();
               break;
            case 15: // MethodHandle
               in.readUnsignedByte();
               in.readUnsignedShort();
               break;
            case 3: // Integer
            case 4: // Float
            case 9: // Fieldref
            case 10: // Methodref
            case 11: // InterfaceMethodref
            case 12: // NameAndType
            case 17: // Dynamic
            case 18: // InvokeDynamic
               in.readInt();
               break;
            case 5: // Long
            case 6: // Double
               in.readLong();
               i++;
               break;
            default:
               // A constant this reader does not know: stop, keeping what was read
               count = i;
               break;
         }
      }
      for (int i = 0; i < strings; i++)
      {
         String value = utf8[stringIndices[i]];
         if (value != null)
         {
            constants.add(value.intern());
         }
      }
   }
}
