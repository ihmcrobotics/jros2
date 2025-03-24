#ifndef FASTDDSJAVA_H
#define FASTDDSJAVA_H

#include <fastdds/dds/topic/TopicDataType.hpp>
#include <fastdds/rtps/common/SerializedPayload.hpp>

#include <fastdds/dds/domain/DomainParticipant.hpp>
#include <fastdds/dds/publisher/DataWriterListener.hpp>
#include <fastdds/dds/topic/TypeSupport.hpp>
#include <fastdds/dds/domain/DomainParticipantFactory.hpp>
#include <fastdds/dds/publisher/DataWriter.hpp>
#include <fastdds/dds/publisher/Publisher.hpp>
#include <fastdds/dds/publisher/qos/DataWriterQos.hpp>
#include <fastdds/dds/publisher/qos/PublisherQos.hpp>
#include <fastdds/dds/subscriber/Subscriber.hpp>
#include <fastdds/dds/subscriber/SampleInfo.hpp>
#include <fastdds/dds/subscriber/DataReader.hpp>

#define JAVACPP_SKIP

struct fastddsjava_TopicDataWrapper {
    uint32_t initial_size;
    std::vector<uint8_t> data_vector;

    void* data_ptr() {
        return data_vector.data();
    }
};

class fastddsjava_TopicDataWrapperType : public eprosima::fastdds::dds::TopicDataType {
public:
    fastddsjava_TopicDataWrapperType(std::string name, uint16_t encapsulation, uint32_t initial_size) {
        set_name(name.c_str());
        this->encapsulation = encapsulation;
        this->initial_size = initial_size;

        // TODO:
        max_serialized_type_size = initial_size + 4; // padding for encapsulation (needed?)
        is_compute_key_provided = false;
    }

    ~fastddsjava_TopicDataWrapperType() override {
        // TODO:
    };

    JAVACPP_SKIP bool serialize(const void* const data_, eprosima::fastdds::rtps::SerializedPayload_t& payload,
                                    eprosima::fastdds::dds::DataRepresentationId_t data_representation) override {
        fastddsjava_TopicDataWrapper* data = const_cast<fastddsjava_TopicDataWrapper*>(static_cast<const fastddsjava_TopicDataWrapper*>(data_));

        payload.encapsulation = this->encapsulation;
        uint32_t data_length = calculate_serialized_size(data, data_representation);
        payload.length = data_length;
        memcpy(payload.data, data->data_ptr(), data_length); // TODO: can remove?
        payload.max_size = payload.length; // TODO:

        return true;
    };

    JAVACPP_SKIP bool deserialize(eprosima::fastdds::rtps::SerializedPayload_t& payload, void* data_) override {
        fastddsjava_TopicDataWrapper* data = static_cast<fastddsjava_TopicDataWrapper*>(data_);

        data->initial_size = payload.length;
        memcpy(data->data_ptr(), payload.data, payload.length);

        return true;
    };

    JAVACPP_SKIP uint32_t calculate_serialized_size(const void* const data_,
                                                        eprosima::fastdds::dds::DataRepresentationId_t data_representation) override {
        fastddsjava_TopicDataWrapper* data = const_cast<fastddsjava_TopicDataWrapper*>(static_cast<const fastddsjava_TopicDataWrapper*>(data_));

        return static_cast<uint32_t>(data->data_vector.size());
    };

    JAVACPP_SKIP bool compute_key(eprosima::fastdds::rtps::SerializedPayload_t& payload, eprosima::fastdds::rtps::InstanceHandle_t& ihandle,
                        bool force_md5 = false) override {
        // TODO:
        std::cout << "compute_key 1" << std::endl;
        return true;
    };

    JAVACPP_SKIP bool compute_key(const void* const data, eprosima::fastdds::rtps::InstanceHandle_t& ihandle, bool force_md5 = false) override {
        // TODO:
        std::cout << "compute_key 2" << std::endl;
        return true;
    };

    void* create_data() override {
        fastddsjava_TopicDataWrapper* data = new fastddsjava_TopicDataWrapper();
        data->initial_size = initial_size;
        data->data_vector = std::vector<uint8_t>(initial_size, 0);
        return reinterpret_cast<void*>(data);
    };

    void delete_data(void* data) override {
        delete(reinterpret_cast<fastddsjava_TopicDataWrapper*>(data));
    };

private:
    /*
     *
     */
    uint32_t initial_size;

    /*
     *
     */
    uint16_t encapsulation;

};


class fastddsjava_DataReaderListener : public eprosima::fastdds::dds::DataReaderListener {
public:
    typedef std::function<void(const fastddsjava_TopicDataWrapper*, const eprosima::fastdds::dds::SampleInfo*)> fastddsjava_DataReaderListenerCallback;

    void set_callback(fastddsjava_DataReaderListenerCallback callback) {
        this->callback = callback;
    }

    JAVACPP_SKIP void on_data_available(eprosima::fastdds::dds::DataReader* reader) override {
        eprosima::fastdds::dds::TypeSupport type = reader->type();
        fastddsjava_TopicDataWrapper* data = reinterpret_cast<fastddsjava_TopicDataWrapper*>(type.create_data());

        eprosima::fastdds::dds::SampleInfo info;
        reader->take_next_sample(data, &info);

        if (callback)
            callback(data, &info);
    }

private:
    fastddsjava_DataReaderListenerCallback callback;
};

/*
 *  Should only be done once during program run
 */
void fastddsjava_load_xml_profiles_string(std::string xml) {
    auto factory = eprosima::fastdds::dds::DomainParticipantFactory::get_instance();

    factory->load_XML_profiles_string(xml.c_str(), xml.length());
}

/*
 *  Returns eprosima::fastdds::dds::DomainParticipant*
 */
void* fastddsjava_create_participant(std::string profile_name) {
    auto factory = eprosima::fastdds::dds::DomainParticipantFactory::get_instance();

    return factory->create_participant_with_profile(profile_name);
}

void fastddsjava_delete_participant(void* participant_) {
    eprosima::fastdds::dds::DomainParticipant* participant = static_cast<eprosima::fastdds::dds::DomainParticipant*>(participant_);
    auto factory = eprosima::fastdds::dds::DomainParticipantFactory::get_instance();

    factory->delete_participant(participant);
}

/*
 *  Returns eprosima::fastdds::dds::Publisher*
 */
void* fastddsjava_create_publisher(void* participant_, std::string profile_name) {
    eprosima::fastdds::dds::DomainParticipant* participant = static_cast<eprosima::fastdds::dds::DomainParticipant*>(participant_);

    return participant->create_publisher_with_profile(profile_name);
}

void fastddsjava_delete_publisher(void* participant_, void* publisher_) {
    eprosima::fastdds::dds::DomainParticipant* participant = static_cast<eprosima::fastdds::dds::DomainParticipant*>(participant_);
    eprosima::fastdds::dds::Publisher* publisher = static_cast<eprosima::fastdds::dds::Publisher*>(publisher_);

    participant->delete_publisher(publisher);
}

/*
 *  Returns eprosima::fastdds::dds::Subscriber*
 */
void* fastddsjava_create_subscriber(void* participant_, std::string profile_name) {
    eprosima::fastdds::dds::DomainParticipant* participant = static_cast<eprosima::fastdds::dds::DomainParticipant*>(participant_);

    return participant->create_subscriber_with_profile(profile_name);
}

void fastddsjava_delete_subscriber(void* participant_, void* subscriber_) {
    eprosima::fastdds::dds::DomainParticipant* participant = static_cast<eprosima::fastdds::dds::DomainParticipant*>(participant_);
    eprosima::fastdds::dds::Subscriber* subscriber = static_cast<eprosima::fastdds::dds::Subscriber*>(subscriber_);

    participant->delete_subscriber(subscriber);
}

void fastddsjava_register_type(void* participant_, fastddsjava_TopicDataWrapperType* type) {
    eprosima::fastdds::dds::DomainParticipant* participant = static_cast<eprosima::fastdds::dds::DomainParticipant*>(participant_);

    eprosima::fastdds::dds::TypeSupport type_support(type);

    participant->register_type(type_support);
}

/*
 *  Returns eprosima::fastdds::dds::Topic*
 */
void* fastddsjava_create_topic(void* participant_, fastddsjava_TopicDataWrapperType* type, std::string topic_name, std::string profile_name) {
    eprosima::fastdds::dds::DomainParticipant* participant = static_cast<eprosima::fastdds::dds::DomainParticipant*>(participant_);

    return participant->create_topic_with_profile(topic_name, type->get_name(), profile_name);
}

void fastddsjava_delete_topic(void* participant_, void* topic_) {
    eprosima::fastdds::dds::DomainParticipant* participant = static_cast<eprosima::fastdds::dds::DomainParticipant*>(participant_);
    eprosima::fastdds::dds::Topic* topic = static_cast<eprosima::fastdds::dds::Topic*>(topic_);

    participant->delete_topic(topic);
}

/*
 *  Returns eprosima::fastdds::dds::DataWriter*
 */
void* fastddsjava_create_datawriter(void* publisher_, void* topic_, std::string profile_name) {
    eprosima::fastdds::dds::Publisher* publisher = static_cast<eprosima::fastdds::dds::Publisher*>(publisher_);
    eprosima::fastdds::dds::Topic* topic = static_cast<eprosima::fastdds::dds::Topic*>(topic_);

    return publisher->create_datawriter_with_profile(topic, profile_name);
}

void fastddsjava_delete_datawriter(void* publisher_, void* writer_) {
    eprosima::fastdds::dds::Publisher* publisher = static_cast<eprosima::fastdds::dds::Publisher*>(publisher_);
    eprosima::fastdds::dds::DataWriter* writer = static_cast<eprosima::fastdds::dds::DataWriter*>(writer_);

    publisher->delete_datawriter(writer);
}

void fastddsjava_datawriter_write(void* writer_, fastddsjava_TopicDataWrapper* data) {
    eprosima::fastdds::dds::DataWriter* writer = static_cast<eprosima::fastdds::dds::DataWriter*>(writer_);

    writer->write(data);
}

/*
 *  Returns eprosima::fastdds::dds::DataReader*
 */
void* fastddsjava_create_datareader(void* subscriber_, void* topic_, fastddsjava_DataReaderListener* listener, std::string profile_name) {
    eprosima::fastdds::dds::Subscriber* subscriber = static_cast<eprosima::fastdds::dds::Subscriber*>(subscriber_);
    eprosima::fastdds::dds::Topic* topic = static_cast<eprosima::fastdds::dds::Topic*>(topic_);

    return subscriber->create_datareader_with_profile(topic, profile_name, listener);
}

void fastddsjava_delete_datareader(void* subscriber_, void* reader_) {
    eprosima::fastdds::dds::Subscriber* subscriber = static_cast<eprosima::fastdds::dds::Subscriber*>(subscriber_);
    eprosima::fastdds::dds::DataReader* reader = static_cast<eprosima::fastdds::dds::DataReader*>(reader_);

    subscriber->delete_datareader(reader);
}

#endif // FASTDDSJAVA_H