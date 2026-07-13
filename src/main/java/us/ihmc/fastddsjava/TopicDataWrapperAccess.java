/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
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
package us.ihmc.fastddsjava;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.LongPointer;
import us.ihmc.fastddsjava.pointers.ByteVector;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;

/**
 * Allocation-free access to a {@link fastddsjava_TopicDataWrapper} payload.
 * <p>
 * JavaCPP's {@code data_vector()} and {@code data_ptr()} allocate a new Pointer wrapper on every call,
 * which is unsuitable for realtime {@code read}/{@code readLatest}/{@code publish} paths.
 * <p>
 * This class keeps stable views for the lifetime of the wrapper:
 * <ul>
 *    <li>{@code fastddsjava_TopicDataWrapper::data_vector} is the first (and only) data member, so a
 *        {@link ByteVector} constructed from the wrapper shares its native address and can call
 *        {@code size()}/{@code resize()} without allocating.</li>
 *    <li>Payload bytes are reached via the first pointer word of that {@code std::vector}
 *        (libstdc++ / libc++ / MSVC all store {@code begin} first). The data pointer is retargeted
 *        into a reusable {@link BytePointer} before each bulk copy.</li>
 * </ul>
 */
public final class TopicDataWrapperAccess
{
   private static final boolean PTR_64 = !"32".equals(System.getProperty("sun.arch.data.model"));

   private final ByteVector vectorView;
   private final LongPointer vectorAsLongs;
   private final IntPointer vectorAsInts;
   private final RetargetableBytePointer dataView;

   public TopicDataWrapperAccess(fastddsjava_TopicDataWrapper wrapper)
   {
      // Share the wrapper address; drop ownership so GC of these views cannot free the wrapper.
      vectorView = new ByteVector(wrapper);
      vectorView.deallocate(false);

      if (PTR_64)
      {
         vectorAsLongs = new LongPointer(wrapper);
         vectorAsLongs.deallocate(false);
         vectorAsInts = null;
      }
      else
      {
         vectorAsLongs = null;
         vectorAsInts = new IntPointer(wrapper);
         vectorAsInts.deallocate(false);
      }

      dataView = new RetargetableBytePointer();
   }

   public long size()
   {
      return vectorView.size();
   }

   public void resize(long size)
   {
      vectorView.resize(size);
   }

   public void copyToHeap(byte[] dst, int offset, int length)
   {
      if (length <= 0)
         return;

      retargetDataView(length);
      dataView.get(dst, offset, length);
   }

   public void copyFromHeap(byte[] src, int offset, int length)
   {
      if (length <= 0)
         return;

      retargetDataView(length);
      dataView.put(src, offset, length);
   }

   private void retargetDataView(long length)
   {
      long dataAddress = PTR_64 ? vectorAsLongs.get(0) : (vectorAsInts.get(0) & 0xffffffffL);
      dataView.retarget(dataAddress, length);
   }

   private static final class RetargetableBytePointer extends BytePointer
   {
      RetargetableBytePointer()
      {
         // Address is retargeted to vector storage on each use; never owns native memory.
         deallocate(false);
      }

      void retarget(long address, long size)
      {
         this.address = address;
         this.position = 0;
         this.limit = size;
         this.capacity = size;
      }
   }
}
