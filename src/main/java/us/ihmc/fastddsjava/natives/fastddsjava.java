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
package us.ihmc.fastddsjava.natives;

import java.nio.ByteBuffer;

/**
 * Hand-written JNI bindings for the fastddsjava native library.
 * Opaque {@code long} values named {@code fastdds*} are native Fast-DDS pointers; 0 means null. With ABI v2 natives
 * ({@link #abiVersion()}), functions given a 0 pointer fail with {@link #RETCODE_BAD_PARAMETER} (or return 0 / do
 * nothing) instead of crashing; ABI v1 natives dereference it. A pointer to an entity that was already deleted is
 * undefined behavior with every version.
 * <p>
 * Listener lifecycle (ABI v2 natives; ABI v1 frees a listener immediately, so it must not be deleted before the
 * entities using it): a listener runs its callbacks behind an enable gate. It is disabled when it is
 * removed from its last entity ({@code dataReaderSetListener(reader, 0)}, deletion of the entity) and permanently by
 * {@code delete*Listener}; a disabled listener starts no Java callback (one that already started completes, and keeps its
 * callback object alive until it returns). {@code delete*Listener} releases the Java callbacks (their JNI global
 * references) immediately; the native object itself is freed once every entity it was ever installed
 * on has been deleted, because only {@link #deleteDataReader} / {@link #deleteDataWriter} (called outside a callback of
 * the same participant, returning {@link #RETCODE_OK}) fence Fast-DDS callbacks; {@code set*Listener} does not.
 * Recommended teardown, on a thread that is not running a Fast-DDS callback of the same participant: set the listener
 * to 0, delete the entity, then delete the listener. Callback setters may be called at any time.
 */
public final class fastddsjava
{
   public static final short CDR_BE = (short) 0x0000;
   public static final short CDR_LE = (short) 0x0001;
   public static final short PL_CDR_BE = (short) 0x0002;
   public static final short PL_CDR_LE = (short) 0x0003;

   public static final int RETCODE_OK = 0;
   public static final int RETCODE_ERROR = 1;
   public static final int RETCODE_UNSUPPORTED = 2;
   public static final int RETCODE_BAD_PARAMETER = 3;
   public static final int RETCODE_PRECONDITION_NOT_MET = 4;
   public static final int RETCODE_OUT_OF_RESOURCES = 5;
   public static final int RETCODE_NOT_ENABLED = 6;
   public static final int RETCODE_IMMUTABLE_POLICY = 7;
   public static final int RETCODE_INCONSISTENT_POLICY = 8;
   public static final int RETCODE_ALREADY_DELETED = 9;
   public static final int RETCODE_TIMEOUT = 10;
   public static final int RETCODE_NO_DATA = 11;
   public static final int RETCODE_ILLEGAL_OPERATION = 12;

   /** Native ABI of the bundled libraries built before ABI versioning existed. */
   public static final int ABI_V1 = 1;
   /** Native ABI adding {@link #dataWriterWriteBuffer}, {@link #dataReaderTakeNextToBuffer} and {@link #nativeHasSecurity}. */
   public static final int ABI_V2 = 2;

   /** {@link #dataReaderTakeNextToBuffer} result: no sample was available. */
   public static final int TAKE_NO_DATA = -1;
   /** {@link #dataReaderTakeNextToBuffer} result: a sample without data (valid_data == false) was taken and should be skipped. */
   public static final int TAKE_INVALID_SAMPLE = -2;
   /** {@link #dataReaderTakeNextToBuffer} results at or below this value encode {@code -(TAKE_ERROR_BASE + retcode)}. */
   public static final int TAKE_ERROR_BASE = 100;

   private static volatile int abiVersion = 0;
   private static volatile int securitySupport = -1;

   private fastddsjava()
   {
   }

   /**
    * @return the ABI version of the loaded native library: {@link #ABI_V1} for libraries that predate versioning,
    *       otherwise the value reported by the library. The result is cached once the native library is loaded; before
    *       that, {@link #ABI_V1} is returned without being cached, so an early call cannot hide a later loaded v2 library.
    */
   public static int abiVersion()
   {
      int version = abiVersion;
      if (version == 0)
      {
         try
         {
            version = nativeAbiVersion();
         }
         catch (UnsatisfiedLinkError notPresent)
         {
            // Either the library predates versioning or it is not loaded yet; only the former may be cached.
            if (!nativesLoaded())
               return ABI_V1;
            version = ABI_V1;
         }
         abiVersion = version;
      }
      return version;
   }

   /**
    * @return true if the fastddsjava JNI library is loaded, determined by calling a function that every ABI version
    *       implements as a no-op
    */
   private static boolean nativesLoaded()
   {
      try
      {
         deleteSampleInfo(0);
         return true;
      }
      catch (UnsatisfiedLinkError notLoaded)
      {
         return false;
      }
   }

   /**
    * @return true if the loaded Fast-DDS was built with DDS-Security (required for SROS2). Always false for ABI v1
    *       libraries. Like {@link #abiVersion()}, the result is only cached once the native library is loaded.
    */
   public static boolean hasSecurity()
   {
      int support = securitySupport;
      if (support < 0)
      {
         boolean supported = false;
         if (abiVersion() >= ABI_V2)
         {
            try
            {
               supported = nativeHasSecurity();
            }
            catch (UnsatisfiedLinkError notPresent)
            {
               supported = false;
            }
         }
         else if (abiVersion == 0)
         {
            return false; // not loaded yet: do not cache
         }
         support = supported ? 1 : 0;
         securitySupport = support;
      }
      return support == 1;
   }

   /** ABI v2+. Use {@link #abiVersion()}, which tolerates older libraries. */
   private static native int nativeAbiVersion();

   /** ABI v2+. Use {@link #hasSecurity()}, which tolerates older libraries. */
   private static native boolean nativeHasSecurity();

   /**
    * ABI v2+: serializes {@code length} bytes of the direct buffer {@code src} (starting at position 0, CDR header
    * included) straight into the DataWriter's payload and writes it. The buffer is only read during this call.
    * Uses no shared native state, so concurrent calls on one writer with distinct buffers are safe.
    *
    * @return a Fast-DDS retcode: {@link #RETCODE_BAD_PARAMETER} for a 0 writer, a non-direct buffer, or a length that is
    *       not positive or exceeds the buffer capacity; {@link #RETCODE_ERROR} if the payload does not fit the writer's
    *       payload pool (fixed-size PREALLOCATED memory policy)
    */
   public static native int dataWriterWriteBuffer(long fastddsDataWriter, ByteBuffer src, int length);

   /**
    * ABI v2+: takes the next sample (any sample state) from the reader. A valid sample whose payload fits in
    * {@code dstCapacity} is copied into the direct buffer {@code dst} at position 0; a larger payload is kept in
    * {@code fastddsTopicData} so the caller can grow its buffer and copy it with {@link #topicDataReadBuffer}.
    * {@code fastddsSampleInfo} is filled for every taken sample.
    *
    * So a result {@code size >= 0} with {@code size <= dstCapacity} means the payload is in {@code dst[0, size)}, and
    * {@code size > dstCapacity} means it is in {@code fastddsTopicData} ({@link #topicDataSize} == size).
    * <p>
    * Unlike {@link #dataReaderTakeNextCustom}, a payload copied into {@code dst} does not update {@code fastddsTopicData}
    * ({@link #topicDataSize} then still reports the previous fallback payload): use the returned size. Samples in any
    * sample state are taken, including ones already read with {@link #dataReaderReadNextSample}. Does not allocate
    * once the fallback storage has grown to the largest payload. The borrowed {@code dst} is only written during this
    * call and is never stored. Concurrent calls must use distinct topic data.
    *
    * @param fastddsSampleInfo may be 0 if the caller does not need the sample info
    * @param dst a direct buffer, or null to always use the {@code fastddsTopicData} fallback
    * @param dstCapacity number of bytes usable in {@code dst}, at most its capacity; 0 to always use the fallback
    * @return the payload size (>= 0) of a valid sample, {@link #TAKE_NO_DATA}, {@link #TAKE_INVALID_SAMPLE}, or
    *       {@code -(TAKE_ERROR_BASE + retcode)} on error (see {@code fastddsjavaTools.takeResultRetcode}); a 0 reader
    *       or topic data, a heap buffer with {@code dstCapacity > 0}, or a {@code dstCapacity} larger than the capacity
    *       of {@code dst} give {@code RETCODE_BAD_PARAMETER} and take nothing
    */
   public static native int dataReaderTakeNextToBuffer(long fastddsDataReader, long fastddsTopicData, long fastddsSampleInfo, ByteBuffer dst, int dstCapacity);

   public static native int loadXmlProfilesString(String xml);

   public static native long createParticipant(String profileName);

   public static native int deleteParticipant(long fastddsParticipant);

   public static native long createPublisher(long fastddsParticipant, String profileName);

   public static native int deletePublisher(long fastddsParticipant, long fastddsPublisher);

   public static native long createSubscriber(long fastddsParticipant, String profileName);

   public static native int deleteSubscriber(long fastddsParticipant, long fastddsSubscriber);

   public static native long createTopicDataWrapperType(String name, short encapsulation);

   public static native long createTypesupport(long fastddsTopicDataWrapperType);

   public static native int registerType(long fastddsParticipant, long fastddsTypeSupport);

   public static native int unregisterType(long fastddsParticipant, String typeName);

   public static native void deleteTypesupport(long fastddsTypeSupport);

   public static native String topicDataWrapperTypeGetName(long fastddsTopicDataWrapperType);

   /**
    * Creates topic data holding one zero byte. With ABI v2 natives it does not depend on the type afterward.
    */
   public static native long createData(long fastddsTopicDataWrapperType);

   public static native void deleteData(long fastddsTopicDataWrapperType, long fastddsTopicData);

   public static native long createTopic(long fastddsParticipant, long fastddsTopicDataWrapperType, String topicName, String profileName);

   public static native int deleteTopic(long fastddsParticipant, long fastddsTopic);

   public static native long createDataWriter(long fastddsPublisher, long fastddsTopic, String profileName);

   public static native int deleteDataWriter(long fastddsPublisher, long fastddsDataWriter);

   /**
    * ABI v1 write of the payload held in {@code fastddsTopicData} (its whole {@link #topicDataSize}). The topic data
    * must not be modified concurrently. Prefer {@link #dataWriterWriteBuffer} when {@link #abiVersion()} is at least 2.
    *
    * @return a Fast-DDS retcode; {@link #RETCODE_ERROR} if the payload is empty or does not fit the writer's payload pool
    */
   public static native int dataWriterWrite(long fastddsDataWriter, long fastddsTopicData);

   public static native int dataWriterSetListener(long fastddsDataWriter, long fastddsDataWriterListener);

   public static native int dataWriterGetPublicationMatchedCount(long fastddsDataWriter);

   public static native long createDataReader(long fastddsSubscriber, long fastddsTopic, long fastddsDataReaderListener, String profileName);

   public static native int deleteDataReader(long fastddsSubscriber, long fastddsDataReader);

   public static native int dataReaderReadNextSample(long fastddsDataReader, long fastddsTopicData, long fastddsSampleInfo);

   /**
    * Takes the next sample in any sample state into {@code fastddsTopicData} ({@link #topicDataSize} is its size).
    * Returns {@link #RETCODE_OK} also for samples without data: check {@link #sampleInfoValidData}, in which case the
    * topic data is left unchanged. Does not allocate (ABI v2 natives; ABI v1 natives allocate per call).
    */
   public static native int dataReaderTakeNextCustom(long fastddsDataReader, long fastddsTopicData, long fastddsSampleInfo);

   public static native int dataReaderSetListener(long fastddsDataReader, long fastddsDataReaderListener);

   public static native int dataReaderGetUnreadCount(long fastddsDataReader);

   public static native int dataReaderGetSubscriptionMatchedCount(long fastddsDataReader);

   public static native int dataReaderGetSubscriptionMatchedStatus(long fastddsDataReader, byte[] lastPublicationGuidOut);

   public static native void getParticipantGuid(long fastddsParticipant, byte[] guidOut);

   public static native void getWriterGuid(long fastddsDataWriter, byte[] guidOut);

   public static native void getReaderGuid(long fastddsDataReader, byte[] guidOut);

   public static native void topicDataResize(long fastddsTopicData, int size);

   public static native int topicDataSize(long fastddsTopicData);

   public static native void topicDataWrite(long fastddsTopicData, byte[] src, int offset, int length);

   public static native void topicDataRead(long fastddsTopicData, byte[] dst, int offset, int length);

   /**
    * Zero-copy write from a direct {@link ByteBuffer}. Prefer this over the {@code byte[]} overload on hot paths.
    */
   public static native void topicDataWriteBuffer(long fastddsTopicData, ByteBuffer src, int offset, int length);

   /**
    * Zero-copy read into a direct {@link ByteBuffer}. Prefer this over the {@code byte[]} overload on hot paths.
    */
   public static native void topicDataReadBuffer(long fastddsTopicData, ByteBuffer dst, int offset, int length);

   public static native long createSampleInfo();

   public static native void deleteSampleInfo(long fastddsSampleInfo);

   public static native boolean sampleInfoValidData(long fastddsSampleInfo);

   public static native long sampleInfoSourceTimestampNanos(long fastddsSampleInfo);

   public static native long sampleInfoReceptionTimestampNanos(long fastddsSampleInfo);

   public static native short sampleInfoSampleState(long fastddsSampleInfo);

   public static native short sampleInfoViewState(long fastddsSampleInfo);

   public static native short sampleInfoInstanceState(long fastddsSampleInfo);

   public static native long createDataReaderListener();

   /**
    * Disables the listener for good and releases its Java callbacks. See the class documentation for when the native
    * object is freed. Must be called at most once per listener.
    */
   public static native void deleteDataReaderListener(long fastddsDataReaderListener);

   public static native void dataReaderListenerSetOnDataAvailable(long fastddsDataReaderListener, fastddsjavaCallback callback);

   public static native void dataReaderListenerSetOnSubscriptionMatched(long fastddsDataReaderListener, fastddsjavaCallback callback);

   public static native long createDataWriterListener();

   /**
    * Disables the listener for good and releases its Java callbacks. See the class documentation for when the native
    * object is freed. Must be called at most once per listener.
    */
   public static native void deleteDataWriterListener(long fastddsDataWriterListener);

   public static native void dataWriterListenerSetOnPublicationMatched(long fastddsDataWriterListener, fastddsjavaCallback callback);
}
