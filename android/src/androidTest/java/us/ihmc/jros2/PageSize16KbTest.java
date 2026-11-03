/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.jros2;

import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 16 KB page size support (required for apps on Android 15+ devices with 16 KB pages).
 * <ul>
 *    <li>Every bundled 64-bit library (arm64-v8a, x86_64) must have all PT_LOAD segments aligned to at least 16 KB. This is a
 *    property of the shipped files, so it is checked on every device.</li>
 *    <li>Uncompressed libraries must start at a 16 KB boundary inside the APK (AGP 8.5.1+ does this).</li>
 *    <li>On the device itself all bundled libraries must load. On a 16 KB device a 4 KB aligned library fails to load.</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class PageSize16KbTest
{
   private static final long SIZE_16K = 16384;
   private static final String[] BUNDLED_LIBRARIES = {"c++_shared", "fastcdr", "fastdds", "jnifastddsjava"};

   private static ApplicationInfo applicationInfo()
   {
      return InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationInfo();
   }

   @Test
   public void devicePageSizeMatchesExpectation()
   {
      long pageSize = Os.sysconf(OsConstants._SC_PAGESIZE);
      Log.i(AndroidTestSupport.TAG, "page size " + pageSize + ", ABIs " + String.join(",", Build.SUPPORTED_ABIS) + ", API " + Build.VERSION.SDK_INT);

      String expected = AndroidTestSupport.argument("expectedPageSize");
      if (expected != null)
      {
         assertEquals("device page size", Long.parseLong(expected), pageSize);
      }
      assertTrue(pageSize == 4096 || pageSize == SIZE_16K || pageSize == 65536);
   }

   @Test
   public void allBundledLibrariesLoadAtDevicePageSize()
   {
      long pageSize = Os.sysconf(OsConstants._SC_PAGESIZE);
      List<String> failures = new ArrayList<>();
      System.loadLibrary("log");
      for (String library : BUNDLED_LIBRARIES)
      {
         try
         {
            System.loadLibrary(library);
         }
         catch (UnsatisfiedLinkError e)
         {
            failures.add("lib" + library + ".so: " + e.getMessage());
         }
      }
      assertTrue("Libraries failed to load with page size " + pageSize + ":\n" + String.join("\n", failures), failures.isEmpty());
   }

   @Test
   public void bundled64BitLibrariesAre16KbAligned() throws IOException
   {
      List<String> report = new ArrayList<>();
      List<String> failures = new ArrayList<>();
      int checked = 0;

      try (ZipFile apk = new ZipFile(applicationInfo().sourceDir))
      {
         Enumeration<? extends ZipEntry> entries = apk.entries();
         while (entries.hasMoreElements())
         {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.startsWith("lib/") || !name.endsWith(".so"))
            {
               continue;
            }

            byte[] elf;
            try (InputStream in = apk.getInputStream(entry))
            {
               elf = readAll(in);
            }
            ElfInfo info = ElfInfo.parse(elf);
            report.add(name + " ELF" + (info.is64 ? "64" : "32") + " LOAD align " + info.loadAlignmentsHex());

            if (info.is64)
            {
               checked++;
               for (long alignment : info.loadAlignments)
               {
                  if (alignment < SIZE_16K || alignment % SIZE_16K != 0)
                  {
                     failures.add(name + ": PT_LOAD alignment 0x" + Long.toHexString(alignment) + " < 0x4000");
                     break;
                  }
               }
            }
         }
      }

      Log.i(AndroidTestSupport.TAG, String.join("\n", report));
      assertTrue("No 64-bit native libraries found in " + applicationInfo().sourceDir, checked > 0);
      assertTrue("Not 16 KB compatible:\n" + String.join("\n", failures), failures.isEmpty());
   }

   @Test
   public void uncompressedLibrariesAre16KbAlignedInApk() throws IOException
   {
      boolean extracted = (applicationInfo().flags & ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS) != 0;
      List<ApkEntry> libraries = ApkEntry.readNativeLibraries(applicationInfo().sourceDir);
      assertFalse(libraries.isEmpty());

      List<String> failures = new ArrayList<>();
      for (ApkEntry library : libraries)
      {
         Log.i(AndroidTestSupport.TAG, library.name + " method=" + library.method + " dataOffset=" + library.dataOffset);
         if (library.method == ZipEntry.STORED && library.dataOffset % SIZE_16K != 0)
         {
            failures.add(library.name + " stored at offset " + library.dataOffset + " (not a multiple of 16384)");
         }
         if (!extracted && library.method != ZipEntry.STORED)
         {
            failures.add(library.name + " is compressed although extractNativeLibs=false");
         }
      }
      assertTrue(String.join("\n", failures), failures.isEmpty());
   }

   private static byte[] readAll(InputStream in) throws IOException
   {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[64 * 1024];
      int read;
      while ((read = in.read(buffer)) != -1)
      {
         out.write(buffer, 0, read);
      }
      return out.toByteArray();
   }

   /** Minimal ELF program header reader (only what the alignment checks need). */
   static final class ElfInfo
   {
      final boolean is64;
      final List<Long> loadAlignments = new ArrayList<>();

      private ElfInfo(boolean is64)
      {
         this.is64 = is64;
      }

      static ElfInfo parse(byte[] bytes)
      {
         assertTrue("not an ELF file", bytes.length > 64 && bytes[0] == 0x7f && bytes[1] == 'E' && bytes[2] == 'L' && bytes[3] == 'F');
         boolean is64 = bytes[4] == 2;
         ByteBuffer buffer = ByteBuffer.wrap(bytes).order(bytes[5] == 2 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
         long phoff = is64 ? buffer.getLong(32) : buffer.getInt(28) & 0xffffffffL;
         int phentsize = buffer.getShort(is64 ? 54 : 42) & 0xffff;
         int phnum = buffer.getShort(is64 ? 56 : 44) & 0xffff;

         ElfInfo info = new ElfInfo(is64);
         for (int i = 0; i < phnum; ++i)
         {
            int header = (int) (phoff + (long) i * phentsize);
            if (buffer.getInt(header) == 1) // PT_LOAD
            {
               info.loadAlignments.add(is64 ? buffer.getLong(header + 48) : buffer.getInt(header + 28) & 0xffffffffL);
            }
         }
         return info;
      }

      String loadAlignmentsHex()
      {
         StringBuilder builder = new StringBuilder();
         for (long alignment : loadAlignments)
         {
            builder.append(builder.length() == 0 ? "0x" : ",0x").append(Long.toHexString(alignment));
         }
         return builder.toString();
      }
   }

   /** Reads lib/**.so entries from the APK's central directory, including where each entry's data starts. */
   static final class ApkEntry
   {
      final String name;
      final int method;
      final long dataOffset;

      private ApkEntry(String name, int method, long dataOffset)
      {
         this.name = name;
         this.method = method;
         this.dataOffset = dataOffset;
      }

      static List<ApkEntry> readNativeLibraries(String apkPath) throws IOException
      {
         List<ApkEntry> result = new ArrayList<>();
         try (RandomAccessFile file = new RandomAccessFile(apkPath, "r"))
         {
            long length = file.length();
            int tailLength = (int) Math.min(length, 65_557); // max EOCD record + comment
            byte[] tail = new byte[tailLength];
            file.seek(length - tailLength);
            file.readFully(tail);
            ByteBuffer tailBuffer = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN);

            int eocd = -1;
            for (int i = tailLength - 22; i >= 0; --i)
            {
               if (tailBuffer.getInt(i) == 0x06054b50)
               {
                  eocd = i;
                  break;
               }
            }
            assertTrue("End of central directory not found in " + apkPath, eocd >= 0);
            int entryCount = tailBuffer.getShort(eocd + 10) & 0xffff;
            long centralDirectorySize = tailBuffer.getInt(eocd + 12) & 0xffffffffL;
            long centralDirectoryOffset = tailBuffer.getInt(eocd + 16) & 0xffffffffL;

            byte[] central = new byte[(int) centralDirectorySize];
            file.seek(centralDirectoryOffset);
            file.readFully(central);
            ByteBuffer centralBuffer = ByteBuffer.wrap(central).order(ByteOrder.LITTLE_ENDIAN);

            int position = 0;
            byte[] localHeader = new byte[30];
            for (int i = 0; i < entryCount; ++i)
            {
               assertEquals("bad central directory entry", 0x02014b50, centralBuffer.getInt(position));
               int method = centralBuffer.getShort(position + 10) & 0xffff;
               int nameLength = centralBuffer.getShort(position + 28) & 0xffff;
               int extraLength = centralBuffer.getShort(position + 30) & 0xffff;
               int commentLength = centralBuffer.getShort(position + 32) & 0xffff;
               long localHeaderOffset = centralBuffer.getInt(position + 42) & 0xffffffffL;
               String name = new String(central, position + 46, nameLength, StandardCharsets.UTF_8);
               position += 46 + nameLength + extraLength + commentLength;

               if (name.startsWith("lib/") && name.endsWith(".so"))
               {
                  file.seek(localHeaderOffset);
                  file.readFully(localHeader);
                  ByteBuffer local = ByteBuffer.wrap(localHeader).order(ByteOrder.LITTLE_ENDIAN);
                  assertEquals("bad local header for " + name, 0x04034b50, local.getInt(0));
                  int localNameLength = local.getShort(26) & 0xffff;
                  int localExtraLength = local.getShort(28) & 0xffff;
                  result.add(new ApkEntry(name, method, localHeaderOffset + 30 + localNameLength + localExtraLength));
               }
            }
         }
         return result;
      }
   }
}
