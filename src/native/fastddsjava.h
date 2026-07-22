#ifndef FASTDDSJAVA_H
#define FASTDDSJAVA_H

#include <cstdint>
#include <functional>
#include <string>
#include <vector>

#include <fastdds/dds/core/status/PublicationMatchedStatus.hpp>
#include <fastdds/dds/core/status/SubscriptionMatchedStatus.hpp>
#include <fastdds/dds/subscriber/SampleInfo.hpp>

struct fastddsjava_TopicDataWrapper
{
   std::vector<uint8_t> data_vector;

   uint8_t* data_ptr();
};

class fastddsjava_TopicDataWrapperType;

class fastddsjava_DataReaderListener;
class fastddsjava_DataWriterListener;

uint32_t fastddsjava_load_xml_profiles_string(const std::string& xml);

void* fastddsjava_create_participant(const std::string& profile_name);
uint32_t fastddsjava_delete_participant(void* participant_);

void* fastddsjava_create_publisher(void* participant_, const std::string& profile_name);
uint32_t fastddsjava_delete_publisher(void* participant_, void* publisher_);

void* fastddsjava_create_subscriber(void* participant_, const std::string& profile_name);
uint32_t fastddsjava_delete_subscriber(void* participant_, void* subscriber_);

fastddsjava_TopicDataWrapperType* fastddsjava_create_topic_data_wrapper_type(const std::string& name, uint16_t encapsulation);
void* fastddsjava_create_typesupport(fastddsjava_TopicDataWrapperType* type);
uint32_t fastddsjava_register_type(void* participant_, void* type_support_);
uint32_t fastddsjava_unregister_type(void* participant_, const std::string& type_name);
void fastddsjava_delete_typesupport(void* type_support_);
std::string fastddsjava_topic_data_wrapper_type_get_name(fastddsjava_TopicDataWrapperType* type);
fastddsjava_TopicDataWrapper* fastddsjava_topic_data_wrapper_type_create_data(fastddsjava_TopicDataWrapperType* type);
void fastddsjava_topic_data_wrapper_type_delete_data(fastddsjava_TopicDataWrapperType* type, fastddsjava_TopicDataWrapper* data);

void* fastddsjava_create_topic(void* participant_, fastddsjava_TopicDataWrapperType* type, const std::string& topic_name,
                               const std::string& profile_name);
uint32_t fastddsjava_delete_topic(void* participant_, void* topic_);

void* fastddsjava_create_datawriter(void* publisher_, void* topic_, const std::string& profile_name);
uint32_t fastddsjava_delete_datawriter(void* publisher_, void* writer_);
uint32_t fastddsjava_datawriter_write(void* writer_, fastddsjava_TopicDataWrapper* data);
uint32_t fastddsjava_datawriter_set_listener(void* writer_, fastddsjava_DataWriterListener* listener = nullptr);
uint32_t fastddsjava_datawriter_get_publication_matched_status(void* writer_, eprosima::fastdds::dds::PublicationMatchedStatus& status);

void* fastddsjava_create_datareader(void* subscriber_, void* topic_, fastddsjava_DataReaderListener* listener,
                                    const std::string& profile_name);
uint32_t fastddsjava_delete_datareader(void* subscriber_, void* reader_);
uint32_t fastddsjava_datareader_read_next_sample(void* reader_, void* data, eprosima::fastdds::dds::SampleInfo* info);
uint32_t fastddsjava_datareader_take_next_custom(void* reader_, void* data, eprosima::fastdds::dds::SampleInfo* info);
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
