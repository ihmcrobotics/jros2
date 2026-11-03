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
package us.ihmc.jros2;

import us.ihmc.fastddsjava.natives.fastddsjava;

/**
 * Metadata for a sample received by a {@link ROS2Subscription}.
 * <p>
 * A SampleInfo is backed by a native Fast-DDS {@code SampleInfo}. Pass it to
 * {@link ROS2MessageReader#read(ROS2Message, SampleInfo)} to receive the metadata of the sample that was read. Reuse one
 * instance per reading thread to stay allocation-free, and {@link #close()} it when it is no longer needed.
 */
public class SampleInfo implements AutoCloseable
{
   final long fastddsSampleInfo;
   private volatile boolean closed;

   /*
    * When true the values below are a copy of another SampleInfo, made when the sample was taken into that one.
    */
   private boolean snapshot;
   private boolean snapshotValidData;
   private long snapshotSourceTimestampNanos;
   private long snapshotReceptionTimestampNanos;
   private short snapshotSampleState;
   private short snapshotViewState;
   private short snapshotInstanceState;

   public SampleInfo()
   {
      fastddsSampleInfo = fastddsjava.createSampleInfo();
      if (fastddsSampleInfo == 0)
      {
         throw new IllegalStateException(HotPathText.sampleInfoAllocationFailed());
      }
   }

   /**
    * @return true if the sample carried data. Readers only hand out samples with data, so this is true for every sample
    *       returned by {@link ROS2MessageReader#read(ROS2Message, SampleInfo)}.
    */
   public boolean hasValidData()
   {
      return snapshot ? snapshotValidData : fastddsjava.sampleInfoValidData(nativeHandle());
   }

   /** @return the time the sample was published, in nanoseconds since the epoch (as stamped by the writer). */
   public long getSourceTimestampNanos()
   {
      return snapshot ? snapshotSourceTimestampNanos : fastddsjava.sampleInfoSourceTimestampNanos(nativeHandle());
   }

   /** @return the time the sample was received, in nanoseconds since the epoch. */
   public long getReceptionTimestampNanos()
   {
      return snapshot ? snapshotReceptionTimestampNanos : fastddsjava.sampleInfoReceptionTimestampNanos(nativeHandle());
   }

   /** @return the DDS sample state (READ = 1, NOT_READ = 2). */
   public short getSampleState()
   {
      return snapshot ? snapshotSampleState : fastddsjava.sampleInfoSampleState(nativeHandle());
   }

   /** @return the DDS view state (NEW = 1, NOT_NEW = 2). */
   public short getViewState()
   {
      return snapshot ? snapshotViewState : fastddsjava.sampleInfoViewState(nativeHandle());
   }

   /** @return the DDS instance state (ALIVE = 1, NOT_ALIVE_DISPOSED = 2, NOT_ALIVE_NO_WRITERS = 4). */
   public short getInstanceState()
   {
      return snapshot ? snapshotInstanceState : fastddsjava.sampleInfoInstanceState(nativeHandle());
   }

   /**
    * @return true once {@link #close()} has been called
    */
   public boolean isClosed()
   {
      return closed;
   }

   /**
    * Prepares this object to be filled directly by a native take and returns the native handle to fill.
    *
    * @throws IllegalStateException if this SampleInfo was closed
    */
   long nativeHandleForFill()
   {
      long handle = nativeHandle();
      snapshot = false;
      return handle;
   }

   /**
    * Copies the values of {@code source} (filled by a native take) into this object.
    * The caller must guarantee that the native memory of {@code source} is alive during the call.
    */
   void copyFrom(SampleInfo source)
   {
      if (source.snapshot)
      {
         snapshotValidData = source.snapshotValidData;
         snapshotSourceTimestampNanos = source.snapshotSourceTimestampNanos;
         snapshotReceptionTimestampNanos = source.snapshotReceptionTimestampNanos;
         snapshotSampleState = source.snapshotSampleState;
         snapshotViewState = source.snapshotViewState;
         snapshotInstanceState = source.snapshotInstanceState;
      }
      else
      {
         long handle = source.fastddsSampleInfo;
         snapshotValidData = fastddsjava.sampleInfoValidData(handle);
         snapshotSourceTimestampNanos = fastddsjava.sampleInfoSourceTimestampNanos(handle);
         snapshotReceptionTimestampNanos = fastddsjava.sampleInfoReceptionTimestampNanos(handle);
         snapshotSampleState = fastddsjava.sampleInfoSampleState(handle);
         snapshotViewState = fastddsjava.sampleInfoViewState(handle);
         snapshotInstanceState = fastddsjava.sampleInfoInstanceState(handle);
      }
      snapshot = true;
   }

   private long nativeHandle()
   {
      if (closed)
      {
         throw new IllegalStateException(HotPathText.sampleInfoClosed());
      }
      return fastddsSampleInfo;
   }

   @Override
   public synchronized void close()
   {
      if (!closed)
      {
         closed = true;
         fastddsjava.deleteSampleInfo(fastddsSampleInfo);
      }
   }
}
