#ifndef FASTDDSJAVA_H
#define FASTDDSJAVA_H

#include <cstdint>
#include <functional>
#include <string>
#include <vector>

#include <fastdds/dds/core/status/PublicationMatchedStatus.hpp>
#include <fastdds/dds/core/status/SubscriptionMatchedStatus.hpp>
#include <fastdds/dds/subscriber/SampleInfo.hpp>

/**
 * ABI version of this library as reported to Java (us.ihmc.fastddsjava.natives.fastddsjava#abiVersion()).
 * Version 1 is every library built before versioning existed. Version 2 adds nativeAbiVersion, nativeHasSecurity,
 * dataWriterWriteBuffer and dataReaderTakeNextToBuffer.
 */
#define FASTDDSJAVA_ABI_VERSION 2

/** fastddsjava_datareader_take_next_to_buffer: no sample was available. */
#define FASTDDSJAVA_TAKE_NO_DATA (-1)
/** fastddsjava_datareader_take_next_to_buffer: a sample without data (valid_data == false) was taken. */
#define FASTDDSJAVA_TAKE_INVALID_SAMPLE (-2)
/** fastddsjava_datareader_take_next_to_buffer: errors are returned as -(FASTDDSJAVA_TAKE_ERROR_BASE + retcode). */
#define FASTDDSJAVA_TAKE_ERROR_BASE 100

/**
 * The sample type handed to Fast-DDS for every jros2 topic: an opaque, already CDR-encoded byte blob
 * (4-byte encapsulation header included).
 *
 * Objects created with fastddsjava_topic_data_wrapper_type_create_data ("topic data") only use data_vector. The view
 * fields are only ever set on short-lived wrappers on the C++ stack of one write or take call, so a borrowed pointer
 * never outlives that call and never becomes visible to another thread:
 * - Writing: serialize() copies from the borrowed source view when has_src_view is set (zero-copy write of a Java
 *   direct buffer, valid only during DataWriter::write()), otherwise from data_vector.
 * - Reading: deserialize() copies into the borrowed destination (dst_ptr) when the payload fits, otherwise into
 *   *fallback (the data_vector of the caller's topic data) or, without a fallback, into data_vector. length and in_dst
 *   describe the result of the last deserialize().
 */
struct fastddsjava_TopicDataWrapper
{
   /** Owned storage: the legacy (ABI v1) write source and the destination of reads without a borrowed destination. */
   std::vector<uint8_t> data_vector;

   /** Borrowed source view for zero-copy writes. */
   const uint8_t* src_ptr = nullptr;
   uint32_t src_len = 0;
   bool has_src_view = false;

   /** Borrowed destination for takes, e.g. the address of a Java direct ByteBuffer. */
   uint8_t* dst_ptr = nullptr;
   uint32_t dst_cap = 0;
   /** Borrowed storage for payloads that do not fit dst_ptr; data_vector if null. */
   std::vector<uint8_t>* fallback = nullptr;

   /** Size in bytes of the last deserialized payload, including the encapsulation header. */
   uint32_t length = 0;
   /** True if the last deserialized payload was copied to dst_ptr, false if it is in the fallback storage. */
   bool in_dst = false;

   const uint8_t* write_ptr() const;
   uint32_t write_len() const;
   std::vector<uint8_t>& read_storage();
   uint8_t* data_ptr();
};

class fastddsjava_TopicDataWrapperType;
struct fastddsjava_TypeSupportHolder;

class fastddsjava_DataReaderListener;
class fastddsjava_DataWriterListener;

int32_t fastddsjava_abi_version();
bool fastddsjava_has_security();

/**
 * True once the process has started to exit (std::exit, return from main, or the JVM's System.exit / Runtime.halt, which
 * call std::exit after the JVM has shut down). Set by an atexit handler that is registered after the first participant
 * was created, so it runs before Fast-DDS's static destructors delete the participants that are still alive. From then
 * on no listener calls Java and no thread calls into the JVM: HotSpot blocks such a thread forever once it has exited,
 * and that thread would still hold the Fast-DDS locks the participant deletion waits for (exit would hang).
 */
bool fastddsjava_process_exiting();

uint32_t fastddsjava_load_xml_profiles_string(const std::string& xml);

void* fastddsjava_create_participant(const std::string& profile_name);
uint32_t fastddsjava_delete_participant(void* participant_);

void* fastddsjava_create_publisher(void* participant_, const std::string& profile_name);
uint32_t fastddsjava_delete_publisher(void* participant_, void* publisher_);

void* fastddsjava_create_subscriber(void* participant_, const std::string& profile_name);
uint32_t fastddsjava_delete_subscriber(void* participant_, void* subscriber_);

fastddsjava_TopicDataWrapperType* fastddsjava_create_topic_data_wrapper_type(const std::string& name, uint16_t encapsulation);
fastddsjava_TypeSupportHolder* fastddsjava_create_typesupport(fastddsjava_TopicDataWrapperType* type);
uint32_t fastddsjava_register_type(void* participant_, fastddsjava_TypeSupportHolder* type_support);
uint32_t fastddsjava_unregister_type(void* participant_, const std::string& type_name);
void fastddsjava_delete_typesupport(fastddsjava_TypeSupportHolder* type_support);
std::string fastddsjava_topic_data_wrapper_type_get_name(fastddsjava_TopicDataWrapperType* type);
fastddsjava_TopicDataWrapper* fastddsjava_topic_data_wrapper_type_create_data(fastddsjava_TopicDataWrapperType* type);
void fastddsjava_topic_data_wrapper_type_delete_data(fastddsjava_TopicDataWrapperType* type, fastddsjava_TopicDataWrapper* data);

void* fastddsjava_create_topic(void* participant_, fastddsjava_TopicDataWrapperType* type, const std::string& topic_name,
                               const std::string& profile_name);
uint32_t fastddsjava_delete_topic(void* participant_, void* topic_);

void* fastddsjava_create_datawriter(void* publisher_, void* topic_, const std::string& profile_name);
uint32_t fastddsjava_delete_datawriter(void* publisher_, void* writer_);
uint32_t fastddsjava_datawriter_write(void* writer_, fastddsjava_TopicDataWrapper* data);
uint32_t fastddsjava_datawriter_write_view(void* writer_, const uint8_t* ptr, uint32_t len);
uint32_t fastddsjava_datawriter_set_listener(void* writer_, fastddsjava_DataWriterListener* listener = nullptr);
uint32_t fastddsjava_datawriter_get_publication_matched_status(void* writer_, eprosima::fastdds::dds::PublicationMatchedStatus& status);

void* fastddsjava_create_datareader(void* subscriber_, void* topic_, fastddsjava_DataReaderListener* listener,
                                    const std::string& profile_name);
uint32_t fastddsjava_delete_datareader(void* subscriber_, void* reader_);
uint32_t fastddsjava_datareader_read_next_sample(void* reader_, void* data, eprosima::fastdds::dds::SampleInfo* info);
uint32_t fastddsjava_datareader_take_next_custom(void* reader_, void* data, eprosima::fastdds::dds::SampleInfo* info);
int32_t fastddsjava_datareader_take_next_to_buffer(void* reader_, fastddsjava_TopicDataWrapper* data,
                                                   eprosima::fastdds::dds::SampleInfo* info, uint8_t* dst, uint32_t dst_cap);
uint32_t fastddsjava_datareader_set_listener(void* reader_, fastddsjava_DataReaderListener* listener = nullptr);
uint32_t fastddsjava_datareader_get_unread_count(void* reader_);
uint32_t fastddsjava_datareader_get_subscription_matched_status(void* reader_,
                                                                eprosima::fastdds::dds::SubscriptionMatchedStatus& status);

void fastddsjava_get_participant_guid(void* participant_, uint8_t* guid_out);
void fastddsjava_get_writer_guid(void* writer_, uint8_t* guid_out);
void fastddsjava_get_reader_guid(void* reader_, uint8_t* guid_out);
void fastddsjava_subscription_matched_status_last_publication_guid(
      const eprosima::fastdds::dds::SubscriptionMatchedStatus& status, uint8_t* guid_out);

void fastddsjava_topic_data_resize(fastddsjava_TopicDataWrapper* data, size_t size);
size_t fastddsjava_topic_data_size(fastddsjava_TopicDataWrapper* data);
void fastddsjava_topic_data_write(fastddsjava_TopicDataWrapper* data, const uint8_t* src, size_t length);
void fastddsjava_topic_data_read(fastddsjava_TopicDataWrapper* data, uint8_t* dst, size_t length);

eprosima::fastdds::dds::SampleInfo* fastddsjava_create_sample_info();
void fastddsjava_delete_sample_info(eprosima::fastdds::dds::SampleInfo* info);

/*
 * Listener lifecycle (see fastddsjava.cpp for the full contract):
 * - Callbacks run behind an enable gate. A listener is disabled when it is detached from its last entity, and
 *   permanently when it is deleted, so it never calls a callback again after that point.
 * - Deleting a listener drops its callbacks (and with them the JNI global references) immediately. Its memory is
 *   released once every entity it was ever attached to has been deleted and no callback is running, because only
 *   delete_datareader / delete_datawriter fence Fast-DDS callbacks. Until then the disabled object is kept alive.
 * - Callback setters may be called at any time; a running callback keeps the previous callback alive until it returns.
 */
fastddsjava_DataReaderListener* fastddsjava_create_datareader_listener();
void fastddsjava_delete_datareader_listener(fastddsjava_DataReaderListener* listener);
void fastddsjava_datareader_listener_set_on_data_available(fastddsjava_DataReaderListener* listener,
                                                           std::function<void()> callback);
void fastddsjava_datareader_listener_set_on_subscription_matched(fastddsjava_DataReaderListener* listener,
                                                                 std::function<void()> callback);

fastddsjava_DataWriterListener* fastddsjava_create_datawriter_listener();
void fastddsjava_delete_datawriter_listener(fastddsjava_DataWriterListener* listener);
void fastddsjava_datawriter_listener_set_on_publication_matched(fastddsjava_DataWriterListener* listener,
                                                                std::function<void()> callback);

#endif // FASTDDSJAVA_H
