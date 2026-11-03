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

import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Java side of the {@code alloccount} LD_PRELOAD shim ({@code src/test/native/alloccount/alloccount.c}): maps the shim's
 * control/result file (path in the environment variable {@value #FILE_ENV}, created by the shim when the JVM starts) and
 * arms, disarms and reads the per-thread native heap counters without any JNI.
 * <p>
 * Only usable in a JVM started with {@code LD_PRELOAD=liballoccount.so} and {@value #FILE_ENV} set (see
 * {@link NativeHeapAllocationTest}). {@link #arm()} and {@link #disarm()} themselves do not allocate natively (plain
 * stores to the mapping), so the thread that calls them may itself be measured.
 */
final class NativeAllocationCounter
{
   static final String FILE_ENV = "ALLOCCOUNT_FILE";
   /** Set by the parent for the child: the shim unsets {@value #FILE_ENV} so that processes started by the JVM do not inherit it. */
   static final String FILE_PROPERTY = "alloccount.file";

   private static final long MAGIC = 0x324E43434F4C4C41L;
   private static final int SLOT_HEADER_SIZE = 128;
   private static final int RECORD_HEADER_SIZE = 32;
   private static final int COUNT_KINDS = 9;

   static final int KIND_MALLOC = 1;
   static final int KIND_CALLOC = 2;
   static final int KIND_REALLOC = 3;
   static final int KIND_MEMALIGN = 4;
   static final int KIND_FREE = 5;
   static final int KIND_NEW = 6;
   static final int KIND_MMAP = 7;
   static final int KIND_MUNMAP = 8;

   static String kindName(int kind)
   {
      return switch (kind)
      {
         case KIND_MALLOC -> "malloc";
         case KIND_CALLOC -> "calloc";
         case KIND_REALLOC -> "realloc";
         case KIND_MEMALIGN -> "memalign";
         case KIND_FREE -> "free";
         case KIND_NEW -> "operator_new";
         case KIND_MMAP -> "mmap";
         case KIND_MUNMAP -> "munmap";
         default -> "kind" + kind;
      };
   }

   /** One distinct call stack: raw return addresses, frame 0 is the caller of the interposed function. */
   static final class Stack
   {
      final int kind;
      /** Requested size of the first call with this stack. */
      final long size;
      /** Calls with exactly this stack and kind. */
      final long count;
      final long[] frames;

      Stack(int kind, long size, long count, long[] frames)
      {
         this.kind = kind;
         this.size = size;
         this.count = count;
         this.frames = frames;
      }
   }

   /** Counters of one thread during one armed window. */
   static final class ThreadCounts
   {
      final int tid;
      /** pthread name when the thread made its first counted call of the window (at most 15 characters). */
      final String name;
      final long malloc, calloc, realloc, memalign, free, bytes, operatorNew, mmap, munmap;
      final List<Stack> allocationStacks;
      final List<Stack> freeStacks;
      /** Calls whose (new) stack did not fit the per-thread stack table. */
      final long allocationUnrecorded, freeUnrecorded;

      ThreadCounts(int tid, String name, long[] counts, List<Stack> allocationStacks, List<Stack> freeStacks, long allocationUnrecorded, long freeUnrecorded)
      {
         this.tid = tid;
         this.name = name;
         this.malloc = counts[0];
         this.calloc = counts[1];
         this.realloc = counts[2];
         this.memalign = counts[3];
         this.free = counts[4];
         this.bytes = counts[5];
         this.operatorNew = counts[6];
         this.mmap = counts[7];
         this.munmap = counts[8];
         this.allocationStacks = allocationStacks;
         this.freeStacks = freeStacks;
         this.allocationUnrecorded = allocationUnrecorded;
         this.freeUnrecorded = freeUnrecorded;
      }

      long allocations()
      {
         return malloc + calloc + realloc + memalign + operatorNew + mmap;
      }

      long releases()
      {
         return free + munmap;
      }

      long calls()
      {
         return allocations() + releases();
      }

      String summary()
      {
         return "malloc=" + malloc + " calloc=" + calloc + " realloc=" + realloc + " memalign=" + memalign + " new=" + operatorNew + " free=" + free
                + " mmap=" + mmap + " munmap=" + munmap + " bytes=" + bytes + " unrecordedStacks=" + (allocationUnrecorded + freeUnrecorded);
      }
   }

   /** Result of one armed window. */
   static final class Window
   {
      final List<ThreadCounts> threads;
      final int droppedThreads;
      final long unslottedCalls;
      final long totalCalls;
      /** Heap calls made by the shim itself while it recorded (must be 0: the shim does not allocate). */
      final long internalCalls;

      Window(List<ThreadCounts> threads, int droppedThreads, long unslottedCalls, long totalCalls, long internalCalls)
      {
         this.threads = threads;
         this.droppedThreads = droppedThreads;
         this.unslottedCalls = unslottedCalls;
         this.totalCalls = totalCalls;
         this.internalCalls = internalCalls;
      }

      ThreadCounts thread(int tid)
      {
         for (ThreadCounts counts : threads)
         {
            if (counts.tid == tid)
               return counts;
         }
         return null;
      }
   }

   private final MappedByteBuffer map;
   private final int maxThreads;
   private final int maxStacks;
   private final int maxDepth;
   private final int slotSize;
   private final int headerSize;
   private final int recordSize;

   private NativeAllocationCounter(MappedByteBuffer map)
   {
      this.map = map;
      map.order(ByteOrder.LITTLE_ENDIAN);
      if (map.getLong(0) != MAGIC)
         throw new IllegalStateException("not an alloccount file of this version (magic mismatch)");
      if (map.getInt(52) != 1)
         throw new IllegalStateException("the alloccount shim is not initialized");
      if (map.getInt(48) != (int) ProcessHandle.current().pid())
         throw new IllegalStateException("the alloccount file belongs to process " + map.getInt(48) + ", not to this JVM");
      maxThreads = map.getInt(20);
      maxStacks = map.getInt(24);
      maxDepth = map.getInt(28);
      slotSize = map.getInt(40);
      headerSize = map.getInt(44);
      recordSize = map.getInt(88);
      if (recordSize != RECORD_HEADER_SIZE + 8 * maxDepth)
         throw new IllegalStateException("unexpected alloccount record size " + recordSize);
   }

   /**
    * @return the counter of this JVM, or null if the JVM was not started with the shim
    */
   static NativeAllocationCounter open() throws IOException
   {
      String file = System.getProperty(FILE_PROPERTY);
      if (file == null)
         file = System.getenv(FILE_ENV);
      if (file == null)
         return null;
      Path path = Paths.get(file);
      try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE))
      {
         return new NativeAllocationCounter(channel.map(FileChannel.MapMode.READ_WRITE, 0, channel.size()));
      }
   }

   /** Starts a new window: every thread claims a fresh slot at its next counted call. */
   void arm()
   {
      map.putInt(12, 0);
      map.putInt(16, map.getInt(16) + 1);
      map.putInt(32, 0);
      map.putInt(36, 0);
      map.putLong(56, 0);
      map.putLong(64, 0);
      map.putLong(80, 0);
      // Publishes the stores above before the armed flag (a full fence on every platform the shim supports).
      VarHandle.fullFence();
      map.putInt(12, 1);
      VarHandle.fullFence();
   }

   void disarm()
   {
      map.putInt(12, 0);
      VarHandle.fullFence();
   }

   /**
    * Disarms and reads the window. Waits briefly so that calls that were already being recorded complete.
    */
   Window disarmAndRead()
   {
      disarm();
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
      return read();
   }

   Window read()
   {
      int claimed = Math.min(map.getInt(32), maxThreads);
      List<ThreadCounts> threads = new ArrayList<>();
      for (int i = 0; i < claimed; ++i)
      {
         int base = headerSize + i * slotSize;
         int tid = map.getInt(base);
         byte[] nameBytes = new byte[16];
         map.get(base + 8, nameBytes);
         int length = 0;
         while (length < 16 && nameBytes[length] != 0)
            length++;
         String name = new String(nameBytes, 0, length, StandardCharsets.UTF_8);
         long[] counts = new long[COUNT_KINDS];
         for (int c = 0; c < COUNT_KINDS; ++c)
            counts[c] = map.getLong(base + 24 + 8 * c);
         int allocationStackCount = Math.min(map.getInt(base + 96), maxStacks);
         int freeStackCount = Math.min(map.getInt(base + 100), maxStacks);
         List<Stack> allocationStacks = readStacks(base + SLOT_HEADER_SIZE, allocationStackCount);
         List<Stack> freeStacks = readStacks(base + SLOT_HEADER_SIZE + maxStacks * recordSize, freeStackCount);
         threads.add(new ThreadCounts(tid, name, counts, allocationStacks, freeStacks, map.getLong(base + 104), map.getLong(base + 112)));
      }
      return new Window(threads, map.getInt(36), map.getLong(56), map.getLong(64), map.getLong(80));
   }

   private List<Stack> readStacks(int offset, int count)
   {
      List<Stack> stacks = new ArrayList<>();
      for (int s = 0; s < count; ++s)
      {
         int record = offset + s * recordSize;
         int kind = map.getInt(record);
         int depth = Math.min(map.getInt(record + 4), maxDepth);
         long size = map.getLong(record + 8);
         long calls = map.getLong(record + 16);
         long[] frames = new long[depth];
         for (int f = 0; f < depth; ++f)
            frames[f] = map.getLong(record + RECORD_HEADER_SIZE + 8 * f);
         stacks.add(new Stack(kind, size, calls, frames));
      }
      return stacks;
   }
}
