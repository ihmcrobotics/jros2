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
package us.ihmc.jros2.allocation.nativeheap;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Resolves raw return addresses of this process to {@code library!function+offset}, using {@code /proc/self/maps} and
 * the symbol tables printed by {@code nm} (binutils). Stripped libraries are resolved through an unstripped copy with
 * the same GNU build ID when one is found in the directories of the system property {@value #SYMBOL_DIRS_PROPERTY}
 * (path-separated), otherwise through their dynamic symbol table. Must be called after the measurement (it allocates).
 */
final class NativeStackResolver
{
   static final String SYMBOL_DIRS_PROPERTY = "alloccount.symbolDirs";

   /** One resolved frame. */
   static final class Frame
   {
      final long address;
      /** Library file name (no directory), or "[anon]" for addresses outside any file mapping (e.g. JIT code). */
      final String library;
      final String function;

      Frame(long address, String library, String function)
      {
         this.address = address;
         this.library = library;
         this.function = function;
      }

      @Override
      public String toString()
      {
         return library + "!" + function;
      }
   }

   private static final class Mapping
   {
      final long start, end, offset;
      final String path;

      Mapping(long start, long end, long offset, String path)
      {
         this.start = start;
         this.end = end;
         this.offset = offset;
         this.path = path;
      }
   }

   private static final class SymbolTable
   {
      final long[] addresses;
      final long[] sizes;
      final String[] names;

      SymbolTable(long[] addresses, long[] sizes, String[] names)
      {
         this.addresses = addresses;
         this.sizes = sizes;
         this.names = names;
      }

      String lookup(long vaddr)
      {
         int low = 0, high = addresses.length - 1, found = -1;
         while (low <= high)
         {
            int mid = (low + high) >>> 1;
            if (addresses[mid] <= vaddr)
            {
               found = mid;
               low = mid + 1;
            }
            else
            {
               high = mid - 1;
            }
         }
         if (found < 0)
            return null;
         long delta = vaddr - addresses[found];
         if (sizes[found] > 0 && delta >= sizes[found])
            return null;
         return names[found] + "+0x" + Long.toHexString(delta);
      }
   }

   private final List<Mapping> mappings = new ArrayList<>();
   private final Map<String, Long> loadBases = new HashMap<>();
   private final Map<String, SymbolTable> symbolTables = new HashMap<>();
   private final List<Path> symbolDirectories = new ArrayList<>();

   NativeStackResolver() throws IOException
   {
      for (String line : Files.readAllLines(Paths.get("/proc/self/maps"), StandardCharsets.UTF_8))
      {
         String[] fields = line.trim().split("\\s+", 6);
         if (fields.length < 5)
            continue;
         String[] range = fields[0].split("-");
         long start = Long.parseUnsignedLong(range[0], 16);
         long end = Long.parseUnsignedLong(range[1], 16);
         long offset = Long.parseUnsignedLong(fields[2], 16);
         String path = fields.length == 6 ? fields[5] : "";
         mappings.add(new Mapping(start, end, offset, path));
         if (path.startsWith("/"))
         {
            long base = start - offset;
            loadBases.merge(path, base, Math::min);
         }
      }
      String dirs = System.getProperty(SYMBOL_DIRS_PROPERTY, "");
      for (String dir : dirs.split(File.pathSeparator))
      {
         if (!dir.isEmpty())
            symbolDirectories.add(Paths.get(dir));
      }
   }

   Frame resolve(long address)
   {
      for (Mapping mapping : mappings)
      {
         if (address >= mapping.start && address < mapping.end)
         {
            if (!mapping.path.startsWith("/"))
               return new Frame(address, mapping.path.isEmpty() ? "[anon]" : mapping.path, "0x" + Long.toHexString(address));
            String library = Paths.get(mapping.path).getFileName().toString();
            long vaddr = address - loadBases.get(mapping.path);
            // A return address points after the call; look up the call instruction itself.
            String function = symbols(mapping.path).lookup(vaddr - 1);
            return new Frame(address, library, function != null ? function : "0x" + Long.toHexString(vaddr));
         }
      }
      return new Frame(address, "[unmapped]", "0x" + Long.toHexString(address));
   }

   List<Frame> resolve(long[] frames)
   {
      List<Frame> resolved = new ArrayList<>(frames.length);
      for (long frame : frames)
         resolved.add(resolve(frame));
      return resolved;
   }

   private SymbolTable symbols(String path)
   {
      return symbolTables.computeIfAbsent(path, this::loadSymbols);
   }

   private SymbolTable loadSymbols(String path)
   {
      String symbolFile = unstrippedCopy(path);
      List<String> lines = run("nm", "--defined-only", "-C", "-S", symbolFile);
      if (lines.isEmpty() || (lines.size() == 1 && lines.get(0).contains("no symbols")))
         lines = run("nm", "--defined-only", "-C", "-S", "-D", symbolFile);

      List<long[]> entries = new ArrayList<>();
      List<String> names = new ArrayList<>();
      for (String line : lines)
      {
         // "<address> [<size>] <type> <name>"
         String[] fields = line.split(" ", 4);
         try
         {
            long address;
            long size;
            String type;
            String name;
            if (fields.length == 4 && fields[2].length() == 1)
            {
               address = Long.parseUnsignedLong(fields[0], 16);
               size = Long.parseUnsignedLong(fields[1], 16);
               type = fields[2];
               name = fields[3];
            }
            else
            {
               String[] short3 = line.split(" ", 3);
               if (short3.length != 3 || short3[1].length() != 1)
                  continue;
               address = Long.parseUnsignedLong(short3[0], 16);
               size = 0;
               type = short3[1];
               name = short3[2];
            }
            if (!"TtWwi".contains(type))
               continue;
            entries.add(new long[] {address, size, names.size()});
            names.add(name);
         }
         catch (NumberFormatException ignored)
         {
         }
      }
      entries.sort((a, b) -> Long.compareUnsigned(a[0], b[0]));
      long[] addresses = new long[entries.size()];
      long[] sizes = new long[entries.size()];
      String[] sortedNames = new String[entries.size()];
      for (int i = 0; i < entries.size(); ++i)
      {
         addresses[i] = entries.get(i)[0];
         sizes[i] = entries.get(i)[1];
         sortedNames[i] = names.get((int) entries.get(i)[2]);
      }
      return new SymbolTable(addresses, sizes, sortedNames);
   }

   /**
    * An unstripped file with the same build ID: from the symbol directories, else the system's detached debug file
    * ({@code /usr/lib/debug/.build-id/xx/yyyy.debug}, e.g. libc6-dbg), else {@code path} itself.
    */
   private String unstrippedCopy(String path)
   {
      String buildId = buildId(path);
      if (buildId == null)
         return path;
      if (buildId.length() > 2)
      {
         Path debugFile = Paths.get("/usr/lib/debug/.build-id", buildId.substring(0, 2), buildId.substring(2) + ".debug");
         if (Files.isRegularFile(debugFile) && symbolDirectories.isEmpty())
            return debugFile.toString();
      }
      String name = Paths.get(path).getFileName().toString();
      for (Path dir : symbolDirectories)
      {
         File[] candidates = dir.toFile().listFiles((d, candidate) -> candidate.startsWith(name.contains(".so") ? name.substring(0, name.indexOf(".so") + 3) : name));
         if (candidates == null)
            continue;
         Arrays.sort(candidates);
         for (File candidate : candidates)
         {
            if (candidate.isFile() && buildId.equals(buildId(candidate.getPath())))
               return candidate.getPath();
         }
      }
      Path debugFile = Paths.get("/usr/lib/debug/.build-id", buildId.substring(0, 2), buildId.substring(2) + ".debug");
      return buildId.length() > 2 && Files.isRegularFile(debugFile) ? debugFile.toString() : path;
   }

   private final Map<String, String> buildIds = new HashMap<>();

   private String buildId(String path)
   {
      return buildIds.computeIfAbsent(path, NativeStackResolver::readBuildId);
   }

   private static String readBuildId(String path)
   {
      for (String line : run("readelf", "-n", path))
      {
         int index = line.indexOf("Build ID:");
         if (index >= 0)
            return line.substring(index + "Build ID:".length()).trim();
      }
      return null;
   }

   private static List<String> run(String... command)
   {
      List<String> lines = new ArrayList<>();
      try
      {
         ProcessBuilder builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
         // The tools must not run with the shim of the measured JVM.
         builder.environment().remove("LD_PRELOAD");
         Process process = builder.start();
         try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
         {
            String line;
            while ((line = reader.readLine()) != null)
               lines.add(line);
         }
         process.waitFor(60, TimeUnit.SECONDS);
      }
      catch (IOException e)
      {
         return lines;
      }
      catch (InterruptedException e)
      {
         Thread.currentThread().interrupt();
      }
      return lines;
   }
}
