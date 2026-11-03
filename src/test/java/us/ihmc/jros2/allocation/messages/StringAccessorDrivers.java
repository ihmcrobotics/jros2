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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Writes and compiles call drivers for the string convenience accessors that generated messages have for every
 * non-array string field: {@code setX(String)}, {@code setX(CharSequence)}, {@code getX()} (the StringBuilder) and
 * {@code getXAsString()}. The drivers call them directly (compiled code, no reflection in the measured region).
 * <p>
 * The setters driver, per field: {@code setX(ascii)}, {@code setX(unicode)} (non-Latin-1 and a surrogate pair),
 * {@code setX(builder)} (the CharSequence overload), {@code setX(getX())} (self-assignment keeps the content) and
 * {@code getX().length()}. The as-string driver calls every {@code getXAsString()}, which returns a new String and so
 * allocates by nature.
 * </p>
 */
public final class StringAccessorDrivers
{
   /** The content every string field holds after a run of the setters driver. */
   public static final String BUILDER_CONTENT = "büilder_€";

   private final List<Class<?>> types = new ArrayList<>();
   private final List<String> fieldAccessors = new ArrayList<>(); // "type index:accessor base name", e.g. "3:FrameId"
   private final Object[] messages;
   private final ToIntFunction<Object[]> setters;
   private final ToIntFunction<Object[]> asStrings;
   private final URLClassLoader classLoader;

   /**
    * @param messageTypes   generated message classes; the ones without a non-array string field are ignored
    * @param workDirectory  an empty directory for the driver sources and classes
    * @param parent         a class loader that sees {@code messageTypes}
    * @param extraClassPath the class directories of {@code messageTypes} that are not on the test class path
    */
   public StringAccessorDrivers(List<Class<?>> messageTypes, Path workDirectory, ClassLoader parent, Path... extraClassPath) throws Exception
   {
      StringBuilder setterCalls = new StringBuilder();
      StringBuilder asStringCalls = new StringBuilder();
      for (Class<?> type : messageTypes)
      {
         List<String> accessors = stringAccessors(type);
         if (accessors.isEmpty())
            continue;

         int index = types.size();
         types.add(type);
         String typeName = type.getCanonicalName();
         setterCalls.append("      {\n         ").append(typeName).append(" message = (").append(typeName).append(") messages[").append(index).append("];\n");
         asStringCalls.append("      {\n         ").append(typeName).append(" message = (").append(typeName).append(") messages[").append(index).append("];\n");
         for (String accessor : accessors)
         {
            fieldAccessors.add(index + ":" + accessor);
            setterCalls.append("         message.set").append(accessor).append("(ascii);\n");
            setterCalls.append("         message.set").append(accessor).append("(unicode);\n");
            setterCalls.append("         message.set").append(accessor).append("(builder);\n");
            setterCalls.append("         message.set").append(accessor).append("(message.get").append(accessor).append("());\n");
            setterCalls.append("         checksum += message.get").append(accessor).append("().length();\n");
            asStringCalls.append("         checksum += message.get").append(accessor).append("AsString().length();\n");
         }
         setterCalls.append("      }\n");
         asStringCalls.append("      }\n");
      }

      Path sources = workDirectory.resolve("driver-sources").resolve("jros2_alloc_driver");
      Files.createDirectories(sources);
      Files.writeString(sources.resolve("StringSetters.java"), driverSource("StringSetters", setterCalls), StandardCharsets.UTF_8);
      Files.writeString(sources.resolve("StringGetters.java"), driverSource("StringGetters", asStringCalls), StandardCharsets.UTF_8);
      classLoader = TestSourceCompiler.compileAndLoad(sources.getParent(), workDirectory.resolve("driver-classes"), parent, extraClassPath);
      setters = newDriver("StringSetters");
      asStrings = newDriver("StringGetters");

      messages = new Object[types.size()];
      for (int i = 0; i < messages.length; i++)
         messages[i] = types.get(i).getDeclaredConstructor().newInstance();
   }

   private static String driverSource(String className, StringBuilder calls)
   {
      return """
             package jros2_alloc_driver;

             public final class %s implements java.util.function.ToIntFunction<Object[]>
             {
                private final String ascii = "ascii_frame_42";
                private final String unicode = "fr\\u00e2me_\\u20ac_\\uD83D\\uDE00";
                private final StringBuilder builder = new StringBuilder("%s");

                @Override
                public int applyAsInt(Object[] messages)
                {
                   int checksum = 0;
             %s      return checksum;
                }
             }
             """.formatted(className, BUILDER_CONTENT, calls);
   }

   @SuppressWarnings("unchecked")
   private ToIntFunction<Object[]> newDriver(String simpleName) throws ReflectiveOperationException
   {
      return (ToIntFunction<Object[]>) classLoader.loadClass("jros2_alloc_driver." + simpleName).getDeclaredConstructor().newInstance();
   }

   /**
    * Returns the accessor base names (e.g. {@code FrameId}) of the non-array string fields of {@code type}, after
    * checking that each one has the four accessors.
    */
   public static List<String> stringAccessors(Class<?> type)
   {
      List<String> accessors = new ArrayList<>();
      for (Method method : type.getDeclaredMethods())
      {
         if (!Modifier.isPublic(method.getModifiers()) || !method.getName().startsWith("set") || method.getParameterCount() != 1
             || method.getParameterTypes()[0] != String.class)
            continue;

         String accessor = method.getName().substring(3);
         try
         {
            assertEquals(void.class, type.getMethod("set" + accessor, CharSequence.class).getReturnType());
            assertEquals(StringBuilder.class, type.getMethod("get" + accessor).getReturnType());
            assertEquals(String.class, type.getMethod("get" + accessor + "AsString").getReturnType());
         }
         catch (NoSuchMethodException e)
         {
            fail(type.getName() + ": set" + accessor + "(String) without the matching set" + accessor + "(CharSequence), get" + accessor + "() or get"
                 + accessor + "AsString()", e);
         }
         accessors.add(accessor);
      }
      accessors.sort(null);
      return accessors;
   }

   public int getTypeCount()
   {
      return types.size();
   }

   public int getFieldCount()
   {
      return fieldAccessors.size();
   }

   public int runSetters()
   {
      return setters.applyAsInt(messages);
   }

   public int runAsStringGetters()
   {
      return asStrings.applyAsInt(messages);
   }

   /** Checks (with reflection, outside any measured region) that every field holds {@link #BUILDER_CONTENT}. */
   public void assertSetterResults() throws ReflectiveOperationException
   {
      for (String fieldAccessor : fieldAccessors)
      {
         int separator = fieldAccessor.indexOf(':');
         Object message = messages[Integer.parseInt(fieldAccessor.substring(0, separator))];
         String accessor = fieldAccessor.substring(separator + 1);
         Object value = message.getClass().getMethod("get" + accessor + "AsString").invoke(message);
         assertEquals(BUILDER_CONTENT, value, message.getClass().getName() + ".get" + accessor + "AsString()");
      }
      assertTrue(getFieldCount() > 0);
   }

   public void close() throws Exception
   {
      classLoader.close();
   }
}
