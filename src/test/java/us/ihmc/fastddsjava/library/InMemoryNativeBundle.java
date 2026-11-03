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
package us.ihmc.fastddsjava.library;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fake native libraries for extraction tests: deterministic contents, counted and optionally slowed-down reads.
 */
final class InMemoryNativeBundle extends fastddsjavaNativeLibrary.NativeBundle
{
   private final Map<String, byte[]> contents;
   private final long chunkDelayMillis;
   final AtomicInteger opens = new AtomicInteger();

   InMemoryNativeBundle(String platform, Map<String, byte[]> contents, long chunkDelayMillis)
   {
      super(platform, new ArrayList<>(contents.keySet()));
      this.contents = contents;
      this.chunkDelayMillis = chunkDelayMillis;
   }

   /** Three libraries of {@code size} bytes each whose contents depend only on {@code seed}. */
   static InMemoryNativeBundle deterministic(String platform, int size, long seed, long chunkDelayMillis)
   {
      Random random = new Random(seed);
      Map<String, byte[]> contents = new LinkedHashMap<>();
      for (String name : new String[] {"libfakecdr.so.2", "libfakedds.so.3.6", "libjnifake.so"})
      {
         byte[] bytes = new byte[size];
         random.nextBytes(bytes);
         contents.put(name, bytes);
      }
      return new InMemoryNativeBundle(platform, contents, chunkDelayMillis);
   }

   /** The same libraries with one byte of one library changed. */
   InMemoryNativeBundle withOneByteChanged()
   {
      Map<String, byte[]> changed = new LinkedHashMap<>();
      for (Map.Entry<String, byte[]> entry : contents.entrySet())
         changed.put(entry.getKey(), entry.getValue().clone());
      byte[] first = changed.get(names.get(0));
      first[first.length / 2] ^= 0x01;
      return new InMemoryNativeBundle(platform, changed, chunkDelayMillis);
   }

   byte[] content(String name)
   {
      return contents.get(name);
   }

   /** @return true if every library in {@code directory} has exactly the expected content */
   boolean matches(Path directory) throws IOException
   {
      for (String name : names)
      {
         Path file = directory.resolve(name);
         if (!Files.isRegularFile(file) || !Arrays.equals(contents.get(name), Files.readAllBytes(file)))
            return false;
      }
      return true;
   }

   @Override
   InputStream open(String name) throws IOException
   {
      byte[] bytes = contents.get(name);
      if (bytes == null)
         throw new IOException("no such library " + name);
      opens.incrementAndGet();
      return new ByteArrayInputStream(bytes)
      {
         @Override
         public synchronized int read(byte[] buffer, int offset, int length)
         {
            if (chunkDelayMillis > 0)
            {
               try
               {
                  Thread.sleep(chunkDelayMillis);
               }
               catch (InterruptedException e)
               {
                  Thread.currentThread().interrupt();
               }
            }
            return super.read(buffer, offset, Math.min(length, 32 * 1024));
         }
      };
   }

   @Override
   long size(String name)
   {
      return contents.get(name).length;
   }

   @Override
   String fingerprint() throws IOException
   {
      MessageDigest digest = fastddsjavaNativeLibrary.sha256();
      for (String name : names)
      {
         digest.update(name.getBytes(StandardCharsets.UTF_8));
         digest.update(contents.get(name));
      }
      return fastddsjavaNativeLibrary.toHex(digest.digest());
   }
}
