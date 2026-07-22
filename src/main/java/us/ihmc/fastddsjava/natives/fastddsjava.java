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
 * Opaque {@code long} values named {@code fastdds*} are native Fast-DDS pointers; 0 means null.
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

   private fastddsjava()
   {
   }

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

   public static native long createData(long fastddsTopicDataWrapperType);

   public static native void deleteData(long fastddsTopicDataWrapperType, long fastddsTopicData);

   public static native long createTopic(long fastddsParticipant, long fastddsTopicDataWrapperType, String topicName, String profileName);

   public static native int deleteTopic(long fastddsParticipant, long fastddsTopic);

   public static native long createDataWriter(long fastddsPublisher, long fastddsTopic, String profileName);

   public static native int deleteDataWriter(long fastddsPublisher, long fastddsDataWriter);

   public static native int dataWriterWrite(long fastddsDataWriter, long fastddsTopicData);

   public static native int dataWriterSetListener(long fastddsDataWriter, long fastddsDataWriterListener);

   public static native int dataWriterGetPublicationMatchedCount(long fastddsDataWriter);

   public static native long createDataReader(long fastddsSubscriber, long fastddsTopic, long fastddsDataReaderListener, String profileName);

   public static native int deleteDataReader(long fastddsSubscriber, long fastddsDataReader);

   public static native int dataReaderReadNextSample(long fastddsDataReader, long fastddsTopicData, long fastddsSampleInfo);

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

   public static native void deleteDataReaderListener(long fastddsDataReaderListener);

   public static native void dataReaderListenerSetOnDataAvailable(long fastddsDataReaderListener, fastddsjavaCallback callback);

   public static native void dataReaderListenerSetOnSubscriptionMatched(long fastddsDataReaderListener, fastddsjavaCallback callback);

   public static native long createDataWriterListener();

   public static native void deleteDataWriterListener(long fastddsDataWriterListener);

   public static native void dataWriterListenerSetOnPublicationMatched(long fastddsDataWriterListener, fastddsjavaCallback callback);
}
