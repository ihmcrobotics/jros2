#include "fastddsjava.h"

#include <jni.h>

#include <cstring>
#include <functional>
#include <memory>
#include <string>

namespace
{
JavaVM* g_jvm = nullptr;
jclass g_nativeCallbackClass = nullptr;
jmethodID g_callbackCallMethod = nullptr;

struct JniCallback
{
   jobject global_ref = nullptr;

   ~JniCallback()
   {
      if (g_jvm != nullptr && global_ref != nullptr)
      {
         JNIEnv* env = nullptr;
         if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK && env != nullptr)
         {
            env->DeleteGlobalRef(global_ref);
         }
#if defined(__ANDROID__)
         else if (g_jvm->AttachCurrentThread(&env, nullptr) == 0 && env != nullptr)
#else
         else if (g_jvm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr) == 0 && env != nullptr)
#endif
         {
            env->DeleteGlobalRef(global_ref);
            g_jvm->DetachCurrentThread();
         }
         global_ref = nullptr;
      }
   }
};

JNIEnv* getEnv()
{
   JNIEnv* env = nullptr;

   if (g_jvm != nullptr)
   {
      jint status = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
      if (status == JNI_EDETACHED)
      {
         // Attach as daemon and leave attached for the lifetime of the Fast-DDS thread.
         // Detaching after every on_data_available made e2e latency ~2x worse than JavaCPP.
         JavaVMAttachArgs args;
         args.version = JNI_VERSION_1_6;
         args.name = const_cast<char*>("fastdds-callback");
         args.group = nullptr;
#if defined(__ANDROID__)
         if (g_jvm->AttachCurrentThreadAsDaemon(&env, &args) != 0)
#else
         if (g_jvm->AttachCurrentThreadAsDaemon(reinterpret_cast<void**>(&env), &args) != 0)
#endif
         {
            env = nullptr;
         }
      }
      else if (status != JNI_OK)
      {
         env = nullptr;
      }
   }

   return env;
}

void invokeCallback(jobject global_ref)
{
   if (global_ref != nullptr && g_callbackCallMethod != nullptr)
   {
      JNIEnv* env = getEnv();
      if (env != nullptr)
      {
         env->CallVoidMethod(global_ref, g_callbackCallMethod);
         if (env->ExceptionCheck())
         {
            env->ExceptionDescribe();
         }
      }
   }

   // Intentionally do not DetachCurrentThread here. Fast-DDS reuses native threads; keeping
   // them attached avoids Attach/Detach on every sample.
}

std::string jstringToString(JNIEnv* env, jstring value)
{
   if (value == nullptr)
      return {};
   const char* chars = env->GetStringUTFChars(value, nullptr);
   std::string out = chars != nullptr ? chars : "";
   if (chars != nullptr)
      env->ReleaseStringUTFChars(value, chars);
   return out;
}

void fillGuid(JNIEnv* env, jbyteArray guidOut, const uint8_t* guid)
{
   if (guidOut == nullptr || env->GetArrayLength(guidOut) < 16)
      return;
   env->SetByteArrayRegion(guidOut, 0, 16, reinterpret_cast<const jbyte*>(guid));
}

bool ensureCallbackMethod(JNIEnv* env)
{
   if (g_callbackCallMethod != nullptr)
      return true;

   jclass local = env->FindClass("us/ihmc/fastddsjava/natives/fastddsjavaCallback");
   if (local == nullptr)
      return false;

   g_nativeCallbackClass = static_cast<jclass>(env->NewGlobalRef(local));
   env->DeleteLocalRef(local);
   if (g_nativeCallbackClass == nullptr)
      return false;

   // Method IDs from the interface are valid for all implementing classes.
   g_callbackCallMethod = env->GetMethodID(g_nativeCallbackClass, "call", "()V");
   return g_callbackCallMethod != nullptr;
}

std::function<void()> makeJavaCallback(JNIEnv* env, jobject callback)
{
   if (callback == nullptr)
      return nullptr;
   if (!ensureCallbackMethod(env))
      return nullptr;

   auto holder = std::make_shared<JniCallback>();
   holder->global_ref = env->NewGlobalRef(callback);
   if (holder->global_ref == nullptr)
      return nullptr;

   return [holder]() { invokeCallback(holder->global_ref); };
}
} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*)
{
   g_jvm = vm;
   return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM*, void*)
{
   if (g_jvm != nullptr && g_nativeCallbackClass != nullptr)
   {
      JNIEnv* env = nullptr;
      if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK && env != nullptr)
         env->DeleteGlobalRef(g_nativeCallbackClass);
   }
   g_nativeCallbackClass = nullptr;
   g_callbackCallMethod = nullptr;
   g_jvm = nullptr;
}

#define JNIFASTDDS(name) Java_us_ihmc_fastddsjava_natives_fastddsjava_##name

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(loadXmlProfilesString)(JNIEnv* env, jclass, jstring xml)
{
   return static_cast<jint>(fastddsjava_load_xml_profiles_string(jstringToString(env, xml)));
}

extern "C" JNIEXPORT jlong JNICALL JNIFASTDDS(createParticipant)(JNIEnv* env, jclass, jstring profileName)
{
   return reinterpret_cast<jlong>(fastddsjava_create_participant(jstringToString(env, profileName)));
}

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(deleteParticipant)(JNIEnv*, jclass, jlong participant)
{
   return static_cast<jint>(fastddsjava_delete_participant(reinterpret_cast<void*>(participant)));
}

extern "C" JNIEXPORT jlong JNICALL JNIFASTDDS(createPublisher)(JNIEnv* env, jclass, jlong participant, jstring profileName)
{
   return reinterpret_cast<jlong>(fastddsjava_create_publisher(reinterpret_cast<void*>(participant), jstringToString(env, profileName)));
}

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(deletePublisher)(JNIEnv*, jclass, jlong participant, jlong publisher)
{
   return static_cast<jint>(fastddsjava_delete_publisher(reinterpret_cast<void*>(participant), reinterpret_cast<void*>(publisher)));
}

extern "C" JNIEXPORT jlong JNICALL JNIFASTDDS(createSubscriber)(JNIEnv* env, jclass, jlong participant, jstring profileName)
{
   return reinterpret_cast<jlong>(fastddsjava_create_subscriber(reinterpret_cast<void*>(participant), jstringToString(env, profileName)));
}

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(deleteSubscriber)(JNIEnv*, jclass, jlong participant, jlong subscriber)
{
   return static_cast<jint>(fastddsjava_delete_subscriber(reinterpret_cast<void*>(participant), reinterpret_cast<void*>(subscriber)));
}

extern "C" JNIEXPORT jlong JNICALL JNIFASTDDS(createTopicDataWrapperType)(JNIEnv* env, jclass, jstring name, jshort encapsulation)
{
   return reinterpret_cast<jlong>(fastddsjava_create_topic_data_wrapper_type(jstringToString(env, name), static_cast<uint16_t>(encapsulation)));
}

extern "C" JNIEXPORT jlong JNICALL JNIFASTDDS(createTypesupport)(JNIEnv*, jclass, jlong type)
{
   return reinterpret_cast<jlong>(fastddsjava_create_typesupport(reinterpret_cast<fastddsjava_TopicDataWrapperType*>(type)));
}

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(registerType)(JNIEnv*, jclass, jlong participant, jlong typeSupport)
{
   return static_cast<jint>(fastddsjava_register_type(reinterpret_cast<void*>(participant), reinterpret_cast<void*>(typeSupport)));
}

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(unregisterType)(JNIEnv* env, jclass, jlong participant, jstring typeName)
{
   return static_cast<jint>(fastddsjava_unregister_type(reinterpret_cast<void*>(participant), jstringToString(env, typeName)));
}

extern "C" JNIEXPORT void JNICALL JNIFASTDDS(deleteTypesupport)(JNIEnv*, jclass, jlong typeSupport)
{
   fastddsjava_delete_typesupport(reinterpret_cast<void*>(typeSupport));
}

extern "C" JNIEXPORT jstring JNICALL JNIFASTDDS(topicDataWrapperTypeGetName)(JNIEnv* env, jclass, jlong type)
{
   std::string name = fastddsjava_topic_data_wrapper_type_get_name(reinterpret_cast<fastddsjava_TopicDataWrapperType*>(type));
   return env->NewStringUTF(name.c_str());
}

extern "C" JNIEXPORT jlong JNICALL JNIFASTDDS(createData)(JNIEnv*, jclass, jlong type)
{
   return reinterpret_cast<jlong>(fastddsjava_topic_data_wrapper_type_create_data(reinterpret_cast<fastddsjava_TopicDataWrapperType*>(type)));
}

extern "C" JNIEXPORT void JNICALL JNIFASTDDS(deleteData)(JNIEnv*, jclass, jlong type, jlong data)
{
   fastddsjava_topic_data_wrapper_type_delete_data(reinterpret_cast<fastddsjava_TopicDataWrapperType*>(type),
                                                   reinterpret_cast<fastddsjava_TopicDataWrapper*>(data));
}

extern "C" JNIEXPORT jlong JNICALL JNIFASTDDS(createTopic)(JNIEnv* env, jclass, jlong participant, jlong type, jstring topicName, jstring profileName)
{
   return reinterpret_cast<jlong>(fastddsjava_create_topic(reinterpret_cast<void*>(participant), reinterpret_cast<fastddsjava_TopicDataWrapperType*>(type),
                                                           jstringToString(env, topicName), jstringToString(env, profileName)));
}

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(deleteTopic)(JNIEnv*, jclass, jlong participant, jlong topic)
{
   return static_cast<jint>(fastddsjava_delete_topic(reinterpret_cast<void*>(participant), reinterpret_cast<void*>(topic)));
}

extern "C" JNIEXPORT jlong JNICALL JNIFASTDDS(createDataWriter)(JNIEnv* env, jclass, jlong publisher, jlong topic, jstring profileName)
{
   return reinterpret_cast<jlong>(
         fastddsjava_create_datawriter(reinterpret_cast<void*>(publisher), reinterpret_cast<void*>(topic), jstringToString(env, profileName)));
}

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(deleteDataWriter)(JNIEnv*, jclass, jlong publisher, jlong writer)
{
   return static_cast<jint>(fastddsjava_delete_datawriter(reinterpret_cast<void*>(publisher), reinterpret_cast<void*>(writer)));
}

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(dataWriterWrite)(JNIEnv*, jclass, jlong writer, jlong data)
{
   return static_cast<jint>(fastddsjava_datawriter_write(reinterpret_cast<void*>(writer), reinterpret_cast<fastddsjava_TopicDataWrapper*>(data)));
}

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(dataWriterSetListener)(JNIEnv*, jclass, jlong writer, jlong listener)
{
   return static_cast<jint>(fastddsjava_datawriter_set_listener(reinterpret_cast<void*>(writer),
                                                                reinterpret_cast<fastddsjava_DataWriterListener*>(listener)));
}

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(dataWriterGetPublicationMatchedCount)(JNIEnv*, jclass, jlong writer)
{
   eprosima::fastdds::dds::PublicationMatchedStatus status;
   if (fastddsjava_datawriter_get_publication_matched_status(reinterpret_cast<void*>(writer), status) != 0)
      return 0;
   return static_cast<jint>(status.current_count);
}

extern "C" JNIEXPORT jlong JNICALL JNIFASTDDS(createDataReader)(JNIEnv* env, jclass, jlong subscriber, jlong topic, jlong listener, jstring profileName)
{
   return reinterpret_cast<jlong>(fastddsjava_create_datareader(reinterpret_cast<void*>(subscriber), reinterpret_cast<void*>(topic),
                                                                reinterpret_cast<fastddsjava_DataReaderListener*>(listener),
                                                                jstringToString(env, profileName)));
}

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(deleteDataReader)(JNIEnv*, jclass, jlong subscriber, jlong reader)
{
   return static_cast<jint>(fastddsjava_delete_datareader(reinterpret_cast<void*>(subscriber), reinterpret_cast<void*>(reader)));
}

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(dataReaderReadNextSample)(JNIEnv*, jclass, jlong reader, jlong data, jlong sampleInfo)
{
   return static_cast<jint>(fastddsjava_datareader_read_next_sample(reinterpret_cast<void*>(reader), reinterpret_cast<void*>(data),
                                                                    reinterpret_cast<eprosima::fastdds::dds::SampleInfo*>(sampleInfo)));
}

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(dataReaderTakeNextCustom)(JNIEnv*, jclass, jlong reader, jlong data, jlong sampleInfo)
{
   return static_cast<jint>(fastddsjava_datareader_take_next_custom(reinterpret_cast<void*>(reader), reinterpret_cast<void*>(data),
                                                                   reinterpret_cast<eprosima::fastdds::dds::SampleInfo*>(sampleInfo)));
}

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(dataReaderSetListener)(JNIEnv*, jclass, jlong reader, jlong listener)
{
   return static_cast<jint>(fastddsjava_datareader_set_listener(reinterpret_cast<void*>(reader),
                                                                reinterpret_cast<fastddsjava_DataReaderListener*>(listener)));
}

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(dataReaderGetUnreadCount)(JNIEnv*, jclass, jlong reader)
{
   return static_cast<jint>(fastddsjava_datareader_get_unread_count(reinterpret_cast<void*>(reader)));
}

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(dataReaderGetSubscriptionMatchedCount)(JNIEnv*, jclass, jlong reader)
{
   eprosima::fastdds::dds::SubscriptionMatchedStatus status;
   if (fastddsjava_datareader_get_subscription_matched_status(reinterpret_cast<void*>(reader), status) != 0)
      return 0;
   return static_cast<jint>(status.current_count);
}

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(dataReaderGetSubscriptionMatchedStatus)(JNIEnv* env, jclass, jlong reader, jbyteArray lastPublicationGuidOut)
{
   eprosima::fastdds::dds::SubscriptionMatchedStatus status;
   if (fastddsjava_datareader_get_subscription_matched_status(reinterpret_cast<void*>(reader), status) != 0)
      return 0;
   uint8_t guid[16];
   fastddsjava_subscription_matched_status_last_publication_guid(status, guid);
   fillGuid(env, lastPublicationGuidOut, guid);
   return static_cast<jint>(status.current_count);
}

extern "C" JNIEXPORT void JNICALL JNIFASTDDS(getParticipantGuid)(JNIEnv* env, jclass, jlong participant, jbyteArray guidOut)
{
   uint8_t guid[16];
   fastddsjava_get_participant_guid(reinterpret_cast<void*>(participant), guid);
   fillGuid(env, guidOut, guid);
}

extern "C" JNIEXPORT void JNICALL JNIFASTDDS(getWriterGuid)(JNIEnv* env, jclass, jlong writer, jbyteArray guidOut)
{
   uint8_t guid[16];
   fastddsjava_get_writer_guid(reinterpret_cast<void*>(writer), guid);
   fillGuid(env, guidOut, guid);
}

extern "C" JNIEXPORT void JNICALL JNIFASTDDS(getReaderGuid)(JNIEnv* env, jclass, jlong reader, jbyteArray guidOut)
{
   uint8_t guid[16];
   fastddsjava_get_reader_guid(reinterpret_cast<void*>(reader), guid);
   fillGuid(env, guidOut, guid);
}

extern "C" JNIEXPORT void JNICALL JNIFASTDDS(topicDataResize)(JNIEnv*, jclass, jlong data, jint size)
{
   fastddsjava_topic_data_resize(reinterpret_cast<fastddsjava_TopicDataWrapper*>(data), static_cast<size_t>(size));
}

extern "C" JNIEXPORT jint JNICALL JNIFASTDDS(topicDataSize)(JNIEnv*, jclass, jlong data)
{
   return static_cast<jint>(fastddsjava_topic_data_size(reinterpret_cast<fastddsjava_TopicDataWrapper*>(data)));
}

extern "C" JNIEXPORT void JNICALL JNIFASTDDS(topicDataWrite)(JNIEnv* env, jclass, jlong data, jbyteArray src, jint offset, jint length)
{
   if (src == nullptr || length <= 0)
      return;
   // Prefer critical access to avoid an intermediate native copy of the heap array.
   jbyte* bytes = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(src, nullptr));
   if (bytes == nullptr)
      return;
   fastddsjava_topic_data_write(reinterpret_cast<fastddsjava_TopicDataWrapper*>(data), reinterpret_cast<const uint8_t*>(bytes + offset),
                                static_cast<size_t>(length));
   env->ReleasePrimitiveArrayCritical(src, bytes, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL JNIFASTDDS(topicDataRead)(JNIEnv* env, jclass, jlong data, jbyteArray dst, jint offset, jint length)
{
   if (dst == nullptr || length <= 0)
      return;
   jbyte* bytes = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(dst, nullptr));
   if (bytes == nullptr)
      return;
   fastddsjava_topic_data_read(reinterpret_cast<fastddsjava_TopicDataWrapper*>(data), reinterpret_cast<uint8_t*>(bytes + offset),
                               static_cast<size_t>(length));
   env->ReleasePrimitiveArrayCritical(dst, bytes, 0);
}

extern "C" JNIEXPORT void JNICALL JNIFASTDDS(topicDataWriteBuffer)(JNIEnv* env, jclass, jlong data, jobject src, jint offset, jint length)
{
   if (src == nullptr || length <= 0)
      return;
   void* address = env->GetDirectBufferAddress(src);
   if (address == nullptr)
      return;
   jlong capacity = env->GetDirectBufferCapacity(src);
   if (offset < 0 || length < 0 || static_cast<jlong>(offset) + length > capacity)
      return;
   fastddsjava_topic_data_write(reinterpret_cast<fastddsjava_TopicDataWrapper*>(data),
                                static_cast<const uint8_t*>(address) + offset,
                                static_cast<size_t>(length));
}

extern "C" JNIEXPORT void JNICALL JNIFASTDDS(topicDataReadBuffer)(JNIEnv* env, jclass, jlong data, jobject dst, jint offset, jint length)
{
   if (dst == nullptr || length <= 0)
      return;
   void* address = env->GetDirectBufferAddress(dst);
   if (address == nullptr)
      return;
   jlong capacity = env->GetDirectBufferCapacity(dst);
   if (offset < 0 || length < 0 || static_cast<jlong>(offset) + length > capacity)
      return;
   fastddsjava_topic_data_read(reinterpret_cast<fastddsjava_TopicDataWrapper*>(data),
                               static_cast<uint8_t*>(address) + offset,
                               static_cast<size_t>(length));
}

extern "C" JNIEXPORT jlong JNICALL JNIFASTDDS(createSampleInfo)(JNIEnv*, jclass)
{
   return reinterpret_cast<jlong>(fastddsjava_create_sample_info());
}

extern "C" JNIEXPORT void JNICALL JNIFASTDDS(deleteSampleInfo)(JNIEnv*, jclass, jlong sampleInfo)
{
   fastddsjava_delete_sample_info(reinterpret_cast<eprosima::fastdds::dds::SampleInfo*>(sampleInfo));
}

extern "C" JNIEXPORT jboolean JNICALL JNIFASTDDS(sampleInfoValidData)(JNIEnv*, jclass, jlong sampleInfo)
{
   auto* info = reinterpret_cast<eprosima::fastdds::dds::SampleInfo*>(sampleInfo);
   return info->valid_data ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL JNIFASTDDS(sampleInfoSourceTimestampNanos)(JNIEnv*, jclass, jlong sampleInfo)
{
   auto* info = reinterpret_cast<eprosima::fastdds::dds::SampleInfo*>(sampleInfo);
   return static_cast<jlong>(info->source_timestamp.to_ns());
}

extern "C" JNIEXPORT jlong JNICALL JNIFASTDDS(sampleInfoReceptionTimestampNanos)(JNIEnv*, jclass, jlong sampleInfo)
{
   auto* info = reinterpret_cast<eprosima::fastdds::dds::SampleInfo*>(sampleInfo);
   return static_cast<jlong>(info->reception_timestamp.to_ns());
}

extern "C" JNIEXPORT jshort JNICALL JNIFASTDDS(sampleInfoSampleState)(JNIEnv*, jclass, jlong sampleInfo)
{
   auto* info = reinterpret_cast<eprosima::fastdds::dds::SampleInfo*>(sampleInfo);
   return static_cast<jshort>(info->sample_state);
}

extern "C" JNIEXPORT jshort JNICALL JNIFASTDDS(sampleInfoViewState)(JNIEnv*, jclass, jlong sampleInfo)
{
   auto* info = reinterpret_cast<eprosima::fastdds::dds::SampleInfo*>(sampleInfo);
   return static_cast<jshort>(info->view_state);
}

extern "C" JNIEXPORT jshort JNICALL JNIFASTDDS(sampleInfoInstanceState)(JNIEnv*, jclass, jlong sampleInfo)
{
   auto* info = reinterpret_cast<eprosima::fastdds::dds::SampleInfo*>(sampleInfo);
   return static_cast<jshort>(info->instance_state);
}

extern "C" JNIEXPORT jlong JNICALL JNIFASTDDS(createDataReaderListener)(JNIEnv*, jclass)
{
   return reinterpret_cast<jlong>(fastddsjava_create_datareader_listener());
}

extern "C" JNIEXPORT void JNICALL JNIFASTDDS(deleteDataReaderListener)(JNIEnv*, jclass, jlong listener)
{
   fastddsjava_delete_datareader_listener(reinterpret_cast<fastddsjava_DataReaderListener*>(listener));
}

extern "C" JNIEXPORT void JNICALL JNIFASTDDS(dataReaderListenerSetOnDataAvailable)(JNIEnv* env, jclass, jlong listener, jobject callback)
{
   auto* nativeListener = reinterpret_cast<fastddsjava_DataReaderListener*>(listener);
   fastddsjava_datareader_listener_set_on_data_available(nativeListener, makeJavaCallback(env, callback));
}

extern "C" JNIEXPORT void JNICALL JNIFASTDDS(dataReaderListenerSetOnSubscriptionMatched)(JNIEnv* env, jclass, jlong listener, jobject callback)
{
   auto* nativeListener = reinterpret_cast<fastddsjava_DataReaderListener*>(listener);
   fastddsjava_datareader_listener_set_on_subscription_matched(nativeListener, makeJavaCallback(env, callback));
}

extern "C" JNIEXPORT jlong JNICALL JNIFASTDDS(createDataWriterListener)(JNIEnv*, jclass)
{
   return reinterpret_cast<jlong>(fastddsjava_create_datawriter_listener());
}

extern "C" JNIEXPORT void JNICALL JNIFASTDDS(deleteDataWriterListener)(JNIEnv*, jclass, jlong listener)
{
   fastddsjava_delete_datawriter_listener(reinterpret_cast<fastddsjava_DataWriterListener*>(listener));
}

extern "C" JNIEXPORT void JNICALL JNIFASTDDS(dataWriterListenerSetOnPublicationMatched)(JNIEnv* env, jclass, jlong listener, jobject callback)
{
   auto* nativeListener = reinterpret_cast<fastddsjava_DataWriterListener*>(listener);
   fastddsjava_datawriter_listener_set_on_publication_matched(nativeListener, makeJavaCallback(env, callback));
}
